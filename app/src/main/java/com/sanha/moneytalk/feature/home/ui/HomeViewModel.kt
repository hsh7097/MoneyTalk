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
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

    init {
        loadSettings()
    }

    /**
     * 설정 로드 및 초기 데이터 로드
     * 월 시작일 설정을 가져와서 현재 커스텀 월 기간을 계산합니다.
     */
    private fun loadSettings() {
        viewModelScope.launch {
            val monthStartDay = settingsDataStore.getMonthStartDay()

            // 현재 커스텀 월 기간 계산 (예: 25일 시작이면 1/25 ~ 2/24)
            val (startTs, _) = DateUtils.getCurrentCustomMonthPeriod(monthStartDay)
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = startTs }
            val year = calendar.get(java.util.Calendar.YEAR)
            val month = calendar.get(java.util.Calendar.MONTH) + 1

            _uiState.update {
                it.copy(
                    monthStartDay = monthStartDay,
                    selectedYear = year,
                    selectedMonth = month
                )
            }
            loadData()
        }
    }

    /**
     * 홈 화면 데이터 로드
     * 선택된 월의 수입, 지출, 카테고리별 합계, 최근 지출 내역을 조회합니다.
     */
    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val state = _uiState.value
                val (monthStart, monthEnd) = DateUtils.getCustomMonthPeriod(
                    state.selectedYear,
                    state.selectedMonth,
                    state.monthStartDay
                )

                // 기간 레이블 생성
                val periodLabel = DateUtils.formatCustomMonthPeriod(
                    state.selectedYear,
                    state.selectedMonth,
                    state.monthStartDay
                )

                // 이번 기간 지출 합계
                val totalExpense = expenseRepository.getTotalExpenseByDateRange(monthStart, monthEnd)

                // 이번 기간 수입 합계
                val totalIncome = incomeRepository.getTotalIncomeByDateRange(monthStart, monthEnd)

                // 카테고리별 지출
                val categoryExpenses = expenseRepository.getExpenseSumByCategory(monthStart, monthEnd)

                // 해당 기간 지출 내역 (전체)
                val recentExpenses = expenseRepository.getExpensesByDateRangeOnce(monthStart, monthEnd)
                    .sortedByDescending { it.dateTime }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        periodLabel = periodLabel,
                        monthlyIncome = totalIncome,
                        monthlyExpense = totalExpense,
                        remainingBudget = totalIncome - totalExpense,
                        categoryExpenses = categoryExpenses,
                        recentExpenses = recentExpenses
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message
                    )
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
                // 마지막 동기화 시간 가져오기
                val lastSyncTime = if (forceFullSync) 0L else settingsDataStore.getLastSyncTime()
                val currentTime = System.currentTimeMillis()

                android.util.Log.e("sanha", "=== syncSmsMessages (Hybrid) 시작 ===")
                android.util.Log.e("sanha", "forceFullSync: $forceFullSync, lastSyncTime: $lastSyncTime")

                // ===== 1단계: Regex로 빠르게 분류 =====
                val regexSmsList = if (lastSyncTime > 0) {
                    smsReader.readCardSmsByDateRange(contentResolver, lastSyncTime, currentTime)
                } else {
                    smsReader.readAllCardSms(contentResolver)
                }

                var regexCount = 0
                val processedSmsIds = mutableSetOf<String>()
                val hasGeminiKey = settingsDataStore.getGeminiApiKey().isNotBlank()

                for (sms in regexSmsList) {
                    try {
                        if (expenseRepository.existsBySmsId(sms.id)) {
                            processedSmsIds.add(sms.id)
                            continue
                        }

                        // Regex 파싱
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
                            expenseRepository.insert(expense)
                            regexCount++
                            processedSmsIds.add(sms.id)

                            // Regex 성공 결과를 벡터 DB에 학습 (패턴 축적)
                            if (hasGeminiKey) {
                                try {
                                    hybridSmsClassifier.learnFromRegexResult(sms.body, sms.address, analysis)
                                } catch (e: Exception) {
                                    android.util.Log.w("sanha", "벡터 학습 실패 (무시): ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("sanha", "Regex SMS 처리 실패 (무시): ${sms.id} - ${e.message}")
                    }
                }

                android.util.Log.e("sanha", "Tier 1 (Regex): ${regexCount}건 처리")

                // ===== 2~3단계: Regex 미스 SMS를 벡터+LLM으로 재분류 =====
                var hybridCount = 0

                if (hasGeminiKey) {
                    // 기간 내 모든 SMS를 가져와서 미처리분 추출
                    val allSms = if (lastSyncTime > 0) {
                        smsReader.readAllSmsByDateRange(contentResolver, lastSyncTime, currentTime)
                    } else {
                        // 전체 동기화: 최근 1년으로 제한 (너무 오래된 데이터 제외)
                        val oneYearAgo = currentTime - (365L * 24 * 60 * 60 * 1000)
                        smsReader.readAllSmsByDateRange(contentResolver, oneYearAgo, currentTime)
                    }

                    // 미처리 SMS 필터링
                    val unclassifiedSms = allSms.filter { sms ->
                        sms.id !in processedSmsIds &&
                        !expenseRepository.existsBySmsId(sms.id) &&
                        !SmsParser.isIncomeSms(sms.body) &&
                        sms.body.length >= 10
                    }

                    android.util.Log.e("sanha", "미분류 SMS: ${unclassifiedSms.size}건")

                    if (unclassifiedSms.isNotEmpty()) {
                        if (forceFullSync || unclassifiedSms.size > 50) {
                            // === 대량 처리: 배치 프로세서 사용 (그룹핑 + 샘플 검증) ===
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
                                    if (analysis.amount > 0 && !expenseRepository.existsBySmsId(smsData.id)) {
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
                                        expenseRepository.insert(expense)
                                        hybridCount++
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("sanha", "배치 결과 저장 실패 (무시): ${e.message}")
                                }
                            }
                        } else {
                            // === 소량 처리: 개별 하이브리드 분류 ===
                            for (sms in unclassifiedSms) {
                                try {
                                    val result = hybridSmsClassifier.classify(sms.body, sms.date, sms.address)

                                    if (result.isPayment && result.analysisResult != null) {
                                        val analysis = result.analysisResult
                                        if (analysis.amount > 0) {
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
                                            expenseRepository.insert(expense)
                                            hybridCount++
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.w("sanha", "개별 SMS 하이브리드 분류 실패 (무시): ${e.message}")
                                }
                            }
                        }
                    }
                }

                android.util.Log.e("sanha", "Tier 2~3 (Hybrid): ${hybridCount}건 추가 발견")
                android.util.Log.e("sanha", "=== 동기화 결과 요약 ===")
                android.util.Log.e("sanha", "Regex: ${regexCount}건, Hybrid: ${hybridCount}건, 처리된 SMS ID: ${processedSmsIds.size}건")

                val newCount = regexCount + hybridCount

                // ===== 수입 SMS 동기화 (기존 로직 유지) =====
                var incomeCount = 0
                val incomeSmsList = if (lastSyncTime > 0) {
                    smsReader.readIncomeSmsByDateRange(contentResolver, lastSyncTime, currentTime)
                } else {
                    smsReader.readAllIncomeSms(contentResolver)
                }

                for (sms in incomeSmsList) {
                    try {
                        if (incomeRepository.existsBySmsId(sms.id)) {
                            continue
                        }

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
                            incomeRepository.insert(income)
                            incomeCount++
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("sanha", "수입 SMS 처리 실패 (무시): ${sms.id} - ${e.message}")
                    }
                }

                // 마지막 동기화 시간 저장
                settingsDataStore.saveLastSyncTime(currentTime)

                // 오래된 패턴 정리
                hybridSmsClassifier.cleanupStalePatterns()

                // 데이터 새로고침
                loadData()

                // 결과 메시지 생성
                val hybridInfo = if (hybridCount > 0) " (AI: ${hybridCount}건)" else ""
                val resultMessage = when {
                    newCount > 0 && incomeCount > 0 -> "${newCount}건의 지출${hybridInfo}, ${incomeCount}건의 수입이 추가되었습니다"
                    newCount > 0 -> "${newCount}건의 새 지출이 추가되었습니다${hybridInfo}"
                    incomeCount > 0 -> "${incomeCount}건의 새 수입이 추가되었습니다"
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
            loadDataSync()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * 데이터 동기 로드 (refresh에서 사용)
     */
    private suspend fun loadDataSync() {
        try {
            val monthStartDay = settingsDataStore.getMonthStartDay()
            val (startTs, _) = DateUtils.getCurrentCustomMonthPeriod(monthStartDay)
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = startTs }
            val year = calendar.get(java.util.Calendar.YEAR)
            val month = calendar.get(java.util.Calendar.MONTH) + 1

            _uiState.update {
                it.copy(
                    monthStartDay = monthStartDay,
                    selectedYear = year,
                    selectedMonth = month
                )
            }

            val state = _uiState.value
            val (monthStart, monthEnd) = DateUtils.getCustomMonthPeriod(
                state.selectedYear,
                state.selectedMonth,
                state.monthStartDay
            )

            val periodLabel = DateUtils.formatCustomMonthPeriod(
                state.selectedYear,
                state.selectedMonth,
                state.monthStartDay
            )

            val totalExpense = expenseRepository.getTotalExpenseByDateRange(monthStart, monthEnd)
            val totalIncome = incomeRepository.getTotalIncomeByDateRange(monthStart, monthEnd)
            val categoryExpenses = expenseRepository.getExpenseSumByCategory(monthStart, monthEnd)
            val recentExpenses = expenseRepository.getExpensesByDateRangeOnce(monthStart, monthEnd)
                .sortedByDescending { it.dateTime }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    periodLabel = periodLabel,
                    monthlyIncome = totalIncome,
                    monthlyExpense = totalExpense,
                    remainingBudget = totalIncome - totalExpense,
                    categoryExpenses = categoryExpenses,
                    recentExpenses = recentExpenses
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isLoading = false, errorMessage = e.message)
            }
        }
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
                val count = categoryClassifierService.classifyUnclassifiedExpenses()
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
            val count = categoryClassifierService.getUnclassifiedCount()
            onResult(count)
        }
    }

    /**
     * 동기화 완료 후 미분류 항목 확인하고 분류 다이얼로그 표시
     */
    fun checkUnclassifiedAfterSync() {
        viewModelScope.launch {
            val count = categoryClassifierService.getUnclassifiedCount()
            val hasApiKey = categoryClassifierService.hasGeminiApiKey()

            if (count > 0 && hasApiKey) {
                _uiState.update {
                    it.copy(
                        showClassifyDialog = true,
                        unclassifiedCount = count
                    )
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
                val totalClassified = categoryClassifierService.classifyAllUntilComplete(
                    onProgress = { round, classifiedInRound, remaining ->
                        _uiState.update {
                            it.copy(
                                classifyProgress = "라운드 $round: ${classifiedInRound}건 분류 완료 (남은 미분류: ${remaining}건)"
                            )
                        }
                    },
                    maxRounds = 3  // 최대 3라운드로 제한
                )

                // 분류 완료 후 데이터 새로고침
                loadData()

                val finalRemaining = categoryClassifierService.getUnclassifiedCount()
                val resultMessage = if (finalRemaining > 0) {
                    "${totalClassified}건 분류 완료. ${finalRemaining}건은 수동 분류가 필요합니다."
                } else {
                    "${totalClassified}건 분류 완료!"
                }

                _uiState.update {
                    it.copy(
                        isClassifying = false,
                        classifyProgress = "",
                        errorMessage = resultMessage
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "전체 분류 실패: ${e.message}")
                _uiState.update {
                    it.copy(
                        isClassifying = false,
                        classifyProgress = "",
                        errorMessage = "분류 실패: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Gemini API 키 존재 여부 확인
     */
    fun hasGeminiApiKey(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            callback(categoryClassifierService.hasGeminiApiKey())
        }
    }

    /**
     * 특정 지출의 카테고리 변경
     * Room 매핑도 함께 업데이트하여 동일 가게명에 대해 학습
     */
    fun updateExpenseCategory(expenseId: Long, storeName: String, newCategory: String) {
        viewModelScope.launch {
            try {
                categoryClassifierService.updateExpenseCategory(expenseId, storeName, newCategory)
                loadData() // 화면 새로고침
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "카테고리 변경 실패: ${e.message}")
            }
        }
    }
}
