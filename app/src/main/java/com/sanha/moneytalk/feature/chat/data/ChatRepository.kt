package com.sanha.moneytalk.feature.chat.data

import com.sanha.moneytalk.core.database.entity.ChatEntity

/**
 * 채팅 컨텍스트 관리 Repository
 *
 * Rolling Summary + Windowed Context 전략을 구현하여
 * 대화 맥락을 효율적으로 관리합니다.
 */
interface ChatRepository {
    /**
     * 사용자 메시지 저장 + 요약 갱신 + 컨텍스트 구성
     * @return 현재 대화 컨텍스트 (요약 + 최근 메시지 + 현재 질문)
     */
    suspend fun sendMessageAndBuildContext(sessionId: Long, userMessage: String): ChatContext

    /**
     * AI 응답 저장
     */
    suspend fun saveAiResponseAndUpdateSummary(sessionId: Long, aiResponse: String)

    /**
     * 로컬 정형 조회 결과 저장.
     * Gemini 호출 없는 단순 조회는 Rolling Summary 갱신도 생략한다.
     */
    suspend fun saveLocalExchange(sessionId: Long, userMessage: String, localResponse: String)

    /**
     * 로컬 조회 처리 중 사용자 메시지만 먼저 저장.
     * 조회 실패 시에도 사용자의 질문이 대화 기록에서 사라지지 않게 한다.
     */
    suspend fun saveLocalUserMessage(sessionId: Long, userMessage: String)

    /**
     * 현재 세션의 컨텍스트 조회
     */
    suspend fun buildCurrentContext(sessionId: Long): ChatContext

    /**
     * 세션의 Rolling Summary 초기화
     */
    suspend fun clearSessionSummary(sessionId: Long)
}

/**
 * 대화 컨텍스트 데이터 클래스
 *
 * @param summary 과거 대화 누적 요약본 (없으면 null)
 * @param recentMessages 최근 윈도우 내 메시지들 (시간순 ASC)
 * @param currentUserMessage 현재 사용자 질문
 */
data class ChatContext(
    val summary: String?,
    val recentMessages: List<ChatEntity>,
    val currentUserMessage: String
)
