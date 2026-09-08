---
type: rendering-action
title: History Rendering Action
description: History 화면의 Composable, action, navigation 연결을 설명한다.
tags: [moneytalk, history, rendering, action, compose]
resource: app/src/main/java/com/sanha/moneytalk/feature/history/ui/
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# 03 Rendering Action

## 렌더링 구성

```text
HistoryScreen
├── HorizontalPager
├── LazyColumn 거래 목록
├── HistoryCalendar
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
