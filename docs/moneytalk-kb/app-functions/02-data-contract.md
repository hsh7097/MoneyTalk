---
type: data-contract
title: App Functions Data Contract
description: App Functions의 함수 그룹, parameter, response model contract를 설명한다.
tags: [moneytalk, app-functions, data-contract]
resource: app/src/main/java/com/sanha/moneytalk/core/appfunctions/
timestamp: 2026-07-09T02:45:00+09:00
status: draft
---

# 02 Data Contract

## 함수 그룹

| 그룹 | 대표 함수 | 관련 파일 |
|---|---|---|
| 상태/요약 | `getDatabaseSnapshot`, `getMonthlyFinanceSummary` | `MoneyTalkChatAppFunctions.kt`, `MoneyTalkFinanceAppFunctions.kt` |
| 지출 조회 | `getExpenses`, `getExpenseCategoryTotals`, `getExpensesByStore`, `getExpensesByCard` | `MoneyTalkChatAppFunctionReader.kt` |
| 수입 조회 | `getIncomes`, `getTotalIncome` | `MoneyTalkChatAppFunctionReader.kt` |
| 설정/메타 조회 | `getOwnedCards`, `getStoreRules`, `getCustomCategories`, `getBudgetStatus` | `MoneyTalkChatAppFunctionReader.kt` |
| 거래/설정 수정 | `addExpense`, `updateExpenseCategory`, `setBudget`, `setMonthStartDay` | `MoneyTalkChatAppFunctionReader.kt` |

현재 KSP 생성 metadata 기준 등록 함수는 50개이고, 기본 활성 함수는 46개다.
`deleteExpense`, `deleteExpensesByKeyword`, `deleteDuplicateExpenses`, `deleteStoreRule`은 `isEnabled=false`로 등록만 되어 있다.
전체 함수 목록은 [06-function-catalog.md](06-function-catalog.md)를 기준으로 본다.

## 모델

- `MoneyTalkChatAppFunctionModels.kt`: 채팅 작업용 조회/수정 응답 모델
- `MoneyTalkFinanceSummaryModels.kt`: 월간 요약 응답 모델

함수 signature나 response model이 바뀌면 KSP 생성 결과, [06-function-catalog.md](06-function-catalog.md), [07-operational-playbook.md](07-operational-playbook.md)를 같이 확인한다.
레거시 루트 문서는 KB로 흡수 후 삭제됐다. 이력 확인이 필요하면 [../source-docs/01-consolidation-map.md](../source-docs/01-consolidation-map.md)에서 삭제 이유를 먼저 확인한다.
