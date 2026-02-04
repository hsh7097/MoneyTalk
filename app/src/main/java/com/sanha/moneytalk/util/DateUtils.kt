package com.sanha.moneytalk.util

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {

    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
    private val yearMonthFormat = SimpleDateFormat("yyyy-MM", Locale.KOREA)
    private val displayDateFormat = SimpleDateFormat("M월 d일 (E)", Locale.KOREA)
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
     * timestamp를 "HH:mm" 형식으로 변환
     */
    fun formatTime(timestamp: Long): String {
        return displayTimeFormat.format(Date(timestamp))
    }

    /**
     * "yyyy-MM-dd HH:mm" 문자열을 timestamp로 변환
     */
    fun parseDateTime(dateTimeString: String): Long {
        return try {
            dateTimeFormat.parse(dateTimeString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
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
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1) // Calendar.MONTH는 0부터 시작
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * 특정 년월의 끝 timestamp
     */
    fun getMonthEndTimestamp(year: Int, month: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1)
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
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
        val calendar = Calendar.getInstance()

        // 시작일 계산
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month - 1)
        calendar.set(Calendar.DAY_OF_MONTH, monthStartDay.coerceAtMost(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)))
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTimestamp = calendar.timeInMillis

        // 종료일 계산 (다음 달 시작일 전날)
        calendar.add(Calendar.MONTH, 1)
        calendar.set(Calendar.DAY_OF_MONTH, (monthStartDay - 1).coerceAtLeast(1).coerceAtMost(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endTimestamp = calendar.timeInMillis

        return Pair(startTimestamp, endTimestamp)
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

        // 오늘이 시작일 이전이면 이전 달 기준으로 계산
        return if (today < monthStartDay) {
            val prevMonth = if (currentMonth == 1) 12 else currentMonth - 1
            val prevYear = if (currentMonth == 1) currentYear - 1 else currentYear
            getCustomMonthPeriod(prevYear, prevMonth, monthStartDay)
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
}
