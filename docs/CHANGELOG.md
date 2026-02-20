# Changelog

모든 주요 변경사항을 기록합니다.

## [Unreleased]

### Added (2026-02-21)
- **RTDB 표본 수집 디버그 로그 강화**: collectSampleToRtdb 진입/스킵/전송/성공/실패 상세 로그 추가
- **누적 추이 차트 공통화 + UX 개선**: `CumulativeTrendSection` 도메인 독립 컴포넌트 추출 (core/ui/component/chart/)
  - `SpendingTrendSection`을 얇은 래퍼로 변환 (HomeScreen 호출 코드 변경 없음)
  - 범례 디자인 변경: 체크박스 제거 → 채워진 원(활성)/테두리 원(비활성) 클릭 토글
  - X축 라벨 5일 간격 (1,5,10,15,20,25,31) + 31일 고정 (월 길이 무관)
  - Y축 고정: 토글 상태 무관하게 모든 곡선 최대값 기준 200만 단위 올림 + 5등분 표시
  - 6개월 평균 곡선 추가 (지난 6개월 데이터 모두 존재 시만 표시)
  - 3개월/6개월 평균 데이터 검증 강화 (N개월 모두 데이터 있어야 표시)
  - 누적 커브 하락 방지: 짧은 월(28/30일) carry-forward 적용
- **History/Chat → Home 차트 데이터 동기화**: DataRefreshEvent를 통한 실시간 반영
  - HistoryViewModel: 지출 삭제/메모 수정/카테고리 변경 시 이벤트 발행
  - ChatViewModel: 채팅 액션 실행 후 이벤트 발행
- **설정 화면 월 예산 설정**: 수입/예산 관리 섹션에 월 예산 입력 아이템 + 다이얼로그 추가
  - BudgetEntity("전체" 카테고리)에 현재 월 기준으로 저장, 홈 차트 예산 곡선에 자동 반영

### Fixed (2026-02-21)
- **SMS 캐시 폴백 버그**: `parseWithPatternRegex()` regex 파싱 실패 시 캐시값 폴백 제거 → null 반환. 동일 템플릿 매칭 시 첫 패턴의 가게명/금액이 모든 SMS에 덮어씌워지는 버그 해결
- **하단 탭 높이**: edge-to-edge 환경에서 NavigationBar windowInsets 분리로 64dp 보장
- **탭 전환 시 초기화**: 다른 탭에서 홈/내역 탭 이동 시 오늘 페이지 + 필터 초기화
- **필터 적용 버튼 미노출**: 큰 글꼴 환경에서 카테고리 그리드 weight 기반으로 변경하여 적용 버튼 항상 노출
- **PR #23 코드 리뷰 반영**: 빈 룰 캐싱, 파싱 실패 시 차순위 룰 시도, 중복 승격 방지

### Added (2026-02-20)
- **LLM 생성 regex 샘플 검증**: `validateRegexAgainstSamples()` — regex 생성 후 샘플 SMS에 실제 적용하여 50%+ 파싱 성공 시만 채택, 실패 시 템플릿 폴백
- **RTDB 원격 regex 룰 매칭 시스템**: Step 4에서 로컬 패턴 미매칭 시 2순위로 RTDB 원격 룰 매칭
  - `RemoteSmsRule.kt`: 원격 룰 데이터 클래스 (embedding, regex 3종, minSimilarity=0.94)
  - `RemoteSmsRuleRepository.kt`: RTDB 룰 로드 + 메모리 캐시(TTL 10분) + sender별 그룹핑
  - `SmsPatternMatcher.matchWithRemoteRules()`: 발신번호 필터 → 유사도 매칭 → regex 파싱
  - `SmsPatternMatcher.promoteToLocalPattern()`: 매칭 성공 시 로컬 DB에 parseSource="remote_rule"로 승격
- **RTDB 표본 수집 필드 정리**: 불필요 필드 제거 (ServerValue, 통계 필드 등) + embedding/normalizedSenderAddress 추가
- **DB 메인 그룹 패턴 저장**: `SmsPatternEntity.isMainGroup` 필드 추가 (DB v2→v3)
  - `getMainPatternBySender()` 쿼리로 발신번호별 메인 패턴 조회
  - Step 5 진입 시 DB에서 메인 regex 선조회 → 예외 그룹 regex 생성 시 참조 전달
  - `MainRegexContext`로 메인 regex를 LLM 프롬프트에 포함
  - `senderAddress` normalizeAddress() 적용 (DB 저장/조회 일관성)
- **sms2 마이그레이션 완료**: 배치 동기화 경로를 V1(HybridSmsClassifier/SmsBatchProcessor)에서 sms2(SmsSyncCoordinator)로 전면 전환
  - `SmsSyncCoordinator.kt`: 유일한 외부 진입점 (process → SmsPreFilter → SmsIncomeFilter → SmsPipeline)
  - `SmsReaderV2.kt`: SMS/MMS/RCS 통합 읽기 → List\<SmsInput\> 직접 반환 (SmsMessage 중간 변환 제거)
  - `SmsIncomeFilter.kt`: PAYMENT/INCOME/SKIP 3분류 (financialKeywords 46개)
  - `SmsIncomeParser.kt`: 수입 SMS 파싱 Object 싱글톤 (금액/유형/출처/날짜시간)
  - `syncIncremental()` + `calculateIncrementalRange()`: HomeScreen용 증분 동기화 래퍼

### Changed (2026-02-20)
- **syncSmsV2() 오케스트레이터 전환**: 내부를 5개 private 메소드로 분리 (readAndFilterSms, processSmsPipeline, saveExpenses, saveIncomes, postSyncCleanup)
- **HomeScreen 호출부 변경**: syncSmsMessages → syncIncremental (2곳)

### Removed (2026-02-20)
- **syncSmsMessages() 삭제**: HomeViewModel에서 V1 동기화 메소드 전체 제거 (~400줄)
- **SmsBatchProcessor DI 제거**: HomeViewModel 생성자에서 제거
- **launchBackgroundHybridClassification() 삭제**: V1 배경 분류 메소드 제거

### Added (2026-02-19)
- **GeneratedSmsRegexParser**: LLM 생성 정규식 파서 신규 추가 (group1 캡처 규약, 폴백 체인)
- **SmsBatchProcessor 발신번호 2레벨 그룹핑**: 37그룹 → 2~4그룹으로 대폭 감소
- **SmsBatchProcessor LLM 병렬화**: async + Semaphore로 배치 간 병렬 실행
- **SmsBatchProcessor 가드레일 3종**: template_regex 신뢰도 하향(0.85), 소그룹 병합 유사도 검증(≥0.70), RTDB 업로드 품질 게이트
- **SmsEmbeddingService {STORE} 플레이스홀더**: 줄바꿈 형식 SMS에서 가게명 줄 자동 치환
- **SmsPatternEntity regex 필드**: amountRegex/storeRegex/cardRegex/parseSource 4개 필드 추가 (DB v5→v6)
- **SMS_PARSING.md RTDB 정규식 로드맵**: Section 16 추가 (정규식 다운로드 구조 설계)
- **SmsFilter 발신자 조건부 제외**: 010/070 발신자 SMS를 조건부 제외 (금융 힌트 있으면 보존)
  - `SmsFilter.normalizeAddress()`: +82→0 변환, 하이픈/공백 제거
  - `SmsFilter.hasFinancialHints()`: 금액 패턴 + 카드/은행 키워드 검출
  - SMS/MMS/RCS 모든 채널에 통일 적용
- **LLM 트리거 0.80 정책**: 벡터 유사도 0.80~0.92 구간에서 LLM 호출 (결제 판정 아님, 확인 요청)
  - `SmsPatternSimilarityPolicy.LLM_TRIGGER_THRESHOLD = 0.80f`
  - batchClassify 3-tier: ≥0.95 캐시, 0.92~0.95 확정, 0.80~0.92 LLM 트리거
- **Regex 오파싱 방어**: storeName='결제'(기본값) 시 Tier 1 결과 거부 → Tier 2/3 이관
- **배치 분류 관측성 로그**: tier별 카운트, 사전필터 수, LLM 호출 수 출력
- **core/sms 패키지 분리**: SmsParser, SmsReader, HybridSmsClassifier, SmsBatchProcessor, VectorSearchEngine, SmsEmbeddingService, GeminiSmsExtractor를 `core/util` → `core/sms`로 이동

### Changed (2026-02-19)
- **초기 동기화 2개월 축소**: DEFAULT_SYNC_PERIOD_MILLIS = 60일 (기존 3개월)
- **광고 시청 후 월별 동기화**: "전체 데이터 가져오기" → 현재 페이지의 해당 월만 동기화
  - `HomeViewModel.syncMonthData(year, month)` + `calculateMonthRange()` 추가
  - HomeScreen/HistoryScreen에서 이미 해제된 경우 해당 월만 재동기화

### Added (이전 2026-02-19)
- **빈 상태 전체 동기화 CTA**: 3개월 이전 빈 페이지에서 "광고 보고 전체 데이터 가져오기" CTA 표시 (Home/History)
- **FullSyncCtaSection 공용 Composable**: 전체 동기화 해제 CTA 공통 컴포넌트
- **탭 재클릭 → 오늘 페이지 이동**: 홈/내역 탭 재클릭 시 현재 월 페이지로 animateScrollToPage
- **HorizontalPager 월별 독립 페이지 캐시**: Home/History 화면에 beyondViewportPageCount=1 기반 페이지별 캐시 적용
- **MonthPagerUtils 유틸**: MonthKey, pageToYearMonth, adjacentMonth, isFutureMonth
- **HomePageContent Composable 분리**: HorizontalPager 내부 렌더링 단위 추출
- **초기 동기화 3개월 제한**: 첫 동기화 시 최근 3개월만 읽기 (THREE_MONTHS_MILLIS=90일)
- **리워드 광고 전체 동기화 해제**: 광고 시청 후 FULL_SYNC_UNLOCKED=true → 전체 기간 동기화 가능
- **전체 동기화 해제 다이얼로그**: HomeScreen/HistoryScreen에 광고 안내 AlertDialog

### Changed (2026-02-19)
- **DonutChartCompose**: 불필요한 rotate 애니메이션 제거 (즉시 렌더링), displayLabel 추가
- **SwipeToNavigate 제거**: HorizontalPager 네이티브 스와이프로 대체
- **광고 로드/표시 실패 시 보상 처리**: Home/History/Chat 모든 onFailed에서 보상 지급 (앱 이슈 = 유저 책임 아님)
- **홈 새로고침 깜빡임 제거**: refreshData()에서 캐시 클리어 제거 + isLoading 조건부 설정
- **"오늘 문자만 동기화" 메뉴 제거**: HomeScreen 새로고침 드롭다운에서 삭제

### Added (2026-02-19 후반)
- **SMS 통합 파이프라인 (core/sms2/)**: 기존 3경로 파편화 SMS 처리를 단일 파이프라인으로 통합 (6개 파일)
  - `SmsPipeline.kt`: 오케스트레이터 (Step 2→3→4→5)
  - `SmsPreFilter.kt`: 사전 필터링 (키워드 + 구조)
  - `SmsTemplateEngine.kt`: 템플릿화 + Gemini Embedding API
  - `SmsPatternMatcher.kt`: 벡터 매칭 + regex 파싱 (자체 코사인 유사도)
  - `SmsGroupClassifier.kt`: 그룹핑 + LLM + regex 생성 + 패턴 DB 등록
  - `SmsPipelineModels.kt`: 데이터 클래스 (SmsInput, EmbeddedSms, SmsParseResult)
- **SmsParser KB 출금 유형 확장**: FBS출금 (카드/페이 자동이체), 공동CMS출 (보험 CMS) 지원
- **SmsParser 보험 카테고리 키워드**: 삼성화, 현대해, 메리츠, DB손해, 한화손해, 흥국화 추가

### Fixed (2026-02-19)
- **레거시 FULL_SYNC_UNLOCKED 호환성**: 기존 FULL_SYNC_UNLOCKED=true 사용자가 월별 동기화 업데이트 후 CTA가 다시 표시되는 regression 수정
  - HomeUiState/HistoryUiState에 `isLegacyFullSyncUnlocked` 필드 추가
  - isMonthSynced()/isPagePartiallyCovered()에서 레거시 전역 해제 상태 체크
- **Android Auto Backup 복원 감지**: 앱 재설치 시 DataStore lastSyncTime이 복원되어 동기화 범위가 잘못되는 버그 수정
- **DataStore 백업 제외**: backup_rules.xml, data_extraction_rules.xml에서 DataStore preferences 백업 제외
- **내역 수입 0원 표시**: HistoryHeader에서 수입이 0일 때 섹션이 사라지는 버그 수정
- **SMS 100자 초과 필터 누락**: HybridSmsClassifier.batchClassify() Step 2에서 100자 초과 SMS가 Vector/LLM으로 전달되던 문제 수정

### Added (2026-02-18)
- **Firebase Analytics 트래킹**: AnalyticsHelper(@Singleton) + AnalyticsEvent 상수로 화면 PV 및 클릭 이벤트 수집
  - 화면 PV: home, history, chat, settings (LaunchedEffect 중앙 집중 방식)
  - 클릭 이벤트: syncSms, sendChat, categoryFilter, backup, restore, themeChange (4개 ViewModel)
- **Gemini API 키 풀링**: geminiApiKeys 배열 + 라운드로빈 분산 (PremiumManager)
- **Gemini 모델명 원격 관리**: GeminiModelConfig 8개 모델, Firebase RTDB `/config/models/`에서 실시간 반영
- **Firebase RTDB 기반 강제 업데이트**: ForceUpdateChecker + MainActivity 다이얼로그 (닫기 불가, 업데이트/종료)
- **개인정보처리방침 웹 페이지**: `docs/privacy-policy.html` (한/영 전환, GitHub Pages용)
- **docs-sync 스킬**: 작업 전/후 docs/ 문서 확인·갱신 스킬

### Changed (2026-02-18)
- **ProGuard(R8) 활성화**: isMinifyEnabled=true, isShrinkResources=true (릴리스 빌드)
- **ProGuard keep 규칙 보강**: Gson 모델 17개 keep + entity 경로 버그 수정 + Apache HTTP dontwarn
- **Gradle JVM heap 증가**: 1024m → 2048m (R8 빌드 OOM 방지)
- **버전 1.1.0**: versionCode=2, versionName="1.1.0" (API 키 풀링 + 모델 원격 관리)
- **PremiumConfig 확장**: geminiApiKeys, GeminiModelConfig, minVersionCode/minVersionName/forceUpdateMessage 필드 추가
- **BuildConfig.GEMINI_API_KEY 폐기**: PremiumManager.getGeminiApiKey()로 일원화
- **GIT_CONVENTION.md 강화**: 커밋 본문 권장 템플릿(문제→원인→조치→검증) + Kotlin Android 특화 체크리스트

### Fixed (2026-02-18)
- **proguard-rules.pro 경로 버그**: `core.db.entity` → `core.database.entity` (실제 패키지 경로로 수정)

### Changed (2026-02-16)
- **분류 상태 관리 안정화**: `ClassificationState`를 activeJob 기반으로 정리하고 `registerJob/completeJob/cancelIfRunning` 흐름으로 통일
- **백그라운드 분류 종료 처리 개선**: HomeViewModel에서 Job 완료 시점 기반 상태 해제로 경합 가능성 완화
- **내역 헤더 정렬 개선**: PeriodSummaryCard의 지출/수입 금액 영역 최소 폭(`120.dp`) 적용으로 정렬/노출 일관성 확보

### Fixed (2026-02-16)
- **전체 데이터 삭제 시 분류 작업 정리**: 진행 중인 백그라운드 분류 작업 즉시 취소 처리
- **SmsReader Lint Range 이슈**: cursor 컬럼 인덱스 가드 추가로 `getColumnIndex` 관련 잠재 오류 제거
- **AndroidManifest Lint 이슈**: `android.hardware.telephony`를 optional 처리해 ChromeOS 호환 lint 이슈 해소
- **문자열 포맷/번역 이슈 정리**: `history_day_header` positional format 적용 + `values-en` 누락 번역 키 보강

### Added (2026-02-14 이후)
- **safe-commit 스킬**: `.claude/skills/safe-commit/SKILL.md` 추가 (셀프 리뷰 후 안전 커밋)
- **홈→내역 카테고리 네비게이션**: 홈에서 카테고리 클릭 시 내역 화면으로 이동
- **AI 인사이트 분리**: HomeScreen에서 AI 인사이트 카드 별도 컴포넌트화
- **AI 인사이트 전월 비교**: 프롬프트에 전월 카테고리별 비교 데이터 추가
- **가맹점 일괄 카테고리 업데이트**: 카테고리 변경 시 동일 가맹점 일괄 업데이트

### Changed (2026-02-14 이후)
- **Compose Stability 최적화**: @Immutable/@Stable 어노테이션, 릴리스 DB 안전성 개선
- **대형 파일 분할**: Repository 추상화 (Interface + Impl 분리)
- **하드코딩 문자열 제거**: strings.xml로 이전

### Fixed (2026-02-14 이후)
- **달력 뷰 카테고리 필터 버그**: dailyTotals/monthlyTotal에 카테고리 필터 미적용 수정
- **수입 필터 버그 수정**

### Added (2026-02-14)
- **History 필터 초기화 버튼**: FilterBottomSheet 상단에 조건부 "초기화" 버튼
  - 필터가 기본값이 아닐 때만 표시
  - 클릭 시 정렬/거래유형/카테고리 모두 기본값으로 리셋
- **벡터 학습 실패 시 스낵바 알림**: HomeViewModel에서 학습 실패 시 사용자에게 알림 표시

### Changed (2026-02-14)
- **Phase 2-A**: HybridSmsClassifier에서 미사용 `BOOTSTRAP_THRESHOLD` 상수 제거
- **Phase 2-B**: `NON_PAYMENT_CACHE_THRESHOLD` 0.97 유지 결정 (0.95 완화 시 오분류 리스크)
- **Phase 2-D**: ChatViewModel에서 카테고리 변경 액션 성공 시 CategoryReferenceProvider 캐시 자동 무효화

### Added (2026-02-13)
- **Clarification 루프**: 쿼리 분석기가 모호한 질문에 추측 대신 확인 질문 반환
  - DataQueryRequest에 `clarification` 필드 추가
  - ChatViewModel에서 clarification 분기 처리 (쿼리/답변 생성 건너뜀)
  - 사용자 추가 입력 후 대화 맥락 포함 재분석

### Changed (2026-02-13)
- **Financial Advisor 수치 정확성 규칙 강화** (Karpathy Guidelines 적용)
  - 직접 계산 금지 (리스트 합산/평균 금지)
  - 비율 계산 금지 (직접 나눗셈 금지)
  - 데이터 간 교차 계산 금지
- **Query Analyzer clarification 응답 규칙 추가**
  - 기간/대상/의도 불명확 시 clarification JSON 반환
  - 기존 "queries 또는 actions 비어있지 않게" 규칙 제거

### Changed (2026-02-12)
- **History 필터 UI 전면 개편**
  - FilterPanel(가로 3칩) → ModalBottomSheet로 전환 (정렬/거래유형/카테고리)
  - 카드 필터 제거, 수입 탭을 BottomSheet 내 거래유형 토글로 통합
- **PeriodSummaryCard 레이아웃 변경**
  - Card 래퍼 제거, 날짜 왼쪽 + 금액 오른쪽 정렬
  - 날짜 줄넘김 표시 (HorizontalDivider 구분선)
  - 필터 적용 시 상단 총 수입/지출도 필터 기준으로 반영
- **하단 NavigationBar 컴팩트화** (64dp, 아이콘+라벨 세로 중앙정렬)
- **SegmentedTabRow 컴팩트화** (아이콘 지원, 사이즈 축소)
- **필터 버튼**: 아이콘+텍스트 형태로 탭 바로 옆에 배치
- **거래 목록 시간순 정렬**: 같은 날짜 내 수입/지출 통합 최신순 정렬

### Added (2026-02-12)
- **DpTextUnit 유틸**: fontScale 무관 고정 텍스트 크기 (Dp.toDpTextUnit, Int.toDpTextUnit)

### Added (2026-02-09~11)
- **OwnedCard 시스템** (카드 화이트리스트)
  - OwnedCardEntity/Dao/Repository (DB v2→v3)
  - CardNameNormalizer: 25+ 카드사 명칭 정규화
  - SMS 동기화 시 자동 카드 등록 + Settings에서 소유 카드 관리
- **SMS 제외 키워드 시스템** (블랙리스트)
  - SmsExclusionKeywordEntity/Dao/Repository (DB v3→v4)
  - 기본(default) / 사용자(user) / 채팅(chat) 3가지 소스
- **DB 성능 인덱스** (v4→v5): expenses/incomes 테이블 인덱스
- **ANALYTICS 쿼리 타입**: 클라이언트 사이드 복합 분석
- **SMS 제외 채팅 액션**: add_sms_exclusion, remove_sms_exclusion
- **전역 스낵바 버스** (DataRefreshEvent 기반)
- **API 키 설정 후 저신뢰도 항목 자동 재분류**
- **UI 공통 컴포넌트**: TransactionCard, TransactionGroupHeader, SegmentedTabRow

### Refactored (2026-02-09~11)
- **프롬프트 XML 이전**: ChatPrompts.kt → string_prompt.xml (6종)
- **HistoryScreen Intent 패턴**: HistoryIntent sealed interface
- **FINANCIAL_ADVISOR 할루시네이션 개선**

### Added (2026-02-08)
- **계좌이체 카테고리 추가** (TRANSFER 🔄)
  - 계좌번호(**패턴) + "출금" 키워드 자동 감지
  - Gemini 분류 프롬프트 및 매핑 테이블 연동
- **보험 카테고리 복원** (INSURANCE 🛡️)
- **메모 기능** (지출/수입 모두 지원)
  - 상세 다이얼로그에서 메모 편집 (클릭 → 편집 다이얼로그)
  - 검색 시 메모 내용 포함
  - AI 채팅 조회 시 메모 표시
  - DB 마이그레이션 v1→v2 (incomes 테이블 memo 컬럼 추가)
- **홈 화면 지출 삭제 기능** (상세 다이얼로그에서 삭제 가능)
- **새 카테고리 4개 추가** (총 14개 카테고리)
  - 술/유흥 (DRINKING) 🍺: 술집, 바, 호프, 노래방
  - 운동 (FITNESS) 💪: 헬스장, 피트니스, 필라테스
  - 주거 (HOUSING) 🏢: 월세, 전세, 관리비
  - 경조 (EVENTS) 🎁: 축의금, 조의금, 선물
- **SMS 수입 파싱 기능**
  - `SmsParser.isIncomeSms()`: 입금 문자 판별
  - `SmsParser.extractIncomeAmount()`: 입금 금액 추출
  - `SmsParser.extractIncomeType()`: 입금 유형 추출 (급여, 이체, 환급 등)
  - `SmsParser.extractIncomeSource()`: 송금인/출처 추출
  - `SmsReader.readAllIncomeSms()`: 전체 수입 SMS 읽기
  - `SmsReader.readIncomeSmsByDateRange()`: 기간별 수입 SMS 읽기
- **IncomeEntity 필드 확장**
  - `smsId`: SMS 고유 ID (중복 방지)
  - `source`: 송금인/출처
  - `originalSms`: 원본 SMS 메시지
- 로컬 SMS 파싱 기능 (`SmsParser.kt`)
  - `parseSms()`: SMS 전체 파싱 (API 호출 없이 로컬 처리)
  - `extractStoreName()`: 가게명 추출
  - `extractDateTime()`: 날짜/시간 추출
  - `inferCategory()`: 카테고리 자동 추론
- Lottie 애니메이션 지원 추가
- README.md 문서 작성

### Refactored
- **VectorSearchEngine 책임 분리 (Phase 1 완료)**
  - Vector(연산) → Policy(판단) → Service(행동) 3계층 구조
  - core/similarity/ 패키지 신설: SimilarityPolicy, SmsPatternSimilarityPolicy, StoreNameSimilarityPolicy, CategoryPropagationPolicy
  - 모든 유사도 임계값을 SimilarityPolicy SSOT로 통합
- **Claude 레거시 코드 완전 제거**
  - ClaudeApi.kt, ClaudeRepository.kt, ClaudeModels.kt, PromptTemplates.kt 삭제
  - Retrofit 의존성 제거 (OkHttp만 유지)
  - NetworkModule에서 Retrofit/ClaudeApi DI 제거
  - HomeViewModel에서 ClaudeRepository 의존성 제거
- **SmsAnalysisResult를 core/model/로 분리** (ClaudeModels.kt에서 독립)
- **ExpenseRepository/ExpenseDao 미사용 메서드 5개 제거**
  - getExpensesByCardName, getExpensesByCardNameAndDateRange, getExpensesByCategoryAndDateRange, getExpensesByStoreNameAndDateRange, getTotalExpenseByStoreName
- **Tier 1.5b 그룹 기반 벡터 매칭 추가** (다수결, 유사도 ≥ 0.88)
- **CategoryReferenceProvider 신설** (동적 참조 리스트 → 모든 LLM 프롬프트 주입)
- **AI 채팅 액션 시스템 확장**
  - delete_by_keyword, add_expense, update_memo, update_store_name, update_amount 5개 액션 추가
  - FINANCIAL_ADVISOR 할루시네이션 방지 규칙 추가
- **SMS 동기화/카테고리 분류 다이얼로그 진행률 표시 개선**
  - 스피너 + 진행률 바 + "N/M건" 텍스트 실시간 업데이트
  - groupBySimilarity suspend 함수 변경 (yield로 UI 블로킹 방지)

### Changed
- **Gemini 카테고리 분류 개선**
  - 단일 카테고리만 반환하도록 프롬프트 강화
  - 괄호 안 추가 정보 파싱 시 자동 제거 (`보험 (의료/건강)` → `기타`)
  - 보험회사는 "기타"로 분류하도록 명시
  - 카테고리 매핑 테이블 확장 (술/유흥, 운동, 주거, 경조)
- **의미 없는 가게명 필터링 강화**
  - "국외발신", "해외발신" 등 발신 코드 제외
  - `KB]날짜시간` 형식 패턴 제외
  - 랜덤 문자열 (영문+숫자 5~8자) 제외
  - 보험/금융 코드 (`삼성화08003` 등) 제외
  - 카드번호 형식 (`롯데카드2508` 등) 제외
- SMS 파싱 방식 변경: Claude API → 로컬 정규식
  - `HomeViewModel.syncSmsMessages()` 수정
  - API 비용 절감, 오프라인 동작 가능

### Fixed
- **SMS 파싱 버그 3건 수정**
  - 결제예정 금액 SMS가 지출로 잡히는 문제 (excludeKeywords에 "결제금액" 추가)
  - 신한카드 승인 SMS에서 이름이 가게명으로 파싱되는 문제 (금액+일시불+시간 뒤 가게명 패턴 추가, 마스킹 이름 필터)
  - 계좌이체 출금 내역 카테고리 미분류 문제 (계좌이체 카테고리 자동 감지)
- Gradle 버전 호환성 문제 수정
  - AGP: 8.2.2
  - Kotlin: 1.9.22
  - KSP: 1.9.22-1.0.17
  - Gradle: 8.5
  - Compose Compiler: 1.5.10

---

## 버전 히스토리

### v1.0.0 (Initial)
- 초기 프로젝트 설정
- SMS 읽기 및 필터링 기능
- Room 데이터베이스 설정
- Claude API 연동 (AI 상담)
- Jetpack Compose UI 구현
