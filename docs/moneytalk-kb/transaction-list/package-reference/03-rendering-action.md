---
type: package-reference
title: Transaction List rendering and action
description: Transaction Detail List 화면의 Composable 구조, 거래 카드 렌더링, 편집 진입 action을 정리한다.
tags: [moneytalk, transaction-list, compose, action]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionlist/ui/TransactionDetailListScreen.kt
timestamp: 2026-09-07T00:00:00+09:00
status: draft
---

# Transaction List rendering and action

## Composable 계층

```text
TransactionDetailListScreen
-> TopAppBar
-> Loading / Empty
-> LazyColumn
   -> TransactionCardCompose(IncomeTransactionCardInfo)
   -> TransactionCardCompose(ExpenseTransactionCardInfo)
```

## 화면 상태

| 상태 | 조건 | UI |
|---|---|---|
| loading | `uiState.isLoading == true` | 중앙 `CircularProgressIndicator` |
| empty | `uiState.items` empty | `transaction_list_empty` 문구 |
| content | `uiState.items` 존재 | ViewModel이 필터/정렬한 Expense/Income 순서로 카드 렌더링 |

## Action 연결

| 사용자 action | 구현 | 영향 |
|---|---|---|
| back | `onBack` | Activity finish |
| 수입 카드 클릭 | `TransactionEditActivity.open(context, incomeId = income.id)` | 수입 편집 화면 진입 |
| 지출 카드 클릭 | `TransactionEditActivity.open(context, expenseId = expense.id)` | 지출 편집 화면 진입 |

## 변경 시 주의

1. `TransactionDetailListItem` 순서는 ViewModel에서 결정한다. key는 지출/수입 접두사와 id를 함께 사용해 같은 id끼리 충돌하지 않게 한다. 날짜 단독 진입의 수입 우선 순서는 유지한다.
2. 거래 카드 공통 UI를 바꾸려면 `core/ui/component/transaction/card/**`와 [../../ui-map/README.md](../../ui-map/README.md)를 같이 본다.
3. 카드 클릭 action을 바꾸면 [../../transaction-edit/README.md](../../transaction-edit/README.md)의 extra 계약과 맞춘다.
4. title 포맷을 바꾸면 ViewModel의 `monthStr`, `dayNum` 생성 방식도 같이 확인한다.
