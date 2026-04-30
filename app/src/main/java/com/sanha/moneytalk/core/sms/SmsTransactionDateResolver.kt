package com.sanha.moneytalk.core.sms

import com.sanha.moneytalk.core.util.DateUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * SMS 본문에서 거래 날짜/시간을 읽는 단일 진입점.
 *
 * 카드/은행 SMS는 대부분 연도 없이 MM/DD만 보내므로, 수신 시각을 기준으로
 * 가장 가까운 연도를 붙인다. 이 규칙을 수입/지출 파서가 공통으로 사용한다.
 */
object SmsTransactionDateResolver {

    private val fullDateTimePattern = Regex("""\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}""")
    private val monthDayTimePattern = Regex("""(\d{1,2})[/.-](\d{1,2})\s+(\d{1,2}):(\d{2})""")
    private val monthDayPattern = Regex("""(\d{1,2})[/.-](\d{1,2})""")
    private val koreanMonthDayPattern = Regex("""(\d{1,2})월\s*(\d{1,2})일""")
    private val timePattern = Regex("""(\d{1,2}):(\d{2})""")

    fun extractDateTime(message: String, smsTimestamp: Long): String {
        findFullDateTime(message)?.let { return it }

        val calendar = Calendar.getInstance().apply { timeInMillis = smsTimestamp }

        findMonthDay(message)?.let { (month, day) ->
            applyMonthDay(calendar, smsTimestamp, month, day)
        }
        findHourMinute(message)?.let { (hour, minute) ->
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
        }

        return format(calendar.time)
    }

    fun normalizeCapturedDateTime(raw: String, smsTimestamp: Long): String? {
        val trimmed = raw.trim()
        if (fullDateTimePattern.matches(trimmed)) {
            return trimmed.replace(Regex("""\s+"""), " ")
        }

        for (match in monthDayTimePattern.findAll(trimmed)) {
            val calendar = Calendar.getInstance().apply { timeInMillis = smsTimestamp }
            val month = match.groupValues[1].toIntOrNull() ?: continue
            val day = match.groupValues[2].toIntOrNull() ?: continue
            val hour = match.groupValues[3].toIntOrNull() ?: continue
            val minute = match.groupValues[4].toIntOrNull() ?: continue
            if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59) {
                continue
            }
            applyMonthDay(calendar, smsTimestamp, month, day)
            applyHourMinute(calendar, hour, minute)
            return format(calendar.time)
        }

        for (match in timePattern.findAll(trimmed)) {
            val calendar = Calendar.getInstance().apply { timeInMillis = smsTimestamp }
            val hour = match.groupValues[1].toIntOrNull() ?: continue
            val minute = match.groupValues[2].toIntOrNull() ?: continue
            if (hour !in 0..23 || minute !in 0..59) continue
            applyHourMinute(calendar, hour, minute)
            return format(calendar.time)
        }

        return null
    }

    private fun findFullDateTime(message: String): String? {
        return fullDateTimePattern.find(message)
            ?.value
            ?.replace(Regex("""\s+"""), " ")
    }

    private fun findMonthDay(message: String): Pair<Int, Int>? {
        monthDayPattern.findAll(message).forEach { match ->
            toMonthDay(match)?.let { return it }
        }
        koreanMonthDayPattern.findAll(message).forEach { match ->
            toMonthDay(match)?.let { return it }
        }
        return null
    }

    private fun findHourMinute(message: String): Pair<Int, Int>? {
        timePattern.findAll(message).forEach { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@forEach
            val minute = match.groupValues[2].toIntOrNull() ?: return@forEach
            if (hour in 0..23 && minute in 0..59) {
                return hour to minute
            }
        }
        return null
    }

    private fun toMonthDay(match: MatchResult): Pair<Int, Int>? {
        val month = match.groupValues[1].toIntOrNull() ?: return null
        val day = match.groupValues[2].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        return month to day
    }

    private fun applyMonthDay(
        calendar: Calendar,
        smsTimestamp: Long,
        month: Int,
        day: Int
    ) {
        if (month !in 1..12 || day !in 1..31) return
        calendar.set(Calendar.YEAR, DateUtils.resolveYearForMonthDay(smsTimestamp, month, day))
        calendar.set(Calendar.MONTH, month - 1)
        calendar.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)))
    }

    private fun applyHourMinute(calendar: Calendar, hour: Int, minute: Int) {
        if (hour !in 0..23 || minute !in 0..59) return
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
    }

    private fun format(date: Date): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(date)
    }
}
