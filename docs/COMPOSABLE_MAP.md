# Composable 맵 - 화면별 컴포넌트 구조

> 각 화면의 Composable 계층 구조를 트리로 정리한 문서
> 함수 참조 클릭 시 IDE에서 해당 파일로 이동 가능
> **최종 갱신**: 2026-02-21

---

## 앱 루트

```
ForceUpdateDialog                    ← 강제 업데이트 다이얼로그 (닫기 불가)
MoneyTalkApp                         ← 앱 최상위 Scaffold + BottomNav + 전역 스낵바
├── NavGraph                         ← 화면 라우팅 (스플래시 → 홈/내역/채팅/설정)
├── BackPressHandler                 ← 채팅방 뒤로가기 처리
└── MoneyTalkTheme                   ← 라이트/다크 테마 적용
```

| 함수 | 설명 | 참조 |
|------|------|------|
| ForceUpdateDialog | 강제 업데이트 다이얼로그 (닫기 불가, 업데이트/종료) | [MainActivityKt](../app/src/main/java/com/sanha/moneytalk/MainActivity.kt) |
| MoneyTalkApp | Scaffold + BottomNav + 전역 스낵바 | [MainActivityKt](../app/src/main/java/com/sanha/moneytalk/MainActivity.kt) |
| BackPressHandler | 채팅방 뒤로가기 처리 | [MainActivityKt](../app/src/main/java/com/sanha/moneytalk/MainActivity.kt) |
| NavGraph | 화면 라우팅 정의 | [NavGraphKt](../app/src/main/java/com/sanha/moneytalk/navigation/NavGraph.kt) |
| MoneyTalkTheme | 라이트/다크 테마 적용 | [ThemeKt](../app/src/main/java/com/sanha/moneytalk/core/theme/Theme.kt) |
| SplashScreen | 로고 페이드인 후 홈으로 전환 | [SplashScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/splash/ui/SplashScreen.kt) |

---

## 1. 홈 화면 (HomeScreen)

```
HomeScreen                           ← 홈 탭 메인 화면
├── MonthlyOverviewSection           ← 월간 수입/지출 현황 + 월 네비게이션
├── SpendingTrendSection             ← 누적 지출 추이 (홈 전용 래퍼)
│   └── CumulativeTrendSection       ← 누적 추이 섹션 (도메인 독립, 원형 토글+차트+범례)
│       └── CumulativeChartCompose   ← 누적 곡선 차트 (도메인 독립, Canvas)
├── CategoryExpenseSection           ← 카테고리별 지출 (도넛 차트 + 리스트)
│   ├── DonutChartCompose            ← 도넛 차트 (3+ 카테고리, Canvas)
│   └── CategoryIcon                 ← 카테고리 이모지 아이콘 (공통)
├── AiInsightCard                    ← Gemini AI 소비 분석 요약
├── TodayAndComparisonSection        ← 오늘 지출 + 전월 대비 래퍼
│   ├── TodayExpenseCard             ← 오늘 총 지출 금액/건수
│   └── MonthComparisonCard          ← 전월 동일 기간 대비 차이
├── TransactionCardCompose           ← 오늘 거래 카드 (공통)
│   └── CategoryIcon                 ← 카테고리 이모지 아이콘 (공통)
├── EmptyExpenseSection              ← 지출 없을 때 빈 상태
├── FullSyncCtaSection              ← 전체 동기화 해제 CTA (공통)
├── ExpenseDetailDialog              ← 지출 상세/수정/삭제 다이얼로그 (공통)
│   └── CategorySelectDialog         ← 카테고리 변경 다이얼로그 (공통)
└── [AlertDialog]                    ← 분류 확인/진행률/SMS 동기화 다이얼로그
```

| 함수 | 설명 | 참조 |
|------|------|------|
| HomeScreen | 홈 탭 메인 화면 | [HomeScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt) |
| MonthlyOverviewSection | 월간 수입/지출 현황 + 월 네비게이션 | [HomeScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt) |
| SpendingTrendSection | 누적 지출 추이 (홈 전용 래퍼) | [SpendingTrendSectionKt](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/component/SpendingTrendSection.kt) |
| CumulativeTrendSection | 누적 추이 섹션 (도메인 독립, 원형 토글+차트+범례) | [CumulativeTrendSectionKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/chart/CumulativeTrendSection.kt) |
| CumulativeChartCompose | 누적 곡선 차트 (도메인 독립, Canvas) | [CumulativeChartComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/chart/CumulativeChartCompose.kt) |
| CategoryExpenseSection | 카테고리별 지출 비율 그래프 | [HomeScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt) |
| AiInsightCard | Gemini AI 소비 분석 요약 카드 | [HomeScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt) |
| TodayAndComparisonSection | 오늘 지출 + 전월 대비 래퍼 | [HomeScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt) |
| TodayExpenseCard | 오늘 총 지출 금액/건수 카드 | [HomeScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt) |
| MonthComparisonCard | 전월 동일 기간 대비 차이 카드 | [HomeScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt) |
| EmptyExpenseSection | 지출 없을 때 빈 상태 표시 | [HomeScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt) |
| FullSyncCtaSection | 전체 동기화 해제 CTA (3개월 이전 빈 페이지) | [FullSyncCtaSectionKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/FullSyncCtaSection.kt) |

---

## 2. 내역 화면 (HistoryScreen)

```
HistoryScreen                        ← 내역 탭 메인 화면
├── SearchBar                        ← 가게명/메모 키워드 검색
├── PeriodSummaryCard                ← 기간 네비게이션 + 총 수입/지출
├── FilterTabRow                     ← 목록/달력 탭 + 필터/검색/추가 버튼
│   ├── SegmentedTabRowCompose       ← 목록/달력 전환 탭 (공통)
│   └── FilterChipButton             ← 필터 버튼
│
├── [목록 모드]
│   └── TransactionListView          ← 통합 거래 목록 (순수 렌더링)
│       ├── TransactionGroupHeaderCompose ← 날짜/가게/금액 그룹 헤더 (공통)
│       ├── TransactionCardCompose   ← 지출/수입 거래 카드 (공통)
│       │   └── CategoryIcon         ← 카테고리 이모지 아이콘 (공통)
│       └── FullSyncCtaSection       ← 전체 동기화 해제 CTA (공통, 빈 상태)
│
├── [달력 모드]
│   └── BillingCycleCalendarView     ← 결제 기간 기준 달력
│       ├── CalendarDayCell          ← 날짜 셀 (날짜 + 일별 지출)
│       └── TransactionCardCompose   ← 선택 날짜 거래 카드 (공통)
│
├── FilterBottomSheet                ← 정렬/거래유형/카테고리 필터
│   └── FilterCategoryGridItem       ← 카테고리 선택 그리드 아이템
│
├── ExpenseDetailDialog              ← 지출 상세/수정/삭제 (공통)
│   └── CategorySelectDialog         ← 카테고리 변경 (공통)
├── IncomeDetailDialog               ← 수입 상세 + 원본 SMS 표시
└── AddExpenseDialog                 ← 수동 지출 추가
```

| 함수 | 설명 | 참조 |
|------|------|------|
| HistoryScreen | 내역 탭 메인 화면 | [HistoryScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryScreen.kt) |
| TransactionListView | 통합 거래 목록 (순수 렌더링) | [HistoryScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryScreen.kt) |
| SearchBar | 가게명/메모 키워드 검색 | [HistoryHeaderKt](../app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryHeader.kt) |
| PeriodSummaryCard | 기간 네비게이션 + 총 수입/지출 | [HistoryHeaderKt](../app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryHeader.kt) |
| FilterTabRow | 탭 + 필터 아이콘 통합 Row | [HistoryHeaderKt](../app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryHeader.kt) |
| FilterChipButton | 필터 칩 버튼 (일관된 스타일) | [HistoryHeaderKt](../app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryHeader.kt) |
| BillingCycleCalendarView | 결제 기간 기준 달력 뷰 | [HistoryCalendarKt](../app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryCalendar.kt) |
| CalendarDayCell | 달력 날짜 셀 (날짜 + 일별 지출) | [HistoryCalendarKt](../app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryCalendar.kt) |
| FilterBottomSheet | 정렬/거래유형/카테고리 선택 BottomSheet | [HistoryFilterKt](../app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryFilter.kt) |
| FilterCategoryGridItem | BottomSheet 내 카테고리 그리드 아이템 | [HistoryFilterKt](../app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryFilter.kt) |
| ExpenseDetailDialog | 지출 상세/수정/삭제 다이얼로그 | [ExpenseItemCardKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/ExpenseItemCard.kt) |
| CategorySelectDialog | 카테고리 변경 다이얼로그 (3열 그리드) | [ExpenseItemCardKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/ExpenseItemCard.kt) |
| IncomeDetailDialog | 수입 상세 + 원본 SMS 다이얼로그 | [HistoryDialogsKt](../app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryDialogs.kt) |
| AddExpenseDialog | 수동 지출 추가 다이얼로그 | [HistoryDialogsKt](../app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryDialogs.kt) |

---

## 3. 채팅 화면 (ChatScreen)

```
ChatScreen                           ← 채팅 탭 메인 (목록 ↔ 채팅방 전환)
│
├── [채팅방 목록]
│   └── ChatRoomListView             ← 기존 세션 목록 + 새 채팅 버튼
│       └── SessionItem              ← 세션 카드 (제목 + 시간 + 삭제)
│
├── [채팅방 내부]
│   └── ChatRoomView                 ← 메시지 목록 + 입력창
│       ├── ChatBubble               ← 메시지 버블 (사용자/AI 좌우 정렬)
│       ├── TypingIndicator          ← AI 응답 중 점 애니메이션
│       ├── RetryButton              ← 응답 실패 시 재시도 버튼
│       ├── GuideQuestionsOverlay    ← 빈 채팅방 예시 질문 칩
│       └── RewardAdDialog           ← 리워드 광고 시청 안내 다이얼로그
│
└── ApiKeyDialog                     ← Gemini API 키 입력 (채팅 시작 시)
```

| 함수 | 설명 | 참조 |
|------|------|------|
| ChatScreen | 채팅 탭 메인 화면 | [ChatScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatScreen.kt) |
| ChatRoomView | 채팅방 내부 (메시지 + 입력창) | [ChatScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatScreen.kt) |
| ChatRoomListView | 채팅 세션 목록 + 새 채팅 버튼 | [ChatRoomListViewKt](../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatRoomListView.kt) |
| SessionItem | 세션 카드 (제목 + 시간 + 삭제) | [ChatRoomListViewKt](../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatRoomListView.kt) |
| GuideQuestionsOverlay | 빈 채팅방 가이드 질문 칩 | [ChatComponentsKt](../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatComponents.kt) |
| ChatBubble | 메시지 버블 (마크다운 렌더링) | [ChatComponentsKt](../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatComponents.kt) |
| TypingIndicator | AI 응답 중 점 애니메이션 | [ChatComponentsKt](../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatComponents.kt) |
| RetryButton | 응답 실패 시 재시도 버튼 | [ChatComponentsKt](../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatComponents.kt) |
| RewardAdDialog | 리워드 광고 시청 안내 다이얼로그 | [ChatScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatScreen.kt) |
| ApiKeyDialog | Gemini API 키 입력 (채팅용) | [ChatComponentsKt](../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatComponents.kt) |

---

## 4. 설정 화면 (SettingsScreen)

```
SettingsScreen                       ← 설정 탭 메인 화면
├── SettingsSectionCompose("화면 설정")  ← 섹션 그룹 (공통)
│   └── SettingsItemCompose          ← 테마 선택 (공통)
├── SettingsSectionCompose("기간 설정")
│   └── SettingsItemCompose          ← 월 시작일
├── SettingsSectionCompose("AI 설정")
│   └── SettingsItemCompose          ← API 키 + 카테고리 정리
├── SettingsSectionCompose("데이터 관리")
│   └── SettingsItemCompose × 6      ← 제외키워드/내보내기/Drive/복원/중복제거/삭제
├── SettingsSectionCompose("앱 정보")
│   └── SettingsItemCompose × 2      ← 버전/개인정보
│
├── [BottomSheet]
│   └── BudgetBottomSheet            ← 전체+카테고리별 예산 설정
│       ├── TotalBudgetInput         ← 전체 예산 입력 (OutlinedTextField)
│       ├── CategoryBudgetHeader     ← 카테고리별 예산 헤더 + 초기화
│       └── CategoryBudgetRow        ← 카테고리 예산 입력 행 (% 자동 표시)
│
├── [다이얼로그 — 환경 설정]
│   ├── ThemeModeDialog              ← 시스템/라이트/다크 선택
│   ├── ApiKeySettingDialog          ← Gemini API 키 입력
│   └── MonthStartDayDialog          ← 정산 시작일 (1~31일) 선택
│
├── [다이얼로그 — 데이터 관리]
│   ├── ExportDialog                 ← JSON/CSV 내보내기
│   ├── GoogleDriveDialog            ← Drive 백업 목록/업로드/복원
│   │   └── DriveBackupFileItem      ← Drive 백업 파일 아이템
│   └── ExclusionKeywordDialog       ← SMS 제외 키워드 관리
│       └── ExclusionKeywordItem     ← 개별 키워드 아이템
│
└── [다이얼로그 — 앱 정보]
    ├── AppInfoDialog                ← 앱 버전/빌드 정보
    └── PrivacyPolicyDialog          ← 개인정보 처리방침
```

| 함수 | 설명 | 참조 |
|------|------|------|
| SettingsScreen | 설정 탭 메인 화면 | [SettingsScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsScreen.kt) |
| BudgetBottomSheet | 전체+카테고리별 예산 설정 BottomSheet | [BudgetBottomSheetKt](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/BudgetBottomSheet.kt) |
| TotalBudgetInput | 전체 예산 입력 영역 | [BudgetBottomSheetKt](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/BudgetBottomSheet.kt) |
| CategoryBudgetHeader | 카테고리별 예산 헤더 + 초기화 | [BudgetBottomSheetKt](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/BudgetBottomSheet.kt) |
| CategoryBudgetRow | 카테고리 예산 행 (이모지+입력+%) | [BudgetBottomSheetKt](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/BudgetBottomSheet.kt) |
| ThemeModeDialog | 시스템/라이트/다크 모드 선택 | [SettingsPreferenceDialogsKt](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsPreferenceDialogs.kt) |
| ApiKeySettingDialog | Gemini API 키 입력/저장 | [SettingsPreferenceDialogsKt](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsPreferenceDialogs.kt) |
| MonthStartDayDialog | 정산 시작일 (1~31일) 선택 | [SettingsPreferenceDialogsKt](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsPreferenceDialogs.kt) |
| ExportDialog | JSON/CSV 형식 데이터 내보내기 | [SettingsDataDialogsKt](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsDataDialogs.kt) |
| GoogleDriveDialog | Drive 백업 목록/업로드/복원 | [SettingsDataDialogsKt](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsDataDialogs.kt) |
| DriveBackupFileItem | Drive 백업 파일 아이템 | [SettingsDataDialogsKt](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsDataDialogs.kt) |
| ExclusionKeywordDialog | SMS 제외 키워드 관리 | [SettingsDataDialogsKt](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsDataDialogs.kt) |
| ExclusionKeywordItem | 개별 키워드 아이템 (삭제 가능) | [SettingsDataDialogsKt](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsDataDialogs.kt) |
| AppInfoDialog | 앱 버전/빌드 정보 | [SettingsInfoDialogsKt](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsInfoDialogs.kt) |
| PrivacyPolicyDialog | 개인정보 처리방침 | [SettingsInfoDialogsKt](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsInfoDialogs.kt) |

---

## 5. 공통 컴포넌트

| 함수 | 설명 | 사용 화면 | 참조 |
|------|------|----------|------|
| TransactionCardCompose | 지출/수입 통합 거래 카드 | 홈, 내역(목록/달력) | [TransactionCardComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/transaction/card/TransactionCardCompose.kt) |
| TransactionGroupHeaderCompose | 날짜/가게/금액 그룹 헤더 | 내역(목록) | [TransactionGroupHeaderComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/transaction/header/TransactionGroupHeaderCompose.kt) |
| SegmentedTabRowCompose | 세그먼트 스타일 탭 Row | 내역(FilterTabRow) | [SegmentedTabRowComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/tab/SegmentedTabRowCompose.kt) |
| CategoryIcon | 카테고리 이모지 아이콘 (원형 배경) | 홈, 거래 카드 | [CategoryIconKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/CategoryIcon.kt) |
| CumulativeTrendSection | 누적 추이 섹션 (원형 토글+차트+범례) | 홈(SpendingTrendSection) | [CumulativeTrendSectionKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/chart/CumulativeTrendSection.kt) |
| CumulativeChartCompose | 누적 곡선 차트 (Canvas 렌더러) | CumulativeTrendSection 내부 | [CumulativeChartComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/chart/CumulativeChartCompose.kt) |
| DonutChartCompose | 도넛 차트 (Canvas drawArc + 범례) | 홈(CategoryExpenseSection) | [DonutChartComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/chart/DonutChartCompose.kt) |
| ExpenseDetailDialog | 지출 상세/수정/삭제 다이얼로그 | 홈, 내역 | [ExpenseItemCardKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/ExpenseItemCard.kt) |
| CategorySelectDialog | 카테고리 변경 (3열 그리드) | ExpenseDetailDialog 내부 | [ExpenseItemCardKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/ExpenseItemCard.kt) |
| CategoryPickerDialog | 카테고리 선택 (하위 호환) | AddExpenseDialog 내부 | [ExpenseItemCardKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/ExpenseItemCard.kt) |
| SettingsSectionCompose | 설정 섹션 (타이틀 + Card) | 설정 | [SettingsSectionComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/settings/SettingsSectionCompose.kt) |
| SettingsItemCompose | 설정 아이템 (아이콘 + 텍스트) | 설정 | [SettingsItemComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/settings/SettingsItemCompose.kt) |
| MonthPagerUtils | HorizontalPager 페이지↔월 변환 유틸 | 홈, 내역 | [MonthPagerUtilsKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/MonthPagerUtils.kt) |

---

## Contract (Info) 인터페이스

| Interface | 설명 | 참조 |
|-----------|------|------|
| TransactionCardInfo | 거래 카드 데이터 계약 | [TransactionCardInfo](../app/src/main/java/com/sanha/moneytalk/core/ui/component/transaction/card/TransactionCardInfo.kt) |
| TransactionGroupHeaderInfo | 그룹 헤더 데이터 계약 | [TransactionGroupHeaderInfo](../app/src/main/java/com/sanha/moneytalk/core/ui/component/transaction/header/TransactionGroupHeaderInfo.kt) |
| SegmentedTabInfo | 탭 데이터 계약 | [SegmentedTabInfo](../app/src/main/java/com/sanha/moneytalk/core/ui/component/tab/SegmentedTabInfo.kt) |
| SettingsItemInfo | 설정 아이템 데이터 계약 | [SettingsItemInfo](../app/src/main/java/com/sanha/moneytalk/core/ui/component/settings/SettingsItemInfo.kt) |
