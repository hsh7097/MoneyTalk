package com.sanha.moneytalk.feature.home.ui.model

import com.sanha.moneytalk.core.database.dao.CategorySum
import com.sanha.moneytalk.core.model.Category

/** 카테고리 순위 행의 표시 값. 예산과 지출 비중 계산은 렌더링 전에 끝낸다. */
data class HomeCategoryExpenseInfo(
    val category: String,
    val total: Int,
    val percentage: Int,
    val progress: Float,
    val hasBudget: Boolean,
    val isOverBudget: Boolean,
    val isWarningBudget: Boolean
)

internal object HomeCategoryExpenseMapper {
    fun build(
        expenses: List<CategorySum>,
        budgets: Map<String, Int>
    ): List<HomeCategoryExpenseInfo> {
        val unclassified = Category.UNCLASSIFIED.displayName
        val ranked = expenses.filter { it.category != unclassified }
            .sortedByDescending { it.total }
        val unclassifiedTotal = expenses.filter { it.category == unclassified }.sumOf { it.total }
        val merged = if (unclassifiedTotal > 0) {
            ranked + CategorySum(unclassified, unclassifiedTotal)
        } else {
            ranked
        }
        val totalExpense = merged.sumOf { it.total }

        return merged.map { item ->
            val budget = budgets[item.category] ?: 0
            val hasBudget = budget > 0
            val ratio = when {
                hasBudget -> item.total.toFloat() / budget
                totalExpense > 0 -> item.total.toFloat() / totalExpense
                else -> 0f
            }
            HomeCategoryExpenseInfo(
                category = item.category,
                total = item.total,
                percentage = (ratio * 100).toInt(),
                progress = if (hasBudget) ratio.coerceAtMost(1f) else ratio,
                hasBudget = hasBudget,
                isOverBudget = hasBudget && item.total > budget,
                isWarningBudget = hasBudget && ratio in 0.9f..1f
            )
        }
    }
}
