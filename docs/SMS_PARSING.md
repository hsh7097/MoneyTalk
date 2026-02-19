# SMS 파싱 시스템

> 카드 결제 문자에서 지출 정보를 자동 추출하는 3-tier 하이브리드 분류 시스템

---

## 1. 시스템 개요

MoneyTalk은 SMS/MMS/RCS 문자에서 결제 정보를 자동으로 추출하기 위해 **3단계 하이브리드 분류 시스템**을 사용합니다.

```
SMS/MMS/RCS 수신 (SmsReader)
  │
  ▼
┌─────────────────────────────────────────┐
│ 사전 필터링 (최상위)                      │ ← 비용 0, 즉시 처리
│  └ 발신자 필터 (SmsFilter)               │
│  └ 100자 초과 → 비결제 (Regex 스킵)      │
│  └ 비결제 키워드 → 비결제 (Regex 스킵)    │
│  └ 결제 최소 조건 미충족 → 비결제          │
└────────────┬────────────────────────────┘
             │ 통과
             ▼
┌─────────────────────────────────────────┐
│ Tier 1: Regex (정규식)                   │ ← 비용 0, 즉시 처리
│  └ SmsParser.isCardPaymentSms()         │
│  └ SmsParser.parseSms()                 │
│  └ storeName='결제' → null (오파싱 방어)  │
└────────────┬──────────┬─────────────────┘
        성공 │          │ 실패
             ▼          ▼
     DB 저장 +    ┌────────────────────────────┐
     벡터 학습    │ Tier 2: Vector (벡터 유사도)  │ ← 임베딩 API 배치
                  │  └ 배치 임베딩 생성            │
                  │  └ 비결제 패턴 우선 검색        │
                  │  └ 결제 패턴 코사인 유사도      │
                  └──┬──────┬──────┬──────┬──────┘
                ≥0.95│  ≥0.92│ 0.80 │ <0.80│
                     ▼      ▼ ~0.92▼      ▼
                캐시   캐시     LLM   hasPotential
                재사용 폴백   트리거  PaymentIndicators
                                │      │ (2/3 지표 매칭)
                                ▼      ▼
                         ┌──────────────────────┐
                         │ Tier 3: LLM (Gemini)  │ ← API 1건, ~2초
                         │  └ 결제 여부 판정      │
                         │  └ 정보 추출           │
                         │  └ 정규식 자동 생성     │
                         └──────┬────────────────┘
                           성공 │
                                ▼
                           DB 저장 + 벡터 학습
                           + RTDB 표본 수집
```

### 왜 3단계가 필요한가?

| 단계 | 커버리지 | 비용 | 속도 |
|------|---------|------|------|
| Regex | ~70% (표준 카드 문자) | 무료 | <1ms |
| Vector | ~90% (유사 패턴 재사용) | 임베딩 API 1회 | ~1초 |
| LLM | ~99% (비표준 형식 포함) | Gemini API 1회 | ~2초 |

정규식만으로는 모든 카드사의 다양한 SMS 형식을 커버할 수 없고, 모든 SMS를 LLM에 보내면 비용/속도가 문제됩니다.

### 파싱 파이프라인 2종

| 파이프라인 | 클래스 | 사용 시점 | 특징 |
|-----------|--------|----------|------|
| Full/Initial Sync | `SmsBatchProcessor` | 전체 동기화, 월별 동기화 | 발신번호 기반 그룹핑 → 대표 LLM → 그룹 전파 |
| 증분 Sync | `HybridSmsClassifier` | 일반 증분 동기화, 실시간 수신 | batchClassify 5-step → 개별 LLM |

---

## 2. 파일 구조 및 역할

```
core/sms/
├── SmsReader.kt                 # SMS/MMS/RCS 통합 읽기 (ContentResolver)
├── SmsFilter.kt                 # 발신번호 기반 사전 필터링 + 주소 정규화
├── SmsParser.kt                 # Tier 1 정규식 파싱 (금액/가게명/카드사/카테고리)
├── SmsEmbeddingService.kt       # SMS 템플릿화 + Gemini 임베딩 API (모델 의존: gemini-embedding-001=3072차원)
├── VectorSearchEngine.kt        # 코사인 유사도 검색 엔진 (순수 벡터 연산)
├── HybridSmsClassifier.kt       # 증분 동기화용 3-tier 오케스트레이터
├── SmsBatchProcessor.kt         # Full Sync용 대량 배치 파이프라인
├── GeminiSmsExtractor.kt        # Tier 3 LLM 추출 (단건/배치/정규식 생성)
└── GeneratedSmsRegexParser.kt   # LLM 생성 정규식 파서 (폴백 체인)

core/similarity/
├── SimilarityPolicy.kt          # 유사도 판정 인터페이스
└── SmsPatternSimilarityPolicy.kt # SMS 전용 임계값 레지스트리

core/database/entity/
└── SmsPatternEntity.kt          # 벡터 DB 엔티티 (17 필드)

core/database/dao/
└── SmsPatternDao.kt             # 패턴 DB DAO

core/util/
├── CategoryReferenceProvider.kt # LLM 프롬프트용 카테고리 참조 리스트
└── DateUtils.kt                 # 날짜 검증 유틸리티

core/sms2/                        # ★ 통합 파이프라인 (신규, Tier 1 제거)
├── SmsPipeline.kt               # 오케스트레이터 (Step 2→3→4→5)
├── SmsPipelineModels.kt         # 데이터 클래스 (SmsInput, EmbeddedSms, SmsParseResult)
├── SmsPreFilter.kt              # Step 2: 사전 필터링 (키워드 + 구조)
├── SmsTemplateEngine.kt         # Step 3: 템플릿화 + Gemini Embedding API
├── SmsPatternMatcher.kt         # Step 4: 벡터 매칭 + regex 파싱
└── SmsGroupClassifier.kt        # Step 5: 그룹핑 + LLM + regex 생성
```

> **sms2 패키지**: 기존 sms 패키지의 3경로(SmsBatchProcessor, HybridSmsClassifier, SmsProcessingService)를
> 단일 `SmsPipeline.process()`로 통합. Tier 1 로컬 regex(SmsParser)를 제거하고 모든 SMS를 임베딩 경로로 처리.
> sms2는 core/sms에서 import하지 않음 (GeminiSmsExtractor만 예외). 호출자 연결은 다음 단계.

---

## 3. 메시지 읽기 — SmsReader

### 파일 위치
[`core/sms/SmsReader.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsReader.kt)

### 지원 메시지 유형

| 유형 | Content URI | 날짜 형식 | 비고 |
|------|-----------|----------|------|
| SMS | `content://sms/inbox` | 밀리초 | 표준 문자 |
| MMS | `content://mms/inbox` | **초** (×1000 변환) | 장문 문자 |
| RCS | `content://im/chat` | 밀리초 | 삼성 기기, JSON 본문 |

모든 읽기 메소드는 SMS + MMS + RCS를 통합(Unified) 조회합니다.

### 제공 메소드 (모두 SMS+MMS+RCS 통합)

| 메소드 | 용도 | 필터링 |
|-------|------|-------|
| `readAllCardMessages()` | 전체 동기화 (결제) | 정규식 필터 |
| `readCardMessagesByDateRange()` | 증분 동기화 (결제) | 기간 + 정규식 필터 |
| `readAllMessagesByDateRange()` | 하이브리드 분류용 | 필터링 없음 (전체) |
| `readAllIncomeMessages()` | 전체 동기화 (수입) | 입금 키워드 필터 |
| `readIncomeMessagesByDateRange()` | 증분 동기화 (수입) | 기간 + 입금 필터 |

### SMS ID 생성
`발신번호_수신시간_본문해시코드` → 중복 저장 방지

### 발신자 필터링 — SmsFilter

[`core/sms/SmsFilter.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsFilter.kt)

| 메소드 | 역할 |
|--------|------|
| `normalizeAddress(rawAddress)` | `+82`, 하이픈, 공백 제거 → 순수 숫자열 |
| `shouldSkipBySender(address, body)` | 010/070 + 금융 힌트 없음 → 스킵 |
| `hasFinancialHints(body)` | 금액 패턴(`3자리+원`) 또는 (3자리 숫자 + 금융 키워드) |

| 조건 | 동작 |
|------|------|
| 발신번호가 010/070으로 시작 + 금융 힌트 없음 | 스킵 (개인 문자) |
| 발신번호가 010/070으로 시작 + 금융 힌트 있음 | 통과 (카드사 알림 등) |
| 그 외 발신번호 | 통과 |

금융 힌트 키워드: `결제`, `승인`, `사용`, `출금`, `이용`, `입금`, `이체`, `송금`, `카드`, `은행`, `체크`, `kb`, `국민`, `신한` 등

### RCS 메시지 본문 파싱

RCS 메시지의 body는 JSON 형식이며, 위젯 트리 구조로 텍스트가 포함됩니다.

**`extractRcsText()` 추출 순서:**
1. 직접 텍스트 필드 검색: `text`, `body`, `message`, `msg`, `content`
2. `layout` 필드의 위젯 트리를 재귀 탐색 (`extractTextsFromLayout`)
3. `widget == "TextView"` 인 노드에서 `text` 값 수집
4. 줄바꿈(`\n`)으로 조합하여 일반 텍스트 반환

---

## 4. Tier 1: 정규식 파싱 — SmsParser

### 파일 위치
[`core/sms/SmsParser.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsParser.kt)

### 결제 문자 판별 — `isCardPaymentSms(smsBody)`

**순서:**
1. `excludeKeywords` 포함 여부 → 포함 시 false (광고, 명세서, 청구서, 결제내역, 퇴직 등)
2. `cancellationKeywords` 포함 여부 → 포함 시 false (출금취소, 승인취소 등)
3. `userExcludeKeywords` 포함 여부 → 포함 시 false (사용자 정의 제외 키워드)
4. `MAX_SMS_LENGTH`(100자) 초과 → false
5. `cardKeywordsSet` 중 하나 포함 필수 (50+ 카드사 키워드)
6. `paymentKeywords` 중 하나 포함 필수 (결제, 승인, 사용, 출금, 이용)
7. 금액 패턴 존재 필수 (`숫자+원` 또는 줄바꿈 사이 3자리+ 숫자)

### 추출 항목 및 메소드

| 항목 | 메소드 | 추출 방법 | 예시 |
|------|--------|----------|------|
| 금액 | `extractAmount(body)` | 정규식 (`숫자+원`, KB 줄바꿈 등) | 15,000원 → 15000 |
| 가게명 | `extractStoreName(body)` | 위치 기반 추출 (시간 뒤, 금액 앞/뒤) | 스타벅스 |
| 카드사 | `extractCardName(body)` | 키워드 매칭 (50+ 카드사) | KB → KB국민 |
| 날짜시간 | `extractDateTime(body, timestamp)` | MM/DD HH:mm 패턴 | 12/25 14:30 → 2025-12-25 14:30 |
| 카테고리 | `inferCategory(storeName, body)` | 190+ 키워드 매핑 | 스타벅스 → 카페 |
| 통합파싱 | `parseSms(body, timestamp)` | 위 항목 모두 한번에 | → SmsAnalysisResult |

### 카테고리 키워드 매핑 (190+ 키워드)

앱 전체 17개 카테고리: 식비, 카페, 배달, 술/유흥, 교통, 쇼핑, 구독, 의료/건강, 운동, 문화/여가, 교육, 주거, 생활, 보험, 계좌이체, 경조, 기타

이 중 `SmsParser.categoryKeywords`에서 키워드 매핑이 정의된 카테고리는 12~13개.
계좌이체, 경조, 기타 등은 LLM이 직접 판정하거나 별도 로직으로 분류. 예:
- 식비: 편의점(GS25, CU 등), 마트(이마트, 홈플러스 등), 패스트푸드 등
- 카페: 스타벅스, 투썸, 이디야, 베이커리 등
- 교통: 택시, 주유소, KTX, 하이패스, 주차 등

### KB 스타일 특수 처리

KB국민카드는 줄바꿈으로 구분된 독특한 형식:
```
[KB]
02/05 22:47
801302**775
*60원캐쉬백주식회사
체크카드출금
11,940
잔액45,091
```
→ `체크카드출금` 키워드를 기준으로 위아래 줄에서 가게명/금액 추출

### Regex 오파싱 방어

현대카드 등 일부 카드사 SMS에서 Regex가 "성공하되 잘못 파싱"하는 케이스 방어:
- `storeName == "결제"` → `classifyWithRegex()`는 null 반환
- Tier 2/3으로 이관 → Vector/LLM이 정확한 가게명 추출

### 수입 문자 판별 — `isIncomeSms(body)`

별도 키워드 세트(입금, 이체입금, 급여, 월급, 출금취소 등)로 입금 문자 감지.
자동이체출금, 카드대금, 보험료 등은 `incomeExcludeKeywords`로 필터링.

### 사용자 정의 제외 키워드

`setUserExcludeKeywords(keywords)`: DB에서 로드한 사용자 정의 키워드를 설정.
`HomeViewModel.syncSmsMessages()` 시작 전에 호출하여 사용자가 제외한 패턴을 반영.

---

## 5. SMS 템플릿화 + 임베딩 — SmsEmbeddingService

### 파일 위치
[`core/sms/SmsEmbeddingService.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsEmbeddingService.kt)

### 템플릿화 — `templateizeSms(smsBody)`

변하는 부분을 플레이스홀더로 치환하여 **구조적 유사성**을 극대화합니다.

**치환 순서 (순서 중요):**

| 순서 | 원본 패턴 | 플레이스홀더 | 예시 |
|------|----------|-------------|------|
| 1 | `[\d,]+원` | `{AMOUNT}원` | 15,000원 → {AMOUNT}원 |
| 2 | `\n[\d,]{3,}\n` (줄바꿈 사이 숫자) | `\n{AMOUNT}\n` | \n11,940\n → \n{AMOUNT}\n |
| 3 | `\d{1,2}[/.-]\d{1,2}` | `{DATE}` | 02/05 → {DATE} |
| 4 | `\d{1,2}:\d{2}` | `{TIME}` | 22:47 → {TIME} |
| 5 | `잔액[\d,]+` | `잔액{BALANCE}` | 잔액45,091 → 잔액{BALANCE} |
| 6 | `\d+\*+\d+` | `{CARD_NUM}` | 801302**775 → {CARD_NUM} |
| 7 | 가게명 줄 (줄바꿈 SMS만) | `{STORE}` | 스타벅스 → {STORE} |

**가게명 치환 규칙 (7단계):**
- 줄바꿈 4줄 이상인 SMS에서만 적용
- `isLikelyStoreName(line)` 판별:
  - 빈 줄, 2자 미만, 20자 초과 → 가게명 아님
  - `{` 포함 → 이미 플레이스홀더 → 가게명 아님
  - `structuralKeywords` 포함 → 구조 키워드 → 가게명 아님
    - 구조 키워드: `출금`, `입금`, `승인`, `결제`, `이체`, `잔액`, `[web발신]`, `누적`, `일시불`, `할부`, `체크카드`, `해외승인`
  - 숫자/콤마로만 구성 → 가게명 아님
  - 한글/영문/`(`/`*`로 시작 → **가게명 후보** (최대 1개만 치환)

**효과:** 동일 카드사 SMS가 가게명만 다를 때 동일 템플릿으로 수렴
```
변경 전: [Web발신]\n[KB]{DATE} {TIME}\n{CARD_NUM}\n스타벅스\n출금\n{AMOUNT}\n잔액{BALANCE}
변경 후: [Web발신]\n[KB]{DATE} {TIME}\n{CARD_NUM}\n{STORE}\n출금\n{AMOUNT}\n잔액{BALANCE}
```

### 임베딩 생성

Gemini Embedding API (RTDB 원격 설정 모델, 기본값 `gemini-embedding-001`)로 벡터 생성. 차원 수는 모델 의존 (gemini-embedding-001 = 3072차원).

| 메소드 | API | 용도 |
|--------|-----|------|
| `generateEmbedding(text)` | `embedContent` | 단건 임베딩 |
| `generateEmbeddings(texts)` | `batchEmbedContents` | 배치 임베딩 (최대 100건) |

### Rate Limit 처리 (429)
- `MAX_RETRIES = 3` (최대 3회 재시도)
- 지수 백오프: 2s, 4s
- Quota 초과 (`exceeded your current quota`) → 재시도 불가, 즉시 실패
- Rate Limit → 재시도

---

## 6. 벡터 유사도 검색 — VectorSearchEngine

### 파일 위치
[`core/sms/VectorSearchEngine.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/VectorSearchEngine.kt)

### 코사인 유사도

```
코사인 유사도 = (A·B) / (|A| × |B|)
범위: -1 ~ 1 (1에 가까울수록 유사)
```

내부 최적화: FloatArray boxing/unboxing 오버헤드 제거, RandomAccess 체크

### 검색 메소드

| 메소드 | 용도 | 반환 |
|--------|------|------|
| `cosineSimilarity(A, B)` | 두 벡터 유사도 계산 | Float |
| `findBestMatch(query, patterns, minSimilarity)` | 최고 유사 패턴 1개 | SearchResult? |
| `findTopK(query, patterns, topK, minSimilarity)` | 상위 K개 유사 패턴 | List<SearchResult> |
| `findBestStoreMatch(query, embeddings, minSimilarity)` | 최고 유사 가게명 1개 | StoreSearchResult? |
| `findSimilarStores(query, embeddings, minSimilarity)` | 유사 가게명 전체 | List<StoreSearchResult> |

---

## 7. 유사도 임계값 레지스트리 — SmsPatternSimilarityPolicy

### 파일 위치
[`core/similarity/SmsPatternSimilarityPolicy.kt`](../app/src/main/java/com/sanha/moneytalk/core/similarity/SmsPatternSimilarityPolicy.kt)

### 임계값 (SSOT)

| 상수 | 값 | 용도 | 사용처 |
|------|---|------|--------|
| `profile.autoApply` | **0.95** | 캐시된 파싱 결과 재사용 | HybridSmsClassifier, SmsBatchProcessor |
| `profile.confirm` | **0.92** | 결제 문자 판정 (벡터 매칭 확정) | HybridSmsClassifier, SmsBatchProcessor Step1 |
| `profile.group` | **0.95** | SMS 패턴 그룹핑 (SmsBatchProcessor Step3) | SmsBatchProcessor.groupBySimilarityInternal() |
| `NON_PAYMENT_CACHE_THRESHOLD` | **0.97** | 비결제 패턴 캐시 히트 | HybridSmsClassifier Step4 |
| `LLM_TRIGGER_THRESHOLD` | **0.80** | LLM 요청 대상 선별 (결제 판정 기준 아님) | HybridSmsClassifier Step4 |

### 판정 로직 플로우

```
유사도 결과
  │
  ├─ ≥ 0.97 (비결제 패턴) → 즉시 비결제 판정
  │
  ├─ ≥ 0.95 (autoApply) → 캐시 재사용 (저장된 파싱 결과 그대로, 금액/날짜만 재추출)
  │
  ├─ 0.92 ~ 0.95 (confirm) → 결제 확정 + 캐시 폴백으로 정보 추출
  │
  ├─ 0.80 ~ 0.92 (LLM trigger) → LLM 호출하여 확인
  │
  └─ < 0.80 → hasPotentialPaymentIndicators() → 통과 시 LLM, 미통과 시 비결제
```

---

## 8. 증분 동기화 파이프라인 — HybridSmsClassifier

### 파일 위치
[`core/sms/HybridSmsClassifier.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/HybridSmsClassifier.kt)

### 사용 시점
- 일반 증분 동기화 (`syncSmsMessages` + `batchClassify`)
- 미분류 SMS 수동 재분류

### 설정 상수

| 상수 | 값 | 설명 |
|------|---|------|
| `EMBEDDING_BATCH_SIZE` | 100 | batchEmbedContents 최대 개수 |
| `EMBEDDING_CONCURRENCY` | 10 | 임베딩 배치 병렬 동시 실행 수 |
| `LLM_CONCURRENCY` | 5 | LLM 병렬 동시 실행 수 |
| `STALE_PATTERN_THRESHOLD_MS` | 30일 | 오래된 패턴 정리 기준 |

### batchClassify 5-step 파이프라인

```
batchClassify(smsList: List<Triple<body, timestamp, address>>)
  │
  ├── Step 1: 사전 필터링 (Regex보다 먼저 — 명백한 비결제 조기 제거)
  │   ├ 20자 미만 / 100자 초과 → 비결제
  │   └ isObviouslyNonPayment(body) → 비결제
  │
  ├── Step 2: Regex (사전 필터 통과한 SMS만)
  │   ├ classifyWithRegex(body, timestamp)
  │   │   ├ SmsParser.isCardPaymentSms(body) → false이면 null
  │   │   ├ SmsParser.parseSms(body, timestamp)
  │   │   └ storeName == "결제" → null (오파싱 방어)
  │   └ 성공 시 ClassificationResult(tier=1, confidence=1.0)
  │
  ├── Step 3: 배치 임베딩 생성 (Regex 미통과분)
  │   ├ embeddingService.templateizeSms(body)
  │   ├ 100건씩 chunking
  │   └ Semaphore(10) 병렬 → embeddingService.generateEmbeddings(batch)
  │
  ├── Step 4: 벡터 DB 매칭
  │   ├ 4-a: 비결제 패턴 우선 매칭
  │   │   └ ≥ 0.97 → 비결제 확정
  │   ├ 4-b: 결제 패턴 매칭 (minSimilarity = 0.80)
  │   │   ├ ≥ 0.95 → 캐시 재사용 (buildAnalysisFromPattern)
  │   │   ├ 0.92 ~ 0.95 → 캐시 폴백 (buildAnalysisFromPattern)
  │   │   └ 0.80 ~ 0.92 → LLM 트리거 후보
  │   └ 4-c: < 0.80 → hasPotentialPaymentIndicators() → LLM 후보
  │
  └── Step 5: LLM 호출 (병렬)
      ├ Semaphore(5) 병렬 → classifyWithLlm(body, timestamp, address)
      │   └ smsExtractor.extractFromSms(body, timestamp) → LlmExtractionResult
      └ 결제 성공 시 → learnPatternWithLlmRegex() 패턴 학습
          ├ 임베딩 생성
          ├ 정규식 생성 (smsExtractor.generateRegexForSms)
          └ SmsPatternEntity 저장 (parseSource = "llm" or "llm_regex")
```

### buildAnalysisFromPattern — 캐시 재사용 파싱 체인

```
buildAnalysisFromPattern(smsBody, smsTimestamp, pattern)
  │
  ├── 1. GeneratedSmsRegexParser.hasUsableRegex(pattern) → true이면
  │   └ parseWithPattern(smsBody, smsTimestamp, pattern)
  │      └ 정규식으로 금액/가게명/카드사 추출 + 폴백
  │
  └── 2. 정규식 없거나 실패 시
      ├ SmsParser.extractAmount(smsBody) ?? pattern.parsedAmount
      ├ extractStoreNameOrCached(smsBody, pattern.parsedStoreName)
      │   └ SmsParser.extractStoreName() → "결제" 아니고 2자 이상이면 채택, 아니면 캐시
      ├ SmsParser.extractCardName(smsBody) → "기타"면 캐시 사용
      └ pattern.parsedCategory ?? SmsParser.inferCategory()
```

### 비결제 키워드 (NON_PAYMENT_KEYWORDS)

인증/보안: `인증번호`, `OTP`, `authentication`, `verification` 등
해외 발신: `국외발신`, `국제발신`, `해외발신`
광고: `광고`, `수신거부`, `프로모션`, `할인쿠폰` 등
청구/안내: `결제내역`, `명세서`, `청구서`, `이용대금`, `결제예정`, `결제금액`, `카드대금`, `청구금액`, `출금예정`, `자동이체` 등
배송: `배송`, `택배`, `운송장`

### 결제 가능성 사전 체크 — `hasPotentialPaymentIndicators(body)`

3가지 지표 중 **2개 이상** 매칭 시 LLM 호출 허용:

| 지표 | 체크 방법 | 예시 |
|------|----------|------|
| 금액 패턴 | `[\d,]+원` 정규식 | "15,000원" |
| 결제 키워드 | 승인/결제/출금/사용/이용/체크카드/신용카드/누적 | "승인" |
| 카드사 키워드 | `SmsParser.extractCardName()` ≠ "기타" | "신한" → true |

### 자가 학습

| 학습 메소드 | 트리거 | parseSource |
|------------|--------|-------------|
| `learnPatternWithLlmRegex()` | LLM 결제 성공 (batchClassify Step5) | `llm` or `llm_regex` |
| `batchLearnFromRegexResults(items)` | Regex 성공 결과 배치 학습 (동기화 후 백그라운드) | `regex` |
| `learnNonPaymentPattern()` | LLM 비결제 판정 | `llm_non_payment` |

### 오래된 패턴 정리 — `cleanupStalePatterns()`
- 30일 이상 미사용 + 1회만 매칭된 패턴 삭제
- 동기화 완료 후 호출

---

## 9. Full Sync 파이프라인 — SmsBatchProcessor

### 파일 위치
[`core/sms/SmsBatchProcessor.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsBatchProcessor.kt)

### 사용 시점
- 전체 동기화 (forceFullSync)
- 월별 동기화 (광고 시청 후 특정 월 동기화)

### 설정 상수

| 상수 | 값 | 설명 |
|------|---|------|
| `EMBEDDING_BATCH_SIZE` | 100 | 배치 임베딩 최대 개수 |
| `EMBEDDING_CONCURRENCY` | 10 | 임베딩 병렬 동시 실행 수 |
| `LLM_BATCH_SIZE` | 20 | LLM 배치 호출 시 그룹 수 |
| `LLM_CONCURRENCY` | 5 | LLM 병렬 동시 실행 수 |
| `MAX_UNCLASSIFIED_TO_PROCESS` | 500 | 한 번에 처리할 최대 SMS 수 |
| `REGEX_SAMPLE_SIZE` | 3 | 정규식 생성 시 사용할 샘플 수 |
| `REGEX_MIN_SAMPLES_FOR_GENERATION` | 3 | 정규식 생성 최소 멤버 수 |
| `REGEX_FAILURE_THRESHOLD` | 2 | 정규식 실패 쿨다운 기준 횟수 |
| `REGEX_FAILURE_COOLDOWN_MS` | 30분 | 정규식 실패 쿨다운 시간 |
| `SMALL_GROUP_MERGE_THRESHOLD` | 5 | 소그룹 병합 기준 멤버 수 |
| `RTDB_DEDUP_SIMILARITY` | 0.99 | RTDB 표본 중복 판정 유사도 |

### processBatch 4-step 파이프라인

```
processBatch(unclassifiedSms, maxProcessCount=500)
  │
  ├── 사전 필터링
  │   ├ isObviouslyNonPayment(body) → 키워드 기반 비결제 제외
  │   └ lacksPaymentRequirements(body) → 구조적 필터링
  │       ├ 20자 미만 / 100자 초과 → 제외
  │       ├ 숫자 없음 → 제외
  │       ├ 2자리 이상 연속 숫자 없음 → 제외
  │       ├ HTTP 링크 + 결제/승인 키워드 없음 → 제외
  │       └ 결제 힌트 키워드 없음 + 금액 패턴 없음 → 제외
  │
  ├── Step 1+2 통합: matchAgainstExistingPatterns()
  │   ├ smsPatternDao.getAllPaymentPatterns()
  │   ├ 전체 SMS 템플릿화 → embeddingService.templateizeSms()
  │   ├ 100건씩 배치 → Semaphore(10) 병렬 임베딩 생성
  │   ├ VectorSearchEngine.findBestMatch(minSimilarity=0.92)
  │   │   ├ 매칭 성공 → GeneratedSmsRegexParser.parseWithPattern() 우선
  │   │   │           → 실패 시 SmsParser 폴백 → SmsAnalysisResult
  │   │   └ 매칭 실패 → **임베딩 보존** (Triple<SmsData, template, embedding>)
  │   └ 반환: (매칭 결과, 미매칭+임베딩)
  │   ※ 핵심: Step1에서 생성한 임베딩을 Step3에서 재사용 → 중복 API 호출 제거
  │
  ├── Step 3: 발신번호 기반 2레벨 그룹핑 — groupByAddressThenSimilarity()
  │   ├ Level 1: 발신번호(address)별 그룹핑 (O(n), API 호출 없음)
  │   │   └ SmsFilter.normalizeAddress()로 +82/하이픈 정규화
  │   ├ Level 2: 같은 발신번호 내 벡터 유사도 기반 서브그룹핑
  │   │   └ groupBySimilarityInternal() — 그리디 클러스터링 (유사도 ≥ 0.95)
  │   └ Level 3: 소그룹 병합 — mergeSmallGroups()
  │       └ 멤버 ≤ 5인 소그룹 → 최대 그룹에 흡수
  │          (같은 카드사 변형: 해외승인, ATM출금 등)
  │
  └── Step 4: 그룹 대표 LLM 분석 + 정규식 생성 + 멤버 전파
      ├ 4-A: LLM 배치 추출 (병렬)
      │   ├ 20개씩 그룹 배치 → Semaphore(5) 병렬
      │   └ smsExtractor.extractFromSmsBatch(smsTexts, smsTimestamps)
      │       → List<LlmExtractionResult?>
      │
      ├ 4-B: 결제 그룹 필터링
      │   └ isPayment && amount > 0 인 그룹만 통과
      │
      ├ 4-C: 정규식 생성 (병렬)
      │   ├ 쿨다운 체크: shouldSkipRegexGeneration(templateKey)
      │   ├ 최소 멤버 3건 미만 → 생성 스킵
      │   ├ smsExtractor.generateRegexForGroup(sampleBodies, sampleTimestamps)
      │   │   → LlmRegexResult (amountRegex, storeRegex, cardRegex)
      │   ├ 성공 → clearRegexFailure() / 실패 → recordRegexFailure()
      │   └ LLM 정규식 실패 시 → buildTemplateFallbackRegex()
      │       ├ 템플릿에 {STORE}, {AMOUNT} 있으면 최소 정규식 생성
      │       └ 멀티라인/단일라인 패턴 자동 판별
      │
      ├ 4-D: parseSource 결정
      │   ├ LLM 정규식 성공 → "llm_regex"
      │   ├ 템플릿 폴백 정규식 성공 → "template_regex"
      │   └ 정규식 없음 → "llm"
      │
      ├ 4-E: 대표 SMS 파싱
      │   ├ 정규식 있으면 → GeneratedSmsRegexParser.parseWithRegex() 우선
      │   └ 없으면 → LLM 추출 결과 직접 사용 (fallback)
      │
      ├ 4-F: 패턴 등록 — registerPattern()
      │   ├ SmsPatternEntity 생성 → smsPatternDao.insert()
      │   └ collectSampleToRtdb() → Firebase RTDB에 표본 전송
      │
      └ 4-G: 멤버 전파 (그룹 내 각 SMS에 파싱 적용)
          ├ 정규식 있으면 → GeneratedSmsRegexParser.parseWithRegex(member.body)
          └ 없으면 → 개별 추출
              ├ SmsParser.extractAmount(member.body) ?? 대표 금액
              ├ SmsParser.extractStoreName(member.body) → "결제" 아니고 2자+면 채택
              │   └ 다르면 SmsParser.inferCategory(memberStoreName, member.body)
              └ SmsParser.extractDateTime(member.body, member.date)
              ※ 발신번호 그룹에서는 멤버마다 가게명이 다르므로 개별 추출 필수
```

### 사전 필터링 키워드 (NON_PAYMENT_KEYWORDS)

HybridSmsClassifier보다 더 확장된 목록:
- 인증/보안, 해외 발신, 광고/마케팅, 홍보/유혹, 안내/기타
- 청구/안내: `결제내역`, `명세서`, `청구서`, `이용대금` 등
- 배송, 설문/투표, 예약/안내, 금융/부동산 광고
- 결제 힌트 키워드: `승인`, `결제`, `출금`, `이체`, `원`, `USD`, `JPY`, `EUR`, `카드`, `체크`, `CMS`

### 구조적 필터링 — `lacksPaymentRequirements(body)`

결제 SMS 최소 조건:
1. 20~100자 길이
2. 숫자 존재
3. 2자리 이상 연속 숫자 존재
4. HTTP 링크만 있고 결제/승인 키워드 없으면 → 제외
5. 결제 힌트 키워드 또는 금액 패턴(`숫자+원`) 중 하나 이상 필요

---

## 10. LLM 추출 — GeminiSmsExtractor

### 파일 위치
[`core/sms/GeminiSmsExtractor.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/GeminiSmsExtractor.kt)

### 3종 모델 (Firebase RTDB 원격 관리)

| 용도 | 모델 변수 | Config 필드 | temperature | maxTokens |
|------|----------|-------------|-------------|-----------|
| 단건 추출 | `extractorModel` | `smsExtractor` | 0.1 | 1024 |
| 배치 추출 | `batchExtractorModel` | `smsBatchExtractor` | 0.1 | 4096 |
| 정규식 생성 | `regexExtractorModel` | `smsRegexExtractor` | 0.0 | 8192 |

### System Instruction 프롬프트
`res/values/string_prompt.xml`에서 관리:
- `prompt_sms_extract_system`: 단건 추출용
- `prompt_sms_batch_extract_system`: 배치 추출용
- `prompt_sms_regex_extract_system`: 정규식 생성용

### LLM 추출 결과

```kotlin
data class LlmExtractionResult(
    val isPayment: Boolean,
    val amount: Int,
    val storeName: String,
    val cardName: String,
    val dateTime: String,
    val category: String
)
```

### LLM 정규식 결과

```kotlin
data class LlmRegexResult(
    val isPayment: Boolean,
    val amountRegex: String,   // 첫 번째 캡처 그룹으로 금액 추출
    val storeRegex: String,    // 첫 번째 캡처 그룹으로 가게명 추출
    val cardRegex: String      // 첫 번째 캡처 그룹으로 카드사 추출
)
```

### 주요 메소드

| 메소드 | 용도 | 입력 | 출력 |
|--------|------|------|------|
| `extractFromSms(body, timestamp)` | 단건 LLM 추출 | SMS 1건 | LlmExtractionResult? |
| `extractFromSmsBatch(bodies, timestamps)` | 배치 LLM 추출 | SMS N건 | List<LlmExtractionResult?> |
| `generateRegexForSms(body, timestamp)` | 단건 정규식 생성 | SMS 1건 | LlmRegexResult? |
| `generateRegexForGroup(bodies, timestamps)` | 그룹 정규식 생성 | SMS 3건 | LlmRegexResult? |

### 카테고리 정규화 — `normalizeCategory(rawCategory)`

LLM이 반환한 비표준 카테고리를 앱 카테고리로 매핑:
- `온라인쇼핑` → `쇼핑`, `편의점` → `식비`, `헬스` → `운동`
- `이체` → `계좌이체`, `음식` → `식비`, `커피` → `카페`
- 매핑 실패 시 부분 일치 검색 → 최종 실패 시 `기타`

### 참조 리스트 — CategoryReferenceProvider

SMS 추출 프롬프트에 동적 참조 리스트 추가:
- 파일: [`core/util/CategoryReferenceProvider.kt`](../app/src/main/java/com/sanha/moneytalk/core/util/CategoryReferenceProvider.kt)
- source="user" 매핑 우선, 카테고리당 최대 5개 예시
- 사용자가 학습시킨 가게명→카테고리 매핑을 LLM에 주입

### 배치 추출 재시도

- `BATCH_MAX_RETRIES = 2` (최대 2회 재시도)
- `BATCH_RETRY_BASE_DELAY_MS = 1000ms` (병렬 환경 대기)
- 배치 실패 시 → 개별 폴백 호출 (`FALLBACK_SINGLE_DELAY_MS = 50ms` 간격)

### 정규식 검증

정규식 생성 후 샘플 SMS에서 검증:
- `REGEX_MIN_SUCCESS_RATIO = 0.8` (80% 이상 성공해야 채택)
- `REGEX_MIN_AMOUNT = 100` (금액 100원 미만은 날짜/시간 오탐)
- 가게명 검증: 숫자만, 날짜/시간 패턴, 카드 마스킹 패턴, 무효 키워드 필터
- `REGEX_REPAIR_MAX_RETRIES = 0` — 정규식 수선(repair) 비활성 상태. 검증 실패 시 즉시 null 반환

---

## 11. 생성 정규식 파서 — GeneratedSmsRegexParser

### 파일 위치
[`core/sms/GeneratedSmsRegexParser.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/GeneratedSmsRegexParser.kt)

### 역할
LLM이 생성한 정규식으로 SMS를 파싱. 실패 시 SmsParser 폴백.

### 파싱 체인 — `parseWithRegex()`

```
parseWithRegex(smsBody, smsTimestamp, amountRegex, storeRegex, cardRegex, fallbacks)
  │
  ├── 1. amountRegex로 금액 추출 → 실패 시 SmsParser.extractAmount() → 실패 시 fallbackAmount
  ├── 2. storeRegex로 가게명 추출 → sanitizeStoreName() → isValidStoreCandidate() 검증
  │   └ 실패 시 SmsParser.extractStoreName() → fallbackStoreName
  ├── 3. cardRegex로 카드사 추출 → 검증 → 실패 시 SmsParser.extractCardName() → fallbackCardName
  ├── 4. SmsParser.extractDateTime(body, timestamp) 날짜 추출
  └── 5. SmsParser.inferCategory(storeName, body) 카테고리 추론
```

### 주요 메소드

| 메소드 | 용도 |
|--------|------|
| `hasUsableRegex(pattern)` | amountRegex + storeRegex 둘 다 있는지 |
| `parseWithPattern(body, timestamp, pattern)` | SmsPatternEntity의 정규식으로 파싱 |
| `parseWithRegex(body, timestamp, ...)` | 정규식 문자열로 파싱 (폴백 체인 포함) |

### Regex 캐시
`ConcurrentHashMap<String, Regex>` — 같은 정규식 문자열의 재컴파일 방지

---

## 12. Firebase RTDB SMS 표본 수집

### 목적
카드사별 SMS 형식 표본을 수집하여 향후 정규식 주입에 활용.

**수집 전략:**
1. 각 카드사별 SMS 표본 수집 (마스킹된 원본 + 카드명 + 정규식)
2. 수집된 표본으로 카드사별 정규식 생성
3. 앱 배포 시 정규식 적용 또는 RTDB로 정규식 주입

### 수집 시점
`SmsBatchProcessor.registerPattern()` → `collectSampleToRtdb()` 호출.
**모든 parseSource** (regex, llm, llm_regex, template_regex)에서 수집.

### RTDB 데이터 구조

```
sms_samples/
  └── {senderAddress}_{templateHashCode}/
      ├── maskedBody: String     # PII 마스킹된 SMS 본문
      ├── cardName: String       # 카드사명
      ├── senderAddress: String  # 발신번호
      ├── parseSource: String    # 파싱 소스
      ├── amountRegex: String?   # 금액 추출 정규식
      ├── storeRegex: String?    # 가게명 추출 정규식
      ├── cardRegex: String?     # 카드사 추출 정규식
      ├── count: ServerValue.increment(1)  # 수집 횟수
      └── lastSeen: ServerValue.TIMESTAMP  # 마지막 수집 시간
```

### 중복 방지 — 임베딩 유사도 기반

```
sentSampleEmbeddings: MutableList<List<Float>>

새 SMS 임베딩 vs 기존 전송 임베딩 → 코사인 유사도 ≥ 0.99이면 스킵
(사실상 동일한 형식의 SMS만 중복으로 판단)
```

### PII 마스킹 — `maskSmsBody(smsBody)`

**마스킹 순서 (순서 중요 — 앞단계에서 패턴을 보존해야 뒷단계 정확도 유지):**

| 순서 | 대상 | 패턴 | 결과 | 이유 |
|------|------|------|------|------|
| 1 | 가게명 | `isLikelyStoreName()` (줄바꿈 SMS) | `***` (길이 ≤ 10) | 원본 텍스트 상태에서 판별해야 정확 |
| 2 | 카드번호 | `\d+\*+\d+` | `****` | 마스킹된 카드번호 패턴 |
| 3 | 날짜 | `\d{1,2}[/.-]\d{1,2}` | `**/**` | 금액보다 먼저 — 숫자 공유 방지 |
| 4 | 시간 | `\d{1,2}:\d{2}` | `**:**` | 구분자(`:`) 보존 |
| 5 | 금액 | `(\d{1,3})(,\d{3})*` | `*,***` | 쉼표 구분자 보존 |
| 6 | 남은 숫자 | `\d+` | `***` | 잔여 숫자 모두 마스킹 |

**마스킹 예시:**
```
원본: [Web발신]\n[KB]02/05 22:47\n801302**775\n스타벅스\n체크카드출금\n11,940\n잔액45,091
마스킹: [Web발신]\n[KB]**/** **:**\n*********\n****\n체크카드출금\n**,***\n잔액**,***
```

---

## 13. 동기화 흐름 — HomeViewModel.syncSmsMessages

### 파일 위치
[`feature/home/ui/HomeViewModel.kt`](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeViewModel.kt)

### 전체 흐름 (4 Phase)

```
syncSmsMessages(contentResolver, forceFullSync, todayOnly, targetMonthRange)
│
├── 0. 동기화 범위 결정
│   ├ 첫 동기화 + 미해제 → 2개월 전부터 (DEFAULT_SYNC_PERIOD_MILLIS=60일)
│   ├ 첫 동기화 + 해제 → 전체 (0L)
│   ├ targetMonthRange 지정 → 해당 월만 (광고 시청 후 월별 동기화)
│   ├ forceFullSync + 해제 → 전체, 미해제 → 2개월
│   └ 증분 → lastSyncTime 이후
│
├── Phase 1: 수입 SMS 분리 + 중복 필터 (API 불필요)
│   ├ smsReader.readAllMessagesByDateRange(startDate, endDate)
│   │   └ SMS+MMS+RCS 통합 + SmsFilter.shouldSkipBySender()
│   ├ isIncomeSms(body) → incomeCandidates
│   └ 나머지 → paymentCandidates
│
├── Phase 2: 수입 처리 (SmsParser 정규식, API 불필요)
│   └ for sms in incomeCandidates:
│       ├ SmsParser.extractAmount(body) → 금액 추출
│       ├ SmsParser.extractStoreName(body) → 출처
│       └ incomeRepository.insertOrUpdate(IncomeEntity)
│
├── Phase 3: SmsBatchProcessor로 전체 결제 SMS 처리
│   ├ 500건씩 chunking
│   └ for chunk in chunks:
│       ├ smsBatchProcessor.processBatch(chunk)
│       │   └ (사전필터 → Step1+2 벡터매칭 → Step3 그룹핑 → Step4 LLM+정규식)
│       └ expenseRepository.insertOrUpdate(ExpenseEntity)
│   └ hybridSmsClassifier.cleanupStalePatterns() — 30일 미사용 패턴 정리
│
└── Phase 4: 카테고리 분류 (다이얼로그 유지한 채 인라인 처리)
    └ if hasGeminiKey && unclassifiedCount > 0:
        └ categoryClassifierService.classifyUnclassifiedExpenses()
            → classifiedCount 반환
```

### SyncResult 구조

```kotlin
data class SyncResult(
    val expenseCount: Int,
    val incomeCount: Int,
    val detectedCardNames: List<String>,
    val classifiedCount: Int    // Phase 4 카테고리 분류 건수
)
```

---

## 14. 데이터베이스 스키마

### sms_patterns 테이블 (SmsPatternEntity)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동 증가 ID |
| smsTemplate | String | 템플릿화된 SMS 본문 ({AMOUNT}, {DATE}, {TIME}, {STORE}, {BALANCE}, {CARD_NUM}) |
| senderAddress | String | 발신 번호 |
| embedding | List<Float> → JSON | 임베딩 벡터 (모델 의존: gemini-embedding-001=3072차원, FloatListConverter) |
| isPayment | Boolean | 결제 문자 여부 (true: 결제, false: 비결제) |
| parsedAmount | Int | 캐시된 결제 금액 |
| parsedStoreName | String | 캐시된 가게명 |
| parsedCardName | String | 캐시된 카드사명 |
| parsedCategory | String | 캐시된 카테고리 |
| amountRegex | String | LLM 생성 금액 추출 정규식 (첫 번째 캡처 그룹) |
| storeRegex | String | LLM 생성 가게명 추출 정규식 (첫 번째 캡처 그룹) |
| cardRegex | String | LLM 생성 카드사 추출 정규식 (첫 번째 캡처 그룹) |
| parseSource | String | 파싱 소스 |
| confidence | Float | 신뢰도 (0.0~1.0) |
| matchCount | Int | 매칭 횟수 |
| createdAt | Long | 생성 시간 (밀리초) |
| lastMatchedAt | Long | 마지막 매칭 시간 (밀리초) |

### parseSource 값

| 값 | 의미 | confidence | 발생 위치 |
|----|------|-----------|----------|
| `regex` | Tier 1 정규식 성공 | 1.0 | batchLearnFromRegexResults() |
| `llm` | Tier 3 LLM 추출 (정규식 생성 실패) | 0.8 | registerPattern(), learnPatternWithLlmRegex() |
| `llm_regex` | Tier 3 LLM + 정규식 생성 성공 | 1.0 | registerPattern(), learnPatternWithLlmRegex() |
| `template_regex` | LLM 실패 + 템플릿 폴백 정규식 | 1.0 | registerPattern() |
| `llm_non_payment` | LLM이 비결제로 판정 | 0.8 | learnNonPaymentPattern() |

---

## 15. 성능 최적화 요약

### 비용 최적화

| 최적화 | 효과 |
|--------|------|
| 사전 필터링 (키워드 + 구조) | 비결제 SMS를 임베딩/LLM 호출 전에 조기 제거 |
| 발신번호 기반 1차 그룹핑 | 37그룹 → 2~4그룹으로 축소 |
| 임베딩 재사용 (Step1→Step3) | Step2 임베딩 API 호출 완전 제거 |
| 소그룹 병합 | 같은 카드사 변형 그룹 흡수 → LLM 호출 감소 |
| 벡터 캐시 (패턴 재사용) | 동일 형식 SMS는 LLM 없이 처리 |
| 비결제 패턴 캐싱 | LLM 비결제 판정 결과도 벡터 DB에 등록 |
| 정규식 실패 쿨다운 | 동일 템플릿 정규식 반복 실패 시 30분 쿨다운 |

### 속도 최적화

| 최적화 | 효과 |
|--------|------|
| 임베딩 병렬화 (Semaphore 10) | 100건 배치 × N개 동시 실행 |
| LLM 병렬화 (Semaphore 5) | 배치 간 병렬 실행 (직렬 → 병렬) |
| 정규식 생성 병렬화 (Semaphore 5) | 그룹별 정규식 생성 동시 실행 |
| 고정 딜레이 제거 | 429 발생 시에만 백오프 (고정 대기 없음) |
| Regex 캐시 (ConcurrentHashMap) | 동일 정규식 재컴파일 방지 |
| 코사인 유사도 최적화 | FloatArray boxing/unboxing 오버헤드 제거 |

---

## 16. RTDB 정규식 다운로드 로드맵 (미구현)

> 현재는 **업로드만** 구현됨 (앱 → RTDB). 향후 **다운로드 경로** (RTDB → 앱)를 추가하여 Cold Start 문제를 해결하고 LLM 의존도를 줄이는 것이 목표.

### 현재 흐름 vs 목표 흐름

```
[현재] 업로드만:
  앱 → maskedBody + amountRegex/storeRegex/cardRegex → RTDB sms_samples/

[목표] 업로드 + 다운로드:
  앱 → 표본 수집 → RTDB sms_samples/          (기존 유지)
  RTDB sms_regex_rules/ → 검증된 정규식 → 앱    (신규)
```

### 왜 정규식 다운로드가 필요한가?

| 문제 | 현재 | 정규식 다운로드 후 |
|------|------|------------------|
| Cold Start | 첫 설치 시 패턴 0개 → 전부 LLM 경유 | 주요 카드사 regex 사전 배포 → LLM 호출 최소화 |
| LLM 비용 | 신규 사용자일수록 Gemini API 호출 多 | 검증된 regex로 대부분 처리 |
| 속도 | Vector(~1초) + LLM(~2초) | Regex(<1ms) 수준으로 즉시 파싱 |

### RTDB 정규식 규칙 구조 (제안)

```
sms_regex_rules/                              ← 신규 RTDB 노드
  └── {senderAddress}_{templateHash}/         ← sms_samples와 동일 키 체계
       ├── amountRegex: String                # 금액 추출 정규식 (group1 캡처)
       ├── storeRegex: String                 # 가게명 추출 정규식 (group1 캡처)
       ├── cardRegex: String                  # 카드사 추출 정규식 (group1 캡처)
       ├── dateRegex: String                  # 날짜 추출 정규식 (신규, group1 캡처)
       ├── cardName: String                   # 카드사명 (예: "KB국민카드")
       ├── senderAddress: String              # 발신번호
       ├── sampleTemplate: String             # 매칭 대상 템플릿 (유사도 비교용)
       ├── version: Int                       # 규칙 버전 (업데이트 추적)
       ├── verified: Boolean                  # 수동 검증 여부
       └── minAppVersion: Int                 # 호환 최소 앱 버전코드
```

### 정규식 필드별 역할 및 예시

각 정규식은 **첫 번째 캡처 그룹** `(...)` 으로 값을 추출합니다. `GeneratedSmsRegexParser.parseWithRegex()`가 이미 이 규약을 사용하므로 **파서 코드 변경 없이** RTDB regex를 그대로 적용 가능합니다.

#### amountRegex — 금액 추출

결제 금액을 캡처합니다. 콤마 포함 숫자를 추출하고, 파서가 콤마를 제거하여 Int로 변환합니다.

```
KB 출금 형식 (줄바꿈):
  SMS: ...\n체크카드출금\n11,940\n잔액45,091
  amountRegex: "체크카드출금\n([\d,]+)\n"
  매칭: group1 = "11,940" → 11940

신한 승인 형식 (인라인):
  SMS: 신한카드 15,000원 승인 스타벅스 01/15 14:30
  amountRegex: "([\d,]+)원"
  매칭: group1 = "15,000" → 15000

현대 결제 형식:
  SMS: 현대카드 승인 금액:32,500원 가맹점:스타벅스
  amountRegex: "금액[:\s]*([\d,]+)원"
  매칭: group1 = "32,500" → 32500

해외 결제 형식:
  SMS: [KB]해외승인 USD 12.50 STARBUCKS
  amountRegex: "USD\s*([\d.]+)"
  매칭: group1 = "12.50"
```

폴백 체인: amountRegex 실패 → `SmsParser.extractAmount(body)` → fallbackAmount

#### storeRegex — 가게명 추출

가게명(상호명)을 캡처합니다. 카드사마다 가게명 위치가 다르므로 **위치 기반 패턴**이 핵심입니다.

```
KB 출금 형식 (줄바꿈 — 가게명이 독립 줄):
  SMS: [Web발신]\n[KB]02/05 22:47\n801302**775\n스타벅스\n체크카드출금\n11,940\n...
  storeRegex: "\d+\*+\d+\n(.+?)\n"
  매칭: group1 = "스타벅스"

신한 승인 형식 (금액 뒤):
  SMS: 신한카드 15,000원 승인 스타벅스 01/15 14:30
  storeRegex: "원\s+승인\s+(.+?)\s+\d{1,2}/"
  매칭: group1 = "스타벅스"

현대 결제 형식 (키=값):
  SMS: 현대카드 승인 금액:32,500원 가맹점:스타벅스
  storeRegex: "가맹점[:\s]*(.+?)(?:\s|$)"
  매칭: group1 = "스타벅스"

삼성 결제 형식:
  SMS: 삼성카드 승인 스타벅스 15,000원 12/25
  storeRegex: "승인\s+(.+?)\s+[\d,]+원"
  매칭: group1 = "스타벅스"
```

폴백 체인: storeRegex 실패 → `SmsParser.extractStoreName(body)` → fallbackStoreName

검증 규칙 (`isValidStoreCandidate`):
- 2~30자 범위
- 순수 숫자/날짜/시간/카드마스킹 패턴이면 무효
- "승인", "결제", "출금" 등 구조 키워드 포함 시 무효

#### cardRegex — 카드사 추출

카드사명 또는 카드 식별자를 캡처합니다.

```
KB 형식 (헤더에 포함):
  SMS: [Web발신]\n[KB]02/05 22:47\n...
  cardRegex: "\[([\w]+)\]"
  매칭: group1 = "KB"

신한 형식 (접두사):
  SMS: 신한카드 15,000원 승인 ...
  cardRegex: "^([\w가-힣]+카드)"
  매칭: group1 = "신한카드"

일반 형식 (카드번호 앞):
  SMS: 국민카드 1234*5678 15,000원 승인
  cardRegex: "^([\w가-힣]+카드)"
  매칭: group1 = "국민카드"
```

폴백 체인: cardRegex 실패 → `SmsParser.extractCardName(body)` (50+ 카드 키워드 매칭) → fallbackCardName → "기타"

#### dateRegex — 날짜/시간 추출 (신규)

현재 `SmsParser.extractDateTime()`이 처리하므로 선택적이지만, 카드사별 날짜 형식이 다를 때 유용합니다.

```
MM/DD HH:mm 형식:
  SMS: ...02/05 22:47...
  dateRegex: "(\d{2}/\d{2}\s+\d{2}:\d{2})"
  매칭: group1 = "02/05 22:47"

MM.DD HH:mm 형식:
  SMS: ...02.05 22:47...
  dateRegex: "(\d{2}\.\d{2}\s+\d{2}:\d{2})"
  매칭: group1 = "02.05 22:47"

YYYY-MM-DD 형식 (일부 카드사):
  SMS: ...2026-02-05 22:47...
  dateRegex: "(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2})"
  매칭: group1 = "2026-02-05 22:47"
```

폴백: dateRegex 미제공 시 → `SmsParser.extractDateTime(body, timestamp)` (기존 로직 그대로)

### 기존 코드와의 통합 지점

```
[앱 시작 또는 주기적 갱신]
  │
  ▼
Firebase RTDB sms_regex_rules/ 다운로드
  │
  ▼
RtdbRegexRule → SmsPatternEntity 변환
  ├── amountRegex, storeRegex, cardRegex: 그대로
  ├── dateRegex: SmsPatternEntity 필드 추가 필요 (또는 별도 캐시)
  ├── parseSource: "rtdb_regex"  (신규 parseSource)
  ├── smsTemplate: sampleTemplate (유사도 매칭용)
  ├── embedding: 앱에서 sampleTemplate 임베딩 생성
  └── isPayment: true (결제 규칙만 배포)

SMS 파이프라인 삽입 위치 (HybridSmsClassifier 기준):
  Step 1: 사전 필터링
  Step 2: SmsParser Regex (하드코딩)
  Step 2.5: ★ RTDB Regex 매칭 (NEW)
           ├ 발신번호 + 템플릿 유사도로 규칙 선택
           └ GeneratedSmsRegexParser.parseWithRegex() 호출
  Step 3: 배치 임베딩 생성
  Step 4: 벡터 DB 매칭
  Step 5: LLM
```

핵심: `GeneratedSmsRegexParser.parseWithRegex()`가 이미 amountRegex/storeRegex/cardRegex를 group1 캡처로 파싱하므로, RTDB에서 받은 regex를 그대로 전달하면 **파서 코드 변경 없이** 동작합니다.

### 구현 Phase

```
Phase 1 (현재): sms_samples 표본 수집 → 데이터 축적 중  ← 지금 여기
Phase 2: 수집된 표본에서 regex 품질 검증 (수동 또는 스크립트)
Phase 3: 검증된 regex를 sms_regex_rules 노드로 승격
Phase 4: 앱에 RTDB regex 다운로드 + 매칭 로직 추가
         └ 앱 코드 변경: RtdbRegexRuleRepository, Step 2.5 삽입
```

### 주의사항

- **카드사 × 형식 조합**: 같은 카드사라도 출금/승인/해외 등 형식이 다름 → `senderAddress + templateHash` 키로 구분
- **가게명 regex 특수성**: 다른 필드(금액/카드)는 패턴이 보편적이나, 가게명은 위치 기반 → 정규식 품질이 핵심
- **버전 관리**: `version` 필드로 regex 업데이트 추적, `minAppVersion`으로 앱 호환성 보장
- **로컬 패턴 우선**: 사용자가 직접 학습한 로컬 SmsPatternEntity가 RTDB regex보다 우선 (개인화 유지)

---

*마지막 업데이트: 2026-02-19*
