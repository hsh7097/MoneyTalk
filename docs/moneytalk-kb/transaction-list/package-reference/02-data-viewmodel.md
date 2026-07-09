---
type: package-reference
title: Transaction List data and ViewModel
description: Transaction Detail List ViewModel의 날짜 파싱, 지출/수입 조회, 카드 숨김, refresh 흐름을 설명한다.
tags: [moneytalk, transaction-list, viewmodel, data]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionlist/ui/TransactionDetailListViewModel.kt
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Transaction List data and ViewModel

## State

| 상태 | 의미 |
|---|---|
| `isLoading` | 날짜 파싱/조회 중 표시 상태 |
| `dateString` | Activity extra로 받은 원본 날짜 문자열 |
| `monthStr`, `dayNum` | title 표시용 월/일 |
| `expenses` | 숨김 카드 필터 적용 후 최신순 지출 목록 |
| `incomes` | 최신순 수입 목록 |

## 데이터 흐름

```text
SavedStateHandle["extra_date"]
-> parseDateAndLoad()
-> yyyy-MM-dd 파싱
-> 하루 start/end time 계산
-> OwnedCardRepository.getExcludedCardNames()
-> ExpenseRepository.getExpensesByDateRangeOnce()
-> CardVisibilityFilter.filterVisibleExpenses()
-> IncomeRepository.getIncomesByDateRangeOnce()
-> TransactionDetailListUiState
```

## 핵심 dependency

| dependency | 역할 | 변경 시 같이 볼 KB |
|---|---|---|
| `ExpenseRepository` | 하루 지출 조회 | [../../finance-data/README.md](../../finance-data/README.md) |
| `IncomeRepository` | 하루 수입 조회 | [../../finance-data/README.md](../../finance-data/README.md) |
| `OwnedCardRepository` | 숨김 카드 목록 | [../../filtering/README.md](../../filtering/README.md) |
| `CardVisibilityFilter` | 지출 목록에서 숨김 카드 제외 | [../../filtering/README.md](../../filtering/README.md) |
| `DataRefreshEvent` | 다른 화면의 거래 변경 후 재조회 | [../../data-refresh/README.md](../../data-refresh/README.md) |

## refresh 정책

- `observeRefreshEvents()`는 refresh type을 구분하지 않고 이벤트가 오면 `loadTransactions()`를 다시 호출한다.
- 거래 편집, 삭제, 전체 삭제, 카드 숨김 변경이 모두 같은 reload 경로를 탄다.
- 조건이 늘어나면 refresh type을 구분해야 하는지 먼저 결정한다.

## 주의점

1. 지출은 숨김 카드 필터를 적용하지만 수입은 카드 숨김 필터 대상이 아니다.
2. 날짜 파싱 실패 시 `isLoading = false`만 처리하고 목록은 비어 있다.
3. 하루 범위는 local `Calendar` 기준 00:00:00.000부터 23:59:59.999까지다.
4. 장기적으로 날짜 외 조건을 추가하면 Activity extra 하나로 유지할지 query object로 분리할지 먼저 판단한다.
