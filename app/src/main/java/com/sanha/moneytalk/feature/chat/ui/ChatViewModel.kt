package com.sanha.moneytalk.feature.chat.ui

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.core.ad.RewardAdManager
import com.sanha.moneytalk.core.database.dao.ChatDao
import com.sanha.moneytalk.core.database.entity.ChatSessionEntity
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.util.ActionResult
import com.sanha.moneytalk.core.util.ActionType
import com.sanha.moneytalk.core.util.AnalyticsFilter
import com.sanha.moneytalk.core.util.AnalyticsMetric
import com.sanha.moneytalk.core.util.CategoryReferenceProvider
import com.sanha.moneytalk.core.util.ChatContextBuilder
import com.sanha.moneytalk.core.util.DataAction
import com.sanha.moneytalk.core.util.DataQuery
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.QueryResult
import com.sanha.moneytalk.core.util.QueryType
import com.sanha.moneytalk.core.util.StoreAliasManager
import com.sanha.moneytalk.feature.chat.data.ChatRepository
import com.sanha.moneytalk.feature.chat.data.GeminiRepository
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.core.firebase.PremiumManager
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.compose.runtime.Stable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@Stable
data class ChatMessage(
    val id: Long = 0,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Stable
data class ChatSession(
    val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int = 0
)

@Stable
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: Long? = null,
    val isLoading: Boolean = false,
    /** 로딩 중인 세션 ID (다른 채팅방에서는 로딩 표시 안 함) */
    val loadingSessionId: Long? = null,
    val errorMessage: String? = null,
    val hasApiKey: Boolean = false,
    /** 음성 명령 힌트 노출 여부 (true면 아직 1회 노출 전) */
    val showVoiceHint: Boolean = true,
    val showSessionList: Boolean = false,
    val canRetry: Boolean = false,
    /** 채팅방 내부 화면 표시 여부 (false=목록, true=채팅방 내부) */
    val isInChatRoom: Boolean = false,
    /** 리워드 광고 다이얼로그 표시 여부 */
    val showRewardAdDialog: Boolean = false,
    /** 리워드 채팅 잔여 횟수 */
    val rewardChatRemaining: Int = 0,
    /** 광고 시청 후 전송할 대기 메시지 */
    val pendingMessage: String? = null,
    /** 리워드 광고 기능 활성화 여부 */
    val isRewardAdEnabled: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val geminiRepository: GeminiRepository,
    private val chatRepository: ChatRepository,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val chatDao: ChatDao,
    private val settingsDataStore: SettingsDataStore,
    private val smsExclusionRepository: com.sanha.moneytalk.core.database.SmsExclusionRepository,
    private val categoryReferenceProvider: CategoryReferenceProvider,
    private val rewardAdManager: RewardAdManager,
    private val premiumManager: PremiumManager
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
        observeVoiceHintSeen()
        autoCreateSessionIfEmpty()
        observeRewardAdState()
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
        _uiState.update {
            // 다른 채팅방으로 진입하면 로딩 표시 해제 (로딩 중인 세션이 아닌 경우)
            val showLoading = it.loadingSessionId == sessionId
            it.copy(
                currentSessionId = sessionId,
                isInChatRoom = true,
                isLoading = showLoading,
                canRetry = false
            )
        }
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
                        val fallbackTitle =
                            messages.firstOrNull { it.isUser }?.content?.take(30) ?: "대화"
                        chatDao.updateSessionTitle(sessionId, fallbackTitle)
                    }
                } catch (e: Exception) {
                    // 타이틀 생성 실패 시 첫 사용자 메시지로 폴백
                    Log.w("ChatViewModel", "자동 타이틀 생성 실패, 폴백 적용: ${e.message}")
                    try {
                        val fallbackTitle =
                            messages.firstOrNull { it.isUser }?.content?.take(30) ?: "대화"
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
                    val validCurrentId =
                        if (currentId != null && sessionList.any { it.id == currentId }) {
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
            _uiState.update {
                val showLoading = it.loadingSessionId == sessionId
                it.copy(
                    currentSessionId = sessionId,
                    showSessionList = false,
                    isInChatRoom = true,
                    isLoading = showLoading,
                    canRetry = false
                )
            }
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
            _uiState.update {
                it.copy(
                    currentSessionId = sessionId,
                    showSessionList = false,
                    isInChatRoom = true,
                    isLoading = false,
                    canRetry = false
                )
            }
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

    private fun observeVoiceHintSeen() {
        viewModelScope.launch {
            settingsDataStore.chatVoiceHintSeenFlow.collect { seen ->
                _uiState.update { it.copy(showVoiceHint = !seen) }
            }
        }
    }

    fun markVoiceHintSeen() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.setChatVoiceHintSeen(true)
        }
    }

    /**
     * 리워드 광고 관련 상태 감시
     * - 잔여 횟수 Flow 수집
     * - PremiumConfig의 rewardAdEnabled 변경 시 광고 프리로드
     */
    private fun observeRewardAdState() {
        viewModelScope.launch {
            rewardAdManager.rewardChatRemainingFlow.collect { remaining ->
                _uiState.update { it.copy(rewardChatRemaining = remaining) }
            }
        }
        viewModelScope.launch {
            premiumManager.premiumConfig.collect { config ->
                val hasKey = withContext(Dispatchers.IO) { geminiRepository.hasApiKey() }
                _uiState.update {
                    it.copy(
                        isRewardAdEnabled = config.rewardAdEnabled,
                        hasApiKey = hasKey
                    )
                }
                if (config.rewardAdEnabled) {
                    rewardAdManager.preloadAd()
                }
            }
        }
    }

    /**
     * 리워드 광고 시청 완료 처리
     * 보상 충전 후 대기 중인 메시지를 자동 전송합니다.
     */
    fun onRewardAdWatched() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                rewardAdManager.addRewardChats()
            }
            val pending = _uiState.value.pendingMessage
            _uiState.update { it.copy(showRewardAdDialog = false, pendingMessage = null) }
            if (pending != null) {
                sendMessage(pending)
            }
        }
    }

    /** 리워드 광고 다이얼로그 닫기 (광고 시청 안 함) */
    fun onRewardAdDismissed() {
        _uiState.update { it.copy(showRewardAdDialog = false, pendingMessage = null) }
    }

    /**
     * Activity에서 리워드 광고 표시
     */
    fun showRewardAd(activity: Activity) {
        rewardAdManager.showAd(
            activity = activity,
            onRewarded = { onRewardAdWatched() },
            onFailed = {
                _uiState.update { it.copy(showRewardAdDialog = false, pendingMessage = null) }
            }
        )
    }

    /** 리워드 1회 시청 시 충전되는 횟수 */
    fun getRewardChatCount(): Int = rewardAdManager.getRewardChatCount()

    fun sendMessage(message: String) {
        if (message.isBlank()) return
        if (sendMutex.isLocked) return  // 이미 처리 중이면 무시

        viewModelScope.launch {
            // 리워드 광고 체크: 활성 상태이고 잔여 횟수가 0이면 광고 다이얼로그 표시
            if (rewardAdManager.isAdRequired()) {
                _uiState.update {
                    it.copy(showRewardAdDialog = true, pendingMessage = message)
                }
                return@launch
            }

            // 잔여 횟수 차감 (광고 기능 활성 시에만 차감)
            val consumed = withContext(Dispatchers.IO) {
                rewardAdManager.consumeRewardChat()
            }
            if (!consumed) {
                // race condition 방어: 차감 실패 시 광고 다이얼로그 표시
                _uiState.update {
                    it.copy(showRewardAdDialog = true, pendingMessage = message)
                }
                return@launch
            }

            lastUserMessage = message
            _uiState.update { it.copy(canRetry = false) }

            val acquired = withTimeoutOrNull(90_000L) {
                sendMutex.withLock {
                    processSendMessage(message)
                }
            }
            if (acquired == null) {
                _uiState.update {
                    it.copy(isLoading = false, loadingSessionId = null, canRetry = true)
                }
            }
        }
    }

    /**
     * sendMessage 내부 처리 로직 (Mutex 내부에서 실행)
     */
    private suspend fun processSendMessage(message: String) {
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

        _uiState.update { it.copy(isLoading = true, loadingSessionId = sessionId) }

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
                val contextualMessage =
                    ChatContextBuilder.buildQueryAnalysisContext(chatContext)
                val analyzeResult = geminiRepository.analyzeQueryNeeds(contextualMessage)

                val queryResults = mutableListOf<QueryResult>()
                val actionResults = mutableListOf<ActionResult>()

                // clarification 응답 처리 플래그
                var isClarification = false

                analyzeResult.onSuccess { queryRequest ->
                    if (queryRequest != null && queryRequest.isClarification) {
                        // Clarification 응답: 추가 확인 질문을 AI 응답으로 표시
                        Log.d(
                            "gemini",
                            "=== Clarification 응답: ${queryRequest.clarification} ==="
                        )
                        isClarification = true
                        chatRepository.saveAiResponseAndUpdateSummary(
                            sessionId,
                            queryRequest.clarification ?: ""
                        )
                    } else if (queryRequest != null) {
                        Log.d(
                            "gemini",
                            "=== Step2: 쿼리 ${queryRequest.queries.size}개, 액션 ${queryRequest.actions.size}개 실행 시작 ==="
                        )
                        // 3단계: 요청된 쿼리 실행
                        if (queryRequest.queries.isNotEmpty()) {
                            for (query in queryRequest.queries) {
                                Log.d(
                                    "gemini",
                                    "쿼리 실행: type=${query.type}, startDate=${query.startDate}, endDate=${query.endDate}, category=${query.category}, filters=${query.filters?.size ?: 0}개, groupBy=${query.groupBy}, metrics=${query.metrics?.size ?: 0}개, topN=${query.topN}"
                                )
                                val result = executeQuery(query)
                                if (result != null) {
                                    Log.d(
                                        "gemini",
                                        "쿼리 결과 [${result.queryType}]: ${result.data.take(200)}${if (result.data.length > 200) "..." else ""}"
                                    )
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

                // Clarification이면 쿼리/답변 생성을 건너뜀 (사용자의 추가 입력을 기다림)
                if (!isClarification) {
                    // 5단계: 대화 맥락 + 쿼리 결과로 최종 답변 생성
                    val monthlyIncome = settingsDataStore.getMonthlyIncome()

                    val dataContext = queryResults.joinToString("\n\n") { result ->
                        "[${result.queryType.name}]\n${result.data}"
                    }
                    val actionContext =
                        actionResults.joinToString("\n") { "- ${it.message}" }

                    val finalPrompt = ChatContextBuilder.buildFinalAnswerPrompt(
                        context = chatContext,
                        queryResults = dataContext,
                        monthlyIncome = monthlyIncome,
                        actionResults = actionContext
                    )

                    val finalResult =
                        geminiRepository.generateFinalAnswerWithContext(finalPrompt)

                    finalResult.onSuccess { response ->
                        // AI 응답 저장 + 요약 갱신
                        chatRepository.saveAiResponseAndUpdateSummary(
                            sessionId,
                            response
                        )
                    }.onFailure { e ->
                        chatRepository.saveAiResponseAndUpdateSummary(
                            sessionId,
                            "죄송해요, 응답을 받는 중 오류가 발생했어요 😢\n(${e.message})"
                        )
                        _uiState.update { it.copy(canRetry = true) }
                    }
                }
            }

            _uiState.update { it.copy(isLoading = false, loadingSessionId = null) }
        } catch (e: Exception) {
            withContext(Dispatchers.IO) {
                chatRepository.saveAiResponseAndUpdateSummary(
                    sessionId,
                    "오류가 발생했어요 😢\n(${e.message})"
                )
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    loadingSessionId = null,
                    canRetry = true
                )
            }
        }
    }

    /**
     * Gemini가 요청한 쿼리를 실행하여 결과 반환
     */
    private suspend fun executeQuery(query: DataQuery): QueryResult? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

        // 전체 기간이 필요한 쿼리 타입 (날짜 없으면 epoch 0부터)
        val needsFullRange = query.type in listOf(
            QueryType.MONTHLY_TOTALS, QueryType.CARD_LIST, QueryType.MONTHLY_INCOME,
            QueryType.DUPLICATE_LIST, QueryType.SMS_EXCLUSION_LIST
        )

        // 날짜 파싱 (없으면 이번 달 기본값, 전체 기간 필요한 쿼리는 0L)
        val startTimestamp = query.startDate?.let {
            try {
                dateFormat.parse(it)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        } ?: if (needsFullRange) 0L else DateUtils.getMonthStartTimestamp()

        val endTimestamp = query.endDate?.let {
            try {
                // 종료일은 해당 일의 끝까지 포함
                (dateFormat.parse(it)?.time
                    ?: System.currentTimeMillis()) + (24 * 60 * 60 * 1000 - 1)
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
                    expenseRepository.getTotalExpenseByCategoriesAndDateRange(
                        categoryNames,
                        startTimestamp,
                        endTimestamp
                    )
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
                val categoryExpenses =
                    expenseRepository.getExpenseSumByCategory(startTimestamp, endTimestamp)
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
                    expenseRepository.getExpensesByCategoriesAndDateRangeOnce(
                        categoryNames,
                        startTimestamp,
                        endTimestamp
                    )
                } else {
                    expenseRepository.getExpensesByDateRangeOnce(startTimestamp, endTimestamp)
                }.take(limit)

                val expenseList = expenses.joinToString("\n") { expense ->
                    "${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${
                        numberFormat.format(
                            expense.amount
                        )
                    }원 (${expense.category})${expense.memo?.let { " [메모: $it]" } ?: ""}"
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
                    "${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${
                        numberFormat.format(
                            expense.amount
                        )
                    }원"
                }.ifEmpty { "해당 가게 지출 내역이 없습니다." }

                val aliasInfo = if (aliases.size > 1) " (${aliases.joinToString(", ")})" else ""

                QueryResult(
                    queryType = QueryType.EXPENSE_BY_STORE,
                    data = "'$storeName'$aliasInfo 지출 (${query.startDate ?: "이번 달"} ~ ${query.endDate ?: "현재"}):\n총 ${
                        numberFormat.format(
                            total
                        )
                    }원 (${allExpenses.size}건)\n$expenseList"
                )
            }

            QueryType.UNCATEGORIZED_LIST -> {
                val limit = query.limit ?: 20
                val expenses = expenseRepository.getUncategorizedExpenses(limit)
                val expenseList = expenses.joinToString("\n") { expense ->
                    "[ID:${expense.id}] ${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${
                        numberFormat.format(
                            expense.amount
                        )
                    }원"
                }.ifEmpty { "미분류 항목이 없습니다." }

                QueryResult(
                    queryType = QueryType.UNCATEGORIZED_LIST,
                    data = "미분류 항목 (${expenses.size}건):\n$expenseList"
                )
            }

            QueryType.CATEGORY_RATIO -> {
                val monthlyIncome = settingsDataStore.getMonthlyIncome()
                val allCategoryExpenses =
                    expenseRepository.getExpenseSumByCategory(startTimestamp, endTimestamp)

                // category 필터가 있으면 해당 카테고리(+하위)만 필터링
                val categoryExpenses = if (query.category != null) {
                    val cat = Category.fromDisplayName(query.category)
                    val categoryNames = cat.displayNamesIncludingSub
                    allCategoryExpenses.filter { it.category in categoryNames }
                } else {
                    allCategoryExpenses
                }

                val totalExpense = allCategoryExpenses.sumOf { it.total }  // 전체 지출 총액 (비율 계산용)
                val filteredTotal = categoryExpenses.sumOf { it.total }    // 필터된 카테고리 합계

                val ratioBreakdown = categoryExpenses.joinToString("\n") { item ->
                    val category = Category.fromDisplayName(item.category)
                    val incomeRatio =
                        if (monthlyIncome > 0) (item.total * 100.0 / monthlyIncome) else 0.0
                    val expenseRatio =
                        if (totalExpense > 0) (item.total * 100.0 / totalExpense) else 0.0
                    "${category.emoji} ${category.displayName}: ${numberFormat.format(item.total)}원 (수입의 ${
                        String.format(
                            "%.1f",
                            incomeRatio
                        )
                    }%, 지출의 ${String.format("%.1f", expenseRatio)}%)"
                }.ifEmpty { "해당 기간 지출 내역이 없습니다." }

                val totalIncomeRatio =
                    if (monthlyIncome > 0) (totalExpense * 100.0 / monthlyIncome) else 0.0
                val categoryLabel = query.category?.let { " ($it)" } ?: ""

                QueryResult(
                    queryType = QueryType.CATEGORY_RATIO,
                    data = "수입 대비 카테고리별 비율$categoryLabel (${query.startDate ?: "이번 달"} ~ ${query.endDate ?: "현재"}):\n월 수입: ${
                        numberFormat.format(
                            monthlyIncome
                        )
                    }원\n총 지출: ${numberFormat.format(totalExpense)}원 (수입의 ${
                        String.format(
                            "%.1f",
                            totalIncomeRatio
                        )
                    }%)\n\n$ratioBreakdown"
                )
            }

            QueryType.EXPENSE_BY_CARD -> {
                val cardName = query.cardName ?: query.storeName ?: return null
                val allExpenses =
                    expenseRepository.getExpensesByDateRangeOnce(startTimestamp, endTimestamp)
                        .filter { it.cardName.contains(cardName, ignoreCase = true) }
                        .sortedByDescending { it.dateTime }

                val total = allExpenses.sumOf { it.amount }
                val limit = query.limit ?: 20
                val expenseList = allExpenses.take(limit).joinToString("\n") { expense ->
                    "${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${
                        numberFormat.format(
                            expense.amount
                        )
                    }원 (${expense.category})${expense.memo?.let { " [메모: $it]" } ?: ""}"
                }.ifEmpty { "해당 카드 지출 내역이 없습니다." }

                QueryResult(
                    queryType = QueryType.EXPENSE_BY_CARD,
                    data = "'$cardName' 카드 지출 (${query.startDate ?: "전체"} ~ ${query.endDate ?: "현재"}):\n총 ${
                        numberFormat.format(
                            total
                        )
                    }원 (${allExpenses.size}건)\n$expenseList"
                )
            }

            QueryType.SEARCH_EXPENSE -> {
                val keyword = query.searchKeyword ?: query.storeName ?: return null
                val limit = query.limit ?: 30
                val results = expenseRepository.searchExpenses(keyword).take(limit)
                val resultList = results.joinToString("\n") { expense ->
                    "[ID:${expense.id}] ${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${
                        numberFormat.format(
                            expense.amount
                        )
                    }원 (${expense.category}, ${expense.cardName})${expense.memo?.let { " [메모: $it]" } ?: ""}"
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
                val incomes =
                    incomeRepository.getIncomesByDateRangeOnce(startTimestamp, endTimestamp)
                        .take(limit)
                val total = incomes.sumOf { it.amount }
                val incomeList = incomes.joinToString("\n") { income ->
                    "${DateUtils.formatDateTime(income.dateTime)} - ${income.source}: ${
                        numberFormat.format(
                            income.amount
                        )
                    }원 (${income.type})${income.memo?.let { " [메모: $it]" } ?: ""}"
                }.ifEmpty { "해당 기간 수입 내역이 없습니다." }

                QueryResult(
                    queryType = QueryType.INCOME_LIST,
                    data = "수입 내역 (${query.startDate ?: "전체"} ~ ${query.endDate ?: "현재"}):\n총 ${
                        numberFormat.format(
                            total
                        )
                    }원 (${incomes.size}건)\n$incomeList"
                )
            }

            QueryType.DUPLICATE_LIST -> {
                val duplicates = expenseRepository.getDuplicateExpenses()
                val dupList = duplicates.take(20).joinToString("\n") { expense ->
                    "[ID:${expense.id}] ${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${
                        numberFormat.format(
                            expense.amount
                        )
                    }원 (${expense.category})"
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

            QueryType.ANALYTICS -> {
                executeAnalytics(query, startTimestamp, endTimestamp)
            }
        }
    }

    /**
     * ANALYTICS 쿼리 실행: 필터 → 그룹 → 집계 → 포맷
     * 복합 조건 분석을 앱에서 결정론적으로 계산
     */
    private suspend fun executeAnalytics(
        query: DataQuery,
        startTimestamp: Long,
        endTimestamp: Long
    ): QueryResult {
        try {
            Log.d("gemini", "=== ANALYTICS 실행 시작 ===")
            Log.d("gemini", "기간: $startTimestamp ~ $endTimestamp")
            Log.d(
                "gemini",
                "filters: ${query.filters}, groupBy: ${query.groupBy}, metrics: ${query.metrics}, topN: ${query.topN}, sort: ${query.sort}"
            )

            // 1. DB에서 기간 내 전체 지출 조회
            var expenses =
                expenseRepository.getExpensesByDateRangeOnce(startTimestamp, endTimestamp)
            Log.d("gemini", "1단계: DB 조회 결과 ${expenses.size}건")

            // 2. filters 배열 순회하며 메모리 필터링
            val filters = query.filters ?: emptyList()
            val filterDescriptions = mutableListOf<String>()

            for (filter in filters) {
                val before = expenses.size
                expenses = applyAnalyticsFilter(expenses, filter)
                Log.d(
                    "gemini",
                    "2단계 필터: ${filter.field} ${filter.op} ${filter.value} → ${before}건 → ${expenses.size}건"
                )
                if (expenses.size != before || filters.isNotEmpty()) {
                    filterDescriptions.add(describeFilter(filter))
                }
            }

            // 3. groupBy 처리
            val groupBy = query.groupBy
            val grouped: Map<String, List<ExpenseEntity>> =
                if (groupBy.isNullOrBlank() || groupBy == "none") {
                    mapOf("전체" to expenses)
                } else {
                    expenses.groupBy { expense -> getGroupKey(expense, groupBy) }
                }
            Log.d(
                "gemini",
                "3단계 그룹: groupBy=$groupBy → ${grouped.size}개 그룹 (${
                    grouped.keys.take(10).joinToString(", ")
                }${if (grouped.size > 10) "..." else ""})"
            )

            // 4. metrics 계산
            val metrics = if (query.metrics.isNullOrEmpty()) {
                // 기본: sum + count
                listOf(
                    AnalyticsMetric(op = "sum", field = "amount"),
                    AnalyticsMetric(op = "count", field = "amount")
                )
            } else {
                query.metrics
            }

            // 5. 그룹별 집계 계산
            data class GroupResult(
                val key: String,
                val metricValues: List<Pair<String, Number>>, // (label, value)
                val sortValue: Number // 정렬 기준
            )

            val groupResults = grouped.map { (key, items) ->
                val metricValues = metrics.map { metric ->
                    val label = getMetricLabel(metric.op)
                    val value: Number = computeMetric(items, metric.op, metric.field)
                    label to value
                }
                val sortValue = metricValues.firstOrNull()?.second ?: 0
                GroupResult(key, metricValues, sortValue)
            }

            // 6. sort + topN 적용
            val sortDir = query.sort ?: "desc"
            val sorted = if (sortDir == "asc") {
                groupResults.sortedBy { it.sortValue.toDouble() }
            } else {
                groupResults.sortedByDescending { it.sortValue.toDouble() }
            }
            val limited = query.topN?.let { sorted.take(it) } ?: sorted

            // 7. 결과 포맷팅
            val sb = StringBuilder()
            sb.appendLine("[ANALYTICS 계산 결과]")

            if (groupBy.isNullOrBlank() || groupBy == "none") {
                // 그룹 없음: 전체 집계
                val result = limited.firstOrNull()
                if (result != null) {
                    for ((label, value) in result.metricValues) {
                        sb.appendLine("$label: ${formatMetricValue(label, value)}")
                    }
                } else {
                    sb.appendLine("해당 조건에 맞는 데이터가 없습니다.")
                }
            } else {
                // 그룹 있음
                val groupLabel = getGroupByLabel(groupBy)
                val topNLabel = query.topN?.let { " (상위 ${it}개)" } ?: ""
                sb.appendLine("${groupLabel}별 집계$topNLabel:")
                if (limited.isEmpty()) {
                    sb.appendLine("해당 조건에 맞는 데이터가 없습니다.")
                } else {
                    limited.forEachIndexed { idx, result ->
                        val metricsStr = result.metricValues.joinToString(", ") { (label, value) ->
                            "$label: ${formatMetricValue(label, value)}"
                        }
                        sb.appendLine("${idx + 1}. ${result.key}: $metricsStr")
                    }
                }
            }

            // 기간 정보
            sb.appendLine("기간: ${query.startDate ?: "전체"} ~ ${query.endDate ?: "현재"}")
            // 전체 건수
            sb.appendLine("필터 후 총 건수: ${expenses.size}건")
            // 필터 설명
            if (filterDescriptions.isNotEmpty()) {
                sb.appendLine("적용된 필터: ${filterDescriptions.joinToString(", ")}")
            }

            val resultData = sb.toString().trimEnd()
            Log.d("gemini", "=== ANALYTICS 최종 결과 ===\n$resultData")
            return QueryResult(
                queryType = QueryType.ANALYTICS,
                data = resultData
            )
        } catch (e: Exception) {
            Log.e("gemini", "ANALYTICS 실행 오류: ${e.message}", e)
            return QueryResult(
                queryType = QueryType.ANALYTICS,
                data = "[ANALYTICS 계산 결과]\n분석 실행 중 오류가 발생했습니다: ${e.message}"
            )
        }
    }

    /**
     * 단일 필터 조건을 적용하여 필터링된 리스트 반환
     */
    private fun applyAnalyticsFilter(
        expenses: List<ExpenseEntity>,
        filter: AnalyticsFilter
    ): List<ExpenseEntity> {
        return expenses.filter { expense ->
            when (filter.field) {
                "category" -> {
                    val expenseCategory = expense.category
                    val targetValue = filter.value
                    if (filter.includeSubcategories && targetValue is String) {
                        // 하위 카테고리 포함: "식비" → ["식비", "배달"]
                        val cat = Category.fromDisplayName(targetValue)
                        val names = cat.displayNamesIncludingSub
                        when (filter.op) {
                            "==" -> expenseCategory in names
                            "!=" -> expenseCategory !in names
                            "in" -> {
                                // value가 배열이면 각각에 대해 subcategory 포함
                                val valueList = toStringList(targetValue)
                                val allNames = valueList.flatMap {
                                    Category.fromDisplayName(it).displayNamesIncludingSub
                                }
                                expenseCategory in allNames
                            }

                            "not_in" -> {
                                val valueList = toStringList(targetValue)
                                val allNames = valueList.flatMap {
                                    Category.fromDisplayName(it).displayNamesIncludingSub
                                }
                                expenseCategory !in allNames
                            }

                            else -> matchStringOp(expenseCategory, filter.op, targetValue)
                        }
                    } else {
                        when (filter.op) {
                            "in" -> expenseCategory in toStringList(filter.value)
                            "not_in" -> expenseCategory !in toStringList(filter.value)
                            else -> matchStringOp(
                                expenseCategory,
                                filter.op,
                                filter.value?.toString() ?: ""
                            )
                        }
                    }
                }

                "storeName" -> {
                    val value = filter.value?.toString() ?: ""
                    when (filter.op) {
                        "==" -> expense.storeName.equals(value, ignoreCase = true)
                        "!=" -> !expense.storeName.equals(value, ignoreCase = true)
                        "contains" -> expense.storeName.contains(value, ignoreCase = true)
                        "not_contains" -> !expense.storeName.contains(value, ignoreCase = true)
                        "in" -> toStringList(filter.value).any {
                            expense.storeName.equals(
                                it,
                                ignoreCase = true
                            )
                        }

                        "not_in" -> toStringList(filter.value).none {
                            expense.storeName.equals(
                                it,
                                ignoreCase = true
                            )
                        }

                        else -> true
                    }
                }

                "cardName" -> {
                    val value = filter.value?.toString() ?: ""
                    when (filter.op) {
                        "==" -> expense.cardName.equals(value, ignoreCase = true)
                        "!=" -> !expense.cardName.equals(value, ignoreCase = true)
                        "contains" -> expense.cardName.contains(value, ignoreCase = true)
                        "not_contains" -> !expense.cardName.contains(value, ignoreCase = true)
                        "in" -> toStringList(filter.value).any {
                            expense.cardName.equals(
                                it,
                                ignoreCase = true
                            )
                        }

                        "not_in" -> toStringList(filter.value).none {
                            expense.cardName.equals(
                                it,
                                ignoreCase = true
                            )
                        }

                        else -> true
                    }
                }

                "amount" -> {
                    val targetAmount = toNumber(filter.value)
                    when (filter.op) {
                        "==" -> expense.amount.toDouble() == targetAmount
                        "!=" -> expense.amount.toDouble() != targetAmount
                        ">" -> expense.amount > targetAmount
                        ">=" -> expense.amount >= targetAmount
                        "<" -> expense.amount < targetAmount
                        "<=" -> expense.amount <= targetAmount
                        else -> true
                    }
                }

                "memo" -> {
                    val value = filter.value?.toString() ?: ""
                    val memo = expense.memo ?: ""
                    when (filter.op) {
                        "==" -> memo.equals(value, ignoreCase = true)
                        "!=" -> !memo.equals(value, ignoreCase = true)
                        "contains" -> memo.contains(value, ignoreCase = true)
                        "not_contains" -> !memo.contains(value, ignoreCase = true)
                        else -> true
                    }
                }

                "dayOfWeek" -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = expense.dateTime }
                    val dayOfWeek = getDayOfWeekString(cal.get(Calendar.DAY_OF_WEEK))
                    when (filter.op) {
                        "==" -> dayOfWeek.equals(filter.value?.toString(), ignoreCase = true)
                        "!=" -> !dayOfWeek.equals(filter.value?.toString(), ignoreCase = true)
                        "in" -> dayOfWeek.uppercase() in toStringList(filter.value).map { it.uppercase() }
                        "not_in" -> dayOfWeek.uppercase() !in toStringList(filter.value).map { it.uppercase() }
                        else -> true
                    }
                }

                else -> true // 미인식 필드는 무시 (필터 통과)
            }
        }
    }

    /** Calendar.DAY_OF_WEEK → "MON"~"SUN" 문자열 변환 */
    private fun getDayOfWeekString(calendarDay: Int): String {
        return when (calendarDay) {
            Calendar.MONDAY -> "MON"
            Calendar.TUESDAY -> "TUE"
            Calendar.WEDNESDAY -> "WED"
            Calendar.THURSDAY -> "THU"
            Calendar.FRIDAY -> "FRI"
            Calendar.SATURDAY -> "SAT"
            Calendar.SUNDAY -> "SUN"
            else -> "UNKNOWN"
        }
    }

    /** 요일 코드를 한글로 변환 */
    private fun dayOfWeekToKorean(code: String): String {
        return when (code.uppercase()) {
            "MON" -> "월"
            "TUE" -> "화"
            "WED" -> "수"
            "THU" -> "목"
            "FRI" -> "금"
            "SAT" -> "토"
            "SUN" -> "일"
            else -> code
        }
    }

    /** 문자열 비교 연산 헬퍼 */
    private fun matchStringOp(actual: String, op: String, expected: String): Boolean {
        return when (op) {
            "==" -> actual.equals(expected, ignoreCase = true)
            "!=" -> !actual.equals(expected, ignoreCase = true)
            "contains" -> actual.contains(expected, ignoreCase = true)
            "not_contains" -> !actual.contains(expected, ignoreCase = true)
            else -> true
        }
    }

    /** Any? → List<String> 변환 (Gson이 배열을 ArrayList로 파싱) */
    private fun toStringList(value: Any?): List<String> {
        return when (value) {
            is List<*> -> value.mapNotNull { it?.toString() }
            is String -> listOf(value)
            else -> emptyList()
        }
    }

    /** Any? → Number 변환 */
    private fun toNumber(value: Any?): Double {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    /** 그룹 키 추출 */
    private fun getGroupKey(expense: ExpenseEntity, groupBy: String): String {
        return when (groupBy) {
            "category" -> expense.category
            "storeName" -> expense.storeName
            "cardName" -> expense.cardName
            "date" -> DateUtils.formatDateTime(expense.dateTime).substring(0, 10) // "yyyy-MM-dd"
            "month" -> DateUtils.formatDateTime(expense.dateTime).substring(0, 7) // "yyyy-MM"
            "dayOfWeek" -> {
                val cal = Calendar.getInstance().apply { timeInMillis = expense.dateTime }
                getDayOfWeekString(cal.get(Calendar.DAY_OF_WEEK))
            }

            else -> "전체" // 미인식 groupBy → 전체 집계
        }
    }

    /** 그룹 기준 한글 라벨 */
    private fun getGroupByLabel(groupBy: String): String {
        return when (groupBy) {
            "category" -> "카테고리"
            "storeName" -> "가게명"
            "cardName" -> "카드"
            "date" -> "날짜"
            "month" -> "월"
            "dayOfWeek" -> "요일"
            else -> groupBy
        }
    }

    /** 메트릭 연산 실행 */
    private fun computeMetric(items: List<ExpenseEntity>, op: String, field: String): Number {
        // 현재 amount만 지원
        val values = items.map { it.amount }
        return when (op) {
            "sum" -> values.sum()
            "avg" -> if (values.isEmpty()) 0 else (values.sum().toDouble() / values.size).toInt()
            "count" -> values.size
            "max" -> values.maxOrNull() ?: 0
            "min" -> values.minOrNull() ?: 0
            else -> 0
        }
    }

    /** 메트릭 라벨 생성 */
    private fun getMetricLabel(op: String): String {
        return when (op) {
            "sum" -> "합계"
            "avg" -> "평균"
            "count" -> "건수"
            "max" -> "최대"
            "min" -> "최소"
            else -> op
        }
    }

    /** 메트릭 값 포맷팅 */
    private fun formatMetricValue(label: String, value: Number): String {
        return when (label) {
            "건수" -> "${numberFormat.format(value)}건"
            else -> "${numberFormat.format(value)}원"
        }
    }

    /** 필터 조건 설명 문자열 */
    private fun describeFilter(filter: AnalyticsFilter): String {
        val fieldLabel = when (filter.field) {
            "category" -> "카테고리"
            "storeName" -> "가게명"
            "cardName" -> "카드"
            "amount" -> "금액"
            "memo" -> "메모"
            "dayOfWeek" -> "요일"
            else -> filter.field
        }
        val opLabel = when (filter.op) {
            "==" -> "="
            "!=" -> "≠"
            ">" -> ">"
            ">=" -> "≥"
            "<" -> "<"
            "<=" -> "≤"
            "contains" -> "포함"
            "not_contains" -> "미포함"
            "in" -> "∈"
            "not_in" -> "∉"
            else -> filter.op
        }
        val valueStr = when (val v = filter.value) {
            is List<*> -> v.joinToString(",")
            else -> v?.toString() ?: ""
        }
        val subLabel = if (filter.includeSubcategories) "(하위포함)" else ""
        return "$fieldLabel$opLabel$valueStr$subLabel"
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
                    if (affected > 0) categoryReferenceProvider.invalidateCache()
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
                        totalAffected += expenseRepository.updateCategoryByStoreNameContaining(
                            alias,
                            newCategory
                        )
                    }
                    if (totalAffected > 0) categoryReferenceProvider.invalidateCache()
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
                        totalAffected += expenseRepository.updateCategoryByStoreNameContaining(
                            alias,
                            newCategory
                        )
                    }
                    if (totalAffected > 0) categoryReferenceProvider.invalidateCache()
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
                            message = "ID $expenseId 항목 (${expense.storeName}: ${
                                numberFormat.format(
                                    expense.amount
                                )
                            }원)을 삭제했습니다.",
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
                            SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(dateStr)?.time
                                ?: System.currentTimeMillis()
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }
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
                            message = "ID $expenseId (${expense.storeName})의 금액을 ${
                                numberFormat.format(
                                    oldAmount
                                )
                            }원 → ${numberFormat.format(newAmount)}원으로 수정했습니다.",
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
        results.add(
            QueryResult(
                queryType = QueryType.TOTAL_EXPENSE,
                data = "이번 달 총 지출: ${numberFormat.format(totalExpense)}원"
            )
        )

        // 카테고리별 지출
        val categoryExpenses = expenseRepository.getExpenseSumByCategory(monthStart, monthEnd)
        val breakdown = categoryExpenses.joinToString("\n") { item ->
            val category = Category.fromDisplayName(item.category)
            "${category.emoji} ${category.displayName}: ${numberFormat.format(item.total)}원"
        }.ifEmpty { "지출 내역이 없습니다." }
        results.add(
            QueryResult(
                queryType = QueryType.EXPENSE_BY_CATEGORY,
                data = "이번 달 카테고리별 지출:\n$breakdown"
            )
        )

        // 최근 지출 10건
        val recentExpenses = expenseRepository.getRecentExpenses(10)
        val expenseList = recentExpenses.joinToString("\n") { expense ->
            "${DateUtils.formatDateTime(expense.dateTime)} - ${expense.storeName}: ${
                numberFormat.format(
                    expense.amount
                )
            }원"
        }.ifEmpty { "최근 지출 내역이 없습니다." }
        results.add(
            QueryResult(
                queryType = QueryType.EXPENSE_LIST,
                data = "최근 지출 내역:\n$expenseList"
            )
        )

        return results
    }

    @Deprecated("API 키는 Firebase RTDB에서 관리됩니다")
    fun setApiKey(key: String) {
        // RTDB 기반 키 관리로 전환 — 로컬 키 저장 제거
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
