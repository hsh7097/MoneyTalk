package com.sanha.moneytalk.feature.history.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// ========== FilterCategoryGridItem Preview ==========

@Preview(showBackground = true, name = "카테고리 그리드 - 선택됨")
@Composable
private fun FilterCategoryGridItemSelectedPreview() {
    MaterialTheme {
        Surface {
            FilterCategoryGridItem(
                emoji = "\uD83C\uDF54",
                label = "식비",
                isSelected = true,
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "카테고리 그리드 - 미선택")
@Composable
private fun FilterCategoryGridItemUnselectedPreview() {
    MaterialTheme {
        Surface {
            FilterCategoryGridItem(
                emoji = "\u2615",
                label = "카페",
                isSelected = false,
                onClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "카테고리 그리드 - 3열 모음")
@Composable
private fun FilterCategoryGridRowPreview() {
    MaterialTheme {
        Surface {
            Row(modifier = Modifier.padding(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    FilterCategoryGridItem(
                        emoji = "\uD83D\uDCCB",
                        label = "전체",
                        isSelected = true,
                        onClick = {}
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    FilterCategoryGridItem(
                        emoji = "\uD83C\uDF54",
                        label = "식비",
                        isSelected = false,
                        onClick = {}
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    FilterCategoryGridItem(
                        emoji = "\u2615",
                        label = "카페",
                        isSelected = false,
                        onClick = {}
                    )
                }
            }
        }
    }
}
