---
type: package-reference
title: Home files checklist
description: Home 수정 전후 확인할 파일과 검증 질문을 정리한다.
tags: [moneytalk, home, checklist]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Home files checklist

## 수정 전 확인

| 작업 | 먼저 볼 파일 |
|---|---|
| 홈 UI 섹션 추가/변경 | `HomeScreen.kt`, `SpendingTrendSection.kt`, `HomeSpendingTrendInfo.kt` |
| 월별 데이터/합계 변경 | `HomeViewModel.kt`, `ExpenseRepository.kt`, `IncomeRepository.kt`, `BudgetDao.kt` |
| 카테고리 지출/분류 변경 | `HomeViewModel.kt`, `CategoryClassifierServiceImpl.kt`, `category-classification/README.md` |
| 코치마크 변경 | `HomeScreen.kt`, `HomeCoachMark.kt`, `core/ui/coachmark/**` |
| 오늘 거래/거래 수정 액션 | `HomeScreen.kt`, `TransactionEditActivity`, `ExpenseRepository.kt`, `IncomeRepository.kt` |

## 검증 질문

1. 커스텀 월 시작일이 월별 합계와 표시 기간에 반영되는가?
2. 제외 카드와 `isExcludedFromStats`가 홈 합계/차트에 반영되는가?
3. `DataRefreshEvent` 수신 후 현재/인접 page cache가 stale하지 않은가?
4. Home 코치마크 target key가 실제 렌더링 target과 일치하는가?
5. 카테고리 클릭 시 `CategoryDetailActivity`로 전달하는 category 값이 displayName 기준인가?
