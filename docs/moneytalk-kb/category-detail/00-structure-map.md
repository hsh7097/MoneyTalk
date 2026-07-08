---
type: structure-map
title: Category Detail 구조 지도
description: 카테고리 상세 화면의 Activity, Screen, ViewModel, filter/model 파일 역할을 정리한다.
tags: [moneytalk, category-detail, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/feature/categorydetail/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Category Detail 구조 지도

```text
HomeScreen.CategoryExpenseSection
-> CategoryDetailActivity.open(category)
-> CategoryDetailScreen
-> CategoryDetailViewModel.loadPageData()
-> ExpenseRepository / SettingsDataStore / CustomCategoryRepository
-> CategoryDetailPageContent
```

| 파일 | 분류 | 역할 | 함께 볼 파일 |
|---|---|---|---|
| `CategoryDetailActivity.kt` | entry | category extra를 받아 Activity 진입 | `CategoryDetailScreen.kt` |
| `CategoryDetailScreen.kt` | rendering/action | 상세 hero, 월 이동, 정렬, 거래 목록, 수정 진입 | `CategoryDetailViewModel.kt` |
| `CategoryDetailViewModel.kt` | data/action | page cache, category filter, 정렬, 거래 수정/삭제 | `ExpenseRepository.kt` |
| `CategoryDetailExpenseFilters.kt` | utility | keyword/card visibility 필터 | `CardVisibilityFilter.kt` |
| `ui/model/CategoryDetailPageData.kt` | model | 상세 화면 월별 page data | `CategoryDetailViewModel.kt` |
| `ui/model/CategorySpendingTrendInfo.kt` | mapper | 카테고리 상세 추세 chart data | `core/ui/component/chart/**` |
