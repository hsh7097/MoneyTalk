package com.sanha.moneytalk.feature.chat.data

import android.content.Context
import com.sanha.moneytalk.R

/**
 * AI 채팅에서 사용하는 모든 시스템 프롬프트를 관리하는 유틸리티
 *
 * 프롬프트 텍스트는 res/values/string_prompt.xml에 정의되어 있으며,
 * 이 클래스를 통해 Context 기반으로 로드합니다.
 *
 * 각 프롬프트는 Gemini 모델의 System Instruction으로 사용됩니다:
 * - [getSummarySystemInstruction]: 대화 요약 모델 (gemini-2.5-flash)
 * - [getQueryAnalyzerSystemInstruction]: 쿼리/액션 분석 모델 (gemini-2.5-pro)
 * - [getFinancialAdvisorSystemInstruction]: 재무 상담 답변 모델 (gemini-2.5-pro)
 */
object ChatPrompts {

    fun getSummarySystemInstruction(context: Context): String =
        context.getString(R.string.prompt_summary_system)

    fun getQueryAnalyzerSystemInstruction(context: Context): String =
        context.getString(R.string.prompt_query_analyzer_system)

    fun getFinancialAdvisorSystemInstruction(context: Context): String =
        context.getString(R.string.prompt_financial_advisor_system)
}
