package com.sanha.moneytalk.feature.categoryreview.data

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.util.CardVisibilityFilter

/** 설정의 건수와 직접 확인 목록이 함께 사용하는 노출 기준. 통계 제외는 거래를 숨기지 않는다. */
internal object CategoryReviewFilter {
    fun filter(
        expenses: List<ExpenseEntity>,
        exclusionKeywords: Set<String>,
        excludedCardNames: Set<String>
    ): List<ExpenseEntity> = expenses.filter { expense ->
        expense.category == Category.UNCLASSIFIED.displayName &&
            !CardVisibilityFilter.isExcluded(expense.cardName, excludedCardNames) &&
            exclusionKeywords.none { keyword -> expense.originalSms.contains(keyword, ignoreCase = true) }
    }.sortedWith(compareByDescending<ExpenseEntity> { it.dateTime }.thenByDescending { it.id })
}
