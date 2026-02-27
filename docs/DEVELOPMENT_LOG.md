# 머니톡 (MoneyTalk) - 개발 로그

> 개발 과정과 변경 사항을 기록하는 문서입니다.

---

## 2026-02-27 - PR #41: Step4.5 복구 경로 최적화 및 프롬프트 안정화

### 배경
PR #40에서 Step5 정책이 고도화되었으나, Step4.5(regex 실패건 복구)는 직렬 처리 + 품질 게이트 없이 모든 실패건을 LLM에 전달하는 상태. 프롬프트도 근거 부족 SMS에 대한 처리가 미흡. 누락 SMS 디버깅 수단 부재.

### 작업 내용

#### Step4.5 병렬화 + 품질 게이트 (커밋 2a428f9)
- **병렬 처리**: `Semaphore(LLM_CONCURRENCY)` + `async(Dispatchers.IO)` + `awaitAll()` — 직렬→병렬 전환
- **복구 가능성 판정**: `isRecoverableRegexFailedSms()` — SMS 길이≥12, 금액 패턴 + 힌트 키워드 확인
- **근거 검증**: `isGroundedRegexFailedRecovery()` — 금액 증거(containsAmountEvidence) + 가게명 부분문자열 매칭
- **실패 쿨다운**: `regexFailedRecoveryStates` — `sender:templateHash` 키, 2회 실패→30분 쿨다운
- **KB 멀티라인 출금 휴리스틱**: `tryParseKbDebitFallback()` — 정규식으로 KB 스타일 출금 SMS 직접 파싱 (LLM 스킵)

#### Step5 경로 분리 (커밋 023cf71, 9707f4b, 42a9760)
- **5-A (unmatched)**: full regex policy — 기존 그대로
- **5-B (regexFailedFallback)**: `forceSkipRegexAll=true` — regex 재생성 스킵 (이미 실패한 regex 재시도 방지)
- outcome 코드 구분: `REGEX_FAILED_HEURISTIC_KB`, `REGEX_FAILED_FALLBACK_DIRECT_LLM`
- 단건 LLM null 시 1회 재시도 (`GROUP_LLM_NULL_RETRY_MAX=1`, 300ms delay)

#### 프롬프트 강화 (커밋 1394ff6, 4de73b3)
- 근거 부족 문자 처리 규칙 (rules 7-10), 예외 규칙 추가
- `SMS_EXTRACT_PROMPT_VERSION` / `SMS_BATCH_PROMPT_VERSION` 로깅

#### 누락 SMS 드롭 경로 진단 (커밋 d90a3b2)
- SmsPipeline: parsedIds vs embedded 차집합 추적 → 드롭된 SMS 원문 로그

#### SMS 누락 복구 + insight 재시도 + 메모 표기 (커밋 b8485f7)
- **Home insight 재시도**: `HOME_INSIGHT_MAX_ATTEMPTS=3`, 503/UNAVAILABLE 시 선형 백오프 (1200ms*attempt)
- **TransactionCard 메모 표시**: `memoText` 필드 추가 + `buildAnnotatedString` (alpha=0.72f)
- `TransactionCardInfo`에 `memoText: String?` 프로퍼티 (default null, expense.memo 매핑)

#### 코드 정리 (커밋 9b03917)
- `tryHeuristicParse()` 헬퍼 추출 — 4곳 중복 `tryParseKbDebitFallback()` + `SmsParseResult` 생성 통합
- `DEBIT_FALLBACK_STORE_NAME` 상수 추출 (SmsPatternMatcher)
- outcome 코드 `REGEX_FAILED_HEURISTIC_KB` 구분 (휴리스틱 vs LLM 경로)

### 변경 파일 (PR #41: 9 files)
- `core/sms2/SmsGroupClassifier.kt` — Step4.5 병렬화, 품질 게이트, KB 휴리스틱, tryHeuristicParse 헬퍼
- `core/sms2/SmsPatternMatcher.kt` — DEBIT_FALLBACK_STORE_NAME 상수
- `core/sms2/SmsPipeline.kt` — Step5 경로 분리(5-A/5-B), 드롭 경로 진단 로그
- `core/sms2/GeminiSmsExtractor.kt` — 프롬프트 버전 로깅
- `core/sms2/SmsPreFilter.kt` — (미변경, 원복)
- `res/values/string_prompt.xml` — 근거 부족 규칙, 예외 규칙
- `feature/chat/data/GeminiRepositoryImpl.kt` — insight 재시도 로직
- `core/ui/component/transaction/card/TransactionCardCompose.kt` — 메모 표시
- `core/ui/component/transaction/card/TransactionCardInfo.kt` — memoText 필드

### 결정 사항
- **Step4.5 품질 게이트 도입 이유**: 모든 regex 실패건을 LLM에 보내면 비용 과다 + 근거 없는 SMS(잔액 알림 등)까지 추출 시도 → 복구 가능성 판정으로 LLM 호출 최소화
- **forceSkipRegexAll=true**: regex가 이미 실패한 패턴에 다시 regex를 생성하는 것은 무의미. LLM 직접 추출만 시도
- **KB 휴리스틱**: KB 멀티라인 출금은 형식이 고정되어 정규식으로 확실하게 파싱 가능. LLM 호출 없이 즉시 처리

---

## 2026-02-27 - PR #40: Step5 SMS 파싱 정책/예산/학습 분리 고도화

### 배경
PR #38에서 Step5의 기본 성능 최적화(시간 제한, 최소 샘플)를 적용했으나, 여전히 비효율적인 패턴이 존재: 이질적 SMS가 섞인 unstable 그룹에 regex 생성 시도, 시간 예산 초과 시 데이터 누락, 성공한 regex 패턴의 즉시 등록으로 인한 동기화 병목.

### 작업 내용

#### Phase1: regex 정책 강화 + 시간 예산 (커밋 5876f3a)
- `REGEX_MIN_SAMPLES=5`: 5건 미만 그룹은 regex 생성 스킵 (비용 대비 효과 낮음)
- `GROUP_TIME_BUDGET_MS=10초`: 단일 그룹 처리 시간 제한
- `STEP5_TIME_BUDGET_MS=20초`: Step5 전체 시간 제한
- 계측 로그: 그룹별 처리 시간/결과 상세 출력

#### Phase2: 예산 초과 시 스킵→강등 (커밋 71a7d55)
- 기존: 시간 예산 초과 시 그룹 스킵 → 데이터 누락
- 변경: regex 생성 포기 + LLM 직접 추출로 강등 → 데이터 보존
- 가성비: regex 생성(~5초) vs LLM 직접 추출(~1초) 트레이드오프

#### Phase3: unstable 그룹 정책 + cohesion gate (커밋 dd75187, 584cc4d)
- **unstable 판정**: 그룹 내 유사도 중앙값 < 0.92 또는 P20 < 0.88 → 이질적 SMS 혼입
- **unstable 처리**: 그룹 해체 → 개별 LLM 추출 (max 10건, 초과분 스킵)
- **cohesion gate**: regex 생성 전 그룹 응집도 검증 — 낮으면 regex 생성 abort
- E-lite 모델 분리: unstable 개별 추출에 경량 모델 사용

#### Phase4: 백그라운드 학습 큐 (커밋 3283d02)
- `DeferredLearningTask`: regex 등록을 즉시→비동기로 전환
- `learningQueue` + `learningQueueSize` + `learningDroppedCount`: 큐 관리
- `LEARNING_QUEUE_MAX_SIZE=1000`: 큐 오버플로우 방지
- `learningProcessorRunning`: 단일 프로세서 보장

#### Phase5: outcome 집계 + 학습 큐 dedup (커밋 275098f, 8464f7a)
- outcome별 집계 로그: matched/regexFailed/llmDirect/unstable/skipped 카운트
- `learningDedupKeys`: ConcurrentHashMap.newKeySet() 기반 in-flight 중복 방지

### 변경 파일 (PR #40: 1 file, 7 commits)
- `core/sms2/SmsGroupClassifier.kt` — 전체 변경

### 결정 사항
- **SmsGroupClassifier 단일 파일 PR**: 모든 Step5 정책이 이 파일에 집중되어 있어 분리 효과 낮음
- **강등 정책**: 데이터 누락(=사용자가 직접 입력해야 함)보다 LLM 비용이 낮다고 판단
- **unstable 해체 기준(0.92/0.88)**: 실측으로 이 수치 미만 그룹은 regex 성공률이 <20%였음

---

## 2026-02-27 - SMS Step5 성능 최적화 + Step4.5 배치 LLM 복구 경로

### 배경
데이터 초기화 후 재동기화 시, 패턴 DB에 임베딩은 남아있지만 regex가 비거나 파싱 실패하는 패턴이 존재. 이 SMS들은 Step4 벡터매칭(≥0.92)을 통과하지만 `parseWithPatternRegex` 실패 → Step5로 떨어짐. Step5에서 regex 재생성을 시도하지만 또 실패(STORE_INVALID_KEYWORDS에 "출금","카드" → isValidStoreCandidate 탈락) → 매 동기화마다 25초 낭비하는 무한 루프.

실측: KB출금 SMS 26건이 매번 Step5로 빠져 25.2초 소요 (전체 28초 중 90%).

### 작업 내용

#### Step5 성능 최적화 (커밋 b339245)
- **GeminiSmsExtractor**: LLM 타임아웃 60초 설정(3개 모델), CancellationException을 7개 generic catch보다 먼저 캐치, isPayment=false 조기 리턴, 디버그 로깅 추가
- **SmsGroupClassifier**: REGEX_MIN_SAMPLES=5 (소그룹 regex 생성 스킵), REGEX_TIME_BUDGET_MS=15초 (시간 제한), REGEX_NEAR_MISS_MIN_RATIO=0.4f (near-miss 그룹 regex 재시도 기준), dead code 제거, LLM 배치 호출 시 drop(1) 오류 수정
- **SmsPipeline**: Step5 진행률 콜백(step, current, total) 연동

#### Step4.5 regex 실패건 배치 LLM 복구 (커밋 43de46e)
- **SmsPatternMatcher**: `MatchResult` data class 도입 — 3-way 분할 (matched/regexFailed/unmatched)
  - 벡터매칭 OK + regex 파싱 실패 → `regexFailed` (기존: `unmatched`에 섞임)
  - stats 로그에 `regexFailed` 포함
- **SmsGroupClassifier**: `batchExtractRegexFailed()` 신규 메서드
  - 발신번호별 그룹핑 → chunk(10) → `smsExtractor.extractFromSmsBatch()` 배치 LLM 추출
  - 성공: SmsParseResult(tier=3) 반환 / 실패: Step5 fallback 리스트로 전달
- **SmsPipeline**: Step4→Step4.5→Step5 체인 삽입, `PipelineResult.regexFailedRecoveredCount` 추가
- **SmsPipelineModels**: `SyncStats.regexFailedRecoveredCount` 추가
- **SmsSyncCoordinator**: stats 매핑
- **MainViewModel**: `engineSummaryPatterns`에 `regexFailedRecoveredCount` 합산

### 변경 파일 (PR #38: 8 files, +248/-126)
- `core/sms2/SmsPatternMatcher.kt` — MatchResult 3-way 분할
- `core/sms2/SmsGroupClassifier.kt` — batchExtractRegexFailed() + Step5 최적화 상수
- `core/sms2/SmsPipeline.kt` — Step4.5 삽입
- `core/sms2/SmsPipelineModels.kt` — SyncStats 필드 추가
- `core/sms2/SmsSyncCoordinator.kt` — stats 매핑
- `core/sms2/GeminiSmsExtractor.kt` — 타임아웃, 예외 처리, 디버그 로깅
- `MainViewModel.kt` — engineSummaryPatterns 합산
- `CLAUDE.md` — MoneyTalkLogger 로깅 규칙 추가

### 결정 사항
- **regex 실패건은 Step5(그룹핑+regex생성)를 건너뛰고 배치 LLM 추출**: regex가 이미 실패한 패턴에 다시 regex 생성을 시도하는 것은 무의미. 직접 LLM으로 추출하는 것이 확실하고 빠름 (~3초 vs 25초)
- **Step4.5 LLM 실패건은 Step5로 fallback**: 데이터 손실 없음
- **SmsPipeline `.e()` 로그 레벨 유지**: 사용자가 가시성 목적으로 의도적으로 사용 — 변경하지 않음
- **Codex P1 리뷰(그룹 LLM blended 위험)**: 같은 발신번호+같은 템플릿 그룹이므로 실질적 위험 낮음 → 조치 불필요

---

## 2026-02-26 - generateRegexForGroup 정규식 생성 성공률 개선

### 배경
`SmsGroupClassifier.processGroup()`에서 호출하는 `GeminiSmsExtractor.generateRegexForGroup()`의 regex 생성 실패율이 높아 개별 LLM 호출로 폴백되는 비율이 높았음. 내 분석 + Codex 분석을 교차 검토하여 합의된 6개 개선 항목을 구현.

regex 생성 성공 시 Gemini API 호출이 대폭 줄어듦 (그룹 전체를 regex로 일괄 파싱 → 개별 LLM 호출 불필요).

### 작업 내용

#### 1. repair 1회 활성화
- `REGEX_REPAIR_MAX_RETRIES = 0 → 1` — 기존에 repair 프롬프트가 잘 작성되어 있었으나 0으로 비활성 상태
- 1회 수선 시도로 검증 실패 regex 구제 가능

#### 2. 검증 일원화
- `GeminiSmsExtractor.validateRegexResult()`에서 샘플 성공률 게이트(80% 기준) 제거
- JSON 파싱 + regex 컴파일 + 필수필드 체크만 수행
- 성공률 판정은 `SmsGroupClassifier.validateRegexAgainstSamples()` 60% 기준 1곳에서만
- 이유: 0.5~0.79 사이 regex가 Extractor에서 버려지지 않고 Classifier 검증까지 도달 가능

#### 3. 프롬프트 개선
- 시스템 프롬프트: JSON escape 규칙(rule 8,9), 멀티라인 SMS 예시(예시2), 샘플 수 "1~5건"→"1~10건"
- 유저 프롬프트: `buildRegexPrompt()`에서 날짜 prefix 제거 (LLM이 날짜를 패턴 일부로 오인 방지)
- compact 프롬프트: cardRegex를 %3$s로 추가 (토큰 폴백 시 카드 정보 손실 방지)

#### 4. ultraCompact 3차 폴백 활성화
- `requestRegexWithTokenFallback()`: primary → compact → ultraCompact → 포기 (기존: compact에서 포기)

#### 5. repair 프롬프트 인간 친화적 사유 매핑
- `toHumanFriendlyReason()`: 기술적 코드(예: "amount_regex_compile_failed")를 LLM이 이해하기 쉬운 메시지로 변환
- 문자열은 `strings.xml` + `values-en/strings.xml`에 리소스화 (하드코딩 금지 규칙 준수)

#### 6. 미사용 코드 정리 (사용자 추가 작업)
- 검증 일원화로 dead code가 된 항목 제거: `REGEX_MIN_AMOUNT`, `NON_DIGIT_PATTERN`, `STORE_*` 패턴/키워드, `isValidRegexStoreName()`, `tryExtractGroup1()`, `RegexValidationResult.successRatio`
- `minSuccessRatio` 파라미터 제거 (`generateRegexForGroup`, `generateRegexForSms`)

### 변경 파일
- `core/sms2/GeminiSmsExtractor.kt` — 상수/검증/폴백/프롬프트/코드정리 (85 insertions, 109 deletions)
- `res/values/string_prompt.xml` — 시스템 프롬프트 규칙 추가, compact 포맷 변경
- `res/values/strings.xml` — sms_regex_reason_* 6개 키 추가
- `res/values-en/strings.xml` — sms_regex_reason_* 6개 키 추가 (영문)

### 결정 사항
- `smsTimestamps` 파라미터는 public API 호환성 유지를 위해 `@Suppress("UNUSED_PARAMETER")`로 남김 (날짜 prefix 제거로 미사용이 되었으나, 호출자 변경 없이 유지)
- 검증 기준을 1곳(Classifier)으로 통합한 이유: Classifier의 `parseWithRegex()`가 실제 런타임과 동일 경로를 타므로 더 정확한 검증

---

## 2026-02-26 - SMS 파싱 파이프라인 순서/효율 개선

### 작업 내용

#### P1 (Critical): SmsPreFilter 수입 SMS 누락 방지
- `SmsPreFilter.isObviouslyNonPayment()`에 수입 보호 화이트리스트 추가
- `INCOME_PROTECTION_KEYWORDS` ("입금","급여","월급","환급","송금","정산","지급","출금취소","승인취소","결제취소")
- 수입 키워드가 포함된 SMS는 NON_PAYMENT_KEYWORDS 체크를 스킵하여 SmsIncomeFilter로 전달

#### P5: 취소/환불 SMS 타입 정확도 개선
- `SmsIncomeParser.extractIncomeType()`에 취소 분기 추가
- "출금취소","승인취소","결제취소","취소승인","취소완료" → type="환불" (기존: "입금")

#### P4: 미사용 fallbackCategory 파라미터 제거
- `SmsPatternMatcher.parseWithRegex()`에서 `fallbackCategory` 파라미터 제거
- 내부적으로 항상 `category="미분류"`로 하드코딩되어 파라미터가 실효 없었음
- `SmsGroupClassifier`의 호출부 2곳 + `parseWithPatternRegex()` 1곳 정리

#### classify() 순서 수정 (사용자 발견)
- `SmsIncomeFilter.classify()`에서 incomeExcludeKeywords(step5)를 paymentKeywords(step6) 앞으로 이동
- "자동이체출금"에 "출금"이 포함되어 PAYMENT로 오분류되던 문제 방지

#### 중복 코드 정리 (P2/P3)
- SmsIncomeFilter의 excludeKeywords, MAX_SMS_LENGTH가 SmsPreFilter와 중복 → 방어 코드 주석 추가
- KDoc 번호 순서 정정 (classify() 내부 단계 번호)

### 변경 파일
- `core/sms2/SmsPreFilter.kt` — INCOME_PROTECTION_KEYWORDS + isObviouslyNonPayment() 화이트리스트
- `core/sms2/SmsIncomeFilter.kt` — KDoc 정정, 방어 코드 주석, classify() 순서 수정
- `core/sms2/SmsIncomeParser.kt` — extractIncomeType() 취소→"환불" 분기
- `core/sms2/SmsPatternMatcher.kt` — parseWithRegex() fallbackCategory 제거
- `core/sms2/SmsGroupClassifier.kt` — fallbackCategory 호출 인자 제거

### 결정 사항
- SmsIncomeFilter의 중복 키워드/길이체크는 "방어 코드"로 유지 (SmsIncomeFilter 단독 사용 시 안전망)
- SmsPreFilter의 화이트리스트 방식 채택 (키워드 제거 방식보다 안전 — 새 비결제 키워드 추가 시 수입 누락 재발 방지)

---

## 2026-02-26 - SmsGroupClassifier 품질 개선

### 작업 내용

#### 소그룹 병합 유사도 상향 (0.70 → 0.90)
- 서로 다른 SMS 형식(카드 승인 vs 카드 대금 출금)이 하나로 병합되는 문제 방지
- 같은 발신번호(예: KB 16449999)의 "일반 출금"과 "신한카드 대금 출금"이 메인 그룹으로 합쳐져 전체가 "신한카드"로 태깅되는 근본 원인

#### RTDB 표본 수집 품질 개선
- template 필드 추가: 표본에 `embedded.template` 포함
- regex 존재 가드: `amountRegex.isNotBlank() && storeRegex.isNotBlank()` 일 때만 수집 (무용 표본 제거)
- 기존 `hasVerifiedRegex = source in listOf("llm_regex")` 방식이 "template_regex" 소스를 제외하고 "llm" 소스의 빈 regex 표본을 수집하는 버그 수정

#### LLM 프롬프트 개선
- buildContextualLlmInput: "이 SMS는 아래 발신번호의 예외 케이스입니다" → "같은 발신번호의 메인 케이스입니다. 아래를 참조하여 비슷한 형태로 분석하세요."
- "예외 케이스"라는 표현이 LLM에게 다른 형태로 분석하라는 오해를 줄 수 있으므로 "메인 케이스 참조"로 변경

#### MainCaseContext 확장
- `sample: String` → `samples: List<String>` (3건까지 전달)
- buildContextualLlmInput에 메인 템플릿 + 메인 샘플 3건을 포함하여 LLM 분석 정확도 향상
- sampleBody 참조도 `mainContext.samples.firstOrNull()`로 갱신

### 변경 파일
- `SmsGroupClassifier.kt` — SMALL_GROUP_MERGE_MIN_SIMILARITY 0.70→0.90, RTDB template 추가, regex 가드, 프롬프트 개선, MainCaseContext.samples
- `GeminiSmsExtractor.kt` — 프롬프트 디버깅 로그 추가
- `AI_CONTEXT.md` — 임계값 레지스트리 수직 스케일 0.70→0.90 갱신

### 결정 사항
- "출금"만으로 PAYMENT 통과하는 것은 정상 로직 (은행 출금 알림도 실제 지출이므로)
- KB 은행 SMS에 "신한카드"가 표시되는 것은 신한카드 대금이 KB 계좌에서 출금되는 정상 케이스
- 근본 문제는 메인 그룹 대표 SMS가 "카드 대금 출금" 알림일 때 그 cardName이 전체 그룹에 전파되는 구조 — 유사도 상향으로 그룹 분리 유도

---

## 2026-02-25 - 임베딩 차원 축소 (3072 → 768)

### 작업 내용

#### 임베딩 차원 768 변경
- Gemini Embedding API `outputDimensionality=768` 파라미터 설정
- Matryoshka Representation Learning 활용 — 3072→768 축소 시 MTEB 품질 손실 0.26%
- 변경 이유: 저장/전송 크기 75% 절감, 코사인 유사도 연산 75% 감소, RTDB 대역폭 절약

#### 변경 파일 (코드 4개)
- `SmsEmbeddingService.kt` — `EMBEDDING_DIMENSION=768` 상수 추가, embedContent/batchEmbedContents에 outputDimensionality 설정
- `SmsTemplateEngine.kt` — batchEmbedContents에 outputDimensionality 설정
- `DatabaseMigrations.kt` — v3→v4 마이그레이션 (sms_patterns, store_embeddings 데이터 삭제)
- `AppDatabase.kt` — version 3→4, `DatabaseModule.kt` — MIGRATION_3_4 등록

#### 코드 주석 갱신 (6개)
- `RemoteSmsRule.kt`, `SmsGroupClassifier.kt`, `SmsPatternMatcher.kt`
- `SmsPipeline.kt`, `SmsPipelineModels.kt`, `SmsTemplateEngine.kt`

#### 문서 갱신 (6개)
- `SMS_PARSING.md` — 6건 3072→768
- `CATEGORY_CLASSIFICATION.md`, `PROJECT_CONTEXT.md` (2건), `SCREEN_REQUIREMENTS.md`
- `AI_HANDOFF.md` — DB 버전 v3→v4, 완료 작업 추가

### 결정 사항
- 768차원 선택 이유: Gemini의 Matryoshka 지원으로 별도 모델 변경 없이 축소 가능, 0.26% 품질 손실은 SMS/가게명 유사도 매칭에 무시 가능한 수준
- DB 마이그레이션 전략: DELETE (테이블 유지, 데이터만 삭제) — 재동기화 시 768차원으로 자동 재생성
- RTDB 기존 표본 주의: sms_samples에 수집된 3072차원 임베딩은 새 768차원과 호환 불가. 추후 큐레이션 시 768차원으로 재임베딩 필요

---

## 2026-02-25 - PR #33 MainViewModel 리팩토링 리뷰 확인

### 작업 내용

#### PR #33 Codex 리뷰 확인
- `refactor/main-viewmodel` 브랜치 PR #33에 Codex 봇 리뷰 1건 확인
- P2: `onAppResume()`에서 SMS 권한 없을 때 `DataRefreshEvent`를 emit하지 않아 수동 입력 데이터 갱신 누락 가능성 지적
- 분석: 편집 화면(카테고리 변경, 상세 편집 등)은 자체적으로 refresh 이벤트를 발행하므로 실질적 문제 발생 가능성 낮음

#### 매 진입 시 권한 재요청 팝업 검토
- 현재 PermissionScreen은 첫 실행 시 1회만 표시 (onboardingCompleted 마킹)
- 매번 표시 방안 검토 → 기각
  - UX 피로감: 거부한 사용자에게 반복 노출 시 이탈 유발
  - Google Play 정책: "disruptive permissions request" 위반 가능성
  - 시스템 제한: 2회 거부 시 "다시 묻지 않기"로 전환되어 팝업 자체가 안 뜸

### 결정 사항
- Codex P2 리뷰: 현행 유지 (코드 수정 없음)
- 권한 재요청 UX: 현행 유지 (첫 실행 시 1회 + 기능 사용 시 시스템 다이얼로그)

---

## 2026-02-24 - Vico 차트 수정 + 홈 UI 간소화

### 작업 내용

#### Vico 차트 수정
- 가로 스크롤 제거: `AxisValueOverrider.fixed(maxX = daysInMonth - 1)` + `Zoom.Content` 조합으로 모든 데이터를 한 화면에 표시
- X축 라벨 표시: `HorizontalAxis.ItemPlacer.default(spacing=5, addExtremeLabelPadding=true)`로 5일 간격 라벨 + 양 끝 패딩
- 동기화 후 차트 미갱신: `LaunchedEffect` keys에 `daysInMonth`, `todayDayIndex` 추가로 데이터 변경 시 즉시 반영
- Y축 토글 무관 고정: `yAxisMax`를 `CumulativeTrendSection`에서 전체 `toggleableLines` 기준으로 계산 후 `VicoCumulativeChart`에 전달

#### 홈 UI 간소화
- `TodayAndComparisonSection` 제거: 오늘 지출 카드 + 전월 비교 카드 2개 제거. 오늘 지출 금액/건수는 "오늘 내역" 헤더 오른쪽에 인라인 표시
- `FullSyncCtaSection` 홈에서 제거: 빈 데이터 CTA + 부분 데이터 CTA 모두 제거 (HistoryScreen에서는 유지)
- `buildComparisonText` 형식 변경: 누적 추이 비교문구에 금액+퍼센테이지 병기 ("₩50,000(12%) 더 쓰고 있어요")
- 1월 CTA 잘못 표시 수정: `!isMonthSynced` → `isPartiallyCovered`로 전환 (기본 동기화 범위 내 월은 CTA 미표시)

### 결정 사항
- 전월 비교 카드(MonthComparisonCard)의 절대금액 비교 정보는 누적 차트의 비교문구에 통합. 별도 카드 불필요
- FullSyncCtaSection은 공통 컴포넌트로 유지 (HistoryScreen에서 사용), 홈에서만 제거
- Vico Zoom.Content이 차트를 뷰포트에 맞추되, X축 범위는 `maxX = daysInMonth - 1`로 고정하여 월말까지 표시

---

## 2026-02-24 - 홈 화면 Phase1 리디자인 + 디자인 시스템 + Vico 차트

### 작업 내용

#### 디자인 시스템 정립
- Color.kt: 4색 체계(Navy Primary + Gray Neutral + Red Expense + Blue/Green Income) + 다크 테마 Green/Orange 기반 복원
- Type.kt: Typography 스케일 9단계 정의 (displayLarge~labelSmall, SuitVariable 폰트 적용)
- Dimens.kt: 여백 체계 신규 파일 (spacing 4/8/12/16/20/24/32dp, radius 8/12/16dp, cardElevation 0/1/2dp)
- Theme.kt: lightColorScheme/darkColorScheme 전면 재정의, 다크 테마 surface/background 대비 강화

#### MonthlyOverviewSection Hero 카드 리디자인
- 기존 단순 Column → 배경 카드 + 큰 숫자 강조(headlineLarge 30sp Bold) + 전월 비교 뱃지(increase/decrease 색상 분기)
- SpendingTrendInfo Contract 확장: totalAmountText, comparisonText, isOverBudget 필드 추가
- HomeSpendingTrendInfo Mapper: 전월 비교 금액/퍼센트 계산 + 초과/감소 문구 생성

#### Vico 차트 도입
- `VicoCumulativeChart.kt` 신규: Vico CartesianChartHost 기반 누적 차트 (금융앱 스타일)
- 기존 Canvas CumulativeChartCompose → VicoCumulativeChart 전환 (CumulativeTrendSection에서 호출)
- Vico 라이브러리 의존성 추가 (libs.versions.toml + build.gradle.kts)

#### HomeScreen 구조 개선
- 과도한 Column 중첩 정리, CategoryExpenseSection/AiInsightSection 등 하위 Composable 활용
- MonthlyOverviewSection 상단 배치 + 차트 간격 조정

#### DESIGN_PLAN.md 리디자인 계획서
- GPT/Gemini/Claude 진단 종합 → 4색 체계 + Typography + 여백 규칙 정의
- Phase1(Hero+차트) / Phase2(전체 화면) / Phase3(마이크로) 로드맵 수립

### 변경 파일
- `core/theme/Color.kt` — 4색 팔레트 전면 재정의
- `core/theme/Type.kt` — Typography 9단계 스케일
- `core/theme/Dimens.kt` — 신규 (여백 체계)
- `core/theme/Theme.kt` — lightColorScheme/darkColorScheme 재정의
- `core/ui/component/chart/CumulativeTrendSection.kt` — VicoCumulativeChart 연결
- `core/ui/component/chart/SpendingTrendInfo.kt` — Contract 확장 (3개 필드)
- `core/ui/component/chart/VicoCumulativeChart.kt` — 신규 (Vico 기반 차트)
- `feature/home/ui/HomeScreen.kt` — Hero 카드 리디자인 + 구조 개선
- `feature/home/ui/HomeViewModel.kt` — SpendingTrendInfo 매핑 확장
- `feature/home/ui/component/SpendingTrendSection.kt` — 차트 연결 변경
- `feature/home/ui/model/HomeSpendingTrendInfo.kt` — 전월 비교 계산 로직
- `app/build.gradle.kts` — Vico 의존성 추가
- `gradle/libs.versions.toml` — Vico 버전 정의
- `res/values/strings.xml` — 비교 문구 문자열 5개 추가
- `docs/COMPOSABLE_MAP.md` — VicoCumulativeChart 추가
- `docs/DESIGN_PLAN.md` — 신규 (리디자인 계획서)

---

## 2026-02-22 - 예산 관리 기능 확장 (홈 UI + AI 채팅 연동)

### 작업 내용

#### 홈 카테고리 리스트 예산 표시
- `HomePageData`에 `categoryBudgets: Map<String, Int>` 필드 추가
- `loadPageData()`에서 `getBudgetsByMonthOnce()` 일괄 조회로 변경 (전체+카테고리별)
- `CategoryExpenseSection`에서 예산 설정 카테고리: "₩120,000 / ₩200,000" + "₩80,000 남음" 표시
- 예산 초과 시: 빨간색 프로그레스바 + "₩20,000 초과" 텍스트
- 예산 미설정 카테고리: 기존 전체 지출 대비 비율(%) 유지

#### AI 채팅 예산 조회 (BUDGET_STATUS)
- `QueryType.BUDGET_STATUS` 추가 + `executeBudgetStatusQuery()` 구현
- 카테고리별 예산/사용/잔여 금액을 문자열로 생성하여 Gemini에 전달
- `BudgetDao`를 `ChatViewModel`에 Hilt 주입

#### AI 채팅 예산 설정 (SET_BUDGET)
- `ActionType.SET_BUDGET` + `DataAction.category` 필드 추가
- 현재 월 기준으로 `BudgetEntity` insert (OnConflictStrategy.REPLACE)
- 설정 후 `DataRefreshEvent.emit()`으로 홈 화면 자동 갱신

#### PR 리뷰 반영 (Codex P1)
- `SET_BUDGET` 액션에서 `dataRefreshEvent.emit()` 기본값 `ALL_DATA_DELETED` → `TRANSACTION_ADDED`로 변경
  - 기존: `HomeViewModel.observeDataRefreshEvents()`에서 `loadSettings()` 호출 → 중복 collector 누적 위험
  - 수정: `TRANSACTION_ADDED`로 `clearAllPageCache()` + `loadCurrentAndAdjacentPages()`만 실행
- `executeBudgetStatusQuery()`에서 다중 월 예산 처리 추가
  - 기존: `startTimestamp`의 단일 yearMonth로만 예산 조회 → 기간이 여러 달에 걸치면 첫 달만 비교
  - 수정: `startTimestamp`~`endTimestamp` 범위의 모든 월을 순회, 각 월별 예산/지출 독립 비교

#### 프롬프트 갱신
- `string_prompt.xml`: 쿼리/액션 타입 목록, 파라미터, 분석 규칙, 패턴 예시 모두 갱신

### 변경 파일
- `core/util/DataQueryParser.kt` — QueryType.BUDGET_STATUS, ActionType.SET_BUDGET, DataAction.category
- `feature/home/ui/HomeViewModel.kt` — HomePageData.categoryBudgets, loadPageData 일괄 조회
- `feature/home/ui/HomeScreen.kt` — CategoryExpenseSection 예산 UI
- `feature/chat/ui/ChatViewModel.kt` — BudgetDao 주입, executeBudgetStatusQuery, SET_BUDGET 액션
- `res/values/string_prompt.xml` — 예산 관련 프롬프트

---

## 2026-02-22 - 데이터 삭제 시 광고 동기화 초기화 + resume 시 silent 동기화

### 작업 내용

#### 데이터 삭제 시 광고 시청 기록 초기화
- `SettingsDataStore.clearSyncedMonths()` 신규: SYNCED_MONTHS + FULL_SYNC_UNLOCKED 동시 제거
- `SettingsViewModel.deleteAllData()`에서 clearSyncedMonths() 호출
- 데이터 삭제 후 광고를 다시 시청하면 전체/월별 재동기화 가능

#### 앱 재진입(resume) 시 silent 증분 동기화
- `HomeViewModel.refreshData()`에서 isSyncing이 false일 때 silent syncSmsV2 자동 호출
- calculateIncrementalRange() 기반으로 lastSyncTime부터 현재까지 증분 동기화
- PR 리뷰 반영: silent 모드 애널리틱스 이벤트 스킵 + SMS 권한 체크 추가

### 변경 파일
- `core/datastore/SettingsDataStore.kt` — clearSyncedMonths() 추가
- `feature/settings/ui/SettingsViewModel.kt` — deleteAllData()에서 호출
- `feature/home/ui/HomeViewModel.kt` — refreshData()에 silent 증분 동기화 + 권한 체크

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
