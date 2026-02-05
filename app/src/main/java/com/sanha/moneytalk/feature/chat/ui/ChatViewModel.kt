package com.sanha.moneytalk.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.database.dao.ChatDao
import com.sanha.moneytalk.core.database.entity.ChatEntity
import com.sanha.moneytalk.feature.chat.data.GeminiRepository
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.util.DataQuery
import com.sanha.moneytalk.core.util.DateParser
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.QueryResult
import com.sanha.moneytalk.core.util.QueryType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*
import javax.inject.Inject

data class ChatMessage(
    val id: Long = 0,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasApiKey: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val geminiRepository: GeminiRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val chatDao: ChatDao,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    init {
        loadChatHistory()
        checkApiKey()
    }

    private fun loadChatHistory() {
        viewModelScope.launch {
            chatDao.getAllChats()
                .collect { chats ->
                    _uiState.update {
                        it.copy(
                            messages = chats.map { chat ->
                                ChatMessage(
                                    id = chat.id,
                                    content = chat.message,
                                    isUser = chat.isUser,
                                    timestamp = chat.timestamp
                                )
                            }
                        )
                    }
                }
        }
    }

    private fun checkApiKey() {
        viewModelScope.launch {
            _uiState.update { it.copy(hasApiKey = geminiRepository.hasApiKey()) }
        }
    }

    fun sendMessage(message: String) {
        if (message.isBlank()) return

        viewModelScope.launch {
            // 사용자 메시지 저장
            val userChat = ChatEntity(
                message = message,
                isUser = true
            )
            chatDao.insert(userChat)

            _uiState.update { it.copy(isLoading = true) }

            try {
                // 1단계: Gemini에게 필요한 데이터 쿼리 분석 요청
                val analyzeResult = geminiRepository.analyzeQueryNeeds(message)

                val queryResults = mutableListOf<QueryResult>()

                analyzeResult.onSuccess { queryRequest ->
                    if (queryRequest != null && queryRequest.queries.isNotEmpty()) {
                        // 2단계: 요청된 쿼리 실행
                        for (query in queryRequest.queries) {
                            val result = executeQuery(query)
                            if (result != null) {
                                queryResults.add(result)
                            }
                        }
                    } else {
                        // 쿼리 분석 실패 시 기본 데이터 제공 (현재 월)
                        val fallbackResults = getDefaultQueryResults()
                        queryResults.addAll(fallbackResults)
                    }
                }.onFailure {
                    // 쿼리 분석 실패 시 기본 데이터 제공
                    val fallbackResults = getDefaultQueryResults()
                    queryResults.addAll(fallbackResults)
                }

                // 3단계: 쿼리 결과로 최종 답변 생성
                val monthlyIncome = settingsDataStore.getMonthlyIncome()
                val finalResult = geminiRepository.generateFinalAnswer(
                    userMessage = message,
                    queryResults = queryResults,
                    monthlyIncome = monthlyIncome
                )

                finalResult.onSuccess { response ->
                    val aiChat = ChatEntity(
                        message = response,
                        isUser = false
                    )
                    chatDao.insert(aiChat)
                }.onFailure { e ->
                    val errorChat = ChatEntity(
                        message = "죄송해요, 응답을 받는 중 오류가 발생했어요: ${e.message}",
                        isUser = false
                    )
                    chatDao.insert(errorChat)
                }

                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                val errorChat = ChatEntity(
                    message = "오류가 발생했어요: ${e.message}",
                    isUser = false
                )
                chatDao.insert(errorChat)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Gemini가 요청한 쿼리를 실행하여 결과 반환
     */
    private suspend fun executeQuery(query: DataQuery): QueryResult? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

        // 날짜 파싱 (없으면 현재 월 사용)
        val startTimestamp = query.startDate?.let {
            try {
                dateFormat.parse(it)?.time ?: DateUtils.getMonthStartTimestamp()
            } catch (e: Exception) {
                DateUtils.getMonthStartTimestamp()
            }
        } ?: DateUtils.getMonthStartTimestamp()

        val endTimestamp = query.endDate?.let {
            try {
                // 종료일은 해당 일의 끝까지 포함
                (dateFormat.parse(it)?.time ?: DateUtils.getMonthEndTimestamp()) + (24 * 60 * 60 * 1000 - 1)
            } catch (e: Exception) {
                DateUtils.getMonthEndTimestamp()
            }
        } ?: DateUtils.getMonthEndTimestamp()

        return when (query.type) {
            QueryType.TOTAL_EXPENSE -> {
                val total = expenseRepository.getTotalExpenseByDateRange(startTimestamp, endTimestamp)
                QueryResult(
                    queryType = QueryType.TOTAL_EXPENSE,
                    data = "총 지출: ${numberFormat.format(total)}원 (${query.startDate ?: "이번 달"} ~ ${query.endDate ?: "현재"})"
                )
            }

            QueryType.TOTAL_INCOME -> {
                val total = incomeRepository.getTotalIncomeByDateRange(startTimestamp, endTimestamp)
                QueryResult(
                    queryType = QueryType.TOTAL_INCOME,
                    data = "총 수입: ${numberFormat.format(total)}원 (${query.startDate ?: "이번 달"} ~ ${query.endDate ?: "현재"})"
                )
            }

            QueryType.EXPENSE_BY_CATEGORY -> {
                val categoryExpenses = expenseRepository.getExpenseSumByCategory(startTimestamp, endTimestamp)
                val breakdown = categoryExpenses.joinToString("\n") { item ->
                    val category = Category.fromDisplayName(item.category)
                    "${category.emoji} ${category.displayName}: ${numberFormat.format(item.total)}원"
                }.ifEmpty { "해당 기간 지출 내역이 없습니다." }
                QueryResult(
                    queryType = QueryType.EXPENSE_BY_CATEGORY,
                    data = "카테고리별 지출 (${query.startDate ?: "이번 달"} ~ ${query.endDate ?: "현재"}):\n$breakdown"
                )
            }

            QueryType.EXPENSE_LIST -> {
                val limit = query.limit ?: 10
                val expenses = expenseRepository.getExpensesByDateRangeOnce(startTimestamp, endTimestamp)
                    .let { list ->
                        if (query.category != null) {
                            list.filter { it.category == query.category }
                        } else {
                            list
                        }
                    }
                    .take(limit)

                val expenseList = expenses.joinToString("\n") { expense ->
                    "${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${numberFormat.format(expense.amount)}원 (${expense.category})"
                }.ifEmpty { "해당 기간 지출 내역이 없습니다." }

                QueryResult(
                    queryType = QueryType.EXPENSE_LIST,
                    data = "지출 내역 (${query.startDate ?: "이번 달"} ~ ${query.endDate ?: "현재"}):\n$expenseList"
                )
            }

            QueryType.DAILY_TOTALS -> {
                val dailyTotals = expenseRepository.getDailyTotals(startTimestamp, endTimestamp)
                val totalsStr = dailyTotals.joinToString("\n") { daily ->
                    "${daily.date}: ${numberFormat.format(daily.total)}원"
                }.ifEmpty { "해당 기간 일별 지출 내역이 없습니다." }

                QueryResult(
                    queryType = QueryType.DAILY_TOTALS,
                    data = "일별 지출 (${query.startDate ?: "이번 달"} ~ ${query.endDate ?: "현재"}):\n$totalsStr"
                )
            }

            QueryType.MONTHLY_TOTALS -> {
                val monthlyTotals = expenseRepository.getMonthlyTotals()
                val totalsStr = monthlyTotals.joinToString("\n") { monthly ->
                    "${monthly.month}: ${numberFormat.format(monthly.total)}원"
                }.ifEmpty { "월별 지출 내역이 없습니다." }

                QueryResult(
                    queryType = QueryType.MONTHLY_TOTALS,
                    data = "월별 지출:\n$totalsStr"
                )
            }

            QueryType.MONTHLY_INCOME -> {
                val income = settingsDataStore.getMonthlyIncome()
                QueryResult(
                    queryType = QueryType.MONTHLY_INCOME,
                    data = "설정된 월 수입: ${numberFormat.format(income)}원"
                )
            }
        }
    }

    /**
     * 기본 쿼리 결과 (쿼리 분석 실패 시 사용)
     */
    private suspend fun getDefaultQueryResults(): List<QueryResult> {
        val results = mutableListOf<QueryResult>()
        val monthStart = DateUtils.getMonthStartTimestamp()
        val monthEnd = DateUtils.getMonthEndTimestamp()

        // 이번 달 총 지출
        val totalExpense = expenseRepository.getTotalExpenseByDateRange(monthStart, monthEnd)
        results.add(QueryResult(
            queryType = QueryType.TOTAL_EXPENSE,
            data = "이번 달 총 지출: ${numberFormat.format(totalExpense)}원"
        ))

        // 카테고리별 지출
        val categoryExpenses = expenseRepository.getExpenseSumByCategory(monthStart, monthEnd)
        val breakdown = categoryExpenses.joinToString("\n") { item ->
            val category = Category.fromDisplayName(item.category)
            "${category.emoji} ${category.displayName}: ${numberFormat.format(item.total)}원"
        }.ifEmpty { "지출 내역이 없습니다." }
        results.add(QueryResult(
            queryType = QueryType.EXPENSE_BY_CATEGORY,
            data = "이번 달 카테고리별 지출:\n$breakdown"
        ))

        // 최근 지출 10건
        val recentExpenses = expenseRepository.getRecentExpenses(10)
        val expenseList = recentExpenses.joinToString("\n") { expense ->
            "${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${numberFormat.format(expense.amount)}원"
        }.ifEmpty { "최근 지출 내역이 없습니다." }
        results.add(QueryResult(
            queryType = QueryType.EXPENSE_LIST,
            data = "최근 지출 내역:\n$expenseList"
        ))

        return results
    }

    fun setApiKey(key: String) {
        viewModelScope.launch {
            geminiRepository.setApiKey(key)
            checkApiKey()
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            chatDao.deleteAll()
        }
    }
}
