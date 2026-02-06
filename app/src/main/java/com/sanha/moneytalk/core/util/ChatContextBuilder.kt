package com.sanha.moneytalk.core.util

import com.sanha.moneytalk.core.database.entity.ChatEntity
import com.sanha.moneytalk.feature.chat.data.ChatContext

/**
 * LLM 입력 프롬프트 구성 유틸리티
 *
 * Rolling Summary + Windowed Context를 활용하여
 * [시스템 프롬프트 + 요약 + 최근 대화 + 현재 질문] 구조의 프롬프트를 생성
 */
object ChatContextBuilder {

    /**
     * 쿼리 분석용 컨텍스트 구성
     * 대화 맥락을 포함하여 쿼리 분석 정확도 향상
     */
    fun buildQueryAnalysisContext(context: ChatContext): String {
        val sb = StringBuilder()

        // 요약이 있으면 포함
        context.summary?.takeIf { it.isNotBlank() }?.let {
            sb.appendLine("[이전 대화 요약]")
            sb.appendLine(it)
            sb.appendLine()
        }

        // 최근 대화 (현재 메시지 제외)
        val recentWithoutCurrent = context.recentMessages.dropLast(1)
        if (recentWithoutCurrent.isNotEmpty()) {
            sb.appendLine("[최근 대화]")
            sb.appendLine(formatMessages(recentWithoutCurrent))
            sb.appendLine()
        }

        // 현재 질문
        sb.appendLine("[현재 질문]")
        sb.appendLine(context.currentUserMessage)

        return sb.toString().trim()
    }

    /**
     * 최종 답변용 프롬프트 구성
     * 대화 맥락 + 쿼리 결과 + 액션 결과를 통합
     */
    fun buildFinalAnswerPrompt(
        context: ChatContext,
        queryResults: String,
        monthlyIncome: Int,
        actionResults: String = ""
    ): String {
        val sb = StringBuilder()

        // 대화 요약
        context.summary?.takeIf { it.isNotBlank() }?.let {
            sb.appendLine("[이전 대화 요약]")
            sb.appendLine(it)
            sb.appendLine()
        }

        // 최근 대화 (현재 메시지 제외)
        val recentWithoutCurrent = context.recentMessages.dropLast(1)
        if (recentWithoutCurrent.isNotEmpty()) {
            sb.appendLine("[최근 대화]")
            sb.appendLine(formatMessages(recentWithoutCurrent))
            sb.appendLine()
        }

        // 월 수입
        sb.appendLine("[월 수입] ${String.format("%,d", monthlyIncome)}원")
        sb.appendLine()

        // 쿼리 결과
        sb.appendLine("[조회된 데이터]")
        sb.appendLine(queryResults)

        // 액션 결과
        if (actionResults.isNotBlank()) {
            sb.appendLine()
            sb.appendLine("[실행된 액션 결과]")
            sb.appendLine(actionResults)
        }

        // 현재 질문
        sb.appendLine()
        sb.appendLine("[현재 질문]")
        sb.appendLine(context.currentUserMessage)

        return sb.toString().trim()
    }

    /**
     * 메시지 리스트를 텍스트로 변환
     */
    private fun formatMessages(messages: List<ChatEntity>): String {
        return messages.joinToString("\n") { msg ->
            val role = if (msg.isUser) "사용자" else "상담사"
            "$role: ${msg.message}"
        }
    }
}
