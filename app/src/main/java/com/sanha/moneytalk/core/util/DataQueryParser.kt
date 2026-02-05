package com.sanha.moneytalk.core.util

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Gemini가 요청할 수 있는 데이터 쿼리 모델
 * Gemini는 사용자 질문을 분석하여 필요한 쿼리를 JSON으로 반환
 */
data class DataQueryRequest(
    @SerializedName("queries")
    val queries: List<DataQuery> = emptyList()
)

data class DataQuery(
    @SerializedName("type")
    val type: QueryType,

    @SerializedName("startDate")
    val startDate: String? = null,  // "YYYY-MM-DD" 형식

    @SerializedName("endDate")
    val endDate: String? = null,    // "YYYY-MM-DD" 형식

    @SerializedName("category")
    val category: String? = null,   // 특정 카테고리 필터

    @SerializedName("limit")
    val limit: Int? = null          // 결과 개수 제한
)

enum class QueryType {
    @SerializedName("total_expense")
    TOTAL_EXPENSE,              // 총 지출

    @SerializedName("total_income")
    TOTAL_INCOME,               // 총 수입

    @SerializedName("expense_by_category")
    EXPENSE_BY_CATEGORY,        // 카테고리별 지출

    @SerializedName("expense_list")
    EXPENSE_LIST,               // 지출 내역 리스트

    @SerializedName("daily_totals")
    DAILY_TOTALS,               // 일별 총액

    @SerializedName("monthly_totals")
    MONTHLY_TOTALS,             // 월별 총액

    @SerializedName("monthly_income")
    MONTHLY_INCOME              // 설정된 월 수입
}

/**
 * 쿼리 결과를 담는 데이터 클래스
 */
data class QueryResult(
    val queryType: QueryType,
    val data: String  // 사람이 읽기 쉬운 형태의 문자열
)

object DataQueryParser {
    private val gson = Gson()

    /**
     * Gemini 응답에서 JSON 쿼리 요청을 파싱
     */
    fun parseQueryRequest(response: String): DataQueryRequest? {
        return try {
            // JSON 부분만 추출
            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}") + 1
            if (jsonStart == -1 || jsonEnd == 0) return null

            val jsonStr = response.substring(jsonStart, jsonEnd)
            gson.fromJson(jsonStr, DataQueryRequest::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 데이터 쿼리 분석 요청을 위한 프롬프트 생성
     */
    fun createQueryAnalysisPrompt(userMessage: String): String {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
        val currentDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)

        return """
사용자의 질문을 분석하여 필요한 데이터 쿼리를 JSON으로 반환해줘.

[오늘 날짜]
${currentYear}년 ${currentMonth}월 ${currentDay}일

[사용 가능한 쿼리 타입]
- total_expense: 기간 내 총 지출 금액
- total_income: 기간 내 총 수입 금액
- expense_by_category: 카테고리별 지출 합계
- expense_list: 지출 내역 리스트 (limit으로 개수 제한)
- daily_totals: 일별 지출 합계
- monthly_totals: 월별 지출 합계
- monthly_income: 설정된 월 수입

[카테고리 목록]
식비, 카페, 교통, 쇼핑, 구독, 의료/건강, 문화/여가, 교육, 생활, 기타

[사용자 질문]
$userMessage

[규칙]
1. 날짜 형식은 "YYYY-MM-DD"를 사용
2. 연도가 명시되지 않으면 올해(${currentYear}년)로 가정
3. "지난달", "이번달" 등은 오늘 기준으로 계산
4. 질문에 필요한 최소한의 쿼리만 요청
5. JSON만 반환하고 다른 텍스트는 포함하지 마

[예시 응답]
{
  "queries": [
    {
      "type": "total_expense",
      "startDate": "2024-02-01",
      "endDate": "2024-02-29"
    },
    {
      "type": "expense_by_category",
      "startDate": "2024-02-01",
      "endDate": "2024-02-29"
    }
  ]
}

JSON만 반환해:
""".trimIndent()
    }

    /**
     * 쿼리 결과를 포함한 최종 답변 요청 프롬프트
     */
    fun createFinalAnswerPrompt(
        userMessage: String,
        queryResults: List<QueryResult>,
        monthlyIncome: Int
    ): String {
        val resultsText = queryResults.joinToString("\n\n") { result ->
            "[${result.queryType.name}]\n${result.data}"
        }

        return """
너는 친근한 개인 재무 상담사 '머니톡'이야.

[사용자 설정 월 수입]
${String.format("%,d", monthlyIncome)}원

[조회된 데이터]
$resultsText

[사용자 질문]
$userMessage

[답변 규칙]
1. 친근하게 반말로 답변해줘
2. 구체적인 숫자와 함께 실용적인 조언을 해줘
3. 이모지를 적절히 사용해줘
4. 답변은 간결하게, 핵심만 전달해줘
5. 긍정적이고 응원하는 톤을 유지해줘
6. 조회된 데이터를 기반으로 답변해줘
7. 데이터가 없으면 "해당 기간 데이터가 없어요"라고 안내해줘
""".trimIndent()
    }
}
