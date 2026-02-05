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
}
