package com.sanha.moneytalk.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.core.database.entity.SmsExclusionKeywordEntity
import com.sanha.moneytalk.core.util.DriveBackupFile
import com.sanha.moneytalk.core.util.ExportFilter
import com.sanha.moneytalk.core.util.ExportFormat

// ========== 테스트 데이터 ==========

private val sampleDriveFiles = listOf(
    DriveBackupFile(
        id = "1",
        name = "moneytalk_backup_20260214.json",
        createdTime = System.currentTimeMillis() - 86400000,
        size = 1024 * 50
    ),
    DriveBackupFile(
        id = "2",
        name = "moneytalk_backup_20260210.json",
        createdTime = System.currentTimeMillis() - 345600000,
        size = 1024 * 45
    ),
    DriveBackupFile(
        id = "3",
        name = "moneytalk_backup_20260201.csv",
        createdTime = System.currentTimeMillis() - 1123200000,
        size = 1024 * 30
    )
)

private val sampleExclusionKeywords = listOf(
    SmsExclusionKeywordEntity(keyword = "인증", source = "default"),
    SmsExclusionKeywordEntity(keyword = "광고", source = "default"),
    SmsExclusionKeywordEntity(keyword = "OTP", source = "default"),
    SmsExclusionKeywordEntity(keyword = "택배", source = "user"),
    SmsExclusionKeywordEntity(keyword = "할인쿠폰", source = "user"),
    SmsExclusionKeywordEntity(keyword = "이벤트당첨", source = "chat")
)

// ========== ExportDialog Preview ==========

@Preview(showBackground = true, name = "내보내기 - 기본 (구글 미로그인)")
@Composable
private fun ExportDialogDefaultPreview() {
    MaterialTheme {
        ExportDialog(
            availableCards = listOf("신한카드", "국민카드", "삼성카드"),
            availableCategories = listOf("식비", "카페", "교통", "쇼핑", "구독"),
            currentFilter = ExportFilter(),
            currentFormat = ExportFormat.JSON,
            isGoogleSignedIn = false,
            onDismiss = {},
            onFilterChange = {},
            onFormatChange = {},
            onExportLocal = {},
            onExportGoogleDrive = {},
            onSignInGoogle = {}
        )
    }
}

@Preview(showBackground = true, name = "내보내기 - 구글 로그인됨")
@Composable
private fun ExportDialogGoogleSignedInPreview() {
    MaterialTheme {
        ExportDialog(
            availableCards = listOf("신한카드", "국민카드"),
            availableCategories = listOf("식비", "카페"),
            currentFilter = ExportFilter(),
            currentFormat = ExportFormat.CSV,
            isGoogleSignedIn = true,
            onDismiss = {},
            onFilterChange = {},
            onFormatChange = {},
            onExportLocal = {},
            onExportGoogleDrive = {},
            onSignInGoogle = {}
        )
    }
}

// ========== GoogleDriveDialog Preview ==========

@Preview(showBackground = true, name = "구글 드라이브 - 백업 파일 있음")
@Composable
private fun GoogleDriveDialogWithFilesPreview() {
    MaterialTheme {
        GoogleDriveDialog(
            backupFiles = sampleDriveFiles,
            accountName = "user@gmail.com",
            onDismiss = {},
            onRefresh = {},
            onRestore = {},
            onDelete = {},
            onSignOut = {}
        )
    }
}

@Preview(showBackground = true, name = "구글 드라이브 - 백업 없음")
@Composable
private fun GoogleDriveDialogEmptyPreview() {
    MaterialTheme {
        GoogleDriveDialog(
            backupFiles = emptyList(),
            accountName = "user@gmail.com",
            onDismiss = {},
            onRefresh = {},
            onRestore = {},
            onDelete = {},
            onSignOut = {}
        )
    }
}

// ========== DriveBackupFileItem Preview ==========

@Preview(showBackground = true, name = "드라이브 백업 파일 아이템", widthDp = 320)
@Composable
private fun DriveBackupFileItemPreview() {
    MaterialTheme {
        Surface {
            DriveBackupFileItem(
                file = sampleDriveFiles[0],
                dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.KOREA),
                onRestore = {},
                onDelete = {}
            )
        }
    }
}

// ========== ExclusionKeywordDialog Preview ==========

@Preview(showBackground = true, name = "SMS 제외 키워드 - 키워드 있음")
@Composable
private fun ExclusionKeywordDialogWithKeywordsPreview() {
    MaterialTheme {
        ExclusionKeywordDialog(
            keywords = sampleExclusionKeywords,
            onDismiss = {},
            onAdd = {},
            onRemove = {}
        )
    }
}

@Preview(showBackground = true, name = "SMS 제외 키워드 - 빈 상태")
@Composable
private fun ExclusionKeywordDialogEmptyPreview() {
    MaterialTheme {
        ExclusionKeywordDialog(
            keywords = emptyList(),
            onDismiss = {},
            onAdd = {},
            onRemove = {}
        )
    }
}

// ========== ExclusionKeywordItem Preview ==========

@Preview(showBackground = true, name = "키워드 아이템 - 삭제 가능 (사용자)", widthDp = 320)
@Composable
private fun ExclusionKeywordItemUserPreview() {
    MaterialTheme {
        Surface {
            ExclusionKeywordItem(
                keyword = "택배",
                source = "user",
                canDelete = true,
                onDelete = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "키워드 아이템 - 삭제 불가 (기본)", widthDp = 320)
@Composable
private fun ExclusionKeywordItemDefaultPreview() {
    MaterialTheme {
        Surface {
            ExclusionKeywordItem(
                keyword = "인증",
                source = "default",
                canDelete = false,
                onDelete = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "키워드 아이템 - 채팅 소스", widthDp = 320)
@Composable
private fun ExclusionKeywordItemChatPreview() {
    MaterialTheme {
        Surface {
            ExclusionKeywordItem(
                keyword = "이벤트당첨",
                source = "chat",
                canDelete = true,
                onDelete = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "키워드 아이템 모음", widthDp = 320)
@Composable
private fun ExclusionKeywordItemListPreview() {
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(8.dp)) {
                ExclusionKeywordItem("택배", "user", true) {}
                ExclusionKeywordItem("이벤트당첨", "chat", true) {}
                ExclusionKeywordItem("인증", "default", false) {}
            }
        }
    }
}
