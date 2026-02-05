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

    companion object {
        private const val TAG = "GeminiChat"

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
- uncategorized_list: 미분류(기타) 항목 리스트
- category_ratio: 수입 대비 카테고리별 비율 분석

[사용 가능한 액션 타입]
- update_category: 특정 지출의 카테고리 변경 (expenseId, newCategory 필수)
- update_category_by_store: 가게명 기준 일괄 카테고리 변경 (storeName, newCategory 필수)
- update_category_by_keyword: 키워드 포함 가게명 일괄 변경 (searchKeyword, newCategory 필수)

[카테고리 목록]
식비, 카페, 교통, 쇼핑, 구독, 의료/건강, 문화/여가, 교육, 생활, 기타

[쿼리 파라미터]
- type: 쿼리 타입 (필수)
- startDate: 시작일 "YYYY-MM-DD" (선택)
- endDate: 종료일 "YYYY-MM-DD" (선택)
- category: 카테고리 필터 (선택)
- storeName: 가게명 필터 - 정확히 일치 (선택)
- limit: 결과 개수 제한 (선택)

[액션 파라미터]
- type: 액션 타입 (필수)
- expenseId: 특정 지출 ID (update_category 시)
- storeName: 가게명 - 정확히 일치 (update_category_by_store 시)
- searchKeyword: 검색 키워드 - 포함 검색 (update_category_by_keyword 시)
- newCategory: 변경할 카테고리 (필수)

[날짜 규칙]
1. 날짜 형식은 "YYYY-MM-DD" 사용
2. 연도 미지정 시 올해로 가정
3. "지난달" = 전월 1일~말일
4. "이번달" = 이번달 1일~오늘
5. "작년 10월부터 올해 2월" = 작년-10-01 ~ 올해-02-말일
6. "2월" (연도 없음) = 올해 2월
7. "3개월간" = 최근 3개월

[질문 패턴 → 쿼리 매핑 예시]
- "2월에 쿠팡에서 얼마 썼어?" → expense_by_store (storeName: "쿠팡", 2월 기간)
- "작년 10월부터 올해 2월까지 총 지출" → total_expense (해당 기간)
- "이번달 식비가 수입 대비 적절해?" → category_ratio + expense_by_category (식비)
- "카페 지출 줄여야 할까?" → category_ratio + expense_by_category (카페) + monthly_income
- "미분류 항목 보여줘" → uncategorized_list
- "쿠팡 결제는 쇼핑으로 분류해줘" → actions: update_category_by_store
- "배달의민족 포함된건 식비로 바꿔줘" → actions: update_category_by_keyword

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
4. 분석/조언 질문은 queries만, 데이터 수정 요청은 actions 포함"""
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
                modelName = "gemini-2.0-flash",
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
                modelName = "gemini-2.0-flash",
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

    // 수동으로 API 키 설정 (설정 화면에서 사용)
    suspend fun setApiKey(key: String) {
        cachedApiKey = key
        queryAnalyzerModel = null
        financialAdvisorModel = null
        settingsDataStore.saveGeminiApiKey(key)
    }

    // API 키 존재 여부 확인
    suspend fun hasApiKey(): Boolean {
        return getApiKey().isNotBlank()
    }

    /**
     * 1단계: 사용자 질문을 분석하여 필요한 데이터 쿼리 결정
     * System Instruction에 스키마가 이미 포함되어 있으므로 간단한 프롬프트만 전송
     */
    suspend fun analyzeQueryNeeds(userMessage: String): Result<DataQueryRequest?> {
        return try {
            Log.d(TAG, "=== analyzeQueryNeeds 시작 ===")
            Log.d(TAG, "사용자 메시지: $userMessage")

            val model = getQueryAnalyzerModel()
            if (model == null) {
                Log.e(TAG, "API 키가 설정되지 않음")
                return Result.failure(Exception("API 키가 설정되지 않았습니다."))
            }

            val apiKey = getApiKey()
            Log.d(TAG, "API 키 (앞 10자): ${apiKey.take(10)}...")

            // 오늘 날짜 정보만 추가 (스키마는 System Instruction에 있음)
            val calendar = Calendar.getInstance()
            val today = "${calendar.get(Calendar.YEAR)}년 ${calendar.get(Calendar.MONTH) + 1}월 ${calendar.get(Calendar.DAY_OF_MONTH)}일"

            val prompt = """오늘: $today

사용자 질문: $userMessage

위 질문에 필요한 데이터 쿼리를 JSON으로 반환해줘:"""

            Log.d(TAG, "프롬프트 전송 중...")
            val response = model.generateContent(prompt)
            val responseText = response.text ?: return Result.success(null)

            Log.d(TAG, "Gemini 응답: $responseText")

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

            Log.d(TAG, "최종 답변 프롬프트:\n$prompt")
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
     * 간단한 채팅 (데이터 없이 일반 대화)
     */
    suspend fun simpleChat(userMessage: String): Result<String> {
        return try {
            Log.d(TAG, "=== simpleChat 시작 ===")
            Log.d(TAG, "사용자 메시지: $userMessage")

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
}
