---
type: package-reference
title: Home data/ViewModel
description: HomeViewModel의 state, page cache, repository 조회, AI insight, 분류 흐름을 설명한다.
tags: [moneytalk, home, viewmodel, data]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeViewModel.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Home data/ViewModel

## 상태

`HomeUiState`는 현재 year/month, page cache, 선택 카테고리, 로딩/error, 분류 dialog, AI insight 상태를 가진다.
월별 데이터는 `HomePageData`로 묶이며 현재/인접 월을 cache한다.

## 데이터 흐름

```text
HomeViewModel.loadPageData(year, month)
-> SettingsDataStore monthStartDay/excluded cards
-> ExpenseRepository / IncomeRepository / BudgetDao
-> category sums, today expenses/incomes, monthly totals
-> HomePageData cache update
-> HomePageContent render
```

## 주요 action

| 메서드 | 역할 | 주의 |
|---|---|---|
| `loadCurrentAndAdjacentPages()` | 현재/인접 월 page cache 로딩 | 월 이동 UX와 cache eviction 영향 |
| `observeDataRefreshEvents()` | 거래/카테고리/카드 변경 event 수집 | Home/History/CategoryDetail 갱신 경계 확인 |
| `loadAiInsight()` | 홈 한줄 AI 인사이트 로딩 | Gemini 모델/비용 문서는 `chat`/`MONETIZATION`도 확인 |
| `selectCategory()` | 홈 카테고리 선택 state 변경 | category detail 이동과 혼동하지 않기 |
| `classifyUnclassifiedExpenses()` | 미분류 일괄 분류 | `category-classification` KB 필수 |
| `updateExpenseCategory()` | 가게명 기준 카테고리 변경 | Store rule/embedding 학습 영향 |

## 함께 볼 데이터 문서

- [finance-data/README.md](../../finance-data/README.md): DAO/Repository/Entity
- [category-classification/README.md](../../category-classification/README.md): 자동/수동 카테고리 분류
- [data-refresh/README.md](../../data-refresh/README.md): `DataRefreshEvent` 갱신 흐름
