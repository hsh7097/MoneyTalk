package com.sanha.moneytalk.feature.chat.data

import com.sanha.moneytalk.core.util.ActionResult
import com.sanha.moneytalk.core.util.DataQueryRequest
import com.sanha.moneytalk.core.util.QueryResult

/**
 * Gemini AI Repository 인터페이스
 *
 * AI 채팅 시스템의 핵심 Repository로, 3-step 파이프라인의 Step 1(쿼리 분석)과
 * Step 3(최종 답변 생성)을 담당합니다.
 *
 * 주요 기능:
 * - 쿼리 분석: 사용자 질문 → 필요한 DB 쿼리/액션 결정
 * - 최종 답변: 쿼리 결과 + 대화 컨텍스트 → 재무 상담 답변 생성
 * - Rolling Summary: 대화 내용 누적 요약
 * - 홈 인사이트: 한줄 AI 코멘트 생성
 *
 * @see GeminiRepositoryImpl 구현체
 * @see ChatRepository 채팅 데이터 관리
 */
interface GeminiRepository {

    /** API 키 설정 (설정 화면에서 사용) */
    suspend fun setApiKey(key: String)

    /** API 키 존재 여부 확인 */
    suspend fun hasApiKey(): Boolean

    /**
     * 1단계: 사용자 질문을 분석하여 필요한 데이터 쿼리 결정
     *
     * @param contextualMessage ChatContextBuilder가 생성한 컨텍스트 포함 메시지
     * @return 파싱된 쿼리 요청 (null이면 데이터 불필요)
     */
    suspend fun analyzeQueryNeeds(contextualMessage: String): Result<DataQueryRequest?>

    /**
     * 2단계: 쿼리/액션 결과를 바탕으로 최종 답변 생성
     *
     * @param userMessage 사용자 원본 메시지
     * @param queryResults DB 쿼리 결과 목록
     * @param monthlyIncome 월 수입 (컨텍스트용)
     * @param actionResults 실행된 액션 결과 목록
     */
    suspend fun generateFinalAnswer(
        userMessage: String,
        queryResults: List<QueryResult>,
        monthlyIncome: Int,
        actionResults: List<ActionResult> = emptyList()
    ): Result<String>

    /**
     * 대화 컨텍스트가 포함된 프롬프트로 최종 답변 생성
     *
     * @param contextPrompt ChatContextBuilder가 생성한 통합 프롬프트
     */
    suspend fun generateFinalAnswerWithContext(contextPrompt: String): Result<String>

    /** 간단한 채팅 (데이터 없이 일반 대화) */
    suspend fun simpleChat(userMessage: String): Result<String>

    /**
     * 대화 내용 기반 채팅방 타이틀 생성
     *
     * @param recentMessages 최근 대화 내용
     * @return 생성된 타이틀 (실패 시 null)
     */
    suspend fun generateChatTitle(recentMessages: String): String?

    /**
     * Rolling Summary 생성
     *
     * @param existingSummary 기존 누적 요약본 (첫 요약이면 null)
     * @param newMessages 윈도우 밖으로 밀려난 새로운 메시지들
     */
    suspend fun generateRollingSummary(
        existingSummary: String?,
        newMessages: String
    ): Result<String>

    /**
     * 홈 화면 AI 인사이트 생성 (한줄 코멘트)
     *
     * @param monthlyExpense 이번 달 총 지출
     * @param lastMonthExpense 지난 달 총 지출
     * @param todayExpense 오늘 지출
     * @param topCategories 주요 카테고리별 지출 (이름, 금액)
     * @param lastMonthTopCategories 전월 동일 카테고리 지출 (이번 달 TOP 3 기준)
     */
    suspend fun generateHomeInsight(
        monthlyExpense: Int,
        lastMonthExpense: Int,
        todayExpense: Int,
        topCategories: List<Pair<String, Int>>,
        lastMonthTopCategories: List<Pair<String, Int>> = emptyList()
    ): String?
}
