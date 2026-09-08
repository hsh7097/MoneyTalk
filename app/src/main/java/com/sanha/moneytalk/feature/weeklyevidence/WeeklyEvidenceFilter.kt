package com.sanha.moneytalk.feature.weeklyevidence

import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.isIncludedInExpenseStats
import com.sanha.moneytalk.core.util.CardVisibilityFilter
import java.time.LocalDate
import java.time.Instant

data class WeeklyEvidenceRecords(
    val recent: List<ExpenseEntity> = emptyList(),
    val previous: List<ExpenseEntity> = emptyList(),
    val recentByDate: Map<LocalDate, List<ExpenseEntity>> = emptyMap(),
    val previousByDate: Map<LocalDate, List<ExpenseEntity>> = emptyMap()
) {
    val recentAmount: Long = recent.sumOf { it.amount.toLong() }
    val previousAmount: Long = previous.sumOf { it.amount.toLong() }
}

/** 홈의 표시/통계 정책과 고정비 제외를 동일하게 적용한 카테고리별 비교 근거. */
object WeeklyEvidenceFilter {
    fun filter(
        expenses: List<ExpenseEntity>,
        request: WeeklyEvidenceRequest,
        excludedCardNames: Set<String>,
        exclusionKeywords: Set<String>
    ): WeeklyEvidenceRecords {
        val zoneId = request.zoneId
        val eligible = CardVisibilityFilter.filterVisibleExpenses(expenses, excludedCardNames)
            .filter { expense ->
                val smsLower = expense.originalSms.lowercase()
                (request.category == null || expense.category == request.category) && !expense.isFixed &&
                    expense.isIncludedInExpenseStats() && expense.dateTime <= request.asOfMillis &&
                    exclusionKeywords.none { smsLower.contains(it) }
            }
            .sortedWith(compareByDescending<ExpenseEntity> { it.dateTime }.thenByDescending { it.id })

        fun inWindow(start: LocalDate, endInclusive: LocalDate): List<ExpenseEntity> {
            val startMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val endExclusive = endInclusive.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            return eligible.filter { it.dateTime >= startMillis && it.dateTime < endExclusive }
        }

        val recent = inWindow(request.recentStart, request.recentEndInclusive)
        val previous = inWindow(request.previousStart, request.previousEndInclusive)
        return WeeklyEvidenceRecords(
            recent = recent,
            previous = previous,
            recentByDate = recent.groupBy { Instant.ofEpochMilli(it.dateTime).atZone(zoneId).toLocalDate() },
            previousByDate = previous.groupBy { Instant.ofEpochMilli(it.dateTime).atZone(zoneId).toLocalDate() }
        )
    }
}
