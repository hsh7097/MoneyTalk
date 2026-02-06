package com.sanha.moneytalk.feature.home.ui

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.database.dao.CategorySum
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.HybridSmsClassifier
import com.sanha.moneytalk.core.util.SmsBatchProcessor
import com.sanha.moneytalk.core.util.SmsParser
import com.sanha.moneytalk.core.util.SmsReader
import com.sanha.moneytalk.feature.chat.data.ClaudeRepository
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.chat.data.SmsAnalysisResult
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 홈 화면 UI 상태
 *
 * @property isLoading 데이터 로딩 중 여부
 * @property isRefreshing Pull-to-Refresh 진행 중 여부
 * @property selectedYear 선택된 연도
 * @property selectedMonth 선택된 월
 * @property monthStartDay 월 시작일 (1~28, 사용자 설정)
 * @property monthlyIncome 해당 월 총 수입
 * @property monthlyExpense 해당 월 총 지출
 * @property remainingBudget 잔여 예산 (수입 - 지출)
 * @property categoryExpenses 카테고리별 지출 합계 목록
 * @property recentExpenses 최근 지출 내역 목록
 * @property periodLabel 표시용 기간 레이블 (예: "1/25 ~ 2/24")
 * @property errorMessage 에러 메시지 (null이면 에러 없음)
 * @property isSyncing SMS 동기화 진행 중 여부
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedYear: Int = DateUtils.getCurrentYear(),
    val selectedMonth: Int = DateUtils.getCurrentMonth(),
    val monthStartDay: Int = 1,
    val monthlyIncome: Int = 0,
    val monthlyExpense: Int = 0,
    val remainingBudget: Int = 0,
    val categoryExpenses: List<CategorySum> = emptyList(),
    val recentExpenses: List<ExpenseEntity> = emptyList(),
    val periodLabel: String = "",
    val errorMessage: String? = null,
    val isSyncing: Boolean = false,
    // 카테고리 필터 (null이면 전체 표시)
    val selectedCategory: String? = null,
    // 카테고리 분류 관련
    val showClassifyDialog: Boolean = false,
    val unclassifiedCount: Int = 0,
    val isClassifying: Boolean = false,
    val classifyProgress: String = ""
)

/**
 * 홈 화면 ViewModel
 *
 * 홈 화면의 월간 지출 현황, 카테고리별 지출, 최근 지출 내역을 관리합니다.
 * SMS 동기화 기능을 통해 카드 결제 문자에서 자동으로 지출 내역을 추출합니다.
 *
 * 주요 기능:
 * - 월별 수입/지출/잔여 예산 표시
 * - 카테고리별 지출 합계 표시
 * - 최근 지출 내역 목록 표시
 * - SMS 동기화 (증분/전체)
 * - Pull-to-Refresh 지원
 * - 카테고리 수동 변경
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val claudeRepository: ClaudeRepository,
    private val categoryClassifierService: CategoryClassifierService,
    private val smsReader: SmsReader,
    private val settingsDataStore: SettingsDataStore,
    private val hybridSmsClassifier: HybridSmsClassifier,
    private val smsBatchProcessor: SmsBatchProcessor
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 현재 실행 중인 데이터 로드 작업 (취소 가능) */
    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        loadSettings()
    }

    /**
     * 설정 로드 및 초기 데이터 로드
     * 월 시작일 설정을 가져와서 현재 커스텀 월 기간을 계산합니다.
     */
    private fun loadSettings() {
        viewModelScope.launch {
            val (monthStartDay, year, month) = withContext(Dispatchers.IO) {
                val msd = settingsDataStore.getMonthStartDay()
                val (_, endTs) = DateUtils.getCurrentCustomMonthPeriod(msd)
                val calendar = java.util.Calendar.getInstance().apply { timeInMillis = endTs }
                Triple(msd, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH) + 1)
            }
            _uiState.update {
                it.copy(monthStartDay = monthStartDay, selectedYear = year, selectedMonth = month)
            }
            loadData()
        }
    }

    /**
     * 홈 화면 데이터 로드
     * 선택된 월의 수입, 지출, 카테고리별 합계, 최근 지출 내역을 조회합니다.
     */
    private fun loadData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val state = _uiState.value
                val (monthStart, monthEnd) = DateUtils.getCustomMonthPeriod(
                    state.selectedYear, state.selectedMonth, state.monthStartDay
                )
                val periodLabel = DateUtils.formatCustomMonthPeriod(
                    state.selectedYear, state.selectedMonth, state.monthStartDay
                )

                // 수입/카테고리별 합계 로드 (1회성)
                val (totalIncome, categoryExpenses) = withContext(Dispatchers.IO) {
                    val income = incomeRepository.getTotalIncomeByDateRange(monthStart, monthEnd)
                    val categories = expenseRepository.getExpenseSumByCategory(monthStart, monthEnd)
                    Pair(income, categories)
                }

                _uiState.update {
                    it.copy(periodLabel = periodLabel, monthlyIncome = totalIncome, categoryExpenses = categoryExpenses)
                }

                // 지출 내역은 Flow로 실시간 감지 (Room DB 변경 시 자동 업데이트)
                expenseRepository.getExpensesByDateRange(monthStart, monthEnd)
                    .catch { e ->
                        _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                    }
                    .collect { expenses ->
                        val totalExpense = expenses.sumOf { it.amount }
                        val categories = withContext(Dispatchers.IO) {
                            expenseRepository.getExpenseSumByCategory(monthStart, monthEnd)
                        }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                monthlyExpense = totalExpense,
                                remainingBudget = it.monthlyIncome - totalExpense,
                                categoryExpenses = categories,
                                recentExpenses = expenses.sortedByDescending { e -> e.dateTime }
                            )
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message)
                }
            }
        }
    }


    /** 이전 월로 이동 */
    fun previousMonth() {
        val state = _uiState.value
        var newYear = state.selectedYear
        var newMonth = state.selectedMonth - 1
        if (newMonth < 1) {
            newMonth = 12
            newYear -= 1
        }
        _uiState.update { it.copy(selectedYear = newYear, selectedMonth = newMonth) }
        loadData()
    }

    /** 다음 월로 이동 */
    fun nextMonth() {
        val state = _uiState.value
        var newYear = state.selectedYear
        var newMonth = state.selectedMonth + 1
        if (newMonth > 12) {
            newMonth = 1
            newYear += 1
        }
        _uiState.update { it.copy(selectedYear = newYear, selectedMonth = newMonth) }
        loadData()
    }

    /** 화면이 다시 표시될 때 데이터 새로고침 (LaunchedEffect에서 호출) */
    fun refreshData() {
        loadData()
    }

    /**
     * SMS 메시지 동기화 (하이브리드 3-tier 분류)
     *
     * 카드 결제 문자를 읽어서 지출 내역으로 변환합니다.
     *
     * 분류 전략:
     * 1단계 (Regex): 기존 정규식으로 빠르게 분류 + 파싱
     * 2단계 (Vector): 정규식 미스 SMS를 벡터 유사도로 재분류
     * 3단계 (LLM): 벡터로 확인된 결제 SMS의 데이터를 Gemini로 추출
     *
     * @param contentResolver SMS 읽기용 ContentResolver
     * @param forceFullSync true면 전체 동기화, false면 마지막 동기화 이후만 (증분 동기화)
     */
    fun syncSmsMessages(contentResolver: ContentResolver, forceFullSync: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }

            try {
                // ===== 모든 무거운 작업을 IO 스레드에서 실행 (UI 스레드 블로킹 방지) =====
                data class SyncResult(
                    val regexCount: Int,
                    val hybridCount: Int,
                    val incomeCount: Int,
                    val hasGeminiKey: Boolean,
                    val regexLearningData: List<Triple<String, String, SmsAnalysisResult>>
                )

                val result = withContext(Dispatchers.IO) {
                    // 마지막 동기화 시간 가져오기
                    val lastSyncTime = if (forceFullSync) 0L else settingsDataStore.getLastSyncTime()
                    val currentTime = System.currentTimeMillis()

                    android.util.Log.e("sanha", "=== syncSmsMessages (Hybrid) 시작 ===")
                    android.util.Log.e("sanha", "forceFullSync: $forceFullSync, lastSyncTime: $lastSyncTime")

                    // ===== 성능 최적화: 인메모리 캐시 초기화 =====
                    val existingSmsIds = expenseRepository.getAllSmsIds() // O(1) 조회용 HashSet
                    categoryClassifierService.initCategoryCache() // DB 쿼리 제거

                    // ===== 1단계: Regex로 빠르게 분류 =====
                    val regexSmsList = if (lastSyncTime > 0) {
                        smsReader.readCardSmsByDateRange(contentResolver, lastSyncTime, currentTime)
                    } else {
                        smsReader.readAllCardSms(contentResolver)
                    }

                    var regexCount = 0
                    val processedSmsIds = mutableSetOf<String>()
                    val expenseBatch = mutableListOf<ExpenseEntity>()
                    val regexLearningData = mutableListOf<Triple<String, String, SmsAnalysisResult>>()

                    for (sms in regexSmsList) {
                        try {
                            if (sms.id in existingSmsIds) {
                                processedSmsIds.add(sms.id)
                                continue
                            }

                            val result = hybridSmsClassifier.classifyRegexOnly(sms.body, sms.date)
                            if (result != null && result.isPayment && result.analysisResult != null) {
                                val analysis = result.analysisResult
                                val category = categoryClassifierService.getCategory(
                                    storeName = analysis.storeName,
                                    originalSms = sms.body
                                )

                                val expense = ExpenseEntity(
                                    amount = analysis.amount,
                                    storeName = analysis.storeName,
                                    category = category,
                                    cardName = analysis.cardName,
                                    dateTime = DateUtils.parseDateTime(analysis.dateTime),
                                    originalSms = sms.body,
                                    smsId = sms.id
                                )
                                expenseBatch.add(expense)
                                regexCount++
                                processedSmsIds.add(sms.id)
                                regexLearningData.add(Triple(sms.body, sms.address, analysis))

                                if (expenseBatch.size >= 100) {
                                    expenseRepository.insertAll(expenseBatch)
                                    expenseBatch.clear()
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("sanha", "Regex SMS 처리 실패 (무시): ${sms.id} - ${e.message}")
                        }
                    }

                    if (expenseBatch.isNotEmpty()) {
                        expenseRepository.insertAll(expenseBatch)
                        expenseBatch.clear()
                    }

                    android.util.Log.e("sanha", "Tier 1 (Regex): ${regexCount}건 처리")

                    // ===== 2~3단계: Regex 미스 SMS를 벡터+LLM으로 재분류 =====
                    var hybridCount = 0
                    val hasGeminiKey = settingsDataStore.getGeminiApiKey().isNotBlank()

                    if (hasGeminiKey) {
                        val allSms = if (lastSyncTime > 0) {
                            smsReader.readAllSmsByDateRange(contentResolver, lastSyncTime, currentTime)
                        } else {
                            val oneYearAgo = currentTime - (365L * 24 * 60 * 60 * 1000)
                            smsReader.readAllSmsByDateRange(contentResolver, oneYearAgo, currentTime)
                        }

                        val unclassifiedSms = allSms.filter { sms ->
                            sms.id !in processedSmsIds &&
                            sms.id !in existingSmsIds &&
                            !SmsParser.isIncomeSms(sms.body) &&
                            sms.body.length >= 10
                        }

                        android.util.Log.e("sanha", "미분류 SMS: ${unclassifiedSms.size}건")

                        if (unclassifiedSms.isNotEmpty()) {
                            if (forceFullSync || unclassifiedSms.size > 50) {
                                android.util.Log.e("sanha", "배치 모드로 처리 (${unclassifiedSms.size}건)")

                                val batchData = unclassifiedSms.map { sms ->
                                    SmsBatchProcessor.SmsData(
                                        id = sms.id,
                                        address = sms.address,
                                        body = sms.body,
                                        date = sms.date
                                    )
                                }

                                val batchResults = smsBatchProcessor.processBatch(
                                    unclassifiedSms = batchData,
                                    listener = object : SmsBatchProcessor.BatchProgressListener {
                                        override fun onProgress(phase: String, current: Int, total: Int) {
                                            _uiState.update {
                                                it.copy(classifyProgress = "$phase ($current/$total)")
                                            }
                                        }
                                    }
                                )

                                for ((smsData, analysis) in batchResults) {
                                    try {
                                        if (analysis.amount > 0 && smsData.id !in existingSmsIds) {
                                            val category = categoryClassifierService.getCategory(
                                                storeName = analysis.storeName,
                                                originalSms = smsData.body
                                            )

                                            val expense = ExpenseEntity(
                                                amount = analysis.amount,
                                                storeName = analysis.storeName,
                                                category = category,
                                                cardName = analysis.cardName,
                                                dateTime = DateUtils.parseDateTime(analysis.dateTime),
                                                originalSms = smsData.body,
                                                smsId = smsData.id
                                            )
                                            expenseBatch.add(expense)
                                            hybridCount++

                                            if (expenseBatch.size >= 100) {
                                                expenseRepository.insertAll(expenseBatch)
                                                expenseBatch.clear()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w("sanha", "배치 결과 저장 실패 (무시): ${e.message}")
                                    }
                                }
                            } else {
                                android.util.Log.e("sanha", "소량 배치 모드로 처리 (${unclassifiedSms.size}건)")
                                try {
                                    val batchInput = unclassifiedSms.map { sms ->
                                        Triple(sms.body, sms.date, sms.address)
                                    }
                                    val batchResults = hybridSmsClassifier.batchClassify(batchInput)

                                    for ((idx, classResult) in batchResults.withIndex()) {
                                        if (classResult.isPayment && classResult.analysisResult != null) {
                                            val analysis = classResult.analysisResult
                                            if (analysis.amount > 0) {
                                                val sms = unclassifiedSms[idx]
                                                val category = categoryClassifierService.getCategory(
                                                    storeName = analysis.storeName,
                                                    originalSms = sms.body
                                                )

                                                val expense = ExpenseEntity(
                                                    amount = analysis.amount,
                                                    storeName = analysis.storeName,
                                                    category = category,
                                                    cardName = analysis.cardName,
                                                    dateTime = DateUtils.parseDateTime(analysis.dateTime),
                                                    originalSms = sms.body,
                                                    smsId = sms.id
                                                )
                                                expenseBatch.add(expense)
                                                hybridCount++
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("sanha", "배치 하이브리드 분류 실패 (무시): ${e.message}")
                                }
                            }
                        }
                    }

                    // 남은 하이브리드 배치 삽입
                    if (expenseBatch.isNotEmpty()) {
                        expenseRepository.insertAll(expenseBatch)
                        expenseBatch.clear()
                    }

                    android.util.Log.e("sanha", "Tier 2~3 (Hybrid): ${hybridCount}건 추가 발견")
                    android.util.Log.e("sanha", "=== 동기화 결과 요약 ===")
                    android.util.Log.e("sanha", "Regex: ${regexCount}건, Hybrid: ${hybridCount}건, 처리된 SMS ID: ${processedSmsIds.size}건")

                    // ===== 수입 SMS 동기화 (배치 최적화) =====
                    var incomeCount = 0
                    val existingIncomeSmsIds = incomeRepository.getAllSmsIds().toHashSet()
                    val incomeBatch = mutableListOf<com.sanha.moneytalk.core.database.entity.IncomeEntity>()

                    val incomeSmsList = if (lastSyncTime > 0) {
                        smsReader.readIncomeSmsByDateRange(contentResolver, lastSyncTime, currentTime)
                    } else {
                        smsReader.readAllIncomeSms(contentResolver)
                    }

                    for (sms in incomeSmsList) {
                        try {
                            if (sms.id in existingIncomeSmsIds) continue

                            val amount = SmsParser.extractIncomeAmount(sms.body)
                            val incomeType = SmsParser.extractIncomeType(sms.body)
                            val source = SmsParser.extractIncomeSource(sms.body)
                            val dateTime = SmsParser.extractDateTime(sms.body, sms.date)

                            if (amount > 0) {
                                val income = com.sanha.moneytalk.core.database.entity.IncomeEntity(
                                    smsId = sms.id,
                                    amount = amount,
                                    type = incomeType,
                                    source = source,
                                    description = if (source.isNotBlank()) "${source}에서 $incomeType" else incomeType,
                                    isRecurring = incomeType == "급여",
                                    dateTime = DateUtils.parseDateTime(dateTime),
                                    originalSms = sms.body
                                )
                                incomeBatch.add(income)
                                incomeCount++

                                if (incomeBatch.size >= 100) {
                                    incomeRepository.insertAll(incomeBatch)
                                    incomeBatch.clear()
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("sanha", "수입 SMS 처리 실패 (무시): ${sms.id} - ${e.message}")
                        }
                    }

                    if (incomeBatch.isNotEmpty()) {
                        incomeRepository.insertAll(incomeBatch)
                        incomeBatch.clear()
                    }

                    // ===== 성능 최적화: 캐시 정리 =====
                    categoryClassifierService.flushPendingMappings()
                    categoryClassifierService.clearCategoryCache()

                    // 마지막 동기화 시간 저장
                    settingsDataStore.saveLastSyncTime(currentTime)

                    // 오래된 패턴 정리
                    hybridSmsClassifier.cleanupStalePatterns()

                    SyncResult(
                        regexCount = regexCount,
                        hybridCount = hybridCount,
                        incomeCount = incomeCount,
                        hasGeminiKey = hasGeminiKey,
                        regexLearningData = regexLearningData
                    )
                } // withContext(Dispatchers.IO) 끝

                // ===== UI 스레드에서 결과 처리 =====
                val newCount = result.regexCount + result.hybridCount

                // 벡터 DB 배치 학습 (백그라운드, 동기화 시간에 영향 없음)
                if (result.regexLearningData.isNotEmpty() && result.hasGeminiKey) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            hybridSmsClassifier.batchLearnFromRegexResults(result.regexLearningData)
                        } catch (e: Exception) {
                            android.util.Log.w("sanha", "벡터 배치 학습 실패 (무시): ${e.message}")
                        }
                    }
                }

                // 데이터 새로고침
                loadData()

                // 결과 메시지 생성
                val hybridInfo = if (result.hybridCount > 0) " (AI: ${result.hybridCount}건)" else ""
                val resultMessage = when {
                    newCount > 0 && result.incomeCount > 0 -> "${newCount}건의 지출${hybridInfo}, ${result.incomeCount}건의 수입이 추가되었습니다"
                    newCount > 0 -> "${newCount}건의 새 지출이 추가되었습니다${hybridInfo}"
                    result.incomeCount > 0 -> "${result.incomeCount}건의 새 수입이 추가되었습니다"
                    else -> "새로운 내역이 없습니다"
                }

                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        errorMessage = resultMessage
                    )
                }

                // 동기화 완료 후 미분류 항목 확인
                checkUnclassifiedAfterSync()
            } catch (e: Exception) {
                // 예외 시에도 캐시 정리
                categoryClassifierService.clearCategoryCache()

                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        errorMessage = "동기화 실패: ${e.message}"
                    )
                }
            }
        }
    }

    /** Claude API 키 설정 (레거시, 현재 미사용) */
    fun setApiKey(key: String) {
        viewModelScope.launch {
            claudeRepository.setApiKey(key)
        }
    }

    /** Claude API 키 존재 여부 확인 (레거시, 현재 미사용) */
    fun hasApiKey(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            callback(claudeRepository.hasApiKey())
        }
    }

    /**
     * Pull-to-Refresh 및 외부에서 호출 가능한 데이터 새로고침
     * 다른 화면에서 DB 변경 시에도 호출됨
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }

            try {
                val (monthStartDay, year, month) = withContext(Dispatchers.IO) {
                    val msd = settingsDataStore.getMonthStartDay()
                    val (_, endTs) = DateUtils.getCurrentCustomMonthPeriod(msd)
                    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = endTs }
                    Triple(msd, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH) + 1)
                }

                _uiState.update {
                    it.copy(monthStartDay = monthStartDay, selectedYear = year, selectedMonth = month, isRefreshing = false)
                }

                // loadData()가 Flow 기반이므로 새로 시작하면 자동 갱신
                loadData()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, errorMessage = e.message)
                }
            }
        }
    }

    /** 카테고리 필터 선택/해제 */
    fun selectCategory(category: String?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    /** 에러 메시지 초기화 */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 미분류 항목을 Gemini로 일괄 분류
     */
    fun classifyUnclassifiedExpenses(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    categoryClassifierService.classifyUnclassifiedExpenses()
                }
                if (count > 0) {
                    loadData()
                }
                onResult(count)
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "분류 실패: ${e.message}")
                onResult(0)
            }
        }
    }

    /**
     * 미분류 항목 수 조회
     */
    fun getUnclassifiedCount(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                categoryClassifierService.getUnclassifiedCount()
            }
            onResult(count)
        }
    }

    /**
     * 동기화 완료 후 미분류 항목 확인하고 분류 다이얼로그 표시
     */
    fun checkUnclassifiedAfterSync() {
        viewModelScope.launch {
            val (count, hasApiKey) = withContext(Dispatchers.IO) {
                Pair(
                    categoryClassifierService.getUnclassifiedCount(),
                    categoryClassifierService.hasGeminiApiKey()
                )
            }
            if (count > 0 && hasApiKey) {
                _uiState.update {
                    it.copy(showClassifyDialog = true, unclassifiedCount = count)
                }
            }
        }
    }

    /**
     * 분류 다이얼로그 닫기
     */
    fun dismissClassifyDialog() {
        _uiState.update { it.copy(showClassifyDialog = false) }
    }

    /**
     * 미분류 항목 전체 분류 시작 (최대 3라운드)
     */
    fun startFullClassification() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showClassifyDialog = false,
                    isClassifying = true,
                    classifyProgress = "분류 준비 중..."
                )
            }

            try {
                val totalClassified = withContext(Dispatchers.IO) {
                    categoryClassifierService.classifyAllUntilComplete(
                        onProgress = { round, classifiedInRound, remaining ->
                            _uiState.update {
                                it.copy(
                                    classifyProgress = "라운드 $round: ${classifiedInRound}건 분류 완료 (남은 미분류: ${remaining}건)"
                                )
                            }
                        },
                        maxRounds = 3
                    )
                }

                loadData()

                val finalRemaining = withContext(Dispatchers.IO) {
                    categoryClassifierService.getUnclassifiedCount()
                }
                val resultMessage = if (finalRemaining > 0) {
                    "${totalClassified}건 분류 완료. ${finalRemaining}건은 수동 분류가 필요합니다."
                } else {
                    "${totalClassified}건 분류 완료!"
                }

                _uiState.update {
                    it.copy(isClassifying = false, classifyProgress = "", errorMessage = resultMessage)
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "전체 분류 실패: ${e.message}")
                _uiState.update {
                    it.copy(isClassifying = false, classifyProgress = "", errorMessage = "분류 실패: ${e.message}")
                }
            }
        }
    }

    /**
     * Gemini API 키 존재 여부 확인
     */
    fun hasGeminiApiKey(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val hasKey = withContext(Dispatchers.IO) {
                categoryClassifierService.hasGeminiApiKey()
            }
            callback(hasKey)
        }
    }

    /**
     * 특정 지출의 카테고리 변경
     * Room 매핑도 함께 업데이트하여 동일 가게명에 대해 학습
     */
    fun updateExpenseCategory(expenseId: Long, storeName: String, newCategory: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    categoryClassifierService.updateExpenseCategory(expenseId, storeName, newCategory)
                }
                loadData()
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "카테고리 변경 실패: ${e.message}")
            }
        }
    }
}
