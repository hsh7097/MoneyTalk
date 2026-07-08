---
type: structure-map
title: Home 구조 지도
description: Home 탭의 UI, ViewModel, data repository, 코치마크 파일 구조와 AI 참조 순서를 정리한다.
tags: [moneytalk, home, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Home 구조 지도

## 패키지 구조

```text
feature/home/
├── ui/
│   ├── HomeScreen.kt
│   ├── HomeViewModel.kt
│   ├── coachmark/HomeCoachMark.kt
│   ├── component/SpendingTrendSection.kt
│   └── model/HomeSpendingTrendInfo.kt
└── data/
    ├── ExpenseRepository.kt
    ├── IncomeRepository.kt
    ├── CategoryRepository.kt
    ├── CategoryClassifierService*.kt
    ├── GeminiCategoryRepository*.kt
    ├── StoreEmbeddingRepository*.kt
    ├── StoreRuleRepository.kt
    └── StoreRuleSyncService.kt
```

## 핵심 파일

| 파일 | 분류 | 역할 | 함께 볼 파일 |
|---|---|---|---|
| `HomeScreen.kt` | entry/rendering/action | 홈 탭 Composable, 월 이동, 카테고리/거래 클릭, dialog, 코치마크 overlay | `HomeViewModel.kt`, `HomeCoachMark.kt` |
| `HomeViewModel.kt` | data/action | 월별 page cache, repository 조회, AI insight, 미분류 분류, refresh event 수집 | `ExpenseRepository.kt`, `IncomeRepository.kt`, `SettingsDataStore.kt` |
| `component/SpendingTrendSection.kt` | rendering | 누적/추세 차트 섹션 | `HomeSpendingTrendInfo.kt`, `core/ui/component/chart/**` |
| `model/HomeSpendingTrendInfo.kt` | mapper | 홈 월별 페이지 데이터를 공통 차트 model로 변환 | `SpendingTrendSection.kt` |
| `coachmark/HomeCoachMark.kt` | onboarding | Home 화면 코치마크 step 정의 | `core/ui/coachmark/**`, `HomeScreen.kt` |
| `ExpenseRepository.kt` | data | 지출 조회/집계/수정/삭제 repository | `ExpenseDao.kt`, `finance-data/README.md` |
| `IncomeRepository.kt` | data | 수입 조회/집계/수정/삭제 repository | `IncomeDao.kt` |
| `CategoryClassifierService*.kt` | feature data | 미분류 자동 분류 orchestration | `category-classification/README.md` |

## AI 참조 순서

| 작업 | 참조 순서 |
|---|---|
| 홈 UI 섹션 수정 | `home/README.md` -> `package-reference/03-rendering-action.md` -> `HomeScreen.kt` |
| 월별 합계/cache 문제 | `home/README.md` -> `package-reference/02-data-viewmodel.md` -> `HomeViewModel.kt` |
| 카테고리 지출 클릭/상세 이동 | `HomeScreen.kt` -> `CategoryDetailActivity` -> `category-detail/README.md` |
| 미분류 자동 분류 | `HomeViewModel.kt` -> `category-classification/README.md` -> `CategoryClassifierServiceImpl.kt` |
| 홈 코치마크 | `HomeScreen.kt` -> `HomeCoachMark.kt` -> `coachmark/README.md` |
