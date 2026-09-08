---
type: package-reference
title: Home data/ViewModel
description: HomeViewModel의 state, page cache, repository 조회, 로컬 브리핑과 고정 지출 예상, 분류 흐름을 설명한다.
tags: [moneytalk, home, viewmodel, data]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeViewModel.kt
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# Home data/ViewModel

## 상태

`HomeUiState`는 현재 year/month, page cache, 선택 카테고리, 로딩/error, 분류 dialog 상태를 가진다.
월별 데이터는 `HomePageData`로 묶으며 현재/인접 월을 cache한다. `spendingBriefing`과 `recurringForecast`는 순수 계산 결과이며 기존 `aiInsight` 필드는 제거했다.

## 데이터 흐름

```text
HomeViewModel.loadPageData(year, month)
-> SettingsDataStore monthStartDay/excluded cards
-> ExpenseRepository / IncomeRepository / BudgetDao
-> category sums, today expenses/incomes, monthly totals
-> SpendingBriefingCalculator / RecurringExpenseForecastCalculator
-> HomePageData cache update
-> HomePageContent render
```

## 주요 action

| 메서드 | 역할 | 주의 |
|---|---|---|
| `loadCurrentAndAdjacentPages()` | 현재/인접 월 page cache 로딩 | 월 이동 UX와 cache eviction 영향 |
| `observeDataRefreshEvents()` | 거래/카테고리/카드 변경 event 수집 | Home/History/CategoryDetail 갱신 경계 확인 |
| `refreshForDateChange()` | 다음 날짜에 화면이 resume되면 현재/인접 월 재계산 | 오늘 포함 잔여 일수와 예상일 갱신 |
| `selectCategory()` | 홈 카테고리 선택 state 변경 | category detail 이동과 혼동하지 않기 |
| `classifyUnclassifiedExpenses()` | 미분류 일괄 분류 | `category-classification` KB 필수 |
| `updateExpenseCategory()` | 가게명 기준 카테고리 변경 | Store rule/embedding 학습 영향 |

## 로컬 소비 브리핑

- 현재 회계월의 Flow 조회 시작을 현재 달력 월의 3개월 전 1일까지 넓힌다. `visibleHistory`에는 카드 숨김과 SMS 제외를 적용하고, 기존 월 합계/목록에는 회계월 날짜 필터를 다시 적용한다. 과거 회계월은 해당 월만 읽는다.
- `SpendingBriefingCalculator` 입력은 `BriefingExpense(amount: Long, category, dateTime, isFixed)`다. 호출자가 `isIncludedInExpenseStats()`까지 적용한다. 계산기는 카드/문자 정책을 다시 구현하거나 수입을 지출에서 빼지 않는다.
- 월 지출은 회계월 안에 저장된 미래일 수동 거래까지 기존 홈 합계와 같이 포함한다. 주간 비교는 오늘을 포함한 최근 7개 날짜와 직전 7개 날짜를 사용하되 현재 시각 이후 거래는 제외한다. 과거 월은 마지막 날을 기준으로 비교한다.
- 남은 예산은 전체 월 예산에서 월 지출을 뺀 값이다. 현재 회계월의 남은 날짜는 오늘을 포함한다. 하루 참고액은 남은 예산을 날짜 수로 나눈 원 단위 내림이며, 미설정·초과·과거/미래 월에서는 제공하지 않는다. 초과액은 별도 표시한다.
- 날짜는 `LocalDate`/`ZoneId`의 날짜 시작 경계로 계산하고 금액 합계는 `Long`이다. 현재 호출부의 회계월은 `DateUtils.getCustomMonthPeriod()`와 동일하다.
- 가장 증가한 카테고리는 최근 금액 순위가 아니라 두 주의 차액으로 고른다. 양수 증가만 표시하며 동률은 이름순이다. 버튼은 `WeeklyEvidenceActivity`로 동일한 두 7일 구간과 category, 계산 시각·시간대를 전달한다. 금액 카드는 category=null로 해당 기간 전체 소비를 열고, 증가 카테고리 버튼은 저장된 category가 정확히 같은 거래를 연다. 일반 월별 카테고리 행의 상세 이동은 유지한다.
- 이 계산 경로는 Gemini/광고/크레딧을 호출하지 않는다. `GeminiRepository.generateHomeInsight()`와 `AiInsightCard` 선언은 남아 있지만 현재 홈의 자동 실행/표시 경로에는 연결하지 않는다.

## 고정 지출 예상

- `RecurringExpenseForecastCalculator`는 가시성 필터만 적용한 과거 지출을 받는다. 통계 제외/비고정 행도 먼저 거래처+카드로 묶어 중복 월 청구나 최근 설정 변경을 숨기지 않는다.
- 계산기 자체는 최근 6개월 안에서 최대 3개 관측 월을 확인한다. 현재 Home 입력은 현재 달력 월의 3개월 전부터이며, 이는 최신 연속 2~3개월 조건을 판단할 범위를 포함한다.
- 최근 관측 월마다 정확히 1건, 연속 최소 2개월, 모두 고정·일반 지출·통계 포함·양수, 동일 금액/카테고리일 때만 후보를 만든다. 일자 차이는 최대 3일 또는 모두 월말이어야 한다. 최신 기록은 이번 달 또는 직전 달이어야 한다.
- 최신 기록의 다음 달 일자만 계산하고 짧은 달은 말일로 보정한다. 이미 지난 예상일을 다음 달로 계속 이월하지 않으며 오늘부터 30일 이내만 표시한다.
- 예상 결과는 DB에 저장하지 않고 월 예산에서 차감하지 않는다. `sourceExpenseId`는 최신 실제 거래 ID이며 누르면 그 거래 편집 화면을 연다.

## 갱신과 검증

- 거래 수정/삭제의 `TRANSACTION_ADDED`, 예산 저장의 `CATEGORY_UPDATED`, 카드 변경 이벤트로 현재/인접 페이지를 다시 읽는다. 날짜 변경은 Home `ON_RESUME`에서 확인하고 전체 월 캐시를 무효화한 뒤 현재/인접 월을 다시 읽는다. 인접 범위 밖에 남아 있던 월도 이전 날짜의 참고액/예상을 재사용하지 않는다.
- `SpendingBriefingCalculatorTest`: 회계월, 오늘 포함 일수, 윤일, 미설정/0/초과 예산, 월경계 주간 비교, 미래 거래 포함 범위, DST, 카테고리 증가·동률, Long 합계, 빈 기록.
- `RecurringExpenseForecastCalculatorTest`: 월말/윤년, 중복 월·누락 월·다른 카드, 변동 금액/일자, 고정 해제/제외/이체, 오래된 기록, 미래·미저장 거래, 실제 결제 도착과 Long 합계.
- 부분 coverage/권한 없음, 큰 글자, 카테고리·실제 거래 도착은 UI 검증 대상이다. 최종 실행 결과는 제품 검토 문서에 별도 기록한다.

## 함께 볼 데이터 문서

- [finance-data/README.md](../../finance-data/README.md): DAO/Repository/Entity
- [category-classification/README.md](../../category-classification/README.md): 자동/수동 카테고리 분류
- [data-refresh/README.md](../../data-refresh/README.md): `DataRefreshEvent` 갱신 흐름

## 화면 책임 점검 (2026-09-08)

- `HomeUiState.kt`에 화면/월 페이지 계약을 두고 ViewModel은 Repository 조회, 월 캐시, 취소 Job, 분류 진행 상태를 조율한다.
- 분류 알고리즘은 기존 `CategoryClassifierService`, 누적 계산은 `CumulativeChartDataBuilder`, 차트 변환은 `HomeSpendingTrendInfo`에 유지한다. MVI 전체 전환 없이 기존 MVVM 수명주기를 유지한다.
- 카테고리 순위/미분류 병합/예산 비율/90% 경고/초과 판정은 `HomeCategoryExpenseMapper`로 분리했다. 표시 모델은 DB 조회나 상태 변경을 하지 않는다.
- `HomeCategoryExpenseMapperTest`는 순위와 미분류 합산, 90%/100%/초과 예산, 미설정/0 예산, 빈 목록을 검증한다.

- 주간 비교와 증가 카테고리 계산에서만 `isFixed` 거래를 제외한다. 월 지출·예산·누적 차트는 전체 지출을 유지한다. 근거 화면도 카드/SMS/통계/고정 제외를 동일하게 적용하고 계산 시각 이후 거래는 제외한다. `WeeklyEvidenceViewModel`은 Room Flow와 DataRefreshEvent로 편집·삭제를 반영하며 합계와 날짜 그룹은 Default에서 계산한다.
