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
│   ├── HomeComparisonPeriod.kt
│   ├── theme/HomeTheme.kt
│   ├── theme/HomeColors.kt
│   ├── coachmark/HomeCoachMark.kt
│   ├── component/SpendingTrendSection.kt
│   ├── component/MonthlyOverviewSection.kt
│   ├── component/HomeTransactionCard.kt
│   ├── component/HomeImportDataCta.kt
│   ├── component/HomeFullSyncCta.kt
│   ├── component/CategoryExpenseSection.kt
│   ├── component/AiInsightCard.kt
│   ├── component/EmptyExpenseSection.kt
│   ├── model/HomeCategoryExpenseInfo.kt
│   ├── model/HomeSpendingComparison.kt
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
| `HomePageContent.kt` | rendering | CTA/기존 그라데이션 요약/누적 차트/최근 소비 비교/예상/카테고리/오늘 전체 거래 조합 | `component/**` |
| `theme/HomeTheme.kt`, `HomeColors.kt` | rendering | 홈에만 원래 색상·Typography·카드/CTA 색상 제공 | 공용 `Theme.kt`, `component/Home*` |
| `component/HomeTransactionCard.kt`, `HomeImportDataCta.kt`, `HomeFullSyncCta.kt` | rendering/action | 원래 홈 표현과 기존 Info/콜백·권한/수집/광고 안내 계약 | `HomePageContent.kt`, 공용 카드/CTA |
| `component/MonthlyOverviewSection.kt`, `CategoryExpenseSection.kt` | rendering | 월 요약, 카테고리 순위의 독립 렌더링 | `HomePageContent.kt` |
| `briefing/**` | calculation/rendering | 예산 잔여·일수·주간 비교 계산 유지. UI는 주간 비교가 있을 때 기록 안내와 최근 소비 비교만 표시 | `HomePageData`, `HomeViewModel.kt` |
| `recurring/**` | calculation/rendering | 반복 근거 판정과 다음 고정 결제 예상, 실제 거래 연결 | `HomeViewModel.kt`, `TransactionEditActivity` |
| `component/AiInsightCard.kt` | rendering | 기존 선언 유지, 현재 홈에서 미호출 | 다시 연결 시 AI 비용 정책 확인 |
| `model/HomeCategoryExpenseInfo.kt` | mapper | 순위/미분류 병합, 예산 사용률, 경고/초과 표시 값 | `CategoryExpenseSection.kt` |
| `HomeViewModel.kt` | data/action | 월별 page cache, 확장 조회/필터, 로컬 계산 연결, 미분류 분류, refresh/date 수집 | `ExpenseRepository.kt`, `IncomeRepository.kt`, `SettingsDataStore.kt` |
| `component/SpendingTrendSection.kt` | rendering | 공통 차트 연결. 홈만 카드 없이 표시하고 오늘까지·켜진 곡선으로 Y축 조정 | `HomeSpendingTrendInfo.kt`, `core/ui/component/chart/**` |
| `model/HomeSpendingTrendInfo.kt`, `HomeSpendingComparison.kt` | mapper/calculation | 동일 경과일/과거 전체·수집 상태·실제 0원 비교를 누적 차트 정보로 변환 | `SpendingTrendSection.kt`, `HomeScreen.kt` |
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
