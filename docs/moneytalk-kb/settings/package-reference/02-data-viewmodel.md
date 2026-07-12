---
type: package-reference
title: Settings data/ViewModel
description: SettingsViewModel의 intent, 저장소, 예산, 백업/복원, Google Drive, 카드 보유 흐름을 설명한다.
tags: [moneytalk, settings, viewmodel, data]
resource: app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsViewModel.kt
timestamp: 2026-07-12T15:30:00+09:00
status: verified
---

# Settings data/ViewModel

## 상태와 intent

`SettingsViewModel`은 `SettingsUiState`와 `SettingsIntent`를 통해 row action과 dialog action을 처리한다.
설정 값은 `SettingsDataStore`, DB 값은 DAO/Repository, 백업 파일은 `DataBackupManager`와 `GoogleDriveHelper`로 위임한다.

## 주요 책임

| 책임 | 메서드/파일 | 함께 볼 문서 |
|---|---|---|
| 테마/월 시작일 | `saveThemeMode()`, `saveMonthStartDay()` | `SettingsPreferenceDialogs.kt` |
| AI 서비스 상태 | `serviceStatusFlow`, `ClassifyUnclassified` 처리 | `GeminiConfigProvider.kt`, category-classification KB |
| 예산 | `loadMonthlyBudget()`, `saveBudgets()` | `BudgetBottomSheet.kt`, `BudgetDao.kt` |
| 백업 export/import | `prepareBackup()`, `exportBackup()`, `importBackup()`, `restoreData()` | `backup-restore/README.md` |
| Google Drive | `checkGoogleSignIn()`, `exportToGoogleDrive()`, `restoreFromGoogleDrive()` | `GoogleDriveHelper.kt` |
| 카드 보유 | `loadOwnedCards()`, `updateCardOwnership()` | `OwnedCardRepository.kt`, `filtering/README.md` |
| 미분류 분류 | `classifyUnclassifiedExpenses()` | `category-classification/README.md` |
| 중복 삭제 | `deleteDuplicates()` | `transaction-mutation/README.md` |

## 주의 경계

- `SettingsViewModel`은 저장 orchestration을 담당하고, 실제 백업 직렬화는 `DataBackupManager`로 둔다.
- Google sign-in intent와 Drive API 호출은 Context/Activity 의존이 있으므로 Compose UI와 ViewModel 경계를 확인한다.
- 사용자에게 보이는 메시지는 `strings.xml` resource를 사용한다.
- API key 입력/저장 intent와 DataStore preference는 제거됐다. AI 사용 가능 여부는 RTDB service flag와 Firebase AI Logic/App Check 결과로 판단한다.
