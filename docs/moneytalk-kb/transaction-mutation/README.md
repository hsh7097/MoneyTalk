---
type: feature
title: Transaction Mutation 기능
description: 거래 추가/수정/삭제, 카테고리/고정지출/통계 제외 일괄 적용 흐름을 설명한다.
tags: [moneytalk, transaction, mutation, feature]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditViewModel.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Transaction Mutation 기능

> 상태: draft
> 기준: 2026-07-08 현재 `TransactionEditViewModel`, `HistoryDialogs`, `ExpenseRepository`, `IncomeRepository`, `DataQueryParser` action 확인

Transaction Mutation 기능은 거래를 추가/수정/삭제하고, 같은 거래처 또는 조건에 따라 카테고리/고정/통계 제외 값을 일괄 적용하는 흐름이다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [01-feature-flow.md](01-feature-flow.md) | 거래 변경 trigger부터 DB 저장/refresh까지 흐름을 설명한다. | 아이템 변경, 일괄 적용, 삭제 작업 |
| [change-log.md](change-log.md) | Transaction Mutation KB 변경 로그다. | 문서 변경 이유 확인 |
| [../transaction-edit/README.md](../transaction-edit/README.md) | 거래 수정 화면 KB다. | UI 기반 거래 변경 |
| [../chat/README.md](../chat/README.md) | AI 채팅 action KB다. | 채팅에서 거래 수정/삭제 action 수행 |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `TransactionEditViewModel.kt` | 거래 저장/삭제와 일괄 적용 option 처리 |
| `HistoryDialogs.kt` | 내역에서 지출 추가 dialog |
| `ExpenseRepository.kt`, `IncomeRepository.kt` | 거래 DB 저장/수정/삭제 repository |
| `DataRefreshEvent.kt` | 변경 후 화면 cache 갱신 event |
| `DataQueryParser.kt` | 채팅 action type contract |
| `ChatViewModel.executeAction()` | 채팅 기반 거래 변경 실행 |
