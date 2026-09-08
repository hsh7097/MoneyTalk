package com.sanha.moneytalk.feature.categoryreview.ui

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface CategoryReviewUiState {
    data object Loading : CategoryReviewUiState
    data object Error : CategoryReviewUiState
    data class Content(val days: List<CategoryReviewDay>) : CategoryReviewUiState {
        val count: Int = days.sumOf { it.expenses.size }

        companion object {
            fun from(expenses: List<ExpenseEntity>, zoneId: ZoneId = ZoneId.systemDefault()): Content =
                Content(expenses.groupBy {
                    Instant.ofEpochMilli(it.dateTime).atZone(zoneId).toLocalDate()
                }.map { (date, items) -> CategoryReviewDay(date, items) })
        }
    }
}

data class CategoryReviewDay(val date: LocalDate, val expenses: List<ExpenseEntity>)
