---
type: screen-entry-map
title: MoneyTalk 화면별 진입 경로
description: 앱 화면별 사용자 진입 경로, 코드 route, Activity entry를 한 곳에서 확인한다.
tags: [moneytalk, kb, screen-entry, navigation, qa]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# 화면별 진입 경로

> 상태: draft
> 기준: 2026-07-08 현재 `AndroidManifest.xml`, `navigation/**`, 주요 `*Activity.kt`, `*Screen.kt` 확인

이 문서는 화면 QA 또는 UI 진입 경로 변경 작업에서 먼저 보는 화면 route 색인이다.
사용자 조작 경로와 코드 진입점을 같이 기록한다.

## 검증 기준

| 항목 | 결과 |
|---|---|
| 빌드 | `./gradlew assembleDebug` 성공 |
| 코드 근거 | `AndroidManifest.xml`, `Screen.kt`, `NavGraph.kt`, `BottomNavItem.kt`, 각 `*Activity.open()` 호출부 확인 |
| 실기기 smoke | USB 디버깅 승인 후 앱 실행/대표 탭 이동으로 확인 대상 |

## 메인 진입과 하단 탭

| 화면 | 사용자 진입 경로 | 코드 진입점 | route/extra | 함께 볼 KB |
|---|---|---|---|---|
| Splash/Intro/Permission | 앱 아이콘 실행 | `feature/intro/ui/IntroActivity` | launcher Activity. `SPLASH -> ONBOARDING -> PERMISSION -> MainActivity`, 완료 사용자는 `SPLASH -> MainActivity` | [onboarding/README.md](onboarding/README.md), [app-shell/README.md](app-shell/README.md) |
| App Shell | 온보딩/권한 완료 후 자동 진입 | `MainActivity` -> `MoneyTalkApp` -> `NavGraph` | start destination `home` | [app-shell/README.md](app-shell/README.md) |
| 홈 | 하단 탭 `홈`, 또는 앱 최초 Main 진입 | `NavGraph` -> `HomeScreen` | `Screen.Home.route = "home"` | [home/README.md](home/README.md) |
| 가계부 | 하단 탭 `가계부` | `NavGraph` -> `HistoryScreen` | `Screen.History.route = "history?category={category}"`, 기본 진입은 `history` | [history/README.md](history/README.md) |
| 인사이트 | 하단 탭 `인사이트` | `NavGraph` -> `ChatScreen` | `Screen.Chat.route = "chat"` | [chat/README.md](chat/README.md) |
| 설정 | 하단 탭 `설정` | `NavGraph` -> `SettingsScreen` | `Screen.Settings.route = "settings"` | [settings/README.md](settings/README.md) |

## 보조 화면 Activity

| 화면 | 사용자 진입 경로 | 코드 진입점 | route/extra | 함께 볼 KB |
|---|---|---|---|---|
| 카테고리 상세 | `홈` -> 카테고리 지출 영역에서 카테고리 선택 | `CategoryDetailActivity.open(context, category, year, month, includeSubcategories)` | `extra_category`, `extra_year`, `extra_month`, `extra_include_subcategories` | [category-detail/README.md](category-detail/README.md) |
| 거래 추가 | `가계부` -> 추가 버튼 | `TransactionEditActivity.open(context)` | `extra_expense_id = -1`, `extra_income_id = -1` 기본값 | [transaction-edit/README.md](transaction-edit/README.md), [transaction-mutation/README.md](transaction-mutation/README.md) |
| 거래 수정 | `홈` 오늘 내역/`가계부` 목록/`카테고리 상세` 목록/`거래 상세 목록`에서 거래 선택 | `TransactionEditActivity.open(context, expenseId = id)` 또는 `TransactionEditActivity.open(context, incomeId = id)` | `extra_expense_id` 또는 `extra_income_id` | [transaction-edit/README.md](transaction-edit/README.md) |
| 날짜별 거래 목록 | `가계부` -> 달력 보기 -> 현재 기간의 미래가 아닌 날짜 선택 | `TransactionDetailListActivity.open(context, date)` | `extra_date = yyyy-MM-dd` | [transaction-list/README.md](transaction-list/README.md) |
| AI 크레딧 | `설정` -> `AI 크레딧` row | `AiCreditActivity.open(context)` | 별도 extra 없음. `uiState.isCreditFeatureEnabled`일 때 row 노출 | [ai-credit-screen/README.md](ai-credit-screen/README.md), [budget-credit-monetization/README.md](budget-credit-monetization/README.md) |
| 카테고리 설정 | `설정` -> `카테고리 설정` row | `CategorySettingsActivity.open(context)` | 별도 extra 없음 | [category-settings/README.md](category-settings/README.md) |
| 거래처 규칙 | `설정` -> `거래처 규칙` row | `StoreRuleSettingsActivity.open(context)` | 별도 extra 없음 | [store-rule-settings/README.md](store-rule-settings/README.md) |
| 문자 설정 | `설정` -> `문자 설정` row | `SmsSettingsActivity.open(context)` | 별도 extra 없음 | [sms-settings/README.md](sms-settings/README.md) |

## 문자 설정 내부 경로

`SmsSettingsActivity` 내부는 별도 Activity가 아니라 `SmsSettingsScreen` 내부 `NavHost`로 이동한다.

| 내부 화면 | 사용자 진입 경로 | 내부 route | 함께 볼 KB |
|---|---|---|---|
| 문자 설정 메인 | `설정` -> `문자 설정` | `main` | [sms-settings/README.md](sms-settings/README.md) |
| 수신거부 문구 관리 | 문자 설정 메인 -> `수신거부 문구 관리` | `blocked_phrases` | [sms-settings/README.md](sms-settings/README.md), [filtering/README.md](filtering/README.md) |
| 수신거부 전화번호 관리 | 문자 설정 메인 -> `수신거부 전화번호 관리` | `blocked_senders` | [sms-settings/README.md](sms-settings/README.md), [sms-parsing/README.md](sms-parsing/README.md) |
| 제외 카드 관리 | 문자 설정 메인 -> `제외 카드 관리` | `excluded_cards` | [sms-settings/README.md](sms-settings/README.md), [filtering/README.md](filtering/README.md) |

## 설정 탭 내부 다이얼로그/외부 설정 경로

아래 항목은 별도 화면 Activity가 아니라 `SettingsScreen`의 dialog, bottom sheet, system settings, file picker로 열린다.

| 항목 | 사용자 진입 경로 | 코드 경로 | 비고 |
|---|---|---|---|
| 월 시작일 | `설정` -> `월 시작일 설정` | `SettingsIntent.ShowMonthStartDayDialog` -> `MonthStartDayDialog` | 앱 기준 월 계산에 영향 |
| 월 예산/카테고리별 예산 | `설정` -> `월 예산 설정` row | `SettingsIntent.ShowBudgetBottomSheet` -> `BudgetBottomSheet` | [budget-credit-monetization/README.md](budget-credit-monetization/README.md) 같이 확인 |
| 테마 | `설정` -> 테마 row | `SettingsIntent.ShowThemeDialog` -> `ThemeModeDialog` | 별도 Activity 없음 |
| 알림 접근 | `설정` -> 알림 접근 row | `NotificationAccessHelper.openNotificationListenerSettings(context)` | Android 시스템 설정으로 이동 |
| 내보내기 | `설정` -> `내보내기` | `SettingsIntent.ShowExportDialog` -> `ExportDialog` | [backup-restore/README.md](backup-restore/README.md) 같이 확인 |
| Google Drive | `설정` -> `Google Drive` | Google sign-in 또는 `SettingsIntent.ShowGoogleDriveDialog` | 계정 상태에 따라 sign-in intent 또는 Drive dialog |
| 로컬 복원 | `설정` -> 로컬 복원 row | `SettingsIntent.OpenRestoreFilePicker` | Android file picker 사용 |
| 앱 정보/개인정보 | `설정` -> 앱 정보/개인정보 row | `AppInfoDialog`, `PrivacyPolicyDialog` | 별도 Activity 없음 |

## QA 순서 제안

1. `IntroActivity` launcher 실행 후 `MainActivity` 진입 여부 확인.
2. 하단 탭 `홈 -> 가계부 -> 인사이트 -> 설정` 순서로 이동.
3. `설정`에서 `AI 크레딧`, `카테고리 설정`, `거래처 규칙`, `문자 설정` 진입 확인.
4. `문자 설정` 내부에서 `수신거부 문구 관리`, `수신거부 전화번호 관리`, `제외 카드 관리` 이동 확인.
5. 데이터가 있는 기기에서는 `홈` 카테고리 선택, `가계부` 달력 날짜 선택, 거래 카드 선택으로 보조 화면 진입 확인.
