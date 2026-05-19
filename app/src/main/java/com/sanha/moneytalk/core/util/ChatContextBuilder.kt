package com.sanha.moneytalk.core.util

import android.content.Context
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.ChatEntity
import com.sanha.moneytalk.feature.chat.data.ChatContext
import java.util.Locale

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
    fun buildQueryAnalysisContext(
        context: Context,
        chatContext: ChatContext
    ): String {
        val sb = StringBuilder()

        // 요약이 있으면 포함
        chatContext.summary?.takeIf { it.isNotBlank() }?.let {
            sb.appendLine(context.getString(R.string.ai_chat_section_previous_summary))
            sb.appendLine(it)
            sb.appendLine()
        }

        // 최근 대화 (현재 메시지 제외)
        val recentWithoutCurrent = chatContext.recentMessages.dropLast(1)
        if (recentWithoutCurrent.isNotEmpty()) {
            sb.appendLine(context.getString(R.string.ai_chat_section_recent_messages))
            sb.appendLine(formatMessages(context, recentWithoutCurrent))
            sb.appendLine()
        }

        // 현재 질문
        sb.appendLine(context.getString(R.string.ai_chat_section_current_question))
        sb.appendLine(chatContext.currentUserMessage)

        return sb.toString().trim()
    }

    /**
     * 최종 답변용 프롬프트 구성
     * 대화 맥락 + 쿼리 결과 + 액션 결과를 통합
     */
    fun buildFinalAnswerPrompt(
        context: Context,
        chatContext: ChatContext,
        queryResults: String,
        monthlyIncome: Int?,
        actionResults: String = ""
    ): String {
        val sb = StringBuilder()

        // 대화 요약
        chatContext.summary?.takeIf { it.isNotBlank() }?.let {
            sb.appendLine(context.getString(R.string.ai_chat_section_previous_summary))
            sb.appendLine(it)
            sb.appendLine()
        }

        // 최근 대화 (현재 메시지 제외)
        val recentWithoutCurrent = chatContext.recentMessages.dropLast(1)
        if (recentWithoutCurrent.isNotEmpty()) {
            sb.appendLine(context.getString(R.string.ai_chat_section_recent_messages))
            sb.appendLine(formatMessages(context, recentWithoutCurrent))
            sb.appendLine()
        }

        // 월 수입은 조회/분석에 필요한 경우에만 포함한다.
        monthlyIncome?.let {
            sb.appendLine(
                context.getString(
                    R.string.ai_chat_section_monthly_income,
                    String.format(Locale.KOREA, "%,d", it)
                )
            )
            sb.appendLine()
        }

        // 쿼리 결과
        sb.appendLine(context.getString(R.string.ai_chat_section_queried_data))
        sb.appendLine(
            queryResults.ifBlank {
                context.getString(R.string.prompt_final_answer_empty_data_context)
            }
        )

        // 액션 결과
        if (actionResults.isNotBlank()) {
            sb.appendLine()
            sb.appendLine(context.getString(R.string.ai_chat_section_action_results))
            sb.appendLine(actionResults)
        }

        // 현재 질문
        sb.appendLine()
        sb.appendLine(context.getString(R.string.ai_chat_section_current_question))
        sb.appendLine(chatContext.currentUserMessage)

        return sb.toString().trim()
    }

    /**
     * 메시지 리스트를 텍스트로 변환
     */
    private fun formatMessages(context: Context, messages: List<ChatEntity>): String {
        return messages.joinToString("\n") { msg ->
            val role = if (msg.isUser) {
                context.getString(R.string.ai_chat_role_user)
            } else {
                context.getString(R.string.ai_chat_role_advisor)
            }
            "$role: ${msg.message}"
        }
    }
}
