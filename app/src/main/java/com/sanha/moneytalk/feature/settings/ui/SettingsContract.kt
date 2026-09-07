package com.sanha.moneytalk.feature.settings.ui

import android.net.Uri
import androidx.compose.runtime.Stable
import com.sanha.moneytalk.core.database.entity.OwnedCardEntity
import com.sanha.moneytalk.core.theme.ThemeMode
import com.sanha.moneytalk.core.util.DriveBackupFile
import com.sanha.moneytalk.core.util.ExportFilter
import com.sanha.moneytalk.core.util.ExportFormat

/** Settings 화면의 모든 사용자 인터랙션을 Intent로 정의 */
sealed interface SettingsIntent {
    // 다이얼로그 열기
    data object ShowMonthStartDayDialog : SettingsIntent
    data object ShowDeleteConfirmDialog : SettingsIntent
    data object ShowExportDialog : SettingsIntent
    data object ShowGoogleDriveDialog : SettingsIntent
    data object ShowAppInfoDialog : SettingsIntent
    data object ShowPrivacyDialog : SettingsIntent
    data object ShowThemeDialog : SettingsIntent
    data object ShowMonthlyBudgetDialog : SettingsIntent
    data object ShowBudgetBottomSheet : SettingsIntent

    // 다이얼로그 닫기
    data object DismissDialog : SettingsIntent

    // 액션
    data class SaveMonthStartDay(val day: Int) : SettingsIntent
    data class SaveMonthlyBudget(val amount: Int) : SettingsIntent
    data class SaveBudgets(
        val totalBudget: Int?,
        val categoryBudgets: Map<String, Int>
    ) : SettingsIntent
    data class SaveThemeMode(val mode: ThemeMode) : SettingsIntent
    data object ClassifyUnclassified : SettingsIntent
    data object DeleteAllData : SettingsIntent
    data object DeleteDuplicates : SettingsIntent
    data object DebugFullSyncAllMessages : SettingsIntent
    data object DebugSyncTodayMessages : SettingsIntent
    data object OpenRestoreFilePicker : SettingsIntent
    data class SetPendingRestoreUri(val uri: Uri) : SettingsIntent
    data object ConfirmRestore : SettingsIntent
    data class ToggleNotification(val enabled: Boolean) : SettingsIntent
    data class SetExportFilter(val filter: ExportFilter) : SettingsIntent
    data class SetExportFormat(val format: ExportFormat) : SettingsIntent
    data object PrepareBackup : SettingsIntent
    data class ImportBackup(val uri: Uri) : SettingsIntent
    data object LoadDriveBackupFiles : SettingsIntent
    data class RestoreDriveBackup(val fileId: String) : SettingsIntent
    data class DeleteDriveBackup(val fileId: String) : SettingsIntent
    data object SignOutGoogle : SettingsIntent
    data object ResetScreenOnboardings : SettingsIntent
}

/** 다이얼로그 종류 (하나의 필드로 관리) */
enum class SettingsDialog {
    MONTH_START_DAY,
    DELETE_CONFIRM,
    RESTORE_CONFIRM,
    EXPORT,
    GOOGLE_DRIVE,
    APP_INFO,
    PRIVACY,
    THEME,
    MONTHLY_BUDGET,
    BUDGET_BOTTOM_SHEET
}

@Stable
data class SettingsUiState(
    val hasApiKey: Boolean = false,
    val monthStartDay: Int = 1,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isLoading: Boolean = false,
    val backupContent: String? = null,
    val exportFormat: ExportFormat = ExportFormat.JSON,
    val exportFilter: ExportFilter = ExportFilter(),
    // 카드/카테고리 목록 (필터용)
    val availableCards: List<String> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    // 구글 드라이브 관련
    val isGoogleSignedIn: Boolean = false,
    val googleAccountName: String? = null,
    val driveBackupFiles: List<DriveBackupFile> = emptyList(),
    // 카테고리 분류 관련
    val unclassifiedCount: Int = 0,
    val isClassifying: Boolean = false,
    val classifyProgress: String = "",
    val classifyProgressCurrent: Int = 0,
    val classifyProgressTotal: Int = 0,
    // 내 카드 관리
    val ownedCards: List<OwnedCardEntity> = emptyList(),
    // 백그라운드 분류 진행 중 (HomeViewModel에서 진행 중인 경우)
    val isBackgroundClassifying: Boolean = false,
    // 다이얼로그 상태 (null이면 닫힘)
    val activeDialog: SettingsDialog? = null,
    // 복원 대기 URI
    val pendingRestoreUri: Uri? = null,
    // 복원 파일 선택 트리거
    val triggerRestoreFilePicker: Boolean = false,
    // 월 예산
    val monthlyBudget: Int? = null,
    // 카테고리별 예산 (category displayName → monthlyLimit)
    val categoryBudgets: Map<String, Int> = emptyMap(),
    // 거래 알림 설정
    val notificationEnabled: Boolean = false,
    // 알림 접근 권한 상태
    val notificationAccessEnabled: Boolean = false,
    // AI 크레딧
    val isCreditFeatureEnabled: Boolean = false,
    val aiCreditBalance: Int = 0
)
