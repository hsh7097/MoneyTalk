---
type: package-reference
title: Home surface map
description: Home 탭의 화면 블록, 데이터 출처, 사용자 진입점, 보조 화면 연결을 섹션 단위로 정리한다.
tags: [moneytalk, home, surface-map, compose]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# Home surface map

Home은 하단 탭 첫 번째 화면이다. `HomeScreen`이 state와 Activity callback을 연결하고, `HomePageContent`가 월별 `HomePageData`를 섹션 단위로 렌더링한다.

## 화면 블록

| 블록 | 표시 조건 | 데이터/상태 | 사용자 액션 | 함께 볼 파일 |
|---|---|---|---|---|
| SMS 가져오기 CTA | 현재 월이고 SMS 권한이 없거나 현재 월이 아직 동기화되지 않은 빈 상태 | `hasSmsPermission`, `isMonthSynced`, `pageData.monthlyExpense/monthlyIncome` | 현재 월 증분 동기화 요청 | `HomeScreen.kt`, `MainViewModel.kt`, `ImportDataCtaSection` |
| 과거 월 전체 동기화 CTA | 현재 월이 아니고 해당 월 sync coverage가 없을 때 | `isPartiallyCovered`, `isAdEnabled`, `isSyncing` | 광고/크레딧 정책을 거쳐 월별 full sync 요청 | `FullSyncCtaSection`, `MainViewModel.showFullSyncAdDialog()` |
| 월간 현황 hero | 항상 표시 | 월 시작일 기준 기간, 월 수입, 월 지출 | 이전/다음 월 이동 | `MonthlyOverviewSection`, `DateUtils.getCustomMonthPeriod()` |
| 누적 지출 추이 | `dailyCumulativeExpenses`가 비어 있지 않을 때 | 현재 월, 전월 동일 기간, 최근 3/6개월 평균 누적 데이터 | 차트 확인 전용 | `SpendingTrendSection`, `HomeSpendingTrendInfo`, `CumulativeChartDataBuilder` |
| 소비 브리핑 | `pageData.spendingBriefing != null` | 월 잔여/초과 예산, 오늘 포함 잔여 일수, 최근/직전 7일 지출 | 증가 카테고리 내역, 예산 미설정 시 설정 경로 안내 | `SpendingBriefingCard`, `SpendingBriefingCalculator` |
| 고정 지출 예상 | 현재 회계월이고 계산 후보가 있을 때 | 오늘부터 30일, 반복 근거가 충분한 거래의 예상일/금액 | 처음 3개·전체 보기, 최신 실제 근거 거래 열기 | `RecurringExpenseForecastCard`, `RecurringExpenseForecastCalculator` |
| 카테고리 지출 | 항상 표시 | 통계 포함 지출의 카테고리 합계, 카테고리별 예산 | 카테고리 선택/해제, 상세 이동 | `CategoryExpenseSection`, `CategoryDetailActivity.open()` |
| 오늘 내역 | 현재 월일 때만 표시 | 오늘 지출/수입, 오늘 지출 합계/건수 | 클릭 상세 편집, 롱클릭 수정/삭제 메뉴 | `TransactionCardCompose`, `TransactionQuickActionDialog`, `TransactionEditActivity` |
| Scroll to top FAB | 리스트가 스크롤된 뒤 표시 | `LazyListState` | 최상단으로 스크롤 | `HomePageContent` |

## 데이터 생성 기준

`HomeViewModel.loadPageData()`는 월별 page cache를 기준으로 데이터를 쌓는다.

```text
monthStartDay
-> DateUtils.getCustomMonthPeriod()
-> ExpenseRepository / IncomeRepository
-> SmsExclusionRepository keyword filter
-> OwnedCardRepository excluded card filter
-> isIncludedInExpenseStats() 통계 포함 필터
-> HomePageData
```

필터가 적용되는 지점은 세 단계로 나뉜다.

| 필터 | 적용 위치 | 의미 |
|---|---|---|
| SMS 제외 키워드 | `originalSms` 포함 여부로 지출/수입 목록에서 제외 | 사용자가 SMS 설정에서 제외한 문구가 포함된 거래를 화면/집계에서 숨김 |
| 제외 카드 | `CardVisibilityFilter.filterVisibleExpenses()` | 보유 카드 관리에서 숨긴 카드를 홈 노출/집계에서 제외 |
| 통계 제외 | `ExpenseEntity.isIncludedInExpenseStats()` | 목록에는 남기되 월 지출, 오늘 지출, 차트, 카테고리 합계에서 제외 |

브리핑에는 위 필터가 적용된 기록을 전달한다. 고정 지출 예상에는 가시성 필터 뒤의 기록을 전달하고 계산기가 같은 거래처/카드 안의 비고정·통계 제외·이체까지 확인해 모호한 반복을 차단한다. 두 기능 모두 기록 기반이며 수집의 완전성이나 실제 계좌 잔액을 보장하지 않는다. 홈 자동 Gemini 인사이트 요청은 현재 실행 경로에서 제거했다.

## 보조 화면 연결

| 홈 액션 | 진입 대상 | 전달 값 | 주의 |
|---|---|---|---|
| 카테고리 상세 | `CategoryDetailActivity.open(context, category, year, month)` | 선택 category, 현재 year/month | 사용자 정의 하위 카테고리명도 저장된 문자열 기준으로 전달한다. |
| 지출 거래 카드 | `TransactionEditActivity.open(context, expenseId = id)` | `expense.id` | 수정 후 `DataRefreshEvent.TRANSACTION_ADDED/CATEGORY_UPDATED` 계열 refresh를 확인한다. |
| 수입 거래 카드 | `TransactionEditActivity.open(context, incomeId = id)` | `income.id` | 수입은 카드/통계 제외가 아니라 recurring/fixed 성격을 확인한다. |
| 오늘 거래 롱클릭 | `TransactionQuickActionViewModel.open(target)` | 지출/수입 유형과 실제 ID | 수정은 기존 편집 화면을 열고 삭제는 별도 확인 후 한 건만 처리한다. |
| 예상 거래 | `TransactionEditActivity.open(context, expenseId = sourceExpenseId)` | 실제 근거 ID | 미래 예상 행을 새 거래로 삽입하지 않는다. |
| 현재 월 가져오기 | Activity callback `onIncrementalSync` | 없음 | SMS 권한/알림 접근 권한은 Activity/ViewModel 쪽 정책을 따른다. |
| 과거 월 가져오기 | Activity callback `onFullSync` | 현재 page의 year/month | 광고/크레딧 정책과 sync coverage를 같이 본다. |

## 변경 시 체크

1. 홈 섹션을 추가하면 `HomePageData` 생성 위치와 `HomePageContent` 렌더링 위치가 모두 설명되는가?
2. 월 시작일이 1일이 아닐 때 이전/다음 월, 기간 라벨, 동기화 CTA가 같은 기준을 쓰는가?
3. 제외 카드/통계 제외/SMS 제외 키워드가 목록, 합계, 차트, 브리핑과 반복 예상의 각 계약에 맞게 적용되는가?
4. 카테고리 상세/거래 편집 진입 extra가 보조 화면 KB와 일치하는가?
