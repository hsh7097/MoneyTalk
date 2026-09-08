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

Home은 하단 탭 첫 번째 화면이다. `HomeScreen`이 state와 Activity callback을 연결하고 `HomeTheme`으로 홈의 기존 색상·Typography를 적용한다. 사용자 요청에 따라 600996a의 그라데이션과 기존 UI를 복원한 뒤, 차트 위치와 예산 표시를 추가 조정했다. 수집 CTA → 중앙 월 이동/그라데이션 지출·수입 → 누적 차트 → 최근 소비 비교 → 현재 월 고정 예상 → 카테고리 → 오늘 전체 내역 순서이며 분석 접기는 없다. 다른 화면의 금융 UI 정리는 유지한다.

## 화면 블록

| 블록 | 표시 조건 | 데이터/상태 | 사용자 액션 | 함께 볼 파일 |
|---|---|---|---|---|
| SMS 가져오기 CTA | 현재 월이고 SMS 권한이 없거나 현재 월이 아직 동기화되지 않은 빈 상태 | `hasSmsPermission`, `isMonthSynced`, `pageData.monthlyExpense/monthlyIncome` | 현재 월 증분 동기화 요청 | `HomeScreen.kt`, `MainViewModel.kt`, `HomeImportDataCta` |
| 과거 월 전체 동기화 CTA | 현재 월이 아니고 해당 월 sync coverage가 없을 때 | `isPartiallyCovered`, `isAdEnabled`, `isSyncing` | 광고/크레딧 정책을 거쳐 월별 full sync 요청 | `HomeFullSyncCta`, `MainViewModel.showFullSyncAdDialog()` |
| 월간 현황 | 항상 표시 | 중앙 월 시작일 기준 기간, 기존 초록·노랑 그라데이션 카드의 월 전체 지출·수입 배지 | 이전/다음 월 이동 | `MonthlyOverviewSection`, `HomeExpenseAmount`, `DateUtils.getCustomMonthPeriod()` |
| 누적 지출 추이 | `HomeSpendingTrendInfo`가 생성될 때, 월 지출 바로 아래 | 당월 오늘까지/과거 월 전체 비교 금액, 수집 상태 안내, 선택 기간·수집 완료 전월·최근 3/6개월 평균·양수 예산 누적 데이터 | 전월/평균/예산 범례 토글, `home_trend` 코치마크 | `SpendingTrendSection`, `HomeSpendingTrendInfo`, `CumulativeChartDataBuilder` |
| 소비 브리핑 | `pageData.spendingBriefing?.weeklyComparison != null` | ‘내 소비 한눈에’의 기록 기준/부분 수집 안내와 테두리 카드의 고정 제외 최근/이전 7일 금액·날짜 비교 | 증가 카테고리 내역 이동 | `SpendingBriefingCard`, `BriefingWeeklySection`, `SpendingBriefingCalculator` |
| 고정 지출 예상 | 현재 회계월이며 계산 후보가 있을 때 | 오늘부터 30일, 반복 근거가 충분한 거래의 예상일/금액 | 처음 3개·전체 보기, 최신 실제 근거 거래 열기 | `RecurringExpenseForecastCard`, `RecurringExpenseForecastCalculator` |
| 카테고리 지출 | 브리핑/고정 예상 다음에 표시 | 통계 포함 지출의 카테고리 합계, 카테고리별 예산/전체 지출 대비 비율 | 처음 4개/전체 펼침, 카테고리 선택/해제, 상세 이동 | `CategoryExpenseSection`, `CategoryDetailActivity.open()` |
| 오늘 내역 | 현재 월일 때만 카테고리 다음에 표시 | 오늘 지출 합계·지출 건수, 전체 지출/수입 시간 역순 목록 | 클릭 상세 편집, 롱클릭 수정/삭제 | `HomePageContent`, `HomeTransactionCard`, `TransactionQuickActionDialog` |
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

Hero는 `monthlyExpense`와 `monthlyIncome`의 월 전체 기록을 표시한다. 누적 차트의 금액과 전월 차이는 유지한 `HomeSpendingComparison`이 계산한다. 당월은 시작 0원 다음부터 오늘의 경과일 지점까지, 과거 월은 각 월 전체 누적을 사용한다. 전월 일수가 더 짧으면 전월 마지막 값으로 제한한다. 미래 날짜 기록은 당월 차트 금액과 주 곡선에서 제외하지만 Hero·예산·카테고리는 월 전체 기록 기준이므로 값이 다를 수 있다.

차트의 현재 기간은 `isMonthSynced && !isPartiallyCovered && hasSmsPermission`, 전월은 선택 페이지의 이전 `year/month`에 대한 완료·부분 아님으로 비교 가능 여부를 판단한다. 현재 미완료 → 전월 미완료 → 누적 배열 부재 순으로 안내하며 해당 상태에서 많음/적음 평가는 하지 않는다. 수집 완료 전월의 실제 0원은 금액 차이로 비교하고 0으로 나누는 퍼센트는 만들지 않는다. 전월 미완료면 전월 선을 숨긴다. 브리핑은 별도로 기록 기준과 권한/부분 수집 안내를 표시한다. 수집 완료는 앱 coverage 정책상 상태이며 실제 계좌 기록의 완전성을 뜻하지 않는다.

`HomeExpenseAmount`는 기존 `displayLarge` 32sp와 숫자/통화 단위가 같은 크기인 흰색 중앙 정렬을 따른다. 1px 여유 폭에서 비례 축소하고 결과를 재측정해 넘치면 0.5sp씩 낮춰 긴 금액의 한 줄 표시를 유지한다. 그라데이션은 원래 초록 두 색과 노랑을 사용한다.

별도 보조 비교 차트와 분석 토글은 제거한다. `HomePageContent`가 `SpendingTrendSection(showCard = false, scaleToVisibleLines = true)`를 전달하고 공통 `CumulativeTrendSection`으로 이어져 홈에서만 원래 카드 없는 배치와 큰 누적 금액을 표시한다. wrapper와 공통 컴포넌트의 기본 `showCard = true`는 CategoryDetail 등 다른 화면에 유지한다. 홈 Y축은 오늘까지 주 곡선과 켜진 비교선의 최대값을 `visibleTrendAxisMax`로 1/2/5 단계 올림한다(모두 0원이면 1). 꺼진 예산선과 기존 100만원 최소축 때문에 적은 지출선이 바닥에 눌리지 않게 하며 다른 화면은 기본 false의 전체 곡선·`ceilToNiceValue` 기준을 유지한다. 범례는 전월/평균/예산 토글을 사용하고 영역 채움을 복원한다. 미사용 `showAreaFill` 옵션은 제거하고 공통 Vico의 0원 원점~월말 X축과 연·월 범례/비교 계산 보정은 유지한다.

브리핑은 차트 다음에 최근 소비 비교가 있을 때만 표시한다. 사용자 요청에 따라 예산·잔여 일수·하루 참고액 표시와 `BriefingBudgetSection` 파일을 제거했다. 예산 미설정·0원·초과 계산, 설정과 차트의 양수 예산 토글은 유지하며 과거 기간 계산에도 현재 공통 예산을 적용한다. 브리핑이 없더라도 CTA와 차트의 권한/수집 상태 안내는 별도로 남는다.

분석 접힘·오늘 3건 제한 상태는 제거한다. 오늘 목록은 전체 지출/수입을 표시하고 헤더는 오늘 지출 합계와 지출 건수를 표시한다. 이 지출 건수는 수입을 포함한 목록 총 건수와 다르다. 현재 회계월에만 오늘 블록을 표시하고 기존 상세 이동 callback을 유지한다.

`HomeTransactionCard`는 기존 `TransactionCardInfo`를 재사용하면서 원래 카테고리 칩·테두리·지출/수입 색상을 홈에만 제공한다. 큰 글자·좁은 폭·긴 금액의 세로 배치와 메타데이터 줄바꿈은 유지한다. `HomeImportDataCta`/`HomeFullSyncCta`도 원래 색상·테두리를 `HomeColors`에서 읽으며 다른 화면의 공용 카드/CTA는 바꾸지 않는다.

카테고리 행은 원래 배치와 퍼센트 칩/예산 상태로 복원한다. 예산이 있으면 해당 카테고리 예산, 없으면 전체 표시 지출이 비율의 분모이며 별도 ‘예산’/‘비중’ 접두어는 없다. 예산 초과/주의 상태와 총액·퍼센트는 기존 `HomeCategoryExpenseMapper`를 따른다.

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
5. Hero 월 전체 기록과 당월 차트 오늘까지 금액의 범위를 구분하는가? 전월 비교의 같은 경과일/수집 상태·실제 0원·짧은 전월·미래 날짜·연도 경계를 확인했는가?
6. 홈 전용 테마·카드 없는 차트 옵션이 다른 화면의 새 테마/공통 차트 기본값에 영향을 주지 않는가?

- 브리핑의 두 기간 금액은 전체 소비 비교, 증가 카테고리는 해당 category 비교를 `WeeklyEvidenceActivity`로 연다. 월별 상세가 아니라 동일한 최근/이전 7일, 기준 시각·시간대를 전달한다. 고정 지출 제외는 이 주간 비교에만 적용한다.
