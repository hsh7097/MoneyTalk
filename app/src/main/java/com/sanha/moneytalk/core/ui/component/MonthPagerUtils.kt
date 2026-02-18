package com.sanha.moneytalk.core.ui.component

import com.sanha.moneytalk.core.util.DateUtils

/** 페이지 캐시의 키: (year, month) 쌍 */
data class MonthKey(val year: Int, val month: Int)

/**
 * HorizontalPager 기반 월 네비게이션 유틸리티.
 *
 * Virtual Infinite Pager: 현재 월이 마지막 페이지.
 * 과거 방향으로만 스크롤 가능하여 미래 월 접근을 원천 차단한다.
 * 페이지 인덱스 ↔ (year, month) 양방향 변환을 제공한다.
 *
 * [BASE_YEAR]/[BASE_MONTH]는 object 초기화 시 고정되어
 * 세션 중 페이지 매핑의 안정성을 보장한다.
 */
object MonthPagerUtils {

    /** 현재 월이 매핑되는 페이지 인덱스 (= 과거 방향 여유: 50년) */
    const val INITIAL_PAGE = 600

    /** 총 페이지 수: 현재 월이 마지막 페이지 (미래 월 원천 차단) */
    const val TOTAL_PAGE_COUNT = INITIAL_PAGE + 1

    // 앱 프로세스 시작 시 기준 월 (세션 중 고정)
    private val BASE_YEAR = DateUtils.getCurrentYear()
    private val BASE_MONTH = DateUtils.getCurrentMonth()

    /**
     * 페이지 인덱스 → (year, month) 변환.
     * @param page HorizontalPager 페이지 인덱스
     * @return Pair(year, month) — month는 1~12
     */
    fun pageToYearMonth(page: Int): Pair<Int, Int> {
        val offset = page - INITIAL_PAGE
        val totalMonths = (BASE_YEAR * 12 + BASE_MONTH - 1) + offset
        val year = totalMonths / 12
        val month = (totalMonths % 12) + 1
        return Pair(year, month)
    }

    /**
     * (year, month) → 페이지 인덱스 변환.
     * 버튼 클릭 시 Pager를 특정 월로 이동할 때 사용.
     */
    fun yearMonthToPage(year: Int, month: Int): Int {
        val baseTotal = BASE_YEAR * 12 + BASE_MONTH - 1
        val targetTotal = year * 12 + month - 1
        return INITIAL_PAGE + (targetTotal - baseTotal)
    }

    /**
     * 현재 월 이후 페이지인지 확인 (미래 월 차단용).
     * [DateUtils.getCurrentYear]/[DateUtils.getCurrentMonth]를 실시간 호출하여
     * 자정 넘김에도 정확히 동작한다.
     */
    fun isFutureMonth(page: Int): Boolean {
        val (year, month) = pageToYearMonth(page)
        return isFutureYearMonth(year, month)
    }

    /**
     * (year, month)가 미래 월인지 확인.
     * [DateUtils.getCurrentYear]/[DateUtils.getCurrentMonth]를 실시간 호출하여
     * 자정 넘김에도 정확히 동작한다.
     */
    fun isFutureYearMonth(year: Int, month: Int): Boolean {
        val currentYear = DateUtils.getCurrentYear()
        val currentMonth = DateUtils.getCurrentMonth()
        return year > currentYear || (year == currentYear && month > currentMonth)
    }

    /**
     * (year, month)에서 offset만큼 이동한 (year, month) 반환.
     * 인접 월 프리로드 시 사용.
     * @param offset 양수면 미래, 음수면 과거
     */
    fun adjacentMonth(year: Int, month: Int, offset: Int): Pair<Int, Int> {
        val totalMonths = year * 12 + month - 1 + offset
        return Pair(totalMonths / 12, totalMonths % 12 + 1)
    }
}
