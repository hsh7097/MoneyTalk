package com.sanha.moneytalk.feature.chat.ui

import com.sanha.moneytalk.core.util.MoneyTalkLogger
import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.ad.RewardAdManager
import com.sanha.moneytalk.core.database.dao.ChatDao
import com.sanha.moneytalk.core.database.entity.ChatSessionEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.firebase.AnalyticsEvent
import com.sanha.moneytalk.core.firebase.AnalyticsHelper
import com.sanha.moneytalk.core.firebase.FirebaseAiRateLimitPolicy
import com.sanha.moneytalk.core.util.ActionResult
import com.sanha.moneytalk.core.util.ActionType
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.ChatContextBuilder
import com.sanha.moneytalk.core.util.ChatCreditPolicy
import com.sanha.moneytalk.core.util.LocalChatQueryRouter
import com.sanha.moneytalk.core.util.QueryResult
import com.sanha.moneytalk.feature.chat.data.ChatRepository
import com.sanha.moneytalk.feature.chat.data.ChatIncomeContextPolicy
import com.sanha.moneytalk.feature.chat.data.GeminiRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import com.sanha.moneytalk.feature.chat.data.ChatQueryExecutor
import com.sanha.moneytalk.feature.chat.data.ChatActionExecutor
import com.sanha.moneytalk.feature.chat.data.ChatMessageObserver

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val geminiRepository: GeminiRepository,
    private val chatRepository: ChatRepository,
    private val chatDao: ChatDao,
    private val settingsDataStore: SettingsDataStore,
    private val rewardAdManager: RewardAdManager,
    private val analyticsHelper: AnalyticsHelper,
    private val dataRefreshEvent: DataRefreshEvent,
    private val queryExecutor: ChatQueryExecutor,
    private val actionExecutor: ChatActionExecutor,
    private val messageObserver: ChatMessageObserver
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** sendMessage 동시 호출 방지용 Mutex */
    private val sendMutex = Mutex()

    private class CreditRefundGuard(val amount: Int) {
        private val refunded = AtomicBoolean(false)

        suspend fun refundOnce(
            sessionId: Long?,
            refund: suspend (amount: Int, sessionId: Long?) -> Unit
        ) {
            if (amount <= 0) return
            if (refunded.compareAndSet(false, true)) {
                refund(amount, sessionId)
            }
        }
    }

    /** 재시도를 위한 마지막 사용자 메시지 저장 */
    private var lastUserMessage: String? = null

    init {
        loadSessions()
        observeCurrentSessionMessages()
        checkApiKey()
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
                    MoneyTalkLogger.w("자동 타이틀 생성 실패, 폴백 적용: ${e.message}")
                    try {
                        val fallbackTitle =
                            messages.firstOrNull { it.isUser }?.content?.take(30) ?: "대화"
                        chatDao.updateSessionTitle(sessionId, fallbackTitle)
                    } catch (inner: Exception) {
                        MoneyTalkLogger.e("폴백 타이틀 저장도 실패: ${inner.message}")
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
                }
        }
    }

    private fun observeCurrentSessionMessages() {
        viewModelScope.launch {
            messageObserver.observe(uiState.map { it.currentSessionId })
                .collect { snapshot ->
                    _uiState.update {
                        // 방 전환 직전 큐에 들어온 이전 방의 결과도 표시하지 않는다.
                        if (it.currentSessionId != snapshot.sessionId) return@update it
                        it.copy(
                            messages = snapshot.messages.map { chat ->
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

    /**
     * 리워드 광고 관련 상태 감시
     * - AI 크레딧 잔액 Flow 수집
     * - PremiumConfig의 credit_ad_enable/reward_ad_enabled 변경 시 광고 프리로드
     */
    private fun observeRewardAdState() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                rewardAdManager.prepareCreditBalance()
            }
            rewardAdManager.rewardChatRemainingFlow.collect { remaining ->
                _uiState.update { it.copy(rewardChatRemaining = remaining) }
            }
        }
        viewModelScope.launch {
            rewardAdManager.isCreditRewardAdEnabledFlow.collect { isRewardAdEnabled ->
                val hasKey = withContext(Dispatchers.IO) { geminiRepository.hasApiKey() }
                _uiState.update {
                    it.copy(
                        isRewardAdEnabled = isRewardAdEnabled,
                        hasApiKey = hasKey
                    )
                }
                if (isRewardAdEnabled) {
                    rewardAdManager.preloadCreditAd()
                }
            }
        }
    }

    /**
     * 리워드 광고 시청 완료 처리
     * AI 크레딧 충전 후 대기 중인 메시지를 자동 전송합니다.
     */
    fun onRewardAdWatched() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                rewardAdManager.addRewardChats()
            }
            val pending = _uiState.value.pendingMessage
            _uiState.update {
                it.copy(
                    showRewardAdDialog = false,
                    pendingMessage = null,
                    pendingCreditCost = 0
                )
            }
            if (pending != null) {
                sendMessage(pending)
            }
        }
    }

    /** 리워드 광고 다이얼로그 닫기 (광고 시청 안 함) */
    fun onRewardAdDismissed() {
        _uiState.update {
            it.copy(
                showRewardAdDialog = false,
                pendingMessage = null,
                pendingCreditCost = 0
            )
        }
    }

    /**
     * Activity에서 리워드 광고 표시
     */
    fun showRewardAd(activity: Activity) {
        rewardAdManager.showCreditAd(
            activity = activity,
            onRewarded = { onRewardAdWatched() },
            onFailed = { onRewardAdDismissed() }
        )
    }

    /** 리워드 1회 시청 시 충전되는 AI 크레딧 */
    fun getRewardChatCount(): Int = rewardAdManager.getRewardChatCount()

    fun sendMessage(message: String) {
        if (message.isBlank()) return
        viewModelScope.launch {
            if (!sendMutex.tryLock()) return@launch
            try {
                analyticsHelper.logClick(AnalyticsEvent.SCREEN_CHAT, AnalyticsEvent.CLICK_SEND_CHAT)
                val creditDecision = ChatCreditPolicy.estimate(message)

                // 리워드 광고 체크: 채팅 1회분 크레딧이 부족하면 광고 다이얼로그 표시
                if (rewardAdManager.isAdRequired(creditDecision.cost)) {
                    showRewardAdDialog(message, creditDecision.cost)
                    return@launch
                }

                // AI 크레딧 차감 (크레딧 광고 활성 시에만 차감)
                val consumed = withContext(Dispatchers.IO) {
                    rewardAdManager.consumeRewardChat(creditDecision.cost)
                }
                if (!consumed) {
                    // race condition 방어: 차감 실패 시 광고 다이얼로그 표시
                    showRewardAdDialog(message, creditDecision.cost)
                    return@launch
                }
                val chargedCredits =
                    if (rewardAdManager.isCreditRewardAdEnabled()) creditDecision.cost else 0
                val refundGuard = CreditRefundGuard(chargedCredits)

                lastUserMessage = message
                _uiState.update { it.copy(canRetry = false) }

                val acquired = withTimeoutOrNull(90_000L) {
                    processSendMessage(message, refundGuard)
                }
                if (acquired == null) {
                    refundGuard.refundOnce(_uiState.value.currentSessionId) { amount, sessionId ->
                        refundChargedCredits(amount, sessionId)
                    }
                    _uiState.update {
                        it.copy(isLoading = false, loadingSessionId = null, canRetry = true)
                    }
                }
            } finally {
                sendMutex.unlock()
            }
        }
    }

    private fun showRewardAdDialog(message: String, requiredCredits: Int) {
        _uiState.update {
            it.copy(
                showRewardAdDialog = true,
                pendingMessage = message,
                pendingCreditCost = requiredCredits
            )
        }
    }

    private suspend fun refundChargedCredits(amount: Int, sessionId: Long?) {
        if (amount <= 0) return
        rewardAdManager.refundChatCredits(amount, sessionId)
    }

    /**
     * sendMessage 내부 처리 로직 (Mutex 내부에서 실행)
     */
    private suspend fun processSendMessage(message: String, refundGuard: CreditRefundGuard) {
        suspend fun refundOnce(sessionId: Long?) {
            refundGuard.refundOnce(sessionId) { amount, refundSessionId ->
                refundChargedCredits(amount, refundSessionId)
            }
        }

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
                if (processLocalSimpleLookup(sessionId, message)) return@withContext

                // 1단계: 메시지 저장 + 요약 갱신 + 컨텍스트 구성
                val chatContext = chatRepository.sendMessageAndBuildContext(
                    sessionId = sessionId,
                    userMessage = message
                )

                // 2단계: 대화 맥락을 포함하여 쿼리 분석 요청
                val contextualMessage =
                    ChatContextBuilder.buildQueryAnalysisContext(appContext, chatContext)
                val analyzeResult = geminiRepository.analyzeQueryNeeds(contextualMessage)
                analyzeResult.exceptionOrNull()?.let { error ->
                    // 인증 실패는 기본 조회로 보완해도 다음 AI 요청에서 해결되지 않는다.
                    if (FirebaseAiRateLimitPolicy.isAppCheckFailure(error)) throw error
                }

                val queryResults = mutableListOf<QueryResult>()
                val actionResults = mutableListOf<ActionResult>()

                // clarification 응답 처리 플래그
                var isClarification = false

                analyzeResult.onSuccess { queryRequest ->
                    if (queryRequest != null && queryRequest.isClarification) {
                        refundOnce(sessionId)
                        // Clarification 응답: 추가 확인 질문을 AI 응답으로 표시
                        isClarification = true
                        chatRepository.saveAiResponseAndUpdateSummary(
                            sessionId,
                            queryRequest.clarification ?: ""
                        )
                    } else if (queryRequest != null) {
                        // 3단계: 요청된 쿼리 실행
                        if (queryRequest.queries.isNotEmpty()) {
                            val scopedQueries = ChatIncomeContextPolicy.filterQueries(
                                userMessage = message,
                                queries = queryRequest.queries
                            )
                            for (query in scopedQueries) {
                                val result = queryExecutor.execute(query)
                                if (result != null) {
                                    queryResults.add(result)
                                }
                            }
                        }

                        // 4단계: 요청된 액션 실행
                        if (queryRequest.actions.isNotEmpty()) {
                            for (action in queryRequest.actions) {
                                val result = actionExecutor.execute(action)
                                actionResults.add(result)
                            }
                            // DB 변경 액션이 성공하면 다른 화면(Home/History)에 알림
                            val hasDataChange = actionResults.any { it.success && it.affectedCount > 0 }
                            if (hasDataChange) {
                                val hasCategoryChange = actionResults.any {
                                    it.success && isCategoryChangeAction(it.actionType)
                                }
                                dataRefreshEvent.emit(
                                    if (hasCategoryChange) DataRefreshEvent.RefreshType.CATEGORY_UPDATED
                                    else DataRefreshEvent.RefreshType.TRANSACTION_ADDED
                                )
                            }
                        }

                        // 쿼리/액션 모두 없으면 일반 대화로 처리한다.
                        if (queryRequest.queries.isEmpty() && queryRequest.actions.isEmpty()) {
                            MoneyTalkLogger.d("채팅 쿼리 없음: 기본 지출 데이터 전송 생략")
                        }
                    } else {
                        if (shouldUseDefaultQueryResults(message)) {
                            val fallbackResults = queryExecutor.getDefaultResults()
                            queryResults.addAll(fallbackResults)
                        }
                    }
                }.onFailure {
                    if (shouldUseDefaultQueryResults(message)) {
                        val fallbackResults = queryExecutor.getDefaultResults()
                        queryResults.addAll(fallbackResults)
                    }
                }

                // Clarification이면 쿼리/답변 생성을 건너뜀 (사용자의 추가 입력을 기다림)
                if (!isClarification) {
                    // 5단계: 대화 맥락 + 쿼리 결과로 최종 답변 생성
                    val hasFinancialContext =
                        queryResults.isNotEmpty() || actionResults.isNotEmpty()
                    val monthlyIncome = if (
                        hasFinancialContext &&
                        ChatIncomeContextPolicy.requiresIncomeContext(message)
                    ) {
                        settingsDataStore.getMonthlyIncome()
                    } else {
                        null
                    }

                    val dataContext = queryResults.joinToString("\n\n") { result ->
                        "[${result.queryType.name}]\n${result.data}"
                    }
                    val actionContext =
                        actionResults.joinToString("\n") { "- ${it.message}" }

                    val finalPrompt = ChatContextBuilder.buildFinalAnswerPrompt(
                        context = appContext,
                        chatContext = chatContext,
                        queryResults = dataContext,
                        monthlyIncome = monthlyIncome,
                        actionResults = actionContext
                    )

                    val finalResult =
                        geminiRepository.generateFinalAnswerWithContext(finalPrompt)

                    finalResult.onSuccess { response ->
                        // AI 응답 저장
                        chatRepository.saveAiResponseAndUpdateSummary(
                            sessionId,
                            response
                        )
                    }.onFailure { e ->
                        refundOnce(sessionId)
                        chatRepository.saveAiResponseAndUpdateSummary(
                            sessionId,
                            if (FirebaseAiRateLimitPolicy.isAppCheckFailure(e)) {
                                appContext.getString(R.string.chat_app_verification_failed)
                            } else {
                                appContext.getString(R.string.error_response, e.message)
                            }
                        )
                        _uiState.update { it.copy(canRetry = true) }
                    }
                }
            }

            _uiState.update { it.copy(isLoading = false, loadingSessionId = null) }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                refundOnce(sessionId)
            }
            throw e
        } catch (e: Exception) {
            refundOnce(sessionId)
            withContext(Dispatchers.IO) {
                chatRepository.saveAiResponseAndUpdateSummary(
                    sessionId,
                    if (FirebaseAiRateLimitPolicy.isAppCheckFailure(e)) {
                        appContext.getString(R.string.chat_app_verification_failed)
                    } else {
                        appContext.getString(R.string.error_general, e.message)
                    }
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

    private suspend fun processLocalSimpleLookup(sessionId: Long, message: String): Boolean {
        val route = LocalChatQueryRouter.tryRoute(message) ?: return false
        val queryResults = try {
            route.queries.mapNotNull { query -> queryExecutor.execute(query) }
        } catch (e: Exception) {
            chatRepository.saveLocalUserMessage(sessionId, message)
            MoneyTalkLogger.e("로컬 단순 조회 실패: ${route.type}", e)
            throw e
        }
        if (queryResults.isEmpty()) return false

        val response = buildLocalLookupResponse(queryResults)
        chatRepository.saveLocalExchange(
            sessionId = sessionId,
            userMessage = message,
            localResponse = response
        )
        MoneyTalkLogger.d("로컬 단순 조회 처리: ${route.type}")
        return true
    }

    private fun buildLocalLookupResponse(queryResults: List<QueryResult>): String {
        val resultText = queryResults.joinToString("\n\n") { result -> result.data }
        return appContext.getString(R.string.chat_local_lookup_answer, resultText)
    }

    /**
     * 쿼리 분석이 실패했을 때만 사용하는 보수적 폴백 판정.
     * 일반 인사/잡담에는 최근 지출 내역을 자동 첨부하지 않는다.
     */
    private fun shouldUseDefaultQueryResults(message: String): Boolean {
        val lower = message.lowercase(Locale.KOREA)
        val financialKeywords = listOf(
            "지출", "소비", "결제", "수입", "입금", "돈", "금액", "얼마",
            "가계부", "내역", "카테고리", "분류", "예산", "절약", "분석",
            "비율", "카드", "가게", "상점", "식비", "배달", "카페", "쇼핑",
            "미분류", "중복", "삭제", "수정", "추가", "메모",
            "expense", "spend", "spent", "income", "payment", "budget",
            "category", "categorize", "saving", "analysis", "ratio",
            "card", "store", "transaction", "uncategorized", "duplicate"
        )
        return financialKeywords.any { lower.contains(it) }
    }

    private fun isCategoryChangeAction(actionType: ActionType): Boolean {
        return actionType == ActionType.UPDATE_CATEGORY ||
            actionType == ActionType.UPDATE_CATEGORY_BY_STORE ||
            actionType == ActionType.UPDATE_CATEGORY_BY_KEYWORD
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

    // ===== 화면별 온보딩 =====

    fun hasSeenScreenOnboardingFlow(screenId: String) =
        settingsDataStore.hasSeenScreenOnboardingFlow(screenId)

    fun markScreenOnboardingSeen(screenId: String) {
        viewModelScope.launch {
            settingsDataStore.setScreenOnboardingSeen(screenId)
        }
    }
}
