package com.sanha.moneytalk.core.ui.component.tab

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// ========== Preview용 테스트 데이터 ==========

private val primaryColor = Color(0xFF6750A4)
private val incomeColor = Color(0xFF4CAF50)

private fun createTab(
    label: String,
    isSelected: Boolean,
    color: Color = primaryColor,
    textColor: Color = Color.White
) = object : SegmentedTabInfo {
    override val label = label
    override val isSelected = isSelected
    override val selectedColor = color
    override val selectedTextColor = textColor
}

private val listSelectedTabs = listOf(
    createTab("목록", isSelected = true),
    createTab("달력", isSelected = false),
    createTab("수입", isSelected = false, color = incomeColor)
)

private val calendarSelectedTabs = listOf(
    createTab("목록", isSelected = false),
    createTab("달력", isSelected = true),
    createTab("수입", isSelected = false, color = incomeColor)
)

private val incomeSelectedTabs = listOf(
    createTab("목록", isSelected = false),
    createTab("달력", isSelected = false),
    createTab("수입", isSelected = true, color = incomeColor)
)

private val twoTabExample = listOf(
    createTab("전체", isSelected = true),
    createTab("즐겨찾기", isSelected = false)
)

// ========== Preview ==========

@Preview(showBackground = true, name = "목록 선택")
@Composable
private fun ListSelectedPreview() {
    MaterialTheme {
        SegmentedTabRowCompose(
            tabs = listSelectedTabs,
            onTabClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, name = "달력 선택")
@Composable
private fun CalendarSelectedPreview() {
    MaterialTheme {
        SegmentedTabRowCompose(
            tabs = calendarSelectedTabs,
            onTabClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, name = "수입 선택")
@Composable
private fun IncomeSelectedPreview() {
    MaterialTheme {
        SegmentedTabRowCompose(
            tabs = incomeSelectedTabs,
            onTabClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, name = "2탭 예시")
@Composable
private fun TwoTabPreview() {
    MaterialTheme {
        SegmentedTabRowCompose(
            tabs = twoTabExample,
            onTabClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, name = "전체 탭 상태 모음")
@Composable
private fun AllTabStatesPreview() {
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                SegmentedTabRowCompose(tabs = listSelectedTabs, onTabClick = {})
                Spacer(modifier = Modifier.height(12.dp))
                SegmentedTabRowCompose(tabs = calendarSelectedTabs, onTabClick = {})
                Spacer(modifier = Modifier.height(12.dp))
                SegmentedTabRowCompose(tabs = incomeSelectedTabs, onTabClick = {})
            }
        }
    }
}
