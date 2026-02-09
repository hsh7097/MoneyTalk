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

    @SerializedName("cardName")
    val cardName: String? = null,   // 특정 카드명 필터

    @SerializedName("searchKeyword")
    val searchKeyword: String? = null,  // 검색 키워드

    @SerializedName("limit")
    val limit: Int? = null,         // 결과 개수 제한

    // === ANALYTICS 전용 필드 ===

    /** 필터 조건 배열 (모두 AND 결합) */
    @SerializedName("filters")
    val filters: List<AnalyticsFilter>? = null,

    /** 그룹핑 기준: "category"|"storeName"|"cardName"|"date"|"month"|"dayOfWeek" */
    @SerializedName("groupBy")
    val groupBy: String? = null,

    /** 집계 메트릭 배열 */
    @SerializedName("metrics")
    val metrics: List<AnalyticsMetric>? = null,

    /** 그룹 결과 상위 N개 */
    @SerializedName("topN")
    val topN: Int? = null,

    /** 정렬 방향: "desc"|"asc" */
    @SerializedName("sort")
    val sort: String? = null
)

/**
 * ANALYTICS 쿼리의 필터 조건
 * 모든 필터는 AND로 결합됨
 */
data class AnalyticsFilter(
    /** 필터 대상 필드: "category"|"storeName"|"cardName"|"amount"|"memo"|"dayOfWeek" */
    @SerializedName("field")
    val field: String,

    /** 비교 연산자: "=="|"!="|">"|">="|"<"|"<="|"contains"|"not_contains"|"in"|"not_in" */
    @SerializedName("op")
    val op: String,

    /** 비교 값 (String, Number, 또는 List<String>) */
    @SerializedName("value")
    val value: Any? = null,

    /** true이면 하위 카테고리 포함 (예: 식비 → 배달 포함) */
    @SerializedName("includeSubcategories")
    val includeSubcategories: Boolean = false
)

/**
 * ANALYTICS 쿼리의 집계 메트릭
 */
data class AnalyticsMetric(
    /** 집계 연산: "sum"|"avg"|"count"|"max"|"min" */
    @SerializedName("op")
    val op: String,

    /** 집계 대상 필드 (기본: "amount") */
    @SerializedName("field")
    val field: String = "amount"
)

/**
 * 데이터 수정 액션 (카테고리 변경, 삭제, 추가, 수정 등)
 */
data class DataAction(
    @SerializedName("type")
    val type: ActionType,

    @SerializedName("storeName")
    val storeName: String? = null,      // 가게명으로 찾기 / 추가 시 가게명

    @SerializedName("expenseId")
    val expenseId: Long? = null,        // 특정 지출 ID

    @SerializedName("newCategory")
    val newCategory: String? = null,    // 변경할 카테고리

    @SerializedName("searchKeyword")
    val searchKeyword: String? = null,  // 검색 키워드 (가게명에 포함된)

    @SerializedName("amount")
    val amount: Int? = null,            // 금액 (추가/수정 시)

    @SerializedName("date")
    val date: String? = null,           // 날짜 "YYYY-MM-DD" (추가 시)

    @SerializedName("cardName")
    val cardName: String? = null,       // 카드명 (추가 시)

    @SerializedName("memo")
    val memo: String? = null,           // 메모 (추가/수정 시)

    @SerializedName("newStoreName")
    val newStoreName: String? = null,   // 변경할 가게명 (가게명 수정 시)

    @SerializedName("newAmount")
    val newAmount: Int? = null          // 변경할 금액 (금액 수정 시)
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

    @SerializedName("expense_by_card")
    EXPENSE_BY_CARD,            // 특정 카드 지출

    @SerializedName("daily_totals")
    DAILY_TOTALS,               // 일별 총액

    @SerializedName("monthly_totals")
    MONTHLY_TOTALS,             // 월별 총액

    @SerializedName("monthly_income")
    MONTHLY_INCOME,             // 설정된 월 수입

    @SerializedName("uncategorized_list")
    UNCATEGORIZED_LIST,         // 미분류 항목 리스트

    @SerializedName("category_ratio")
    CATEGORY_RATIO,             // 수입 대비 카테고리 비율

    @SerializedName("search_expense")
    SEARCH_EXPENSE,             // 가게명/카테고리/카드로 검색

    @SerializedName("card_list")
    CARD_LIST,                  // 사용 중인 카드 목록

    @SerializedName("income_list")
    INCOME_LIST,                // 수입 내역 리스트

    @SerializedName("duplicate_list")
    DUPLICATE_LIST,             // 중복 지출 항목 리스트

    @SerializedName("sms_exclusion_list")
    SMS_EXCLUSION_LIST,         // SMS 제외 키워드 목록

    @SerializedName("analytics")
    ANALYTICS                   // 복합 조건 분석 (필터+그룹+집계를 앱에서 계산)
}

enum class ActionType {
    @SerializedName("update_category")
    UPDATE_CATEGORY,            // 카테고리 변경 (expenseId, newCategory 필수)

    @SerializedName("update_category_by_store")
    UPDATE_CATEGORY_BY_STORE,   // 가게명 기준 카테고리 일괄 변경 (storeName, newCategory 필수)

    @SerializedName("update_category_by_keyword")
    UPDATE_CATEGORY_BY_KEYWORD, // 키워드 포함 가게명 일괄 변경 (searchKeyword, newCategory 필수)

    @SerializedName("delete_expense")
    DELETE_EXPENSE,             // 특정 지출 삭제 (expenseId 필수)

    @SerializedName("delete_by_keyword")
    DELETE_BY_KEYWORD,          // 키워드 기반 일괄 삭제 (searchKeyword 필수)

    @SerializedName("delete_duplicates")
    DELETE_DUPLICATES,          // 중복 지출 일괄 삭제

    @SerializedName("add_expense")
    ADD_EXPENSE,                // 지출 수동 추가 (storeName, amount, date 필수)

    @SerializedName("update_memo")
    UPDATE_MEMO,                // 메모 수정 (expenseId, memo 필수)

    @SerializedName("update_store_name")
    UPDATE_STORE_NAME,          // 가게명 수정 (expenseId, newStoreName 필수)

    @SerializedName("update_amount")
    UPDATE_AMOUNT,              // 금액 수정 (expenseId, newAmount 필수)

    @SerializedName("add_sms_exclusion")
    ADD_SMS_EXCLUSION,          // SMS 제외 키워드 추가 (searchKeyword 필수)

    @SerializedName("remove_sms_exclusion")
    REMOVE_SMS_EXCLUSION        // SMS 제외 키워드 삭제 (searchKeyword 필수)
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
