---
type: feature-flow
title: App Functions Feature Flow
description: App Functions 호출, reader 처리, 응답 반환 흐름을 설명한다.
tags: [moneytalk, app-functions, flow]
resource: app/src/main/java/com/sanha/moneytalk/core/appfunctions/
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 01 Feature Flow

## 조회 함수 흐름

```text
agent call
→ MoneyTalkChatAppFunctions.getExpenses(...)
→ execute { reader.getExpenses(...) }
→ MoneyTalkChatAppFunctionReader
→ Repository/DAO/DataStore
→ MoneyTalkExpenseListResponse
```

## 월간 요약 함수 흐름

```text
agent call
→ MoneyTalkFinanceAppFunctions.getMonthlyFinanceSummary(...)
→ MoneyTalkFinanceSummaryReader.readMonthlySummary(...)
→ ExpenseRepository / IncomeRepository / SettingsDataStore / BudgetDao
→ MoneyTalkMonthlyFinanceSummary
```

## 수정 함수 흐름

```text
agent call
→ @AppFunction update/add/set method
→ Reader validation
→ Repository/DAO/DataStore write
→ DataRefreshEvent emit where needed
→ MoneyTalkOperationResult
```

## 삭제성 함수 정책

삭제성 함수는 `isEnabled=false`로 기본 비활성 상태다.
삭제가 필요하면 사용자 승인과 UI/Repository 안전 경로를 먼저 검토한다.
