package com.sanha.moneytalk.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * DateUtils 단위 테스트.
 *
 * 주요 테스트 대상:
 * - getCustomMonthPeriod: 커스텀 월 기간 계산 (monthStartDay 1~28 다양한 케이스)
 * - getEffectiveCurrentMonth: 현재 커스텀 기간의 실효 (year, month)
 * - 연도 경계 (12월→1월), 월말 보정 (2월 28/29일) 등 엣지 케이스
 */
class DateUtilsTest {

    // ========== getCustomMonthPeriod 테스트 ==========

    @Test
    fun `monthStartDay 1 - 해당 월 1일부터 말일까지`() {
        // 2026년 2월, monthStartDay=1 → 2/1 ~ 2/28
        val (start, end) = DateUtils.getCustomMonthPeriod(2026, 2, 1)
        val startCal = calFrom(start)
        val endCal = calFrom(end)

        assertDate(startCal, 2026, 2, 1)
        assertDate(endCal, 2026, 2, 28)
        assertStartOfDay(startCal)
        assertEndOfDay(endCal)
    }

    @Test
    fun `monthStartDay 1 - 윤년 2월은 29일까지`() {
        // 2024년 2월 (윤년), monthStartDay=1 → 2/1 ~ 2/29
        val (start, end) = DateUtils.getCustomMonthPeriod(2024, 2, 1)
        val startCal = calFrom(start)
        val endCal = calFrom(end)

        assertDate(startCal, 2024, 2, 1)
        assertDate(endCal, 2024, 2, 29)
    }

    @Test
    fun `monthStartDay 19 - 이전달 19일부터 이번달 18일까지`() {
        // 2026년 2월, monthStartDay=19 → 1/19 ~ 2/18
        val (start, end) = DateUtils.getCustomMonthPeriod(2026, 2, 19)
        val startCal = calFrom(start)
        val endCal = calFrom(end)

        assertDate(startCal, 2026, 1, 19)
        assertDate(endCal, 2026, 2, 18)
        assertStartOfDay(startCal)
        assertEndOfDay(endCal)
    }

    @Test
    fun `monthStartDay 19 - 12월 데이터는 11월 19일부터 12월 18일`() {
        // 2025년 12월, monthStartDay=19 → 11/19 ~ 12/18
        val (start, end) = DateUtils.getCustomMonthPeriod(2025, 12, 19)
        val startCal = calFrom(start)
        val endCal = calFrom(end)

        assertDate(startCal, 2025, 11, 19)
        assertDate(endCal, 2025, 12, 18)
    }

    @Test
    fun `monthStartDay 19 - 1월은 작년 12월 19일부터 1월 18일`() {
        // 2026년 1월, monthStartDay=19 → 2025/12/19 ~ 2026/1/18
        val (start, end) = DateUtils.getCustomMonthPeriod(2026, 1, 19)
        val startCal = calFrom(start)
        val endCal = calFrom(end)

        assertDate(startCal, 2025, 12, 19)
        assertDate(endCal, 2026, 1, 18)
    }

    @Test
    fun `monthStartDay 25 - 일반 케이스`() {
        // 2026년 3월, monthStartDay=25 → 2/25 ~ 3/24
        val (start, end) = DateUtils.getCustomMonthPeriod(2026, 3, 25)
        val startCal = calFrom(start)
        val endCal = calFrom(end)

        assertDate(startCal, 2026, 2, 25)
        assertDate(endCal, 2026, 3, 24)
    }

    @Test
    fun `monthStartDay 30 - 2월은 28일로 보정되어야 함`() {
        // 2026년 3월, monthStartDay=30 → 이전 달(2월)의 30일은 존재하지 않으므로 28일로 보정
        val (start, end) = DateUtils.getCustomMonthPeriod(2026, 3, 30)
        val startCal = calFrom(start)
        val endCal = calFrom(end)

        // 2월에는 30일이 없으므로 28일(비윤년)로 보정
        assertDate(startCal, 2026, 2, 28)
        assertDate(endCal, 2026, 3, 29)
    }

    @Test
    fun `monthStartDay 18 - 결제일 18일 케이스`() {
        // 2026년 2월, monthStartDay=18 → 1/18 ~ 2/17
        val (start, end) = DateUtils.getCustomMonthPeriod(2026, 2, 18)
        val startCal = calFrom(start)
        val endCal = calFrom(end)

        assertDate(startCal, 2026, 1, 18)
        assertDate(endCal, 2026, 2, 17)
    }

    @Test
    fun `기간이 겹치지 않고 연속해야 함`() {
        // monthStartDay=19, month 2 끝 = 2/18, month 3 시작 = 2/19
        val (_, endMonth2) = DateUtils.getCustomMonthPeriod(2026, 2, 19)
        val (startMonth3, _) = DateUtils.getCustomMonthPeriod(2026, 3, 19)

        val endCal = calFrom(endMonth2)
        val startCal = calFrom(startMonth3)

        // 2월 기간의 끝 다음 날 = 3월 기간의 시작
        assertEquals(endCal.get(Calendar.DAY_OF_MONTH) + 1, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(endCal.get(Calendar.MONTH), startCal.get(Calendar.MONTH))
    }

    // ========== getEffectiveCurrentMonth 테스트 ==========

    @Test
    fun `monthStartDay 1 - 일반 달력 기준 현재 월 반환`() {
        val (year, month) = DateUtils.getEffectiveCurrentMonth(1)
        val now = Calendar.getInstance()
        assertEquals(now.get(Calendar.YEAR), year)
        assertEquals(now.get(Calendar.MONTH) + 1, month)
    }

    @Test
    fun `getEffectiveCurrentMonth 결과가 getCurrentCustomMonthPeriod 종료일의 년월과 일치`() {
        // monthStartDay=19일 때, 실효 년월은 커스텀 기간 종료일에서 추출
        val monthStartDay = 19
        val (effYear, effMonth) = DateUtils.getEffectiveCurrentMonth(monthStartDay)
        val (_, endTs) = DateUtils.getCurrentCustomMonthPeriod(monthStartDay)
        val endCal = calFrom(endTs)

        assertEquals(endCal.get(Calendar.YEAR), effYear)
        assertEquals(endCal.get(Calendar.MONTH) + 1, effMonth)
    }

    // ========== 시나리오 테스트: monthStartDay 변경 시 기간 이동 ==========

    @Test
    fun `시나리오 - monthStartDay 1에서 19로 변경 시 12월 기간 달라짐`() {
        // monthStartDay=1: "12월" = 12/1 ~ 12/31
        val (startDay1, endDay1) = DateUtils.getCustomMonthPeriod(2025, 12, 1)
        val startCal1 = calFrom(startDay1)
        val endCal1 = calFrom(endDay1)
        assertDate(startCal1, 2025, 12, 1)
        assertDate(endCal1, 2025, 12, 31)

        // monthStartDay=19: "12월" = 11/19 ~ 12/18
        val (startDay19, endDay19) = DateUtils.getCustomMonthPeriod(2025, 12, 19)
        val startCal19 = calFrom(startDay19)
        val endCal19 = calFrom(endDay19)
        assertDate(startCal19, 2025, 11, 19)
        assertDate(endCal19, 2025, 12, 18)

        // 기간이 완전히 다름: monthStartDay=1으로 12/1~12/31 동기화했더라도
        // monthStartDay=19로 변경하면 11/19~12/18이 필요하므로 11/19~11/30 데이터 누락
        assertTrue(
            "monthStartDay 변경 시 기간이 달라져야 함",
            startDay1 != startDay19 || endDay1 != endDay19
        )
    }

    @Test
    fun `시나리오 - monthStartDay 19로 변경 시 12월 1일 이전 데이터 부재 감지`() {
        // monthStartDay=19 기준 "12월" = 11/19 ~ 12/18
        val (customStart, _) = DateUtils.getCustomMonthPeriod(2025, 12, 19)

        // monthStartDay=1로 12월 동기화: 실제 데이터는 12/1부터 존재
        val dataStart = Calendar.getInstance().apply {
            clear()
            set(2025, 11, 1, 0, 0, 0) // 12/1 (month=11 = December)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // 커스텀 시작(11/19) < 데이터 시작(12/1) → 부분 커버
        assertTrue(
            "커스텀 월 시작이 실제 데이터 시작보다 이전이어야 함 (부분 커버)",
            customStart < dataStart
        )
    }

    // ========== isPartiallyCovered 로직 검증 ==========

    @Test
    fun `isPartiallyCovered 로직 - 60일 동기화 범위 밖의 커스텀 시작일은 부분 커버`() {
        // 60일 전 이전에 시작하는 커스텀 월 = 부분 커버
        val now = System.currentTimeMillis()
        val sixtyDaysMillis = 60L * 24 * 60 * 60 * 1000

        // 2025년 12월, monthStartDay=19 → 11/19 ~ 12/18
        val (customMonthStart, _) = DateUtils.getCustomMonthPeriod(2025, 12, 19)
        val syncCoverageStart = now - sixtyDaysMillis

        // 2025-11-19은 현재(2026-02-24)에서 60일 이전
        val isPartiallyCovered = customMonthStart < syncCoverageStart

        assertTrue(
            "2025-11-19은 현재 기준 60일 전 밖이므로 부분 커버",
            isPartiallyCovered
        )
    }

    @Test
    fun `isPartiallyCovered 로직 - 60일 범위 안의 커스텀 시작일은 완전 커버`() {
        val now = System.currentTimeMillis()
        val sixtyDaysMillis = 60L * 24 * 60 * 60 * 1000

        // 현재 월 (2026년 2월) → monthStartDay=19이면 1/19 ~ 2/18
        // 현재 기준 약 36일 전이므로 60일 범위 안
        val (effYear, effMonth) = DateUtils.getEffectiveCurrentMonth(19)
        val (customMonthStart, _) = DateUtils.getCustomMonthPeriod(effYear, effMonth, 19)
        val syncCoverageStart = now - sixtyDaysMillis

        val isPartiallyCovered = customMonthStart < syncCoverageStart

        // 현재 월의 커스텀 기간 시작은 60일 범위 안에 있어야 함
        assertTrue(
            "현재 월의 커스텀 기간 시작은 60일 범위 안에 있어야 함 (완전 커버)",
            !isPartiallyCovered
        )
    }

    // ========== CTA 조건 로직 테스트 ==========

    @Test
    fun `CTA 조건 - 현재월은 showEmptyCta와 showPartialCta가 항상 false`() {
        val isCurrentMonth = true
        val hasNoData = true
        val isMonthSynced = false
        val isPartiallyCovered = true

        val showEmptyCta = hasNoData && !isCurrentMonth && !isMonthSynced
        val showPartialCta = !hasNoData && !isCurrentMonth && isPartiallyCovered && !isMonthSynced

        assertEquals("현재월은 showEmptyCta가 false", false, showEmptyCta)
        assertEquals("현재월은 showPartialCta가 false", false, showPartialCta)
    }

    @Test
    fun `CTA 조건 - 과거월 + 데이터없음 + 미동기화 = showEmptyCta true`() {
        val isCurrentMonth = false
        val hasNoData = true
        val isMonthSynced = false

        val showEmptyCta = hasNoData && !isCurrentMonth && !isMonthSynced

        assertTrue("과거월 + 데이터없음 + 미동기화 → showEmptyCta", showEmptyCta)
    }

    @Test
    fun `CTA 조건 - 과거월 + 데이터있음 + 부분커버 + 미동기화 = showPartialCta true`() {
        val isCurrentMonth = false
        val hasNoData = false
        val isMonthSynced = false
        val isPartiallyCovered = true

        val showPartialCta = !hasNoData && !isCurrentMonth && isPartiallyCovered && !isMonthSynced

        assertTrue("과거월 + 데이터있음 + 부분커버 + 미동기화 → showPartialCta", showPartialCta)
    }

    @Test
    fun `CTA 조건 - 동기화 완료 시 CTA 미노출`() {
        val isCurrentMonth = false
        val hasNoData = true
        val isMonthSynced = true
        val isPartiallyCovered = true

        val showEmptyCta = hasNoData && !isCurrentMonth && !isMonthSynced
        val showPartialCta = !hasNoData && !isCurrentMonth && isPartiallyCovered && !isMonthSynced

        assertEquals("동기화 완료 시 showEmptyCta false", false, showEmptyCta)
        assertEquals("동기화 완료 시 showPartialCta false", false, showPartialCta)
    }

    @Test
    fun `CTA 조건 - 과거월 + 데이터없음 + 동기화됨 = showEmptyCta false`() {
        val isCurrentMonth = false
        val hasNoData = true
        val isMonthSynced = true

        val showEmptyCta = hasNoData && !isCurrentMonth && !isMonthSynced

        assertEquals(false, showEmptyCta)
    }

    @Test
    fun `CTA 조건 - monthStartDay 변경 후 syncedMonths 초기화 시나리오`() {
        // 시나리오:
        // 1. monthStartDay=1로 12월 동기화 → syncedMonths에 "2025-12" 추가
        // 2. monthStartDay=19로 변경 → syncedMonths 초기화 (clearSyncedMonths 호출)
        // 3. 이제 isMonthSynced=false → showEmptyCta 또는 showPartialCta 노출 가능

        // Phase 1: 동기화 후
        var syncedMonths = setOf("2025-12")
        var isMonthSynced = "2025-12" in syncedMonths
        assertEquals("동기화 후 isMonthSynced=true", true, isMonthSynced)

        // Phase 2: monthStartDay 변경 → clearSyncedMonths
        syncedMonths = emptySet() // clearSyncedMonths() 호출 후
        isMonthSynced = "2025-12" in syncedMonths
        assertEquals("초기화 후 isMonthSynced=false", false, isMonthSynced)

        // Phase 3: CTA 노출 확인
        val isCurrentMonth = false
        val hasNoData = false // 12/1 이후 데이터는 여전히 존재
        val isPartiallyCovered = true // 11/19~11/30 데이터 없음

        val showPartialCta = !hasNoData && !isCurrentMonth && isPartiallyCovered && !isMonthSynced
        assertTrue(
            "monthStartDay 변경 후 syncedMonths 초기화 → showPartialCta true",
            showPartialCta
        )
    }

    // ========== 헬퍼 ==========

    private fun calFrom(timestamp: Long): Calendar {
        return Calendar.getInstance().apply { timeInMillis = timestamp }
    }

    private fun assertDate(cal: Calendar, expectedYear: Int, expectedMonth: Int, expectedDay: Int) {
        assertEquals("연도", expectedYear, cal.get(Calendar.YEAR))
        assertEquals("월", expectedMonth, cal.get(Calendar.MONTH) + 1)
        assertEquals("일", expectedDay, cal.get(Calendar.DAY_OF_MONTH))
    }

    private fun assertStartOfDay(cal: Calendar) {
        assertEquals("시", 0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals("분", 0, cal.get(Calendar.MINUTE))
        assertEquals("초", 0, cal.get(Calendar.SECOND))
        assertEquals("밀리초", 0, cal.get(Calendar.MILLISECOND))
    }

    private fun assertEndOfDay(cal: Calendar) {
        assertEquals("시", 23, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals("분", 59, cal.get(Calendar.MINUTE))
        assertEquals("초", 59, cal.get(Calendar.SECOND))
        assertEquals("밀리초", 999, cal.get(Calendar.MILLISECOND))
    }
}
