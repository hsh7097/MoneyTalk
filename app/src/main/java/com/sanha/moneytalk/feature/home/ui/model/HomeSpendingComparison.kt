package com.sanha.moneytalk.feature.home.ui.model

/** 수집 범위가 확인된 두 기간만 비교한다. 누적 배열의 0번 원소는 기간 시작의 0원이다. */
data class HomeSpendingComparison(
    val currentAmount: Long,
    val previousAmount: Long,
    val unavailableReason: UnavailableReason?
) {
    val isAvailable: Boolean get() = unavailableReason == null
    val difference: Long? get() = if (isAvailable) currentAmount - previousAmount else null

    enum class UnavailableReason {
        CURRENT_PERIOD_INCOMPLETE,
        PREVIOUS_PERIOD_INCOMPLETE,
        MISSING_DATA
    }

    companion object {
        /** todayDayIndex >= 0이면 동일 경과일까지, -1이면 각 기간의 전체 누적을 비교한다. */
        fun calculate(
            currentPoints: List<Long>,
            previousPoints: List<Long>,
            todayDayIndex: Int,
            isCurrentPeriodComplete: Boolean,
            isPreviousPeriodComplete: Boolean
        ): HomeSpendingComparison {
            val unavailableReason = when {
                !isCurrentPeriodComplete -> UnavailableReason.CURRENT_PERIOD_INCOMPLETE
                !isPreviousPeriodComplete -> UnavailableReason.PREVIOUS_PERIOD_INCOMPLETE
                currentPoints.isEmpty() || previousPoints.isEmpty() -> UnavailableReason.MISSING_DATA
                else -> null
            }
            return HomeSpendingComparison(
                currentAmount = amountAtComparisonPoint(currentPoints, todayDayIndex),
                previousAmount = amountAtComparisonPoint(previousPoints, todayDayIndex),
                unavailableReason = unavailableReason
            )
        }

        private fun amountAtComparisonPoint(points: List<Long>, todayDayIndex: Int): Long {
            if (points.isEmpty()) return 0L
            return if (todayDayIndex >= 0) points[todayDayIndex.coerceAtMost(points.lastIndex)]
            else points.last()
        }
    }
}
