---
type: changelog
title: Transaction Edit KB 변경 로그
description: Transaction Edit KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, transaction-edit, changelog]
resource: docs/moneytalk-kb/transaction-edit/
timestamp: 2026-07-12T00:00:00+09:00
status: draft
---

# Transaction Edit KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-07-12 | SM-F966N, `font_scale=2.0`, 거래 상세/카테고리 picker QA | 날짜 값 두 줄 허용, 공통 picker 3열·두 줄 large-font 모드 추가 | `package-reference/03-rendering-action.md`, `../ui-map/01-screen-composable-index.md` | 날짜·시간 말줄임과 긴 카테고리 이름 식별 불가 문제를 함께 수정. |
| 2026-07-09 | 실기기 History `+`, 기존 거래, Transaction List 수입 카드 진입 확인 및 `TransactionEditActivity`/`TransactionEditViewModel` 대조 | intent extra 계약 보강 | `package-reference/01-entry-screen.md` | 현재 helper는 `expenseId`/`incomeId`만 전달하고, `extra_initial_date`는 ViewModel에서만 읽는 예비 계약임을 기록. |
| 2026-07-08 | `feature/transactionedit/ui/**`, `ExpenseRepository`, `IncomeRepository`, `CustomCategoryRepository` 확인 | Transaction Edit 화면 KB 생성 | `README.md`, `00-structure-map.md`, `package-reference/**`, `05-file-inventory.md` | 아이템 추가/수정/삭제, 카테고리 picker, 일괄 적용, 코치마크 작업 시작점을 분리. |
