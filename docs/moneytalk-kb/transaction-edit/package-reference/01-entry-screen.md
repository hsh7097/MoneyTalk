---
type: package-reference
title: Transaction Edit entry/screen
description: TransactionEditActivity 진입과 신규/기존 거래 화면 초기화 흐름을 설명한다.
tags: [moneytalk, transaction-edit, entry]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditActivity.kt
timestamp: 2026-07-09T01:55:05+09:00
status: draft
---

# Transaction Edit entry/screen

## 진입

Transaction Edit는 하단 탭 route가 아니라 Activity로 열린다.
Home, History, CategoryDetail, TransactionList 같은 화면에서 거래 id/type 또는 신규 생성 intent를 전달한다.

```text
caller screen
-> TransactionEditActivity.open(...)
-> TransactionEditScreen
-> TransactionEditViewModel load/init
```

## intent extra contract

| 진입 유형 | 호출 | extra | 초기화 |
|---|---|---|---|
| 신규 거래 | `TransactionEditActivity.open(context)` | `extra_expense_id = -1`, `extra_income_id = -1` | `initNewExpense()`로 지출 추가 화면을 연다. 기본 날짜는 현재 시각이다. |
| 기존 지출 수정 | `TransactionEditActivity.open(context, expenseId = id)` | `extra_expense_id = id` | `loadExpense(id)` 후 지출 또는 이체 상태로 초기화한다. |
| 기존 수입 수정 | `TransactionEditActivity.open(context, incomeId = id)` | `extra_income_id = id` | `loadIncome(id)` 후 수입 상태로 초기화한다. |

`TransactionEditViewModel`은 `extra_initial_date`도 읽지만, 현재 `TransactionEditActivity.open()` helper는 이 값을 받거나 전달하지 않는다.
날짜별 신규 거래 기본일을 외부에서 지정해야 하는 작업이면 Activity helper와 caller를 함께 수정한다.

## 확인 포인트

- 지출과 수입은 로딩/저장 경로가 다르므로 intent extra와 `TransactionType`을 같이 확인한다.
- 신규 지출 추가와 기존 거래 수정은 초기 state 기본값이 다르다.
- Activity 결과를 caller에 직접 반환하기보다 repository 저장 후 `DataRefreshEvent`로 관련 화면을 갱신한다.

## 알림 진입

`TransactionEditActivity.createIntent(context, expenseId, incomeId)`는 화면 목록의 `open`과 거래 알림이 공유한다. extra 이름은 `TransactionEditArgs`에 모아 ViewModel과 Activity의 중복 문자열을 없앴다. 알림은 `TransactionNotificationIntents`에서 고유 URI와 홈 parent stack을 추가한다. 지출/이체는 expense ID, 수입은 income ID로 조회한다.

기존 거래 ID가 없으면 오류 안내만 표시하고 신규 거래로 초기화하지 않는다. 알림 진입과 기존 목록 진입 모두 같은 정책을 사용한다. 명시적으로 ID를 주지 않은 `open(context)`의 신규 작성은 그대로 유지한다.
