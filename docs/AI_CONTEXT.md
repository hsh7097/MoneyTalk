# AI_CONTEXT.md - MoneyTalk 프로젝트 컨텍스트

> AI 에이전트가 MoneyTalk 프로젝트를 이해하고 작업하기 위한 핵심 컨텍스트 문서
> **최종 갱신**: 2026-02-20

---

## 1. 프로젝트 정의

**MoneyTalk**는 SMS 파싱 기반 자동 지출 추적 + AI 재무 상담 Android 앱입니다.

- **언어**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **아키텍처**: MVVM + Hilt DI + Room DB
- **AI**: Google Gemini (2.5-pro/2.5-flash/2.5-flash-lite)
- **Min SDK**: 26 (Android 8.0)
- **Package**: `com.sanha.moneytalk`
- **DB 버전**: 6 (moneytalk_v4.db)

---

## 2. 핵심 아키텍처

### 2-1. 패키지 구조
```
app/src/main/java/com/sanha/moneytalk/
├── core/
│   ├── database/          # Room DB (entity, dao, repository 일부)
│   │   ├── entity/        # 10개 Entity
│   │   ├── dao/           # 9개 DAO
│   │   ├── converter/     # FloatListConverter
│   │   ├── OwnedCardRepository.kt      # 카드 화이트리스트
│   │   └── SmsExclusionRepository.kt   # SMS 제외 키워드
│   ├── firebase/          # Firebase (PremiumManager, ForceUpdateChecker, CrashlyticsHelper)
│   ├── datastore/         # DataStore (설정값)
│   ├── di/                # Hilt DI 모듈
│   ├── model/             # Category enum, SmsAnalysisResult 등
│   ├── ui/                # 공통 UI (AppSnackbarBus, ClassificationState)
│   │   └── component/     # 공통 UI 컴포넌트 (11개)
│   │       ├── CategoryIcon, ExpenseItemCard
│   │       ├── settings/          # SettingsItem/Section Compose/Info
│   │       ├── tab/               # SegmentedTabRowCompose/Info
│   │       └── transaction/       # card/ (TransactionCard), header/ (GroupHeader)
│   ├── similarity/        # 유사도 판정 정책 (SimilarityPolicy 구현체)
│   ├── sms/               # SMS V1 — 실시간 수신 전용 (9개: SmsParser, SmsReader, SmsFilter, HybridSmsClassifier, SmsBatchProcessor, GeminiSmsExtractor, GeneratedSmsRegexParser, SmsEmbeddingService, VectorSearchEngine)
│   ├── sms2/              # SMS 통합 파이프라인 — 배치 동기화 메인 (12개: SmsSyncCoordinator, SmsReaderV2, SmsIncomeFilter, SmsIncomeParser, SmsPipeline, SmsPipelineModels, SmsPreFilter, SmsTemplateEngine, SmsPatternMatcher, SmsGroupClassifier, RemoteSmsRule, RemoteSmsRuleRepository)
│   └── util/              # 유틸 (DateUtils, CardNameNormalizer, StoreNameGrouper 등)
├── feature/
│   ├── home/              # 홈 화면 (월간 현황, SMS 동기화)
│   │   ├── data/          # Repository (Expense, Income, StoreEmbedding, Category, GeminiCategory, CategoryClassifier)
│   │   └── ui/            # HomeScreen, HomeViewModel
│   ├── history/           # 내역 화면 (지출/수입 목록, 정렬, Intent 패턴)
│   │   └── ui/            # HistoryScreen, HistoryViewModel
│   ├── chat/              # AI 상담 (채팅방 리스트/내부)
│   │   ├── data/          # GeminiRepository, ChatRepository, ChatPrompts
│   │   └── ui/            # ChatScreen, ChatViewModel
│   ├── settings/          # 설정 (백업, 카드관리, SMS 제외 등)
│   └── splash/            # 스플래시
├── navigation/            # NavGraph, Screen, BottomNavItem
├── receiver/              # SmsReceiver (BroadcastReceiver)
└── MoneyTalkApplication.kt
```

### 2-2. 핵심 시스템

| 시스템 | 설명 | 핵심 파일 |
|--------|------|-----------|
| SMS 파싱 (sms2, 2-tier) | Vector → Gemini LLM (배치 동기화) | [SmsSyncCoordinator.kt](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsSyncCoordinator.kt), [SmsPipeline.kt](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsPipeline.kt) |
| SMS 파싱 (V1, 실시간) | Regex only (SmsProcessingService) | [HybridSmsClassifier.kt](../app/src/main/java/com/sanha/moneytalk/core/sms/HybridSmsClassifier.kt), [SmsParser.kt](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsParser.kt) |
| SMS 필터링 (발신자) | 010/070 조건부 제외 + 금융 힌트 보존 | [SmsFilter.kt](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsFilter.kt) |
| 카테고리 분류 (4-tier) | Room → Vector → Keyword → Gemini Batch | [CategoryClassifierService.kt](../app/src/main/java/com/sanha/moneytalk/feature/home/data/CategoryClassifierService.kt), [StoreEmbeddingRepository.kt](../app/src/main/java/com/sanha/moneytalk/feature/home/data/StoreEmbeddingRepository.kt) |
| AI 채팅 (3-step) | 쿼리분석 → DB조회/액션 → 답변생성 | [ChatViewModel.kt](../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatViewModel.kt), [GeminiRepository.kt](../app/src/main/java/com/sanha/moneytalk/feature/chat/data/GeminiRepository.kt) |
| 카드 관리 | 소유 카드 화이트리스트 + 카드명 정규화 | [OwnedCardRepository.kt](../app/src/main/java/com/sanha/moneytalk/core/database/OwnedCardRepository.kt), [CardNameNormalizer.kt](../app/src/main/java/com/sanha/moneytalk/core/util/CardNameNormalizer.kt) |
| SMS 필터링 | 제외 키워드 블랙리스트 | [SmsExclusionRepository.kt](../app/src/main/java/com/sanha/moneytalk/core/database/SmsExclusionRepository.kt) |

### 2-3. DB 엔티티 (10개)

| Entity | 테이블 | 용도 |
|--------|--------|------|
| ExpenseEntity | expenses | 지출 내역 |
| IncomeEntity | incomes | 수입 내역 |
| BudgetEntity | budgets | 예산 |
| ChatEntity | chat_history | 채팅 메시지 |
| ChatSessionEntity | chat_sessions | 채팅 세션(방) |
| CategoryMappingEntity | category_mappings | 가게명→카테고리 매핑 캐시 |
| SmsPatternEntity | sms_patterns | SMS 패턴 벡터 캐시 |
| StoreEmbeddingEntity | store_embeddings | 가게명 벡터 임베딩 |
| OwnedCardEntity | owned_cards | 소유 카드 화이트리스트 |
| SmsExclusionKeywordEntity | sms_exclusion_keywords | SMS 제외 키워드 |

### 2-4. DB 마이그레이션 히스토리

| 버전 | 변경 내용 |
|------|----------|
| v1→v2 | incomes 테이블에 memo 컬럼 추가 |
| v2→v3 | owned_cards 테이블 생성 (카드 화이트리스트) |
| v3→v4 | sms_exclusion_keywords 테이블 생성 (SMS 제외 키워드) |
| v4→v5 | expenses/incomes 성능 인덱스 추가 (smsId UNIQUE, dateTime, category, cardName, storeName+dateTime) |
| v5→v6 | sms_patterns 테이블에 amountRegex, storeRegex, cardRegex, parseSource 컬럼 추가 |

> **sms_patterns 내부 마이그레이션** (DatabaseMigrations.kt):
> - MIGRATION_1_2: amountRegex, storeRegex, cardRegex 컬럼 추가
> - MIGRATION_2_3: isMainGroup 컬럼 추가 (메인 그룹 패턴 식별, AppDatabase v3)

---

## 3. 유사도 시스템: Vector(연산) → Policy(판단) → Service(행동)

> **Phase 1 리팩토링 완료**: 모든 임계값은 SimilarityPolicy 구현체가 SSOT

### 3-1. 계층 구조

```
VectorSearchEngine (순수 벡터 연산)
  ├── cosineSimilarity(), findTopK(), findBestMatch()
  └── 도메인 상수 0개 — 임계값을 모름

SimilarityPolicy (판단 인터페이스)
  ├── shouldAutoApply(similarity) → 자동 적용 여부
  ├── shouldConfirm(similarity)   → 확정 여부
  ├── shouldPropagate(similarity) → 전파 여부
  └── shouldGroup(similarity)     → 그룹핑 여부

도메인별 Policy 구현체 (SSOT)
  ├── SmsPatternSimilarityPolicy  → SMS 분류 임계값
  ├── StoreNameSimilarityPolicy   → 가게명 매칭 임계값
  └── CategoryPropagationPolicy   → 카테고리 전파 + confidence 차단
```

### 3-2. SmsPatternSimilarityPolicy (SMS 분류용)

| 속성 | 값 | 용도 | 참조 파일 |
|------|-----|------|-----------|
| `profile.confirm` | 0.92 | SMS가 결제 문자인지 판정 | HybridSmsClassifier, SmsBatchProcessor |
| `profile.autoApply` | 0.95 | 캐시된 파싱 결과 재사용 | HybridSmsClassifier |
| `profile.group` | 0.95 | SMS 패턴 벡터 그룹핑 | SmsBatchProcessor |
| `NON_PAYMENT_CACHE_THRESHOLD` | 0.97 | 비결제 패턴 캐시 히트 | HybridSmsClassifier |
| `LLM_TRIGGER_THRESHOLD` | 0.80 | LLM 호출 대상 선별 (결제 판정 아님) | HybridSmsClassifier |

#### SmsGroupClassifier 내부 상수 (sms2, SimilarityPolicy 외)

| 상수 | 값 | 용도 | 파일 |
|------|-----|------|------|
| `GROUPING_SIMILARITY` | 0.95 | 벡터 그룹핑 유사도 | SmsGroupClassifier |
| `SMALL_GROUP_MERGE_THRESHOLD` | 5 | 소그룹 병합 대상 멤버 수 상한 | SmsGroupClassifier |
| `SMALL_GROUP_MERGE_MIN_SIMILARITY` | 0.70 | 소그룹 병합 시 대표 벡터 최소 유사도 | SmsGroupClassifier |
| `LLM_BATCH_SIZE` | 20 | LLM 배치당 최대 SMS 수 | SmsGroupClassifier |
| `LLM_CONCURRENCY` | 5 | LLM 병렬 동시 실행 수 | SmsGroupClassifier |
| `REGEX_SAMPLE_SIZE` | 5 | 정규식 생성 시 사용할 샘플 수 | SmsGroupClassifier |
| `REGEX_MIN_SAMPLES_FOR_GENERATION` | 3 | 정규식 생성 최소 샘플 수 | SmsGroupClassifier |
| `REGEX_FAILURE_THRESHOLD` | 2 | 정규식 생성 실패 쿨다운 기준 | SmsGroupClassifier |
| `RTDB_DEDUP_SIMILARITY` | 0.99 | RTDB 표본 중복 판정 유사도 | SmsGroupClassifier |

#### SmsPatternMatcher 내부 상수 (sms2)

| 상수 | 값 | 용도 | 파일 |
|------|-----|------|------|
| `NON_PAYMENT_THRESHOLD` | 0.97 | 비결제 패턴 캐시 히트 | SmsPatternMatcher |
| `PAYMENT_MATCH_THRESHOLD` | 0.92 | 결제 패턴 매칭 판정 | SmsPatternMatcher |

#### SmsPipeline 내부 상수 (sms2)

| 상수 | 값 | 용도 | 파일 |
|------|-----|------|------|
| `EMBEDDING_BATCH_SIZE` | 100 | 임베딩 배치 크기 | SmsPipeline |
| `EMBEDDING_CONCURRENCY` | 10 | 임베딩 배치 병렬 동시 실행 수 | SmsPipeline |

#### SmsPatternEntity.parseSource 값

| source | 의미 | confidence |
|--------|------|-----------|
| `regex` | SmsParser 하드코딩 정규식으로 파싱 | 1.0 |
| `llm_regex` | LLM 생성 정규식으로 파싱 성공 | 1.0 |
| `template_regex` | 같은 그룹 내 다른 SMS의 정규식으로 파싱 | 0.85 |
| `llm` | LLM 직접 추출 (정규식 없음) | 0.8 |
| `llm_non_payment` | LLM이 비결제로 판정 | 0.8 |
| `remote_rule` | RTDB 원격 룰 매칭 → 로컬 승격 | 1.0 |

#### SmsPatternMatcher 원격 룰 매칭 상수 (sms2)

| 상수 | 값 | 용도 | 파일 |
|------|-----|------|------|
| `RemoteSmsRule.DEFAULT_MIN_SIMILARITY` | 0.94 | 원격 룰 매칭 최소 유사도 | RemoteSmsRule |
| `RemoteSmsRuleRepository.CACHE_TTL_MS` | 10분 | 원격 룰 캐시 TTL | RemoteSmsRuleRepository |

### 3-3. StoreNameSimilarityPolicy (가게명 매칭용)

| 속성 | 값 | 용도 | 참조 파일 |
|------|-----|------|-----------|
| `profile.autoApply` | 0.92 | 가게명 → 카테고리 자동 적용 | StoreEmbeddingRepository |
| `profile.confirm` | 0.92 | 가게명 매칭 확정 | StoreEmbeddingRepository |
| `profile.propagate` | 0.90 | 유사 가게 카테고리 전파 | StoreEmbeddingRepository |
| `profile.group` | 0.88 | 가게명 시맨틱 그룹핑 | StoreNameGrouper |

### 3-4. CategoryPropagationPolicy (카테고리 전파용)

| 속성 | 값 | 용도 | 참조 파일 |
|------|-----|------|-----------|
| `profile.propagate` | 0.90 | 유사 가게 전파 기준 | StoreEmbeddingRepository |
| `MIN_PROPAGATION_CONFIDENCE` | 0.6 | confidence 차단 임계값 | StoreEmbeddingRepository |

#### confidence 정책 근거 (왜 0.6인가?)
- **Regex 추출**: confidence = 1.0 → 항상 전파 허용
- **LLM 추출**: confidence = 0.8 → 전파 허용
- **0.6 미만**: 현재 시스템에 없지만, 향후 OCR/음성 등 저신뢰 소스 추가 시 자동 차단
- **설계 의도**: "유사도는 높지만 신뢰도가 낮은" 오분류의 연쇄 전파를 사전 차단
- `shouldPropagateWithConfidence(similarity, confidence)`: 유사도 + confidence 모두 충족해야 전파 허용

### 3-5. 임계값 관계 다이어그램
```
1.00 ─── 완전 일치 (exact match)
0.97 ─── 비결제 패턴 캐시 히트 (SmsPatternMatcher.NON_PAYMENT_THRESHOLD / SmsPatternSimilarityPolicy.NON_PAYMENT_CACHE_THRESHOLD)
0.95 ─── 결제 패턴 캐시 재사용 (SmsPatternSimilarityPolicy.profile.autoApply)
       ─── SMS 벡터 그룹핑 (SmsGroupClassifier.GROUPING_SIMILARITY / SmsPatternSimilarityPolicy.profile.group)
0.94 ─── 원격 룰 매칭 최소 유사도 (RemoteSmsRule.DEFAULT_MIN_SIMILARITY)
0.92 ─── 결제 문자 판정 (SmsPatternMatcher.PAYMENT_MATCH_THRESHOLD / SmsPatternSimilarityPolicy.profile.confirm)
       ─── 가게명 → 카테고리 자동 적용 (StoreNameSimilarityPolicy.profile.autoApply)
0.90 ─── 카테고리 전파 (StoreNameSimilarityPolicy.profile.propagate)
0.88 ─── 가게명 시맨틱 그룹핑 (StoreNameSimilarityPolicy.profile.group)
0.85 ─── template_regex confidence (SmsGroupClassifier)
0.80 ─── LLM 트리거 임계값 (SmsPatternSimilarityPolicy.LLM_TRIGGER_THRESHOLD) — V1 전용
       ─── LLM 고정 confidence
0.70 ─── 소그룹 병합 최소 유사도 (SmsGroupClassifier.SMALL_GROUP_MERGE_MIN_SIMILARITY)
0.60 ─── confidence 차단 임계값 (CategoryPropagationPolicy.MIN_PROPAGATION_CONFIDENCE)
0.00 ─── 매칭 없음
```

---

## 4. AI 채팅 시스템 상세

### 4-1. 쿼리 타입 (17종)

| 타입 | 설명 | 필수 파라미터 |
|------|------|-------------|
| `total_expense` | 기간 내 총 지출 | startDate, endDate |
| `total_income` | 기간 내 총 수입 | startDate, endDate |
| `expense_by_category` | 카테고리별 지출 합계 | startDate, endDate |
| `expense_list` | 지출 내역 리스트 | limit |
| `expense_by_store` | 특정 가게 지출 | storeName |
| `expense_by_card` | 특정 카드 지출 | cardName |
| `daily_totals` | 일별 지출 합계 | startDate, endDate |
| `monthly_totals` | 월별 지출 합계 | — |
| `monthly_income` | 설정된 월 수입 | — |
| `uncategorized_list` | 미분류 항목 | — |
| `category_ratio` | 수입 대비 비율 분석 | — |
| `search_expense` | 검색 (가게/카테고리/카드) | searchKeyword |
| `card_list` | 사용 카드 목록 | — |
| `income_list` | 수입 내역 | startDate, endDate |
| `duplicate_list` | 중복 지출 항목 | — |
| `sms_exclusion_list` | SMS 제외 키워드 목록 | — |
| `analytics` | 복합 분석 (필터+그룹핑+집계) | filters, groupBy, metrics |

### 4-2. 액션 타입 (12종)

| 타입 | 설명 | 필수 파라미터 |
|------|------|-------------|
| `update_category` | 특정 지출 카테고리 변경 | expenseId, newCategory |
| `update_category_by_store` | 가게명 기준 일괄 변경 | storeName, newCategory |
| `update_category_by_keyword` | 키워드 포함 일괄 변경 | searchKeyword, newCategory |
| `delete_expense` | 특정 지출 삭제 | expenseId |
| `delete_by_keyword` | 키워드 기준 일괄 삭제 | searchKeyword |
| `delete_duplicates` | 중복 항목 전체 삭제 | — |
| `add_expense` | 수동 지출 추가 | storeName, amount |
| `update_memo` | 메모 수정 | expenseId, memo |
| `update_store_name` | 가게명 수정 | expenseId, newStoreName |
| `update_amount` | 금액 수정 | expenseId, newAmount |
| `add_sms_exclusion` | SMS 제외 키워드 추가 | searchKeyword |
| `remove_sms_exclusion` | SMS 제외 키워드 삭제 | searchKeyword |

### 4-3. ANALYTICS 쿼리 (클라이언트 사이드 실행)

ANALYTICS 쿼리는 ChatViewModel에서 클라이언트 사이드로 실행되는 복합 분석 기능입니다.

| 구성 요소 | 지원 값 |
|----------|---------|
| **필터 연산자** | ==, !=, >, >=, <, <=, contains, not_contains, in, not_in |
| **필터 필드** | category, storeName, cardName, amount, memo, dayOfWeek |
| **그룹핑** | category, storeName, cardName, date, month, dayOfWeek |
| **집계 메트릭** | sum, avg, count, max, min |
| **정렬** | asc, desc |
| **topN** | 상위 N개 결과 |

### 4-4. Gemini 모델 구성

| 모델 | 역할 | Gemini 모델 | temperature | topK | topP | maxTokens |
|------|------|-----------|-------------|------|------|-----------|
| queryAnalyzerModel | 쿼리/액션 분석 | gemini-2.5-pro | 0.3 | 20 | 0.9 | 10000 |
| financialAdvisorModel | 재무 상담 답변 | gemini-2.5-pro | 0.7 | 40 | 0.95 | 10000 |
| summaryModel | Rolling Summary | gemini-2.5-flash | 0.3 | 20 | 0.9 | 10000 |

### 4-5. 프롬프트 위치

> 모든 시스템 프롬프트는 [`res/values/string_prompt.xml`](../app/src/main/res/values/string_prompt.xml)에서 관리

| 프롬프트 | XML key | 모델 |
|---------|---------|------|
| 쿼리 분석기 | `prompt_query_analyzer_system` | gemini-2.5-pro |
| 재무 상담사 | `prompt_financial_advisor_system` | gemini-2.5-pro |
| 대화 요약 | `prompt_summary_system` | gemini-2.5-flash |
| SMS 추출 (단일) | `prompt_sms_extract_system` | gemini-2.5-flash-lite |
| SMS 추출 (배치) | `prompt_sms_batch_extract_system` | gemini-2.5-flash-lite |
| 카테고리 분류 | `prompt_category_classification` | gemini-2.5-flash-lite |

---

## 5. Golden Flows (핵심 동작 흐름)

### 5-1. SMS → 지출 등록 흐름

**배치 동기화 (메인 경로 — sms2)**:
```
HomeViewModel.syncSmsV2(contentResolver, targetMonthRange)
   → readAndFilterSms(): SmsReaderV2.readAllMessagesByDateRange() → List<SmsInput>
     → 010/070 발신자 조건부 제외 (SmsFilter.shouldSkipBySender)
     → smsId 중복 제거 (expenseRepository + incomeRepository)
   → processSmsPipeline(): SmsSyncCoordinator.process(smsInputs)
     → SmsPreFilter: 비결제 SMS 제거 (60+ 키워드, 100자 초과)
     → SmsIncomeFilter: PAYMENT/INCOME/SKIP 3분류 (financialKeywords 46개)
     → SmsPipeline (결제 후보만):
       → SmsTemplateEngine: 템플릿화 + Gemini Embedding API (배치 100건)
       → SmsPatternMatcher: 기존 패턴 DB에서 코사인 유사도 매칭 + regex 파싱
       → SmsGroupClassifier: 3레벨 그룹핑 → LLM 배치 추출 → regex 생성 → 패턴 DB 등록
   → saveExpenses(): SmsParseResult → ExpenseEntity 변환 + 배치 저장
   → saveIncomes(): SmsIncomeParser 파싱 → IncomeEntity 변환 + 배치 저장
   → postSyncCleanup(): 카테고리 분류 + 패턴 정리 + lastSyncTime + 카드 등록

HomeViewModel.syncIncremental(contentResolver):
   → calculateIncrementalRange() (lastSyncTime/fullSyncUnlocked/Auto Backup 감지)
   → syncSmsV2(contentResolver, range)
```

**실시간 수신 (V1 유지)**:
```
SMS 수신 → SmsReceiver → SmsProcessingService → SmsParser (Regex only)
   → 성공: ExpenseEntity 생성
   → 실패: 로그만 (실시간은 Tier 1만)
```

### 5-2. 카테고리 자동 분류 흐름
```
CategoryClassifierService.getCategory(storeName)
   → Tier 1: Room DB 정확 매칭 (storeName → category)
   → Tier 1.5: 임베딩 1회 생성 → 1.5a/b 모두에서 재사용
      → 1.5a: findCategoryByStoreName(storeName, queryVector)
         → 벡터 매칭 → StoreNameSimilarityPolicy.shouldAutoApply(0.92) → 자동 적용
      → 1.5b: findCategoryByGroup(storeName, queryVector)
         → 그룹 매칭 → StoreNameSimilarityPolicy.shouldGroup(0.88) → 다수결 적용
   → Tier 2: 로컬 키워드 매칭 (SmsParser.inferCategory)
   → Tier 3: StoreNameGrouper로 그룹핑 → Gemini 배치 호출 (별도 트리거)
   → 결과 전파: CategoryPropagationPolicy.shouldPropagateWithConfidence()
      → 유사도(≥0.90) + confidence(≥0.6) 모두 충족 시만 전파
   → 참조 리스트: CategoryReferenceProvider → 모든 LLM 프롬프트에 주입
```

### 5-3. Gemini API 최적화 방식 비교

| 방식 | 적합성 | 이유 |
|------|--------|------|
| Context Caching | 불가 | 최소 32k 토큰 미달 |
| Function Calling | 이미 적용 중 | query_analyzer가 사실상 이 구조 (쿼리/액션 타입 정의 → JSON 반환) |
| Vertex AI Managed Prompt | 과잉 | GCP 전환 비용 대비 효과 미미 |
| 프롬프트 다이어트 (RAG) | 검토 가능 | 카테고리 가이드 부분만 동적으로 축소 |

### 5-4. AI 채팅 흐름
```
ChatViewModel.sendMessage(message)
   → ChatRepository.sendMessageAndBuildContext() [Rolling Summary + 윈도우]
   → GeminiRepository.analyzeQueryNeeds() [쿼리/액션/clarification JSON 파싱]
   → [분기] isClarification?
      → Yes: 확인 질문을 AI 응답으로 저장 → 사용자 추가 입력 대기
      → No:  executeQuery() / executeAction() / executeAnalytics() [DB 조회/수정/분석]
             → GeminiRepository.generateFinalAnswerWithContext() [최종 답변]
   → ChatRepository.saveAiResponseAndUpdateSummary() [요약 갱신]
```

---

## 6. UI 공통 컴포넌트 (core/ui/component/, 11개 파일)

### 6-1. TransactionCard (거래 카드)
- **Interface**: `TransactionCardInfo` — title, subtitle, amount, isIncome, category, iconEmoji
- **구현체**: `ExpenseTransactionCardInfo`, `IncomeTransactionCardInfo`
- **Composable**: `TransactionCardCompose` — 지출/수입 통합 카드 렌더링
- **사용처**: HomeScreen, HistoryScreen

### 6-2. TransactionGroupHeader (그룹 헤더)
- **Interface**: `TransactionGroupHeaderInfo` — title, expenseTotal, incomeTotal
- **Composable**: `TransactionGroupHeaderCompose` — 날짜별/가게별/금액별 그룹 헤더
- **사용처**: HistoryScreen

### 6-3. SegmentedTabRow (탭 버튼)
- **Interface**: `SegmentedTabInfo` — label, isSelected, selectedColor, selectedTextColor, icon
- **Composable**: `SegmentedTabRowCompose` — 라운드 버튼 스타일 탭 (아이콘 지원)
- **사용처**: HistoryScreen (보기 모드 전환)

### 6-4. SettingsItem / SettingsSection (설정 항목)
- **Interface**: `SettingsItemInfo` — title, subtitle, onClick 등
- **Composable**: `SettingsItemCompose`, `SettingsSectionCompose`
- **사용처**: SettingsScreen

### 6-5. CategoryIcon (카테고리 아이콘)
- **Composable**: `CategoryIcon` — 카테고리별 이모지 아이콘 렌더링
- **사용처**: HomeScreen, HistoryScreen, ChatScreen

### 6-6. Preview 배치 규칙
- Preview 파일은 `src/debug/` 소스셋에 동일 패키지 경로로 배치 (release 빌드 제외)

---

## 7. 리팩토링 컨텍스트

### Phase 1 완료: VectorSearchEngine 책임 분리
`VectorSearchEngine`이 담당하던 **순수 벡터 연산**과 **도메인별 유사도 판정 정책**을 분리 완료.
→ Vector(연산) → Policy(판단) → Service(행동) 3계층 구조 확립

### History Intent 패턴 도입 (2026-02-11)
HistoryScreen에 `HistoryIntent` sealed interface 패턴을 적용하여 UI-비즈니스 로직 분리.
- `SelectExpense`, `SelectIncome`, `DismissDialog`
- `DeleteExpense`, `ChangeCategory`, `UpdateExpenseMemo`
- `DeleteIncome`, `UpdateIncomeMemo`

---

## 8. 동기화 규칙 (Document Sync Rules)

### SSOT (Single Source of Truth) 원칙
- 임계값 수치 → 이 문서의 "임계값 레지스트리" 섹션이 SSOT
- 코드 변경 시 → 이 문서를 먼저 업데이트, 코드에 반영
- 문서 간 충돌 시 → [AI_CONTEXT.md](AI_CONTEXT.md) > 개별 문서 ([SMS_PARSING.md](SMS_PARSING.md) 등)

### 갱신 타이밍
- 임계값 변경 시 → 즉시 갱신
- 새 파일/패키지 추가 시 → 패키지 구조 섹션 갱신
- 리팩토링 완료 시 → AI_TASKS.md 체크박스 갱신 + AI_CONTEXT.md 구조 반영
