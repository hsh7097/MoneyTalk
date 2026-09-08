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
| `feature/categoryreview/data/**`, `feature/categoryreview/ui/**` | 전체 기간 미분류 조회·노출 필터·건수·화면·편집 진입 | 미분류 직접 확인과 설정 건수 불일치 | 기능 | `SettingsCategorySection.kt`, `TransactionEditActivity.kt` |
| `feature/settings/ui/SettingsScreen.kt` | 설정 탭 UI와 row action | 설정 UI/진입 변경 | 핵심 | `SettingsViewModel.kt` |
| `feature/settings/ui/SettingsViewModel.kt` | 설정 state/intent/data orchestration | 설정 저장, backup, drive, budget | 핵심 | `SettingsUiState`, repositories |
| `feature/settings/ui/BudgetBottomSheet.kt` | 예산 입력 bottom sheet | 총/카테고리 예산 | 핵심 후보 | `BudgetDao.kt` |
| `feature/settings/ui/BudgetInputAmounts.kt` | 활성 입력 모드와 현재 전체 예산으로 저장 금액 계산 | 비율/금액 전환, 예산 저장 | 보조 | `BudgetBottomSheet.kt`, `BudgetInputAmountsTest.kt` |
| `feature/settings/ui/SettingsDataDialogs.kt` | export/import/Drive dialog | 백업/복원/Drive | 핵심 후보 | `DataBackupManager.kt` |
| `feature/settings/ui/SettingsInfoDialogs.kt` | 앱 정보/개인정보 dialog | 정보성 dialog | 보조 | `strings.xml` |
| `feature/settings/ui/SettingsPreferenceDialogs.kt` | 테마/월 시작일/예산 dialog | 설정 preference | 핵심 후보 | `SettingsViewModel.kt` |
| `feature/settings/ui/coachmark/SettingsCoachMark.kt` | Settings 코치마크 step | 온보딩 target 변경 | 핵심 후보 | `coachmark/README.md` |

## 2026-09-08 기능 분리 파일

| 파일 | 역할 |
|---|---|
| `feature/settings/ui/SettingsContract.kt` | `SettingsUiState`, `SettingsIntent`, `SettingsDialog` 정의 |
| `feature/settings/ui/SettingsDisplaySection.kt` | 테마 메뉴 |
| `feature/settings/ui/SettingsBudgetSection.kt` | 월 시작일과 전체/카테고리 예산 메뉴 |
| `feature/settings/ui/SettingsCreditSection.kt` | 크레딧 잔액과 화면 진입 메뉴 |
| `feature/settings/ui/SettingsCategorySection.kt` | 분류 상태와 카테고리/거래처 규칙 메뉴 |
| `feature/settings/ui/SettingsDataSection.kt` | 알림, SMS, 동기화, 백업/복원, 중복/전체 삭제 메뉴 |
| `feature/settings/ui/SettingsAppSection.kt` | 버전, 개인정보, 가이드 초기화 메뉴 |
| `feature/settings/ui/SettingsDialogs.kt` | 상태별 다이얼로그 조합과 intent 전달 |
| `feature/settings/ui/SettingsLoadingOverlay.kt` | 작업/분류 진행 표시 |
| `feature/settings/data/SettingsBackupService.kt` | export 필터/형식에 맞는 저장소 조회와 복원 순서 |
| `feature/settings/data/SettingsDataResetService.kt` | 수집 등록 차단, 큐 비우기, 선택적 초기화와 삭제 이벤트 |
