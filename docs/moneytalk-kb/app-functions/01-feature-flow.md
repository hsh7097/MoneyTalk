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
→ execute { actionExecutor.update/add/set(...) }
→ MoneyTalkChatAppFunctionActionExecutor validation
→ Repository/DAO/DataStore write
→ DataRefreshEvent emit where needed
→ MoneyTalkOperationResult
```

## 복합 분석 흐름

```text
agent call
→ MoneyTalkChatAppFunctions.analyzeExpenses(...)
→ Reader: 날짜 범위 검증 + Repository 조회
→ MoneyTalkAppFunctionAnalyticsCalculator: 필터 → 그룹 → 메트릭 → 정렬/개수 제한
→ MoneyTalkAnalyticsResponse
```

날짜 해석은 조회와 수정이 `MoneyTalkAppFunctionInputRules.parseDate`를 공유한다. 카테고리 하위 항목 해석과 결과 개수 제한도 Reader/계산기가 같은 규칙을 사용한다. 조회·수정 모두 노출 클래스의 `execute`에서 IO dispatcher 및 `AppFunctionInvalidArgumentException` 변환을 유지한다.

## 삭제성 함수 정책

삭제성 함수는 `isEnabled=false`로 기본 비활성 상태다.
삭제가 필요하면 사용자 승인과 UI/Repository 안전 경로를 먼저 검토한다.
