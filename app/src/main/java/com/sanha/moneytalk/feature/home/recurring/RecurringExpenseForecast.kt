package com.sanha.moneytalk.feature.home.recurring

import java.time.LocalDate

/** 실제 거래를 만들거나 예산에서 차감하지 않는, 과거 고정 지출의 다음 결제 예상. */
data class RecurringExpenseForecast(
    val asOfDate: LocalDate,
    val untilDate: LocalDate,
    val items: List<RecurringExpenseForecastItem>
) {
    val totalExpectedAmount: Long
        get() = items.sumOf { it.expectedAmount }
}

data class RecurringExpenseForecastItem(
    val sourceExpenseId: Long,
    val storeName: String,
    val cardName: String,
    val category: String,
    val expectedDate: LocalDate,
    val expectedAmount: Long,
    val lastPaymentDate: LocalDate,
    val observedMonthCount: Int
)
