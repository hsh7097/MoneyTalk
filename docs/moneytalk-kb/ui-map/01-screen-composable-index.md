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
| Home | `HomeScreen` → `HomeTheme`/`HomePageContent`: `HomeImportDataCta`/`HomeFullSyncCta`, `MonthlyOverviewSection` → `HomeExpenseAmount`, `SpendingTrendSection`, `SpendingBriefingCard` → `BriefingWeeklySection`, `RecurringExpenseForecastCard`, `CategoryExpenseSection`, 오늘 전체 `HomeTransactionCard` | [home](../home/README.md), [home surface map](../home/package-reference/05-surface-map.md) |
| History | `HistoryScreen`, `PeriodSummaryCard`, `SearchBar`, `FilterTabRow`, `FilterBottomSheet`, `TransactionListView`, `BillingCycleCalendarView` — 필터 진입을 항상 유지하고 초기화 X를 분리. 달력 날짜 선택은 현재 필터와 정렬을 상세 화면에 전달 | [history](../history/README.md), [filtering](../filtering/README.md) |
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
| Weekly Evidence | `WeeklyEvidenceActivity`, `WeeklyEvidenceScreen`, `WeeklyEvidenceContent`: 전체/category별 두 기간 탭·합계·날짜 그룹·편집/롱클릭 | [home](../home/package-reference/03-rendering-action.md) |
| Category Review | `CategoryReviewActivity`, `CategoryReviewScreen` → `CategoryReviewContent`, `CategoryReviewList`, `CategoryReviewMessage`. 설정의 직접 확인 메뉴에서 기존 편집 화면으로 연결 | [settings](../settings/package-reference/03-rendering-action.md) |
| Category Settings | `CategorySettingsActivity`, `CategorySettingsScreen` | [category-settings](../category-settings/README.md), [category-classification](../category-classification/README.md) |
| Store Rule Settings | `StoreRuleSettingsActivity`, `StoreRuleSettingsScreen`, `StoreRuleEditorDialog.kt`의 `AddEditRuleDialog` | [store-rule-settings](../store-rule-settings/README.md) |

## 공통 컴포넌트

| 컴포넌트 | 역할 | 주의 |
|---|---|---|
| `TransactionCardCompose` / `TransactionCardInfo` | 거래명·금액 우선의 지출/수입 통합 카드 | History/CategoryDetail/TransactionList 공용. Home은 같은 Info를 받는 `HomeTransactionCard`로 원래 표현을 사용. 공용은 일반 지출 중립색·별도 상태 FlowRow. 양쪽 모두 글자 배율 1.3 초과·폭 320dp 미만·금액 12자 초과이면 금액을 다음 줄로 배치 |
| `TransactionQuickActionDialog` | 거래 롱클릭 중앙 모달의 수정/삭제 메뉴 | 거래명·닫기 X와 구분선 아래 아이콘 포함 세로 작업 행. 최대 320dp, 행 전체 터치, 삭제만 위험 색상. 수정은 기존 편집 화면, 삭제는 별도 확인. 4개 목록에서 유형과 ID 보존 |
| `TransactionGroupHeaderCompose` / `TransactionGroupHeaderInfo` | 날짜/그룹 헤더 | 목록 grouping 변경 시 확인 |
| `CumulativeTrendSection` / `VicoCumulativeChart` | 누적 추이 차트 | Home과 CategoryDetail의 기간/필터 차이를 같이 확인. 양축 라벨은 `onSurfaceVariant`. 0원 원점부터 `daysInMonth`까지 X축 경과일을 맞춘다. Home은 `showCard = false`로 카드 없는 원래 배치, 다른 화면은 기본 true의 새 카드 유지 |
| `SegmentedTabRowCompose` / `SegmentedTabInfo` | 아이콘 지원 탭, 최소 48dp 높이와 선택 상태 접근성 | History 및 CategorySettings의 `CategoryTypeTabRow`에서 재사용. 선택 색은 Info에서 전달 |
| `SettingsItemCompose` / `SettingsSectionCompose` | 중립 표면의 설정 row/section | 최소 60dp 행, 부제가 있을 때만 추가 줄. Settings menu map과 함께 갱신 |
| `CategoryIcon` | 카테고리 icon/색상 | custom category fallback 확인 |
| `CategorySelectDialog` / `CategoryGridItem` | 거래 편집, History filter, Store Rule의 공통 카테고리 picker | `fontScale >= 1.5`에서는 3열과 최대 두 줄 라벨을 유지해 이름 말줄임을 방지 |

## Coachmark

코치마크는 홈, 내역, 설정, 채팅, 필터, 거래 편집, 거래처 규칙 화면에 연결되어 있다. 홈의 두 번째 단계는 원래 누적 차트 target `home_trend`를 사용한다. 접힌 분석 버튼 target은 제거했다. 화면 구조나 주요 버튼 위치를 바꾸면 [coachmark](../coachmark/README.md)를 같이 확인한다.

## 금융 UI 표시 체계

- `HomeTheme`의 다크 보조 글자는 `#B0BAC6`을 사용한다. `RecurringExpenseForecastCard`는 기본 content color를 `onSurface`로 지정해 제목·거래명·금액을 밝게 유지하고, 날짜·근거와 차트 축은 보조색으로 구분한다.
- 고정 예상 행은 관측 개월 수만 표시한다. 근거 거래 열기 안내는 카드 또는 전체 시트에 한 번만 표시하며 실제 결제일·금액의 불확실성 안내를 유지한다.

색상·간격·큰 글자 규칙은 [금융 UI 디자인 기준](../project-context/05-finance-ui-design-system-20260908.md), 화면 위계와 독립 리뷰는 [금융 UI 사용성 감사](../project-context/06-finance-ux-plan-20260908.md)를 본다. `MoneyTalkTheme`의 중립 배경/표면과 의미 색상을 공통으로 사용하며, 실제 검사 통과 여부는 [통합 검증 기록](../project-context/07-finance-ui-validation-20260908.md)과 로그로 확인한다.

- Home은 `HomeTheme`/`HomeColors`로 기존 UI를 복원한다. CTA → 중앙 월 이동/초록·노랑 그라데이션의 월 전체 지출·수입 → 카드 없는 누적 차트 → 예산을 뺀 최근 소비 비교 → 고정 예상 → 카테고리 → 오늘 전체 내역 순서다. 주간 비교가 없으면 브리핑도 숨긴다. 차트의 당월 오늘까지/과거 전체·미래 날짜 제외·수집 상태·실제 0원 보정과 예산/평균 토글은 유지한다. Hero는 월 전체 기록이므로 당월 차트와 범위가 다를 수 있다. 카테고리의 예산/전체 표시 지출 분모 계산은 같고 원래 퍼센트 표시를 사용한다.
- 내역은 기간 요약, 목록/달력·검색·추가, 필터 영역을 나눈다. 달력은 큰 글자에서 셀 높이를 늘리고 월 전체를 세로로 스크롤한다.
- App root의 `NavigationBarItem`은 24dp 기본 아이콘과 `label` 슬롯을 사용한다. 아이콘 설명은 null로 두어 탭 이름을 중복 낭독하지 않는다.
- 채팅은 새 질문 버튼, 기존 대화 목록, 카테고리별 질문 행을 구분한다. 설정은 섹션/코치마크 순서를 유지하며 공통 행을 사용한다. 관리·편집 화면도 같은 표면과 의미 색상을 사용한다.

## 기능별 선언 위치 (2026-09-08)

아래 경로는 `app/src/main/java/com/sanha/moneytalk/` 기준이다. 기존 책임 분리와 이후 추가한 제품 기능을 함께 기록한다. 공개 진입점, internal/private helper, 문자열을 반환하는 Composable도 실제 선언 기준으로 포함한다.

| 경로 | 선언된 Composable | 책임 |
|---|---|---|
| `MoneyTalkApp.kt` | `MoneyTalkApp`, `BackPressHandler` | 표준 아이콘/label 탭, IME 시 하단바 숨김, 전역 UI와 앱 종료 뒤로가기 |
| `core/theme/Theme.kt` | `MoneyTalkTheme` | 라이트/다크 색상·Typography·확장 의미 색상 공급 |
| `core/ui/component/transaction/card/TransactionCardCompose.kt` | `TransactionCardCompose` | 큰 글자·좁은 폭·큰 금액 대응 거래 행과 클릭/롱클릭 |
| `core/ui/component/transaction/header/TransactionGroupHeaderCompose.kt` | `TransactionGroupHeaderCompose` | 날짜/그룹별 수입·지출 표시 |
| `core/ui/component/tab/SegmentedTabRowCompose.kt` | `SegmentedTabRowCompose`, `SegmentedTab` | 선택 상태·아이콘·최소 터치 크기의 공통 탭 |
| `core/ui/component/settings/SettingsItemCompose.kt`, `SettingsSectionCompose.kt` | `SettingsItemCompose`, `SettingsSectionCompose` | 중립 설정 그룹과 내용에 따라 늘어나는 행 |
| `core/ui/component/cta/ImportDataCtaSection.kt`, `FullSyncCtaSection.kt` | `ImportDataCtaSection`, `FullSyncCtaSection` | 권한/부분 수집/광고 조건과 진행 차단을 보존하는 동기화 CTA |
| `core/ui/component/chart/CumulativeTrendSection.kt` | `CumulativeTrendSection` (두 오버로드), `LegendItem` | Info 오버로드의 `showCard` 기본 true. Home만 false로 원래 카드 없는 배치·큰 누적 금액 사용. `scaleToVisibleLines` 기본 false는 전체 곡선, Home true는 오늘까지 주 곡선·켜진 비교선 기준 Y축. 48dp 범례와 선택 상태 |
| `core/ui/component/chart/VicoCumulativeChart.kt` | `VicoCumulativeChart` | 당월 주 곡선은 오늘 지점까지, X축은 시작 0원부터 월말까지. 현재/비교 실선 3dp/2dp, 테마 축 색상과 원래 영역 채움 |
| `SmsSyncDialogs.kt` | `SmsSyncProgressDialog`, `SmsEngineSummaryDialog`, `SyncStepIndicator` | 동기화 진행/결과 표시 |
| `feature/categoryreview/ui/CategoryReviewScreen.kt` | `CategoryReviewScreen`, `CategoryReviewContent`, `CategoryReviewList`, `CategoryReviewMessage` | 전체 기간 미분류 목록, 날짜 그룹·건수, 로딩/오류/빈 상태, 기존 거래 편집 및 복귀 갱신 |
| `feature/home/ui/HomeScreen.kt` | `HomeScreen` | 탭 수명주기, pager, dialog/coachmark. 이전 페이지 연·월의 완료/부분 수집 상태 전달 |
| `feature/home/ui/HomePageContent.kt` | `HomePageContent` | 기존 홈 조합을 복원하되 월 지출 바로 아래 누적 차트 배치. 주간 비교·예상·카테고리·오늘 전체 목록과 CTA 연결. 목록 아래 80dp 여백으로 마지막 거래와 맨 위로 FAB 겹침 방지 |
| `feature/home/ui/theme/HomeTheme.kt` | `HomeTheme` | 홈 범위의 기존 MaterialTheme·확장 색상·숫자 Typography 공급, 앱 전체 테마는 유지 |
| `feature/home/ui/theme/HomeColors.kt` | `HomeColors`의 `isDark`, `elevatedCardBackground`, `textPrimary`, `textSecondary`, `border`, `mintTint`, `mintTintContent` (`@Composable` getter) | 홈 거래 카드·CTA·카테고리에 600996a 색상 제공 |
| `feature/home/ui/component/HomeTransactionCard.kt` | `HomeTransactionCard` | 원래 홈 카드의 22dp 반지름·테두리·카테고리 칩·지출 색상, 큰 글자 금액 배치와 기존 Info/클릭 계약 |
| `feature/home/ui/component/HomeImportDataCta.kt` | `HomeImportDataCta` | 원래 홈 가져오기 CTA와 동기화 중 입력 차단 |
| `feature/home/ui/component/HomeFullSyncCta.kt` | `HomeFullSyncCta` | 원래 홈 기간 가져오기 CTA, 부분 수집·광고 유무 안내 |
| `feature/home/briefing/SpendingBriefingCard.kt` | `SpendingBriefingCard` | 주간 비교가 있을 때만 ‘내 소비 한눈에’와 기록 기준·부분 수집 안내 표시. 예산 UI 제거 |
| `feature/home/briefing/BriefingWeeklySection.kt` | `BriefingWeeklySection`, `BriefingPeriodAmount` | 테두리 브리핑 안의 두 7일 파랑 금액/날짜, 고정 제외, 전체/category 근거 진입 |
| `feature/weeklyevidence/ui/WeeklyEvidenceScreen.kt` | `WeeklyEvidenceScreen`, `WeeklyEvidenceContent` | 동일 날짜·시간대·기준 시각의 두 기간 전환, 합계/목록 및 기존 편집/롱클릭 |
| `feature/home/recurring/RecurringExpenseForecastCard.kt` | `RecurringExpenseForecastCard`, `RecurringExpenseHeader`, `RecurringExpenseRow`, `recurringDate`, `recurringAmount` | 고정 지출 예상 3개·전체 시트, 근거 거래 버튼, 날짜/금액 표시 |
| `feature/home/ui/component/MonthlyOverviewSection.kt` | `MonthlyOverviewSection`, `HomeExpenseAmount` | 중앙 월 이동·그라데이션 월 전체 지출/수입. 32sp 흰 금액·동일 크기 통화 단위를 1px 여유 폭으로 측정·재측정해 한 줄 표시 |
| `feature/home/ui/model/HomeSpendingTrendInfo.kt` | `HomeSpendingTrendInfo.from`, `buildComparisonText` (`@Composable` factory/문구 함수) | `HomeSpendingComparison`의 동일 경과일/과거 전체/수집 상태를 기존 누적 차트 정보로 변환. 실제 0원도 금액 차이로 비교 |
| `feature/home/ui/component/CategoryExpenseSection.kt` | `CategoryExpenseSection`, `CategoryRankingExpenseRow` | 원래 카테고리 순위 행·퍼센트·예산 상태와 펼치기/선택 유지 |
| `feature/home/ui/component/AiInsightCard.kt` | `AiInsightCard`, `AiCoachMascot` | 기존 선언 유지, 현재 홈 자동 표시 경로에서는 미호출 |
| `feature/home/ui/component/EmptyExpenseSection.kt` | `EmptyExpenseSection` | 기존 빈 지출 표시 |
| `feature/home/ui/component/SpendingTrendSection.kt` | `SpendingTrendSection` | 공통 차트 연결. 기본 `showCard = true`/`scaleToVisibleLines = false`, HomePageContent만 각각 false/true 지정 |
| `feature/history/ui/HistoryScreen.kt` | `HistoryScreen`, `TransactionListView` | 월 pager, 현재 필터/검색/보기, 날짜별 거래 행과 롱클릭 메뉴 연결 |
| `feature/history/ui/HistoryHeader.kt` | `SearchBar`, `PeriodSummaryCard`, `FilterTabRow`, `FilterActionButton`, `FilterStatusChip` | 월 요약과 보기 도구, 활성 상태에서도 필터 재편집 및 별도 X 초기화 |
| `feature/history/ui/HistoryCalendar.kt` | `BillingCycleCalendarView`, `CalendarDayCell`, `CalendarAmountText` | 큰 글자에 맞춘 셀 높이·월 세로 스크롤. 7열 금액의 실제 폭을 측정해 부호·만/억 단위와 정확한 원 단위 접근성 설명을 보존 |
| `feature/history/ui/HistoryFilter.kt` | `FilterBottomSheet` | 임시 필터 상태와 시트 조합/적용 |
| `feature/history/ui/HistoryFilterControls.kt` | `FilterTransactionTypeSelector`, `FilterTypeTile`, `FilterGuideCard`, `FilterNoticeCard`, `FilterOptionPillRow`, `FilterOptionPill`, `FilterCategoryChipGroup`, `CategoryChoiceChip`, `FilterCategorySummaryRow` | 유형/옵션/카테고리 선택 UI |
| `feature/history/ui/HistoryFilterPickers.kt` | `CardFilterListBottomSheet`, `CategoryFilterListBottomSheet`, `CategoryFilterListRow` | 카드/카테고리 전체 목록 선택 |
| `feature/chat/ui/ChatScreen.kt` | `ChatScreen` | 상태 수집과 대화 목록/방 전환 |
| `feature/chat/ui/ChatRoomListView.kt` | `ChatRoomListView`, `SessionItem` | 새 질문 CTA, 기존 대화와 삭제 진입 |
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
| `feature/categorysettings/ui/CategorySettingsScreen.kt` | `CategorySettingsScreen`, `CategoryTypeTabRow`, `CategoryListItem` | 공통 세그먼트 기반 유형 선택, 카테고리 목록/삭제 |
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
| `feature/transactionedit/ui/TransactionHeroCard.kt` | `TransactionHeroCard`, `EditableHeroText`, `EditableHeroAmount`, `TransactionTypeSegmentedControl`, `TransactionType.accentColor` | 거래명 행 아래 전체 폭의 금액 입력, 사용자 글자 배율과 실제 표시 폭으로 크기 조정. 유형 Composable 확장 함수가 테마의 의미 색상 반환 |
| `feature/transactionedit/ui/TransactionBasicInfoCard.kt` | `TransactionBasicInfoCard`, `DetailActionRow`, `DetailStaticRow`, `DetailEditRow`, `DetailRowFrame`, `DetailLabel`, `CompactRuleCheckbox` | 기본 입력과 정보 행 |
| `feature/transactionedit/ui/TransactionAutomationCard.kt` | `TransactionAutomationCard`, `AutomationOptionRow`, `SameStoreHeaderCheckbox`, `fixedShortLabel`, `fixedDescription` | 고정/통계 제외/일괄 적용 옵션 |
| `feature/transactionedit/ui/TransactionSameStoreRuleCard.kt` | `TransactionSameStoreRuleCard`, `RuleKeywordInput`, `KeywordGuideCard` | 동일 거래처 적용 키워드 |
| `feature/transactionedit/ui/TransactionOriginalSmsCard.kt` | `TransactionOriginalSmsCard` | 원본 문자 표시 |
| `feature/transactionedit/ui/TransactionEditComponents.kt` | `TransactionSectionCard`, `TransactionDivider` | 거래 편집 카드 공통 요소 |
| `feature/transactionactions/ui/TransactionQuickActionDialog.kt` | `TransactionQuickActionDialog`, `TransactionQuickActionContent`, `QuickTransactionMenuItem` | 중앙 모달의 거래명·닫기와 아이콘 포함 세로 수정/삭제 행, 기존 편집 화면 진입과 삭제 확인 |

알림 진입의 거래 식별/삭제 후 처리 계약은 [notification-display](../notification-display/README.md)와 [transaction-edit](../transaction-edit/README.md)를 따른다. 상태/서비스/mapper 분리의 전체 감사는 [화면과 기능 책임 감사](../project-context/03-screen-function-architecture-audit.md)를 본다.

- 홈 누적 추이의 현재/이전 월 범례는 월만 표시한다. 상단 월 선택과 실제 비교 기간은 연도를 유지한다.
