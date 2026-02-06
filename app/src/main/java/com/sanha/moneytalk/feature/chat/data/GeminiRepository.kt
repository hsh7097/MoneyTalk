package com.sanha.moneytalk.feature.chat.data

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.util.ActionResult
import com.sanha.moneytalk.core.util.DataQueryParser
import com.sanha.moneytalk.core.util.DataQueryRequest
import com.sanha.moneytalk.core.util.QueryResult
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    private var cachedApiKey: String? = null

    // 쿼리 분석용 모델 (System Instruction에 스키마 포함)
    private var queryAnalyzerModel: GenerativeModel? = null

    // 재무 상담용 모델 (System Instruction에 상담사 역할 포함)
    private var financialAdvisorModel: GenerativeModel? = null

    // 요약 전용 모델
    private var summaryModel: GenerativeModel? = null

    companion object {
        private const val TAG = "GeminiChat"
        private const val TAG_PROMPT = "PROMPT"

        // Rolling Summary 전용 시스템 명령어
        private const val SUMMARY_SYSTEM_INSTRUCTION = """당신은 대화 요약 전문가입니다.

[역할]
기존 요약본과 새로운 대화 내용을 통합하여 하나의 간결한 누적 요약본을 생성합니다.

[요약 규칙]
1. 반드시 한국어로 작성
2. 사용자의 핵심 관심사, 질문 의도, 선호도를 반드시 포함
3. 상담사가 제공한 주요 조언과 데이터 포인트 유지
4. 시간순으로 정리하되, 중복 내용은 최신 정보로 통합
5. 금액, 카테고리, 기간 등 구체적인 숫자와 키워드는 보존
6. 200자 이내로 간결하게 작성
7. "~에 대해 물었음", "~를 조언받음" 등 요약체로 작성

[출력 형식]
- 요약본만 반환 (다른 텍스트 없이)
- 마크다운이나 특수 포맷 사용 금지"""

        // 재무 상담사 시스템 명령어
        private const val FINANCIAL_ADVISOR_SYSTEM_INSTRUCTION = """당신은 '머니톡'이라는 친근한 개인 재무 상담 AI입니다.

[역할]
- 사용자의 지출 데이터를 분석하고 재무 조언 제공
- 한국어로 친근하게 반말로 대화
- 이모지를 적절히 사용하여 친근한 느낌 전달

[답변 규칙]
1. 구체적인 숫자와 함께 실용적인 조언 제공
2. 답변은 간결하게, 핵심만 전달
3. 긍정적이고 응원하는 톤 유지
4. 데이터가 없으면 "해당 기간 데이터가 없어요"라고 안내
5. 액션 결과가 있으면 결과를 친절하게 안내

[수입 대비 지출 분석 기준]
- 식비: 수입의 15-20%가 적정
- 주거비: 수입의 25-30%가 적정
- 교통비: 수입의 5-10%가 적정
- 카페/여가: 수입의 5-10%가 적정
- 저축: 수입의 20% 이상 권장
- 총 지출이 수입의 80% 이하면 건강한 재정

[참고]
- 사용자의 지출 데이터는 메시지에 포함되어 제공됨
- 월 수입 정보도 함께 제공됨
- 액션 실행 결과도 포함될 수 있음"""

        // 쿼리/액션 분석용 시스템 명령어
        private const val QUERY_ANALYZER_SYSTEM_INSTRUCTION = """당신은 사용자의 재무 관련 질문을 분석하여 필요한 데이터베이스 쿼리와 액션을 결정하는 AI입니다.

[사용 가능한 쿼리 타입]
- total_expense: 기간 내 총 지출 금액
- total_income: 기간 내 총 수입 금액
- expense_by_category: 카테고리별 지출 합계
- expense_list: 지출 내역 리스트 (limit으로 개수 제한)
- expense_by_store: 특정 가게/브랜드 지출 (storeName 필수)
- daily_totals: 일별 지출 합계
- monthly_totals: 월별 지출 합계
- monthly_income: 설정된 월 수입
- uncategorized_list: 미분류 항목 리스트
- category_ratio: 수입 대비 카테고리별 비율 분석

[사용 가능한 액션 타입]
- update_category: 특정 지출의 카테고리 변경 (expenseId, newCategory 필수)
- update_category_by_store: 가게명 기준 일괄 카테고리 변경 (storeName, newCategory 필수)
- update_category_by_keyword: 키워드 포함 가게명 일괄 변경 (searchKeyword, newCategory 필수)

[카테고리 목록]
식비, 카페, 술/유흥, 교통, 쇼핑, 구독, 의료/건강, 운동, 문화/여가, 교육, 주거, 생활, 경조, 기타, 미분류

[쿼리 파라미터]
- type: 쿼리 타입 (필수)
- startDate: 시작일 "YYYY-MM-DD" (선택)
- endDate: 종료일 "YYYY-MM-DD" (선택)
- category: 카테고리 필터 (선택, 위 카테고리 목록의 displayName 사용)
- storeName: 가게명 필터 - 정확히 일치 (선택)
- limit: 결과 개수 제한 (선택)

[액션 파라미터]
- type: 액션 타입 (필수)
- expenseId: 특정 지출 ID (update_category 시)
- storeName: 가게명 - 정확히 일치 (update_category_by_store 시)
- searchKeyword: 검색 키워드 - 포함 검색 (update_category_by_keyword 시)
- newCategory: 변경할 카테고리 (필수, 위 카테고리 목록에서 선택)

[날짜 규칙]
1. 날짜 형식은 "YYYY-MM-DD" 사용
2. 연도 미지정 시 올해로 가정
3. "지난달" = 전월 1일~말일
4. "이번달" = 이번달 1일~오늘
5. "작년 10월부터 올해 2월" = 작년-10-01 ~ 올해-02-말일
6. "2월" (연도 없음) = 올해 2월
7. "3개월간" = 최근 3개월

[분석 규칙]
1. 금액 관련 질문("얼마", "얼마나", "비용")이 있으면 반드시 해당하는 쿼리를 생성할 것
2. 카테고리명이나 가게명이 언급되면 해당 필터를 적용할 것
3. 기간이 언급되면 반드시 startDate/endDate를 설정할 것
4. "배달비", "배달" 등 키워드는 expense_by_store로 storeName에 "배달"을 사용
5. "줄이다", "절약" 등 조언 요청은 category_ratio를 포함하여 비율 분석 제공
6. 특정 카테고리의 금액을 묻는 질문은 expense_by_category에 category 필터 적용
7. 질문에 기간과 카테고리/가게가 모두 있으면 반드시 둘 다 쿼리에 포함

[질문 패턴 → 쿼리 매핑 예시]
- "2월에 쿠팡에서 얼마 썼어?" → expense_by_store (storeName: "쿠팡", 2월 기간)
- "작년 10월부터 올해 2월까지 총 지출" → total_expense (해당 기간)
- "이번달 식비가 수입 대비 적절해?" → category_ratio + expense_by_category (식비)
- "카페 지출 줄여야 할까?" → category_ratio + expense_by_category (카페) + monthly_income
- "미분류 항목 보여줘" → uncategorized_list
- "쿠팡 결제는 쇼핑으로 분류해줘" → actions: update_category_by_store
- "배달의민족 포함된건 식비로 바꿔줘" → actions: update_category_by_keyword
- "지난 3개월 배달비 얼마야?" → expense_by_store (storeName: "배달", 3개월 기간)
- "술값 줄여야 할까?" → category_ratio + expense_by_category (category: "술/유흥")
- "이번달 운동 관련 지출" → expense_by_category (category: "운동", 이번달 기간)

[응답 형식]
{
  "queries": [
    {"type": "쿼리타입", "startDate": "시작일", "endDate": "종료일", "category": "카테고리", "storeName": "가게명", "limit": 10}
  ],
  "actions": [
    {"type": "액션타입", "storeName": "가게명", "searchKeyword": "키워드", "newCategory": "새카테고리"}
  ]
}

[중요]
1. 질문에 필요한 최소한의 쿼리/액션만 요청
2. JSON만 반환 (다른 텍스트 없이)
3. 액션은 사용자가 명시적으로 변경을 요청할 때만 포함
4. 분석/조언 질문은 queries만, 데이터 수정 요청은 actions 포함
5. 대화 맥락([이전 대화 요약], [최근 대화])을 참고하여 대명사나 생략된 주어를 해석할 것
6. 반드시 queries 또는 actions 중 하나 이상은 비어있지 않게 반환할 것"""
    }

    // DataStore에서 API 키 가져오기 (캐싱)
    private suspend fun getApiKey(): String {
        if (cachedApiKey.isNullOrBlank()) {
            cachedApiKey = settingsDataStore.getGeminiApiKey()
        }
        return cachedApiKey ?: ""
    }

    // 쿼리 분석용 모델 가져오기 (System Instruction 포함)
    private suspend fun getQueryAnalyzerModel(): GenerativeModel? {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return null

        if (queryAnalyzerModel == null || cachedApiKey != apiKey) {
            queryAnalyzerModel = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.3f  // 쿼리 분석은 정확도가 중요
                    topK = 20
                    topP = 0.9f
                    maxOutputTokens = 512
                },
                systemInstruction = content { text(QUERY_ANALYZER_SYSTEM_INSTRUCTION) }
            )
        }
        return queryAnalyzerModel
    }

    // 재무 상담용 모델 가져오기 (System Instruction 포함)
    private suspend fun getFinancialAdvisorModel(): GenerativeModel? {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return null

        if (financialAdvisorModel == null || cachedApiKey != apiKey) {
            financialAdvisorModel = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.7f
                    topK = 40
                    topP = 0.95f
                    maxOutputTokens = 1024
                },
                systemInstruction = content { text(FINANCIAL_ADVISOR_SYSTEM_INSTRUCTION) }
            )
        }
        return financialAdvisorModel
    }

    // 요약 전용 모델 가져오기 (System Instruction 포함)
    private suspend fun getSummaryModel(): GenerativeModel? {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return null

        if (summaryModel == null || cachedApiKey != apiKey) {
            summaryModel = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.3f  // 요약은 정확도가 중요
                    topK = 20
                    topP = 0.9f
                    maxOutputTokens = 512
                },
                systemInstruction = content { text(SUMMARY_SYSTEM_INSTRUCTION) }
            )
        }
        return summaryModel
    }

    // 수동으로 API 키 설정 (설정 화면에서 사용)
    suspend fun setApiKey(key: String) {
        cachedApiKey = key
        queryAnalyzerModel = null
        financialAdvisorModel = null
        summaryModel = null
        settingsDataStore.saveGeminiApiKey(key)
    }

    // API 키 존재 여부 확인
    suspend fun hasApiKey(): Boolean {
        return getApiKey().isNotBlank()
    }

    /**
     * 1단계: 사용자 질문을 분석하여 필요한 데이터 쿼리 결정
     * System Instruction에 스키마가 이미 포함되어 있으므로,
     * 대화 맥락(요약 + 최근 대화)과 오늘 날짜를 포함한 프롬프트를 전송
     *
     * @param contextualMessage ChatContextBuilder.buildQueryAnalysisContext()가 생성한 컨텍스트 포함 메시지
     */
    suspend fun analyzeQueryNeeds(contextualMessage: String): Result<DataQueryRequest?> {
        return try {
            Log.d(TAG, "=== analyzeQueryNeeds 시작 ===")

            val model = getQueryAnalyzerModel()
            if (model == null) {
                Log.e(TAG, "API 키가 설정되지 않음")
                return Result.failure(Exception("API 키가 설정되지 않았습니다."))
            }

            // 오늘 날짜 정보 추가 (스키마는 System Instruction에 있음)
            val calendar = Calendar.getInstance()
            val today = "${calendar.get(Calendar.YEAR)}년 ${calendar.get(Calendar.MONTH) + 1}월 ${calendar.get(Calendar.DAY_OF_MONTH)}일"

            val prompt = """오늘: $today

$contextualMessage

위 질문에 필요한 데이터 쿼리를 JSON으로 반환해줘:"""

            Log.d(TAG_PROMPT, "=== 쿼리 분석 프롬프트 ===")
            Log.d(TAG_PROMPT, prompt)
            Log.d(TAG_PROMPT, "=== 프롬프트 끝 (길이: ${prompt.length}) ===")

            val response = model.generateContent(prompt)
            val responseText = response.text ?: return Result.success(null)

            Log.d(TAG, "Gemini 쿼리 분석 응답: $responseText")

            val queryRequest = DataQueryParser.parseQueryRequest(responseText)
            Log.d(TAG, "파싱된 쿼리: $queryRequest")
            Result.success(queryRequest)
        } catch (e: Exception) {
            Log.e(TAG, "쿼리 분석 실패", e)
            Log.e(TAG, "에러 메시지: ${e.message}")
            Log.e(TAG, "에러 클래스: ${e.javaClass.simpleName}")
            Result.failure(Exception("쿼리 분석 실패: ${e.message}"))
        }
    }

    /**
     * 2단계: 쿼리/액션 결과를 바탕으로 최종 답변 생성
     * System Instruction에 상담사 역할이 이미 포함되어 있으므로 데이터만 전송
     */
    suspend fun generateFinalAnswer(
        userMessage: String,
        queryResults: List<QueryResult>,
        monthlyIncome: Int,
        actionResults: List<ActionResult> = emptyList()
    ): Result<String> {
        return try {
            Log.d(TAG, "=== generateFinalAnswer 시작 ===")
            Log.d(TAG, "사용자 메시지: $userMessage")
            Log.d(TAG, "쿼리 결과 수: ${queryResults.size}")
            Log.d(TAG, "액션 결과 수: ${actionResults.size}")

            val model = getFinancialAdvisorModel()
            if (model == null) {
                Log.e(TAG, "API 키가 설정되지 않음")
                return Result.failure(Exception("API 키가 설정되지 않았습니다."))
            }

            // 데이터만 전송 (역할/규칙은 System Instruction에 있음)
            val dataContext = queryResults.joinToString("\n\n") { result ->
                "[${result.queryType.name}]\n${result.data}"
            }

            val actionContext = if (actionResults.isNotEmpty()) {
                "\n\n[실행된 액션 결과]\n" + actionResults.joinToString("\n") { result ->
                    "- ${result.message}"
                }
            } else ""

            val prompt = """[월 수입] ${String.format("%,d", monthlyIncome)}원

[조회된 데이터]
$dataContext$actionContext

[사용자 질문]
$userMessage"""

            Log.d(TAG_PROMPT, "=== 최종 답변 프롬프트 ===")
            Log.d(TAG_PROMPT, prompt)
            Log.d(TAG_PROMPT, "=== 프롬프트 끝 (길이: ${prompt.length}) ===")
            Log.d(TAG, "Gemini 호출 중...")

            val response = model.generateContent(prompt)
            val responseText = response.text ?: "응답을 받지 못했어요."

            Log.d(TAG, "Gemini 최종 응답: $responseText")

            Result.success(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "최종 답변 생성 실패", e)
            Log.e(TAG, "에러 메시지: ${e.message}")
            Log.e(TAG, "에러 클래스: ${e.javaClass.simpleName}")
            Result.failure(Exception("요청 실패: ${e.message}"))
        }
    }

    /**
     * 대화 컨텍스트가 포함된 프롬프트로 최종 답변 생성
     *
     * ChatContextBuilder가 구성한 [요약 + 최근 대화 + 데이터 + 질문] 프롬프트를 그대로 전달
     * Rolling Summary 전략과 함께 사용하여 대화 맥락을 유지하면서 답변 생성
     *
     * @param contextPrompt ChatContextBuilder.buildFinalAnswerPrompt()가 생성한 통합 프롬프트
     * @return AI 응답 텍스트
     */
    suspend fun generateFinalAnswerWithContext(contextPrompt: String): Result<String> {
        return try {
            Log.d(TAG, "=== generateFinalAnswerWithContext 시작 ===")
            Log.d(TAG_PROMPT, "=== 컨텍스트 기반 최종 답변 프롬프트 ===")
            Log.d(TAG_PROMPT, contextPrompt)
            Log.d(TAG_PROMPT, "=== 프롬프트 끝 (길이: ${contextPrompt.length}) ===")

            val model = getFinancialAdvisorModel()
            if (model == null) {
                return Result.failure(Exception("API 키가 설정되지 않았습니다."))
            }

            val response = model.generateContent(contextPrompt)
            val responseText = response.text ?: "응답을 받지 못했어요."

            Log.d(TAG, "Gemini 최종 응답: ${responseText.take(200)}...")
            Result.success(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "컨텍스트 기반 답변 생성 실패", e)
            Result.failure(Exception("요청 실패: ${e.message}"))
        }
    }

    /**
     * 간단한 채팅 (데이터 없이 일반 대화)
     */
    suspend fun simpleChat(userMessage: String): Result<String> {
        return try {
            Log.d(TAG, "=== simpleChat 시작 ===")
            Log.d(TAG_PROMPT, "=== 심플 채팅 프롬프트 ===")
            Log.d(TAG_PROMPT, userMessage)
            Log.d(TAG_PROMPT, "=== 프롬프트 끝 (길이: ${userMessage.length}) ===")

            val model = getFinancialAdvisorModel()
            if (model == null) {
                Log.e(TAG, "API 키가 설정되지 않음")
                return Result.failure(Exception("API 키가 설정되지 않았습니다. 설정에서 Gemini API 키를 입력해주세요."))
            }

            Log.d(TAG, "Gemini 호출 중...")
            val response = model.generateContent(userMessage)
            val responseText = response.text ?: "응답을 받지 못했어요."

            Log.d(TAG, "Gemini 응답: $responseText")

            Result.success(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "simpleChat 실패", e)
            Log.e(TAG, "에러 메시지: ${e.message}")
            Log.e(TAG, "에러 클래스: ${e.javaClass.simpleName}")
            Result.failure(Exception("요청 실패: ${e.message}"))
        }
    }

    /**
     * Rolling Summary 생성
     *
     * 기존 요약본과 새로운 대화 내용을 통합하여 누적 요약본을 생성한다.
     *
     * @param existingSummary 기존 누적 요약본 (첫 요약이면 null)
     * @param newMessages 윈도우 밖으로 밀려난 새로운 메시지들 (텍스트)
     * @return 새로운 통합 요약본
     */
    suspend fun generateRollingSummary(
        existingSummary: String?,
        newMessages: String
    ): Result<String> {
        return try {
            Log.d(TAG, "=== Rolling Summary 생성 시작 ===")

            val model = getSummaryModel()
            if (model == null) {
                return Result.failure(Exception("API 키가 설정되지 않았습니다."))
            }

            val prompt = if (existingSummary.isNullOrBlank()) {
                // 첫 요약: 새 메시지만으로 요약 생성
                """다음 대화 내용을 요약해주세요:

$newMessages"""
            } else {
                // 누적 요약: 기존 요약 + 새 메시지를 통합
                """다음 기존 요약본과 새로운 대화 내용을 통합하여 하나의 누적 요약본을 생성해주세요.

[기존 요약본]
$existingSummary

[새로운 대화 내용]
$newMessages"""
            }

            Log.d(TAG_PROMPT, "=== 요약 프롬프트 ===")
            Log.d(TAG_PROMPT, prompt)
            Log.d(TAG_PROMPT, "=== 프롬프트 끝 (길이: ${prompt.length}) ===")
            val response = model.generateContent(prompt)
            val summaryText = response.text ?: return Result.failure(Exception("요약 응답 없음"))

            Log.d(TAG, "생성된 요약: $summaryText")
            Result.success(summaryText.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Rolling Summary 생성 실패", e)
            Result.failure(Exception("요약 생성 실패: ${e.message}"))
        }
    }
}
