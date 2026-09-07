---
type: package-reference
title: Settings rendering/action
description: SettingsScreen, dialog, bottom sheet, 설정 row action 연결을 설명한다.
tags: [moneytalk, settings, compose, action]
resource: app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsScreen.kt
timestamp: 2026-07-12T00:00:00+09:00
status: verified
---

# Settings rendering/action

## 주요 UI 파일

| 파일 | 역할 |
|---|---|
| `SettingsScreen.kt` | 설정 탭 전체 UI, row group, action dispatch |
| `SettingsPreferenceDialogs.kt` | 테마/월 시작일/월 예산 dialog |
| `BudgetBottomSheet.kt` | 카테고리별 예산 입력 bottom sheet |
| `SettingsDataDialogs.kt` | 백업 export/import, Google Drive 파일 목록 dialog |
| `SettingsInfoDialogs.kt` | 앱 정보/개인정보 dialog |

## 액션 연결 원칙

- 화면 row click은 가능하면 `SettingsIntent`로 ViewModel에 전달한다.
- dialog 표시 여부는 `SettingsDialog`와 `SettingsUiState`가 관리한다.
- 파일 picker/Google sign-in처럼 Activity 결과가 필요한 액션은 UI에서 launcher를 들고 ViewModel 메서드로 결과를 전달한다.
- 예산/백업/Drive/카드 보유처럼 여러 저장소를 만지는 액션은 관련 기능 KB를 같이 확인한다.
- AI 서비스 불가 상태는 사용자 key 입력 dialog로 우회하지 않고 현재 작업 실패/서비스 상태 메시지로 안내한다.

## 최대 글자 배율 계약

- `ExportDialog`의 JSON/CSV 형식 선택지는 세로 전체 폭 `FilterChip`으로 배치한다. 가로 `Row`로 되돌리면 선택 아이콘이 있는 JSON 항목이 폭을 먼저 차지해 `CSV (엑셀)`이 한 글자씩 세로로 붕괴한다.
- 카드와 카테고리 필터는 항목 수가 가변적이므로 기존 가로 스크롤을 유지한다.
- SM-F966N 닫힘 화면 1080x2520, Android 16, `font_scale=2.0`에서 제목, 형식 선택, 데이터 유형, 필터, 로컬 저장/Google 버튼까지 스크롤 가능 상태를 확인한다.

## 예산 입력과 저장 계약

- `BudgetBottomSheet`의 비율 모드에서는 저장할 때 `BudgetInputAmounts.kt`의 `resolveCategoryBudgetAmounts()`로 현재 전체 예산과 비율 입력값을 계산한다. 비율 입력 뒤 전체 예산만 변경해도 표시 금액과 저장 금액이 같아야 한다. 예: 전체 100만원·식비 30%에서 전체를 200만원으로 바꾸면 식비 60만원을 저장한다.
- 금액 모드 또는 전체 예산이 비어 있거나 0인 경우에는 화면에 표시된 금액 입력을 저장한다. 0·빈 입력은 카테고리 예산에서 제외한다.
- 비율에서 금액으로 전환할 때도 같은 계산 함수를 사용하고, 이미 선택된 모드를 다시 눌러 입력을 재변환하지 않는다. 계산 결과는 기존 표시 방식과 같이 원 미만을 버린다.
- 잘못된 숫자·음수·금액 범위 초과는 `null` 결과로 구분하고 전체 저장 및 모드 전환을 막으며 오류를 표시한다. 일부 정상 항목만 저장하거나 값을 제한해서 저장하지 않는다. 전체 예산의 숫자 범위도 저장 전에 확인한다.
- 비율 표시와 자동 변환은 `Long`으로 계산한다. 전체 1원·카테고리 `Int.MAX_VALUE`원처럼 비율이 `Int` 범위를 넘는 경우도 원래 금액을 표현할 수 있으면 허용한다. 표시·저장은 `resolveBudgetPercentAmount()`를 함께 쓰며 곱셈 전에 계산 금액의 범위를 검증한다.
- 회귀 검증은 `BudgetInputAmountsTest`의 전체 예산 증감, 직접 입력 보존, 전체 예산 미설정, 비율 삭제, 금액 계산 경계 사례를 사용한다.

## 기능별 렌더러 (2026-09-08)

`SettingsScreen` 아래 메뉴는 `SettingsDisplaySection`, `SettingsBudgetSection`, `SettingsCreditSection`, `SettingsCategorySection`, `SettingsDataSection`, `SettingsAppSection`으로 분리한다. 진행 표시는 `SettingsLoadingOverlay`, `activeDialog` 분기는 `SettingsDialogs`가 담당한다. 메뉴 순서, 문구, 패딩, ID, 파일 선택/Drive 로그인 결과 처리는 유지한다.

코치마크 스크롤 index는 AI 크레딧 섹션 노출 여부로 계산한다. 크레딧 메뉴가 들어간 경우 카테고리/데이터 target의 실제 index도 1 증가한다.
