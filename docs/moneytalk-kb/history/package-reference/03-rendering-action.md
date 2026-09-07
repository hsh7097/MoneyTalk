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
└── TransactionGroupHeaderCompose
```

## Action 흐름

- 외부 카테고리 필터는 `HistoryScreen`의 `LaunchedEffect(filterCategory)`에서 한 번 소비된다.
- 거래 클릭/삭제/메모/카테고리 변경은 `HistoryIntent`를 통해 `HistoryViewModel`로 전달된다.
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

## 필터 기능 분리 (2026-09-08)

- `HistoryFilterSelection`은 시트 안의 9개 임시 선택 값을 하나로 묶는다. `copy`/`selectType`/`selectCategories`로 새 값을 만들며 적용 전에는 ViewModel 필터를 바꾸지 않는다.
- 유형 버튼을 다시 누르면 전체로 돌아간다. 전체 상태의 첫 세부 카테고리 선택은 해당 유형만 남기며, 이후 다른 유형 카테고리 선택은 기존 선택과 함께 유지한다. 전체 선택/초기화 시 이 규칙을 다시 시작한다.
- 카드/카테고리 전체 선택 시트는 `HistoryFilterPickers.kt`, 타일/칩/안내/요약 렌더링은 `HistoryFilterControls.kt`에 둔다. `FilterBottomSheet`는 조합, 임시 상태, 적용/닫기, 확장 스크롤과 코치마크를 담당한다.
- 달력(`HistoryCalendar`), 기간 헤더(`HistoryHeader`), 거래 대화상자(`HistoryDialogs`)는 이미 기능 경계가 있어 유지한다. 미사용 기존 `AddExpenseDialog`를 이번 작업에서 삭제하지 않는다.
- 검증: `HistoryFilterSelectionTest`의 유형 재선택·첫 카테고리 축소·다중 유형·카드/고정 유지·초기화 및 실제 시트 적용/닫기. 목록/달력 전환과 날짜 상세 필터 전달도 함께 확인한다.
