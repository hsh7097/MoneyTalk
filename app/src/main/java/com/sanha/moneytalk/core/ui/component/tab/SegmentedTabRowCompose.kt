package com.sanha.moneytalk.core.ui.component.tab

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.core.theme.FriendlyMoneyColors

/**
 * 세그먼트 스타일 탭 Row.
 * 모든 탭이 동일한 최소 너비를 가지며, 선택 상태에 따라 배경색이 변한다.
 */
@Composable
fun SegmentedTabRowCompose(
    tabs: List<SegmentedTabInfo>,
    onTabClick: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .width(IntrinsicSize.Max)
            .clip(RoundedCornerShape(14.dp))
            .background(FriendlyMoneyColors.elevatedCardBackground)
            .selectableGroup()
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            SegmentedTab(
                info = tab,
                onClick = { onTabClick(index) }
            )
        }
    }
}

/**
 * 세그먼트 탭 개별 아이템.
 */
@Composable
private fun SegmentedTab(
    info: SegmentedTabInfo,
    onClick: () -> Unit
) {
    val textColor = if (info.isSelected) info.selectedTextColor
    else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (info.isSelected) info.selectedColor
                else Color.Transparent
            )
            .selectable(selected = info.isSelected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            info.icon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = textColor
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = info.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (info.isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
