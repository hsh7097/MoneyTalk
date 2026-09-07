package com.sanha.moneytalk.feature.history.ui

import android.content.Context
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.database.entity.isIncludedInExpenseStats
import com.sanha.moneytalk.core.database.entity.isIncludedInTransferIncomeStats
import java.util.Calendar
import java.util.Date

/** 거래의 표시 필터, 정렬, 그룹 헤더를 구성한다. DB 조회나 상태 변경은 하지 않는다. */
internal class HistoryTransactionListMapper(private val context: Context) {
    /** 정렬 방식에 따라 지출 내역 정렬 */
    fun sortExpenses(
        expenses: List<ExpenseEntity>,
        sortOrder: SortOrder
    ): List<ExpenseEntity> {
        return when (sortOrder) {
            SortOrder.DATE_DESC -> expenses.sortedByDescending { it.dateTime }
            SortOrder.AMOUNT_DESC -> expenses.sortedByDescending { it.amount }
            SortOrder.STORE_FREQ -> {
                // 가게별 사용 빈도 계산
                val storeFrequency = expenses.groupingBy { it.storeName }.eachCount()
                // 빈도 높은 순 정렬, 같은 가게 내에서는 최신순
                expenses.sortedWith(
                    compareByDescending<ExpenseEntity> { storeFrequency[it.storeName] ?: 0 }
                        .thenByDescending { it.dateTime }
                )
            }
        }
    }

    /**
     * 지출+수입 데이터를 LazyColumn에 바로 렌더링 가능한 플랫 리스트로 가공
     * showExpenses/showIncomes 필터에 따라 표시할 항목을 결정
     */
    fun build(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>,
        sortOrder: SortOrder,
        showExpenses: Boolean,
        showIncomes: Boolean,
        showTransfers: Boolean,
        fixedExpenseFilter: FixedExpenseFilter = FixedExpenseFilter.ALL
    ): List<TransactionListItem> {
        val filteredExpenses = expenses.filterExpensesByFixed(fixedExpenseFilter).filter { expense ->
            if (expense.transactionType == "TRANSFER") showTransfers else showExpenses
        }
        val filteredIncomes = if (showIncomes) incomes.filterIncomesByFixed(fixedExpenseFilter) else emptyList()

        // 둘 다 해제된 경우 빈 리스트
        if (!showExpenses && !showIncomes && !showTransfers) {
            return emptyList()
        }

        // 수입만 보기 모드: 날짜별 그룹핑
        if (!showExpenses && !showTransfers && showIncomes) {
            return buildIncomeDayGroups(filteredIncomes)
        }

        return when (sortOrder) {
            SortOrder.DATE_DESC -> buildDateDescItems(filteredExpenses, filteredIncomes)
            SortOrder.AMOUNT_DESC -> buildAmountDescItems(filteredExpenses, filteredIncomes)
            SortOrder.STORE_FREQ -> buildStoreFreqItems(filteredExpenses, filteredIncomes)
        }
    }

    /** DATE_DESC: 날짜별 그룹핑 (지출 + 수입 통합) */
    private fun buildDateDescItems(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>
    ): List<TransactionListItem> {
        val items = mutableListOf<TransactionListItem>()

        val groupedExpenses = expenses.groupBy { it.dateTime.toDateKey() }
        val groupedIncomes = incomes.groupBy { it.dateTime.toDateKey() }
        val allDates = (groupedExpenses.keys + groupedIncomes.keys)
            .toSortedSet(compareByDescending { it })

        allDates.forEach { date ->
            val dayExpenses = groupedExpenses[date] ?: emptyList()
            val dayIncomes = groupedIncomes[date] ?: emptyList()
            val dailyExpenseTotal = dayExpenses
                .filter { it.isIncludedInExpenseStats() }
                .sumOf { it.amount }
            val dailyIncomeTotal = dayIncomes.sumOf { it.amount } +
                dayExpenses
                    .filter { it.isIncludedInTransferIncomeStats() }
                    .sumOf { it.amount }

            val calendar = Calendar.getInstance().apply { time = date }
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val dayOfWeekStr =
                context.getString(getDayOfWeekResId(calendar.get(Calendar.DAY_OF_WEEK)))
            val title = context.getString(R.string.history_day_header, dayOfMonth, dayOfWeekStr)

            items.add(
                TransactionListItem.Header(
                    title = title,
                    expenseTotal = dailyExpenseTotal,
                    incomeTotal = dailyIncomeTotal
                )
            )
            // 수입+지출을 시간 최신순으로 통합 정렬
            val merged = dayExpenses.map { it.dateTime to TransactionListItem.ExpenseItem(it) } +
                    dayIncomes.map { it.dateTime to TransactionListItem.IncomeItem(it) }
            merged.sortedByDescending { it.first }
                .forEach { items.add(it.second) }
        }

        return items
    }

    /** AMOUNT_DESC: 금액 높은순 플랫 리스트 (지출 + 수입 통합) */
    private fun buildAmountDescItems(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>
    ): List<TransactionListItem> {
        val items = mutableListOf<TransactionListItem>()
        val totalCount = expenses.size + incomes.size
        items.add(
            TransactionListItem.Header(
                title = "${context.getString(R.string.history_sort_amount)} (${
                    context.getString(R.string.history_count_with_unit, totalCount)
                })",
                expenseTotal = expenses.filter { it.isIncludedInExpenseStats() }.sumOf { it.amount },
                incomeTotal = incomes.sumOf { it.amount } +
                    expenses.filter { it.isIncludedInTransferIncomeStats() }.sumOf { it.amount }
            )
        )
        // 지출+수입 금액 높은순 통합 정렬
        val merged = expenses.map { it.amount to TransactionListItem.ExpenseItem(it) } +
                incomes.map { it.amount to TransactionListItem.IncomeItem(it) }
        merged.sortedByDescending { it.first }
            .forEach { items.add(it.second) }
        return items
    }

    /** STORE_FREQ: 사용처별 그룹핑 (지출 + 수입 출처별 통합) */
    private fun buildStoreFreqItems(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>
    ): List<TransactionListItem> {
        val items = mutableListOf<TransactionListItem>()

        // 지출: 사용처별 그룹핑
        val storeGroups = expenses.groupBy { it.storeName }
            .entries
            .sortedByDescending { it.value.size }

        storeGroups.forEach { (storeName, storeExpenses) ->
            val storeExpenseTotal = storeExpenses
                .filter { it.isIncludedInExpenseStats() }
                .sumOf { it.amount }
            val storeIncomeTotal = storeExpenses
                .filter { it.isIncludedInTransferIncomeStats() }
                .sumOf { it.amount }
            items.add(
                TransactionListItem.Header(
                    title = "$storeName (${
                        context.getString(R.string.history_visit_with_unit, storeExpenses.size)
                    })",
                    expenseTotal = storeExpenseTotal,
                    incomeTotal = storeIncomeTotal
                )
            )
            storeExpenses.sortedByDescending { it.dateTime }
                .forEach { items.add(TransactionListItem.ExpenseItem(it)) }
        }

        // 수입: 출처별 그룹핑 (지출 그룹 뒤에 추가)
        if (incomes.isNotEmpty()) {
            val sourceGroups = incomes.groupBy { it.source.ifBlank { it.type } }
                .entries
                .sortedByDescending { it.value.size }

            sourceGroups.forEach { (source, sourceIncomes) ->
                val sourceTotal = sourceIncomes.sumOf { it.amount }
                items.add(
                    TransactionListItem.Header(
                        title = "$source (${
                            context.getString(R.string.history_count_with_unit, sourceIncomes.size)
                        })",
                        incomeTotal = sourceTotal
                    )
                )
                sourceIncomes.sortedByDescending { it.dateTime }
                    .forEach { items.add(TransactionListItem.IncomeItem(it)) }
            }
        }

        return items
    }

    /** 수입 전용: 날짜별 그룹핑 */
    private fun buildIncomeDayGroups(incomes: List<IncomeEntity>): List<TransactionListItem> {
        val items = mutableListOf<TransactionListItem>()

        val groupedIncomes = incomes.groupBy { it.dateTime.toDateKey() }
            .toSortedMap(compareByDescending { it })

        groupedIncomes.forEach { (date, dayIncomes) ->
            val dailyTotal = dayIncomes.sumOf { it.amount }
            val calendar = Calendar.getInstance().apply { time = date }
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val dayOfWeekStr =
                context.getString(getDayOfWeekResId(calendar.get(Calendar.DAY_OF_WEEK)))
            val title = context.getString(R.string.history_day_header, dayOfMonth, dayOfWeekStr)

            items.add(
                TransactionListItem.Header(
                    title = title,
                    incomeTotal = dailyTotal
                )
            )
            dayIncomes.forEach { items.add(TransactionListItem.IncomeItem(it)) }
        }

        return items
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
