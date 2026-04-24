# MoneyTalk 앱 구조

## 개요

MoneyTalk는 SMS 문자 메시지를 분석하여 지출/수입을 자동으로 추적하는 Android 앱입니다.
Jetpack Compose, Hilt DI, Room Database, MVVM 아키텍처를 사용하며,
Google Gemini AI를 활용한 SMS 파싱, 카테고리 분류, 재무 상담 기능을 제공합니다.

## 패키지 구조

```
com.sanha.moneytalk/
├── MainActivity.kt                        # 앱 진입점
├── MoneyTalkApplication.kt                # Application 클래스 (Hilt)
│
├── core/                                  # 공통 모듈
│   ├── database/                          # Room 데이터베이스
│   │   ├── AppDatabase.kt                 # Room Database 정의 (v2, 15 entities)
│   │   ├── converter/
│   │   │   └── FloatListConverter.kt      # Float 리스트 타입 컨버터
│   │   ├── dao/                           # Data Access Objects
│   │   │   ├── BudgetDao.kt              # 예산 DAO
│   │   │   ├── CategoryMappingDao.kt     # 카테고리 매핑 DAO
│   │   │   ├── ChatDao.kt               # 채팅 DAO
│   │   │   ├── ExpenseDao.kt            # 지출 DAO
│   │   │   ├── IncomeDao.kt             # 수입 DAO
│   │   │   ├── OwnedCardDao.kt          # 소유 카드 DAO
│   │   │   ├── SmsExclusionKeywordDao.kt # SMS 제외 키워드 DAO
│   │   │   ├── SmsPatternDao.kt         # SMS 패턴 캐시 DAO
│   │   │   ├── SmsRegexRuleDao.kt      # SMS regex 룰 DAO (Fast Path)
│   │   │   └── StoreEmbeddingDao.kt     # 가게명 임베딩 DAO
│   │   ├── entity/                       # Room Entities / extensions
│   │   │   ├── BudgetEntity.kt           # 예산 Entity
│   │   │   ├── CategoryMappingEntity.kt  # 카테고리 매핑 Entity
│   │   │   ├── ChatEntity.kt            # 채팅 메시지 Entity
│   │   │   ├── ChatSessionEntity.kt     # 채팅 세션(방) Entity
│   │   │   ├── ExpenseEntity.kt         # 지출 Entity
│   │   │   ├── IncomeEntity.kt          # 수입 Entity
│   │   │   ├── OwnedCardEntity.kt       # 소유 카드 Entity (화이트리스트)
│   │   │   ├── SmsExclusionKeywordEntity.kt # SMS 제외 키워드 Entity (블랙리스트)
│   │   │   ├── SmsPatternEntity.kt      # SMS 패턴 캐시 Entity
│   │   │   ├── SmsRegexRuleEntity.kt   # SMS regex 룰 Entity (Fast Path)
│   │   │   └── StoreEmbeddingEntity.kt  # 가게명 벡터 임베딩 Entity
│   │   ├── OwnedCardRepository.kt        # 카드 화이트리스트 + CardNameNormalizer 연동
│   │   └── SmsExclusionRepository.kt     # SMS 제외 키워드 관리
│   │
│   ├── datastore/                        # DataStore 설정
│   │   └── SettingsDataStore.kt          # 앱 설정 저장소
│   │
│   ├── di/                               # Hilt DI 모듈
│   │   ├── DatabaseModule.kt             # DB 의존성 주입
│   │   ├── FirebaseModule.kt             # Firebase 의존성 주입 (Analytics, Crashlytics, RTDB)
│   │   ├── NetworkModule.kt              # 네트워크 의존성 주입
│   │   └── RepositoryModule.kt           # Repository 의존성 주입
│   │
│   ├── firebase/                         # Firebase 관련
│   │   ├── AnalyticsEvent.kt             # 화면/클릭 이벤트 상수
│   │   ├── AnalyticsHelper.kt            # Firebase Analytics 래퍼 (@Singleton)
│   │   ├── CrashlyticsHelper.kt          # Crashlytics 래퍼
│   │   ├── ForceUpdateChecker.kt         # 강제 업데이트 판정
│   │   ├── PremiumConfig.kt              # 서버 설정 data class (API 키 풀, 모델 설정)
│   │   └── PremiumManager.kt             # Firebase RTDB 설정 실시간 감시
│   │
│   ├── model/                            # 공통 모델
│   │   ├── Category.kt                   # 지출 카테고리 Enum (18개)
│   │   └── SmsAnalysisResult.kt          # SMS 분석 결과 모델
│   │
│   ├── similarity/                       # 유사도 정책 (SSOT)
│   │   ├── SimilarityPolicy.kt           # 유사도 정책 인터페이스
│   │   ├── SimilarityProfile.kt          # 유사도 프로필 데이터 클래스
│   │   ├── SmsPatternSimilarityPolicy.kt # SMS 패턴 유사도 임계값
│   │   ├── StoreNameSimilarityPolicy.kt  # 가게명 유사도 임계값
│   │   └── CategoryPropagationPolicy.kt  # 카테고리 전파 정책
│   │
│   ├── theme/                            # Compose 테마
│   │   ├── Color.kt                      # 색상 정의
│   │   ├── Theme.kt                      # 테마 정의
│   │   └── Type.kt                       # 타이포그래피 정의
│   │
│   ├── ui/                               # 공통 UI
│   │   ├── AppSnackbarBus.kt             # 전역 스낵바 이벤트 버스
│   │   ├── ClassificationState.kt        # 분류 상태 관리
│   │   └── component/                    # 공통 UI 컴포넌트 (11개)
│   │       ├── CategoryIcon.kt           # 카테고리 이모지 아이콘
│   │       ├── ExpenseItemCard.kt        # 지출 항목 카드
│   │       ├── settings/
│   │       │   ├── SettingsItemCompose.kt    # 설정 항목 Composable
│   │       │   ├── SettingsItemInfo.kt       # 설정 항목 Contract
│   │       │   └── SettingsSectionCompose.kt # 설정 섹션 Composable
│   │       ├── tab/
│   │       │   ├── SegmentedTabInfo.kt       # 탭 Contract (아이콘 지원)
│   │       │   └── SegmentedTabRowCompose.kt # 라운드 버튼 스타일 탭
│   │       └── transaction/
│   │           ├── card/
│   │           │   ├── TransactionCardCompose.kt  # 지출/수입 통합 카드
│   │           │   └── TransactionCardInfo.kt     # 거래 카드 Contract
│   │           └── header/
│   │               ├── TransactionGroupHeaderCompose.kt # 그룹 헤더
│   │               └── TransactionGroupHeaderInfo.kt    # 그룹 헤더 Contract
│   │
│   ├── sms/                              # SMS 통합 패키지 (배치 + 실시간 + 보조 유틸, 25개)
│   │   ├── SmsSyncCoordinator.kt         # 배치 동기화 외부 진입점
│   │   ├── SmsReaderV2.kt                # SMS/MMS/RCS 통합 읽기
│   │   ├── SmsInstantProcessor.kt        # 실시간 1건 처리
│   │   ├── SmsPreFilter.kt               # Step 0 사전 필터링
│   │   ├── SmsIncomeFilter.kt            # Step 1 PAYMENT/INCOME/SKIP 분류
│   │   ├── SmsRegexRuleMatcher.kt        # Step 1.5 sender regex Fast Path
│   │   ├── SmsPipeline.kt                # Step 2~5 오케스트레이션
│   │   ├── SmsPatternMatcher.kt          # 벡터 매칭 + regex 파싱
│   │   ├── SmsGroupClassifier.kt         # 그룹핑 + LLM + regex 생성
│   │   ├── GeminiSmsExtractor.kt         # Gemini LLM 추출
│   │   ├── SmsParser.kt / SmsFilter.kt   # 공용 파서/발신자 필터
│   │   ├── SmsEmbeddingService.kt / VectorSearchEngine.kt # 임베딩/유사도 유틸
│   │   ├── DeletedSmsTracker.kt          # 삭제 SMS 재삽입 방지
│   │   ├── SmsChannelProbeCollector.kt   # DEBUG 채널 진단 수집
│   │   └── RemoteSmsRule*.kt / SmsRegexRule*.kt # Fast Path 룰/시드/동기화
│   │
│   └── util/                             # 유틸리티 (12개)
│       ├── CategoryReferenceProvider.kt  # 카테고리 참조 데이터 제공
│       ├── StoreNameGrouper.kt           # 가게명 그룹화
│       ├── StoreAliasManager.kt          # 가게명 별칭 관리
│       ├── CardNameNormalizer.kt         # 카드사 명칭 정규화 (25+)
│       ├── ChatContextBuilder.kt         # 채팅 컨텍스트 빌더
│       ├── DataQueryParser.kt            # 데이터 쿼리 파서 (17 쿼리 + 12 액션)
│       ├── DateParser.kt                 # 날짜 파서
│       ├── DateUtils.kt                  # 날짜/시간 유틸리티
│       ├── DpTextUnit.kt                 # fontScale 무관 고정 텍스트 크기
│       ├── DataBackupManager.kt          # 데이터 백업/복원 관리
│       ├── DataRefreshEvent.kt           # 데이터 새로고침 이벤트
│       └── GoogleDriveHelper.kt          # Google Drive API 헬퍼
│
├── feature/                              # 기능별 모듈
│   ├── home/                             # 홈 탭
│   │   ├── data/ (9개)
│   │   │   ├── ExpenseRepository.kt          # 지출 Repository
│   │   │   ├── IncomeRepository.kt           # 수입 Repository
│   │   │   ├── CategoryRepository.kt         # 카테고리 매핑 Repository
│   │   │   ├── StoreEmbeddingRepository.kt   # 가게 임베딩 Repository (인터페이스)
│   │   │   ├── StoreEmbeddingRepositoryImpl.kt # 가게 임베딩 Repository 구현체
│   │   │   ├── GeminiCategoryRepository.kt   # Gemini 카테고리 분류 (인터페이스)
│   │   │   ├── GeminiCategoryRepositoryImpl.kt # Gemini 카테고리 분류 구현체
│   │   │   ├── CategoryClassifierService.kt  # 4-tier 카테고리 분류 서비스 (인터페이스)
│   │   │   └── CategoryClassifierServiceImpl.kt # 4-tier 분류 서비스 구현체
│   │   └── ui/
│   │       ├── HomeScreen.kt                 # 홈 화면 UI
│   │       └── HomeViewModel.kt              # 홈 ViewModel
│   │
│   ├── history/                          # 내역 탭
│   │   └── ui/ (6개)
│   │       ├── HistoryScreen.kt          # 내역 화면 UI (목록/달력/수입)
│   │       ├── HistoryViewModel.kt       # 내역 ViewModel
│   │       ├── HistoryCalendar.kt        # 달력 뷰 컴포넌트
│   │       ├── HistoryDialogs.kt         # 상세/수정/삭제 다이얼로그
│   │       ├── HistoryFilter.kt          # 필터 BottomSheet + 초기화 버튼
│   │       └── HistoryHeader.kt          # 기간 요약 헤더 (PeriodSummaryCard)
│   │
│   ├── chat/                             # AI 상담 탭
│   │   ├── data/ (5개)
│   │   │   ├── ChatPrompts.kt            # AI 프롬프트 템플릿
│   │   │   ├── ChatRepository.kt         # 채팅 Repository 인터페이스
│   │   │   ├── ChatRepositoryImpl.kt     # 채팅 Repository 구현체
│   │   │   ├── GeminiRepository.kt       # Gemini API Repository (인터페이스)
│   │   │   └── GeminiRepositoryImpl.kt   # Gemini API Repository 구현체
│   │   └── ui/ (4개)
│   │       ├── ChatScreen.kt             # 채팅 화면 UI
│   │       ├── ChatViewModel.kt          # 채팅 ViewModel (~1717줄)
│   │       ├── ChatComponents.kt         # 채팅 공통 UI 컴포넌트
│   │       └── ChatRoomListView.kt       # 채팅방 리스트 뷰
│   │
│   ├── settings/                         # 설정 탭
│   │   └── ui/ (5개)
│   │       ├── SettingsScreen.kt         # 설정 화면 UI
│   │       ├── SettingsViewModel.kt      # 설정 ViewModel
│   │       ├── SettingsPreferenceDialogs.kt # 수입/시작일/API키 다이얼로그
│   │       ├── SettingsDataDialogs.kt    # 데이터 관리 다이얼로그
│   │       └── SettingsInfoDialogs.kt    # 정보/정책 다이얼로그
│   │
│   ├── categorysettings/                 # 카테고리 설정
│   │   └── ui/
│   │       ├── CategorySettingsActivity.kt
│   │       ├── CategorySettingsScreen.kt
│   │       └── CategorySettingsViewModel.kt
│   │
│   ├── smssettings/                      # 문자 설정
│   │   └── ui/
│   │       ├── SmsSettingsActivity.kt
│   │       ├── SmsSettingsScreen.kt
│   │       └── SmsSettingsViewModel.kt
│   │
│   ├── storerulesettings/                # 거래처 규칙 설정
│   │   └── ui/
│   │       ├── StoreRuleSettingsActivity.kt
│   │       ├── StoreRuleSettingsScreen.kt
│   │       └── StoreRuleSettingsViewModel.kt
│   │
│   ├── transactionedit/                  # 거래 편집/추가
│   │   └── ui/
│   │       ├── TransactionEditActivity.kt
│   │       ├── TransactionEditScreen.kt
│   │       ├── TransactionEditViewModel.kt
│   │       └── model/
│   │           └── TransactionType.kt
│   │
│   ├── transactionlist/                  # 날짜별 거래 목록
│   │   └── ui/
│   │       ├── TransactionDetailListActivity.kt
│   │       ├── TransactionDetailListScreen.kt
│   │       └── TransactionDetailListViewModel.kt
│   │
│   ├── categorydetail/                   # 카테고리 상세
│   │   └── ui/
│   │       ├── CategoryDetailActivity.kt
│   │       ├── CategoryDetailScreen.kt
│   │       ├── CategoryDetailViewModel.kt
│   │       └── model/
│   │           ├── CategoryDetailPageData.kt
│   │           └── CategorySpendingTrendInfo.kt
│   │
│   ├── intro/                            # 인트로/권한
│   │   └── ui/
│   │       ├── IntroActivity.kt
│   │       ├── OnboardingScreen.kt
│   │       └── PermissionScreen.kt
│   │
│   └── splash/                           # 스플래시
│       └── ui/
│           └── SplashScreen.kt           # 스플래시 화면
│
├── navigation/                           # 네비게이션
│   ├── NavGraph.kt                       # Navigation Graph
│   ├── Screen.kt                         # Screen 정의
│   └── BottomNavItem.kt                  # 하단 탭 아이템
│
└── receiver/                             # 실시간 수신/알림 보완 경로
    ├── SmsReceiver.kt                    # SMS 수신 리시버 (goAsync)
    ├── MmsContentObserver.kt             # MMS provider 실시간 감시
    ├── RcsContentObserver.kt             # RCS provider 실시간 감시
    ├── NotificationTransactionService.kt # 메시지 앱 알림 기반 실시간 보완
    └── NotificationContentParser.kt      # 메시지 앱 알림 파서
```

**총 217개 .kt 파일**

### Feature 패키지 원칙

- 화면 전용 `Activity`, `ViewModel`, `UiState`, 보조 모델은 각 feature의 `ui/` 또는 `ui/model/` 아래에 둡니다.
- 여러 화면이 공유하는 저장소/서비스만 `data/` 또는 `core/`에 둡니다.
- `feature` 루트는 기능 이름 경계만 나타내고, 실행 가능한 화면 파일은 가능한 한 `ui/`에 모읍니다.

## 주요 기능

### 1. 홈 (Home)
- 월간 수입/지출 현황 표시
- 카테고리별 지출 요약 (원형 차트)
- 오늘 내역 표시
- SMS 동기화 (증분 동기화, 배치 처리)
- 미분류 항목 자동 카테고리 분류
- 월 이동 (이전/다음 월)
- 커스텀 월 시작일 지원 (월급일 기준)

### 2. 내역 (History)
- 전체 지출/수입 내역 조회 (목록/달력/수입 3가지 보기 모드)
- 월별 필터링
- 필터 BottomSheet (정렬/거래유형/카테고리) + 초기화 버튼
- 달력 뷰 (일별 합계, 무지출일 표시)
- 일별/월별 합계
- 지출 수정 (카테고리, 메모, 금액, 가게명)
- 지출 삭제
- 홈→내역 카테고리 네비게이션

### 3. AI 상담 (Chat)
- Gemini AI 기반 재무 상담 (3-step pipeline)
- 채팅방 관리 (생성, 삭제, 제목 편집)
- DB 쿼리 자동 실행 (지출 조회, 분석)
- 채팅 액션 지원 (삭제, 추가, 수정, SMS 제외 등 12종)
- 대화 요약 기능 (컨텍스트 유지)

### 4. 설정 (Settings)
- 월 수입 / 월 시작일 설정
- Gemini API 키 설정
- SMS 동기화 / 카테고리 분류 (진행률 표시)
- 소유 카드 관리 (화이트리스트)
- SMS 제외 키워드 관리 (블랙리스트)
- 데이터 내보내기 (JSON/CSV, 필터 지원)
- Google Drive 백업/복원
- 로컬 파일 복원
- 중복 데이터 삭제
- 전체 데이터 삭제
- 버전 정보 / 개인정보 처리방침

## 핵심 시스템

### SMS 파싱 (sms — 3-tier Fast Path + Vector + LLM)

**배치 동기화** (메인 경로 — sms):
```
SMS 읽기 (SmsReaderV2) → SmsSyncCoordinator
  → SmsPreFilter → SmsIncomeFilter (PAYMENT/INCOME/SKIP)
  → PAYMENT만 SmsRegexRuleMatcher (sender Fast Path)
  → Fast Path miss만 SmsPipeline: 템플릿+임베딩 → 벡터매칭 → 그룹+LLM
  → INCOME은 SmsIncomeParser로 저장
```

| 단계 | 엔진 (sms) | 비용 | 설명 |
|------|-------------|------|------|
| 사전 필터 | SmsPreFilter | 0 | 비결제 SMS 제거 (60+ 키워드) |
| 수입 분류 | SmsIncomeFilter | 0 | PAYMENT/INCOME/SKIP 키워드 분류 |
| Fast Path | SmsRegexRuleMatcher | 0 | 결제 후보 sender 기반 regex 룰 우선 매칭 |
| 벡터 매칭 | SmsPatternMatcher | 0 | 기존 패턴 DB에서 코사인 유사도 매칭 |
| LLM 추출 | SmsGroupClassifier | API | 그룹핑 → Gemini LLM 배치 추출 → regex 생성 |

**실시간 수신**:
```
SMS → SmsReceiver → SmsInstantProcessor
MMS → MmsContentObserver → SmsInstantProcessor
RCS(provider) → RcsContentObserver → SmsInstantProcessor
RCS/비즈메시지(cold start) → NotificationTransactionService → 최근 provider row 조회 → SmsInstantProcessor
```

### 카테고리 분류 (4-tier)

| Tier | 방식 | 설명 |
|------|------|------|
| 1 | Room DB 정확 매칭 | CategoryMappingDao에서 가게명 정확 매칭 |
| 1.5a | 벡터 유사도 (개별) | 가게명 임베딩 유사도로 카테고리 추론 |
| 1.5b | 벡터 유사도 (그룹 투표) | 유사 가게명 그룹의 다수결 투표 |
| 2 | 로컬 키워드 | SmsParser.inferCategory 키워드 매칭 |
| 3 | Gemini 배치 호출 | GeminiCategoryRepository 배치 분류 |

### AI 채팅 (3-step pipeline)

```
사용자 질문 → Step 1: QUERY_ANALYZER → Step 2: DB 조회/액션 실행 → Step 3: FINANCIAL_ADVISOR → 답변
```

| Step | 모델 | 프롬프트 위치 | 역할 |
|------|------|-------------|------|
| 1 | gemini-2.5-pro | string_prompt.xml (QUERY_ANALYZER) | 질문 → 쿼리/액션 JSON 변환 (17 쿼리 + 12 액션) |
| 2 | - | ChatViewModel.kt | DB 조회, 액션 실행, ANALYTICS 분석 |
| 3 | gemini-2.5-pro | string_prompt.xml (FINANCIAL_ADVISOR) | 데이터 기반 최종 답변 생성 |

## AI 프롬프트 위치

> 모든 시스템 프롬프트는 [`res/values/string_prompt.xml`](../app/src/main/res/values/string_prompt.xml)에서 관리

| 프롬프트 | XML key | 모델 | 목적 |
|---------|---------|------|------|
| 쿼리 분석기 | `prompt_query_analyzer_system` | gemini-2.5-pro | 사용자 질문 → 쿼리/액션 JSON |
| 재무 상담사 | `prompt_financial_advisor_system` | gemini-2.5-pro | 재무 상담 답변 생성 |
| 대화 요약 | `prompt_summary_system` | gemini-2.5-flash | 대화 요약 |
| SMS 추출 (단일) | `prompt_sms_extract_system` | gemini-2.5-flash-lite | SMS → 결제 정보 추출 |
| SMS 추출 (배치) | `prompt_sms_batch_extract_system` | gemini-2.5-flash-lite | 다건 SMS 배치 추출 |
| 카테고리 분류 | `prompt_category_classification` | gemini-2.5-flash-lite | 가게명 → 카테고리 분류 |

## 유사도 정책 (core/similarity/)

모든 임계값은 `core/similarity/` 패키지에서 SSOT로 관리:

| 정책 | 판정 | 캐시 재사용 | 비결제 캐시 |
|------|------|-----------|-----------|
| SmsPatternSimilarityPolicy | 0.92 | 0.95 | 0.97 |
| StoreNameSimilarityPolicy | 0.88 (그룹핑) | 0.90 (전파) | 0.92 (자동적용) |
| CategoryPropagationPolicy | confidence < 0.6 차단 | - | - |

## 데이터 흐름

### SMS → 지출 저장 (배치 동기화 — sms)
```
HomeViewModel.syncSmsV2()
  → SmsReaderV2.readAllMessagesByDateRange() → List<SmsInput>
  → 중복 제거 (expenseRepository + incomeRepository smsId)
  → SmsSyncCoordinator.process()
    → SmsPreFilter (비결제 제거)
    → SmsIncomeFilter (PAYMENT/INCOME/SKIP 분류)
    → SmsRegexRuleMatcher (PAYMENT Fast Path)
    → SmsPipeline (Fast Path 미매칭 결제 후보)
      → SmsTemplateEngine (템플릿화 + 임베딩)
      → SmsPatternMatcher (벡터 매칭 + regex 파싱)
      → SmsGroupClassifier (그룹핑 + LLM 추출 + regex 생성)
  → saveExpenses() / saveIncomes(SmsIncomeParser)
  → CategoryClassifierService (4-tier 카테고리 분류)
  → UI 반영
```

### SMS/MMS/RCS → 지출 저장 (실시간 수신)
```
실시간 이벤트 (SmsReceiver / MmsContentObserver / RcsContentObserver / NotificationTransactionService)
  → SmsInstantProcessor.processAndSave()
  → sender 필터 + PreFilter + IncomeFilter
  → 지출은 regex 매칭 성공 시 ExpenseEntity 저장
  → 수입은 SmsIncomeParser로 IncomeEntity 저장
  → 거래 알림 표시
  → 미매칭/실패 시 DataRefreshEvent.SMS_RECEIVED
  → 이후 syncSmsV2에서 Vector/LLM 폴백
```

### 채팅 질의
```
사용자 질문
  → ChatViewModel.sendMessage()
  → QUERY_ANALYZER (Gemini) → JSON {queries, actions}
  → DataQueryParser.executeQuery() / executeAction()
  → FINANCIAL_ADVISOR (Gemini) + DB 결과
  → 최종 답변 → UI
```

## 기술 스택

| 분류 | 기술 | 버전 |
|------|------|------|
| **UI** | Jetpack Compose, Material 3 | BOM 2024.11.00 |
| **Architecture** | MVVM, Repository Pattern | - |
| **DI** | Hilt | 2.50 |
| **Database** | Room | 2.6.1 |
| **Preferences** | DataStore | 1.0.0 |
| **Network** | OkHttp | 4.12.0 |
| **AI** | Google Generative AI (Gemini) | 0.9.0 |
| **Cloud** | Google Drive API, Play Services Auth | v3, 21.0.0 |
| **Analytics** | Firebase Analytics, Crashlytics | BOM |
| **Ads** | Google AdMob (Rewarded) | - |
| **Navigation** | Compose Navigation | 2.7.7 |
| **Animation** | Lottie | 6.3.0 |
| **Serialization** | Gson | 2.10.1 |
| **Language** | Kotlin | 1.9.22 |

## 빌드 설정

- Gradle: 8.5
- AGP: 8.2.2
- Kotlin: 1.9.22
- KSP: 1.9.22-1.0.17
- compileSdk: 34
- minSdk: 26
- targetSdk: 34
- JVM Target: 17
- R8 (ProGuard): release 빌드에서 minify + shrinkResources 활성화
