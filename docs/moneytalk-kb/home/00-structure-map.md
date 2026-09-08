---
type: structure-map
title: Home 구조 지도
description: Home 탭의 UI, ViewModel, data repository, 코치마크 파일 구조와 AI 참조 순서를 정리한다.
tags: [moneytalk, home, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# Home 구조 지도

## 패키지 구조

```text
feature/home/
├── briefing/
│   ├── SpendingBriefing.kt
│   ├── SpendingBriefingCalculator.kt
│   ├── SpendingBriefingCard.kt
│   ├── BriefingBudgetSection.kt
│   └── BriefingWeeklySection.kt
├── recurring/
│   ├── RecurringExpenseForecast.kt
│   ├── RecurringExpenseForecastCalculator.kt
│   └── RecurringExpenseForecastCard.kt
├── ui/
│   ├── HomeScreen.kt
│   ├── HomeViewModel.kt
│   ├── HomeUiState.kt
│   ├── HomePageContent.kt
│   ├── coachmark/HomeCoachMark.kt
│   ├── component/SpendingTrendSection.kt
│   ├── component/MonthlyOverviewSection.kt
│   ├── component/CategoryExpenseSection.kt
│   ├── component/AiInsightCard.kt
│   ├── component/EmptyExpenseSection.kt
│   ├── model/HomeCategoryExpenseInfo.kt
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
| `HomeUiState.kt` | state | 월별 HomePageData와 화면 HomeUiState 계약 | `HomeViewModel.kt`, `HomePageContent.kt` |
| `HomePageContent.kt` | rendering | 월 페이지의 CTA/요약/차트/카테고리/오늘 거래 조합 | `component/**` |
| `component/MonthlyOverviewSection.kt`, `CategoryExpenseSection.kt` | rendering | 월 요약, 카테고리 순위의 독립 렌더링 | `HomePageContent.kt` |
| `briefing/**` | calculation/rendering | 기록 기준 예산 잔여·일수·주간 비교 순수 계산과 기능별 렌더링 | `HomePageData`, `HomeViewModel.kt` |
| `recurring/**` | calculation/rendering | 반복 근거 판정과 다음 고정 결제 예상, 실제 거래 연결 | `HomeViewModel.kt`, `TransactionEditActivity` |
| `component/AiInsightCard.kt` | rendering | 기존 선언 유지, 현재 홈에서 미호출 | 다시 연결 시 AI 비용 정책 확인 |
| `model/HomeCategoryExpenseInfo.kt` | mapper | 순위/미분류 병합, 예산 사용률, 경고/초과 표시 값 | `CategoryExpenseSection.kt` |
| `HomeViewModel.kt` | data/action | 월별 page cache, 확장 조회/필터, 로컬 계산 연결, 미분류 분류, refresh/date 수집 | `ExpenseRepository.kt`, `IncomeRepository.kt`, `SettingsDataStore.kt` |
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
