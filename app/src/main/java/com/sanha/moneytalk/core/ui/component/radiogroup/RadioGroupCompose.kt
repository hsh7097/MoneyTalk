package com.sanha.moneytalk.core.ui.component.radiogroup

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.core.util.toDpTextUnit

/**
 * 라디오 그룹 옵션 데이터.
 *
 * 도메인 독립적 Contract — 사용 시점에서 데이터만 변환하여 전달.
 */
data class RadioGroupOption(
    /** 표시할 텍스트 */
    val label: String,
    /** 현재 선택 상태 */
    val isSelected: Boolean
)

/**
 * 균등 너비 Outlined 라디오 그룹 Composable.
 *
 * 동일 그룹 내 모든 옵션이 동일한 너비를 가지며,
 * 하나만 선택되는 라디오 형태로 동작한다.
 * 선택 시 primary 색상 테두리 + 텍스트, 미선택 시 회색 테두리.
 *
 * @param options 표시할 옵션 목록 (RadioGroupOption)
 * @param onOptionSelected 선택된 옵션의 인덱스를 전달하는 콜백
 * @param modifier Row 전체에 적용할 Modifier
 */
@Composable
fun RadioGroupCompose(
    options: List<RadioGroupOption>,
    onOptionSelected: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, option ->
            RadioGroupItem(
                option = option,
                onClick = { onOptionSelected(index) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 라디오 그룹 개별 아이템 (Outlined 스타일).
 *
 * 선택: primary 테두리 (1.5dp) + primary 텍스트 + Bold
 * 미선택: outlineVariant 테두리 (1dp) + onSurfaceVariant 텍스트
 */
@Composable
private fun RadioGroupItem(
    option: RadioGroupOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor = if (option.isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (option.isSelected) 1.5.dp else 1.dp
    val textColor = if (option.isSelected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(shape)
            .border(width = borderWidth, color = borderColor, shape = shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = option.label,
            fontSize = 14.toDpTextUnit,
            fontWeight = if (option.isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
