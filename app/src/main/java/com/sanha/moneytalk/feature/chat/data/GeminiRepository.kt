package com.sanha.moneytalk.feature.chat.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.sanha.moneytalk.core.datastore.SettingsDataStore
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
        // 재무 상담사 시스템 명령어 (모델 초기화 시 1회 설정)
        private const val FINANCIAL_ADVISOR_SYSTEM_INSTRUCTION = """당신은 '머니톡'이라는 친근한 개인 재무 상담 AI입니다.

[역할]
- 사용자의 지출 데이터를 분석하고 재무 조언 제공
- 한국어로 친근하게 반말로 대화
- 이모지를 적절히 사용하여 친근한 느낌 전달

[답변 규칙]
1. 구체적인 숫자와 함께 실용적인 조언
2. 답변은 간결하게, 핵심만 전달
3. 긍정적이고 응원하는 톤 유지
4. 데이터가 없으면 "해당 기간 데이터가 없어요"라고 안내

[참고]
- 사용자의 지출 데이터는 메시지에 포함되어 제공됨
- 월 수입 정보도 함께 제공됨"""

        // 쿼리 분석용 시스템 명령어 (모델 초기화 시 1회 설정)
        private const val QUERY_ANALYZER_SYSTEM_INSTRUCTION = """당신은 사용자의 재무 관련 질문을 분석하여 필요한 데이터베이스 쿼리를 결정하는 AI입니다.

[사용 가능한 쿼리 타입]
- total_expense: 기간 내 총 지출 금액
- total_income: 기간 내 총 수입 금액
- expense_by_category: 카테고리별 지출 합계
- expense_list: 지출 내역 리스트 (limit으로 개수 제한 가능)
- daily_totals: 일별 지출 합계
- monthly_totals: 월별 지출 합계
- monthly_income: 설정된 월 수입

[카테고리 목록]
식비, 카페, 교통, 쇼핑, 구독, 의료/건강, 문화/여가, 교육, 생활, 기타

[쿼리 파라미터]
- type: 쿼리 타입 (필수)
- startDate: 시작일 "YYYY-MM-DD" (선택)
- endDate: 종료일 "YYYY-MM-DD" (선택)
- category: 카테고리 필터 (선택)
- limit: 결과 개수 제한 (선택)

[규칙]
1. 날짜 형식은 "YYYY-MM-DD" 사용
2. 연도 미지정 시 올해로 가정
3. "지난달", "이번달" 등은 오늘 기준 계산
4. 질문에 필요한 최소한의 쿼리만 요청
5. JSON만 반환 (다른 텍스트 없이)

[응답 형식]
{
  "queries": [
    {"type": "쿼리타입", "startDate": "시작일", "endDate": "종료일"}
  ]
}"""
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
                modelName = "gemini-1.5-flash",
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
                modelName = "gemini-1.5-flash",
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
            val model = getQueryAnalyzerModel()
            if (model == null) {
                return Result.failure(Exception("API 키가 설정되지 않았습니다."))
            }

            // 오늘 날짜 정보만 추가 (스키마는 System Instruction에 있음)
            val calendar = Calendar.getInstance()
            val today = "${calendar.get(Calendar.YEAR)}년 ${calendar.get(Calendar.MONTH) + 1}월 ${calendar.get(Calendar.DAY_OF_MONTH)}일"

            val prompt = """오늘: $today

사용자 질문: $userMessage

위 질문에 필요한 데이터 쿼리를 JSON으로 반환해줘:"""

            val response = model.generateContent(prompt)
            val responseText = response.text ?: return Result.success(null)

            val queryRequest = DataQueryParser.parseQueryRequest(responseText)
            Result.success(queryRequest)
        } catch (e: Exception) {
            Result.failure(Exception("쿼리 분석 실패: ${e.message}"))
        }
    }

    /**
     * 2단계: 쿼리 결과를 바탕으로 최종 답변 생성
     * System Instruction에 상담사 역할이 이미 포함되어 있으므로 데이터만 전송
     */
    suspend fun generateFinalAnswer(
        userMessage: String,
        queryResults: List<QueryResult>,
        monthlyIncome: Int
    ): Result<String> {
        return try {
            val model = getFinancialAdvisorModel()
            if (model == null) {
                return Result.failure(Exception("API 키가 설정되지 않았습니다."))
            }

            // 데이터만 전송 (역할/규칙은 System Instruction에 있음)
            val dataContext = queryResults.joinToString("\n\n") { result ->
                "[${result.queryType.name}]\n${result.data}"
            }

            val prompt = """[월 수입] ${String.format("%,d", monthlyIncome)}원

[조회된 데이터]
$dataContext

[사용자 질문]
$userMessage"""

            val response = model.generateContent(prompt)
            val responseText = response.text ?: "응답을 받지 못했어요."

            Result.success(responseText)
        } catch (e: Exception) {
            Result.failure(Exception("요청 실패: ${e.message}"))
        }
    }

    /**
     * 간단한 채팅 (데이터 없이 일반 대화)
     */
    suspend fun simpleChat(userMessage: String): Result<String> {
        return try {
            val model = getFinancialAdvisorModel()
            if (model == null) {
                return Result.failure(Exception("API 키가 설정되지 않았습니다. 설정에서 Gemini API 키를 입력해주세요."))
            }

            val response = model.generateContent(userMessage)
            val responseText = response.text ?: "응답을 받지 못했어요."

            Result.success(responseText)
        } catch (e: Exception) {
            Result.failure(Exception("요청 실패: ${e.message}"))
        }
    }
}
