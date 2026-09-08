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
| `HomePageContent` (`HomePageContent.kt`) | 월별 page data 기반 전체 홈 content 조합 | 섹션별 callback 전달 |
| `MonthlyOverviewSection` (`component/MonthlyOverviewSection.kt`) | 월 지출/수입/예산 요약 | 월 이동 header와 함께 확인 |
| `SpendingBriefingCard` (`briefing/SpendingBriefingCard.kt`) | 기록 기준 예산·주간 비교 조합 | 증가 카테고리 내역 이동 |
| `BriefingBudgetSection` / `BriefingWeeklySection` (`briefing/**`) | 예산/일수/참고액과 주간 지출의 독립 렌더링 | 선택적 예산 callback, 카테고리 callback |
| `RecurringExpenseForecastCard` (`recurring/RecurringExpenseForecastCard.kt`) | 앞으로 30일 고정 지출 예상, 처음 3개와 전체 시트 | 최신 실제 근거 거래 열기 |
| `SpendingTrendSection` | 누적/추세 차트 | `HomeSpendingTrendInfo` 확인 |
| `CategoryExpenseSection` (`component/CategoryExpenseSection.kt`) | 카테고리별 지출 랭킹 | category chip 선택 또는 상세 이동 |
| `AiInsightCard` (`component/AiInsightCard.kt`) | 남아 있는 기존 선언; 현재 홈에서는 미호출 | 다시 연결하면 비용 정책부터 검토 |
| `EmptyExpenseSection` (`component/EmptyExpenseSection.kt`) | 데이터 없음 상태 | SMS 권한/동기화 CTA 확인 |

## Dialog와 overlay

| UI | 파일 | 역할 |
|---|---|---|
| 미분류 분류 dialog | `HomeScreen.kt` | 미분류 건이 있을 때 전체 분류 실행 유도 |
| 거래 작업 중앙 모달 | `feature/transactionactions/ui/TransactionQuickActionDialog.kt` | 오늘 거래 롱클릭으로 수정/삭제 메뉴 표시. 수정은 기존 편집 화면, 삭제는 확인 후 처리 |
| Home coachmark | `HomeCoachMark.kt`, `CoachMarkOverlay.kt` | 첫 사용 시 홈 주요 영역 안내 |

## Navigation/action

- 카테고리 클릭은 `CategoryDetailActivity.open()`으로 상세 화면을 연다.
- 거래 클릭 또는 수정은 `TransactionEditActivity` 경로를 확인한다.
- 오늘 거래 롱클릭은 `TransactionTarget.Expense/Income(id)`를 화면별 `TransactionQuickActionViewModel`에 전달한다. 모달에는 거래명·닫기·수정/삭제만 표시하며 닫기는 데이터를 바꾸지 않는다. 수정은 유형과 ID로 기존 상세 편집을 연다.
- 브리핑은 항상 기록 기준임을 표시하고, 권한 없음/부분 수집이면 추가 안내한다. 예산 callback이 없는 현재 홈에서는 미설정 시 `설정 > 월 예산 설정` 경로를 보여준다.
- 예상 카드는 후보가 없으면 보이지 않는다. 예상을 실제 결제나 확정 일정으로 표현하지 않으며 클릭은 `sourceExpenseId`의 실제 거래를 연다.
- 전체 월 동기화는 Activity-scoped `MainViewModel.showFullSyncAdDialog()`와 연결된다.
- `onRequestSmsPermission`은 Activity 권한 요청 callback이므로 화면 내부에서 직접 permission launcher를 만들지 않는다.

## 분리 후 경계

- `HomeScreen`은 state 수집, pager와 ViewModel 연결, 알림/코치마크/분류 dialog를 담당한다.
- `HomePageContent`는 기존 표시 조건과 callback을 유지하며 섹션을 조합한다. `TodayItem`은 오늘 지출/수입 렌더링용 모델로 같은 파일에 둔다.
- 카테고리 섹션은 `HomeCategoryExpenseInfo`의 비율과 예산 상태를 읽고 펼치기/선택만 처리한다. 표시 문자열·색상·아이콘은 Composable에서 처리한다.
- 회귀 확인: 월 이동/탭 재클릭, 예산 초과·미설정·과거 월, 부분 기록 안내, 증가 카테고리·예상 근거 진입, 오늘 지출/수입 클릭·롱클릭, 권한·과거 월 CTA, 분류 표시.
