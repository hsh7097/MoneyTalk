package com.sanha.moneytalk.feature.history.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.util.toDpTextUnit
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
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

/** 백그라운드에 있는 동안 날짜가 바뀌어도 복귀한 달력은 오늘을 다시 계산한다. */
@Composable
internal fun rememberCalendarToday(currentDate: () -> LocalDate = LocalDate::now): LocalDate {
    val readCurrentDate by rememberUpdatedState(currentDate)
    var today by remember { mutableStateOf(currentDate()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = readCurrentDate() }
    return today
}

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
    dailyIncomeTotals: Map<String, Int> = emptyMap(), // "yyyy-MM-dd" -> income amount
    today: LocalDate = rememberCalendarToday(),
    onDateClick: (String) -> Unit
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val compactNumberFormat = NumberFormat.getNumberInstance(Locale.KOREA).apply {
        maximumFractionDigits = 1
    }
    // 결제 기간에 해당하는 날짜 목록 생성
    val calendarDays = remember(year, month, monthStartDay, today) {
        generateBillingCycleDays(year, month, monthStartDay, today.year, today.monthValue, today.dayOfMonth)
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 무지출일 배너
        if (noSpendDays > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
                                .background(MaterialTheme.colorScheme.primary)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        // 작은 화면과 큰 글자에서도 날짜/금액이 잘리지 않도록 세로 스크롤한다.
        Column {
            weeks.forEachIndexed { weekIndex, week ->
                val weekTotal = weeklyTotals.getOrNull(weekIndex) ?: 0
                val weekIncomeTotal = weeklyIncomeTotals.getOrNull(weekIndex) ?: 0

                Column {
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
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(end = 4.dp, top = 4.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (weekIncomeTotal > 0) {
                                Text(
                                    text = "+${formatCompactCalendarAmount(weekIncomeTotal, compactNumberFormat)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp,
                                        letterSpacing = 0.toDpTextUnit
                                    ),
                                    color = MaterialTheme.moneyTalkColors.income,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    softWrap = false
                                )
                                if (weekTotal > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }
                            if (weekTotal > 0) {
                                Text(
                                    text = "-${formatCompactCalendarAmount(weekTotal, compactNumberFormat)}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp,
                                        letterSpacing = 0.toDpTextUnit
                                    ),
                                    color = MaterialTheme.moneyTalkColors.expense,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                    softWrap = false
                                )
                            }
                        }
                    } else {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                                .background(MaterialTheme.colorScheme.surface)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp * fontScale)
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
                                        onDateClick(calendarDay.dateString)
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
 * 날짜 숫자 + 일별 수입(초록)/지출(중립색) 금액 표시
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
    val compactNumberFormat = NumberFormat.getNumberInstance(Locale.KOREA).apply {
        maximumFractionDigits = 1
    }
    val exactNumberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

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
                    .size(32.dp * LocalDensity.current.fontScale.coerceAtLeast(1f))
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
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    fontWeight = if (calendarDay.isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        calendarDay.isToday -> MaterialTheme.colorScheme.onPrimary
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
                val incomeDescription = stringResource(
                    R.string.common_won,
                    exactNumberFormat.format(dayIncome)
                )
                CalendarAmountText(
                    text = "+${formatCompactCalendarAmount(dayIncome, compactNumberFormat)}",
                    exactAmountDescription = "+$incomeDescription",
                    color = MaterialTheme.moneyTalkColors.income
                )
            }

            // 일별 지출 (있을 때만 표시, 미래 날짜는 표시 안함)
            if (dayTotal > 0 && !calendarDay.isFuture && calendarDay.isCurrentPeriod) {
                val expenseDescription = stringResource(
                    R.string.common_won,
                    exactNumberFormat.format(dayTotal)
                )
                CalendarAmountText(
                    text = "-${formatCompactCalendarAmount(dayTotal, compactNumberFormat)}",
                    exactAmountDescription = "-$expenseDescription",
                    color = MaterialTheme.moneyTalkColors.expense
                )
            }
        }
    }
}

/** Keep the sign, amount and unit together inside one of the seven calendar columns. */
@Composable
private fun CalendarAmountText(
    text: String,
    exactAmountDescription: String,
    color: Color
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val availableWidth = constraints.maxWidth
        val fittedStyle = remember(text, availableWidth, baseStyle, textMeasurer, density) {
            val measuredWidth = textMeasurer.measure(
                text = text, style = baseStyle, softWrap = false, maxLines = 1
            ).size.width
            val scale = (availableWidth.toFloat() / measuredWidth.coerceAtLeast(1)).coerceAtMost(1f)
            var style = baseStyle.copy(fontSize = (baseStyle.fontSize.value * scale).coerceAtLeast(1f).sp)
            // Android's large-font scaling can be nonlinear; verify the fitted size as well.
            while (style.fontSize.value > 1f && textMeasurer.measure(
                    text = text, style = style, softWrap = false, maxLines = 1
                ).size.width > availableWidth
            ) {
                style = style.copy(fontSize = (style.fontSize.value - 0.25f).coerceAtLeast(1f).sp)
            }
            style
        }
        Text(
            text = text,
            style = fittedStyle,
            color = color,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = exactAmountDescription
            }
        )
    }
}

private fun formatCompactCalendarAmount(amount: Int, numberFormat: NumberFormat): String {
    return when {
        amount >= 100_000_000 -> "${numberFormat.format(amount / 100_000_000.0)}억"
        amount >= 10_000 -> "${numberFormat.format(amount / 10_000.0)}만"
        else -> numberFormat.format(amount)
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
