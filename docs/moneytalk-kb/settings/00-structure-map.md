---
type: structure-map
title: Settings 구조 지도
description: Settings 탭의 UI, ViewModel, dialog, backup/drive/credit 관련 파일 역할을 정리한다.
tags: [moneytalk, settings, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/feature/settings/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Settings 구조 지도

## 패키지 구조

```text
feature/settings/ui/
├── SettingsScreen.kt
├── SettingsViewModel.kt
├── BudgetBottomSheet.kt
├── SettingsDataDialogs.kt
├── SettingsInfoDialogs.kt
├── SettingsPreferenceDialogs.kt
└── coachmark/SettingsCoachMark.kt
```

## 핵심 파일

| 파일 | 분류 | 역할 | 함께 볼 파일 |
|---|---|---|---|
| `SettingsScreen.kt` | entry/rendering/action | 설정 탭 Composable, row group, 보조 화면/다이얼로그 진입 | `SettingsViewModel.kt` |
| `SettingsViewModel.kt` | data/action | 설정 intent 처리, DataStore, API key, 예산, backup/restore, drive, 카드 보유, 분류 | `SettingsDataStore.kt`, `DataBackupManager.kt` |
| `BudgetBottomSheet.kt` | rendering/action | 총 예산/카테고리 예산 입력 bottom sheet | `BudgetDao.kt`, `SettingsViewModel.kt` |
| `SettingsPreferenceDialogs.kt` | rendering/action | 테마, API key, 월 시작일, 월 예산 dialog | `SettingsViewModel.kt` |
| `SettingsDataDialogs.kt` | rendering/action | export/import/Google Drive dialog와 file item | `DataBackupManager.kt`, `GoogleDriveHelper.kt` |
| `SettingsInfoDialogs.kt` | rendering | 앱 정보/개인정보 dialog | `strings.xml` |
| `coachmark/SettingsCoachMark.kt` | onboarding | Settings 화면 코치마크 step 정의 | `coachmark/README.md` |

## AI 참조 순서

| 작업 | 참조 순서 |
|---|---|
| 설정 row 추가 | `settings/README.md` -> `package-reference/03-rendering-action.md` -> `SettingsScreen.kt` -> `SettingsViewModel.kt` |
| 예산 변경 | `SettingsViewModel.kt` -> `BudgetBottomSheet.kt` -> `BudgetDao.kt` |
| 백업/복원 | `settings/README.md` -> `backup-restore/README.md` -> `SettingsDataDialogs.kt` -> `DataBackupManager.kt` |
| Google Drive | `SettingsViewModel.kt` -> `GoogleDriveHelper.kt` -> `SettingsDataDialogs.kt` |
| 카드 보유/숨김 | `SettingsViewModel.kt` -> `OwnedCardRepository.kt` -> `filtering/README.md` |
