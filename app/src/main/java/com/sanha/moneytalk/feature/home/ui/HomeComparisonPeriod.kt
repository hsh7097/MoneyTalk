package com.sanha.moneytalk.feature.home.ui

internal object HomeComparisonPeriod {

    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    fun endOfElapsedDay(
        periodStart: Long,
        periodEnd: Long,
        elapsedDays: Int
    ): Long {
        val elapsedDayEnd = periodStart + ((elapsedDays.toLong() + 1) * DAY_MILLIS) - 1
        return elapsedDayEnd.coerceAtMost(periodEnd)
    }
}
