package com.sanha.moneytalk.feature.history.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp

// ========== CalendarDayCell 테스트 데이터 ==========

private val todayCell = CalendarDay(
    year = 2026, month = 2, day = 14,
    dateString = "2026-02-14",
    isCurrentPeriod = true, isFuture = false, isToday = true
)

private val normalCell = CalendarDay(
    year = 2026, month = 2, day = 10,
    dateString = "2026-02-10",
    isCurrentPeriod = true, isFuture = false, isToday = false
)

private val futureCell = CalendarDay(
    year = 2026, month = 2, day = 20,
    dateString = "2026-02-20",
    isCurrentPeriod = true, isFuture = true, isToday = false
)

private val outsidePeriodCell = CalendarDay(
    year = 2026, month = 1, day = 31,
    dateString = "2026-01-31",
    isCurrentPeriod = false, isFuture = false, isToday = false
)

// ========== PreviewParameterProvider ==========

class CalendarDayCellPreviewProvider : PreviewParameterProvider<CalendarDay> {
    override val values: Sequence<CalendarDay>
        get() = sequenceOf(todayCell, normalCell, futureCell, outsidePeriodCell)
}

// ========== CalendarDayCell Preview ==========

@Preview(showBackground = true, name = "달력 셀 - 오늘")
@Composable
private fun CalendarDayCellTodayPreview() {
    MaterialTheme {
        CalendarDayCell(
            calendarDay = todayCell,
            dayTotal = 45200
        )
    }
}

@Preview(showBackground = true, name = "달력 셀 - 일반 (지출 있음)")
@Composable
private fun CalendarDayCellNormalPreview() {
    MaterialTheme {
        CalendarDayCell(
            calendarDay = normalCell,
            dayTotal = 23400,
            isSelected = true
        )
    }
}

@Preview(showBackground = true, name = "달력 셀 - 미래")
@Composable
private fun CalendarDayCellFuturePreview() {
    MaterialTheme {
        CalendarDayCell(
            calendarDay = futureCell,
            dayTotal = 0
        )
    }
}

@Preview(showBackground = true, name = "달력 셀 - 기간 외")
@Composable
private fun CalendarDayCellOutsidePeriodPreview() {
    MaterialTheme {
        CalendarDayCell(
            calendarDay = outsidePeriodCell,
            dayTotal = 15000
        )
    }
}

@Preview(showBackground = true, name = "달력 셀 - 무지출일")
@Composable
private fun CalendarDayCellNoSpendPreview() {
    MaterialTheme {
        CalendarDayCell(
            calendarDay = normalCell,
            dayTotal = 0
        )
    }
}

@Preview(showBackground = true, name = "달력 주간 행", widthDp = 360)
@Composable
private fun CalendarWeekRowPreview() {
    val weekDays = listOf(
        CalendarDay(2026, 2, 8, "2026-02-08", true, false, false),
        CalendarDay(2026, 2, 9, "2026-02-09", true, false, false),
        CalendarDay(2026, 2, 10, "2026-02-10", true, false, false),
        CalendarDay(2026, 2, 11, "2026-02-11", true, false, false),
        CalendarDay(2026, 2, 12, "2026-02-12", true, false, false),
        CalendarDay(2026, 2, 13, "2026-02-13", true, false, false),
        CalendarDay(2026, 2, 14, "2026-02-14", true, false, true)
    )
    val dailyTotals = mapOf(
        "2026-02-08" to 12000,
        "2026-02-10" to 45200,
        "2026-02-12" to 8900,
        "2026-02-14" to 23400
    )

    MaterialTheme {
        Surface {
            Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                weekDays.forEach { day ->
                    CalendarDayCell(
                        calendarDay = day,
                        dayTotal = dailyTotals[day.dateString] ?: 0,
                        isSelected = day.dateString == "2026-02-14",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "달력 셀 변형 목록")
@Composable
private fun CalendarDayCellVariantsPreview(
    @PreviewParameter(CalendarDayCellPreviewProvider::class)
    calendarDay: CalendarDay
) {
    MaterialTheme {
        CalendarDayCell(
            calendarDay = calendarDay,
            dayTotal = if (calendarDay.isCurrentPeriod && !calendarDay.isFuture) 15000 else 0
        )
    }
}
