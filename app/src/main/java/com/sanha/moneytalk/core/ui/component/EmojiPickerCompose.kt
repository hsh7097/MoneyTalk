package com.sanha.moneytalk.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 이모지 프리셋 선택 그리드.
 *
 * 카테고리 추가 시 사용할 이모지를 4열 그리드로 표시하고
 * 선택 시 콜백으로 반환한다.
 */
@Composable
fun EmojiPickerCompose(
    selectedEmoji: String?,
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = modifier,
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(PRESET_EMOJIS) { emoji ->
            val isSelected = emoji == selectedEmoji
            Surface(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEmojiSelected(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        fontSize = 24.sp
                    )
                }
            }
        }
    }
}

/** 프리셋 이모지 목록 (카테고리별 분류) */
private val PRESET_EMOJIS = listOf(
    // 음식/음료
    "\uD83C\uDF7D\uFE0F", "☕", "\uD83C\uDF7A", "\uD83C\uDF54", "\uD83C\uDF5C",
    "\uD83C\uDF63", "\uD83C\uDF70", "\uD83C\uDF7E", "\uD83E\uDD57", "\uD83C\uDF5E",
    // 쇼핑/패션
    "\uD83D\uDECD\uFE0F", "\uD83D\uDC5C", "\uD83D\uDC57", "\uD83D\uDC5F", "\uD83D\uDC84",
    "\uD83D\uDC8D", "\uD83E\uDDE2", "\uD83D\uDC56", "\uD83E\uDDF4", "\uD83C\uDF92",
    // 교통/여행
    "\uD83D\uDE8C", "\uD83D\uDE97", "✈\uFE0F", "\uD83D\uDE84", "\uD83D\uDEB2",
    "\uD83D\uDE95", "\uD83D\uDEF3\uFE0F", "\uD83C\uDFD6\uFE0F", "\uD83D\uDEA2", "\uD83C\uDFD5\uFE0F",
    // 문화/여가/운동
    "\uD83C\uDFAC", "\uD83C\uDFB5", "\uD83C\uDFAE", "\uD83D\uDCDA", "\uD83C\uDFC3",
    "\uD83D\uDCAA", "\uD83C\uDFCB\uFE0F", "\uD83C\uDFBE", "\uD83C\uDFA8", "\uD83C\uDFB3",
    // 건강/의료
    "\uD83C\uDFE5", "\uD83D\uDC8A", "\uD83E\uDE7A", "\uD83E\uDDD1\u200D⚕\uFE0F", "\uD83E\uDDA7",
    // 금융/주거/통신
    "\uD83D\uDCB0", "\uD83C\uDFE0", "\uD83D\uDCB3", "\uD83D\uDCF1", "\uD83C\uDFE6",
    "\uD83D\uDD11", "\uD83D\uDCB5", "\uD83D\uDCC8", "\uD83D\uDCCA", "\uD83D\uDCBB",
    // 가족/반려동물
    "\uD83D\uDC76", "\uD83D\uDC3E", "\uD83C\uDF81", "\uD83C\uDF93", "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67",
    // 기타
    "\uD83D\uDCE6", "\uD83D\uDCCB", "\uD83D\uDEE1\uFE0F", "\uD83D\uDD04", "⭐",
    "\uD83C\uDF1F", "\uD83D\uDCA1", "\uD83D\uDD25", "\uD83C\uDF3F", "\uD83C\uDF08"
)
