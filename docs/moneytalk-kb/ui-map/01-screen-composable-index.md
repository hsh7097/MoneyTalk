---
type: reference
title: Screen Composable Index
description: MoneyTalk 화면별 Composable 계층과 관련 KB 라우팅을 요약한다.
tags: [moneytalk, ui, compose, screen]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-09T06:00:00+09:00
status: draft
---

# Screen Composable Index

> 기준: 흡수된 Composable map 원문을 KB용으로 재구성

## 앱 진입

| 화면 | 주요 파일 | KB |
|---|---|---|
| Splash/Intro | `feature/splash/ui/SplashScreen.kt`, `feature/intro/ui/IntroActivity.kt`, `OnboardingScreen.kt`, `PermissionScreen.kt` | [onboarding](../onboarding/README.md) |
| App root | `MainActivity.kt`, `navigation/NavGraph.kt`, `navigation/BottomNavItem.kt` | [app-shell](../app-shell/README.md) |

## 하단 탭 4개

| 탭 | 주요 Composable/파일 | 함께 볼 KB |
|---|---|---|
| Home | `HomeScreen`, `HomePageContent`, `MonthlyOverviewSection`, `SpendingTrendSection`, `CategoryExpenseSection`, `AiInsightCard`, `TransactionCardCompose` | [home](../home/README.md), [home surface map](../home/package-reference/05-surface-map.md) |
| History | `HistoryScreen`, `HistoryHeader`, `FilterTabRow`, `HistoryFilter`, `TransactionListView`, `BillingCycleCalendarView` | [history](../history/README.md), [filtering](../filtering/README.md) |
| Chat | `ChatScreen`, `ChatRoomListView`, `ChatRoomView`, `ChatComponents` | [chat](../chat/README.md) |
| Settings | `SettingsScreen`, `SettingsItemCompose`, `SettingsSectionCompose`, `BudgetBottomSheet`, settings dialogs | [settings](../settings/README.md), [settings menu map](../settings/package-reference/05-menu-map.md) |

## 보조 화면

| 화면 | 주요 파일 | 함께 볼 KB |
|---|---|---|
| Category Detail | `CategoryDetailActivity`, `CategoryDetailScreen`, `CategorySpendingTrendInfo` | [category-detail](../category-detail/README.md) |
| Transaction Edit | `TransactionEditActivity`, `TransactionEditScreen`, `TransactionEditViewModel` | [transaction-edit](../transaction-edit/README.md), [transaction-mutation](../transaction-mutation/README.md) |
| Transaction Detail List | `TransactionDetailListActivity`, `TransactionDetailListScreen` | [transaction-list](../transaction-list/README.md) |
| SMS Settings | `SmsSettingsActivity`, `SmsSettingsScreen`, `SmsSettingsViewModel` | [sms-settings](../sms-settings/README.md) |
| AI Credit | `AiCreditActivity`, `AiCreditScreen`, AI credit repository state | [ai-credit-screen](../ai-credit-screen/README.md), [budget-credit-monetization](../budget-credit-monetization/README.md) |
| Category Settings | `CategorySettingsActivity`, `CategorySettingsScreen` | [category-settings](../category-settings/README.md), [category-classification](../category-classification/README.md) |
| Store Rule Settings | `StoreRuleSettingsActivity`, `StoreRuleSettingsScreen` | [store-rule-settings](../store-rule-settings/README.md) |

## 공통 컴포넌트

| 컴포넌트 | 역할 | 주의 |
|---|---|---|
| `TransactionCardCompose` / `TransactionCardInfo` | 지출/수입 통합 카드 | Home/History/CategoryDetail/TransactionList에서 함께 사용 |
| `TransactionGroupHeaderCompose` / `TransactionGroupHeaderInfo` | 날짜/그룹 헤더 | 목록 grouping 변경 시 확인 |
| `CumulativeTrendSection` | 누적 추이 차트 | Home과 CategoryDetail의 기간/필터 차이를 같이 확인 |
| `SegmentedTabRowCompose` / `SegmentedTabInfo` | 아이콘 지원 탭 | History/설정류 화면에서 재사용 |
| `SettingsItemCompose` / `SettingsSectionCompose` | 설정 row/section | Settings menu map과 함께 갱신 |
| `CategoryIcon` | 카테고리 icon/색상 | custom category fallback 확인 |

## Coachmark

코치마크는 홈, 내역, 설정, 채팅, 필터, 거래 편집, 거래처 규칙 화면에 연결되어 있다. 화면 구조나 주요 버튼 위치를 바꾸면 [coachmark](../coachmark/README.md)를 같이 확인한다.
