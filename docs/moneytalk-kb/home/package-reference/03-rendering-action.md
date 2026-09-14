---
type: package-reference
title: Home rendering/action
description: HomeScreen의 Composable 섹션, dialog, 클릭 action, 코치마크 연결을 설명한다.
tags: [moneytalk, home, compose, action]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt
timestamp: 2026-09-09T00:00:00+09:00
status: draft
---

# Home rendering/action

## 주요 Composable

| Composable | 역할 | 액션 |
|---|---|---|
| `HomeScreen` | Home 탭 entry, state 수집, dialog/coachmark overlay | 월 이동, 분류 dialog, category detail, transaction edit |
| `HomeTheme` (`ui/theme/HomeTheme.kt`) | 기존 홈 Typography와 9b4b4bf 색상을 홈 범위에 제공 | 앱 테마 선택/시스템 바 처리와 다른 화면의 배치는 바깥에서 유지 |
| `HomePageContent` (`HomePageContent.kt`) | CTA → 월 요약 → 누적 차트 → 최근 소비 비교 → 카테고리 → 오늘 전체 내역 → 현재 월 고정 예상 | 섹션별 callback 전달, 분석 접기와 오늘 3건 제한 없음 |
| `MonthlyOverviewSection` / `HomeExpenseAmount` (`component/MonthlyOverviewSection.kt`) | 중앙 월 이동과 초록·노랑 그라데이션 카드의 월 전체 지출/수입 배지 | 48dp 월 이동 버튼, 흰색 큰 금액·흰색 80% 수입 배지·흰색 50% 점 표시, 긴 금액의 폭 측정 |
| `HomeImportDataCta` / `HomeFullSyncCta` (`component/**`) | 원래 홈의 테두리·색상·22dp 반지름을 보존한 수집 CTA | 기존 권한/부분 수집/광고 조건과 동기화 중 입력 차단 유지 |
| `HomeTransactionCard` (`component/HomeTransactionCard.kt`) | 기존 `TransactionCardInfo`로 홈의 카테고리 칩·테두리·지출 색상 복원 | 클릭/롱클릭을 상위에 위임, 큰 글자·긴 금액의 세로 배치 유지 |
| `SpendingBriefingCard` (`briefing/SpendingBriefingCard.kt`) | ‘내 소비 한눈에’의 기록 기준/부분 수집 안내와 최근 소비 비교만 표시. 주간 비교가 없으면 숨김 | 증가 카테고리 내역 이동 |
| `BriefingWeeklySection` (`briefing/BriefingWeeklySection.kt`) | 고정 지출을 제외한 최근/이전 7일 비교 | 최근/이전 전체 소비 또는 증가 카테고리 근거 callback |
| `RecurringExpenseForecastCard` (`recurring/RecurringExpenseForecastCard.kt`) | 앞으로 30일 고정 지출 예상, 처음 3개와 전체 시트 | 최신 실제 근거 거래 열기 |
| `SpendingTrendSection` | 월 지출 바로 아래 카드 없는 누적 차트, `HomeSpendingTrendInfo` 비교 보정 유지 | 전월·3/6개월 평균·예산 토글, 탭/롱프레스 드래그 날짜 선택, `home_trend` 코치마크 |
| `CumulativeInspectionReadout` (`core/ui/component/chart/CumulativeInspectionReadout.kt`) | 선택 날짜·기간 경과일과 표시 곡선의 원본 누적 금액을 차트 아래 표시 | 닫기로 선택 해제, 지출 금액은 빨강·곡선 구분은 점 색상 |
| `CategoryExpenseSection` (`component/CategoryExpenseSection.kt`) | 기존 카테고리 지출 행·비율/예산 상태 | 처음 4개/전체 펼침, 행 선택 또는 상세 이동 |
| `AiInsightCard` (`component/AiInsightCard.kt`) | 남아 있는 기존 선언; 현재 홈에서는 미호출 | 다시 연결하면 비용 정책부터 검토 |
| `EmptyExpenseSection` (`component/EmptyExpenseSection.kt`) | 데이터 없음 상태 | SMS 권한/동기화 CTA 확인 |

## Dialog와 overlay

| UI | 파일 | 역할 |
|---|---|---|
| 미분류 분류 dialog | `HomeScreen.kt` | 미분류 건이 있을 때 전체 분류 실행 유도 |
| 거래 작업 중앙 모달 | `feature/transactionactions/ui/TransactionQuickActionDialog.kt` | 오늘 거래 롱클릭으로 수정/삭제 메뉴 표시. 수정은 기존 편집 화면, 삭제는 확인 후 처리 |
| Home coachmark | `ui/coachmark/HomeCoachMark.kt`, `CoachMarkOverlay.kt` | 월 지출과 기존 누적 차트 `home_trend` 안내. 차트가 아직 compose되지 않아 target이 없으면 기존 정책에 따라 생략 |

## Navigation/action

- 홈·내역·카테고리 상세의 월 Pager는 `rememberMonthPagerPageCount`에서 계산한 동일한 페이지 수를 읽는다. 이 helper는 실효 현재 월을 Compose state로 보관하고 복귀/시작일 변경/재구성 때 갱신한다. 측정 중 시계를 직접 다시 읽지 않아 월 경계에서 Pager의 캐시와 측정 범위가 달라지는 충돌을 방지한다.
- 전경에서 아무 재구성 없이 월 경계를 넘긴 경우에는 이전 허용 월을 유지하다 일반 UI 갱신 또는 화면 복귀 때 새 월을 연다. 그 사이 오늘 탭 재클릭이 새 월로 즉시 이동하는 것까지는 보장하지 않으며, 이번 수정에 주기적인 시계 확인이나 추가 refresh wrapper를 넣지 않았다.
- 카테고리 클릭은 `CategoryDetailActivity.open()`으로 상세 화면을 연다.
- 거래 클릭 또는 수정은 `TransactionEditActivity` 경로를 확인한다.
- 오늘 거래 롱클릭은 `TransactionTarget.Expense/Income(id)`를 화면별 `TransactionQuickActionViewModel`에 전달한다. 모달에는 거래명·닫기·수정/삭제만 표시하며 닫기는 데이터를 바꾸지 않는다. 수정은 유형과 ID로 기존 상세 편집을 연다.
- 기본 요약은 기존 `home_this_month_expense` 문구와 월 전체 기록 지출, 수입 배지를 그라데이션 카드에 표시한다. 누적 차트를 바로 아래에 두며 별도 분석 접힘은 없다. 권한 없음/부분 수집은 CTA·차트에서, 기록 기준은 주간 비교가 있는 브리핑에서도 안내한다. ‘내 소비 한눈에’의 예산·일수·하루 참고액은 제거한다.
- 오늘 내역은 현재 회계월의 카테고리 다음에 시간순으로 전체 지출/수입을 표시한다. 제목 옆 오늘 지출 합계와 지출 건수 헤더를 복원하며 클릭·롱클릭과 유형별 ID를 유지한다. 지출 건수는 통합 지출/수입 목록의 총 건수가 아니다.
- 홈 목록의 content padding은 위 16dp·아래 80dp다. 맨 아래까지 스크롤했을 때 마지막 거래 금액과 터치 영역이 ‘맨 위로’ FAB 위로 올라오도록 하단 공간을 확보한다.
- 예상 카드는 후보가 없으면 보이지 않는다. 예상을 실제 결제나 확정 일정으로 표현하지 않으며 클릭은 `sourceExpenseId`의 실제 거래를 연다. 거래별로 최근 관측 개월 수만 표시하고, 근거 거래를 여는 조작 안내는 카드 또는 전체 시트에서 한 번만 표시한다. 결제일·금액이 달라질 수 있다는 안내는 유지한다.
- 전체 월 동기화는 Activity-scoped `MainViewModel.showFullSyncAdDialog()`와 연결된다.
- `onRequestSmsPermission`은 Activity 권한 요청 callback이므로 화면 내부에서 직접 permission launcher를 만들지 않는다.

## 누적 차트 날짜 선택

- `HomePageContent`는 `DateUtils.getCustomMonthPeriod()`의 실제 회계 기간 시작일을 `LocalDate`로 변환해 활성 페이지의 `SpendingTrendSection`에만 전달한다. `inspectionPeriodStart` 기본값은 null이므로 CategoryDetail과 기존 Canvas 오버로드의 동작은 유지한다.
- 차트를 짧게 누르면 해당 날짜를 선택하고, 길게 누른 뒤 좌우로 움직이면 선택 날짜를 바꾼다. 롱프레스가 성립하기 전 이동은 소비하지 않아 홈 세로 스크롤과 가로 월 pager를 보존한다. 손을 떼어도 선택을 유지하며 `닫기`, 선택 월/회계 시작일 변경, 페이지 비활성화 시 초기화한다.
- 선택선과 곡선별 점은 실제 plot 좌표에 표시한다. 금액 패널은 차트 아래·범례 위에 두어 선택 중 차트 위치를 유지한다. 날짜는 `기간 시작일 + (dayIndex - 1)`이며 `M월 d일 · 기간 N일차 누적`으로 표시한다. 시작 0원인 index 0과 plot 밖은 날짜 내역으로 선택하지 않는다.
- `CumulativeChartInspection`은 주 곡선과 켜진 전월·3/6개월 평균·예산선의 원본 `Long`만 읽는다. 렌더링 애니메이션용 0원 배열이나 꺼진 선은 금액 근거로 사용하지 않고 실제 0원은 그대로 표시한다. 선택 범위는 선택 회계 기간과 표시 곡선의 실제 길이 안으로 제한한다.
- 당월 오늘 이후에는 당월 금액을 생략하고 오늘까지만 표시한다는 안내를 둔다. 같은 경과일의 전월·평균·예산 값이 있으면 조회할 수 있다. 각 비교선은 해당 index가 있을 때만 표시하며 짧은 전월의 말일 값을 이후 날짜로 연장하지 않는다. 이는 날짜별 조회 계약이며 아래의 기존 요약 비교 계산과 구분한다. 날짜 선택은 새 DB 조회·저장·AI 호출을 만들지 않는다.

## 분리 후 경계

- `HomeScreen`은 state 수집, pager와 ViewModel 연결, 알림/코치마크/분류 dialog를 담당한다. 선택 페이지의 이전 `year/month`에 대해 sync coverage와 부분 수집 여부를 확인해 `isPreviousMonthSynced`로 전달한다. 회계월 시작일과 연도 경계도 기존 coverage 정책을 따른다.
- `HomePageContent`는 600996a의 좌우 16dp 화면 여백·전체 오늘 목록을 복원하되 최신 요청에 따라 차트를 월 지출 바로 아래로 옮긴다. 하단 content padding은 실기기에서 마지막 거래를 가린 FAB를 피하도록 80dp로 보정한다. `showAnalysis`/`showAllToday`와 별도 상단 비교/분석 진입 컴포넌트는 제거한다. `TodayItem`은 오늘 지출/수입 렌더링용 모델로 같은 파일에 둔다.
- `HomeTheme`과 공통 테마 색상은 최신 요청에 따라 `9b4b4bf` 기준으로 복원한다. 홈의 다크 보조 글자는 원래 `#6B7684`를 쓰고 고정 예상 카드의 기본 글자는 `onSurface`, 금액은 공통 지출색을 사용한다. 기존 Typography·시스템 바 처리·그라데이션·섹션 순서는 유지한다.
- `HomeColors`와 홈 전용 거래 카드/CTA는 원래 홈의 색상·테두리·카테고리 칩을 제공한다. `TransactionCardInfo`와 클릭 계약은 재사용하며 History 등 다른 화면은 공용 `TransactionCardCompose`/CTA의 현재 배치에 원래 팔레트를 적용한다.
- `BriefingBudgetSection`은 호출 제거 후 파일도 삭제했다. `SpendingBriefingCalculator`의 예산 `null`·0원·초과·잔여 일수 계산, 예산 설정과 차트의 양수 예산 토글은 유지한다. 과거 월 계산의 예산 값도 현재 공통 예산이며 월별 스냅샷은 아니다. `SpendingBriefingCard`는 `weeklyComparison`이 있을 때만 기록 안내와 고정 지출을 제외한 최근/이전 7일을 보여준다.
- `HomeSpendingComparison.calculate()`는 누적 배열 0번을 시작 0원으로 보고 당월 차트 비교는 `todayDayIndex`까지, 과거 월은 각 월 마지막 누적값을 비교한다. 전월이 더 짧으면 마지막 지점으로 제한한다. Hero는 `monthlyExpense`의 월 전체 기록을 표시하므로 미래 날짜 기록이 있으면 당월 차트의 오늘까지 금액과 다를 수 있다. 예산/카테고리도 저장된 월 전체 기록 기준을 유지한다.
- `HomeSpendingTrendInfo.from()`은 선택 기간의 수집 완료·부분 아님·SMS 권한과 이전 월의 수집 완료 여부를 비교 조건으로 사용한다. 수집 미완료와 배열 부재는 평가 대신 안내하고, 수집 범위가 확인된 전월 0원은 실제 금액 차이로 비교한다. 전월 곡선은 이전 월 수집 범위가 확인됐을 때만 표시한다. 이는 앱의 수집 상태 확인이며 실제 금융 기록 전체의 완전성 보장이 아니다.
- 별도 두 곡선 상단 차트와 그 Y축 계산은 제거한다. 홈은 `SpendingTrendSection(showCard = false)`로 원래 카드 없는 배치·큰 누적 금액을 사용한다. wrapper와 공통 `CumulativeTrendSection`의 기본값은 `true`여서 CategoryDetail의 새 카드를 유지한다. 홈은 `scaleToVisibleLines = true`로 오늘까지 주 곡선과 켜진 비교선에 맞춰 Y축을 정하고 공통 기본 false는 전체 곡선 기준을 유지한다. 전월·평균·예산 토글과 영역 채움을 유지하며 공통 Vico의 시작 0원~`daysInMonth` X축 보정도 유지한다. 미사용 `showAreaFill` 옵션은 제거했다.
- 카테고리 UI는 600996a의 행/퍼센트/예산 상태 표시로 복원하고 `HomeCategoryExpenseInfo`와 mapper 계산은 유지한다. 예산이 있으면 지출/카테고리 예산, 없으면 지출/전체 표시 지출 합계가 비율의 분모다. 복원 UI에는 별도 ‘예산 %’/‘비중 %’ 접두어가 없다.
- 최신 색상 복원은 홈과 공통 화면의 팔레트를 대상으로 하며 DB 저장·수집 정책·AI 호출·광고 조건을 바꾸지 않는다. 공통 컴포넌트를 사용하는 다른 화면의 현재 기능·배치는 유지한다.
- 회귀 확인: 원래 월 이동/그라데이션/수입 배지와 섹션 순서, 오늘 전체 목록·합계·지출 건수·클릭/롱클릭, 브리핑/고정 예상/카테고리 진입, `home_trend` 코치마크, 긴 금액 한 줄 표시, 당월 차트 동일 경과일·과거 월 전체·수집 미완료·실제 0원·미래 날짜·연도 경계와 다른 화면의 배치 유지.

공통 표현 기준은 [금융 UI 디자인](../../project-context/05-finance-ui-design-system-20260908.md), 선택 이유는 [사용성 감사](../../project-context/06-finance-ux-plan-20260908.md)를 따른다.

- 누적 추이 범례는 `9월`·`8월`처럼 월만 표시한다. 월 이동과 이전 달 조회의 연도 경계 계산은 유지하며 범례에서만 연도를 생략한다.

- 소비 브리핑은 20dp 둥근 테두리 카드다. `최근 7일 소비`와 `이전 7일 소비`의 금액·날짜를 최근은 지출색·이전은 보조색으로 묶고 좁은 폭/큰 글자에서는 세로 배치한다. `고정 지출 제외 · 기록 기준`을 명시한다. 과거 월에도 같은 제목을 사용하고 선택 기간 마지막 날 기준이라는 설명과 실제 날짜를 함께 표시한다.
- 두 금액을 누르면 `WeeklyEvidenceActivity`의 해당 기간 탭을 연다. 증가 카테고리 버튼은 같은 화면에 category를 전달한다. 날짜별 거래·합계·기준 시각, 두 기간 전환, 기존 편집/롱클릭을 제공한다. 기존 월별 카테고리 상세 화면에는 주간 브리핑을 보내지 않는다.

- 다가올 고정 지출은 현재 회계월의 오늘 거래 다음, 홈 최하단에 표시한다. 실제 소비 확인을 먼저 두며 후보 없음 숨김·근거 거래 진입·80dp 하단 여백은 유지한다.

- 최신 색상은 `9b4b4bf`의 팔레트를 따른다. 공통 수입은 라이트 `#137FEC`·다크 `#3AC977`, 지출은 공통 `#EF4444`다. 홈 그라데이션의 큰 금액은 흰색, 수입은 흰색 80%, 점은 흰색 50%다. 통계 제외 금액은 보조색과 태그로 구분하고 고정 태그는 Coral을 사용한다. 편집은 지출 `FriendlyMoneyColors.Coral`, 수입 `FriendlyMoneyColors.Mint`, 이체 `FriendlyMoneyColors.Sky`를 사용한다. 기능·배치·차트 비교선은 유지한다. 이번 색상 복원의 실행 결과는 [후속 통합 검증 기록](../../project-context/08-home-ledger-ux-validation-20260909.md)의 색상 복원 절을 따른다.
