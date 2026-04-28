# AI_CONTEXT.md - MoneyTalk 프로젝트 컨텍스트

> AI 에이전트가 MoneyTalk 프로젝트를 이해하고 작업하기 위한 핵심 컨텍스트 문서
> **최종 갱신**: 2026-04-28

---

## 1. 프로젝트 정의

**MoneyTalk**는 SMS 파싱 기반 자동 지출 추적 + AI 재무 상담 Android 앱입니다.

- **언어**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **아키텍처**: MVVM + Hilt DI + Room DB
- **AI**: Google Gemini (2.5-pro/2.5-flash/2.5-flash-lite)
- **Min SDK**: 26 (Android 8.0)
- **Package**: `com.sanha.moneytalk`
- **DB 버전**: 2 (`moneytalk.db`)

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
│   ├── appfunctions/      # Android App Functions (월간 가계 요약 외부 호출)
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
│   ├── sms/               # SMS 통합 패키지 (25개: 배치=SmsSyncCoordinator/SmsPipeline, 실시간=SmsInstantProcessor/SmsReaderV2, 공용=SmsParser/SmsFilter, 룰=SmsRegexRuleMatcher/RemoteSmsRuleRepository, 진단=DeletedSmsTracker/SmsChannelProbeCollector)
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
│   │   └── ui/            # SettingsScreen, SettingsViewModel, dialogs
│   ├── categorysettings/  # 카테고리 설정 (커스텀 카테고리 추가/수정/삭제)
│   │   └── ui/            # CategorySettingsActivity, Screen, ViewModel
│   ├── smssettings/       # 문자 설정 (제외 키워드/차단번호)
│   │   └── ui/            # SmsSettingsActivity, Screen, ViewModel
│   ├── storerulesettings/ # 거래처 규칙 설정 (StoreRule 추가/편집/삭제)
│   │   └── ui/            # StoreRuleSettingsActivity, Screen, ViewModel
│   ├── transactionedit/   # 거래 편집/추가 (뱅크셀러드 스타일)
│   │   └── ui/            # TransactionEditActivity, Screen, ViewModel, ui/model
│   ├── transactionlist/   # 날짜별 거래 목록
│   │   └── ui/            # TransactionDetailListActivity, Screen, ViewModel
│   ├── categorydetail/    # 카테고리 상세 (월간 추이 + 거래 리스트)
│   │   └── ui/            # CategoryDetailActivity, Screen, ViewModel, ui/model
│   ├── intro/             # 인트로/권한/초기 진입
│   │   └── ui/            # IntroActivity, OnboardingScreen, PermissionScreen
│   └── splash/            # 스플래시
│       └── ui/            # SplashScreen
├── navigation/            # NavGraph, Screen, BottomNavItem
├── receiver/              # 실시간 수신/알림 보완 (SmsReceiver, Mms/RcsContentObserver, NotificationTransactionService)
└── MoneyTalkApplication.kt
```

- 원칙: feature 전용 `Activity` / `ViewModel` / 화면 모델은 `feature/<name>/ui` 또는 `ui/model`에 두고, 여러 화면이 공유하는 Repository/Service만 `data` 또는 `core`에 둔다.

### 2-2. 핵심 시스템

| 시스템 | 설명 | 핵심 파일 |
|--------|------|-----------|
| SMS 파싱 (sms, 3-tier) | Regex Fast Path → Vector → Gemini LLM (배치 동기화) | [SmsSyncCoordinator.kt](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsSyncCoordinator.kt), [SmsRegexRuleMatcher.kt](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsRegexRuleMatcher.kt), [SmsPipeline.kt](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsPipeline.kt) |
| SMS 파싱 (실시간) | SmsInstantProcessor 기반 즉시 처리 + 메시지 앱 알림 보완 | [SmsInstantProcessor.kt](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsInstantProcessor.kt), [SmsReceiver.kt](../app/src/main/java/com/sanha/moneytalk/receiver/SmsReceiver.kt), [MmsContentObserver.kt](../app/src/main/java/com/sanha/moneytalk/receiver/MmsContentObserver.kt), [RcsContentObserver.kt](../app/src/main/java/com/sanha/moneytalk/receiver/RcsContentObserver.kt), [NotificationTransactionService.kt](../app/src/main/java/com/sanha/moneytalk/receiver/NotificationTransactionService.kt) |
| SMS 필터링 (발신자) | 010/070 조건부 제외 + 금융 힌트 보존 | [SmsFilter.kt](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsFilter.kt) |
| 카테고리 분류 (4-tier) | Room → Vector → Keyword → Gemini Batch | [CategoryClassifierService.kt](../app/src/main/java/com/sanha/moneytalk/feature/home/data/CategoryClassifierService.kt), [StoreEmbeddingRepository.kt](../app/src/main/java/com/sanha/moneytalk/feature/home/data/StoreEmbeddingRepository.kt) |
| AI 채팅 (3-step) | 쿼리분석 → DB조회/액션 → 답변생성 | [ChatViewModel.kt](../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatViewModel.kt), [GeminiRepository.kt](../app/src/main/java/com/sanha/moneytalk/feature/chat/data/GeminiRepository.kt) |
| Android App Functions | Assistant/agent가 앱 내부 월간 가계 요약을 조회 | [MoneyTalkFinanceAppFunctions.kt](../app/src/main/java/com/sanha/moneytalk/core/appfunctions/MoneyTalkFinanceAppFunctions.kt), [MoneyTalkFinanceSummaryReader.kt](../app/src/main/java/com/sanha/moneytalk/core/appfunctions/MoneyTalkFinanceSummaryReader.kt), [MoneyTalkApplication.kt](../app/src/main/java/com/sanha/moneytalk/MoneyTalkApplication.kt) |
| 카드 관리 | 소유 카드 화이트리스트 + 카드명 정규화 | [OwnedCardRepository.kt](../app/src/main/java/com/sanha/moneytalk/core/database/OwnedCardRepository.kt), [CardNameNormalizer.kt](../app/src/main/java/com/sanha/moneytalk/core/util/CardNameNormalizer.kt) |
| SMS 필터링 | 제외 키워드 블랙리스트 | [SmsExclusionRepository.kt](../app/src/main/java/com/sanha/moneytalk/core/database/SmsExclusionRepository.kt) |
| 거래처 규칙 (StoreRule) | 거래처 키워드→카테고리/고정지출 자동 적용 (Tier 0) | [StoreRuleRepository.kt](../app/src/main/java/com/sanha/moneytalk/feature/home/data/StoreRuleRepository.kt), [StoreRuleSettingsViewModel.kt](../app/src/main/java/com/sanha/moneytalk/feature/storerulesettings/ui/StoreRuleSettingsViewModel.kt) |

### 2-3. DB 엔티티 (15개)

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
| SmsBlockedSenderEntity | sms_blocked_senders | 수신 차단 발신번호 |
| SmsChannelProbeLogEntity | sms_channel_probe_logs | 채널 진단 로그 |
| SmsExclusionKeywordEntity | sms_exclusion_keywords | SMS 제외 키워드 |
| SmsRegexRuleEntity | sms_regex_rules | SMS regex 룰 (sender+type+ruleKey 복합키) |
| CustomCategoryEntity | custom_categories | 사용자 정의 카테고리 |
| StoreRuleEntity | store_rules | 거래처 규칙 (keyword→category/isFixed 자동 적용) |

### 2-4. DB 버전 정보

- **현재 버전**: v2
- **Migration 코드**: `MIGRATION_1_2` (채널 진단 로그 테이블 추가)
- 이후 스키마 변경 시 추가 Migration 필수

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
| `profile.confirm` | 0.92 | SMS가 결제 문자인지 판정 | SmsPatternMatcher |
| `profile.autoApply` | 0.95 | 캐시된 파싱 결과 재사용 | SmsPatternMatcher |
| `profile.group` | 0.95 | SMS 패턴 벡터 그룹핑 | SmsGroupClassifier |
| `NON_PAYMENT_CACHE_THRESHOLD` | 0.97 | 비결제 패턴 캐시 히트 | SmsPatternMatcher |
| `LLM_TRIGGER_THRESHOLD` | 0.80 | LLM 호출 대상 선별 (결제 판정 아님) | SmsPatternMatcher |

#### SmsGroupClassifier 내부 상수 (sms, SimilarityPolicy 외)

| 상수 | 값 | 용도 | 파일 |
|------|-----|------|------|
| `GROUPING_SIMILARITY` | 0.95 | 벡터 그룹핑 유사도 | SmsGroupClassifier |
| `SMALL_GROUP_MERGE_THRESHOLD` | 5 | 소그룹 병합 대상 멤버 수 상한 | SmsGroupClassifier |
| `SMALL_GROUP_MERGE_MIN_SIMILARITY` | 0.90 | 소그룹 병합 시 대표 벡터 최소 유사도 | SmsGroupClassifier |
| `SIMILARITY_GROUPING_MAX_SIZE` | 120 | 유사도 그룹핑 최대 SMS 수 (초과 시 강등) | SmsGroupClassifier |
| `LLM_CONCURRENCY` | 5 | LLM 병렬 동시 실행 수 (Step4.5/5 공유) | SmsGroupClassifier |
| `LLM_FALLBACK_MAX_SAMPLES` | 10 | Step4.5 배치 LLM chunk 크기 | SmsGroupClassifier |
| `GROUP_CONTEXT_MAX_SAMPLES` | 10 | processGroup LLM 컨텍스트 샘플 수 | SmsGroupClassifier |
| `REGEX_SAMPLE_SIZE` | 5 | 정규식 생성 시 사용할 샘플 수 | SmsGroupClassifier |
| `REGEX_MIN_SAMPLES` | 5 | 정규식 생성 최소 그룹 크기 (미달 시 스킵) | SmsGroupClassifier |
| `REGEX_VALIDATION_MIN_PASS_RATIO` | 0.80 | regex 검증 최소 파싱 성공률 | SmsGroupClassifier |
| `REGEX_NEAR_MISS_MIN_RATIO` | 0.60 | near-miss 그룹 regex 재시도 기준 | SmsGroupClassifier |
| `REGEX_FAILURE_THRESHOLD` | 2 | 정규식 생성 실패 쿨다운 기준 | SmsGroupClassifier |
| `REGEX_FAILURE_COOLDOWN_MS` | 30분 | 정규식 실패 쿨다운 시간 | SmsGroupClassifier |
| `GROUP_TIME_BUDGET_MS` | 10초 | 단일 그룹 처리 시간 제한 | SmsGroupClassifier |
| `STEP5_TIME_BUDGET_MS` | 20초 | Step5 전체 시간 제한 | SmsGroupClassifier |
| `GROUP_LLM_NULL_RETRY_MAX` | 1 | 단건 LLM null 시 재시도 횟수 | SmsGroupClassifier |
| `UNSTABLE_MEDIAN_MIN_SIMILARITY` | 0.92 | unstable 그룹 판정 중앙값 기준 | SmsGroupClassifier |
| `UNSTABLE_P20_MIN_SIMILARITY` | 0.88 | unstable 그룹 판정 P20 기준 | SmsGroupClassifier |
| `UNSTABLE_DIRECT_LLM_MAX_SIZE` | 10 | unstable 해체 시 직접 LLM 최대 수 | SmsGroupClassifier |
| `TEMPLATE_SEED_MIN_BUCKET_SIZE` | 2 | 템플릿 시드 최소 버킷 크기 | SmsGroupClassifier |
| `SAMPLE_KEY_CACHE_MAX_SIZE` | 500 | RTDB 표본 세션 중복 전송 방지 캐시 상한 | SmsGroupClassifier |
| `LEARNING_QUEUE_MAX_SIZE` | 1000 | 백그라운드 학습 큐 최대 크기 | SmsGroupClassifier |

#### SmsGroupClassifier Step4.5 복구 상수

| 상수 | 값 | 용도 | 파일 |
|------|-----|------|------|
| `REGEX_FAILED_RECOVERY_FAILURE_THRESHOLD` | 2 | 복구 실패 쿨다운 기준 (sender:templateHash) | SmsGroupClassifier |
| `REGEX_FAILED_RECOVERY_COOLDOWN_MS` | 30분 | 복구 실패 쿨다운 시간 | SmsGroupClassifier |
| `REGEX_FAILED_RECOVERY_MIN_LENGTH` | 12 | 복구 대상 SMS 최소 길이 | SmsGroupClassifier |
| `KB_DEBIT_FALLBACK_AMOUNT_REGEX` | `출금\n([\d,]+)\n잔액` | KB 멀티라인 출금 금액 추출 | SmsGroupClassifier |
| `KB_DEBIT_FALLBACK_STORE_REGEX` | `\d+\*+\d+\n(.+?)\n출금` | KB 멀티라인 출금 가게명 추출 | SmsGroupClassifier |

#### SmsPatternMatcher 내부 상수 (sms)

| 상수 | 값 | 용도 | 파일 |
|------|-----|------|------|
| `NON_PAYMENT_THRESHOLD` | 0.97 | 비결제 패턴 캐시 히트 | SmsPatternMatcher |
| `PAYMENT_MATCH_THRESHOLD` | 0.92 | 결제 패턴 매칭 판정 | SmsPatternMatcher |
| `DEBIT_FALLBACK_STORE_NAME` | "계좌출금" | 출금 알림 대체 가게명 | SmsPatternMatcher |

#### SmsPipeline 내부 상수 (sms)

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

#### SmsPatternMatcher 원격 룰 매칭 상수 (sms)

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
       ─── 소그룹 병합 최소 유사도 (SmsGroupClassifier.SMALL_GROUP_MERGE_MIN_SIMILARITY)
0.88 ─── 가게명 시맨틱 그룹핑 (StoreNameSimilarityPolicy.profile.group)
0.85 ─── template_regex confidence (SmsGroupClassifier)
0.80 ─── LLM 트리거 임계값 (SmsPatternSimilarityPolicy.LLM_TRIGGER_THRESHOLD) — V1 전용
       ─── LLM 고정 confidence
       ─── regex 검증 최소 파싱 성공률 (SmsGroupClassifier.REGEX_VALIDATION_MIN_PASS_RATIO)
0.60 ─── confidence 차단 임계값 (CategoryPropagationPolicy.MIN_PROPAGATION_CONFIDENCE)
       ─── near-miss 그룹 regex 재시도 기준 (SmsGroupClassifier.REGEX_NEAR_MISS_MIN_RATIO)
0.00 ─── 매칭 없음
```

---

## 4. AI 채팅 시스템 상세

### 4-1. 쿼리 타입 (18종)

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
| `budget_status` | 예산 현황 (카테고리별 한도/사용/잔여) | — |

### 4-2. 액션 타입 (13종)

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
| `set_budget` | 카테고리별 월 예산 설정/변경 | category, amount |

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

수치 안전 원칙:

- 합계/평균/건수/최대/최소 계산은 `ChatViewModel.executeAnalytics()` 또는 Room 쿼리에서 수행
- Gemini 최종 답변은 `[조회된 데이터]`와 `[ANALYTICS 계산 결과]` 값을 인용하고, 거래 리스트를 직접 재계산하지 않음
- 데이터가 없거나 표본이 적으면 금융 판단을 추정하지 않고 추가 데이터 요청 또는 판단 불가를 안내

### 4-4. Gemini 모델 구성

| 모델 | 역할 | Gemini 모델 | temperature | topK | topP | maxTokens |
|------|------|-----------|-------------|------|------|-----------|
| queryAnalyzerModel | 쿼리/액션 분석 | gemini-2.5-pro | 0.3 | 20 | 0.9 | 10000 |
| financialAdvisorModel | 재무 상담 답변 | gemini-2.5-pro | 0.7 | 40 | 0.95 | 10000 |
| summaryModel | Rolling Summary | gemini-2.5-flash | 0.3 | 20 | 0.9 | 10000 |

> 출시 기본값은 안정판 2.5 계열이다. 최신 preview 모델 검증은 Firebase RTDB `/config/models`에서 역할별 모델명을 오버라이드해 진행한다.

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

**배치 동기화 (메인 경로 — sms)**:
```
HomeViewModel.syncSmsV2(contentResolver, targetMonthRange)
   → readAndFilterSms(): SmsReaderV2.readAllMessagesByDateRange() → SmsReadResult.messages
     → 010/070 발신자 조건부 제외 (SmsFilter.shouldSkipBySender)
     → smsId 중복 제거 (expenseRepository + incomeRepository)
   → processSmsPipeline(): SmsSyncCoordinator.process(smsInputs)
     → SmsPreFilter: 비결제 SMS 제거 (60+ 키워드, 통신 단가 안내, 130자 초과)
     → SmsIncomeFilter: PAYMENT/INCOME/SKIP 3분류 (financialKeywords 46개)
     → Step 1.5: SmsRegexRuleMatcher.matchPaymentCandidates() ★ Fast Path
       - sender 기반 로컬 regex 룰 매칭 (Asset seed + RTDB overlay)
       - 매칭 성공 → SmsParseResult (SmsPipeline 스킵)
       - 미매칭 → SmsPipeline으로 fallback
       - 룰 통계 자동 갱신 (matchCount/failCount/priority)
       - 저품질 룰 자동 비활성화, sender/type별 5개 상한
     → SmsPipeline (Fast Path 미매칭만):
       → SmsTemplateEngine: 템플릿화 + Gemini Embedding API (배치 100건)
       → SmsPatternMatcher: 기존 패턴 DB에서 코사인 유사도 매칭 + regex 파싱 → MatchResult(matched/regexFailed/unmatched)
       → SmsGroupClassifier.batchExtractRegexFailed(): regex 실패건 배치 LLM 추출 (Step4.5)
         - 병렬 처리: Semaphore(LLM_CONCURRENCY) + async/awaitAll
         - 품질 게이트: isRecoverableRegexFailedSms() → 길이≥12 + 금액패턴 + 키워드
         - 근거 검증: isGroundedRegexFailedRecovery() → 금액 증거 + 가게명 부분문자열
         - KB 휴리스틱: tryParseKbDebitFallback() → tryHeuristicParse() (LLM 스킵)
         - 실패 쿨다운: sender:templateHash, 2회 실패→30분
       → SmsGroupClassifier.classifyUnmatched(): 미매칭+Step4.5 fallback → 3레벨 그룹핑 → LLM 배치 추출 → regex 생성 → 패턴 DB 등록
         - 5-A (unmatched): full regex policy
         - 5-B (regexFailedFallback): forceSkipRegexAll=true (regex 재생성 스킵)
         - 시간 예산: GROUP_TIME_BUDGET_MS=10초/그룹, STEP5_TIME_BUDGET_MS=20초/전체
         - unstable 그룹: 중앙값<0.92 또는 P20<0.88 → 해체 → 개별 LLM
         - 예산 초과: regex 스킵→LLM 직접 강등 (데이터 누락 방지)
         - 백그라운드 학습 큐: 성공 패턴 비동기 등록 (learningQueue, dedup)
   → saveExpenses(): SmsParseResult → ExpenseEntity 변환 + 배치 저장
   → saveIncomes(): SmsIncomeParser 파싱 → IncomeEntity 변환 + 배치 저장
   → postSyncCleanup(): 카테고리 분류 + 패턴 정리 + lastSyncTime + 카드 등록

HomeViewModel.syncIncremental(contentResolver):
   → calculateIncrementalRange() (lastSyncTime/fullSyncUnlocked/Auto Backup 감지)
   → syncSmsV2(contentResolver, range)
```

**실시간 수신**:
```
SMS 수신 → SmsReceiver → SmsInstantProcessor
MMS 수신 → MmsContentObserver → SmsInstantProcessor
RCS 수신(프로세스 생존) → RcsContentObserver → SmsInstantProcessor
RCS/비즈메시지(프로세스 cold start) → NotificationTransactionService
   → 메시지 앱 알림 파싱
   → 최근 SMS/MMS/RCS provider 원본 조회
   → SmsInstantProcessor
```

### 5-2. 카테고리 자동 분류 흐름
```
CategoryClassifierService.getCategory(storeName)
   → Tier 0: StoreRule contains 매칭 (storeName에 keyword 포함 → category/isFixed 즉시 적용)
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
- **Interface**: `TransactionCardInfo` — title, subtitle, amount, isIncome, category, iconEmoji, categoryTag, time, cardNameText, memoText, isFixed
- **구현체**: `ExpenseTransactionCardInfo`, `IncomeTransactionCardInfo`
- **Composable**: `TransactionCardCompose` — 지출/수입 통합 카드 렌더링 (고정지출 태그 포함)
- **사용처**: HomeScreen, HistoryScreen, CategoryDetailScreen

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
