---
type: feature-flow
title: Transaction Mutation 기능 흐름
description: 거래 추가/수정/삭제와 일괄 적용 후 refresh event 발행 흐름을 설명한다.
tags: [moneytalk, transaction, mutation, feature-flow]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditViewModel.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Transaction Mutation 기능 흐름

## UI 기반 변경

```text
TransactionEditScreen / AddExpenseDialog
-> ViewModel validation
-> ExpenseRepository or IncomeRepository
-> optional same-store/category/fixed/stats-excluded propagation
-> DataRefreshEvent emit
-> Home/History/CategoryDetail refresh
```

## Chat action 기반 변경

```text
ChatViewModel
-> Gemini/DataQueryParser ActionType
-> executeAction()
-> Repository update/delete/add
-> ActionResult
-> DataRefreshEvent
```

## 검증 질문

1. 단일 거래 변경과 일괄 적용 범위가 구분되는가?
2. 수입/지출 저장 경로가 각각 올바른 entity와 DAO를 쓰는가?
3. 카테고리 일괄 변경이 사용자 학습/embedding/source 정책과 충돌하지 않는가?
4. 변경 후 관련 화면 cache가 stale하지 않은가?
5. 채팅 action은 필요한 필드를 검증한 뒤 실행하는가?
