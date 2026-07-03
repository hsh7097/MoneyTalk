---
type: data-contract
title: App Functions Data Contract
description: App Functions의 함수 그룹, parameter, response model contract를 설명한다.
tags: [moneytalk, app-functions, data-contract]
resource: app/src/main/java/com/sanha/moneytalk/core/appfunctions/
timestamp: 2026-07-03T17:10:00+09:00
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

## 모델

- `MoneyTalkChatAppFunctionModels.kt`: 채팅 작업용 조회/수정 응답 모델
- `MoneyTalkFinanceSummaryModels.kt`: 월간 요약 응답 모델

함수 signature나 response model이 바뀌면 `docs/APP_FUNCTIONS.md`와 KSP 생성 결과도 확인한다.
