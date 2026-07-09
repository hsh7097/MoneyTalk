---
type: package-reference
title: Transaction List entry screen
description: 날짜별 거래 상세 목록 화면의 사용자 진입 경로와 Activity extra 계약을 정리한다.
tags: [moneytalk, transaction-list, entry, activity]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionlist/ui/TransactionDetailListActivity.kt
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Transaction List entry screen

## 진입 흐름

```text
History calendar/day surface
-> TransactionDetailListActivity.open(context, date)
-> TransactionDetailListActivity
-> TransactionDetailListScreen
-> TransactionDetailListViewModel(savedStateHandle)
```

Transaction Detail List는 하단 탭 route가 아니라 날짜별 거래를 보여주는 별도 Activity다.
현재 entry 계약은 `yyyy-MM-dd` 문자열 하나다.

## Activity 계약

| 항목 | 현재 값 | 사용 위치 | 변경 시 같이 볼 파일 |
|---|---|---|---|
| `EXTRA_DATE` | `extra_date` | `TransactionDetailListActivity.open`, `TransactionDetailListViewModel` | History 날짜 클릭부, 날짜 포맷 문구 |
| `date` 형식 | `yyyy-MM-dd` | `SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)` | 날짜 파싱, title 표시, empty state |

## Activity 책임

| 파일 | 책임 |
|---|---|
| `TransactionDetailListActivity.kt` | `open(context, date)` helper, theme mode 수집, `TransactionDetailListScreen(onBack = finish)` 연결 |
| `TransactionDetailListViewModel.kt` | SavedStateHandle에서 date를 읽고 하루 범위의 지출/수입을 조회한다. |
| `TransactionDetailListScreen.kt` | 날짜 title과 거래 카드 목록을 표시하고 카드 클릭 시 거래 편집으로 이동한다. |

## 수정 기준

1. 날짜 외 조건을 추가하면 Activity extra, ViewModel state, 화면 title/empty 문구를 동시에 갱신한다.
2. date 포맷을 바꾸면 `parseDateAndLoad()`와 `loadTransactions()`의 `SimpleDateFormat`을 같이 바꾼다.
3. 지출/수입 카드 클릭은 `TransactionEditActivity.open(context, expenseId = ...)` 또는 `incomeId = ...` 계약을 따른다.
4. Activity 진입점이 바뀌면 [../../02-screen-entry-paths.md](../../02-screen-entry-paths.md)와 [../../screen-requirements/01-screen-requirements-index.md](../../screen-requirements/01-screen-requirements-index.md)를 갱신한다.
