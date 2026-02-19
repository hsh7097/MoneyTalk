package com.sanha.moneytalk.core.ui.component.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 설정 화면 아이템 Composable.
 * Info를 받아 순수 렌더링만 수행한다. 클릭은 onClick으로 상위에 위임.
 *
 * 디자인: 최소 높이 56dp (Material 3 가이드라인)
 * - 좌측: 아이콘
 * - 중앙: 제목 + 부제목
 * - 우측: 화살표
 */
@Composable
fun SettingsItemCompose(
    info: SettingsItemInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = info.icon,
                contentDescription = null,
                tint = if (info.isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (info.isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = info.subtitle.ifEmpty { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (info.subtitle.isNotEmpty()) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0f)
                    },
                    maxLines = 1
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
    }
}
