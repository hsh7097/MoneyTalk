# 머니톡 (MoneyTalk) - 개발 로그

> 개발 과정과 변경 사항을 기록하는 문서입니다.

---

## 2026-02-21 - 카테고리 상세 화면 + Budget BottomSheet + HistoryFilter 개선

### 작업 내용

#### 카테고리 상세 화면 (CategoryDetailActivity)
- 홈 카테고리 리스트에서 클릭 → 신규 Activity로 진입 (Intent 기반, NavGraph 외부)
- 상단 TopAppBar: 카테고리 이모지 + 이름 + 뒤로가기
- CumulativeTrendSection 재사용: 당월 지출 vs 전월 지출 비교 곡선
- 하단 거래 리스트: TransactionCardCompose 재사용
- 빈 상태 처리 (지출 없을 때 안내 메시지)
- 다크 모드 완전 지원 (차트 배경 surfaceVariant 기반)

#### Budget BottomSheet (전체+카테고리별 예산 설정)
- `BudgetBottomSheet.kt` 신규 — ModalBottomSheet 기반
  - TotalBudgetInput: 전체 예산 입력 (OutlinedTextField)
  - CategoryBudgetHeader: "카테고리별 예산" 헤더 + 우측 초기화 버튼
  - CategoryBudgetRow: 카테고리별 예산 입력 + % 자동 표시 (전체 예산 대비)
- 전체/카테고리 독립 설정 가능 (전체만, 카테고리만, 둘 다)
- 하단 저장 버튼 고정 + 위 영역 스크롤 (LazyColumn weight)
- 상단 100dp 마진 유지 (heightIn(max = screenHeight - 100.dp))

#### SettingsViewModel 수정
- ShowBudgetBottomSheet, SaveBudgets Intent 추가
- loadMonthlyBudget(): 전체 + 카테고리별 예산 동시 로드 (getBudgetsByMonthOnce)
- saveBudgets(): deleteAllByMonth → 전체/카테고리 일괄 insert

#### HistoryFilter BottomSheet 개선
- 동일 100dp 상단 마진 패턴 적용
- LazyVerticalGrid → FlowRow 변경 (스크롤 부모 호환)
- 고정 하단 적용 버튼 + HorizontalDivider

#### 기타
- BudgetDao: `getBudgetsByMonthOnce` suspend 함수 추가
- strings.xml: 예산/카테고리 상세 관련 문자열 15개 추가
- 누적 차트 다크 모드 색상 수정 (CumulativeChartCompose, CumulativeTrendSection)
- SMS 증분 동기화 5분 오버랩 (경계 SMS 누락 방지)
- HomeScreen: 불필요한 onNavigateToHistory 파라미터 제거

### 변경 파일
- `feature/categorydetail/` — 신규 디렉토리 (Activity + ViewModel)
- `feature/settings/ui/BudgetBottomSheet.kt` — 신규
- `feature/settings/ui/SettingsViewModel.kt` — 예산 Intent/로직 추가
- `feature/settings/ui/SettingsScreen.kt` — BudgetBottomSheet 연결
- `feature/history/ui/HistoryFilter.kt` — BottomSheet 개선
- `feature/home/ui/HomeScreen.kt` — CategoryDetail 진입 + 파라미터 정리
- `feature/home/ui/HomeViewModel.kt` — 5분 오버랩
- `navigation/NavGraph.kt` — onNavigateToHistory 파라미터 제거
- `core/database/dao/BudgetDao.kt` — getBudgetsByMonthOnce 추가
- `core/ui/component/chart/CumulativeChartCompose.kt` — 다크 모드 색상
- `core/ui/component/chart/CumulativeTrendSection.kt` — 다크 모드 색상
- `AndroidManifest.xml` — CategoryDetailActivity 등록
- `strings.xml` — 예산+카테고리 상세 문자열
- `docs/COMPOSABLE_MAP.md` — BudgetBottomSheet 관련 5개 추가

---

## 2026-02-21 - 차트 UX 대폭 개선 + 6개월 평균 + 데이터 동기화

### 작업 내용

#### 누적 추이 차트 UX 개선
- **범례 디자인 변경**: 체크박스 제거 → 채워진 원(primaryLine, 항상 활성) + 테두리 원(toggleable, 클릭 시 채움/비움 전환)
- **X축 6등분 균등 분할**: 실제 말일 기준 6등분 (31일→1,6,11,16,21,26,31 / 28일→1,6,10,15,19,24,28)
- **X축 스트레칭**: 각 곡선이 자기 포인트 수 기준으로 전체 폭 채움
- **0원 시작점**: 모든 곡선의 첫 포인트를 0원으로 추가 (그래프가 항상 0에서 시작)
- **daysInMonth 계산 수정**: `(monthEnd-monthStart)/DAY_MS` → +1 보정 (endTime 23:59:59 정수 나눗셈 문제)
- **Y축 고정**: 토글 상태와 무관하게 모든 곡선 최대값 기준으로 고정
- **Y축 올림 규칙**: ≤100만→100만, >100만→200만 단위 올림 (가능한 값: 100만,200만,400만,600만,800만,...)
- **Y축 5등분**: 수평 가이드라인 6개 (0%, 20%, 40%, 60%, 80%, 100%)

#### 6개월 평균 곡선 추가
- `buildAvgNMonthCumulative(n, ...)` 일반화 (3개월/6개월 공용)
- 6개월 평균은 지난 6개월 데이터가 **모두** 존재할 때만 표시
- 3개월 평균도 동일 규칙 적용 (지난 3개월 데이터 모두 존재 시만)
- 누적 커브 하락 방지: 짧은 월(28/30일)에서 carry-forward 적용 (`getOrNull(day) ?: lastOrNull()`)

#### History/Chat → Home 차트 데이터 동기화
- `HistoryViewModel`: deleteExpense, deleteIncome, updateExpenseMemo, updateIncomeMemo, updateExpenseCategory 후 `DataRefreshEvent.TRANSACTION_ADDED` 발행
- `ChatViewModel`: 채팅 액션(UPDATE_CATEGORY 등) 실행 후 `DataRefreshEvent.TRANSACTION_ADDED` 발행
- `HomeViewModel`에서 이벤트 수신 시 해당 월 차트 데이터 자동 갱신

### 변경 파일 (이전 커밋 포함)
- `CumulativeChartCompose.kt` — X축 5일간격 라벨, Y축 5등분+고정, effectiveDays/yAxisMax 필드, ceilToNiceValue
- `CumulativeTrendSection.kt` — 범례 원형 토글, MAX_DAYS_IN_MONTH=31, yAxisMax 토글 독립 계산
- `SpendingTrendSection.kt` — avgSixMonthPoints 파라미터 추가
- `HomeViewModel.kt` — buildAvgNMonthCumulative 일반화, 6개월 평균, carry-forward, DataRefreshEvent 수신
- `HomeScreen.kt` — avgSixMonthPoints 전달
- `HistoryViewModel.kt` — DataRefreshEvent 발행 추가
- `ChatViewModel.kt` — DataRefreshEvent 발행 추가
- `strings.xml` (ko/en) — "지난 3개월 평균" 라벨 변경 + "지난 6개월 평균" 추가

---

## 2026-02-21 - 차트 공통화 + 예산 설정 + SMS 캐시 폴백 버그 수정

### 작업 내용

#### 누적 추이 차트 공통화 (CumulativeTrendSection)
- `SpendingTrendSection`(홈 전용)에서 도메인 독립적 `CumulativeTrendSection` 추출 → core/ui/component/chart/
- `SpendingTrendSection`을 얇은 래퍼로 변환 (HomeScreen 호출 코드 변경 없음)
- `ToggleableLine` @Immutable 데이터 클래스 (토글 가능 곡선 정보)

#### 설정 화면 월 예산 설정
- "수입/예산 관리" 섹션에 Savings 아이콘 + "월 예산 설정" 아이템 추가
- `MonthlyBudgetDialog` 다이얼로그 (금액 입력, 0=해제)
- SettingsViewModel: `loadMonthlyBudget()` / `saveMonthlyBudget()` — BudgetDao 연동
- 예산 변경 시 `DataRefreshEvent.CATEGORY_UPDATED` → 홈 차트 자동 갱신

#### 차트 추가 수정 (후속 커밋)
- X축 라벨: 5일 간격 고정 → 6등분 균등 분할로 변경
- daysInMonth 계산 +1 보정 (endTime 23:59:59.999 정수 나눗셈 문제)
- 0원 시작점: buildDailyCumulative/buildBudgetCumulativePoints 첫 포인트 0L 추가
- todayDayIndex +1 보정 (points[0]=0원 시작점이므로)
- PR #24 리뷰: onCategorySelected 핸들러 복원 (카테고리 탭 → History 네비게이션)

#### SMS 캐시 폴백 버그 수정
- `SmsPatternMatcher.parseWithPatternRegex()`: regex 파싱 실패 시 캐시값(parsedAmount/parsedStoreName) 폴백을 제거하고 null 반환으로 수정
- 원인: 동일 템플릿에 매칭된 다른 SMS들에 첫 번째 패턴의 가게명/금액이 덮어씌워지는 버그
- 수정 후: regex 파싱 실패 SMS는 Step 5 LLM으로 위임되어 개별 파싱

### 변경 파일
- `CumulativeTrendSection.kt` (신규) — 도메인 독립 누적 추이 섹션
- `SpendingTrendSection.kt` — 얇은 래퍼로 변환
- `SmsPatternMatcher.kt` — 캐시 폴백 제거
- `SettingsScreen.kt` — 월 예산 설정 아이템 + 다이얼로그 연결
- `SettingsViewModel.kt` — 예산 Intent/Dialog/State/로직 추가
- `SettingsPreferenceDialogs.kt` — MonthlyBudgetDialog 추가
- `strings.xml` (ko/en) — 예산 관련 문자열 7개 추가
- `COMPOSABLE_MAP.md` — CumulativeTrendSection 추가

---

## 2026-02-21 - UI 버그 수정 + PR 리뷰 반영

### 작업 내용

#### 하단 탭 높이 고정
- `enableEdgeToEdge()` 환경에서 `NavigationBar`가 내부적으로 시스템 네비게이션 바 insets를 포함하여 실제 콘텐츠 영역이 줄어드는 문제
- `NavigationBar(windowInsets = WindowInsets(0))` + Column에 `windowInsetsPadding(WindowInsets.navigationBars)` 분리 적용

#### 탭 전환 시 오늘 페이지 + 필터 초기화
- `!isSelected` 분기에서도 `homeTabReClickEvent`/`historyTabReClickEvent` 발행
- 다른 탭에서 홈/내역 탭으로 이동 시에도 초기화 동작

#### 필터 BottomSheet 적용 버튼 미노출
- 큰 글꼴(SP 설정) 환경에서 `LazyVerticalGrid`의 `heightIn(max: 300.dp)` → `weight(1f, fill = false)` 변경
- 적용 버튼이 항상 하단에 노출되도록 수정

#### PR #23 코드 리뷰 반영 (3건)
- 빈 룰 결과도 TTL 캐시 (`cachedRules` nullable 전환)
- 원격 룰 파싱 실패 시 차순위 룰 순차 시도 (유사도 내림차순 정렬)
- 동일 sync 내 중복 승격 방지 (`promotedRuleIds` Set)

### 변경 파일
- `MainActivity.kt` — 하단 탭 windowInsets 분리, 탭 전환 초기화
- `HistoryFilter.kt` — 필터 카테고리 그리드 weight 기반 변경
- `RemoteSmsRuleRepository.kt` — nullable cache
- `SmsPatternMatcher.kt` — 차순위 룰 시도, 중복 승격 방지
- `SmsGroupClassifier.kt` — RTDB 디버그 로그 강화

---

## 2026-02-20 - LLM 생성 regex 샘플 검증

### 작업 내용

#### LLM regex 자동 검증 (validateRegexAgainstSamples)
- `SmsGroupClassifier.processGroup()`: regex 생성 성공 후, 샘플 SMS에 실제 적용하여 파싱 성공률 확인
- 샘플 중 50% 이상(`REGEX_VALIDATION_MIN_PASS_RATIO`) 파싱 성공해야 유효한 regex로 인정
- 검증 실패 시 `recordRegexFailure()` + 템플릿 폴백으로 전환
- LLM hallucination으로 인한 깨진 regex가 DB에 저장되는 것 방지

### 변경 파일
- `SmsGroupClassifier.kt` — `validateRegexAgainstSamples()` 추가, `processGroup()` regex 검증 로직 삽입

---

## 2026-02-20 - RTDB 원격 regex 룰 매칭 시스템

### 작업 내용

#### 1. 원격 SMS regex 룰 매칭 (Step 4 2순위)
- `RemoteSmsRule.kt`: RTDB `/sms_regex_rules/v1/{sender}/{ruleId}` 에서 내려받는 정제된 룰 데이터 클래스
  - embedding, amountRegex, storeRegex, cardRegex, minSimilarity(0.94), enabled, updatedAt
- `RemoteSmsRuleRepository.kt`: RTDB 룰 로드 + 메모리 캐시(TTL 10분) + sender별 그룹핑
  - 실패 시 빈 맵 반환 (예외 전파 금지), enabled=true 룰만 로드
- `SmsPatternMatcher.kt`: `matchWithRemoteRules()` 추가
  - 동일 발신번호 룰 필터 → 코사인 유사도 ≥ minSimilarity → regex 파싱
  - 매칭 성공 시 `promoteToLocalPattern()` → 로컬 DB에 parseSource="remote_rule" 승격

#### 2. RTDB 표본 수집 필드 정리
- `collectSampleToRtdb()` 시그니처 간소화 (isMainGroup, sourceTotalCount, sourceSubGroupCount 제거)
- `registerPaymentPattern()`, `processGroup()` 시그니처 간소화
- RTDB 데이터 필드 정리:
  - 유지: maskedBody, cardName, senderAddress, normalizedSenderAddress, parseSource, embedding, groupMemberCount, regex 3종
  - 제거: embeddingDim, templateHash, sourceTotalCount, sourceSubGroupCount, isMainGroup, appVersion, count, lastSeen, updatedAt (ServerValue)
- 각 필드에 인라인 주석 추가

### 변경 파일
- `RemoteSmsRule.kt` — 신규 (원격 룰 데이터 클래스)
- `RemoteSmsRuleRepository.kt` — 신규 (RTDB 로드 + 캐시)
- `SmsPatternMatcher.kt` — 원격 룰 매칭 + 로컬 승격 추가
- `SmsGroupClassifier.kt` — RTDB 표본 필드 정리 + 시그니처 간소화

---

## 2026-02-20 - DB 메인 그룹 패턴 저장 + 메인 regex 선조회

### 작업 내용

#### 1. isMainGroup 시스템 도입
- `SmsPatternEntity`에 `isMainGroup: Boolean = false` 필드 추가
- DB v2 → v3 마이그레이션 (ALTER TABLE ADD COLUMN isMainGroup)
- `SmsPatternDao.getMainPatternBySender()` 쿼리 추가: 발신번호별 메인 패턴 1개 반환

#### 2. SmsGroupClassifier Step 5 메인 패턴 선조회
- `classifyUnmatched()` [5-1.7]: DB에서 발신번호별 메인 패턴 선조회 → dbMainPatterns Map
- `processSourceGroup(sourceGroup, dbMainPattern)`: DB 메인 패턴을 dbMainContext로 변환
  - 서브그룹 1개: DB 메인 있으면 regex 참조로 전달
  - 서브그룹 2개+: 현재 세션 메인 우선, 없으면 DB fallback
- `processGroup(isMainGroup=true)`: 메인 그룹 DB 등록 시 isMainGroup 표시
- `registerPaymentPattern(isMainGroup)`: isMainGroup 파라미터 전달

#### 3. senderAddress 정규화 일관성
- `registerPaymentPattern()`: `senderAddress = normalizeAddress(embedded.input.address)`
- `registerNonPaymentPattern()`: 동일하게 normalizeAddress 적용
- DB 저장 시 정규화된 주소 사용 → `getMainPatternBySender()` 조회 정확도 보장

#### 4. GeminiSmsExtractor LLM 프롬프트 개선
- 시스템 프롬프트 "1~3건" → "1~5건" 수정 (REGEX_SAMPLE_SIZE=5와 일치)
- 8개 프롬프트 XML 이전 (string_prompt.xml)
- MainRegexContext 기반 메인 regex 참조 전달 구조

### 변경 파일
- `SmsPatternEntity.kt` — isMainGroup 필드 추가
- `AppDatabase.kt` — version 2 → 3
- `DatabaseMigrations.kt` — MIGRATION_2_3 추가
- `DatabaseModule.kt` — 마이그레이션 등록
- `SmsPatternDao.kt` — getMainPatternBySender() 쿼리
- `SmsGroupClassifier.kt` — 메인 패턴 선조회 + processSourceGroup/processGroup 시그니처 변경
- `GeminiSmsExtractor.kt` — 프롬프트 개선 + MainRegexContext
- `string_prompt.xml` — 8개 프롬프트 XML 이전

---

## 2026-02-20 - sms2 파이프라인 마이그레이션 완료

### 작업 내용

#### 1. sms2 신규 파일 4개 추가
- `SmsSyncCoordinator.kt`: 유일한 외부 진입점 — process(smsList, onProgress) → SyncResult
  - SmsPreFilter → SmsIncomeFilter → SmsPipeline 순차 호출
  - SMS 읽기/DB 저장/lastSyncTime 관리 없음 (순수 분류 로직만)
- `SmsReaderV2.kt`: SMS/MMS/RCS 통합 읽기 → List<SmsInput> 직접 반환
  - V1의 SmsMessage 중간 변환 제거, SmsFilter.shouldSkipBySender 통합
- `SmsIncomeFilter.kt`: PAYMENT/INCOME/SKIP 3분류
  - financialKeywords 46개, cancellationKeywords(출금취소 등), incomeExcludeKeywords
  - classifyAll()로 배치 분류 지원
- `SmsIncomeParser.kt`: 수입 SMS 파싱 Object 싱글톤
  - extractIncomeAmount/Type/Source/DateTime
  - KB 스타일 멀티라인 소스 추출, FROM_PATTERN, DEPOSIT_PATTERN

#### 2. HomeViewModel syncSmsV2() 오케스트레이터 전환
- syncSmsMessages() 전체 삭제 (~400줄)
- syncSmsV2() 내부를 5개 private 메소드로 분리:
  - readAndFilterSms(): SMS 읽기 + smsId 중복 제거
  - processSmsPipeline(): SmsSyncCoordinator.process() 호출
  - saveExpenses(): SmsParseResult → ExpenseEntity 변환 + 배치 저장
  - saveIncomes(): SmsIncomeParser로 파싱 + IncomeEntity 저장
  - postSyncCleanup(): 카테고리 분류, 패턴 정리, lastSyncTime 등
- syncIncremental() + calculateIncrementalRange() 추가
- HomeScreen 2곳 호출부 변경 (syncSmsMessages → syncIncremental)
- SmsBatchProcessor DI 제거, launchBackgroundHybridClassification() 삭제

#### 3. V1 유지 범위
- core/sms(V1): SmsProcessingService 실시간 수신 전용으로 유지
- 공유: SmsFilter(shouldSkipBySender), GeminiSmsExtractor(LLM 호출)

### 변경 파일
- `core/sms2/SmsSyncCoordinator.kt` — 신규
- `core/sms2/SmsReaderV2.kt` — 신규
- `core/sms2/SmsIncomeFilter.kt` — 신규
- `core/sms2/SmsIncomeParser.kt` — 신규
- `feature/home/ui/HomeViewModel.kt` — syncSmsV2 분리 + syncSmsMessages 삭제
- `feature/home/ui/HomeScreen.kt` — 2개 호출부 syncIncremental로 변경

---

## 2026-02-19 - SMS 통합 파이프라인 (sms2 패키지) 골격 생성

### 작업 내용

#### 1. core/sms2/ 패키지 신규 생성 (6개 파일)
- 기존 3개 경로(SmsBatchProcessor, HybridSmsClassifier, SmsProcessingService)에 파편화된 SMS 처리를 단일 파이프라인으로 통합
- Tier 1 로컬 regex(SmsParser) 제거 — 모든 SMS가 임베딩 경로(Tier 2/3)로 처리
- sms2 패키지는 core/sms에서 import하지 않음 (GeminiSmsExtractor 제외, 자체 구현)

#### 2. 파일별 구현 상태
- `SmsPipelineModels.kt`: 데이터 클래스 3종 (SmsInput, EmbeddedSms, SmsParseResult) — 전체 구현
- `SmsPreFilter.kt`: Step 2 사전 필터링 (키워드 + 구조 필터) — 전체 구현
- `SmsTemplateEngine.kt`: Step 3 템플릿화 + Gemini Embedding API — 전체 구현
- `SmsPatternMatcher.kt`: Step 4 벡터 매칭 + regex 파싱 (자체 코사인 유사도 포함) — 전체 구현
- `SmsGroupClassifier.kt`: Step 5 그룹핑 + LLM + regex 생성 + 패턴 DB 등록 — 전체 구현
- `SmsPipeline.kt`: 오케스트레이터 (Step 2→3→4→5) — 전체 구현

#### 3. SmsParser KB 출금 유형 확장
- FBS출금 (카드/페이 자동이체), 공동CMS출 (보험 CMS) 지원 추가
- `isKbWithdrawalLine()` 헬퍼 도입으로 KB 스타일 출금 줄 판별 통합
- `paymentKeywords`에 "CMS출" 추가
- 보험 카테고리 키워드 추가 (삼성화, 현대해, 메리츠, DB손해, 한화손해, 흥국화)

### 변경 파일
- `core/sms2/SmsPipelineModels.kt` — 신규
- `core/sms2/SmsPreFilter.kt` — 신규
- `core/sms2/SmsTemplateEngine.kt` — 신규
- `core/sms2/SmsPatternMatcher.kt` — 신규
- `core/sms2/SmsGroupClassifier.kt` — 신규
- `core/sms2/SmsPipeline.kt` — 신규
- `core/sms/SmsParser.kt` — KB 출금 유형 확장 + 보험 키워드 추가

---

## 2026-02-19 - 레거시 FULL_SYNC_UNLOCKED 호환성 수정

### 작업 내용

#### 1. 레거시 전역 해제 사용자 regression 수정
- 기존 FULL_SYNC_UNLOCKED=true 사용자가 월별 동기화 업데이트 후 CTA가 다시 표시되는 문제 수정
- HomeUiState/HistoryUiState에 `isLegacyFullSyncUnlocked` 필드 추가
- `isMonthSynced()`/`isPagePartiallyCovered()`에서 레거시 전역 해제 상태 체크
- PR #21 Codex 리뷰 P1 피드백 반영

### 변경 파일
- `HomeViewModel.kt` — isLegacyFullSyncUnlocked 로딩 + isMonthSynced/isPagePartiallyCovered 레거시 체크
- `HistoryViewModel.kt` — isLegacyFullSyncUnlocked 로딩 + isMonthSynced/isPagePartiallyCovered 레거시 체크

---

## 2026-02-19 - SMS 배치 처리 가드레일 + 그룹핑 최적화

### 작업 내용

#### 1. SmsBatchProcessor 그룹핑 최적화
- 발신번호 기반 2레벨 그룹핑 도입 (37그룹 → 2~4그룹으로 대폭 감소)
- 가게명 {STORE} 플레이스홀더 치환 (SmsEmbeddingService.templateizeSms)
- LLM 배치 호출 병렬화 (async + Semaphore, 직렬 → 병렬)
- Step1+2 임베딩 통합 (중복 API 호출 제거)
- 멤버별 가게명 개별 추출 (대표 가게명 복제 방지)

#### 2. 가드레일 3종 (Codex 분석 기반)
- template_regex 신뢰도 1.0 → 0.85 하향 (미검증 정규식에 높은 신뢰도 부여 방지)
- 소그룹 병합 시 코사인 유사도 검증 추가 (SMALL_GROUP_MERGE_MIN_SIMILARITY=0.70)
- RTDB 업로드 품질 게이트 (검증된 소스만 정규식 포함)

#### 3. 신규 파일 및 기능
- GeneratedSmsRegexParser.kt: LLM 생성 정규식 파서 (group1 캡처 규약, 폴백 체인)
- GeminiSmsExtractor: 배치 추출 + 정규식 자동 생성
- SmsPatternEntity: amountRegex/storeRegex/cardRegex/parseSource 필드 추가 (DB v5→v6)
- DatabaseMigrations.kt: v5→v6 마이그레이션

#### 4. 문서 갱신
- SMS_PARSING.md 전면 재작성 + RTDB 정규식 다운로드 로드맵 (Section 16)
- 임베딩 차원 수 768 → 3072 수정 (6개 파일)

### 변경 파일
- `SmsBatchProcessor.kt` — 2레벨 그룹핑 + LLM 병렬화 + 가드레일 3종
- `SmsEmbeddingService.kt` — {STORE} 플레이스홀더 + 차원 주석 수정
- `GeminiSmsExtractor.kt` — 배치 추출 + 정규식 생성
- `GeneratedSmsRegexParser.kt` — 신규 (LLM 정규식 파서)
- `DatabaseMigrations.kt` — 신규 (v5→v6)
- `SmsPatternEntity.kt` — regex 필드 4개 추가
- `FloatListConverter.kt`, `StoreEmbeddingEntity.kt` — 차원 주석 수정
- `docs/SMS_PARSING.md`, `CATEGORY_CLASSIFICATION.md`, `PROJECT_CONTEXT.md` — 문서 갱신

---

## 2026-02-19 - SMS 동기화 최적화 + 부분 데이터 CTA

### 작업 내용

#### 1. core/sms 패키지 분리
- core/util → core/sms로 SMS 핵심 파일 7개 이동 (SmsParser, SmsReader, HybridSmsClassifier, SmsBatchProcessor, VectorSearchEngine, SmsEmbeddingService, GeminiSmsExtractor)
- SmsFilter.kt 신규 (010/070 발신자 조건부 제외 + 금융 힌트 보존)
- 참조하는 파일들 import 경로 수정 (SmsProcessingService, StoreNameGrouper, CategoryClassifierServiceImpl 등)

#### 2. SMS 동기화 최적화
- 초기 동기화 기간 3개월 → 2개월(60일) 축소 (DEFAULT_SYNC_PERIOD_MILLIS)
- 광고 시청 후 해당 월만 동기화 (syncMonthData + calculateMonthRange)
- calculateMonthRange에 커스텀 monthStartDay 반영 (DateUtils.getCustomMonthPeriod)
- SmsReader에 SmsFilter 통합 (SMS/MMS/RCS 모든 채널에 발신자 필터 적용)

#### 3. LLM 트리거 + 관측성
- SmsPatternSimilarityPolicy에 LLM_TRIGGER_THRESHOLD(0.80) 추가
- HybridSmsClassifier: 벡터 유사도 0.80~0.92 구간 → LLM 호출 대상 선별
- HybridSmsClassifier: Regex 오파싱 방어 (storeName='결제' → null → Tier 2/3 이관)
- 배치 분류 완료 로그에 tier별 카운트 (Tier1/2/3, 사전필터, LLM호출) 추가

#### 4. 부분 데이터 CTA
- isPagePartiallyCovered() 헬퍼 추가 (Home/HistoryViewModel)
- FullSyncCtaSection에 isPartial 모드 추가 (⚠️ "일부 데이터만 표시되고 있어요")
- HomeScreen/HistoryScreen에서 부분 데이터 판단 + CTA 표시
- 커스텀 monthStartDay로 인해 동기화 경계에 걸린 페이지에 안내 표시

### 변경 파일
- `core/sms/` — 7개 파일 이동 + SmsFilter 신규
- `SmsPatternSimilarityPolicy.kt` — LLM_TRIGGER_THRESHOLD 추가
- `HybridSmsClassifier.kt` — LLM 트리거 + 오파싱 방어 + 관측성 로그
- `SmsReader.kt` — SmsFilter 통합
- `HomeViewModel.kt` — 2개월 축소 + 월별 동기화 + isPagePartiallyCovered
- `HomeScreen.kt` — 부분 데이터 CTA
- `HistoryViewModel.kt` — isPagePartiallyCovered
- `HistoryScreen.kt` — 부분 데이터 CTA
- `FullSyncCtaSection.kt` — isPartial 모드
- `strings.xml` — partial_sync_cta 문자열
- `docs/AI_CONTEXT.md`, `AI_HANDOFF.md`, `CHANGELOG.md` — 문서 갱신

---

## 2026-02-19 - 버그 수정 + UX 개선 + 빈 상태 CTA + 광고 실패 보상

### 작업 내용

#### 1. Android Auto Backup 복원 감지 + 3개월 동기화 버그 수정
- 앱 재설치 시 Auto Backup이 DataStore(lastSyncTime)를 복원하여 동기화 범위가 잘못되는 버그 수정
- 2-Layer 방어: (1) 코드: savedSyncTime > 0 && DB 비어있으면 stale → 0으로 리셋 (2) backup_rules.xml/data_extraction_rules.xml에서 DataStore 백업 제외

#### 2. 탭 재클릭 → 오늘 페이지 이동
- 홈/내역 탭 재클릭 시 HorizontalPager를 오늘(현재 월) 페이지로 animateScrollToPage
- MutableSharedFlow<Unit> 패턴으로 MainActivity → NavGraph → Screen 이벤트 전달

#### 3. 홈 새로고침 깜빡임 수정
- refreshData()에서 clearAllPageCache() 제거 → 기존 캐시 유지하면서 재로드
- isLoading을 캐시 미존재 시에만 true로 설정

#### 4. "오늘 문자만 동기화" 메뉴 제거
- HomeScreen의 새로고침 DropdownMenu에서 "오늘 문자만 동기화" 항목 삭제

#### 5. 내역 수입 0원 표시
- HistoryHeader PeriodSummaryCard에서 `if (totalIncome > 0)` 조건 제거 → 수입 항상 표시

#### 6. 빈 상태 "광고 보고 전체 데이터 가져오기" CTA
- FullSyncCtaSection 공용 Composable 생성 (core/ui/component)
- HomeScreen: 데이터 0건 + 현재 월 아님 + isFullSyncUnlocked=false → CTA 표시
- HistoryScreen(TransactionListView): 동일 조건으로 CTA 표시
- HistoryViewModel에 RewardAdManager 주입 + 광고 다이얼로그 로직 추가

#### 7. 광고 로드/표시 실패 시 보상 처리
- Home/History/Chat 모든 광고 onFailed 콜백에서 보상 처리 (앱/광고 이슈 = 유저 책임 아님)
- 유저 취소(백키/태스크 킬)는 onAdDismissedFullScreenContent에서 별도 처리됨

### 변경 파일
- `FullSyncCtaSection.kt` — 신규 (공용 CTA Composable)
- `HomeScreen.kt` — CTA + 탭 재클릭 + 오늘 동기화 제거 + 광고 실패 보상
- `HomeViewModel.kt` — Auto Backup 감지 + 깜빡임 수정
- `HistoryScreen.kt` — CTA + 탭 재클릭 + 광고 다이얼로그
- `HistoryViewModel.kt` — RewardAdManager 주입 + isFullSyncUnlocked + 광고 로직
- `HistoryHeader.kt` — 수입 0원 표시
- `ChatViewModel.kt` — 광고 실패 보상
- `MainActivity.kt` — 탭 재클릭 SharedFlow
- `NavGraph.kt` — homeTabReClickEvent + historyTabReClickEvent 전달
- `SmsReader.kt` — 디버그 로그 추가
- `strings.xml` — CTA 문자열 3건
- `backup_rules.xml`, `data_extraction_rules.xml` — DataStore 백업 제외
- `COMPOSABLE_MAP.md` — FullSyncCtaSection 추가

---

## 2026-02-19 - HorizontalPager pageCache + 동기화 제한 + SMS 필터 보강 (이전 세션)

### 작업 내용

#### 1. HorizontalPager 월별 독립 페이지 캐시
- Home/History 화면에 HorizontalPager(beyondViewportPageCount=1) 적용
- MonthPagerUtils 유틸 추가 (MonthKey, pageToYearMonth, adjacentMonth, isFutureMonth)
- HomePageContent Composable 분리 (HorizontalPager 내부 렌더링 단위)
- 기존 SwipeToNavigate 제거 → HorizontalPager 네이티브 스와이프 대체

#### 2. DonutChartCompose 개선
- 불필요한 rotate 애니메이션 제거 (즉시 렌더링)
- displayLabel 프로퍼티 추가 (DonutSlice)

#### 3. SMS 100자 초과 필터 보강
- HybridSmsClassifier.batchClassify() Step 2에 body.length > MAX_SMS_LENGTH 체크 추가
- SmsParser.MAX_SMS_LENGTH를 const(public)로 변경

#### 4. 초기 동기화 3개월 제한 + 리워드 광고 전체 해제
- 첫 동기화 시 fullSyncUnlocked=false면 3개월 전부터만 SMS 읽기
- 전체 동기화 버튼 → 미해제 시 광고 다이얼로그 → 시청 → 해제 → 전체 동기화
- SettingsDataStore에 FULL_SYNC_UNLOCKED 키 추가
- RewardAdManager에 unlockFullSync/isFullSyncUnlocked 추가
- HomeScreen에 전체 동기화 해제 광고 AlertDialog 추가

#### 5. 수익화 문서
- MONETIZATION.md Section 8: 동기화 범위 제한 + 보상형 광고
- MONETIZATION.md Section 9: Vector-First + LLM 아키텍처 향후 개선안

### 변경 파일
- `HomeScreen.kt`, `HomeViewModel.kt`, `HistoryScreen.kt`, `HistoryViewModel.kt` — pageCache + 동기화 분기
- `MonthPagerUtils.kt` — 신규, `SwipeToNavigate.kt` — 삭제
- `DonutChartCompose.kt` — 애니메이션 제거
- `HybridSmsClassifier.kt`, `SmsParser.kt` — 100자 필터
- `SettingsDataStore.kt`, `RewardAdManager.kt` — 전체 동기화 해제
- `strings.xml` — 전체 동기화 해제 문자열
- `COMPOSABLE_MAP.md`, `MONETIZATION.md` — 문서

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
| 2026-02-20 | 1.1.0 | RTDB 원격 regex 룰 매칭 시스템 (RemoteSmsRule, RemoteSmsRuleRepository, 로컬 승격) |
| 2026-02-19 | 1.1.0 | SMS 통합 파이프라인 sms2 패키지 6개 파일 생성 + SmsParser KB 출금 유형 확장 |
| 2026-02-19 | 1.1.0 | 레거시 FULL_SYNC_UNLOCKED 호환성 수정 (PR #21 리뷰 반영) |
| 2026-02-19 | 1.1.0 | SMS 동기화 최적화 (2개월 축소 + 발신자 필터 + LLM 트리거 + core/sms 분리 + 부분 데이터 CTA) |
| 2026-02-19 | 1.1.0 | HorizontalPager pageCache + 3개월 동기화 제한 + SMS 100자 필터 보강 |
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
