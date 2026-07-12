---
type: package-reference
title: Settings menu map
description: Settings 탭의 섹션별 메뉴, 하위 화면, Dialog, 저장소 영향 범위를 정리한다.
tags: [moneytalk, settings, menu-map, compose]
resource: app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsScreen.kt
timestamp: 2026-07-12T00:00:00+09:00
status: verified
---

# Settings menu map

Settings는 하단 탭 네 번째 화면이다. 단순 preference 화면이 아니라 예산, 분류, SMS/알림, 백업/복원, 보조 Activity 진입점이 모인 관리 허브다.

## 섹션별 메뉴

| 섹션 | 메뉴 | 구현 | 액션/진입점 | 영향 데이터 |
|---|---|---|---|---|
| 화면 설정 | 테마 | `SettingsItemCompose` | `SettingsIntent.ShowThemeDialog` -> `ThemeModeDialog` | `SettingsDataStore.themeModeFlow` |
| 기간/예산 | 월 시작일 | `SettingsItemCompose` | `SettingsIntent.ShowMonthStartDayDialog` | `SettingsDataStore.monthStartDayFlow`, 홈/내역/카테고리 상세 월 기간 |
| 기간/예산 | 월 예산/카테고리 예산 | `BudgetBottomSheet` | `SettingsIntent.ShowBudgetBottomSheet`, `SaveBudgets` | `BudgetDao`, 홈 예산 카드, 예산 관련 채팅/App Functions |
| AI | AI 크레딧 | 조건부 섹션 | `AiCreditActivity.open(context)` | `CreditFeaturePolicy`, `AiCreditRepository` |
| 카테고리 관리 | 카테고리 정리 | custom row | `SettingsIntent.ClassifyUnclassified` | `CategoryClassifierService`, local embedding, Firebase AI Logic/App Check |
| 카테고리 관리 | 카테고리 설정 | `SettingsItemCompose` | `CategorySettingsActivity.open(context)` | `CustomCategoryRepository`, category picker |
| 카테고리 관리 | 거래처 규칙 | `SettingsItemCompose` | `StoreRuleSettingsActivity.open(context)` | `StoreRuleRepository`, 신규/수정 거래 카테고리/고정/통계 제외 |
| 데이터 관리 | 거래 알림 | switch row | `SettingsIntent.ToggleNotification` | `SettingsDataStore.notificationEnabled`, `SmsInstantProcessor`의 노티 표시 조건 |
| 데이터 관리 | 알림 접근 권한 | `SettingsItemCompose` | `NotificationAccessHelper.openNotificationListenerSettings(context)` | `NotificationTransactionService` 외부 알림/RCS 보조 경로 |
| 데이터 관리 | SMS 설정 | `SettingsItemCompose` | `SmsSettingsActivity.open(context)` | `SmsExclusionRepository`, `SmsBlockedSenderRepository`, 파싱 입력/화면 필터 |
| 데이터 관리 | 전체 문자 재동기화 | debug only | `SettingsIntent.DebugFullSyncAllMessages` | `MainViewModel` debug sync request |
| 데이터 관리 | 오늘 문자 재동기화 | debug only | `SettingsIntent.DebugSyncTodayMessages` | `MainViewModel` debug sync request |
| 데이터 관리 | 내보내기 | `ExportDialog` | `SettingsIntent.ShowExportDialog` | `DataBackupManager`, `ExportFilter` |
| 데이터 관리 | Google Drive | `GoogleDriveDialog` | `tryOpenGoogleDrive()`, `loadDriveBackupFiles()` | `GoogleDriveHelper`, backup/restore |
| 데이터 관리 | 로컬 복원 | file picker | `SettingsIntent.OpenRestoreFilePicker`, `ConfirmRestore` | `DataBackupManager.restore` |
| 데이터 관리 | 중복 데이터 정리 | `SettingsItemCompose` | `SettingsIntent.DeleteDuplicates` 즉시 실행, confirm 없음 | Expense/Income repository dedupe, 우선순위가 높은 원본 1건 유지 |
| 데이터 관리 | 전체 데이터 삭제 | destructive dialog | `SettingsIntent.ShowDeleteConfirmDialog`, `DeleteAllData` | DB 전체 데이터, refresh event |
| 앱 정보 | 버전 | `AppInfoDialog` | `SettingsIntent.ShowAppInfoDialog` | `BuildConfig.VERSION_NAME` |
| 앱 정보 | 개인정보 처리방침 | `PrivacyPolicyDialog` | `SettingsIntent.ShowPrivacyDialog` | disclosure text |
| 앱 정보 | 가이드 다시 보기 | `SettingsItemCompose` | `resetAllScreenOnboardings()` | onboarding/coachmark seen flags |

## 부작용과 확인 계약

- `중복 데이터 정리`는 row 클릭 즉시 Expense/Income DAO의 `deleteDuplicates()`를 호출한다. QA 탐색 중에는 데이터 변경을 허용할 때만 누른다.
- 중복 정리는 `smsId`, 분류 상태, 메모, 고정/통계 제외 상태와 낮은 id 우선순위로 보존 대상을 고르고 나머지를 삭제한다. 동일 그룹 전체를 삭제하는 동작은 아니다.
- `전체 데이터 삭제`는 confirm dialog를 거친다. `가이드 다시 보기`는 seen flag를 즉시 초기화하므로 화면 확인만 필요할 때는 실행하지 않는다.

## 조건부 노출

| 조건 | 대상 | 코드 기준 |
|---|---|---|
| `uiState.isCreditFeatureEnabled == true` | AI 크레딧 섹션 | debug build나 feature flag 상태에 따라 row가 숨겨질 수 있다. |
| `BuildConfig.DEBUG == true` | 전체 문자/오늘 문자 재동기화 | 운영 빌드에서 노출되면 안 된다. |
| `uiState.notificationAccessEnabled == false` | 알림 접근 warning text | 알림 리스너 권한 안내만 표시하며 권한 요청은 시스템 설정으로 이동한다. |

API key 설정 row와 dialog는 없다. AI 관련 원격 운영 값은 RTDB의 service/model 설정만 읽고 실제 Gemini 요청은 Firebase AI Logic을 통해 전송한다.

## 하위 KB 연결

| 설정 메뉴 | 같이 볼 KB |
|---|---|
| AI 크레딧 | [../../ai-credit-screen/README.md](../../ai-credit-screen/README.md), [../../budget-credit-monetization/README.md](../../budget-credit-monetization/README.md) |
| 카테고리 설정 | [../../category-settings/README.md](../../category-settings/README.md), [../../category-classification/README.md](../../category-classification/README.md) |
| 거래처 규칙 | [../../store-rule-settings/README.md](../../store-rule-settings/README.md), [../../transaction-mutation/README.md](../../transaction-mutation/README.md) |
| 거래 알림/알림 접근 | [../../notification-display/README.md](../../notification-display/README.md), [../../notification-ingestion/README.md](../../notification-ingestion/README.md) |
| SMS 설정 | [../../sms-settings/README.md](../../sms-settings/README.md), [../../sms-parsing/README.md](../../sms-parsing/README.md), [../../filtering/README.md](../../filtering/README.md) |
| 내보내기/복원/Google Drive | [../../backup-restore/README.md](../../backup-restore/README.md) |

## 변경 시 체크

1. 새 설정 row를 추가하면 `SettingsIntent`, `SettingsDialog`, `SettingsUiState`, string resource, 보조 Activity/KB 연결이 모두 정리됐는가?
2. 하위 화면으로 이동하는 row는 `*Activity.open(context)` 계약이 화면 진입 KB와 맞는가?
3. 데이터 삭제/복원/동기화처럼 큰 부작용이 있는 메뉴는 confirm dialog와 refresh event가 있는가?
4. debug-only 메뉴가 release에 노출되지 않는가?
