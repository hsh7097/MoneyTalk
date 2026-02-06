package com.sanha.moneytalk.feature.chat.data

import android.util.Log
import com.sanha.moneytalk.core.database.dao.ChatDao
import com.sanha.moneytalk.core.database.entity.ChatEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ChatRepository 구현체
 *
 * Rolling Summary + Windowed Context 전략:
 * - 최근 3턴(6메시지)은 원문 유지 (windowing)
 * - 나머지는 LLM으로 누적 요약 (rolling summary)
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val geminiRepository: GeminiRepository
) : ChatRepository {

    companion object {
        private const val TAG = "ChatRepository"
        private const val WINDOW_SIZE_TURNS = 3      // 최근 3턴 유지
        private const val WINDOW_SIZE_MESSAGES = 6   // 3턴 = 6메시지 (사용자+AI)
    }

    override suspend fun sendMessageAndBuildContext(
        sessionId: Long,
        userMessage: String
    ): ChatContext {
        // 1. 사용자 메시지 DB 저장
        val userChat = ChatEntity(
            sessionId = sessionId,
            message = userMessage,
            isUser = true
        )
        chatDao.insert(userChat)
        chatDao.updateSessionTimestamp(sessionId)

        // 2. 요약 갱신 (윈도우 밖 메시지가 있으면)
        updateRollingSummary(sessionId)

        // 3. 컨텍스트 구성
        return buildCurrentContext(sessionId)
    }

    override suspend fun saveAiResponseAndUpdateSummary(
        sessionId: Long,
        aiResponse: String
    ) {
        // AI 응답 저장
        val aiChat = ChatEntity(
            sessionId = sessionId,
            message = aiResponse,
            isUser = false
        )
        chatDao.insert(aiChat)
        chatDao.updateSessionTimestamp(sessionId)
    }

    override suspend fun buildCurrentContext(sessionId: Long): ChatContext {
        val summary = chatDao.getSessionSummary(sessionId)
        val recentMessages = chatDao.getRecentChatsBySessionAsc(sessionId, WINDOW_SIZE_MESSAGES)
        val currentMessage = recentMessages.lastOrNull()?.message ?: ""

        return ChatContext(
            summary = summary,
            recentMessages = recentMessages,
            currentUserMessage = currentMessage
        )
    }

    override suspend fun clearSessionSummary(sessionId: Long) {
        chatDao.updateSessionSummary(sessionId, "")
    }

    /**
     * Rolling Summary 갱신
     *
     * 전체 메시지 수가 윈도우 크기를 초과하면,
     * 윈도우 밖의 메시지들을 기존 요약과 함께 새 요약으로 통합
     */
    private suspend fun updateRollingSummary(sessionId: Long) {
        try {
            val totalMessages = chatDao.getMessageCountBySession(sessionId)

            // 윈도우 크기 이하면 요약 불필요
            if (totalMessages <= WINDOW_SIZE_MESSAGES) {
                Log.d(TAG, "메시지 수($totalMessages)가 윈도우 크기 이하, 요약 생략")
                return
            }

            // 모든 메시지를 시간순 조회
            val allMessages = chatDao.getRecentChatsBySessionAsc(sessionId, totalMessages)

            // 윈도우 밖 메시지들 (요약 대상)
            val outsideWindow = allMessages.dropLast(WINDOW_SIZE_MESSAGES)
            if (outsideWindow.isEmpty()) return

            // 텍스트 변환
            val newMessagesText = outsideWindow.joinToString("\n") { msg ->
                val role = if (msg.isUser) "사용자" else "상담사"
                "$role: ${msg.message}"
            }

            // 기존 요약 가져오기
            val existingSummary = chatDao.getSessionSummary(sessionId)

            // LLM으로 새 요약 생성
            val result = geminiRepository.generateRollingSummary(existingSummary, newMessagesText)
            result.onSuccess { newSummary ->
                chatDao.updateSessionSummary(sessionId, newSummary)
                Log.d(TAG, "Rolling Summary 갱신 완료: ${newSummary.take(100)}...")
            }.onFailure { e ->
                Log.e(TAG, "Rolling Summary 생성 실패, 기존 요약 유지", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateRollingSummary 오류", e)
        }
    }
}
