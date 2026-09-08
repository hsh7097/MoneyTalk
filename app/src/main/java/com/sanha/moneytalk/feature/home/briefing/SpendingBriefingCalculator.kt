package com.sanha.moneytalk.feature.home.briefing

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 저장소, 화면 상태와 AI를 참조하지 않는 홈 브리핑 계산기. */
object SpendingBriefingCalculator {
    /**
     * [expenses]는 표시/통계 정책이 적용된 지출이어야 한다.
     * 월 잔여 예산에는 회계월 기록만, 주간 비교에는 기준일 이전 14개 날짜의 기록을 쓴다.
     * 따라서 조회 시작은 min(periodStart, min(today, periodEndInclusive) - 13일)이어야 한다.
     * 월 합계는 홈과 동일하게 해당 월에 저장된 미래일 수동 기록도 포함한다.
     * 주간 비교의 오늘만 현재 시각까지 집계하고, 이전 7일과 과거 회계월은 날짜 전체를 집계한다.
     */
    fun calculate(
        expenses: List<BriefingExpense>,
        periodStart: LocalDate,
        periodEndInclusive: LocalDate,
        now: Instant,
        zoneId: ZoneId,
        monthlyBudget: Long?
    ): SpendingBriefing {
        require(!periodEndInclusive.isBefore(periodStart))
        require(monthlyBudget == null || monthlyBudget >= 0L)

        val today = now.atZone(zoneId).toLocalDate()
        val nowMillis = now.toEpochMilli()
        val current = !today.isBefore(periodStart) && !today.isAfter(periodEndInclusive)
        val monthExpenses = expenses.inWindow(periodStart, periodEndInclusive, zoneId)
        val recordedExpense = monthExpenses.sumOf { it.amount }
        val remaining = monthlyBudget?.minus(recordedExpense)
        val remainingDays = if (current) {
            ChronoUnit.DAYS.between(today, periodEndInclusive).toInt() + 1
        } else {
            0
        }
        val dailyReference = remaining?.takeIf { current && it >= 0L }?.div(remainingDays)
        val weekly = if (today.isBefore(periodStart)) {
            null
        } else {
            val anchor = minOf(today, periodEndInclusive)
            calculateWeekly(expenses, anchor, zoneId, nowMillis)
        }

        return SpendingBriefing(
            periodStart = periodStart,
            periodEndInclusive = periodEndInclusive,
            isCurrentPeriod = current,
            recordedExpense = recordedExpense,
            monthlyBudget = monthlyBudget,
            budgetRemaining = remaining,
            remainingDays = remainingDays,
            dailyReference = dailyReference,
            weeklyComparison = weekly
        )
    }

    private fun calculateWeekly(
        expenses: List<BriefingExpense>,
        anchor: LocalDate,
        zoneId: ZoneId,
        nowMillis: Long
    ): BriefingWeeklyComparison {
        val recentStart = anchor.minusDays(6)
        val previousStart = anchor.minusDays(13)
        val previousEnd = anchor.minusDays(7)
        val recent = expenses.inWindow(recentStart, anchor, zoneId, nowMillis)
        val previous = expenses.inWindow(previousStart, previousEnd, zoneId, nowMillis)
        val previousByCategory = previous.groupBy { it.category }
            .mapValues { (_, records) -> records.sumOf { it.amount } }
        val largestIncrease = recent.groupBy { it.category }
            .map { (category, records) ->
                BriefingCategoryIncrease(
                    category = category,
                    recentAmount = records.sumOf { it.amount },
                    previousAmount = previousByCategory[category] ?: 0L
                )
            }
            .filter { it.increase > 0L }
            .sortedWith(compareByDescending<BriefingCategoryIncrease> { it.increase }.thenBy { it.category })
            .firstOrNull()

        return BriefingWeeklyComparison(
            recent = BriefingSpendingWindow(recentStart, anchor, recent.sumOf { it.amount }, recent.size),
            previous = BriefingSpendingWindow(previousStart, previousEnd, previous.sumOf { it.amount }, previous.size),
            largestCategoryIncrease = largestIncrease
        )
    }

    private fun List<BriefingExpense>.inWindow(
        start: LocalDate,
        endInclusive: LocalDate,
        zoneId: ZoneId,
        nowMillis: Long? = null
    ): List<BriefingExpense> {
        // 날짜 경계는 시간대에서 생성한다. 24시간 밀리초 덧셈은 DST 날짜를 잘못 자른다.
        val startMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endExclusiveMillis = endInclusive.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return filter {
            it.dateTime >= startMillis && it.dateTime < endExclusiveMillis &&
                (nowMillis == null || it.dateTime <= nowMillis)
        }
    }
}
