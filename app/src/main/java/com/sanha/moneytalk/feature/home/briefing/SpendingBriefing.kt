package com.sanha.moneytalk.feature.home.briefing

import java.time.LocalDate

/**
 * 호출자가 카드 숨김, SMS 제외와 통계 포함 정책을 적용한 지출이다.
 * 계산기는 필터를 다시 적용하거나 수입/환불을 임의로 차감하지 않는다.
 */
data class BriefingExpense(
    val amount: Long,
    val category: String,
    val dateTime: Long,
    val isFixed: Boolean = false
)

/** 시작일과 종료일을 모두 포함하는 날짜 구간의 기록된 지출. */
data class BriefingSpendingWindow(
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
    val amount: Long,
    val transactionCount: Int
)

data class BriefingCategoryIncrease(
    val category: String,
    val recentAmount: Long,
    val previousAmount: Long
) {
    val increase: Long get() = recentAmount - previousAmount
}

data class BriefingWeeklyComparison(
    val recent: BriefingSpendingWindow,
    val previous: BriefingSpendingWindow,
    val largestCategoryIncrease: BriefingCategoryIncrease?,
    /** 근거 화면도 브리핑과 같은 시각/시간대 경계를 사용한다. */
    val asOfMillis: Long,
    val timeZoneId: String
) {
    val difference: Long get() = recent.amount - previous.amount
}

/** 조회 범위의 완전성은 알 수 없으며 모든 금액은 전달된 기록만으로 계산한다. */
data class SpendingBriefing(
    val periodStart: LocalDate,
    val periodEndInclusive: LocalDate,
    val isCurrentPeriod: Boolean,
    val recordedExpense: Long,
    val monthlyBudget: Long?,
    val budgetRemaining: Long?,
    /** 현재 회계월에서 오늘을 포함한 남은 날짜 수. 종료/미래 기간은 0이다. */
    val remainingDays: Int,
    /** 예산 미설정/종료/미래 기간/예산 초과 시 null. */
    val dailyReference: Long?,
    /** 현재 회계월은 오늘, 과거 회계월은 마지막 날짜를 기준으로 한다. */
    val weeklyComparison: BriefingWeeklyComparison?
)
