package com.sanha.moneytalk.feature.categorydetail.ui

import android.content.Context
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.feature.categorydetail.ui.model.CategoryTransactionItem
import java.util.Calendar
import java.util.Date

/** 카테고리 지출의 정렬/날짜 그룹을 구성한다. 표시 목록과 통계 합계를 구분한다. */
internal class CategoryTransactionListMapper(private val context: Context) {
    // ========== 거래 목록 빌딩 ==========

    /** 지출 목록을 날짜별 그룹핑된 플랫 리스트로 변환 */
    fun build(
        expenses: List<ExpenseEntity>,
        sortOrder: CategorySortOrder = CategorySortOrder.DATE_DESC
    ): List<CategoryTransactionItem> {
        if (expenses.isEmpty()) return emptyList()

        return when (sortOrder) {
            CategorySortOrder.DATE_DESC -> buildDateGroupedItems(expenses)
            CategorySortOrder.AMOUNT_DESC -> buildAmountSortedItems(expenses)
        }
    }

    /** 날짜별 그룹핑 (최신순) */
    private fun buildDateGroupedItems(
        expenses: List<ExpenseEntity>
    ): List<CategoryTransactionItem> {
        val items = mutableListOf<CategoryTransactionItem>()
        val grouped = expenses.groupBy { it.dateTime.toDateKey() }
        val sortedDates = grouped.keys.sortedDescending()

        sortedDates.forEach { date ->
            val dayExpenses = grouped[date] ?: return@forEach
            val dailyTotal = CategoryDetailExpenseFilters.filterStatsExpenses(dayExpenses)
                .sumOf { it.amount }

            val calendar = Calendar.getInstance().apply { time = date }
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val dayOfWeekStr = context.getString(
                getDayOfWeekResId(calendar.get(Calendar.DAY_OF_WEEK))
            )
            val title = context.getString(
                R.string.history_day_header, dayOfMonth, dayOfWeekStr
            )

            items.add(CategoryTransactionItem.Header(title = title, expenseTotal = dailyTotal))
            dayExpenses.sortedByDescending { it.dateTime }.forEach { expense ->
                items.add(CategoryTransactionItem.ExpenseItem(expense))
            }
        }

        return items
    }

    /** 금액순 정렬 (높은 금액 → 낮은 금액) */
    private fun buildAmountSortedItems(
        expenses: List<ExpenseEntity>
    ): List<CategoryTransactionItem> {
        return expenses.sortedByDescending { it.amount }.map { expense ->
            CategoryTransactionItem.ExpenseItem(expense)
        }
    }

    /** timestamp → 날짜 키 (시분초 제거) */
    private fun Long.toDateKey(): Date {
        return try {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = this@toDateKey
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            calendar.time
        } catch (e: Exception) {
            Date()
        }
    }

    /** Calendar.DAY_OF_WEEK → R.string.day_* 리소스 ID */
    private fun getDayOfWeekResId(dayOfWeek: Int): Int {
        return when (dayOfWeek) {
            Calendar.SUNDAY -> R.string.day_sunday
            Calendar.MONDAY -> R.string.day_monday
            Calendar.TUESDAY -> R.string.day_tuesday
            Calendar.WEDNESDAY -> R.string.day_wednesday
            Calendar.THURSDAY -> R.string.day_thursday
            Calendar.FRIDAY -> R.string.day_friday
            Calendar.SATURDAY -> R.string.day_saturday
            else -> R.string.day_sunday
        }
    }

}
