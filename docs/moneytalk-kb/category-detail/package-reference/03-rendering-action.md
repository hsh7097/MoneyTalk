---
type: package-reference
title: Category Detail rendering and action
description: Category Detail 화면의 Composable 구조, pager, hero, 거래 목록, 정렬 action을 정리한다.
tags: [moneytalk, category-detail, compose, action]
resource: app/src/main/java/com/sanha/moneytalk/feature/categorydetail/ui/CategoryDetailScreen.kt
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Category Detail rendering and action

## Composable 계층

```text
CategoryDetailScreen
-> HorizontalPager / MonthNavigationHeader
-> CategoryDetailPageContent
   -> CategoryDetailHeroCard
   -> SpendingTrendSection
   -> CategoryTransactionListHeader
   -> TransactionCardCompose(ExpenseTransactionCardInfo)
```

## 주요 UI 블록

| 블록 | 구현 | 데이터 |
|---|---|---|
| 월 navigation | `MonthNavigationHeader`와 pager state | `selectedYear`, `selectedMonth`, `setMonth()` |
| hero card | `CategoryDetailHeroCard` | category emoji/name, 월 지출, 예산 |
| 추세 차트 | `SpendingTrendSection` | `CategorySpendingTrendInfo.from(...)` |
| 거래 목록 header | `CategoryTransactionListHeader` | 거래 수, 정렬 segmented tab |
| 거래 카드 | `TransactionCardCompose(ExpenseTransactionCardInfo)` | `CategoryTransactionItem.ExpenseItem` |

## Action 연결

| 사용자 action | 연결 함수 | 영향 |
|---|---|---|
| 뒤로가기 | `onBack` | Activity finish |
| 월 swipe/button | `viewModel.setMonth(year, month)` | 현재/인접 월 cache load |
| 정렬 tab 변경 | `viewModel.setSortOrder(order)` | cache 안 거래 목록 재정렬 |
| 거래 카드 클릭 | 거래 수정 화면 진입 | [../../transaction-edit/README.md](../../transaction-edit/README.md) extra 계약 확인 |
| 거래 삭제/메모/카테고리 변경 | ViewModel CRUD 함수 | [../../transaction-mutation/README.md](../../transaction-mutation/README.md) 영향 확인 |

## 변경 시 주의

1. pager의 현재 월과 ViewModel의 `selectedYear/month`가 어긋나면 인접 월 preloading이 깨질 수 있다.
2. `CategoryDetailPageContent`에 새 블록을 추가하면 loading/empty/error 상태에서 같은 블록이 보여야 하는지 먼저 정한다.
3. 정렬 tab을 늘리면 `CategorySortOrder`, `setSortOrder()`, `buildTransactionItems()`를 동시에 갱신한다.
4. 거래 카드 UI 자체를 바꾸면 공통 [../../ui-map/README.md](../../ui-map/README.md)와 transaction card 공통 컴포넌트 영향도 확인한다.
