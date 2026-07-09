---
type: package-reference
title: Transaction List rendering and action
description: Transaction Detail List 화면의 Composable 구조, 거래 카드 렌더링, 편집 진입 action을 정리한다.
tags: [moneytalk, transaction-list, compose, action]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionlist/ui/TransactionDetailListScreen.kt
timestamp: 2026-07-09T07:05:00+09:00
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
| empty | 지출/수입 모두 empty | `transaction_list_empty` 문구 |
| content | 지출 또는 수입 존재 | 수입 카드 먼저, 지출 카드 다음 렌더링 |

## Action 연결

| 사용자 action | 구현 | 영향 |
|---|---|---|
| back | `onBack` | Activity finish |
| 수입 카드 클릭 | `TransactionEditActivity.open(context, incomeId = income.id)` | 수입 편집 화면 진입 |
| 지출 카드 클릭 | `TransactionEditActivity.open(context, expenseId = expense.id)` | 지출 편집 화면 진입 |

## 변경 시 주의

1. 수입과 지출을 시간순으로 섞어 표시하려면 현재의 "수입 먼저, 지출 다음" 구조를 바꾸고 key 충돌을 확인한다.
2. 거래 카드 공통 UI를 바꾸려면 `core/ui/component/transaction/card/**`와 [../../ui-map/README.md](../../ui-map/README.md)를 같이 본다.
3. 카드 클릭 action을 바꾸면 [../../transaction-edit/README.md](../../transaction-edit/README.md)의 extra 계약과 맞춘다.
4. title 포맷을 바꾸면 ViewModel의 `monthStr`, `dayNum` 생성 방식도 같이 확인한다.
