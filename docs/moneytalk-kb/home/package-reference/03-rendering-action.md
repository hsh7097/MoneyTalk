---
type: package-reference
title: Home rendering/action
description: HomeScreen의 Composable 섹션, dialog, 클릭 action, 코치마크 연결을 설명한다.
tags: [moneytalk, home, compose, action]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# Home rendering/action

## 주요 Composable

| Composable | 역할 | 액션 |
|---|---|---|
| `HomeScreen` | Home 탭 entry, state 수집, dialog/coachmark overlay | 월 이동, 분류 dialog, category detail, transaction edit |
| `HomeTheme` (`ui/theme/HomeTheme.kt`) | 600996a 시점의 색상·Typography를 홈 범위에 제공 | 앱 테마 선택/시스템 바와 다른 화면의 새 테마는 바깥에서 유지 |
| `HomePageContent` (`HomePageContent.kt`) | CTA → 월 요약 → 누적 차트 → 최근 소비 비교 → 현재 월 예상 → 카테고리 → 오늘 전체 내역 | 섹션별 callback 전달, 분석 접기와 오늘 3건 제한 없음 |
| `MonthlyOverviewSection` / `HomeExpenseAmount` (`component/MonthlyOverviewSection.kt`) | 중앙 월 이동과 초록·노랑 그라데이션 카드의 월 전체 지출/수입 배지 | 48dp 월 이동 버튼, 흰 금액·수입 표시, 긴 금액의 폭 측정 |
| `HomeImportDataCta` / `HomeFullSyncCta` (`component/**`) | 원래 홈의 테두리·색상·22dp 반지름을 보존한 수집 CTA | 기존 권한/부분 수집/광고 조건과 동기화 중 입력 차단 유지 |
| `HomeTransactionCard` (`component/HomeTransactionCard.kt`) | 기존 `TransactionCardInfo`로 홈의 카테고리 칩·테두리·지출 색상 복원 | 클릭/롱클릭을 상위에 위임, 큰 글자·긴 금액의 세로 배치 유지 |
| `SpendingBriefingCard` (`briefing/SpendingBriefingCard.kt`) | ‘내 소비 한눈에’의 기록 기준/부분 수집 안내와 최근 소비 비교만 표시. 주간 비교가 없으면 숨김 | 증가 카테고리 내역 이동 |
| `BriefingWeeklySection` (`briefing/BriefingWeeklySection.kt`) | 최근/직전 7일 또는 과거 월 마지막 7일 비교 | 카테고리 callback |
| `RecurringExpenseForecastCard` (`recurring/RecurringExpenseForecastCard.kt`) | 앞으로 30일 고정 지출 예상, 처음 3개와 전체 시트 | 최신 실제 근거 거래 열기 |
| `SpendingTrendSection` | 월 지출 바로 아래 카드 없는 누적 차트, `HomeSpendingTrendInfo` 비교 보정 유지 | 전월·3/6개월 평균·예산 범례 토글, `home_trend` 코치마크 |
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

- 카테고리 클릭은 `CategoryDetailActivity.open()`으로 상세 화면을 연다.
- 거래 클릭 또는 수정은 `TransactionEditActivity` 경로를 확인한다.
- 오늘 거래 롱클릭은 `TransactionTarget.Expense/Income(id)`를 화면별 `TransactionQuickActionViewModel`에 전달한다. 모달에는 거래명·닫기·수정/삭제만 표시하며 닫기는 데이터를 바꾸지 않는다. 수정은 유형과 ID로 기존 상세 편집을 연다.
- 기본 요약은 기존 `home_this_month_expense` 문구와 월 전체 기록 지출, 수입 배지를 그라데이션 카드에 표시한다. 누적 차트를 바로 아래에 두며 별도 분석 접힘은 없다. 권한 없음/부분 수집은 CTA·차트에서, 기록 기준은 주간 비교가 있는 브리핑에서도 안내한다. ‘내 소비 한눈에’의 예산·일수·하루 참고액은 제거한다.
- 오늘 내역은 현재 회계월의 카테고리 다음에 시간순으로 전체 지출/수입을 표시한다. 제목 옆 오늘 지출 합계와 지출 건수 헤더를 복원하며 클릭·롱클릭과 유형별 ID를 유지한다. 지출 건수는 통합 지출/수입 목록의 총 건수가 아니다.
- 홈 목록의 content padding은 위 16dp·아래 80dp다. 맨 아래까지 스크롤했을 때 마지막 거래 금액과 터치 영역이 ‘맨 위로’ FAB 위로 올라오도록 하단 공간을 확보한다.
- 예상 카드는 후보가 없으면 보이지 않는다. 예상을 실제 결제나 확정 일정으로 표현하지 않으며 클릭은 `sourceExpenseId`의 실제 거래를 연다.
- 전체 월 동기화는 Activity-scoped `MainViewModel.showFullSyncAdDialog()`와 연결된다.
- `onRequestSmsPermission`은 Activity 권한 요청 callback이므로 화면 내부에서 직접 permission launcher를 만들지 않는다.

## 분리 후 경계

- `HomeScreen`은 state 수집, pager와 ViewModel 연결, 알림/코치마크/분류 dialog를 담당한다. 선택 페이지의 이전 `year/month`에 대해 sync coverage와 부분 수집 여부를 확인해 `isPreviousMonthSynced`로 전달한다. 회계월 시작일과 연도 경계도 기존 coverage 정책을 따른다.
- `HomePageContent`는 600996a의 좌우 16dp 화면 여백·전체 오늘 목록을 복원하되 최신 요청에 따라 차트를 월 지출 바로 아래로 옮긴다. 하단 content padding은 실기기에서 마지막 거래를 가린 FAB를 피하도록 80dp로 보정한다. `showAnalysis`/`showAllToday`와 별도 상단 비교/분석 진입 컴포넌트는 제거한다. `TodayItem`은 오늘 지출/수입 렌더링용 모델로 같은 파일에 둔다.
- `HomeTheme`은 앱 전체 색상·Typography를 되돌리지 않고 홈 안의 `MaterialTheme`과 MoneyTalk 확장 색상/숫자 스타일만 원래 표현으로 감싼다. 다크 모드 보조 글자는 `#B0BAC6`으로 표시하고, 고정 예상 카드의 기본 글자는 `onSurface`로 지정해 제목·거래명·금액이 흐린 보조색을 상속하지 않게 한다. 그라데이션·섹션 순서와 다른 화면의 색상은 유지한다.
- `HomeColors`와 홈 전용 거래 카드/CTA는 원래 홈의 색상·테두리·카테고리 칩을 제공한다. `TransactionCardInfo`와 클릭 계약은 재사용하며 History 등 다른 화면은 공용 `TransactionCardCompose`/CTA의 새 표현을 유지한다.
- `BriefingBudgetSection`은 호출 제거 후 파일도 삭제했다. `SpendingBriefingCalculator`의 예산 `null`·0원·초과·잔여 일수 계산, 예산 설정과 차트의 양수 예산 토글은 유지한다. 과거 월 계산의 예산 값도 현재 공통 예산이며 월별 스냅샷은 아니다. `SpendingBriefingCard`는 `weeklyComparison`이 있을 때만 기록 안내와 최근/직전 7일 또는 과거 월 마지막 7일을 보여준다.
- `HomeSpendingComparison.calculate()`는 누적 배열 0번을 시작 0원으로 보고 당월 차트 비교는 `todayDayIndex`까지, 과거 월은 각 월 마지막 누적값을 비교한다. 전월이 더 짧으면 마지막 지점으로 제한한다. Hero는 `monthlyExpense`의 월 전체 기록을 표시하므로 미래 날짜 기록이 있으면 당월 차트의 오늘까지 금액과 다를 수 있다. 예산/카테고리도 저장된 월 전체 기록 기준을 유지한다.
- `HomeSpendingTrendInfo.from()`은 선택 기간의 수집 완료·부분 아님·SMS 권한과 이전 월의 수집 완료 여부를 비교 조건으로 사용한다. 수집 미완료와 배열 부재는 평가 대신 안내하고, 수집 범위가 확인된 전월 0원은 실제 금액 차이로 비교한다. 전월 곡선은 이전 월 수집 범위가 확인됐을 때만 표시한다. 이는 앱의 수집 상태 확인이며 실제 금융 기록 전체의 완전성 보장이 아니다.
- 별도 두 곡선 상단 차트와 그 Y축 계산은 제거한다. 홈은 `SpendingTrendSection(showCard = false)`로 원래 카드 없는 배치·큰 누적 금액을 사용한다. wrapper와 공통 `CumulativeTrendSection`의 기본값은 `true`여서 CategoryDetail의 새 카드를 유지한다. 홈은 `scaleToVisibleLines = true`로 오늘까지 주 곡선과 켜진 비교선에 맞춰 Y축을 정하고 공통 기본 false는 전체 곡선 기준을 유지한다. 전월·평균·예산 토글과 영역 채움을 유지하며 공통 Vico의 시작 0원~`daysInMonth` X축 보정도 유지한다. 미사용 `showAreaFill` 옵션은 제거했다.
- 카테고리 UI는 600996a의 행/퍼센트/예산 상태 표시로 복원하고 `HomeCategoryExpenseInfo`와 mapper 계산은 유지한다. 예산이 있으면 지출/카테고리 예산, 없으면 지출/전체 표시 지출 합계가 비율의 분모다. 복원 UI에는 별도 ‘예산 %’/‘비중 %’ 접두어가 없다.
- 복원은 홈 UI 범위이며 DB 저장·수집 정책·AI 호출·광고 조건을 바꾸지 않는다. 공통 컴포넌트를 사용하는 다른 화면의 새 UI는 유지한다.
- 회귀 확인: 원래 월 이동/그라데이션/수입 배지와 섹션 순서, 오늘 전체 목록·합계·지출 건수·클릭/롱클릭, 브리핑/고정 예상/카테고리 진입, `home_trend` 코치마크, 긴 금액 한 줄 표시, 당월 차트 동일 경과일·과거 월 전체·수집 미완료·실제 0원·미래 날짜·연도 경계와 다른 화면의 테마 유지.

공통 표현 기준은 [금융 UI 디자인](../../project-context/05-finance-ui-design-system-20260908.md), 선택 이유는 [사용성 감사](../../project-context/06-finance-ux-plan-20260908.md)를 따른다.
