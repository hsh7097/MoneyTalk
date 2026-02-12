package com.sanha.moneytalk.core.ui.component.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// ========== Preview용 테스트 데이터 ==========

private val apiKeyInfo = object : SettingsItemInfo {
    override val icon = Icons.Default.Key
    override val title = "Gemini API 키"
    override val subtitle = "AIzaSy...Xk4s"
}

private val classifyInfo = object : SettingsItemInfo {
    override val icon = Icons.Default.AutoAwesome
    override val title = "카테고리 정리"
    override val subtitle = "미정리 12건"
}

private val exportInfo = object : SettingsItemInfo {
    override val icon = Icons.Default.Backup
    override val title = "데이터 내보내기"
    override val subtitle = "JSON/CSV 형식으로 저장"
}

private val restoreInfo = object : SettingsItemInfo {
    override val icon = Icons.Default.Restore
    override val title = "데이터 복원"
    override val subtitle = "백업 파일에서 복원"
}

private val deleteInfo = object : SettingsItemInfo {
    override val icon = Icons.Default.DeleteForever
    override val title = "전체 데이터 삭제"
    override val subtitle = "모든 지출/수입 데이터를 삭제합니다"
    override val isDestructive = true
}

private val versionInfo = object : SettingsItemInfo {
    override val icon = Icons.Default.Info
    override val title = "앱 버전"
    override val subtitle = "1.0.0"
}

private val privacyInfo = object : SettingsItemInfo {
    override val icon = Icons.Default.Description
    override val title = "개인정보 처리방침"
}

// ========== Preview ==========

@Preview(showBackground = true, name = "단일 섹션")
@Composable
private fun SingleSectionPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            SettingsSectionCompose(title = "AI 설정") {
                SettingsItemCompose(info = apiKeyInfo, onClick = {})
                HorizontalDivider()
                SettingsItemCompose(info = classifyInfo, onClick = {})
            }
        }
    }
}

@Preview(showBackground = true, name = "데이터 관리 섹션")
@Composable
private fun DataSectionPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            SettingsSectionCompose(title = "데이터 관리") {
                SettingsItemCompose(info = exportInfo, onClick = {})
                HorizontalDivider()
                SettingsItemCompose(info = restoreInfo, onClick = {})
                HorizontalDivider()
                SettingsItemCompose(info = deleteInfo, onClick = {})
            }
        }
    }
}

@Preview(showBackground = true, name = "전체 설정 화면")
@Composable
private fun FullSettingsPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsSectionCompose(title = "AI 설정") {
                    SettingsItemCompose(info = apiKeyInfo, onClick = {})
                    HorizontalDivider()
                    SettingsItemCompose(info = classifyInfo, onClick = {})
                }
                SettingsSectionCompose(title = "데이터 관리") {
                    SettingsItemCompose(info = exportInfo, onClick = {})
                    HorizontalDivider()
                    SettingsItemCompose(info = restoreInfo, onClick = {})
                    HorizontalDivider()
                    SettingsItemCompose(info = deleteInfo, onClick = {})
                }
                SettingsSectionCompose(title = "앱 정보") {
                    SettingsItemCompose(info = versionInfo, onClick = {})
                    HorizontalDivider()
                    SettingsItemCompose(info = privacyInfo, onClick = {})
                }
            }
        }
    }
}
