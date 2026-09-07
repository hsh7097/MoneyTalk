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

## 기능 서비스 경계 (2026-09-08)

`SettingsUiState`, `SettingsIntent`, `SettingsDialog`는 `SettingsContract.kt`에 둔다. 화면 하위 섹션과 다이얼로그는 ViewModel을 직접 받지 않고 state/intent/callback을 사용한다.

- `prepareBackup()`은 `SettingsBackupService.prepare(filter, format, monthStartDay)`를 IO에서 호출한다. JSON 설정 포함 범위, CSV 거래 범위와 필터 규칙은 기존과 같다.
- `restoreData()`는 `SettingsBackupService.restore()`가 반환한 `SettingsRestoreCounts`로 완료 메시지를 만든다. 거래 저장 → 카테고리/규칙 → 예산/카드/제외 문구 → 중복 정리 → 캐시 무효화 순서를 보존한다.
- `deleteAllData()`는 `SettingsDataResetService.reset()`에 위임한다. `withRegistrationsPaused` 안에서 예약 문자 큐와 사용자 데이터를 초기화하고 `ALL_DATA_DELETED` 이벤트까지 전달한 뒤 UI 완료 상태를 변경한다. 벡터 학습 데이터는 보존한다.
- 보유 카드 Flow는 초기 1회만 구독한다. 복원 후 카드 upsert는 기존 Flow로 전달되므로 같은 observer를 추가하지 않는다.
- 예산과 Drive 상태 orchestration은 ViewModel에 유지한다. 별도 MVI 프레임워크, DB 스키마/백업 형식 변경은 없다.
