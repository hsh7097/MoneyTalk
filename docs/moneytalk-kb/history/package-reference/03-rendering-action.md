---
type: rendering-action
title: History Rendering Action
description: History 화면의 Composable, action, navigation 연결을 설명한다.
tags: [moneytalk, history, rendering, action, compose]
resource: app/src/main/java/com/sanha/moneytalk/feature/history/ui/
timestamp: 2026-09-09T00:00:00+09:00
status: draft
---

# 03 Rendering Action

## 렌더링 구성

```text
HistoryScreen
├── HistoryScrollLayout
│   ├── HistoryTitleBar / SearchBar + PeriodSummaryCard (접히는 요약)
│   ├── FilterTabRow (고정 도구 한 줄)
│   └── HorizontalPager → LazyColumn 거래 목록 / HistoryCalendar
├── HistoryFilter
├── HistoryDialogs
├── TransactionCardCompose
├── TransactionQuickActionDialog
└── TransactionGroupHeaderCompose
```

## Action 흐름

- 외부 카테고리 필터는 `HistoryScreen`의 `LaunchedEffect(filterCategory)`에서 한 번 소비된다.
- 일반 거래 클릭은 상세 편집으로 이동한다. 롱클릭은 화면별 `TransactionQuickActionViewModel`의 수정/삭제 메뉴를 연다. 수정은 기존 `TransactionEditActivity`, 확인한 단건 삭제는 공용 `TransactionQuickActionService`가 처리한다. 기존 상세 dialog의 `HistoryIntent` 경로와 구분한다.
- `+` 버튼 또는 수정 진입은 `TransactionEditActivity`를 확인한다.
- 달력 날짜 클릭은 `BillingCycleCalendarView.onDateClick`을 통해 `HistoryScreen`으로 전달한다. `HistoryScreen`은 현재 유형/카테고리/카드/고정/정렬을 `TransactionDetailFilter`로 묶어 날짜 상세 Activity에 전달한다.
- 동기화 CTA, 권한 상태는 `MainViewModel.screenSyncUiState`를 통해 표시된다.

## 수정 시 확인

| 작업 | 먼저 볼 파일 | 함께 볼 파일 |
|---|---|---|
| 목록 카드 UI 변경 | `HistoryScreen.kt` | `core/ui/component/transaction/card/*` |
| 그룹 헤더 변경 | `HistoryScreen.kt` | `core/ui/component/transaction/header/*` |
| 달력 UI 변경 | `HistoryCalendar.kt` | `HistoryViewModel.kt` |
| 필터 UI 변경 | `HistoryFilter.kt` | `HistoryViewModel.kt` |
| 상세/삭제/메모 dialog 변경 | `HistoryDialogs.kt` | `HistoryViewModel.kt` |
| 롱클릭 수정/삭제 메뉴 | `feature/transactionactions/ui/TransactionQuickActionDialog.kt` | `TransactionQuickActionViewModel`, `TransactionQuickActionService`, `TransactionEditActivity` |

## 직접 정리와 검색

- 목록의 지출/수입 카드에 `onLongClick`을 전달한다. `TransactionTarget`에 유형과 실제 ID를 함께 보존하므로 동일 숫자 ID의 지출과 수입이 충돌하지 않는다.
- 중앙 모달에는 거래명·닫기 X·수정/삭제만 표시한다. 수정은 유형과 ID로 기존 상세 편집을 열고, 삭제는 거래명·금액이 있는 별도 확인 dialog를 거친다. 카테고리·고정·통계 제외 등 입력은 상세 편집에서 처리한다.
- 내역 검색은 전체 기간 지출과 수입을 모두 조회한다. 수입 전용 보기에서도 검색 결과를 표시하며 검색 종료 시 선택 월의 일반 목록으로 돌아간다. 유형·카드·카테고리·고정·SMS 제외가 적용되는 세부 경계는 `02-data-viewmodel.md`를 본다.
- 기존 미분류/카테고리 필터 뒤에서 롱클릭으로 직접 정리할 수 있다. `기타`를 오류나 미분류로 간주하는 새 검토 상태는 추가하지 않는다.
- 검증: 수입 전용 검색, 검색어 연속 변경/검색 종료, 검색 결과에서 편집 후 결과 유지/갱신, 메뉴 닫기, 삭제한 거래 재열기, 삭제 중 중복 동작 차단.

## 필터 기능 분리 (2026-09-08)

- `HistoryFilterSelection`은 시트 안의 9개 임시 선택 값을 하나로 묶는다. `copy`/`selectType`/`selectCategories`로 새 값을 만들며 적용 전에는 ViewModel 필터를 바꾸지 않는다.
- 유형 버튼을 다시 누르면 전체로 돌아간다. 전체 상태의 첫 세부 카테고리 선택은 해당 유형만 남기며, 이후 다른 유형 카테고리 선택은 기존 선택과 함께 유지한다. 전체 선택/초기화 시 이 규칙을 다시 시작한다.
- 카드/카테고리 전체 선택 시트는 `HistoryFilterPickers.kt`, 타일/칩/안내/요약 렌더링은 `HistoryFilterControls.kt`에 둔다. `FilterBottomSheet`는 조합, 임시 상태, 적용/닫기, 확장 스크롤과 코치마크를 담당한다.
- 달력(`HistoryCalendar`), 기간 헤더(`HistoryHeader`), 거래 대화상자(`HistoryDialogs`)는 이미 기능 경계가 있어 유지한다. 미사용 기존 `AddExpenseDialog`를 이번 작업에서 삭제하지 않는다.
- 검증: `HistoryFilterSelectionTest`의 유형 재선택·첫 카테고리 축소·다중 유형·카드/고정 유지·초기화 및 실제 시트 적용/닫기. 목록/달력 전환과 날짜 상세 필터 전달도 함께 확인한다.

## 금융 UI 정리 (2026-09-08)

- `PeriodSummaryCard`는 월/실제 결제 기간과 수입·지출 금액을 중립 배경 카드 안에서 구분한다. 금액/필터 계산은 기존 값을 사용하고, 다음 달 버튼은 실제 연·월 순서로 활성 여부를 판단한다.
- `FilterTabRow`는 목록·달력·필터를 한 행에 둔다. 활성 필터는 선택한 단일 카테고리/카드 또는 조건 종류를 표시하며, 긴 이름은 말줄임한다. 필터 아이콘과 오른쪽 48dp X는 독립적이고, X만 초기화한다. 전체 필터 상태는 접근성 `stateDescription`으로 유지한다.
- 검색·추가·월 이동은 48dp 터치 영역을 사용한다. 필터 유형/선택 상태는 앱의 단일 강조색을 쓰며 장식 그라데이션과 안내 이모지는 제거한다.
- `BillingCycleCalendarView`는 헤더가 커지거나 화면이 작아도 날짜와 금액을 압축하지 않도록 본문을 세로 스크롤한다. 날짜 상세 콜백·유형·카테고리·고정 필터 전달과 합계 의미는 유지한다.
- 일별 금액은 `CalendarAmountText`에서 실제 셀 너비와 글자 배율로 측정하여 부호·숫자·만/억 단위가 한 줄에 모두 들어오는 크기를 사용한다. 표시 금액 포맷과 정확한 원 단위 접근성 설명은 그대로 유지한다. `CalendarAmountLayoutTest`는 320dp·7열에서 1.5/2배 글자와 만/억/콤마 금액의 실제 레이아웃 및 접근성 설명을 검증한다.
- 확인 항목: 필터 적용 → 같은 필터 다시 열기 → 취소 시 선택 유지 → 전용 X 초기화, 목록/달력 전환 후 날짜 상세 진입, 큰 글자에서 월 금액/날짜/필터 버튼 접근, 밝은/어두운 화면의 지출 파랑·수입 빨강 의미색.


## 가계부 목록 공간과 고정 도구 (2026-09-09)

- `HistoryTitleBar`에 검색/추가 48dp 버튼을 배치한다. 제목·기간·월 합계는 `HistoryScrollLayout`에서 목록 또는 달력의 위 방향 중첩 스크롤만큼 먼저 접히며, 자식 목록/달력이 맨 위에 도달한 뒤 아래로 당기면 펼쳐진다.
- 보기·필터 도구는 접히는 영역 밖에 남는다. 좁은 폭/큰 글자에서는 도구를 가로로 이동할 수 있고 초기화 X는 항상 오른쪽에 유지한다. 별도의 하단 필터 행은 없다.
- 월, 결제 시작일, 검색 진입/종료와 내역 탭 재클릭은 요약을 펼친다. 목록↔달력 전환은 접힌 높이를 유지한다. 기존 목록의 맨 위로 버튼도 목록 이동 완료 후 요약을 복원한다.
- 검색 모드에서는 요약 접힘과 월 페이징을 멈추고 검색 바를 고정한다. 달력에서 검색을 시작해도 거래 목록을 표시하며, 검색을 닫으면 선택한 보기 모드로 돌아간다. 월별 데이터/집계/필터/날짜 상세 전달은 바꾸지 않는다.
- `HistoryScrollInteractionTest`는 실제 `TransactionListView`와 `BillingCycleCalendarView`를 DB 없는 fixture에 연결해 제스처에 따른 요약 접힘/회복, 고정 도구 위치, 월·검색 전환, 320dp·1.5배 글자에서 도구 접근을 검증한다. 밝은 초기/접힘·어두운 큰 글자 PNG를 저장한다. `HistoryFilterInteractionTest`는 재편집·취소·전용 초기화 및 접근성 상태를 검증한다. 실행 결과와 실제 화면 검수는 통합 QA 기록에서 별도로 확인한다.
