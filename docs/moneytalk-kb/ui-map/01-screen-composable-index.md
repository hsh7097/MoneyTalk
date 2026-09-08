---
type: reference
title: Screen Composable Index
description: MoneyTalk 화면별 Composable 계층과 관련 KB 라우팅을 요약한다.
tags: [moneytalk, ui, compose, screen]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# Screen Composable Index

> 기준: 2026-09-08 현재 Composable 선언과 기능별 파일 경로 확인. 화면 요구사항은 각 담당 KB를 함께 본다.

## 앱 진입

| 화면 | 주요 파일 | KB |
|---|---|---|
| Splash/Intro | `feature/splash/ui/SplashScreen.kt`, `feature/intro/ui/IntroActivity.kt`, `OnboardingScreen.kt`, `PermissionScreen.kt` | [onboarding](../onboarding/README.md) |
| App root | `MainActivity.kt` → `MoneyTalkApp.kt`, `SmsSyncDialogs.kt`, 월별 sync 보상형 광고/전역 다이얼로그, `navigation/NavGraph.kt`, `navigation/BottomNavItem.kt` | [app-shell](../app-shell/README.md), [budget-credit-monetization](../budget-credit-monetization/02-policy-and-plans.md) |

## 하단 탭 4개

| 탭 | 주요 Composable/파일 | 함께 볼 KB |
|---|---|---|
| Home | `HomeScreen`, `HomePageContent`, `MonthlyOverviewSection`, `SpendingBriefingCard`, `RecurringExpenseForecastCard`, `SpendingTrendSection`, `CategoryExpenseSection`, `TransactionCardCompose` | [home](../home/README.md), [home surface map](../home/package-reference/05-surface-map.md) |
| History | `HistoryScreen`, `HistoryHeader`, `FilterTabRow`, `FilterBottomSheet`, `TransactionListView`, `BillingCycleCalendarView` — 달력 날짜 선택은 callback으로 현재 필터와 정렬을 상세 화면에 전달 | [history](../history/README.md), [filtering](../filtering/README.md) |
| Chat | `ChatScreen`, `ChatRoomListView`, `ChatRoomView`, 기능별 메시지/가이드/진행/재시도/광고 dialog 파일 — 채팅방의 시스템 뒤로가기는 대화 목록으로 복귀 | [chat](../chat/README.md) |
| Settings | `SettingsScreen`, 기능별 `Settings*Section`, `SettingsDialogs`, `SettingsLoadingOverlay`, `BudgetBottomSheet`, `ExportDialog` — 비율 예산 저장은 현재 전체 예산으로 계산하며 범위 초과 입력은 오류 표시와 함께 저장 차단 | [settings](../settings/README.md), [settings menu map](../settings/package-reference/05-menu-map.md) |

## 보조 화면

| 화면 | 주요 파일 | 함께 볼 KB |
|---|---|---|
| Category Detail | `CategoryDetailActivity`, `CategoryDetailScreen`, `CategorySpendingTrendInfo` | [category-detail](../category-detail/README.md) |
| Transaction Edit | `TransactionEditActivity`, `TransactionEditScreen`, `TransactionEditDetailContent`, 기능별 `Transaction*Card`, `TransactionEditViewModel` | [transaction-edit](../transaction-edit/README.md), [transaction-mutation](../transaction-mutation/README.md) |
| Transaction Detail List | `TransactionDetailListActivity`, `TransactionDetailListScreen` — 달력에서 전달한 조건에 따라 지출·수입 통합 목록을 렌더링 | [transaction-list](../transaction-list/README.md) |
| SMS Settings | `SmsSettingsActivity`, `SmsSettingsScreen`, `SmsSettingsMainContent`, `BlockedPhraseManageScreen`, `BlockedSenderManageScreen`, `ExcludedCardManageScreen` | [sms-settings](../sms-settings/README.md) |
| AI Credit | `AiCreditActivity`, `AiCreditScreen`, AI credit repository state | [ai-credit-screen](../ai-credit-screen/README.md), [budget-credit-monetization](../budget-credit-monetization/README.md) |
| Category Settings | `CategorySettingsActivity`, `CategorySettingsScreen` | [category-settings](../category-settings/README.md), [category-classification](../category-classification/README.md) |
| Store Rule Settings | `StoreRuleSettingsActivity`, `StoreRuleSettingsScreen`, `StoreRuleEditorDialog.kt`의 `AddEditRuleDialog` | [store-rule-settings](../store-rule-settings/README.md) |

## 공통 컴포넌트

| 컴포넌트 | 역할 | 주의 |
|---|---|---|
| `TransactionCardCompose` / `TransactionCardInfo` | 지출/수입 통합 카드 | Home/History/CategoryDetail/TransactionList에서 함께 사용 |
| `TransactionQuickActionDialog` | 거래 롱클릭 중앙 모달의 수정/삭제 메뉴 | 거래명·닫기 X와 구분선 아래 아이콘 포함 세로 작업 행. 최대 320dp, 행 전체 터치, 삭제만 위험 색상. 수정은 기존 편집 화면, 삭제는 별도 확인. 4개 목록에서 유형과 ID 보존 |
| `TransactionGroupHeaderCompose` / `TransactionGroupHeaderInfo` | 날짜/그룹 헤더 | 목록 grouping 변경 시 확인 |
| `CumulativeTrendSection` / `VicoCumulativeChart` | 누적 추이 차트 | Home과 CategoryDetail의 기간/필터 차이를 같이 확인. 기간 헤더가 시작/종료일을 제공하므로 X축 양끝은 비우고 중간 날짜만 표시하며, Samsung Fold와 큰 글자 AVD에서 `...`가 없는지 검증 |
| `SegmentedTabRowCompose` / `SegmentedTabInfo` | 아이콘 지원 탭 | History/설정류 화면에서 재사용 |
| `SettingsItemCompose` / `SettingsSectionCompose` | 설정 row/section | Settings menu map과 함께 갱신 |
| `CategoryIcon` | 카테고리 icon/색상 | custom category fallback 확인 |
| `CategorySelectDialog` / `CategoryGridItem` | 거래 편집, History filter, Store Rule의 공통 카테고리 picker | `fontScale >= 1.5`에서는 3열과 최대 두 줄 라벨을 유지해 이름 말줄임을 방지 |

## Coachmark

코치마크는 홈, 내역, 설정, 채팅, 필터, 거래 편집, 거래처 규칙 화면에 연결되어 있다. 화면 구조나 주요 버튼 위치를 바꾸면 [coachmark](../coachmark/README.md)를 같이 확인한다.

## 기능별 선언 위치 (2026-09-08)

아래 경로는 `app/src/main/java/com/sanha/moneytalk/` 기준이다. 기존 책임 분리와 이후 추가한 제품 기능을 함께 기록한다. 공개 진입점, internal/private helper, 문자열을 반환하는 Composable도 실제 선언 기준으로 포함한다.

| 경로 | 선언된 Composable | 책임 |
|---|---|---|
| `MoneyTalkApp.kt` | `MoneyTalkApp`, `BackPressHandler` | 탭/전역 UI와 앱 종료 뒤로가기 |
| `SmsSyncDialogs.kt` | `SmsSyncProgressDialog`, `SmsEngineSummaryDialog`, `SyncStepIndicator` | 동기화 진행/결과 표시 |
| `feature/home/ui/HomeScreen.kt` | `HomeScreen` | 탭 수명주기, pager, dialog/coachmark |
| `feature/home/ui/HomePageContent.kt` | `HomePageContent` | 월 페이지 섹션·CTA·오늘 거래 조합 |
| `feature/home/briefing/SpendingBriefingCard.kt` | `SpendingBriefingCard` | 기록 기준 안내와 예산·주간 비교 조합 |
| `feature/home/briefing/BriefingBudgetSection.kt` | `BriefingBudgetSection` | 잔여/초과 예산, 오늘 포함 일수, 하루 참고액 |
| `feature/home/briefing/BriefingWeeklySection.kt` | `BriefingWeeklySection` | 최근/직전 7일, 증가 카테고리와 내역 진입 |
| `feature/home/recurring/RecurringExpenseForecastCard.kt` | `RecurringExpenseForecastCard`, `RecurringExpenseHeader`, `RecurringExpenseRow`, `recurringDate`, `recurringAmount` | 고정 지출 예상 3개·전체 시트, 근거 거래 버튼, 날짜/금액 표시 |
| `feature/home/ui/component/MonthlyOverviewSection.kt` | `MonthlyOverviewSection` | 월 이동과 월 지출/수입 요약 |
| `feature/home/ui/component/CategoryExpenseSection.kt` | `CategoryExpenseSection`, `CategoryRankingExpenseRow` | 카테고리 순위 펼치기/선택/행 |
| `feature/home/ui/component/AiInsightCard.kt` | `AiInsightCard`, `AiCoachMascot` | 기존 선언 유지, 현재 홈 자동 표시 경로에서는 미호출 |
| `feature/home/ui/component/EmptyExpenseSection.kt` | `EmptyExpenseSection` | 기존 빈 지출 표시 |
| `feature/home/ui/component/SpendingTrendSection.kt` | `SpendingTrendSection` | 공통 차트 연결 |
| `feature/history/ui/HistoryFilter.kt` | `FilterBottomSheet` | 임시 필터 상태와 시트 조합/적용 |
| `feature/history/ui/HistoryFilterControls.kt` | `FilterTransactionTypeSelector`, `FilterTypeTile`, `FilterGuideCard`, `FilterNoticeCard`, `FilterOptionPillRow`, `FilterOptionPill`, `FilterCategoryChipGroup`, `CategoryChoiceChip`, `FilterCategorySummaryRow` | 유형/옵션/카테고리 선택 UI |
| `feature/history/ui/HistoryFilterPickers.kt` | `CardFilterListBottomSheet`, `CategoryFilterListBottomSheet`, `CategoryFilterListRow` | 카드/카테고리 전체 목록 선택 |
| `feature/chat/ui/ChatScreen.kt` | `ChatScreen` | 상태 수집과 대화 목록/방 전환 |
| `feature/chat/ui/ChatRoomView.kt` | `ChatRoomView` | 메시지 목록/입력/전송 화면 |
| `feature/chat/ui/ChatGuideQuestions.kt` | `GuideQuestionsOverlay` | 질문 가이드 |
| `feature/chat/ui/ChatMessageBubble.kt` | `ChatBubble` | 메시지 카드 |
| `feature/chat/ui/ChatTypingIndicator.kt` | `TypingIndicator` | 응답 진행 표시 |
| `feature/chat/ui/ChatRetryButton.kt` | `RetryButton` | 오류 재시도 |
| `feature/chat/ui/ChatRewardAdDialog.kt` | `RewardAdDialog` | 크레딧 충전 광고 안내 |
| `feature/chat/ui/ChatServiceUnavailableDialog.kt` | `AiServiceUnavailableDialog` | AI 서비스 상태 안내 |
| `feature/settings/ui/SettingsScreen.kt` | `SettingsScreen` | 탭 진입, launcher, state/intent 연결 |
| `feature/settings/ui/SettingsDisplaySection.kt` | `SettingsDisplaySection` | 테마/화면 설정 |
| `feature/settings/ui/SettingsBudgetSection.kt` | `SettingsBudgetSection` | 월 시작일/예산 메뉴 |
| `feature/settings/ui/SettingsCreditSection.kt` | `SettingsCreditSection` | AI 크레딧 메뉴 |
| `feature/settings/ui/SettingsCategorySection.kt` | `SettingsCategorySection` | 카테고리/거래처/SMS 설정 메뉴 |
| `feature/settings/ui/SettingsDataSection.kt` | `SettingsDataSection` | 동기화/백업/복원/삭제 메뉴 |
| `feature/settings/ui/SettingsAppSection.kt` | `SettingsAppSection` | 앱 정보 메뉴 |
| `feature/settings/ui/SettingsDialogs.kt` | `SettingsDialogs` | activeDialog 분기와 이벤트 연결 |
| `feature/settings/ui/SettingsLoadingOverlay.kt` | `SettingsLoadingOverlay` | 장기 작업 진행 표시 |
| `feature/smssettings/ui/SmsSettingsScreen.kt` | `SmsSettingsScreen` | route/title/back과 하위 화면 조합 |
| `feature/smssettings/ui/SmsSettingsMainContent.kt` | `SmsSettingsMainContent` | SMS 설정 요약/동기화/관리 메뉴 |
| `feature/smssettings/ui/BlockedPhraseManageScreen.kt` | `BlockedPhraseManageScreen`, `BlockedPhraseItem` | 제외 문구 관리 |
| `feature/smssettings/ui/BlockedSenderManageScreen.kt` | `BlockedSenderManageScreen` | 차단 발신자 관리 |
| `feature/smssettings/ui/ExcludedCardManageScreen.kt` | `ExcludedCardManageScreen`, `ExcludedCardItem` | 제외 카드 관리 |
| `feature/storerulesettings/ui/StoreRuleSettingsScreen.kt` | `StoreRuleSettingsScreen`, `StoreRuleListItem` | 거래처 규칙 목록/화면 조합 |
| `feature/storerulesettings/ui/StoreRuleEditorDialog.kt` | `AddEditRuleDialog` | 규칙 추가/편집 입력 |
| `feature/transactionedit/ui/TransactionEditScreen.kt` | `TransactionEditScreen`, `TimePickerDialog` | 상태 수집, 로딩/없는 거래 안내, picker/저장 결과 |
| `feature/transactionedit/ui/TransactionEditDetailContent.kt` | `TransactionEditDetailContent` | 기능 카드 배치 |
| `feature/transactionedit/ui/TransactionEditChrome.kt` | `TransactionEditTopBar`, `TransactionEditBottomActions`, `TransactionEditSystemBars` | 상하단 액션과 시스템 바 |
| `feature/transactionedit/ui/TransactionHeroCard.kt` | `TransactionHeroCard`, `EditableHeroText`, `EditableHeroAmount`, `TransactionTypeSegmentedControl` | 거래 유형/금액/거래처 입력 |
| `feature/transactionedit/ui/TransactionBasicInfoCard.kt` | `TransactionBasicInfoCard`, `DetailActionRow`, `DetailStaticRow`, `DetailEditRow`, `DetailRowFrame`, `DetailLabel`, `CompactRuleCheckbox` | 기본 입력과 정보 행 |
| `feature/transactionedit/ui/TransactionAutomationCard.kt` | `TransactionAutomationCard`, `AutomationOptionRow`, `SameStoreHeaderCheckbox`, `fixedShortLabel`, `fixedDescription` | 고정/통계 제외/일괄 적용 옵션 |
| `feature/transactionedit/ui/TransactionSameStoreRuleCard.kt` | `TransactionSameStoreRuleCard`, `RuleKeywordInput`, `KeywordGuideCard` | 동일 거래처 적용 키워드 |
| `feature/transactionedit/ui/TransactionOriginalSmsCard.kt` | `TransactionOriginalSmsCard` | 원본 문자 표시 |
| `feature/transactionedit/ui/TransactionEditComponents.kt` | `TransactionSectionCard`, `TransactionDivider` | 거래 편집 카드 공통 요소 |
| `feature/transactionactions/ui/TransactionQuickActionDialog.kt` | `TransactionQuickActionDialog`, `TransactionQuickActionContent`, `QuickTransactionMenuItem` | 중앙 모달의 거래명·닫기와 아이콘 포함 세로 수정/삭제 행, 기존 편집 화면 진입과 삭제 확인 |

알림 진입의 거래 식별/삭제 후 처리 계약은 [notification-display](../notification-display/README.md)와 [transaction-edit](../transaction-edit/README.md)를 따른다. 상태/서비스/mapper 분리의 전체 감사는 [화면과 기능 책임 감사](../project-context/03-screen-function-architecture-audit.md)를 본다.
