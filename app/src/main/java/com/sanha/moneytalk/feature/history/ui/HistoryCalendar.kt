package com.sanha.moneytalk.feature.history.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanha.moneytalk.R
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.feature.transactionlist.ui.TransactionDetailListActivity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 날짜 정보를 담는 데이터 클래스
 */
data class CalendarDay(
    val year: Int,
    val month: Int,
    val day: Int,
    val dateString: String, // "yyyy-MM-dd" 형식
    val isCurrentPeriod: Boolean, // 현재 결제 기간에 속하는지
    val isFuture: Boolean, // 오늘 이후인지
    val isToday: Boolean
)

/**
 * 결제 기간 기준 달력 뷰
 * 무지출일 배너 + 요일 헤더 + 주간 합계 + 날짜별 지출 표시
 */
@Composable
fun BillingCycleCalendarView(
    year: Int,
    month: Int,
    monthStartDay: Int,
    dailyTotals: Map<String, Int>, // "yyyy-MM-dd" -> expense amount
    dailyIncomeTotals: Map<String, Int> = emptyMap() // "yyyy-MM-dd" -> income amount
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val context = LocalContext.current
    val today = Calendar.getInstance()
    val todayYear = today.get(Calendar.YEAR)
    val todayMonth = today.get(Calendar.MONTH) + 1
    val todayDay = today.get(Calendar.DAY_OF_MONTH)

    // 결제 기간에 해당하는 날짜 목록 생성
    val calendarDays = remember(year, month, monthStartDay) {
        generateBillingCycleDays(year, month, monthStartDay, todayYear, todayMonth, todayDay)
    }

    // 주 단위로 그룹핑
    val weeks = remember(calendarDays) {
        calendarDays.chunked(7)
    }

    // 주별 지출 합계 계산
    val weeklyTotals = remember(weeks, dailyTotals) {
        weeks.map { week ->
            week.filter { it.isCurrentPeriod }.sumOf { day ->
                dailyTotals[day.dateString] ?: 0
            }
        }
    }

    // 주별 수입 합계 계산
    val weeklyIncomeTotals = remember(weeks, dailyIncomeTotals) {
        weeks.map { week ->
            week.filter { it.isCurrentPeriod }.sumOf { day ->
                dailyIncomeTotals[day.dateString] ?: 0
            }
        }
    }

    // 무지출일 계산 (오늘까지만)
    val noSpendDays = remember(calendarDays, dailyTotals) {
        calendarDays.count { day ->
            day.isCurrentPeriod && !day.isFuture && (dailyTotals[day.dateString] ?: 0) == 0
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        // 무지출일 배너
        if (noSpendDays > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.moneyTalkColors.calendarSunday)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.history_no_spend_month, month),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = stringResource(R.string.history_no_spend_total, noSpendDays),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // 요일 헤더
        val dayLabels = listOf(
            R.string.day_sun, R.string.day_mon, R.string.day_tue,
            R.string.day_wed, R.string.day_thu, R.string.day_fri, R.string.day_sat
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            dayLabels.forEachIndexed { index, dayResId ->
                Text(
                    text = stringResource(dayResId),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (index) {
                        0 -> MaterialTheme.moneyTalkColors.calendarSunday
                        6 -> MaterialTheme.moneyTalkColors.calendarSaturday
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }
                )
            }
        }

        // 달력 그리드 — 남은 공간을 균등 분배하여 화면 하단까지 채움
        Column(modifier = Modifier.weight(1f)) {
            weeks.forEachIndexed { weekIndex, week ->
                val weekTotal = weeklyTotals.getOrNull(weekIndex) ?: 0
                val weekIncomeTotal = weeklyIncomeTotals.getOrNull(weekIndex) ?: 0

                Column(modifier = Modifier.weight(1f)) {
                    // 주간 디바이더
                    if (weekIndex > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.moneyTalkColors.divider,
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    // 주간 합계 (오른쪽 정렬, 수입+지출)
                    if (weekTotal > 0 || weekIncomeTotal > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(end = 4.dp, top = 4.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (weekIncomeTotal > 0) {
                                Text(
                                    text = "+${numberFormat.format(weekIncomeTotal)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.moneyTalkColors.income
                                )
                                if (weekTotal > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }
                            if (weekTotal > 0) {
                                Text(
                                    text = "-${numberFormat.format(weekTotal)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        week.forEachIndexed { index, calendarDay ->
                            if (index > 0) {
                                VerticalDivider(
                                    color = MaterialTheme.moneyTalkColors.divider,
                                    thickness = 0.5.dp,
                                    modifier = Modifier.fillMaxHeight()
                                )
                            }
                            CalendarDayCell(
                                calendarDay = calendarDay,
                                dayTotal = dailyTotals[calendarDay.dateString] ?: 0,
                                dayIncome = dailyIncomeTotals[calendarDay.dateString] ?: 0,
                                isSelected = false,
                                onClick = {
                                    if (calendarDay.isCurrentPeriod && !calendarDay.isFuture) {
                                        context.startActivity(
                                            Intent(context, TransactionDetailListActivity::class.java).apply {
                                                putExtra(TransactionDetailListActivity.EXTRA_DATE, calendarDay.dateString)
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                        // 부족한 셀 채우기
                        repeat(7 - week.size) {
                            VerticalDivider(
                                color = MaterialTheme.moneyTalkColors.divider,
                                thickness = 0.5.dp,
                                modifier = Modifier.fillMaxHeight()
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 달력 날짜 셀
 * 날짜 숫자 + 일별 수입(초록)/지출(빨강) 금액 표시
 */
@Composable
fun CalendarDayCell(
    calendarDay: CalendarDay,
    dayTotal: Int,
    dayIncome: Int = 0,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else Color.Transparent
            )
            .clickable(
                enabled = calendarDay.isCurrentPeriod && !calendarDay.isFuture
            ) { onClick() },
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 날짜
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            calendarDay.isToday -> MaterialTheme.colorScheme.primary
                            else -> Color.Transparent
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = calendarDay.day.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (calendarDay.isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        calendarDay.isToday -> Color.White
                        calendarDay.isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        !calendarDay.isCurrentPeriod -> MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.3f
                        )

                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            // 일별 수입 (있을 때만 표시, 미래 날짜는 표시 안함)
            if (dayIncome > 0 && !calendarDay.isFuture && calendarDay.isCurrentPeriod) {
                Text(
                    text = "+${numberFormat.format(dayIncome)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.moneyTalkColors.income,
                    maxLines = 1
                )
            }

            // 일별 지출 (있을 때만 표시, 미래 날짜는 표시 안함)
            if (dayTotal > 0 && !calendarDay.isFuture && calendarDay.isCurrentPeriod) {
                Text(
                    text = "-${numberFormat.format(dayTotal)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 결제 기간에 해당하는 날짜 목록 생성
 * 예: monthStartDay가 21이면, 이전 달 21일 ~ 이번 달 20일
 */
internal fun generateBillingCycleDays(
    year: Int,
    month: Int,
    monthStartDay: Int,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int
): List<CalendarDay> {
    val days = mutableListOf<CalendarDay>()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

    // 시작 날짜 계산
    val startCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)

        // 시작일이 1이 아니면 이전 달로 이동
        if (monthStartDay > 1) {
            add(Calendar.MONTH, -1)
        }
        set(
            Calendar.DAY_OF_MONTH,
            monthStartDay.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH))
        )
    }

    // 종료 날짜 계산 (시작일 - 1 또는 월말)
    val endCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        if (monthStartDay > 1) {
            set(
                Calendar.DAY_OF_MONTH,
                (monthStartDay - 1).coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH))
            )
        } else {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }
    }

    // 시작 주의 일요일로 이동 (캘린더 첫 행 시작)
    val displayStartCal = startCal.clone() as Calendar
    while (displayStartCal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
        displayStartCal.add(Calendar.DAY_OF_MONTH, -1)
    }

    // 종료 주의 토요일로 이동 (캘린더 마지막 행 끝)
    val displayEndCal = endCal.clone() as Calendar
    while (displayEndCal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
        displayEndCal.add(Calendar.DAY_OF_MONTH, 1)
    }

    // 날짜 목록 생성
    val currentCal = displayStartCal.clone() as Calendar
    while (!currentCal.after(displayEndCal)) {
        val calYear = currentCal.get(Calendar.YEAR)
        val calMonth = currentCal.get(Calendar.MONTH) + 1
        val calDay = currentCal.get(Calendar.DAY_OF_MONTH)
        val dateString = dateFormat.format(currentCal.time)

        // 현재 결제 기간에 속하는지 확인
        val isInPeriod = !currentCal.before(startCal) && !currentCal.after(endCal)

        // 미래 날짜인지 확인
        val isFuture = when {
            calYear > todayYear -> true
            calYear < todayYear -> false
            calMonth > todayMonth -> true
            calMonth < todayMonth -> false
            else -> calDay > todayDay
        }

        // 오늘인지 확인
        val isToday = calYear == todayYear && calMonth == todayMonth && calDay == todayDay

        days.add(
            CalendarDay(
                year = calYear,
                month = calMonth,
                day = calDay,
                dateString = dateString,
                isCurrentPeriod = isInPeriod,
                isFuture = isFuture,
                isToday = isToday
            )
        )

        currentCal.add(Calendar.DAY_OF_MONTH, 1)
    }

    return days
}
