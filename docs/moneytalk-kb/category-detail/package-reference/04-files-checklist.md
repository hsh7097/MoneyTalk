---
type: package-reference
title: Category Detail files checklist
description: Category Detail 수정 전후 확인할 파일과 검증 질문을 정리한다.
tags: [moneytalk, category-detail, checklist]
resource: app/src/main/java/com/sanha/moneytalk/feature/categorydetail/
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Category Detail files checklist

## 빠른 파일 찾기

| 변경 유형 | 먼저 볼 파일 | 함께 볼 파일 |
|---|---|---|
| 진입 extra 변경 | `CategoryDetailActivity.kt` | `CategoryDetailViewModel.kt`, 홈 카테고리 클릭부 |
| 월 이동/cache | `CategoryDetailViewModel.kt` | `MonthPagerUtils`, `DateUtils`, `CategoryDetailScreen.kt` |
| 정렬 추가/수정 | `CategoryDetailViewModel.kt` | `CategoryDetailScreen.kt`, string resource |
| 카드 숨김/통계 제외 | `CategoryDetailExpenseFilters.kt` | `CardVisibilityFilter.kt`, `SettingsDataStore`, [../../filtering/README.md](../../filtering/README.md) |
| 거래 수정/삭제 | `CategoryDetailViewModel.kt` | `ExpenseRepository`, `DeletedSmsTracker`, [../../transaction-mutation/README.md](../../transaction-mutation/README.md) |
| 차트/예산 표시 | `CategorySpendingTrendInfo.kt` | `BudgetDao`, `SpendingTrendSection` |

## 검증 질문

1. 기본 카테고리와 custom category가 모두 올바른 emoji/name으로 표시되는가?
2. `includeSubcategories`가 true일 때 하위 카테고리까지 합계와 거래 목록에 포함되는가?
3. 월 시작일 변경 후 cache가 비워지고 현재/인접 월이 다시 로드되는가?
4. 카드 숨김, SMS 제외 키워드, 통계 제외가 목록/차트/header 합계에 의도대로 반영되는가?
5. 거래 삭제 후 SMS 재동기화에서 삭제 거래가 되살아나지 않도록 `DeletedSmsTracker`가 유지되는가?
6. 거래 수정 후 `DataRefreshEvent`로 상세 화면 cache가 최신화되는가?

## 권장 검증

- `.\gradlew.bat assembleDebug`
- 홈에서 카테고리 카드 클릭 후 Category Detail 진입
- 월 이동, 최신순/가격순 전환
- 숨김 카드와 통계 제외 거래가 있는 데이터에서 합계/목록 확인
- 거래 카드 클릭 후 Transaction Edit 진입
