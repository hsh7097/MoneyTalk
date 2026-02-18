# 머니톡 (MoneyTalk) - 개발 로그

> 개발 과정과 변경 사항을 기록하는 문서입니다.

---

## 2026-02-18 - ProGuard(R8) 활성화 + Firebase Analytics 트래킹

### 작업 내용

#### 1. ProGuard(R8) 활성화
- `isMinifyEnabled = true`, `isShrinkResources = true` 릴리스 빌드 설정
- proguard-rules.pro 버그 수정: `core.db.entity` → `core.database.entity` 경로 오류
- Gson 직렬화 모델 keep 규칙 17개 추가 (DataQueryRequest, BackupData 등)
- Apache HTTP/javax.naming/ietf.jgss dontwarn 규칙 추가 (Google Drive API 의존)
- Gradle JVM heap 1024m → 2048m (R8 OOM 방지)

#### 2. Firebase Analytics 트래킹
- AnalyticsHelper.kt 신규 (Hilt @Singleton, logScreenView/logClick/logEvent)
- AnalyticsEvent.kt 신규 (화면 4종 + 클릭 7종 상수)
- FirebaseModule에 FirebaseAnalytics DI provider 추가
- MainActivity에서 LaunchedEffect(currentRoute)로 화면 PV 중앙 집중 트래킹
- 4개 ViewModel에 클릭 이벤트 트래킹 (Home: syncSms, Chat: sendChat, History: categoryFilter, Settings: backup/restore/theme)

### 변경 파일
- `app/build.gradle.kts` — isMinifyEnabled=true, isShrinkResources=true
- `app/proguard-rules.pro` — 경로 버그 수정 + Gson 모델 keep + Apache dontwarn
- `gradle.properties` — jvmargs 2048m
- `AnalyticsHelper.kt` — 신규
- `AnalyticsEvent.kt` — 신규
- `FirebaseModule.kt` — provideFirebaseAnalytics 추가
- `MainActivity.kt` — analyticsHelper 주입, PV 트래킹
- `HomeViewModel.kt` — analyticsHelper 주입, syncSms 이벤트
- `ChatViewModel.kt` — analyticsHelper 주입, sendChat 이벤트
- `HistoryViewModel.kt` — analyticsHelper 주입, categoryFilter 이벤트
- `SettingsViewModel.kt` — analyticsHelper 주입, backup/restore/theme 이벤트

---

## 2026-02-18 - Firebase RTDB 원격 설정 마이그레이션

### 작업 내용

#### 1. Gemini API 키 풀링
- PremiumConfig에 `geminiApiKeys: List<String>` 배열 필드 추가
- PremiumManager에 라운드로빈 방식 `getGeminiApiKey()` 구현 (AtomicInteger 카운터)
- 기존 단일 키(`geminiApiKey`) 하위호환 유지

#### 2. Gemini 모델명 원격 관리
- GeminiModelConfig data class 신설 (8개 모델: queryAnalyzer, financialAdvisor, summary 등)
- PremiumConfig에 `modelConfig: GeminiModelConfig` 필드 추가
- PremiumManager에서 Firebase RTDB `/config/models/` 파싱
- GeminiRepository/CategoryClassifier/SmsExtractor 등 11개 파일에서 하드코딩 모델명 → 원격 설정 참조로 전환

#### 3. BuildConfig 정리 + 버전 업
- BuildConfig.GEMINI_API_KEY 폐기 (local.properties에서 제거)
- 버전 1.1.0 (versionCode=2, versionName="1.1.0")

### 변경 파일
- `PremiumConfig.kt` — geminiApiKeys, GeminiModelConfig 추가
- `PremiumManager.kt` — 키 풀링 + 모델명 파싱
- `GeminiRepositoryImpl.kt` — 원격 모델명 사용
- `CategoryClassifierServiceImpl.kt` — 원격 모델명 사용
- `GeminiCategoryRepositoryImpl.kt` — 원격 모델명 사용
- `GeminiSmsExtractor.kt` — 원격 모델명 사용
- `SmsEmbeddingService.kt` — 원격 모델명 사용
- `HomeViewModel.kt` — 원격 모델명 사용
- `app/build.gradle.kts` — 버전 1.1.0, GEMINI_API_KEY 제거
- `proguard-rules.pro` — GeminiModelConfig keep 규칙
- `FirebaseModule.kt` — FirebaseDatabase DI 추가

---

## 2026-02-18 - 알파 배포 준비

### 작업 내용

#### 1. 강제 업데이트 시스템
- Firebase RTDB `min_version_code` vs `BuildConfig.VERSION_CODE` 비교
- ForceUpdateChecker 싱글톤 (Flow 기반, distinctUntilChanged)
- MainActivity에 닫기 불가 AlertDialog (업데이트/종료만 가능)
- PremiumConfig에 minVersionCode/minVersionName/forceUpdateMessage 필드 추가
- PremiumManager 양쪽(startObservingConfig/fetchConfigOnce) 파싱 추가
- Firebase RTDB `/config` 노드에 3개 필드 등록 완료

#### 2. 버전 변경
- versionName "1.0" → "1.0.0", versionCode 1 유지

#### 3. 개인정보처리방침 웹 페이지
- `docs/privacy-policy.html` 생성 (한국어/영어 전환)
- Google Play 알파 배포용 URL 제공 예정 (GitHub Pages 설정 필요)

#### 4. ProGuard 규칙
- Hilt/Room/Gson/Firebase/Gemini/AdMob/OkHttp/Coroutines/Compose/Lottie/DataStore 전체 커버

#### 5. GIT_CONVENTION.md 강화
- 커밋 본문 권장 템플릿 (문제→원인→조치→검증) 추가
- Kotlin Android 특화 체크리스트 추가 (MVVM, Flow, Compose, Hilt)
- 코드 식별자 영어 사용 규칙 명시

### 변경 파일
- `app/build.gradle.kts` — versionName 변경
- `app/proguard-rules.pro` — ProGuard 규칙 전면 작성
- `ForceUpdateChecker.kt` — 신규
- `PremiumConfig.kt` — 3개 필드 추가
- `PremiumManager.kt` — 파싱 추가
- `MainActivity.kt` — 강제 업데이트 다이얼로그 연동
- `strings.xml` (ko/en) — 강제 업데이트 문자열
- `docs/privacy-policy.html` — 신규
- `docs/GIT_CONVENTION.md` — 커밋 가이드 반영

---

## 2026-02-14~ - Compose Stability + 리팩토링 + 기능 개선

### 작업 내용

#### 1. 대형 파일 분할 + Repository 추상화
- Repository 인터페이스/구현체 분리 (GeminiRepository, ChatRepository, StoreEmbeddingRepository, GeminiCategoryRepository, CategoryClassifierService)
- 하드코딩 문자열 → strings.xml 이전
- Hilt binding 수정 (GeminiRepository 인터페이스 바인딩 추가)

#### 2. Compose Stability 최적화
- @Immutable/@Stable 어노테이션 적용 (데이터 클래스, sealed interface 등)
- 릴리스 DB 안전성 개선 (fallbackToDestructiveMigration 대신 안전한 마이그레이션)
- 코드 리뷰 리포트 파일 제거 (레거시 스냅샷)

#### 3. 홈→내역 카테고리 네비게이션
- Screen.History에 `category` optional 파라미터 추가
- 홈에서 카테고리 클릭 시 해당 카테고리 필터가 적용된 내역 화면으로 이동
- AI 인사이트를 별도 컴포넌트로 분리

#### 4. 달력 뷰 카테고리 필터 버그 수정
- dailyTotals/monthlyTotal에 카테고리 필터가 미적용되던 버그 수정

#### 5. AI 인사이트 전월 비교 데이터 추가
- 프롬프트에 전월 카테고리별 비교 데이터 포함

#### 6. 가맹점 일괄 카테고리 업데이트
- 카테고리 변경 시 동일 가맹점 전체 일괄 업데이트
- updateExpenseCategory에서 미사용 expenseId 파라미터 제거

#### 7. safe-commit 스킬 추가
- `.claude/skills/safe-commit/SKILL.md` 추가 (셀프 리뷰 후 안전 커밋)

### 변경 파일
- `HomeScreen.kt` — AI 인사이트 분리, 카테고리 네비게이션
- `HomeViewModel.kt` — AI 인사이트 전월 비교 데이터, 인사이트 입력 해시 가드
- `HistoryScreen.kt` — 카테고리 파라미터 수신, 필터 적용
- `HistoryViewModel.kt` — 카테고리 필터 적용 달력 뷰 수정
- `Screen.kt` — History 라우트에 category 파라미터
- `NavGraph.kt` — category argument 전달
- `GeminiRepository.kt` / `GeminiRepositoryImpl.kt` — 인터페이스/구현 분리
- `ChatRepository.kt` / `ChatRepositoryImpl.kt` — 인터페이스/구현 분리
- `StoreEmbeddingRepository.kt` / `StoreEmbeddingRepositoryImpl.kt` — 인터페이스/구현 분리
- `CategoryClassifierService.kt` / `CategoryClassifierServiceImpl.kt` — 인터페이스/구현 분리
- `GeminiCategoryRepository.kt` / `GeminiCategoryRepositoryImpl.kt` — 인터페이스/구현 분리
- `RepositoryModule.kt` — Hilt 바인딩 추가
- `string_prompt.xml` — AI 인사이트 프롬프트 업데이트
- `strings.xml` — 하드코딩 문자열 이전
- `.claude/skills/safe-commit/SKILL.md` — 신규

---

## 2026-02-14 - Phase 2 완료 + History 필터 초기화 버튼

### 작업 내용

#### 1. History 필터 초기화 버튼
- FilterBottomSheet 상단에 "초기화" 텍스트 버튼 추가
- 필터가 기본값(날짜순 + 지출/수입 모두 + 전체 카테고리)이 아닐 때만 표시
- 클릭 시 모든 필터를 기본값으로 리셋

#### 2. Phase 2-A: 부트스트랩 모드 게이트 제거
- `isBootstrap` 로직은 이미 이전에 제거 완료 확인
- 미사용 `BOOTSTRAP_THRESHOLD = 10` 상수 제거

#### 3. Phase 2-B: 캐시 재사용 임계값 검토
- `NON_PAYMENT_CACHE_THRESHOLD` 0.97 → 0.95 완화 검토
- 결론: 0.97 유지 (payment autoApply=0.95와 동일하면 오분류 리스크)

#### 4. Phase 2-C: 벡터 학습 실패 시 사용자 알림
- HomeViewModel의 batchLearnFromRegexResults catch 블록에 스낵바 알림 추가
- `AppSnackbarBus.show("벡터 패턴 학습 일부 실패 (다음 동기화 시 재시도)")`

#### 5. Phase 2-D: 채팅 카테고리 변경 시 캐시 무효화
- ChatViewModel에 CategoryReferenceProvider 생성자 주입
- UPDATE_CATEGORY / UPDATE_CATEGORY_BY_STORE / UPDATE_CATEGORY_BY_KEYWORD 성공 시 `categoryReferenceProvider.invalidateCache()` 호출

### 변경 파일
- `HistoryFilter.kt` — 필터 초기화 버튼 + isFilterDefault() 헬퍼
- `strings.xml` — `history_filter_reset` 문자열 추가
- `HybridSmsClassifier.kt` — BOOTSTRAP_THRESHOLD 상수 제거
- `HomeViewModel.kt` — 벡터 학습 실패 시 스낵바 알림
- `ChatViewModel.kt` — CategoryReferenceProvider 주입 + invalidateCache() 호출

---

## 2026-02-13 - 채팅 프롬프트 Karpathy Guidelines 적용 + Clarification 루프

### 작업 내용

#### 1. Query Analyzer 프롬프트 업데이트
- `[clarification 응답 규칙]` 섹션 추가: 모호한 질문 시 추측 대신 확인 질문 반환
- clarification 사용/미사용 기준 명시 (대화 맥락 유무, 기간 생략 가능 여부)
- 기존 "queries/actions 비어있지 않게" 규칙 제거 (clarification 응답 허용)

#### 2. Financial Advisor 프롬프트 업데이트
- `[수치 정확성 필수 규칙]` 섹션 추가 (Karpathy "Simplicity First" 적용)
- 직접 계산/비율 계산/교차 계산 금지, 불확실 시 인정 규칙

#### 3. Clarification 루프 구현
- `DataQueryRequest`에 `clarification: String?` 필드 + `isClarification` computed property 추가
- `ChatViewModel.sendMessage()`에 clarification 분기 처리
- clarification이면 확인 질문을 AI 응답으로 저장, 쿼리/답변 생성 건너뜀

### 변경 파일
- `res/values/string_prompt.xml` — query_analyzer, financial_advisor 프롬프트
- `core/util/DataQueryParser.kt` — DataQueryRequest clarification 필드
- `feature/chat/ui/ChatViewModel.kt` — clarification 분기 처리

---

## 2026-02-12 - History UI 개편 및 하단 네비게이션 개선

### 작업 내용

#### 1. History 필터 BottomSheet 전환
- 기존 FilterPanel(가로 3칩 탭) → ModalBottomSheet로 전면 교체
- 카드 필터 제거 (ViewModel에서 `selectedCardName`, `cardNames`, `loadCardNames()` 등 삭제)
- 수입 탭을 BottomSheet 내 거래유형 체크박스(지출/수입)로 통합
- BottomSheet 내부: 정렬(날짜순/금액순/사용처순) + 거래유형(지출/수입) + 카테고리 그리드

#### 2. PeriodSummaryCard 레이아웃 변경
- Card 래퍼 제거 → 단순 Row 구조
- 왼쪽: 날짜 네비게이션 (줄넘김 형태, HorizontalDivider 구분선)
- 오른쪽: 지출/수입 금액 오른쪽 정렬 (라벨 고정 너비 28dp, 금액 120dp)
- 필터 적용 시 상단 총 수입/지출도 필터 기준으로 반영 (filteredExpenseTotal/filteredIncomeTotal)

#### 3. FilterTabRow 개편
- SegmentedTabRow에 아이콘 지원 추가 (목록: List, 달력: DateRange)
- 필터 버튼: 아이콘+텍스트 형태로 탭 바로 옆에 배치 (마진 8dp)
- 검색/추가 아이콘은 오른쪽에 배치

#### 4. 하단 NavigationBar 컴팩트화
- 높이 80dp → 64dp로 축소
- 아이콘+라벨을 Column으로 직접 배치 (label=null), Box로 세로 중앙정렬
- 라벨: DpTextUnit(12dp) 적용으로 fontScale 무관 고정 크기

#### 5. DpTextUnit 유틸 추가
- `core/util/DpTextUnit.kt`: fontScale을 제거한 고정 텍스트 크기
- `Dp.toDpTextUnit`, `Int.toDpTextUnit` 확장 프로퍼티

#### 6. 거래 목록 시간순 정렬
- 같은 날짜 내 수입/지출 통합 최신순(dateTime desc) 정렬
- 기존: 수입이 항상 최상위 → 변경: 시간순 혼합 정렬

### 변경 파일
- `HistoryScreen.kt` - PeriodSummaryCard, FilterTabRow, FilterBottomSheet
- `HistoryViewModel.kt` - 필터 로직, buildDateDescItems 시간순 정렬
- `SegmentedTabInfo.kt` - icon 프로퍼티 추가
- `SegmentedTabRowCompose.kt` - 아이콘 렌더링, 컴팩트 사이즈
- `MainActivity.kt` - NavigationBar 컴팩트화
- `DpTextUnit.kt` - 신규 유틸
- `strings.xml` - 필터 관련 문자열 추가

---

## 2026-02-05 - 프로젝트 초기 설정

### 작업 내용

#### 1. 프로젝트 생성
- Android Studio에서 Empty Compose Activity로 생성
- Package: `com.sanha.moneytalk`
- Min SDK: 26 (Android 8.0)

#### 2. 의존성 추가
`gradle/libs.versions.toml` 및 `app/build.gradle.kts` 수정

추가된 라이브러리:
- Room 2.6.1
- Hilt 2.50
- Retrofit 2.9.0 + OkHttp 4.12.0
- Navigation Compose 2.7.7
- Coroutines 1.7.3
- DataStore 1.0.0

#### 3. 패키지 구조 생성
Clean Architecture 기반 구조:
- `data/` - 데이터 레이어 (local, remote, repository)
- `domain/` - 도메인 레이어 (model, usecase)
- `presentation/` - UI 레이어 (screens, viewmodels)
- `di/` - 의존성 주입
- `util/` - 유틸리티

#### 4. Room Database 구현
- `ExpenseEntity` - 지출 내역
- `IncomeEntity` - 수입 내역
- `BudgetEntity` - 예산
- `ChatEntity` - AI 대화 기록

각 Entity에 대한 DAO 구현 완료.

#### 5. Claude API 연동
- `ClaudeApi` 인터페이스 (Retrofit)
- `ClaudeRepository` 구현
- `PromptTemplates` - 문자 분석, 재무 상담 프롬프트

#### 6. SMS 수집 기능
- `SmsReceiver` - 실시간 문자 수신
- `SmsReader` - 기존 문자 읽기
- `SmsParser` - 카드 결제 문자 필터링 및 파싱

#### 7. UI 화면 구현
- `HomeScreen` - 월간 현황, 카테고리별 지출
- `HistoryScreen` - 지출 내역 목록, 필터링
- `ChatScreen` - AI 상담 채팅
- `SettingsScreen` - 설정

#### 8. Navigation 설정
- Bottom Navigation Bar (홈, 내역, 상담, 설정)
- NavGraph 구성

### 생성된 파일 목록

```
app/src/main/java/com/sanha/moneytalk/
├── MoneyTalkApplication.kt
├── MainActivity.kt
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── dao/ExpenseDao.kt
│   │   ├── dao/IncomeDao.kt
│   │   ├── dao/BudgetDao.kt
│   │   ├── dao/ChatDao.kt
│   │   ├── entity/ExpenseEntity.kt
│   │   ├── entity/IncomeEntity.kt
│   │   ├── entity/BudgetEntity.kt
│   │   └── entity/ChatEntity.kt
│   ├── remote/
│   │   ├── api/ClaudeApi.kt
│   │   └── dto/ClaudeModels.kt
│   └── repository/
│       ├── ClaudeRepository.kt
│       ├── ExpenseRepository.kt
│       └── IncomeRepository.kt
├── domain/model/Category.kt
├── presentation/
│   ├── navigation/Screen.kt
│   ├── navigation/BottomNavItem.kt
│   ├── navigation/NavGraph.kt
│   ├── home/HomeScreen.kt
│   ├── home/HomeViewModel.kt
│   ├── history/HistoryScreen.kt
│   ├── history/HistoryViewModel.kt
│   ├── chat/ChatScreen.kt
│   ├── chat/ChatViewModel.kt
│   └── settings/SettingsScreen.kt
├── di/DatabaseModule.kt
├── di/NetworkModule.kt
├── receiver/SmsReceiver.kt
└── util/
    ├── SmsParser.kt
    ├── SmsReader.kt
    ├── DateUtils.kt
    └── PromptTemplates.kt
```

### 다음 작업 예정
1. Gradle Sync 및 빌드 테스트
2. 빌드 오류 수정
3. 실기기 테스트
4. API 키 영구 저장 (DataStore)

---

## 2026-02-05 - 색상 리소스 추가

### 작업 내용

#### Color.kt 대폭 업데이트
가계부 앱에 어울리는 종합적인 색상 팔레트 추가

**추가된 색상 카테고리:**
- Primary Colors (초록 계열 - 메인 브랜드)
- Secondary Colors (블루 계열)
- Tertiary Colors (오렌지 계열)
- Error Colors (빨강 계열)
- Background & Surface Colors
- Semantic Colors (수입, 지출, 저축, 성공, 경고, 정보)
- Category Colors (카테고리별 10가지 색상)
- Chart Colors (차트용 10가지 팔레트)
- Gradient Colors (그라데이션용)
- Text Colors
- Grey Scale (50~900)

#### Theme.kt 업데이트
- 라이트/다크 테마에 새 색상 적용
- 상태바 색상 자동 설정 추가
- dynamicColor 기본값 false로 변경 (브랜드 색상 유지)

---

---

## 2026-02-08 - Phase 1 리팩토링 + 대규모 기능 추가

### 작업 내용

#### 1. VectorSearchEngine 책임 분리 (Phase 1)
- core/similarity/ 패키지 신설 (5개 파일)
- 임계값 상수를 SimilarityPolicy SSOT로 구조화
- Vector(연산) → Policy(판단) → Service(행동) 3계층 구조 확립

#### 2. SMS 파싱 버그 수정 3건
- 결제예정 금액 SMS가 지출로 잡히는 문제
- 신한카드 승인 SMS에서 이름이 가게명으로 파싱되는 문제
- 계좌이체 출금 내역 카테고리 미분류 문제

#### 3. 채팅 시스템 확장
- 채팅 액션 5개 추가 (delete_by_keyword, add_expense, update_memo, update_store_name, update_amount)
- FINANCIAL_ADVISOR 할루시네이션 방지 규칙
- 채팅방 나갈 때 대화 기반 자동 타이틀 설정

#### 4. 기타
- 메모 기능 (DB v1→v2), 보험 카테고리 복원
- 수입 내역 통합 표시, 채팅 UI 리팩토링
- Claude 레거시 코드 완전 제거 (Retrofit 포함)
- SMS 동기화/카테고리 분류 진행률 표시 개선

---

## 2026-02-09 - 데이터 관리 + ANALYTICS

### 작업 내용

#### 1. OwnedCard 시스템 (DB v2→v3)
- OwnedCardEntity/Dao/Repository 신설
- CardNameNormalizer로 25+ 카드사 명칭 정규화
- Settings에서 소유 카드 체크박스 관리

#### 2. SMS 제외 키워드 시스템 (DB v3→v4)
- SmsExclusionKeywordEntity/Dao/Repository 신설
- 기본/사용자/채팅 3가지 소스 구분
- 채팅 액션 2개 추가 (add_sms_exclusion, remove_sms_exclusion)

#### 3. ANALYTICS 쿼리 타입
- ChatViewModel에서 클라이언트 사이드 복합 분석
- 필터(10종 연산자) + 그룹핑(6종) + 집계(5종 메트릭)

#### 4. 기타
- 전역 스낵바 버스 도입
- API 키 설정 후 저신뢰도 항목 자동 재분류
- 프롬프트 XML 이전 (ChatPrompts.kt → string_prompt.xml)
- DB 성능 인덱스 추가 (v4→v5)

---

## 2026-02-11 - UI 공통화 + Intent 패턴

### 작업 내용

#### 1. UI 공통 컴포넌트
- TransactionCardCompose/Info: 지출/수입 통합 카드
- TransactionGroupHeaderCompose/Info: 날짜별/가게별/금액별 그룹 헤더
- SegmentedTabRowCompose/Info: 라운드 버튼 스타일 탭
- Preview 파일 debug/ 소스셋 배치

#### 2. HistoryScreen Intent 패턴
- HistoryIntent sealed interface 도입
- UI-비즈니스 로직 분리 (SelectExpense, DeleteExpense, ChangeCategory 등)

#### 3. 카테고리 아이콘
- 이모지 → 벡터 아이콘 교체 시도 → revert (이모지 유지)

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|-----------|
| 2026-02-18 | 1.1.0 | ProGuard(R8) 활성화 + Firebase Analytics 트래킹 |
| 2026-02-18 | 1.1.0 | Firebase RTDB 원격 설정 (API 키 풀링 + 모델명 관리) |
| 2026-02-18 | 1.0.0 | 알파 배포 준비 (강제 업데이트, 개인정보처리방침, ProGuard 규칙) |
| 2026-02-15 | - | 문서 갱신 (ARCHITECTURE, AI_CONTEXT, AI_HANDOFF, PROJECT_CONTEXT 등) |
| 2026-02-14~ | 0.7.0 | safe-commit 스킬, 홈→내역 네비게이션, AI 인사이트 개선, 가맹점 일괄 업데이트, Compose Stability, 리팩토링 |
| 2026-02-14 | 0.6.0 | Phase 2 완료 + History 필터 초기화 버튼 |
| 2026-02-13 | 0.5.0 | 채팅 Clarification 루프 + Karpathy 수치 정확성 규칙 |
| 2026-02-12 | - | History UI 개편, 필터 BottomSheet, NavigationBar 컴팩트화, DpTextUnit 유틸 |
| 2026-02-11 | 0.4.0 | UI 공통화 (TransactionCard, GroupHeader, SegmentedTab), Intent 패턴 |
| 2026-02-09 | 0.3.0 | OwnedCard, SMS 제외 키워드, ANALYTICS, DB v5 |
| 2026-02-08 | 0.2.0 | Phase 1 리팩토링, 채팅 액션 확장, Claude 레거시 제거 |
| 2026-02-05 | 0.1.1 | 색상 리소스 대폭 추가 (80+ 색상) |
| 2026-02-05 | 0.1.0 | 프로젝트 초기 설정 및 기본 구조 완성 |
