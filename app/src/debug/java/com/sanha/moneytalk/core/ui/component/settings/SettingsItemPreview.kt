package com.sanha.moneytalk.core.ui.component.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp

// ========== Preview용 테스트 데이터 ==========

private val basicInfo = object : SettingsItemInfo {
    override val icon = Icons.Default.Key
    override val title = "Gemini API 키"
    override val subtitle = "AIzaSy...Xk4s"
}

private val noSubtitleInfo = object : SettingsItemInfo {
    override val icon = Icons.Default.Description
    override val title = "개인정보 처리방침"
}

private val destructiveInfo = object : SettingsItemInfo {
    override val icon = Icons.Default.DeleteForever
    override val title = "전체 데이터 삭제"
    override val subtitle = "모든 지출/수입 데이터를 삭제합니다"
    override val isDestructive = true
}

private val longSubtitleInfo = object : SettingsItemInfo {
    override val icon = Icons.Default.Cloud
    override val title = "구글 드라이브"
    override val subtitle = "연결됨: very.long.email.address.example@gmail.com"
}

// ========== PreviewParameterProvider ==========

class SettingsItemPreviewProvider : PreviewParameterProvider<SettingsItemInfo> {
    override val values: Sequence<SettingsItemInfo>
        get() = sequenceOf(
            basicInfo,
            noSubtitleInfo,
            destructiveInfo,
            longSubtitleInfo
        )
}

// ========== Preview ==========

@Preview(showBackground = true, name = "기본 아이템")
@Composable
private fun BasicItemPreview() {
    MaterialTheme {
        SettingsItemCompose(info = basicInfo, onClick = {})
    }
}

@Preview(showBackground = true, name = "부제목 없음")
@Composable
private fun NoSubtitlePreview() {
    MaterialTheme {
        SettingsItemCompose(info = noSubtitleInfo, onClick = {})
    }
}

@Preview(showBackground = true, name = "위험 액션")
@Composable
private fun DestructivePreview() {
    MaterialTheme {
        SettingsItemCompose(info = destructiveInfo, onClick = {})
    }
}

@Preview(showBackground = true, name = "아이템 목록")
@Composable
private fun ItemListPreview(
    @PreviewParameter(SettingsItemPreviewProvider::class)
    info: SettingsItemInfo
) {
    MaterialTheme {
        SettingsItemCompose(info = info, onClick = {})
    }
}

@Preview(showBackground = true, name = "혼합 아이템 목록")
@Composable
private fun MixedItemListPreview() {
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingsItemCompose(info = basicInfo, onClick = {})
                HorizontalDivider()
                SettingsItemCompose(info = noSubtitleInfo, onClick = {})
                HorizontalDivider()
                SettingsItemCompose(info = longSubtitleInfo, onClick = {})
                HorizontalDivider()
                SettingsItemCompose(info = destructiveInfo, onClick = {})
            }
        }
    }
}
