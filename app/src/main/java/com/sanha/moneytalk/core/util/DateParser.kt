package com.sanha.moneytalk.core.util

import java.util.Calendar

/**
 * 사용자 메시지에서 날짜/기간을 파싱하는 유틸리티
 * 예: "2월 소비패턴" -> 2024년 2월
 *     "작년 12월" -> 2023년 12월
 *     "올해 지출" -> 2024년 전체
 */
object DateParser {

    data class ParsedDateRange(
        val startTimestamp: Long,
        val endTimestamp: Long,
        val displayText: String,  // "2024년 2월", "2024년 1월~3월" 등
        val isParsed: Boolean = true
    )

    /**
     * 사용자 메시지에서 날짜 정보를 파싱하여 해당 기간의 timestamp 범위를 반환
     * 날짜 정보가 없으면 현재 월 기준으로 반환
     */
    fun parseFromMessage(message: String): ParsedDateRange {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1

        // 1. "작년" 패턴 체크
        if (message.contains("작년") || message.contains("지난해") || message.contains("전년")) {
            val year = currentYear - 1
            val month = extractMonth(message)
            return if (month != null) {
                createMonthRange(year, month)
            } else {
                createYearRange(year)
            }
        }

        // 2. "올해" 패턴 체크 (월 지정 없이)
        if ((message.contains("올해") || message.contains("금년")) && extractMonth(message) == null) {
            return createYearToDateRange(currentYear)
        }

        // 3. "YYYY년 M월" 패턴 (예: "2024년 2월", "2023년 12월")
        val yearMonthRegex = Regex("(\\d{4})\\s*년\\s*(\\d{1,2})\\s*월")
        yearMonthRegex.find(message)?.let { match ->
            val year = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            if (month in 1..12) {
                return createMonthRange(year, month)
            }
        }

        // 4. "M월" 패턴 (연도 미지정 - 올해로 간주)
        val monthOnlyRegex = Regex("(\\d{1,2})\\s*월")
        monthOnlyRegex.find(message)?.let { match ->
            val month = match.groupValues[1].toInt()
            if (month in 1..12) {
                // 미래 월이면 작년으로 처리
                val year = if (month > currentMonth) currentYear - 1 else currentYear
                return createMonthRange(year, month)
            }
        }

        // 5. "지난달", "저번달" 패턴
        if (message.contains("지난달") || message.contains("저번달") || message.contains("전달") || message.contains(
                "이전달"
            )
        ) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, -1)
            return createMonthRange(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1
            )
        }

        // 6. "이번달", "이달" 패턴
        if (message.contains("이번달") || message.contains("이달") || message.contains("이번 달")) {
            return createMonthRange(currentYear, currentMonth)
        }

        // 7. "최근 N개월" 패턴
        val recentMonthsRegex = Regex("최근\\s*(\\d+)\\s*개월")
        recentMonthsRegex.find(message)?.let { match ->
            val months = match.groupValues[1].toInt()
            return createRecentMonthsRange(months)
        }

        // 8. "N월~M월" 또는 "N월부터 M월" 패턴
        val monthRangeRegex = Regex("(\\d{1,2})\\s*월\\s*[~부터]\\s*(\\d{1,2})\\s*월")
        monthRangeRegex.find(message)?.let { match ->
            val startMonth = match.groupValues[1].toInt()
            val endMonth = match.groupValues[2].toInt()
            if (startMonth in 1..12 && endMonth in 1..12) {
                return createMonthRangeSpan(currentYear, startMonth, endMonth)
            }
        }

        // 9. "YYYY년" 패턴 (월 없이 연도만)
        val yearOnlyRegex = Regex("(\\d{4})\\s*년")
        yearOnlyRegex.find(message)?.let { match ->
            // 이미 월 패턴에서 처리되지 않은 경우만
            if (!message.contains("월")) {
                val year = match.groupValues[1].toInt()
                return createYearRange(year)
            }
        }

        // 날짜 정보를 찾지 못한 경우 현재 월 반환
        return createMonthRange(currentYear, currentMonth).copy(isParsed = false)
    }

    /**
     * 메시지에서 월 숫자 추출
     */
    private fun extractMonth(message: String): Int? {
        val monthRegex = Regex("(\\d{1,2})\\s*월")
        return monthRegex.find(message)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..12 }
    }

    /**
     * 특정 월의 시작~끝 범위 생성
     */
    private fun createMonthRange(year: Int, month: Int): ParsedDateRange {
        return ParsedDateRange(
            startTimestamp = DateUtils.getMonthStartTimestamp(year, month),
            endTimestamp = DateUtils.getMonthEndTimestamp(year, month),
            displayText = "${year}년 ${month}월"
        )
    }

    /**
     * 특정 연도 전체 범위 생성
     */
    private fun createYearRange(year: Int): ParsedDateRange {
        return ParsedDateRange(
            startTimestamp = DateUtils.getMonthStartTimestamp(year, 1),
            endTimestamp = DateUtils.getMonthEndTimestamp(year, 12),
            displayText = "${year}년"
        )
    }

    /**
     * 올해 1월~현재까지 범위
     */
    private fun createYearToDateRange(year: Int): ParsedDateRange {
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
        return ParsedDateRange(
            startTimestamp = DateUtils.getMonthStartTimestamp(year, 1),
            endTimestamp = DateUtils.getMonthEndTimestamp(year, currentMonth),
            displayText = "${year}년 (1월~${currentMonth}월)"
        )
    }

    /**
     * 최근 N개월 범위
     */
    private fun createRecentMonthsRange(months: Int): ParsedDateRange {
        val calendar = Calendar.getInstance()
        val endYear = calendar.get(Calendar.YEAR)
        val endMonth = calendar.get(Calendar.MONTH) + 1

        calendar.add(Calendar.MONTH, -(months - 1))
        val startYear = calendar.get(Calendar.YEAR)
        val startMonth = calendar.get(Calendar.MONTH) + 1

        return ParsedDateRange(
            startTimestamp = DateUtils.getMonthStartTimestamp(startYear, startMonth),
            endTimestamp = DateUtils.getMonthEndTimestamp(endYear, endMonth),
            displayText = "최근 ${months}개월"
        )
    }

    /**
     * 월 범위 (N월~M월)
     */
    private fun createMonthRangeSpan(year: Int, startMonth: Int, endMonth: Int): ParsedDateRange {
        val actualEndYear = if (endMonth < startMonth) year + 1 else year
        return ParsedDateRange(
            startTimestamp = DateUtils.getMonthStartTimestamp(year, startMonth),
            endTimestamp = DateUtils.getMonthEndTimestamp(actualEndYear, endMonth),
            displayText = "${year}년 ${startMonth}월~${endMonth}월"
        )
    }
}
