package com.sanha.moneytalk.feature.home.ui

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.database.dao.CategorySum
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.feature.chat.data.ClaudeRepository
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.SmsParser
import com.sanha.moneytalk.core.util.SmsReader
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
    val isSyncing: Boolean = false
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
    private val settingsDataStore: SettingsDataStore
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
     * SMS 메시지 동기화
     *
     * 카드 결제 문자를 읽어서 지출 내역으로 변환합니다.
     * 로컬 정규식 파싱을 사용하며, 카테고리는 Room DB 매핑 또는 로컬 키워드 매칭으로 결정됩니다.
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

                android.util.Log.e("sanha", "=== syncSmsMessages 시작 ===")
                android.util.Log.e("sanha", "forceFullSync: $forceFullSync, lastSyncTime: $lastSyncTime")
                android.util.Log.e("sanha", "lastSyncTime 날짜: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.KOREA).format(java.util.Date(lastSyncTime))}")

                // 마지막 동기화 이후의 SMS만 가져오기 (증분 동기화)
                val smsList = if (lastSyncTime > 0) {
                    android.util.Log.e("sanha", "증분 동기화 모드")
                    smsReader.readCardSmsByDateRange(contentResolver, lastSyncTime, currentTime)
                } else {
                    android.util.Log.e("sanha", "전체 동기화 모드")
                    smsReader.readAllCardSms(contentResolver)
                }

                var newCount = 0

                for (sms in smsList) {
                    // 이미 처리된 문자인지 확인
                    if (expenseRepository.existsBySmsId(sms.id)) {
                        continue
                    }

                    // 로컬 정규식으로 파싱 (API 호출 없음)
                    val analysis = SmsParser.parseSms(sms.body, sms.date)

                    // 금액이 0보다 큰 경우에만 저장
                    if (analysis.amount > 0) {
                        // Room DB에서 카테고리 조회 (저장된 매핑 우선 사용)
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
                        newCount++
                    }
                }

                // 마지막 동기화 시간 저장
                settingsDataStore.saveLastSyncTime(currentTime)

                // 데이터 새로고침
                loadData()

                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        errorMessage = if (newCount > 0) "${newCount}건의 새 지출이 추가되었습니다" else "새로운 지출이 없습니다"
                    )
                }
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
