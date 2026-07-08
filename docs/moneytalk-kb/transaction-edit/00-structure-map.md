---
type: structure-map
title: Transaction Edit 구조 지도
description: 거래 추가/수정 화면의 Activity, Screen, DetailContent, ViewModel, model 파일 역할을 정리한다.
tags: [moneytalk, transaction-edit, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionedit/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Transaction Edit 구조 지도

## 패키지 구조

```text
feature/transactionedit/ui/
├── TransactionEditActivity.kt
├── TransactionEditScreen.kt
├── TransactionEditDetailContent.kt
├── TransactionEditDateTimeMapper.kt
├── TransactionEditViewModel.kt
├── coachmark/TransactionEditCoachMark.kt
└── model/TransactionType.kt
```

## 핵심 파일

| 파일 | 분류 | 역할 | 함께 볼 파일 |
|---|---|---|---|
| `TransactionEditActivity.kt` | entry | edit/new intent extra를 받아 Compose 화면을 연다. | `TransactionEditScreen.kt` |
| `TransactionEditScreen.kt` | entry/rendering/action | ViewModel state 수집, date/time picker, category picker, dialog 연결 | `TransactionEditViewModel.kt` |
| `TransactionEditDetailContent.kt` | rendering/action | 실제 입력 폼, hero card, 자동화 옵션, bottom action | `TransactionEditUiState` |
| `TransactionEditDateTimeMapper.kt` | utility | date/time UI 값과 millis 변환 | `DateUtils` |
| `TransactionEditViewModel.kt` | data/action | 신규/기존 거래 로딩, 저장, 삭제, 일괄 적용, category entries | `ExpenseRepository.kt`, `IncomeRepository.kt` |
| `coachmark/TransactionEditCoachMark.kt` | onboarding | 수정 화면 코치마크 step | `coachmark/README.md` |
| `model/TransactionType.kt` | model | 지출/수입/이체성 타입 구분 | `TransactionEditViewModel.kt` |

## AI 참조 순서

| 작업 | 참조 순서 |
|---|---|
| 신규 거래 추가 | `transaction-edit/README.md` -> `package-reference/01-entry-screen.md` -> `TransactionEditViewModel.initNewExpense()` |
| 지출/수입 저장 로직 | `package-reference/02-data-viewmodel.md` -> `saveAsExpense()`/`saveAsIncome()` -> `transaction-mutation/README.md` |
| 카테고리 picker | `TransactionEditScreen.kt` -> `TransactionEditViewModel.selectCategory()` -> `category-settings`/`category-classification` KB |
| UI 폼 변경 | `package-reference/03-rendering-action.md` -> `TransactionEditDetailContent.kt` |
