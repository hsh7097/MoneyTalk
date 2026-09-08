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

`SettingsScreen` 아래 메뉴는 `SettingsDisplaySection`, `SettingsBudgetSection`, `SettingsCreditSection`, `SettingsCategorySection`, `SettingsDataSection`, `SettingsAppSection`으로 분리한다. 진행 표시는 `SettingsLoadingOverlay`, `activeDialog` 분기는 `SettingsDialogs`가 담당한다. 메뉴 순서, 문구, ID, 파일 선택/Drive 로그인 결과 처리는 유지한다.

코치마크 스크롤 index는 AI 크레딧 섹션 노출 여부로 계산한다. 크레딧 메뉴가 들어간 경우 카테고리/데이터 target의 실제 index도 1 증가한다.

## 금융 UI 정리 (2026-09-08)

- 설정 목록 외부 여백은 20dp로 통일한다. 공통 설정 섹션/행의 중립색 카드·아이콘·텍스트 계층을 따르고, 카테고리 정리/알림 토글 custom 행도 60dp 최소 높이와 22dp 아이콘·14dp 간격을 사용한다.
- 설명 텍스트는 한 줄로 자르지 않고 줄바꿈을 허용하며 읽을 수 있는 보조 텍스트 색상을 쓴다.
- 섹션 순서 및 item 수, 크레딧 조건부 표시, 코치마크 스크롤 index, 모든 설정/복원/삭제 콜백은 바꾸지 않는다.
- 확인 항목: 테마/예산 시트 진입·취소, 큰 글자에서 토글과 설명 접근, 크레딧 유무에 따른 코치마크 위치, 삭제 확인과 데이터 관리 메뉴의 기존 동작.

## 미정리 거래 직접 확인 (2026-09-09)

- `SettingsCategorySection`의 `미정리 거래`는 `CategoryReviewActivity`의 전체 기간 목록을 연다. 기존 자동 분류는 바로 아래 `자동으로 분류하기`로 분리하며, 목록 열기와 조회만으로 분류 요청이나 데이터 변경을 실행하지 않는다. 분류 중에도 직접 확인 진입은 유지하고 자동 실행만 중복 클릭을 막는다.
- 설정 건수와 목록은 `feature/categoryreview/data/CategoryReviewRepository.kt`의 같은 Flow를 사용한다. 기존 DB의 `category == Category.UNCLASSIFIED.displayName`인 지출에 SMS 제외 키워드와 숨긴 카드 필터를 적용한다. `기타`를 미정리로 간주하지 않고, 기간·금액·통계 제외 여부로 거래를 추가 제외하지 않는다. 수입은 이 목록의 대상이 아니다.
- `CategoryReviewActivity`는 외부 노출하지 않는다. `CategoryReviewScreen`은 날짜별 최신순 목록과 로딩·오류/재시도·빈 상태를 표시한다. 선택한 지출 ID를 기존 `TransactionEditActivity.open(expenseId=...)`에 전달하고 같은 거래처 적용 및 저장 정책을 그대로 사용한다. 원문이나 카테고리 저장 로직을 새 화면에 복제하지 않는다.
- Room 지출 Flow, 카드 Flow와 전역 갱신 이벤트로 목록을 다시 계산한다. 편집 복귀 시 조회도 갱신하되 기존 목록을 유지해 스크롤 상태를 불필요하게 초기화하지 않는다. 설정 건수도 같은 소스를 관찰하고 설정 복귀 시 다시 구독한다. 자동 분류 내부 진행 건수는 숨김 필터가 없는 기존 서비스 값이므로 직접 확인 건수에 덮어쓰지 않는다.
- 구현 파일: `CategoryReviewActivity`, `CategoryReviewScreen`/`CategoryReviewContent`, `CategoryReviewViewModel`, `CategoryReviewUiState`, `CategoryReviewRepository`, `CategoryReviewFilter`. 신규 문구는 한국어/영어 `strings_category_review.xml`에 둔다.
- 회귀 확인: `CategoryReviewFilterTest`는 카테고리 대상·노출 필터·0원/음수/통계 제외·정렬·날짜와 건수를 검증한다. `CategoryReviewRepositoryTest`는 임시 메모리 DB에서 단건/같은 거래처 수정 후 자동 반영, 숨김 조건 갱신, 실패 후 재시도를 확인한다. `CategoryReviewScreenTest`는 상태별 표시, 유형을 보존하는 편집 ID, 직접 확인/자동 분류 액션 분리와 큰 글자 메뉴를 확인한다. 테스트는 준비한 항목이며 실행 결과는 통합 검증 기록을 따른다.
