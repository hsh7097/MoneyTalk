---
type: file-inventory
title: Settings 파일 인벤토리
description: Settings 도메인 파일의 역할, 확인 시점, 함께 볼 파일을 정리한다.
tags: [moneytalk, settings, file-inventory]
resource: app/src/main/java/com/sanha/moneytalk/feature/settings/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Settings 파일 인벤토리

| 파일 | 역할 | 언제 보는가 | 분류 | 함께 볼 파일 |
|---|---|---|---|---|
| `feature/settings/ui/SettingsScreen.kt` | 설정 탭 UI와 row action | 설정 UI/진입 변경 | 핵심 | `SettingsViewModel.kt` |
| `feature/settings/ui/SettingsViewModel.kt` | 설정 state/intent/data orchestration | 설정 저장, backup, drive, budget | 핵심 | `SettingsUiState`, repositories |
| `feature/settings/ui/BudgetBottomSheet.kt` | 예산 입력 bottom sheet | 총/카테고리 예산 | 핵심 후보 | `BudgetDao.kt` |
| `feature/settings/ui/SettingsDataDialogs.kt` | export/import/Drive dialog | 백업/복원/Drive | 핵심 후보 | `DataBackupManager.kt` |
| `feature/settings/ui/SettingsInfoDialogs.kt` | 앱 정보/개인정보 dialog | 정보성 dialog | 보조 | `strings.xml` |
| `feature/settings/ui/SettingsPreferenceDialogs.kt` | 테마/API key/월 시작일/예산 dialog | 설정 preference | 핵심 후보 | `SettingsViewModel.kt` |
| `feature/settings/ui/coachmark/SettingsCoachMark.kt` | Settings 코치마크 step | 온보딩 target 변경 | 핵심 후보 | `coachmark/README.md` |
