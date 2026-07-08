---
type: package-reference
title: Transaction Edit files checklist
description: 거래 추가/수정 화면 수정 전후 확인할 파일과 검증 질문을 정리한다.
tags: [moneytalk, transaction-edit, checklist]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionedit/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Transaction Edit files checklist

## 수정 전 확인

| 작업 | 먼저 볼 파일 |
|---|---|
| 입력 UI 변경 | `TransactionEditDetailContent.kt`, `TransactionEditScreen.kt`, `strings.xml` |
| 저장 로직 변경 | `TransactionEditViewModel.kt`, `ExpenseRepository.kt`, `IncomeRepository.kt` |
| 카테고리 picker | `TransactionEditViewModel.kt`, `CategoryProvider`, `CustomCategoryRepository` |
| 고정/통계 제외 일괄 적용 | `TransactionEditViewModel.kt`, `StoreRuleRepository`, `StatsExclusionClassifier` |
| 코치마크 | `TransactionEditCoachMark.kt`, `core/ui/coachmark/**` |

## 검증 질문

1. 지출/수입/이체성 타입별로 필수 입력 validation이 맞는가?
2. 저장 후 `DataRefreshEvent`가 관련 화면 cache를 갱신하는가?
3. 같은 거래처 일괄 적용 옵션이 의도한 범위만 수정하는가?
4. custom category 추가 후 picker와 저장 값이 같은 displayName을 쓰는가?
5. 삭제 후 caller 화면이 stale list를 유지하지 않는가?
