package com.sanha.moneytalk.feature.weeklyevidence

import com.sanha.moneytalk.feature.home.briefing.BriefingWeeklyComparison
import java.time.LocalDate
import java.time.ZoneId

/** 월 선택값 대신 실제 브리핑의 두 날짜 구간과 계산 시각을 전달한다. */
data class WeeklyEvidenceRequest(
    val category: String?,
    val recentStart: LocalDate,
    val recentEndInclusive: LocalDate,
    val previousStart: LocalDate,
    val previousEndInclusive: LocalDate,
    val asOfMillis: Long,
    val timeZoneId: String,
    val initiallyRecent: Boolean = true
) {
    val zoneId: ZoneId get() = ZoneId.of(timeZoneId)
    val queryStartMillis: Long get() = previousStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val queryEndMillis: Long get() = minOf(
        recentEndInclusive.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1,
        asOfMillis
    )

    companion object {
        fun from(comparison: BriefingWeeklyComparison, category: String?, initiallyRecent: Boolean = true) = WeeklyEvidenceRequest(
            category = category,
            recentStart = comparison.recent.startDate,
            recentEndInclusive = comparison.recent.endDateInclusive,
            previousStart = comparison.previous.startDate,
            previousEndInclusive = comparison.previous.endDateInclusive,
            asOfMillis = comparison.asOfMillis,
            timeZoneId = comparison.timeZoneId,
            initiallyRecent = initiallyRecent
        )
    }
}
