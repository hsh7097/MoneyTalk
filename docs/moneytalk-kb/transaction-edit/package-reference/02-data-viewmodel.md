---
type: package-reference
title: Transaction Edit data/ViewModel
description: TransactionEditViewModel의 state, 저장/삭제, category, 고정지출/통계 제외 일괄 적용 흐름을 설명한다.
tags: [moneytalk, transaction-edit, viewmodel, data]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditViewModel.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Transaction Edit data/ViewModel

## 상태

`TransactionEditUiState`는 거래 type, amount, store/source, category, date/time, memo, 고정 여부, 통계 제외, 일괄 적용 옵션, picker/dialog 상태를 가진다.

## 주요 action

| 메서드 | 역할 | 함께 볼 파일 |
|---|---|---|
| `loadExpense()` / `loadIncome()` | 기존 거래 로딩 | `ExpenseRepository`, `IncomeRepository` |
| `initNewExpense()` | 신규 지출 기본 state | `TransactionType` |
| `selectCategory()` / `addCategoryFromPicker()` | category 선택/추가 | `CustomCategoryRepository`, `CategoryProvider` |
| `save()` | state validation 후 지출/수입 저장 분기 | `saveAsExpense()`, `saveAsIncome()` |
| `saveAsExpense()` | 지출 저장, 카테고리/고정/통계 제외 일괄 적용 | `transaction-mutation/README.md` |
| `saveAsIncome()` | 수입 저장 | `IncomeRepository` |
| `delete()` | 거래 삭제 | `DataRefreshEvent` |

## 주의 경계

- category displayName과 custom category name을 혼동하지 않는다.
- 고정지출/통계 제외 일괄 적용은 현재 거래뿐 아니라 같은 store/rule 대상에 영향을 줄 수 있다.
- 저장 후 Home/History/CategoryDetail cache refresh를 고려한다.
