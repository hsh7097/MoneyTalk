---
type: package-reference
title: Transaction List entry screen
description: 날짜별 거래 상세 목록 화면의 사용자 진입 경로와 Activity extra 계약을 정리한다.
tags: [moneytalk, transaction-list, entry, activity]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionlist/ui/TransactionDetailListActivity.kt
timestamp: 2026-09-07T00:00:00+09:00
status: draft
---

# Transaction List entry screen

## 진입 흐름

```text
History calendar/day surface
-> HistoryScreen.onDateClick
-> TransactionDetailListActivity.open(context, date, filter)
-> TransactionDetailListActivity
-> TransactionDetailListScreen
-> TransactionDetailListViewModel(savedStateHandle)
```

Transaction Detail List는 하단 탭 route가 아니라 날짜별 거래를 보여주는 별도 Activity다.
날짜 `yyyy-MM-dd` 문자열은 필수이며, 달력 진입에서는 현재 내역 필터를 함께 전달한다. 기존 `open(context, date)`와 날짜 extra만 있는 intent는 필터 없는 기본 동작을 유지한다.

## Activity 계약

| 항목 | 현재 값 | 사용 위치 | 변경 시 같이 볼 파일 |
|---|---|---|---|
| `EXTRA_DATE` | `extra_date` | `TransactionDetailListActivity.open`, `TransactionDetailListViewModel` | History 날짜 클릭부, 날짜 포맷 문구 |
| `date` 형식 | `yyyy-MM-dd` | `SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)` | 날짜 파싱, title 표시, empty state |
| 필터 여부 | `extra_filter_enabled` Boolean | `TransactionDetailFilter.fromSavedStateHandle` | 누락/false이면 기존 날짜 단독 진입 |
| 정렬/고정 조건 | `extra_sort_order`, `extra_fixed_filter` String | enum name 복원, 미지원 값은 기본값 | History 정렬/고정 필터 |
| 거래 유형 | `extra_show_expenses`, `extra_show_incomes`, `extra_show_transfers` Boolean | 유형별 표시, 누락 시 true | History 필터 |
| 카테고리/카드 | `extra_expense_categories`, `extra_income_categories`, `extra_transfer_categories`, `extra_card_names` String ArrayList | 선택 집합 복원, 누락 시 전체 | History 필터 |

## Activity 책임

| 파일 | 책임 |
|---|---|
| `TransactionDetailListActivity.kt` | `open(context, date, filter = null)` helper, primitive extra 작성, theme 및 finish 연결 |
| `TransactionDetailListViewModel.kt` | SavedStateHandle에서 날짜와 선택 필터를 복원하고 하루 범위의 지출/수입에 적용한다. |
| `TransactionDetailListScreen.kt` | 날짜 title과 거래 카드 목록을 표시하고 카드 클릭 시 거래 편집으로 이동한다. |

## 수정 기준

1. 날짜 외 조건을 추가하면 Activity extra, ViewModel state, 화면 title/empty 문구를 동시에 갱신한다.
2. date 포맷을 바꾸면 `parseDateAndLoad()`와 `loadTransactions()`의 `SimpleDateFormat`을 같이 바꾼다.
3. 지출/수입 카드 클릭은 `TransactionEditActivity.open(context, expenseId = ...)` 또는 `incomeId = ...` 계약을 따른다.
4. Activity 진입점이 바뀌면 [../../02-screen-entry-paths.md](../../02-screen-entry-paths.md)와 [../../screen-requirements/01-screen-requirements-index.md](../../screen-requirements/01-screen-requirements-index.md)를 갱신한다.
