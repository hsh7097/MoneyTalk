package com.sanha.moneytalk.feature.home.recurring

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 표시 가능한 과거 거래를 받아 앞으로 30일의 고정 지출만 보수적으로 예상한다.
 *
 * 최근 2~3개 연속 월에 월 1건, 같은 카테고리와 금액으로 기록된 고정 지출만 사용한다.
 * 서로 다른 구독을 한 건으로 합치지 않도록 거래처/카드는 공백과 대소문자만 정리한다.
 * 호출부가 카드/문구 표시 필터와 coverage 안내를 담당하며 이 계산기는 저장소를 변경하지 않는다.
 */
object RecurringExpenseForecastCalculator {
    private const val FORECAST_DAYS = 30L
    private const val LOOKBACK_MONTHS = 6L
    private const val MAX_EVIDENCE_MONTHS = 3
    private const val MAX_DAY_VARIATION = 3
    private val whitespace = Regex("\\s+")

    fun calculate(
        expenses: List<ExpenseEntity>,
        now: Instant,
        zoneId: ZoneId
    ): RecurringExpenseForecast {
        val today = now.atZone(zoneId).toLocalDate()
        val untilDate = today.plusDays(FORECAST_DAYS)
        val currentMonth = YearMonth.from(today)
        val firstDate = currentMonth.minusMonths(LOOKBACK_MONTHS - 1).atDay(1)
        val nowMillis = now.toEpochMilli()

        val items = expenses.asSequence()
            .filter { it.id > 0L && it.dateTime <= nowMillis }
            .map { DatedExpense(it, Instant.ofEpochMilli(it.dateTime).atZone(zoneId).toLocalDate()) }
            .filter { it.date >= firstDate && it.date <= today }
            .filter { it.expense.storeName.isNotBlank() && it.expense.cardName.isNotBlank() }
            // 비고정/통계 제외 거래도 먼저 묶어 월 복수 청구나 최신 표시 변경을 놓치지 않는다.
            .groupBy { StoreCardKey(normalize(it.expense.storeName), normalize(it.expense.cardName)) }
            .values
            .mapNotNull { records -> forecast(records, today, untilDate, currentMonth) }
            .sortedWith(
                compareBy<RecurringExpenseForecastItem> { it.expectedDate }
                    .thenBy { it.storeName }
                    .thenBy { it.cardName }
                    .thenBy { it.sourceExpenseId }
            )

        return RecurringExpenseForecast(today, untilDate, items)
    }

    private fun forecast(
        records: List<DatedExpense>,
        today: LocalDate,
        untilDate: LocalDate,
        currentMonth: YearMonth
    ): RecurringExpenseForecastItem? {
        val months = records.groupBy { YearMonth.from(it.date) }
            .entries.sortedByDescending { it.key }
            .take(MAX_EVIDENCE_MONTHS)
        if (months.size < 2 || months.any { it.value.size != 1 }) return null
        if (months.first().key < currentMonth.minusMonths(1)) return null
        if (months.zipWithNext().any { (newer, older) ->
                ChronoUnit.MONTHS.between(older.key, newer.key) != 1L
            }) return null

        val evidence = months.map { it.value.single() }
        if (evidence.any {
                !it.expense.isFixed || it.expense.isExcludedFromStats ||
                    it.expense.transactionType != "EXPENSE" || it.expense.amount <= 0 ||
                    it.expense.category.isBlank()
            }) return null
        if (evidence.map { normalize(it.expense.category) }.distinct().size != 1) return null
        // 변동 요금이나 같은 거래처의 여러 상품은 확정 일정으로 추정하지 않는다.
        if (evidence.map { it.expense.amount }.distinct().size != 1) return null

        val isMonthEnd = evidence.all { it.date.dayOfMonth == it.date.lengthOfMonth() }
        val days = evidence.map { it.date.dayOfMonth }
        if (!isMonthEnd && days.max() - days.min() > MAX_DAY_VARIATION) return null
        val anchorDay = if (isMonthEnd) 31 else days.max()
        val nextMonth = months.first().key.plusMonths(1)
        val expectedDate = nextMonth.atDay(anchorDay.coerceAtMost(nextMonth.lengthOfMonth()))
        // 이번 주기가 지났는데 기록이 없으면 다음 달로 임의 이월하지 않는다.
        if (expectedDate < today || expectedDate > untilDate) return null

        val latest = evidence.first()
        return RecurringExpenseForecastItem(
            sourceExpenseId = latest.expense.id,
            storeName = latest.expense.storeName.trim(),
            cardName = latest.expense.cardName.trim(),
            category = latest.expense.category.trim(),
            expectedDate = expectedDate,
            expectedAmount = latest.expense.amount.toLong(),
            lastPaymentDate = latest.date,
            observedMonthCount = evidence.size
        )
    }

    private fun normalize(value: String): String =
        value.trim().replace(whitespace, " ").lowercase(Locale.ROOT)

    private data class StoreCardKey(val storeName: String, val cardName: String)
    private data class DatedExpense(val expense: ExpenseEntity, val date: LocalDate)
}
