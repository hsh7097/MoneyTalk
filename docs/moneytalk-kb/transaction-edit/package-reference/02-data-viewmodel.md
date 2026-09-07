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

## 책임 분리와 편집 변경 감지 (2026-09-08)

- `TransactionEditUiState.kt`: 로딩/저장 결과와 편집 입력 계약.
- `TransactionEditArgs.kt`: Activity와 ViewModel이 공유하는 진입 인자.
- `TransactionEditSnapshot.kt`: 저장에 영향을 주는 입력 비교. 규칙 키워드와 카테고리/고정 일괄 적용도 포함한다. picker 열기 같은 표시 상태는 포함하지 않는다.
- `TransactionEditViewModel`: 최초 로딩 시 snapshot을 보관하고 UI에 변경 여부를 제공한다. 화면 재생성 후에도 기존 ViewModel 기준을 유지하여 미저장 변경 확인을 건너뛰지 않는다.
- 없는 거래는 `loadErrorResId` 상태로 표시하고 `save()`를 차단한다. 저장/삭제/규칙 적용의 기존 repository 호출과 새로고침 이벤트는 유지한다.

회귀 검증: `TransactionEditSnapshotTest`, `TransactionNotificationInstrumentedTest`의 화면 재생성/기존 진입/없는 ID/목표 거래 저장.
