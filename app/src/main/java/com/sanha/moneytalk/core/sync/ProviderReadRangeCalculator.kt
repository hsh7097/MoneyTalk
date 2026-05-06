package com.sanha.moneytalk.core.sync

object ProviderReadRangeCalculator {

    fun calculateCatchUpStart(
        endTime: Long,
        lastSuccessfulScanTime: Long,
        fallbackStart: Long,
        overlapMargin: Long
    ): Long {
        val start = if (lastSuccessfulScanTime > 0L) {
            lastSuccessfulScanTime - overlapMargin
        } else {
            fallbackStart
        }
        return start.coerceAtLeast(0L).coerceAtMost(endTime)
    }
}
