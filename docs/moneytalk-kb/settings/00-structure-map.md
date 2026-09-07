---
type: structure-map
title: Settings 구조 지도
description: Settings 탭의 UI, ViewModel, dialog, backup/drive/credit 관련 파일 역할을 정리한다.
tags: [moneytalk, settings, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/feature/settings/
timestamp: 2026-07-12T15:30:00+09:00
status: verified
---

# Settings 구조 지도

## 패키지 구조

```text
feature/settings/
├── data/
│   ├── SettingsBackupService.kt
│   └── SettingsDataResetService.kt
└── ui/
    ├── SettingsScreen.kt
    ├── SettingsContract.kt
    ├── SettingsViewModel.kt
    ├── SettingsDisplaySection.kt
    ├── SettingsBudgetSection.kt
    ├── SettingsCreditSection.kt
    ├── SettingsCategorySection.kt
    ├── SettingsDataSection.kt
    ├── SettingsAppSection.kt
    ├── SettingsDialogs.kt
    ├── SettingsLoadingOverlay.kt
    ├── BudgetBottomSheet.kt
    ├── BudgetInputAmounts.kt
    ├── SettingsDataDialogs.kt
    ├── SettingsInfoDialogs.kt
    ├── SettingsPreferenceDialogs.kt
    └── coachmark/SettingsCoachMark.kt
```

## 핵심 파일

| 파일 | 분류 | 역할 | 함께 볼 파일 |
|---|---|---|---|
| `SettingsScreen.kt` | entry/rendering/action | 설정 탭 Composable, row group, 보조 화면/다이얼로그 진입 | `SettingsViewModel.kt` |
| `SettingsViewModel.kt` | data/action | 설정 intent 처리, DataStore, AI 서비스 상태, 예산, backup/restore, drive, 카드 보유, 분류 | `SettingsDataStore.kt`, `DataBackupManager.kt`, `GeminiConfigProvider.kt` |
| `BudgetBottomSheet.kt` | rendering/action | 총 예산/카테고리 예산 입력 bottom sheet | `BudgetDao.kt`, `SettingsViewModel.kt` |
| `SettingsPreferenceDialogs.kt` | rendering/action | 테마, 월 시작일, 월 예산 dialog | `SettingsViewModel.kt` |
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

## 기능별 책임 정리 (2026-09-08)

- `SettingsScreen`은 생명주기, ActivityResult 런처, 메뉴 배치, 코치마크와 화면 진입을 조합한다.
- 테마/예산/크레딧/분류/데이터/앱 정보 섹션은 각각 `Settings*Section.kt`에서 상태를 렌더링하고 intent 또는 callback을 전달한다. 하위 섹션은 ViewModel과 Activity를 참조하지 않는다.
- `SettingsDialogs`는 `activeDialog`로 기존 다이얼로그를 선택한다. 로컬 복원과 Drive 파일 액션은 `SettingsIntent`로 전달하고 Google 로그인/내보내기 런처는 entry에 남긴다.
- `SettingsContract`는 화면 state, intent, dialog 타입의 단일 정의다.
- `SettingsBackupService`는 백업 대상 저장소 조회와 복원 순서를, `SettingsDataResetService`는 수집 차단과 데이터 초기화 순서를 소유한다. `SettingsViewModel`은 작업 시작/완료/오류와 화면 상태를 관리한다.
