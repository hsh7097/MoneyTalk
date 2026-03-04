# Composable 맵 - 화면별 컴포넌트 구조

> 각 화면의 Composable 계층 구조를 트리로 정리한 문서
> 함수 참조 클릭 시 IDE에서 해당 파일로 이동 가능
> **최종 갱신**: 2026-03-04

---

## IntroActivity (LAUNCHER)

```
IntroActivity                        ← 앱 초기 진입 (스플래시 + 온보딩 + 권한 + RTDB + 강제 업데이트)
├── SplashScreen                     ← 로고 페이드인 애니메이션
├── OnboardingScreen                 ← 3페이지 스와이프 인트로 (앱 핵심 가치 전달)
│   ├── OnboardingPageContent        ← 페이지 콘텐츠 (이모지 + 제목 + 설명 + feature bullets)
│   └── PageIndicatorDot             ← 페이지 인디케이터 점
├── PermissionScreen                 ← SMS 권한 설명 + 동의/비동의
├── ForceUpdateDialog                ← 강제 업데이트 다이얼로그 (닫기 불가)
└── [NAVIGATING 배경]               ← RTDB 대기 중 그라데이션 배경
```

| 함수 | 설명 | 참조 |
|------|------|------|
| SplashScreen | 로고 페이드인 후 다음 단계로 전환 | [SplashScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/splash/ui/SplashScreen.kt) |
| OnboardingScreen | 3페이지 HorizontalPager 온보딩 인트로 | [OnboardingScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/intro/ui/OnboardingScreen.kt) |
| PermissionScreen | SMS 권한 설명 카드 UI | [PermissionScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/intro/ui/PermissionScreen.kt) |
| ForceUpdateDialog | 강제 업데이트 다이얼로그 (업데이트/종료) | [ForceUpdateDialogKt](../app/src/main/java/com/sanha/moneytalk/core/ui/ForceUpdateDialog.kt) |

---

## 앱 루트 (MainActivity)

```
ForceUpdateDialog                    ← 강제 업데이트 다이얼로그 (앱 사용 중 RTDB 변경 시)
MoneyTalkApp                         ← 앱 최상위 Scaffold + BottomNav + 전역 스낵바 + 전역 다이얼로그
├── ON_RESUME → MainViewModel        ← Activity 레벨 resume 처리 (동기화/권한)
├── [AlertDialog: SMS 동기화]        ← SMS 동기화 진행 (Stepper UI + dismiss 가능)
│   └── SyncStepIndicator            ← 5단계 파이프라인 진행 인디케이터
├── [AlertDialog: AI 성과 요약]      ← 초기 동기화 완료 후 엔진 부트스트랩 결과 표시
├── [AlertDialog: 전체 동기화 해제]  ← 광고 시청 후 월별 동기화 해제
├── NavGraph                         ← 화면 라우팅 (홈/내역/채팅/설정)
├── BackPressHandler                 ← 채팅방 뒤로가기 처리
└── MoneyTalkTheme                   ← 라이트/다크 테마 적용
```

| 함수 | 설명 | 참조 |
|------|------|------|
| ForceUpdateDialog | 강제 업데이트 다이얼로그 (닫기 불가, 업데이트/종료) | [ForceUpdateDialogKt](../app/src/main/java/com/sanha/moneytalk/core/ui/ForceUpdateDialog.kt) |
| MoneyTalkApp | Scaffold + BottomNav + 전역 스낵바 + 전역 다이얼로그 | [MainActivityKt](../app/src/main/java/com/sanha/moneytalk/MainActivity.kt) |
| SyncStepIndicator | SMS 동기화 5단계 진행 인디케이터 | [MainActivityKt](../app/src/main/java/com/sanha/moneytalk/MainActivity.kt) |
| BackPressHandler | 채팅방 뒤로가기 처리 | [MainActivityKt](../app/src/main/java/com/sanha/moneytalk/MainActivity.kt) |
| NavGraph | 화면 라우팅 정의 | [NavGraphKt](../app/src/main/java/com/sanha/moneytalk/navigation/NavGraph.kt) |
| MoneyTalkTheme | 라이트/다크 테마 적용 | [ThemeKt](../app/src/main/java/com/sanha/moneytalk/core/theme/Theme.kt) |

---

## 1. 홈 화면 (HomeScreen)

```
HomeScreen                           ← 홈 탭 메인 화면
├── MonthlyOverviewSection           ← 월간 수입/지출 현황 + 월 네비게이션
├── SpendingTrendSection             ← 누적 지출 추이 (홈 전용 래퍼)
│   └── CumulativeTrendSection       ← 누적 추이 섹션 (금액+비교문구+Vico차트+범례)
│       └── VicoCumulativeChart      ← Vico 기반 누적 곡선 차트 (금융앱 스타일)
├── CategoryExpenseSection           ← 카테고리별 지출 리스트 (예산 진척률 포함)
│   └── CategoryIcon                 ← 카테고리 이모지 아이콘 (공통)
├── AiInsightCard                    ← Gemini AI 소비 분석 요약
├── TransactionCardCompose           ← 오늘 거래 카드 (공통, 헤더에 오늘 지출 요약 포함)
│   └── CategoryIcon                 ← 카테고리 이모지 아이콘 (공통)
├── ImportDataCtaSection             ← 데이터 가져오기 CTA (현재월, 권한 없거나 데이터 없음)
├── EmptyExpenseSection              ← 지출 없을 때 빈 상태
├── ExpenseDetailDialog              ← 지출 상세/수정/삭제 다이얼로그 (공통)
│   └── CategorySelectDialog         ← 카테고리 변경 다이얼로그 (공통)
├── [AlertDialog]                    ← 분류 확인/진행률 다이얼로그
└── BannerAdCompose                  ← 하단 고정 배너 광고 (RTDB reward_ad_enabled 연동, 공통)
```

| 함수 | 설명 | 참조 |
|------|------|------|
| HomeScreen | 홈 탭 메인 화면 | [HomeScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt) |
| MonthlyOverviewSection | 월간 수입/지출 현황 + 월 네비게이션 | [HomeScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt) |
| SpendingTrendSection | 누적 지출 추이 (홈 전용 래퍼) | [SpendingTrendSectionKt](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/component/SpendingTrendSection.kt) |
| CumulativeTrendSection | 누적 추이 섹션 (도메인 독립, 원형 토글+차트+범례) | [CumulativeTrendSectionKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/chart/CumulativeTrendSection.kt) |
| CumulativeChartCompose | 누적 곡선 차트 (도메인 독립, Canvas) | [CumulativeChartComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/chart/CumulativeChartCompose.kt) |
| CategoryExpenseSection | 카테고리별 지출 리스트 (예산 진척률 포함) | [HomeScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt) |
| AiInsightCard | Gemini AI 소비 분석 요약 카드 | [HomeScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt) |
| ImportDataCtaSection | 데이터 가져오기 CTA (현재월, SMS 권한/데이터 없음) | [ImportDataCtaSectionKt](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/component/ImportDataCtaSection.kt) |
| EmptyExpenseSection | 지출 없을 때 빈 상태 표시 | [HomeScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt) |

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
│       ├── ImportDataCtaSection     ← 데이터 가져오기 CTA (현재월, 빈 상태)
│       ├── TransactionGroupHeaderCompose ← 날짜/가게/금액 그룹 헤더 (공통)
│       ├── TransactionCardCompose   ← 지출/수입 거래 카드 (공통)
│       │   └── CategoryIcon         ← 카테고리 이모지 아이콘 (공통)
│       └── FullSyncCtaSection       ← 전체 동기화 해제 CTA (공통, 빈 상태)
│
├── [달력 모드]
│   └── BillingCycleCalendarView     ← 결제 기간 기준 달력 (날짜 클릭 → TransactionDetailListActivity)
│       └── CalendarDayCell          ← 날짜 셀 (날짜 + 수입/지출 2줄)
│
├── FilterBottomSheet                ← 정렬/거래유형/카테고리 필터
│   └── FilterCategoryGridItem       ← 카테고리 선택 그리드 아이템
│
├── [지출 선택 시]                    → TransactionEditActivity 이동
├── IncomeDetailDialog               ← 수입 상세 + 원본 SMS 표시
├── [+ 버튼]                          → TransactionEditActivity (새 거래 모드) 이동
└── BannerAdCompose                  ← 하단 고정 배너 광고 (RTDB reward_ad_enabled 연동, 공통)
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
| CalendarDayCell | 달력 날짜 셀 (날짜 + 수입/지출 2줄) | [HistoryCalendarKt](../app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryCalendar.kt) |
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
│   └── SettingsItemCompose × 6      ← 문자설정/내보내기/Drive/복원/중복제거/삭제
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
│   └── GoogleDriveDialog            ← Drive 백업 목록/업로드/복원
│       └── DriveBackupFileItem      ← Drive 백업 파일 아이템
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
| AppInfoDialog | 앱 버전/빌드 정보 | [SettingsInfoDialogsKt](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsInfoDialogs.kt) |
| PrivacyPolicyDialog | 개인정보 처리방침 | [SettingsInfoDialogsKt](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsInfoDialogs.kt) |

---

## 5. 카테고리 상세 화면 (CategoryDetailScreen)

```
CategoryDetailScreen                 ← 카테고리 상세 (Activity, 홈에서 카테고리 탭 시 진입)
├── TopAppBar                        ← 뒤로가기 + 카테고리 이모지 + 이름
├── HorizontalPager                  ← 월별 페이징
│   └── CategoryDetailPageContent    ← 월별 콘텐츠 (정렬 토글 포함)
│       ├── FilterChipButton         ← 정렬 토글 칩 (최신순/가격순)
│       ├── MonthNavigationHeader    ← 월 네비게이션 (좌우 화살표)
│       ├── SpendingTrendSection     ← 누적 지출 추이 (카테고리 전용)
│       ├── TransactionGroupHeaderCompose ← 날짜 그룹 헤더 (공통)
│       └── TransactionCardCompose   ← 거래 카드 (공통)
├── [지출 선택 시]                    → TransactionEditActivity 이동
└── BannerAdCompose                  ← 하단 고정 배너 광고 (RTDB reward_ad_enabled 연동, 공통)
```

| 함수 | 설명 | 참조 |
|------|------|------|
| CategoryDetailScreen | 카테고리 상세 메인 화면 | [CategoryDetailScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/categorydetail/ui/CategoryDetailScreen.kt) |
| CategoryDetailPageContent | 월별 콘텐츠 (차트 + 거래 목록) | [CategoryDetailScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/categorydetail/ui/CategoryDetailScreen.kt) |
| MonthNavigationHeader | 월 네비게이션 헤더 | [CategoryDetailScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/categorydetail/ui/CategoryDetailScreen.kt) |

---

## 6. 거래 편집 화면 (TransactionEditScreen)

```
TransactionEditActivity               ← 거래 편집/추가 (별도 Activity, 뱅크셀러드 스타일)
└── TransactionEditScreen             ← 거래 편집 폼 (X 닫기 + 헤더 + 분류 탭 + 폼)
    ├── TransactionHeader             ← 헤더 (X 닫기 + 거래처명 + 금액, 인라인 편집)
    ├── TransactionTypeTab            ← 수입/지출/이체 분류 (SegmentedTabRowCompose)
    ├── CompactReadOnlyRow            ← 읽기 전용 행 (날짜-시간/카테고리 등 picker용)
    ├── CompactEditRow                ← 편집 가능 행 (BasicTextField + 포커스 X버튼)
    ├── FixedExpenseToggle            ← 고정지출 토글 (Switch, 지출/이체만 표시)
    ├── ApplyToAllCheckbox            ← 동일 거래처 일괄 적용 체크박스 (카테고리/고정지출, 기존 거래만)
    ├── CategorySelectDialog          ← 카테고리 선택 (공통)
    ├── DatePickerDialog              ← 날짜 선택 (Material3)
    ├── TimePickerDialog              ← 시간 선택 (AlertDialog 래퍼)
    ├── TransactionBottomButtons      ← 하단 버튼 (삭제 + 저장, 나란히 배치)
    └── [AlertDialog: 삭제 확인]       ← 기존 거래 삭제 시
```

| 함수 | 설명 | 참조 |
|------|------|------|
| TransactionEditScreen | 거래 편집/추가 전체 화면 (뱅크셀러드 스타일) | [TransactionEditScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditScreen.kt) |
| TransactionHeader | 헤더 (X 닫기 + 거래처명 + 금액 인라인 편집) | [TransactionEditScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditScreen.kt) |
| TransactionTypeTab | 수입/지출/이체 분류 탭 (SegmentedTabRowCompose) | [TransactionEditScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditScreen.kt) |
| FixedExpenseToggle | 고정지출 토글 (Switch + 라벨) | [TransactionEditScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditScreen.kt) |
| ApplyToAllCheckbox | 동일 거래처 일괄 적용 체크박스 | [TransactionEditScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditScreen.kt) |
| TransactionBottomButtons | 하단 삭제+저장 버튼 | [TransactionEditScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditScreen.kt) |
| CompactEditRow | 편집 가능 컴팩트 행 (BasicTextField + 포커스 X버튼) | [TransactionEditScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditScreen.kt) |
| CompactReadOnlyRow | 읽기 전용 컴팩트 행 (클릭 → picker) | [TransactionEditScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditScreen.kt) |
| TimePickerDialog | TimePicker AlertDialog 래퍼 | [TransactionEditScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditScreen.kt) |

---

## 7. 날짜별 거래 목록 화면 (TransactionDetailListScreen)

```
TransactionDetailListActivity         ← 날짜별 거래 목록 (달력 날짜 클릭 시 진입)
└── TransactionDetailListScreen       ← 거래 목록 (지출+수입)
    ├── TransactionCardCompose        ← 거래 카드 (공통)
    └── [지출 클릭 시]                 → TransactionEditActivity 이동
```

| 함수 | 설명 | 참조 |
|------|------|------|
| TransactionDetailListScreen | 날짜별 거래 목록 화면 | [TransactionDetailListScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/transactionlist/ui/TransactionDetailListScreen.kt) |

---

## 8. 문자 설정 화면 (SmsSettingsScreen)

```
SmsSettingsScreen                    ← 문자 설정 (Activity, 설정에서 "문자 설정" 탭 시 진입)
├── TopAppBar                        ← 뒤로가기 + 동적 타이틀
└── NavHost                          ← 내부 네비게이션 (MAIN/BLOCKED_PHRASES/BLOCKED_SENDERS)
    ├── SmsSettingsMainContent        ← 메인 (문자분석 업데이트 + 수신차단 메뉴)
    │   ├── SettingsSectionCompose("문자 분석")
    │   │   └── SettingsItemCompose   ← 문자분석 업데이트 (SMS 재동기화 트리거)
    │   └── SettingsSectionCompose("수신 차단")
    │       ├── SettingsItemCompose   ← 수신거부 문구 관리 → BLOCKED_PHRASES
    │       └── SettingsItemCompose   ← 수신거부 전화번호 관리 → BLOCKED_SENDERS
    ├── BlockedPhraseManageScreen     ← 제외 키워드 CRUD (전체 화면)
    │   └── BlockedPhraseItem         ← 키워드 아이템 (소스 표시 + 삭제)
    └── BlockedSenderManageScreen     ← 차단 번호 CRUD (전체 화면)
```

| 함수 | 설명 | 참조 |
|------|------|------|
| SmsSettingsScreen | 문자 설정 메인 화면 (NavHost) | [SmsSettingsScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/smssettings/ui/SmsSettingsScreen.kt) |
| SmsSettingsMainContent | 메인 메뉴 (분석 업데이트 + 수신차단) | [SmsSettingsScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/smssettings/ui/SmsSettingsScreen.kt) |
| BlockedPhraseManageScreen | 제외 키워드 관리 (추가/삭제) | [SmsSettingsScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/smssettings/ui/SmsSettingsScreen.kt) |
| BlockedPhraseItem | 키워드 아이템 (소스 라벨 + 삭제) | [SmsSettingsScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/smssettings/ui/SmsSettingsScreen.kt) |
| BlockedSenderManageScreen | 차단 번호 관리 (추가/삭제) | [SmsSettingsScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/smssettings/ui/SmsSettingsScreen.kt) |

---

## 9. 카테고리 설정 화면 (CategorySettingsScreen)

```
CategorySettingsActivity               ← 카테고리 설정 (별도 Activity, SmsSettings 패턴)
└── CategorySettingsScreen             ← 카테고리 설정 메인 화면
    ├── FilterChip × 3                 ← 지출/수입/이체 탭 전환
    ├── LazyColumn                     ← 기본 + 커스텀 카테고리 목록
    │   ├── 기본 카테고리 (읽기 전용)   ← 이모지 + 이름
    │   ├── 커스텀 카테고리 (삭제 가능)  ← 이모지 + 이름 + 삭제 아이콘
    │   └── "+ 카테고리 추가" 버튼
    ├── AddCategoryDialog              ← 커스텀 카테고리 추가 다이얼로그
    │   ├── EmojiPickerCompose         ← 5열 이모지 그리드 (80개 프리셋)
    │   └── TextField                  ← 카테고리 이름 입력
    └── [AlertDialog: 삭제 확인]        ← 커스텀 카테고리 삭제 시
```

| 함수 | 설명 | 참조 |
|------|------|------|
| CategorySettingsScreen | 카테고리 설정 메인 화면 | [CategorySettingsScreenKt](../app/src/main/java/com/sanha/moneytalk/feature/categorysettings/ui/CategorySettingsScreen.kt) |
| EmojiPickerCompose | 이모지 선택 5열 그리드 (공통) | [EmojiPickerComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/EmojiPickerCompose.kt) |

---

## 10. 공통 컴포넌트

| 함수 | 설명 | 사용 화면 | 참조 |
|------|------|----------|------|
| TransactionCardCompose | 지출/수입 통합 거래 카드 | 홈, 내역(목록/달력) | [TransactionCardComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/transaction/card/TransactionCardCompose.kt) |
| TransactionGroupHeaderCompose | 날짜/가게/금액 그룹 헤더 | 내역(목록) | [TransactionGroupHeaderComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/transaction/header/TransactionGroupHeaderCompose.kt) |
| SegmentedTabRowCompose | 세그먼트 스타일 탭 Row | 내역(FilterTabRow) | [SegmentedTabRowComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/tab/SegmentedTabRowCompose.kt) |
| CategoryIcon | 카테고리 이모지 아이콘 (원형 배경) | 홈, 거래 카드 | [CategoryIconKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/CategoryIcon.kt) |
| CumulativeTrendSection | 누적 추이 섹션 (금액+비교문구+Vico차트+범례) | 홈(SpendingTrendSection) | [CumulativeTrendSectionKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/chart/CumulativeTrendSection.kt) |
| VicoCumulativeChart | Vico 기반 누적 곡선 차트 (금융앱 스타일) | CumulativeTrendSection 내부 | [VicoCumulativeChartKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/chart/VicoCumulativeChart.kt) |
| CumulativeChartCompose | 누적 곡선 차트 (Canvas, 하위 호환) | CumulativeTrendSection(개별 파라미터) 내부 | [CumulativeChartComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/chart/CumulativeChartCompose.kt) |
| DonutChartCompose | 도넛 차트 (Canvas drawArc + 범례) | 홈(CategoryExpenseSection) | [DonutChartComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/chart/DonutChartCompose.kt) |
| MonthlyBarChartSection | 6개월 수입/지출 바 차트 (Canvas) | 홈(결산카드 하단) | [MonthlyBarChartSectionKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/chart/MonthlyBarChartSection.kt) |
| ExpenseDetailDialog | 지출 상세/수정/삭제 다이얼로그 | 홈, 내역 | [ExpenseItemCardKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/ExpenseItemCard.kt) |
| CategorySelectDialog | 카테고리 변경 (3열 그리드) | ExpenseDetailDialog 내부 | [ExpenseItemCardKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/ExpenseItemCard.kt) |
| CategoryPickerDialog | 카테고리 선택 (하위 호환) | AddExpenseDialog 내부 | [ExpenseItemCardKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/ExpenseItemCard.kt) |
| SettingsSectionCompose | 설정 섹션 (타이틀 + Card) | 설정 | [SettingsSectionComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/settings/SettingsSectionCompose.kt) |
| SettingsItemCompose | 설정 아이템 (아이콘 + 텍스트) | 설정 | [SettingsItemComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/settings/SettingsItemCompose.kt) |
| BannerAdCompose | 하단 고정 배너 광고 (AdMob, RTDB 연동) | 홈, 내역, 카테고리 상세 | [BannerAdComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/BannerAdCompose.kt) |
| MonthPagerUtils | HorizontalPager 페이지↔월 변환 유틸 | 홈, 내역 | [MonthPagerUtilsKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/MonthPagerUtils.kt) |
| EmojiPickerCompose | 이모지 선택 5열 그리드 (80개 프리셋) | 카테고리 설정 | [EmojiPickerComposeKt](../app/src/main/java/com/sanha/moneytalk/core/ui/component/EmojiPickerCompose.kt) |

---

## Contract (Info) 인터페이스

| Interface | 설명 | 참조 |
|-----------|------|------|
| TransactionCardInfo | 거래 카드 데이터 계약 | [TransactionCardInfo](../app/src/main/java/com/sanha/moneytalk/core/ui/component/transaction/card/TransactionCardInfo.kt) |
| TransactionGroupHeaderInfo | 그룹 헤더 데이터 계약 | [TransactionGroupHeaderInfo](../app/src/main/java/com/sanha/moneytalk/core/ui/component/transaction/header/TransactionGroupHeaderInfo.kt) |
| SegmentedTabInfo | 탭 데이터 계약 | [SegmentedTabInfo](../app/src/main/java/com/sanha/moneytalk/core/ui/component/tab/SegmentedTabInfo.kt) |
| SettingsItemInfo | 설정 아이템 데이터 계약 | [SettingsItemInfo](../app/src/main/java/com/sanha/moneytalk/core/ui/component/settings/SettingsItemInfo.kt) |
| SpendingTrendInfo | 누적 추이 섹션 데이터 계약 (금액+비교+차트) | [SpendingTrendInfo](../app/src/main/java/com/sanha/moneytalk/core/ui/component/chart/SpendingTrendInfo.kt) |
