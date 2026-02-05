package com.sanha.moneytalk.core.util

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Gemini가 요청할 수 있는 데이터 쿼리/액션 모델
 * Gemini는 사용자 질문을 분석하여 필요한 쿼리 또는 액션을 JSON으로 반환
 */
data class DataQueryRequest(
    @SerializedName("queries")
    val queries: List<DataQuery> = emptyList(),

    @SerializedName("actions")
    val actions: List<DataAction> = emptyList()
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

    @SerializedName("storeName")
    val storeName: String? = null,  // 특정 가게명 필터

    @SerializedName("limit")
    val limit: Int? = null          // 결과 개수 제한
)

/**
 * 데이터 수정 액션 (카테고리 변경 등)
 */
data class DataAction(
    @SerializedName("type")
    val type: ActionType,

    @SerializedName("storeName")
    val storeName: String? = null,      // 가게명으로 찾기

    @SerializedName("expenseId")
    val expenseId: Long? = null,        // 특정 지출 ID

    @SerializedName("newCategory")
    val newCategory: String? = null,    // 변경할 카테고리

    @SerializedName("searchKeyword")
    val searchKeyword: String? = null   // 검색 키워드 (가게명에 포함된)
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

    @SerializedName("expense_by_store")
    EXPENSE_BY_STORE,           // 특정 가게 지출

    @SerializedName("daily_totals")
    DAILY_TOTALS,               // 일별 총액

    @SerializedName("monthly_totals")
    MONTHLY_TOTALS,             // 월별 총액

    @SerializedName("monthly_income")
    MONTHLY_INCOME,             // 설정된 월 수입

    @SerializedName("uncategorized_list")
    UNCATEGORIZED_LIST,         // 미분류 항목 리스트

    @SerializedName("category_ratio")
    CATEGORY_RATIO              // 수입 대비 카테고리 비율
}

enum class ActionType {
    @SerializedName("update_category")
    UPDATE_CATEGORY,            // 카테고리 변경

    @SerializedName("update_category_by_store")
    UPDATE_CATEGORY_BY_STORE,   // 가게명 기준 카테고리 일괄 변경

    @SerializedName("update_category_by_keyword")
    UPDATE_CATEGORY_BY_KEYWORD  // 키워드 포함 가게명 일괄 변경
}

/**
 * 쿼리 결과를 담는 데이터 클래스
 */
data class QueryResult(
    val queryType: QueryType,
    val data: String  // 사람이 읽기 쉬운 형태의 문자열
)

/**
 * 액션 결과를 담는 데이터 클래스
 */
data class ActionResult(
    val actionType: ActionType,
    val success: Boolean,
    val message: String,
    val affectedCount: Int = 0
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
