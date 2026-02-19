package com.sanha.moneytalk.feature.history.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.ui.component.ExpenseDetailDialog
import com.sanha.moneytalk.core.ui.component.transaction.card.ExpenseTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardCompose
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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
    dailyTotals: Map<String, Int>, // "yyyy-MM-dd" -> amount
    expenses: List<ExpenseEntity> = emptyList(),
    onDelete: (ExpenseEntity) -> Unit = {},
    onCategoryChange: (ExpenseEntity, String) -> Unit = { _, _ -> },
    onExpenseMemoChange: (Long, String?) -> Unit = { _, _ -> }
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val today = Calendar.getInstance()
    val todayYear = today.get(Calendar.YEAR)
    val todayMonth = today.get(Calendar.MONTH) + 1
    val todayDay = today.get(Calendar.DAY_OF_MONTH)

    // 선택된 날짜 (dateString)
    var selectedDateString by remember { mutableStateOf<String?>(null) }
    // 상세 다이얼로그용 선택된 지출
    var selectedExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    // 결제 기간에 해당하는 날짜 목록 생성
    val calendarDays = remember(year, month, monthStartDay) {
        generateBillingCycleDays(year, month, monthStartDay, todayYear, todayMonth, todayDay)
    }

    // 주 단위로 그룹핑
    val weeks = remember(calendarDays) {
        calendarDays.chunked(7)
    }

    // 주별 합계 계산
    val weeklyTotals = remember(weeks, dailyTotals) {
        weeks.map { week ->
            week.filter { it.isCurrentPeriod }.sumOf { day ->
                dailyTotals[day.dateString] ?: 0
            }
        }
    }

    // 무지출일 계산 (오늘까지만)
    val noSpendDays = remember(calendarDays, dailyTotals) {
        calendarDays.count { day ->
            day.isCurrentPeriod && !day.isFuture && (dailyTotals[day.dateString] ?: 0) == 0
        }
    }

    // 선택된 날짜의 지출 목록
    val selectedDayExpenses = remember(selectedDateString, expenses) {
        if (selectedDateString == null) emptyList()
        else {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
            expenses.filter { expense ->
                val expenseDate = dateFormat.format(Date(expense.dateTime))
                expenseDate == selectedDateString
            }.sortedByDescending { it.dateTime }
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

        // 달력 그리드
        LazyColumn {
            weeks.forEachIndexed { weekIndex, week ->
                val weekTotal = weeklyTotals.getOrNull(weekIndex) ?: 0

                item {
                    Column {
                        // 주간 디바이더
                        if (weekIndex > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        // 주간 합계 (오른쪽 정렬)
                        if (weekTotal > 0) {
                            Text(
                                text = "-${numberFormat.format(weekTotal)}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 4.dp, top = 4.dp, bottom = 2.dp),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Spacer(modifier = Modifier.height(18.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            week.forEach { calendarDay ->
                                CalendarDayCell(
                                    calendarDay = calendarDay,
                                    dayTotal = dailyTotals[calendarDay.dateString] ?: 0,
                                    isSelected = selectedDateString == calendarDay.dateString,
                                    onClick = {
                                        if (calendarDay.isCurrentPeriod && !calendarDay.isFuture) {
                                            selectedDateString =
                                                if (selectedDateString == calendarDay.dateString) {
                                                    null // 토글: 같은 날짜 다시 클릭 시 닫기
                                                } else {
                                                    calendarDay.dateString
                                                }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // 부족한 셀 채우기
                            repeat(7 - week.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // 선택된 날짜의 지출 목록
            if (selectedDateString != null && selectedDayExpenses.isNotEmpty()) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )

                    val dateStr = selectedDateString ?: ""
                    val parts = dateStr.split("-")
                    val dayNum = parts.getOrNull(2)?.toIntOrNull() ?: 0
                    Text(
                        text = stringResource(R.string.calendar_day_expense_title, parts.getOrNull(1) ?: "", dayNum),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                items(
                    items = selectedDayExpenses,
                    key = { it.id }
                ) { expense ->
                    TransactionCardCompose(
                        info = ExpenseTransactionCardInfo(expense),
                        onClick = { selectedExpense = expense },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            } else if (selectedDateString != null && selectedDayExpenses.isEmpty()) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                    Text(
                        text = stringResource(R.string.calendar_no_expense_for_day),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 지출 상세 다이얼로그 (삭제 및 카테고리 변경 기능 포함)
    selectedExpense?.let { expense ->
        Log.e(
            "MT_DEBUG",
            "HistoryScreen[BillingCycleCalendarView] : ${expense.storeName}, ${expense.amount}원"
        )
        ExpenseDetailDialog(
            expense = expense,
            onDismiss = { selectedExpense = null },
            onDelete = {
                onDelete(expense)
                selectedExpense = null
            },
            onCategoryChange = { newCategory ->
                onCategoryChange(expense, newCategory)
                selectedExpense = null
            },
            onMemoChange = { memo ->
                onExpenseMemoChange(expense.id, memo)
                selectedExpense = null
            }
        )
    }
}

/**
 * 달력 날짜 셀
 * 날짜 숫자 + 일별 지출 금액 표시
 */
@Composable
fun CalendarDayCell(
    calendarDay: CalendarDay,
    dayTotal: Int,
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
            ) { onClick() }
            .aspectRatio(0.8f),
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

            // 일별 지출 (미래 날짜는 표시 안함)
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
