package com.sanha.moneytalk.core.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * 날짜/시간 유틸리티
 *
 * 앱 전반에서 사용되는 날짜/시간 관련 헬퍼 메소드를 제공합니다.
 *
 * 주요 기능:
 * - 날짜/시간 포맷 변환 (timestamp <-> 문자열)
 * - 기간 계산 (월 시작/끝, 오늘, N일 전 등)
 * - 커스텀 월 기간 계산 (월급일 기준 사용자 정의 기간)
 *
 * 커스텀 월 기간:
 * 사용자가 월 시작일을 설정하면 (예: 25일) 해당 날짜부터 다음 달 24일까지가 한 달이 됩니다.
 * 월급일 기준으로 지출을 관리하고 싶은 사용자를 위한 기능입니다.
 */
object DateUtils {

    // ========================
    // 날짜 포맷터 정의
    // ========================

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
    private val yearMonthFormat = SimpleDateFormat("yyyy-MM", Locale.KOREA)
    private val displayDateFormat = SimpleDateFormat("M월 d일 (E)", Locale.KOREA)
    private val displayDateTimeFormat = SimpleDateFormat("M월 d일 (E) HH:mm", Locale.KOREA)
    private val displayTimeFormat = SimpleDateFormat("HH:mm", Locale.KOREA)

    /**
     * timestamp를 "yyyy-MM-dd HH:mm" 형식으로 변환
     */
    fun formatDateTime(timestamp: Long): String {
        return dateTimeFormat.format(Date(timestamp))
    }

    /**
     * timestamp를 "M월 d일 (E)" 형식으로 변환
     */
    fun formatDisplayDate(timestamp: Long): String {
        return displayDateFormat.format(Date(timestamp))
    }

    /**
     * timestamp를 "M월 d일 (E) HH:mm" 형식으로 변환
     */
    fun formatDisplayDateTime(timestamp: Long): String {
        return displayDateTimeFormat.format(Date(timestamp))
    }

    /**
     * timestamp를 "HH:mm" 형식으로 변환
     */
    fun formatTime(timestamp: Long): String {
        return displayTimeFormat.format(Date(timestamp))
    }

    /**
     * "yyyy-MM-dd HH:mm" 문자열을 timestamp로 변환
     *
     * 파싱 실패 시 현재 시간을 반환하고 경고 로그를 출력합니다.
     * 이는 SMS에서 날짜를 추출하지 못했을 때의 폴백입니다.
     */
    fun parseDateTime(dateTimeString: String): Long {
        return try {
            val result = dateTimeFormat.parse(dateTimeString)?.time
            if (result == null) {
                Log.w("DateUtils", "parseDateTime 실패 (null 결과): '$dateTimeString' → 현재 시간 사용")
                System.currentTimeMillis()
            } else {
                result
            }
        } catch (e: Exception) {
            Log.w("DateUtils", "parseDateTime 예외: '$dateTimeString' → 현재 시간 사용: ${e.message}")
            System.currentTimeMillis()
        }
    }

    /**
     * 현재 월의 시작 timestamp
     */
    fun getMonthStartTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * 현재 월의 끝 timestamp
     */
    fun getMonthEndTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    /**
     * 현재 년월 문자열 "yyyy-MM"
     */
    fun getCurrentYearMonth(): String {
        return yearMonthFormat.format(Date())
    }

    /**
     * 오늘 시작 timestamp
     */
    fun getTodayStartTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * 오늘 끝 timestamp
     */
    fun getTodayEndTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    /**
     * n일 전 timestamp
     */
    fun getDaysAgoTimestamp(days: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -days)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * 특정 년월의 시작 timestamp
     */
    fun getMonthStartTimestamp(year: Int, month: Int): Long {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    /**
     * 특정 년월의 끝 timestamp
     */
    fun getMonthEndTimestamp(year: Int, month: Int): Long {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, 1, 23, 59, 59) // DAY=1로 먼저 안전하게 설정
            set(Calendar.MILLISECOND, 999)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        return calendar.timeInMillis
    }

    /**
     * 현재 년도 가져오기
     */
    fun getCurrentYear(): Int {
        return Calendar.getInstance().get(Calendar.YEAR)
    }

    /**
     * 현재 월 가져오기 (1-12)
     */
    fun getCurrentMonth(): Int {
        return Calendar.getInstance().get(Calendar.MONTH) + 1
    }

    /**
     * 년월 표시 문자열 "yyyy년 M월"
     */
    fun formatYearMonth(year: Int, month: Int): String {
        return "${year}년 ${month}월"
    }

    /**
     * 날짜 문자열 "yyyy-MM-dd"를 "M월 d일" 형식으로 변환
     */
    fun formatDateString(dateString: String): String {
        return try {
            val date = dateFormat.parse(dateString)
            if (date != null) {
                SimpleDateFormat("M월 d일", Locale.KOREA).format(date)
            } else {
                dateString
            }
        } catch (e: Exception) {
            dateString
        }
    }

    /**
     * 커스텀 월 기간 계산 (월급일 기준)
     * @param year 기준 연도
     * @param month 기준 월
     * @param monthStartDay 월 시작일 (예: 21일이면 21일부터 다음달 20일까지)
     * @return Pair<시작 timestamp, 종료 timestamp>
     */
    fun getCustomMonthPeriod(year: Int, month: Int, monthStartDay: Int): Pair<Long, Long> {
        if (monthStartDay == 1) {
            // monthStartDay=1: 해당 월 1일 ~ 해당 월 말일
            val startCal = Calendar.getInstance().apply {
                clear()
                set(year, month - 1, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endCal = Calendar.getInstance().apply {
                clear()
                set(year, month - 1, 1, 23, 59, 59)
                set(Calendar.MILLISECOND, 999)
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            return Pair(startCal.timeInMillis, endCal.timeInMillis)
        } else {
            // monthStartDay>1: 이전 달 monthStartDay ~ 이번 달 (monthStartDay-1)
            // 예: month=1, monthStartDay=21 → 12/21 ~ 1/20

            // 시작일: 이전 달의 monthStartDay
            val startCal = Calendar.getInstance().apply {
                clear()
                set(year, month - 1, 1, 0, 0, 0) // 먼저 DAY=1로 안전하게 설정
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MONTH, -1) // 이전 달로 이동
                set(Calendar.DAY_OF_MONTH, monthStartDay.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
            }

            // 종료일: 이번 달의 (monthStartDay - 1)
            val endCal = Calendar.getInstance().apply {
                clear()
                set(year, month - 1, 1, 23, 59, 59) // 먼저 DAY=1로 안전하게 설정
                set(Calendar.MILLISECOND, 999)
                set(Calendar.DAY_OF_MONTH, (monthStartDay - 1).coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
            }

            return Pair(startCal.timeInMillis, endCal.timeInMillis)
        }
    }

    /**
     * 현재 날짜 기준으로 현재 커스텀 월 기간 계산
     * @param monthStartDay 월 시작일
     * @return Pair<시작 timestamp, 종료 timestamp>
     */
    fun getCurrentCustomMonthPeriod(monthStartDay: Int): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_MONTH)
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentYear = calendar.get(Calendar.YEAR)

        if (monthStartDay == 1) {
            // monthStartDay=1이면 단순히 현재 월
            return getCustomMonthPeriod(currentYear, currentMonth, monthStartDay)
        }

        // monthStartDay>1: getCustomMonthPeriod(year, month, startDay)는
        // (month-1)의 startDay ~ month의 (startDay-1)을 반환
        // 오늘이 startDay 이상이면 현재 기간은 다음 달 기준
        // 예: startDay=21, today=25 → 기간은 이번달 21 ~ 다음달 20 = getCustomMonthPeriod(nextMonth)
        // 예: startDay=21, today=7  → 기간은 지난달 21 ~ 이번달 20 = getCustomMonthPeriod(currentMonth)
        return if (today >= monthStartDay) {
            val nextMonth = if (currentMonth == 12) 1 else currentMonth + 1
            val nextYear = if (currentMonth == 12) currentYear + 1 else currentYear
            getCustomMonthPeriod(nextYear, nextMonth, monthStartDay)
        } else {
            getCustomMonthPeriod(currentYear, currentMonth, monthStartDay)
        }
    }

    /**
     * 커스텀 월 기간 표시 문자열
     * @param year 기준 연도
     * @param month 기준 월
     * @param monthStartDay 월 시작일
     * @return "M/D ~ M/D" 형식
     */
    fun formatCustomMonthPeriod(year: Int, month: Int, monthStartDay: Int): String {
        val (startTs, endTs) = getCustomMonthPeriod(year, month, monthStartDay)
        val startCal = Calendar.getInstance().apply { timeInMillis = startTs }
        val endCal = Calendar.getInstance().apply { timeInMillis = endTs }

        val startMonth = startCal.get(Calendar.MONTH) + 1
        val startDay = startCal.get(Calendar.DAY_OF_MONTH)
        val endMonth = endCal.get(Calendar.MONTH) + 1
        val endDay = endCal.get(Calendar.DAY_OF_MONTH)

        return "${startMonth}/${startDay} ~ ${endMonth}/${endDay}"
    }

    /**
     * 커스텀 월 기간 표시 (년월 포함)
     */
    fun formatCustomYearMonth(year: Int, month: Int, monthStartDay: Int): String {
        return if (monthStartDay == 1) {
            "${year}년 ${month}월"
        } else {
            "${year}년 ${month}월 (${monthStartDay}일~)"
        }
    }

    /**
     * LLM/Gemini가 추출한 dateTime 문자열의 연도를 SMS 수신 시간 기준으로 검증/교정
     *
     * LLM이 SMS 본문에서 날짜를 추출할 때 연도를 잘못 추정할 수 있습니다.
     * (예: 2026년 1월 SMS에서 "01/15"를 보고 "2025-01-15"로 반환)
     *
     * SMS 수신 시간과 추출된 날짜의 차이가 6개월 이상이면
     * SMS 수신 시간의 연도로 교정합니다.
     *
     * @param extractedDateTime LLM이 추출한 "yyyy-MM-dd HH:mm" 형식 문자열
     * @param smsTimestamp SMS 수신 시간 (밀리초)
     * @return 교정된 dateTime 문자열
     */
    fun validateExtractedDateTime(extractedDateTime: String, smsTimestamp: Long): String {
        if (extractedDateTime.isBlank()) return extractedDateTime

        return try {
            val parsedTime = dateTimeFormat.parse(extractedDateTime)?.time ?: return extractedDateTime
            val diff = kotlin.math.abs(parsedTime - smsTimestamp)

            // 6개월(~183일) 이상 차이나면 연도가 잘못된 것으로 판단
            val sixMonthsMs = 183L * 24 * 60 * 60 * 1000
            if (diff > sixMonthsMs) {
                // SMS 수신 시간의 연도로 교정
                val smsCal = Calendar.getInstance().apply { timeInMillis = smsTimestamp }
                val smsYear = smsCal.get(Calendar.YEAR)

                val extractedCal = Calendar.getInstance().apply { timeInMillis = parsedTime }
                extractedCal.set(Calendar.YEAR, smsYear)

                val corrected = dateTimeFormat.format(extractedCal.time)
                Log.w("DateUtils", "LLM 날짜 연도 교정: '$extractedDateTime' → '$corrected' (SMS수신: ${dateTimeFormat.format(Date(smsTimestamp))})")
                corrected
            } else {
                extractedDateTime
            }
        } catch (e: Exception) {
            Log.w("DateUtils", "LLM 날짜 검증 실패: '$extractedDateTime' → 원본 유지")
            extractedDateTime
        }
    }
}
