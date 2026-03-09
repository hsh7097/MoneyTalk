package com.sanha.moneytalk.core.model

/**
 * 수입 type 문자열을 앱 카테고리 displayName으로 변환한다.
 */
object IncomeCategoryMapper {
    private val categoryByType = mapOf(
        "급여" to Category.INCOME_SALARY.displayName,
        "보너스" to Category.INCOME_BONUS.displayName,
        "정산" to Category.INCOME_DUTCH_PAY.displayName
    )

    fun categoryForType(type: String): String {
        return categoryByType[type] ?: Category.INCOME_UNCLASSIFIED.displayName
    }

    fun preClassifyMap(): Map<String, String> = categoryByType
}
