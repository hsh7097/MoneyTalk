package com.sanha.moneytalk.feature.history.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.core.ui.component.radiogroup.RadioGroupCompose
import com.sanha.moneytalk.core.ui.component.radiogroup.RadioGroupOption

// ========== SearchBar Preview ==========

@Preview(showBackground = true, name = "검색바 - 빈 상태")
@Composable
private fun SearchBarEmptyPreview() {
    MaterialTheme {
        SearchBar(
            query = "",
            onQueryChange = {},
            onClose = {}
        )
    }
}

@Preview(showBackground = true, name = "검색바 - 입력 중")
@Composable
private fun SearchBarWithQueryPreview() {
    MaterialTheme {
        SearchBar(
            query = "스타벅스",
            onQueryChange = {},
            onClose = {}
        )
    }
}

// ========== PeriodSummaryCard Preview ==========

@Preview(showBackground = true, name = "기간 요약 - 지출+수입")
@Composable
private fun PeriodSummaryCardPreview() {
    MaterialTheme {
        PeriodSummaryCard(
            year = 2026,
            month = 2,
            monthStartDay = 1,
            totalExpense = 1234567,
            totalIncome = 3500000,
            onPreviousMonth = {},
            onNextMonth = {}
        )
    }
}

@Preview(showBackground = true, name = "기간 요약 - 지출만")
@Composable
private fun PeriodSummaryCardExpenseOnlyPreview() {
    MaterialTheme {
        PeriodSummaryCard(
            year = 2026,
            month = 1,
            monthStartDay = 21,
            totalExpense = 580000,
            totalIncome = 0,
            onPreviousMonth = {},
            onNextMonth = {}
        )
    }
}

// ========== FilterTabRow Preview ==========

@Preview(showBackground = true, name = "탭 - 목록 모드 (필터 없음)")
@Composable
private fun FilterTabRowListPreview() {
    MaterialTheme {
        FilterTabRow(
            currentMode = ViewMode.LIST,
            onModeChange = {}
        )
    }
}

@Preview(showBackground = true, name = "탭 - 달력 모드 (필터 활성)")
@Composable
private fun FilterTabRowCalendarWithFilterPreview() {
    MaterialTheme {
        FilterTabRow(
            currentMode = ViewMode.CALENDAR,
            onModeChange = {},
            sortOrder = SortOrder.AMOUNT_DESC,
            selectedExpenseCategories = setOf("식비")
        )
    }
}

// ========== RadioGroupCompose Preview ==========

@Preview(showBackground = true, name = "라디오 그룹 - 정렬")
@Composable
private fun RadioGroupSortPreview() {
    MaterialTheme {
        Surface {
            RadioGroupCompose(
                options = listOf(
                    RadioGroupOption("최신순", isSelected = true),
                    RadioGroupOption("금액순", isSelected = false),
                    RadioGroupOption("사용처별", isSelected = false)
                ),
                onOptionSelected = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}

@Preview(showBackground = true, name = "라디오 그룹 - 분류")
@Composable
private fun RadioGroupTypePreview() {
    MaterialTheme {
        Surface {
            RadioGroupCompose(
                options = listOf(
                    RadioGroupOption("지출", isSelected = true),
                    RadioGroupOption("수입", isSelected = false),
                    RadioGroupOption("이체", isSelected = false)
                ),
                onOptionSelected = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}
