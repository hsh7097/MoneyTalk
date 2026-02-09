package com.sanha.moneytalk.feature.chat.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.database.dao.ChatDao
import com.sanha.moneytalk.core.database.entity.ChatEntity
import com.sanha.moneytalk.core.database.entity.ChatSessionEntity
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.feature.chat.data.ChatContext
import com.sanha.moneytalk.feature.chat.data.ChatRepository
import com.sanha.moneytalk.feature.chat.data.GeminiRepository
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.util.ActionResult
import com.sanha.moneytalk.core.util.ActionType
import com.sanha.moneytalk.core.util.ChatContextBuilder
import com.sanha.moneytalk.core.util.DataAction
import com.sanha.moneytalk.core.util.DataQuery
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.QueryResult
import com.sanha.moneytalk.core.util.QueryType
import com.sanha.moneytalk.core.util.StoreAliasManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.*
import javax.inject.Inject

data class ChatMessage(
    val id: Long = 0,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatSession(
    val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int = 0
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: Long? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasApiKey: Boolean = false,
    val showSessionList: Boolean = false,
    val canRetry: Boolean = false,
    /** 채팅방 내부 화면 표시 여부 (false=목록, true=채팅방 내부) */
    val isInChatRoom: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val geminiRepository: GeminiRepository,
    private val chatRepository: ChatRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val chatDao: ChatDao,
    private val settingsDataStore: SettingsDataStore,
    private val smsExclusionRepository: com.sanha.moneytalk.core.database.SmsExclusionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    /** sendMessage 동시 호출 방지용 Mutex */
    private val sendMutex = Mutex()

    /** 재시도를 위한 마지막 사용자 메시지 저장 */
    private var lastUserMessage: String? = null

    init {
        loadSessions()
        checkApiKey()
        autoCreateSessionIfEmpty()
    }

    /**
     * 채팅방이 하나도 없으면 자동으로 하나 생성하고 바로 진입
     */
    private fun autoCreateSessionIfEmpty() {
        viewModelScope.launch {
            val sessions = withContext(Dispatchers.IO) {
                chatDao.getAllSessionsOnce()
            }
            if (sessions.isEmpty()) {
                val sessionId = withContext(Dispatchers.IO) {
                    val newSession = ChatSessionEntity(
                        title = "새 대화",
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    chatDao.insertSession(newSession)
                }
                _uiState.update { it.copy(currentSessionId = sessionId, isInChatRoom = true) }
                loadMessagesForSession(sessionId)
            }
        }
    }

    /** 채팅방 내부로 진입 */
    fun enterChatRoom(sessionId: Long) {
        _uiState.update { it.copy(currentSessionId = sessionId, isInChatRoom = true) }
        loadMessagesForSession(sessionId)
    }

    /** 채팅방에서 목록으로 나가기 (대화 기반 자동 타이틀 설정) */
    fun exitChatRoom() {
        val sessionId = _uiState.value.currentSessionId
        val messages = _uiState.value.messages
        _uiState.update { it.copy(isInChatRoom = false) }

        // 대화가 있으면 자동 타이틀 생성 시도 (비동기, fire-and-forget)
        if (sessionId != null && messages.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // 최근 메시지 6개(사용자+AI 3쌍)를 타이틀 생성에 사용
                    val recentMessages = messages.takeLast(6).joinToString("\n") { msg ->
                        if (msg.isUser) "사용자: ${msg.content}" else "AI: ${msg.content.take(100)}"
                    }
                    val newTitle = geminiRepository.generateChatTitle(recentMessages)
                    if (newTitle != null) {
                        chatDao.updateSessionTitle(sessionId, newTitle)
                    } else {
                        // LLM이 null 반환 시 첫 사용자 메시지로 폴백
                        val fallbackTitle = messages.firstOrNull { it.isUser }?.content?.take(30) ?: "대화"
                        chatDao.updateSessionTitle(sessionId, fallbackTitle)
                    }
                } catch (e: Exception) {
                    // 타이틀 생성 실패 시 첫 사용자 메시지로 폴백
                    Log.w("ChatViewModel", "자동 타이틀 생성 실패, 폴백 적용: ${e.message}")
                    try {
                        val fallbackTitle = messages.firstOrNull { it.isUser }?.content?.take(30) ?: "대화"
                        chatDao.updateSessionTitle(sessionId, fallbackTitle)
                    } catch (inner: Exception) {
                        Log.e("ChatViewModel", "폴백 타이틀 저장도 실패: ${inner.message}")
                    }
                }
            }
        }
    }

    private fun loadSessions() {
        viewModelScope.launch {
            chatDao.getAllSessions()
                .collect { sessions ->
                    val sessionList = sessions.map { session ->
                        ChatSession(
                            id = session.id,
                            title = session.title,
                            createdAt = session.createdAt,
                            updatedAt = session.updatedAt
                        )
                    }

                    val currentId = _uiState.value.currentSessionId
                    val validCurrentId = if (currentId != null && sessionList.any { it.id == currentId }) {
                        currentId
                    } else {
                        sessionList.firstOrNull()?.id
                    }

                    _uiState.update {
                        it.copy(
                            sessions = sessionList,
                            currentSessionId = validCurrentId
                        )
                    }

                    // 현재 세션의 메시지 로드
                    validCurrentId?.let { loadMessagesForSession(it) }
                }
        }
    }

    private fun loadMessagesForSession(sessionId: Long) {
        viewModelScope.launch {
            chatDao.getChatsBySession(sessionId)
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

    fun selectSession(sessionId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(currentSessionId = sessionId, showSessionList = false, isInChatRoom = true) }
            loadMessagesForSession(sessionId)
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            val sessionId = withContext(Dispatchers.IO) {
                val newSession = ChatSessionEntity(
                    title = "새 대화",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                chatDao.insertSession(newSession)
            }
            _uiState.update { it.copy(currentSessionId = sessionId, showSessionList = false, isInChatRoom = true) }
            loadMessagesForSession(sessionId)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.deleteSessionById(sessionId)
            // 삭제 후 다른 세션 선택 (loadSessions에서 자동 처리)
        }
    }

    fun toggleSessionList() {
        _uiState.update { it.copy(showSessionList = !it.showSessionList) }
    }

    fun hideSessionList() {
        _uiState.update { it.copy(showSessionList = false) }
    }

    private fun checkApiKey() {
        viewModelScope.launch {
            val hasKey = withContext(Dispatchers.IO) { geminiRepository.hasApiKey() }
            _uiState.update { it.copy(hasApiKey = hasKey) }
        }
    }

    fun sendMessage(message: String) {
        if (message.isBlank()) return
        if (sendMutex.isLocked) return  // 이미 처리 중이면 무시

        lastUserMessage = message
        _uiState.update { it.copy(canRetry = false) }

        viewModelScope.launch {
            sendMutex.withLock {
            // 현재 세션 ID 확인, 없으면 새 세션 생성
            var sessionId = _uiState.value.currentSessionId
            if (sessionId == null) {
                sessionId = withContext(Dispatchers.IO) {
                    val newSession = ChatSessionEntity(
                        title = message.take(30) + if (message.length > 30) "..." else "",
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    chatDao.insertSession(newSession)
                }
                _uiState.update { it.copy(currentSessionId = sessionId) }
            } else {
                // 첫 메시지면 세션 제목 업데이트
                withContext(Dispatchers.IO) {
                    val messageCount = chatDao.getMessageCountBySession(sessionId)
                    if (messageCount == 0) {
                        val title = message.take(30) + if (message.length > 30) "..." else ""
                        chatDao.updateSessionTitle(sessionId, title)
                    }
                }
            }

            _uiState.update { it.copy(isLoading = true) }

            try {
                // ===== Rolling Summary + Windowed Context 전략 적용 =====
                // 모든 DB/API 작업을 IO 스레드에서 실행
                withContext(Dispatchers.IO) {
                    // 1단계: 메시지 저장 + 요약 갱신 + 컨텍스트 구성
                    val chatContext = chatRepository.sendMessageAndBuildContext(
                        sessionId = sessionId,
                        userMessage = message
                    )

                    // 2단계: 대화 맥락을 포함하여 쿼리 분석 요청
                    val contextualMessage = ChatContextBuilder.buildQueryAnalysisContext(chatContext)
                    val analyzeResult = geminiRepository.analyzeQueryNeeds(contextualMessage)

                    val queryResults = mutableListOf<QueryResult>()
                    val actionResults = mutableListOf<ActionResult>()

                    analyzeResult.onSuccess { queryRequest ->
                        if (queryRequest != null) {
                            // 3단계: 요청된 쿼리 실행
                            if (queryRequest.queries.isNotEmpty()) {
                                for (query in queryRequest.queries) {
                                    val result = executeQuery(query)
                                    if (result != null) {
                                        queryResults.add(result)
                                    }
                                }
                            }

                            // 4단계: 요청된 액션 실행
                            if (queryRequest.actions.isNotEmpty()) {
                                for (action in queryRequest.actions) {
                                    val result = executeAction(action)
                                    actionResults.add(result)
                                }
                            }

                            // 쿼리/액션 모두 없으면 기본 데이터 제공
                            if (queryRequest.queries.isEmpty() && queryRequest.actions.isEmpty()) {
                                val fallbackResults = getDefaultQueryResults()
                                queryResults.addAll(fallbackResults)
                            }
                        } else {
                            val fallbackResults = getDefaultQueryResults()
                            queryResults.addAll(fallbackResults)
                        }
                    }.onFailure {
                        val fallbackResults = getDefaultQueryResults()
                        queryResults.addAll(fallbackResults)
                    }

                    // 5단계: 대화 맥락 + 쿼리 결과로 최종 답변 생성
                    val monthlyIncome = settingsDataStore.getMonthlyIncome()

                    val dataContext = queryResults.joinToString("\n\n") { result ->
                        "[${result.queryType.name}]\n${result.data}"
                    }
                    val actionContext = actionResults.joinToString("\n") { "- ${it.message}" }

                    val finalPrompt = ChatContextBuilder.buildFinalAnswerPrompt(
                        context = chatContext,
                        queryResults = dataContext,
                        monthlyIncome = monthlyIncome,
                        actionResults = actionContext
                    )

                    val finalResult = geminiRepository.generateFinalAnswerWithContext(finalPrompt)

                    finalResult.onSuccess { response ->
                        // AI 응답 저장 + 요약 갱신
                        chatRepository.saveAiResponseAndUpdateSummary(sessionId, response)
                    }.onFailure { e ->
                        chatRepository.saveAiResponseAndUpdateSummary(
                            sessionId,
                            "죄송해요, 응답을 받는 중 오류가 발생했어요 😢\n(${e.message})"
                        )
                        _uiState.update { it.copy(canRetry = true) }
                    }
                }

                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                withContext(Dispatchers.IO) {
                    chatRepository.saveAiResponseAndUpdateSummary(
                        sessionId,
                        "오류가 발생했어요 😢\n(${e.message})"
                    )
                }
                _uiState.update { it.copy(isLoading = false, canRetry = true) }
            }
            } // sendMutex.withLock
        }
    }

    /**
     * Gemini가 요청한 쿼리를 실행하여 결과 반환
     */
    private suspend fun executeQuery(query: DataQuery): QueryResult? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

        // 날짜 파싱 (없으면 전체 기간 조회)
        val startTimestamp = query.startDate?.let {
            try {
                dateFormat.parse(it)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        } ?: 0L

        val endTimestamp = query.endDate?.let {
            try {
                // 종료일은 해당 일의 끝까지 포함
                (dateFormat.parse(it)?.time ?: System.currentTimeMillis()) + (24 * 60 * 60 * 1000 - 1)
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        } ?: System.currentTimeMillis()

        return when (query.type) {
            QueryType.TOTAL_EXPENSE -> {
                val total = if (query.category != null) {
                    // 카테고리 필터가 있으면 DB에서 직접 해당 카테고리(+소 카테고리)만 합산
                    val cat = Category.fromDisplayName(query.category)
                    val categoryNames = cat.displayNamesIncludingSub
                    expenseRepository.getTotalExpenseByCategoriesAndDateRange(categoryNames, startTimestamp, endTimestamp)
                } else {
                    expenseRepository.getTotalExpenseByDateRange(startTimestamp, endTimestamp)
                }
                val categoryLabel = query.category?.let { " ($it)" } ?: ""
                QueryResult(
                    queryType = QueryType.TOTAL_EXPENSE,
                    data = "총 지출$categoryLabel: ${numberFormat.format(total)}원 (${query.startDate ?: "전체"} ~ ${query.endDate ?: "현재"})"
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
                    .let { list ->
                        if (query.category != null) {
                            // 특정 카테고리(+소 카테고리) 필터
                            val cat = Category.fromDisplayName(query.category)
                            val categoryNames = cat.displayNamesIncludingSub
                            list.filter { it.category in categoryNames }
                        } else {
                            list
                        }
                    }
                val breakdown = categoryExpenses.joinToString("\n") { item ->
                    val category = Category.fromDisplayName(item.category)
                    "${category.emoji} ${category.displayName}: ${numberFormat.format(item.total)}원"
                }.ifEmpty { "해당 기간 지출 내역이 없습니다." }
                val categoryLabel = query.category?.let { " ($it)" } ?: ""
                QueryResult(
                    queryType = QueryType.EXPENSE_BY_CATEGORY,
                    data = "카테고리별 지출$categoryLabel (${query.startDate ?: "전체"} ~ ${query.endDate ?: "현재"}):\n$breakdown"
                )
            }

            QueryType.EXPENSE_LIST -> {
                val limit = query.limit ?: 50
                val expenses = if (query.category != null) {
                    // DB에서 직접 카테고리(+소 카테고리) 필터링
                    val cat = Category.fromDisplayName(query.category)
                    val categoryNames = cat.displayNamesIncludingSub
                    expenseRepository.getExpensesByCategoriesAndDateRangeOnce(categoryNames, startTimestamp, endTimestamp)
                } else {
                    expenseRepository.getExpensesByDateRangeOnce(startTimestamp, endTimestamp)
                }.take(limit)

                val expenseList = expenses.joinToString("\n") { expense ->
                    "${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${numberFormat.format(expense.amount)}원 (${expense.category})${expense.memo?.let { " [메모: $it]" } ?: ""}"
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

            QueryType.EXPENSE_BY_STORE -> {
                val storeName = query.storeName ?: return null

                // StoreAliasManager를 사용하여 모든 별칭으로 검색
                val aliases = StoreAliasManager.getAllAliases(storeName)
                val allExpenses = aliases.flatMap { alias ->
                    expenseRepository.getExpensesByStoreNameContaining(alias)
                        .filter { it.dateTime in startTimestamp..endTimestamp }
                }.distinctBy { it.id }
                    .sortedByDescending { it.dateTime }

                val total = allExpenses.sumOf { it.amount }
                val expenseList = allExpenses.take(10).joinToString("\n") { expense ->
                    "${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${numberFormat.format(expense.amount)}원"
                }.ifEmpty { "해당 가게 지출 내역이 없습니다." }

                val aliasInfo = if (aliases.size > 1) " (${aliases.joinToString(", ")})" else ""

                QueryResult(
                    queryType = QueryType.EXPENSE_BY_STORE,
                    data = "'$storeName'$aliasInfo 지출 (${query.startDate ?: "이번 달"} ~ ${query.endDate ?: "현재"}):\n총 ${numberFormat.format(total)}원 (${allExpenses.size}건)\n$expenseList"
                )
            }

            QueryType.UNCATEGORIZED_LIST -> {
                val limit = query.limit ?: 20
                val expenses = expenseRepository.getUncategorizedExpenses(limit)
                val expenseList = expenses.joinToString("\n") { expense ->
                    "[ID:${expense.id}] ${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${numberFormat.format(expense.amount)}원"
                }.ifEmpty { "미분류 항목이 없습니다." }

                QueryResult(
                    queryType = QueryType.UNCATEGORIZED_LIST,
                    data = "미분류 항목 (${expenses.size}건):\n$expenseList"
                )
            }

            QueryType.CATEGORY_RATIO -> {
                val monthlyIncome = settingsDataStore.getMonthlyIncome()
                val categoryExpenses = expenseRepository.getExpenseSumByCategory(startTimestamp, endTimestamp)
                val totalExpense = categoryExpenses.sumOf { it.total }

                val ratioBreakdown = categoryExpenses.joinToString("\n") { item ->
                    val category = Category.fromDisplayName(item.category)
                    val incomeRatio = if (monthlyIncome > 0) (item.total * 100.0 / monthlyIncome) else 0.0
                    val expenseRatio = if (totalExpense > 0) (item.total * 100.0 / totalExpense) else 0.0
                    "${category.emoji} ${category.displayName}: ${numberFormat.format(item.total)}원 (수입의 ${String.format("%.1f", incomeRatio)}%, 지출의 ${String.format("%.1f", expenseRatio)}%)"
                }.ifEmpty { "해당 기간 지출 내역이 없습니다." }

                val totalIncomeRatio = if (monthlyIncome > 0) (totalExpense * 100.0 / monthlyIncome) else 0.0

                QueryResult(
                    queryType = QueryType.CATEGORY_RATIO,
                    data = "수입 대비 카테고리별 비율 (${query.startDate ?: "이번 달"} ~ ${query.endDate ?: "현재"}):\n월 수입: ${numberFormat.format(monthlyIncome)}원\n총 지출: ${numberFormat.format(totalExpense)}원 (수입의 ${String.format("%.1f", totalIncomeRatio)}%)\n\n$ratioBreakdown"
                )
            }

            QueryType.EXPENSE_BY_CARD -> {
                val cardName = query.cardName ?: query.storeName ?: return null
                val allExpenses = expenseRepository.getExpensesByDateRangeOnce(startTimestamp, endTimestamp)
                    .filter { it.cardName.contains(cardName, ignoreCase = true) }
                    .sortedByDescending { it.dateTime }

                val total = allExpenses.sumOf { it.amount }
                val limit = query.limit ?: 20
                val expenseList = allExpenses.take(limit).joinToString("\n") { expense ->
                    "${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${numberFormat.format(expense.amount)}원 (${expense.category})${expense.memo?.let { " [메모: $it]" } ?: ""}"
                }.ifEmpty { "해당 카드 지출 내역이 없습니다." }

                QueryResult(
                    queryType = QueryType.EXPENSE_BY_CARD,
                    data = "'$cardName' 카드 지출 (${query.startDate ?: "전체"} ~ ${query.endDate ?: "현재"}):\n총 ${numberFormat.format(total)}원 (${allExpenses.size}건)\n$expenseList"
                )
            }

            QueryType.SEARCH_EXPENSE -> {
                val keyword = query.searchKeyword ?: query.storeName ?: return null
                val limit = query.limit ?: 30
                val results = expenseRepository.searchExpenses(keyword).take(limit)
                val resultList = results.joinToString("\n") { expense ->
                    "[ID:${expense.id}] ${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${numberFormat.format(expense.amount)}원 (${expense.category}, ${expense.cardName})${expense.memo?.let { " [메모: $it]" } ?: ""}"
                }.ifEmpty { "'$keyword' 검색 결과가 없습니다." }

                QueryResult(
                    queryType = QueryType.SEARCH_EXPENSE,
                    data = "'$keyword' 검색 결과 (${results.size}건):\n$resultList"
                )
            }

            QueryType.CARD_LIST -> {
                val cardNames = expenseRepository.getAllCardNames()
                val cardList = cardNames.joinToString(", ").ifEmpty { "등록된 카드가 없습니다." }

                QueryResult(
                    queryType = QueryType.CARD_LIST,
                    data = "사용 중인 카드 목록 (${cardNames.size}개): $cardList"
                )
            }

            QueryType.INCOME_LIST -> {
                val limit = query.limit ?: 20
                val incomes = incomeRepository.getIncomesByDateRangeOnce(startTimestamp, endTimestamp)
                    .take(limit)
                val total = incomes.sumOf { it.amount }
                val incomeList = incomes.joinToString("\n") { income ->
                    "${DateUtils.formatDateTime(income.dateTime)} - ${income.source}: ${numberFormat.format(income.amount)}원 (${income.type})${income.memo?.let { " [메모: $it]" } ?: ""}"
                }.ifEmpty { "해당 기간 수입 내역이 없습니다." }

                QueryResult(
                    queryType = QueryType.INCOME_LIST,
                    data = "수입 내역 (${query.startDate ?: "전체"} ~ ${query.endDate ?: "현재"}):\n총 ${numberFormat.format(total)}원 (${incomes.size}건)\n$incomeList"
                )
            }

            QueryType.DUPLICATE_LIST -> {
                val duplicates = expenseRepository.getDuplicateExpenses()
                val dupList = duplicates.take(20).joinToString("\n") { expense ->
                    "[ID:${expense.id}] ${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${numberFormat.format(expense.amount)}원 (${expense.category})"
                }.ifEmpty { "중복 항목이 없습니다." }

                QueryResult(
                    queryType = QueryType.DUPLICATE_LIST,
                    data = "중복 지출 항목 (${duplicates.size}건):\n$dupList"
                )
            }

            QueryType.SMS_EXCLUSION_LIST -> {
                val allKeywords = smsExclusionRepository.getAllKeywords()
                val keywordList = if (allKeywords.isEmpty()) {
                    "등록된 제외 키워드가 없습니다."
                } else {
                    allKeywords.joinToString("\n") { entity ->
                        val sourceLabel = when (entity.source) {
                            "default" -> "(기본)"
                            "chat" -> "(채팅)"
                            else -> "(사용자)"
                        }
                        "- ${entity.keyword} $sourceLabel"
                    }
                }

                QueryResult(
                    queryType = QueryType.SMS_EXCLUSION_LIST,
                    data = "SMS 제외 키워드 목록 (${allKeywords.size}건):\n$keywordList"
                )
            }
        }
    }

    /**
     * Gemini가 요청한 액션을 실행
     */
    private suspend fun executeAction(action: DataAction): ActionResult {
        return when (action.type) {
            ActionType.UPDATE_CATEGORY -> {
                val expenseId = action.expenseId
                val newCategory = action.newCategory

                if (expenseId == null || newCategory == null) {
                    ActionResult(
                        actionType = ActionType.UPDATE_CATEGORY,
                        success = false,
                        message = "지출 ID 또는 새 카테고리가 지정되지 않았습니다."
                    )
                } else {
                    val affected = expenseRepository.updateCategoryById(expenseId, newCategory)
                    ActionResult(
                        actionType = ActionType.UPDATE_CATEGORY,
                        success = affected > 0,
                        message = if (affected > 0) "ID $expenseId 항목의 카테고리를 '$newCategory'(으)로 변경했습니다." else "해당 항목을 찾을 수 없습니다.",
                        affectedCount = affected
                    )
                }
            }

            ActionType.UPDATE_CATEGORY_BY_STORE -> {
                val storeName = action.storeName
                val newCategory = action.newCategory

                if (storeName == null || newCategory == null) {
                    ActionResult(
                        actionType = ActionType.UPDATE_CATEGORY_BY_STORE,
                        success = false,
                        message = "가게명 또는 새 카테고리가 지정되지 않았습니다."
                    )
                } else {
                    // StoreAliasManager를 사용하여 모든 별칭에 대해 업데이트
                    val aliases = StoreAliasManager.getAllAliases(storeName)
                    var totalAffected = 0
                    for (alias in aliases) {
                        totalAffected += expenseRepository.updateCategoryByStoreNameContaining(alias, newCategory)
                    }
                    ActionResult(
                        actionType = ActionType.UPDATE_CATEGORY_BY_STORE,
                        success = totalAffected > 0,
                        message = if (totalAffected > 0) "'$storeName' 관련 ${totalAffected}건의 카테고리를 '$newCategory'(으)로 변경했습니다." else "'$storeName' 관련 항목을 찾을 수 없습니다.",
                        affectedCount = totalAffected
                    )
                }
            }

            ActionType.UPDATE_CATEGORY_BY_KEYWORD -> {
                val keyword = action.searchKeyword
                val newCategory = action.newCategory

                if (keyword == null || newCategory == null) {
                    ActionResult(
                        actionType = ActionType.UPDATE_CATEGORY_BY_KEYWORD,
                        success = false,
                        message = "검색 키워드 또는 새 카테고리가 지정되지 않았습니다."
                    )
                } else {
                    // StoreAliasManager를 사용하여 모든 별칭에 대해 업데이트
                    val aliases = StoreAliasManager.getAllAliases(keyword)
                    var totalAffected = 0
                    for (alias in aliases) {
                        totalAffected += expenseRepository.updateCategoryByStoreNameContaining(alias, newCategory)
                    }
                    ActionResult(
                        actionType = ActionType.UPDATE_CATEGORY_BY_KEYWORD,
                        success = totalAffected > 0,
                        message = if (totalAffected > 0) "'$keyword' 관련 ${totalAffected}건의 카테고리를 '$newCategory'(으)로 변경했습니다." else "'$keyword' 관련 항목을 찾을 수 없습니다.",
                        affectedCount = totalAffected
                    )
                }
            }

            ActionType.DELETE_EXPENSE -> {
                val expenseId = action.expenseId

                if (expenseId == null) {
                    ActionResult(
                        actionType = ActionType.DELETE_EXPENSE,
                        success = false,
                        message = "삭제할 지출 ID가 지정되지 않았습니다."
                    )
                } else {
                    val expense = expenseRepository.getExpenseById(expenseId)
                    if (expense != null) {
                        expenseRepository.deleteById(expenseId)
                        ActionResult(
                            actionType = ActionType.DELETE_EXPENSE,
                            success = true,
                            message = "ID $expenseId 항목 (${expense.storeName}: ${numberFormat.format(expense.amount)}원)을 삭제했습니다.",
                            affectedCount = 1
                        )
                    } else {
                        ActionResult(
                            actionType = ActionType.DELETE_EXPENSE,
                            success = false,
                            message = "ID $expenseId 항목을 찾을 수 없습니다."
                        )
                    }
                }
            }

            ActionType.DELETE_BY_KEYWORD -> {
                val keyword = action.searchKeyword
                if (keyword.isNullOrBlank()) {
                    ActionResult(
                        actionType = ActionType.DELETE_BY_KEYWORD,
                        success = false,
                        message = "삭제할 검색 키워드가 지정되지 않았습니다."
                    )
                } else {
                    val deletedCount = expenseRepository.deleteByKeyword(keyword)
                    Log.d("gemini", "키워드 기반 삭제: '$keyword' → ${deletedCount}건 삭제")
                    ActionResult(
                        actionType = ActionType.DELETE_BY_KEYWORD,
                        success = deletedCount > 0,
                        message = if (deletedCount > 0) "'$keyword' 포함 항목 ${deletedCount}건을 삭제했습니다." else "'$keyword' 포함 항목이 없습니다.",
                        affectedCount = deletedCount
                    )
                }
            }

            ActionType.DELETE_DUPLICATES -> {
                val deletedCount = expenseRepository.deleteDuplicates()
                ActionResult(
                    actionType = ActionType.DELETE_DUPLICATES,
                    success = deletedCount > 0,
                    message = if (deletedCount > 0) "중복 ${deletedCount}건을 삭제했습니다." else "중복 항목이 없습니다.",
                    affectedCount = deletedCount
                )
            }

            ActionType.ADD_EXPENSE -> {
                val storeName = action.storeName
                val amount = action.amount
                val dateStr = action.date

                if (storeName.isNullOrBlank() || amount == null || amount <= 0) {
                    ActionResult(
                        actionType = ActionType.ADD_EXPENSE,
                        success = false,
                        message = "가게명과 금액은 필수입니다."
                    )
                } else {
                    val dateTime = if (!dateStr.isNullOrBlank()) {
                        try {
                            SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(dateStr)?.time ?: System.currentTimeMillis()
                        } catch (e: Exception) { System.currentTimeMillis() }
                    } else {
                        System.currentTimeMillis()
                    }

                    val expense = ExpenseEntity(
                        storeName = storeName,
                        amount = amount,
                        dateTime = dateTime,
                        cardName = action.cardName ?: "수동입력",
                        category = action.newCategory ?: "미분류",
                        originalSms = "",
                        smsId = "manual_${System.currentTimeMillis()}",
                        memo = action.memo
                    )
                    val id = expenseRepository.insert(expense)
                    Log.d("gemini", "지출 추가: $storeName ${amount}원 → ID $id")
                    ActionResult(
                        actionType = ActionType.ADD_EXPENSE,
                        success = true,
                        message = "'$storeName' ${numberFormat.format(amount)}원 지출을 추가했습니다. (ID: $id)",
                        affectedCount = 1
                    )
                }
            }

            ActionType.UPDATE_MEMO -> {
                val expenseId = action.expenseId
                if (expenseId == null) {
                    ActionResult(
                        actionType = ActionType.UPDATE_MEMO,
                        success = false,
                        message = "수정할 지출 ID가 지정되지 않았습니다."
                    )
                } else {
                    val expense = expenseRepository.getExpenseById(expenseId)
                    if (expense != null) {
                        val count = expenseRepository.updateMemo(expenseId, action.memo)
                        Log.d("gemini", "메모 수정: ID $expenseId → '${action.memo}'")
                        ActionResult(
                            actionType = ActionType.UPDATE_MEMO,
                            success = count > 0,
                            message = "ID $expenseId (${expense.storeName})의 메모를 '${action.memo ?: ""}'(으)로 수정했습니다.",
                            affectedCount = count
                        )
                    } else {
                        ActionResult(
                            actionType = ActionType.UPDATE_MEMO,
                            success = false,
                            message = "ID $expenseId 항목을 찾을 수 없습니다."
                        )
                    }
                }
            }

            ActionType.UPDATE_STORE_NAME -> {
                val id = action.expenseId
                val name = action.newStoreName
                if (id == null || name.isNullOrBlank()) {
                    ActionResult(
                        actionType = ActionType.UPDATE_STORE_NAME,
                        success = false,
                        message = "수정할 지출 ID와 새 가게명은 필수입니다."
                    )
                } else {
                    val expense = expenseRepository.getExpenseById(id)
                    if (expense != null) {
                        val oldName = expense.storeName
                        val count = expenseRepository.updateStoreName(id, name)
                        Log.d("gemini", "가게명 수정: ID $id '$oldName' → '$name'")
                        ActionResult(
                            actionType = ActionType.UPDATE_STORE_NAME,
                            success = count > 0,
                            message = "ID ${id}의 가게명을 '$oldName' → '$name'(으)로 수정했습니다.",
                            affectedCount = count
                        )
                    } else {
                        ActionResult(
                            actionType = ActionType.UPDATE_STORE_NAME,
                            success = false,
                            message = "ID $id 항목을 찾을 수 없습니다."
                        )
                    }
                }
            }

            ActionType.UPDATE_AMOUNT -> {
                val expenseId = action.expenseId
                val newAmount = action.newAmount
                if (expenseId == null || newAmount == null || newAmount <= 0) {
                    ActionResult(
                        actionType = ActionType.UPDATE_AMOUNT,
                        success = false,
                        message = "수정할 지출 ID와 새 금액은 필수입니다."
                    )
                } else {
                    val expense = expenseRepository.getExpenseById(expenseId)
                    if (expense != null) {
                        val oldAmount = expense.amount
                        val count = expenseRepository.updateAmount(expenseId, newAmount)
                        Log.d("gemini", "금액 수정: ID $expenseId ${oldAmount}원 → ${newAmount}원")
                        ActionResult(
                            actionType = ActionType.UPDATE_AMOUNT,
                            success = count > 0,
                            message = "ID $expenseId (${expense.storeName})의 금액을 ${numberFormat.format(oldAmount)}원 → ${numberFormat.format(newAmount)}원으로 수정했습니다.",
                            affectedCount = count
                        )
                    } else {
                        ActionResult(
                            actionType = ActionType.UPDATE_AMOUNT,
                            success = false,
                            message = "ID $expenseId 항목을 찾을 수 없습니다."
                        )
                    }
                }
            }

            ActionType.ADD_SMS_EXCLUSION -> {
                val keyword = action.searchKeyword
                if (keyword.isNullOrBlank()) {
                    ActionResult(
                        actionType = ActionType.ADD_SMS_EXCLUSION,
                        success = false,
                        message = "추가할 제외 키워드가 필요합니다."
                    )
                } else {
                    val added = smsExclusionRepository.addKeyword(keyword, source = "chat")
                    ActionResult(
                        actionType = ActionType.ADD_SMS_EXCLUSION,
                        success = added,
                        message = if (added) "\"$keyword\" 키워드를 SMS 제외 목록에 추가했습니다. 다음 동기화부터 적용됩니다."
                        else "\"$keyword\" 키워드가 이미 존재합니다.",
                        affectedCount = if (added) 1 else 0
                    )
                }
            }

            ActionType.REMOVE_SMS_EXCLUSION -> {
                val keyword = action.searchKeyword
                if (keyword.isNullOrBlank()) {
                    ActionResult(
                        actionType = ActionType.REMOVE_SMS_EXCLUSION,
                        success = false,
                        message = "삭제할 제외 키워드가 필요합니다."
                    )
                } else {
                    val deleted = smsExclusionRepository.removeKeyword(keyword)
                    ActionResult(
                        actionType = ActionType.REMOVE_SMS_EXCLUSION,
                        success = deleted > 0,
                        message = if (deleted > 0) "\"$keyword\" 키워드를 SMS 제외 목록에서 삭제했습니다."
                        else "\"$keyword\" 키워드를 찾을 수 없거나 기본 키워드라 삭제할 수 없습니다.",
                        affectedCount = deleted
                    )
                }
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
            withContext(Dispatchers.IO) { geminiRepository.setApiKey(key) }
            checkApiKey()
        }
    }

    fun clearCurrentSessionHistory() {
        viewModelScope.launch {
            _uiState.value.currentSessionId?.let { sessionId ->
                withContext(Dispatchers.IO) {
                    chatDao.deleteChatsBySession(sessionId)
                    chatRepository.clearSessionSummary(sessionId)
                    // 세션 제목 초기화
                    chatDao.updateSessionTitle(sessionId, "새 대화")
                }
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            chatDao.deleteAll()
        }
    }

    /**
     * 마지막 실패한 메시지를 재전송
     * 실패한 AI 응답(에러 메시지)을 삭제하고, 마지막 사용자 메시지도 삭제한 뒤 다시 전송
     */
    fun retryLastMessage() {
        val message = lastUserMessage ?: return
        val sessionId = _uiState.value.currentSessionId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(canRetry = false) }

            // 마지막 AI 응답(에러)과 사용자 메시지를 DB에서 삭제
            withContext(Dispatchers.IO) {
                val recentChats = chatDao.getRecentChatsBySession(sessionId, 2)
                for (chat in recentChats) {
                    chatDao.delete(chat)
                }
            }

            // 다시 전송
            sendMessage(message)
        }
    }
}
