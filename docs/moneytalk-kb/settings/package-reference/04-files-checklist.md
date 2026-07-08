---
type: package-reference
title: Settings files checklist
description: Settings 수정 전후 확인할 파일과 검증 질문을 정리한다.
tags: [moneytalk, settings, checklist]
resource: app/src/main/java/com/sanha/moneytalk/feature/settings/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Settings files checklist

## 수정 전 확인

| 작업 | 먼저 볼 파일 |
|---|---|
| 설정 row/UI 추가 | `SettingsScreen.kt`, `SettingsViewModel.kt`, `strings.xml` |
| 새 dialog 추가 | 관련 `Settings*Dialogs.kt`, `SettingsDialog`, `SettingsUiState` |
| 예산 | `BudgetBottomSheet.kt`, `SettingsViewModel.kt`, `BudgetDao.kt` |
| 백업/복원 | `SettingsDataDialogs.kt`, `SettingsViewModel.kt`, `DataBackupManager.kt` |
| Google Drive | `SettingsDataDialogs.kt`, `SettingsViewModel.kt`, `GoogleDriveHelper.kt` |
| 카드 보유/숨김 | `SettingsViewModel.kt`, `OwnedCardRepository.kt`, `CardVisibilityFilter.kt` |

## 검증 질문

1. 새 사용자 문구가 `strings.xml`에 있는가?
2. dialog dismiss 후 `SettingsDialog` 상태가 정리되는가?
3. 저장 후 Home/History 등 관련 화면이 refresh event를 받는가?
4. 백업/복원은 DB entity와 DataStore 설정을 모두 고려하는가?
5. release/debug 수익화 정책 차이가 UI에 잘 반영되는가?
