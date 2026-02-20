# SMS 파싱 시스템

> SMS/MMS/RCS 문자에서 지출·수입 정보를 자동 추출하는 2-tier 벡터+LLM 파이프라인

---

## 1. 시스템 개요

MoneyTalk은 **sms2 통합 파이프라인**으로 SMS에서 결제/수입 정보를 추출합니다.
기존 3-tier (Regex → Vector → LLM) 구조에서 **Tier 1 로컬 Regex를 제거**하고,
모든 SMS를 임베딩 경로(Vector → LLM)로 처리합니다.

```
SMS/MMS/RCS 수신 (SmsReaderV2)
  │
  ▼
List<SmsInput> (원본 보존)
  │
  ▼
┌─────────────────────────────────────────────────────────┐
│ SmsSyncCoordinator.process() ★ 유일한 외부 진입점        │
│                                                          │
│  Step 0: SmsPreFilter.filter()                           │
│    비결제 키워드/구조 필터 (인증번호, 광고, 배송 등)        │
│    20자 미만/100자 초과 제거, 결제 힌트 없으면 제거         │
│                                                          │
│  Step 1: SmsIncomeFilter.classifyAll()                   │
│    금융기관 키워드 + 금액 패턴 기반 분류                    │
│    → PAYMENT / INCOME / SKIP                              │
│                                                          │
│  Step 2~5: SmsPipeline.process(결제 후보)                 │
│    ┌──────────────────────────────────────────────────┐   │
│    │ Step 3: batchEmbed()                              │   │
│    │   SmsTemplateEngine.templateize() → 플레이스홀더    │   │
│    │   SmsTemplateEngine.batchEmbed() → 3072차원 벡터   │   │
│    │   100건씩 배치 × Semaphore(10) 병렬                │   │
│    │                                                    │   │
│    │ Step 4: SmsPatternMatcher.matchPatterns()          │   │
│    │   DB 패턴과 코사인 유사도 비교                       │   │
│    │   ≥0.97 비결제 → 제외                               │   │
│    │   ≥0.92 결제 → regex 파싱 → SmsParseResult          │   │
│    │   <0.92 미매칭 → Step 5                             │   │
│    │                                                    │   │
│    │ Step 5: SmsGroupClassifier.classifyUnmatched()     │   │
│    │   발신번호→벡터 2레벨 그룹핑                         │   │
│    │   그룹 대표 LLM → regex 생성 → 패턴 DB 등록          │   │
│    │   멤버 일괄 파싱 → SmsParseResult                    │   │
│    └──────────────────────────────────────────────────┘   │
│                                                          │
│  결과: SyncResult(expenses, incomes, stats)               │
└─────────────────────────────────────────────────────────┘
  │
  ▼
호출자(HomeViewModel.syncSmsV2)
  ├ expenses → ExpenseEntity → DB 저장
  └ incomes → SmsIncomeParser로 파싱 → IncomeEntity → DB 저장
```

### 2-tier 구조의 이점

| 단계 | 커버리지 | 비용 | 속도 |
|------|---------|------|------|
| Vector (기존 패턴 재사용) | ~90% (유사 패턴) | 임베딩 API 1회 | ~1초 |
| LLM (신규 패턴) | ~99% (비표준 포함) | Gemini API N회 | ~2초 |

Tier 1 Regex 제거 이유:
- 카드사별 형식이 너무 다양 → 정규식 유지보수 한계
- 벡터 패턴이 축적되면 Regex 수준의 속도로 처리 가능
- LLM이 생성한 regex를 패턴 DB에 캐시 → 재방문 시 regex 파싱

---

## 2. 파일 구조

```
core/sms2/                           ★ sms2 통합 파이프라인
├── SmsSyncCoordinator.kt            # 외부 진입점 (process → PreFilter → IncomeFilter → Pipeline)
├── SmsPipeline.kt                   # 오케스트레이터 (Step 2→3→4→5)
├── SmsPipelineModels.kt             # 데이터 모델 (SmsInput, EmbeddedSms, SmsParseResult, SyncResult 등)
├── SmsPreFilter.kt                  # Step 0: 비결제 키워드+구조 필터
├── SmsIncomeFilter.kt               # Step 1: 결제/수입/스킵 분류
├── SmsTemplateEngine.kt             # Step 3: 템플릿화 + Gemini Embedding API
├── SmsPatternMatcher.kt             # Step 4: 벡터 매칭 + 원격 룰 매칭 + regex 파싱 (자체 코사인 유사도)
├── SmsGroupClassifier.kt            # Step 5: 그룹핑 + LLM + regex 생성 + 패턴 등록 + RTDB 표본 수집
├── GeminiSmsExtractor.kt            # LLM 추출 (배치 추출 + regex 생성 + MainRegexContext)
├── SmsReaderV2.kt                   # SMS/MMS/RCS 통합 읽기 (ContentResolver → List<SmsInput>)
├── SmsIncomeParser.kt               # 수입 SMS 파싱 (금액/유형/출처/날짜 추출)
├── RemoteSmsRule.kt                 # 원격 SMS regex 룰 데이터 클래스 (RTDB → 로컬 매칭)
└── RemoteSmsRuleRepository.kt       # 원격 룰 리포지토리 (RTDB 로드 + 메모리 캐시 + TTL)

core/database/entity/
└── SmsPatternEntity.kt              # 벡터 DB 엔티티 (18 필드, isMainGroup 포함)

core/database/dao/
└── SmsPatternDao.kt                 # 패턴 DB DAO (getMainPatternBySender 포함)
```

**V1 레거시 (core/sms/)**: SmsProcessingService 실시간 수신 전용으로 유지.
sms2에서는 `SmsFilter.shouldSkipBySender()` (SmsReaderV2 발신자 필터)만 참조.

---

## 3. 메시지 읽기 — SmsReaderV2

### 파일 위치
[`core/sms2/SmsReaderV2.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsReaderV2.kt)

### 지원 메시지 유형

| 유형 | Content URI | 날짜 형식 | 비고 |
|------|-----------|----------|------|
| SMS | `content://sms/inbox` | 밀리초 | 표준 문자 |
| MMS | `content://mms/inbox` | **초** (×1000 변환) | 장문 문자 |
| RCS | `content://im/chat` | 밀리초 | 삼성 기기, JSON 본문 |

### 핵심 메소드

| 메소드 | 반환 | 용도 |
|--------|------|------|
| `readAllMessagesByDateRange(cr, start, end)` | `List<SmsInput>` | SMS+MMS+RCS 통합 읽기 |

V1의 `SmsReader`는 `SmsMessage`를 반환했지만, **SmsReaderV2는 `SmsInput`을 직접 반환**합니다.
중간 변환 단계가 없어 호출자(HomeViewModel)가 바로 SmsSyncCoordinator에 전달 가능.

### SMS ID 생성
`발신번호_수신시간_본문해시코드` → 중복 저장 방지

### 발신자 필터링 — SmsFilter (공유)

[`core/sms/SmsFilter.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsFilter.kt) — V1/sms2 공유

| 메소드 | 역할 |
|--------|------|
| `normalizeAddress(rawAddress)` | `+82`, 하이픈, 공백 제거 → 순수 숫자열 |
| `shouldSkipBySender(address, body)` | 010/070 + 금융 힌트 없음 → 스킵 |
| `hasFinancialHints(body)` | 금액 패턴(`3자리+원`) 또는 (3자리 숫자 + 금융 키워드) |

### RCS 메시지 본문 파싱

RCS 메시지의 body는 JSON 형식이며, 위젯 트리 구조로 텍스트가 포함됩니다.

**`extractRcsText()` 추출 순서:**
1. 직접 텍스트 필드 검색: `text`, `body`, `message`, `msg`, `content`
2. `layout` 필드의 위젯 트리를 재귀 탐색 (`extractTextsFromLayout`)
3. `widget == "TextView"` 인 노드에서 `text` 값 수집
4. 줄바꿈(`\n`)으로 조합하여 일반 텍스트 반환

---

## 4. 외부 진입점 — SmsSyncCoordinator

### 파일 위치
[`core/sms2/SmsSyncCoordinator.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsSyncCoordinator.kt)

### 역할
**sms2 패키지의 유일한 외부 진입점.** 하위 컴포넌트(SmsPipeline, SmsPreFilter 등)를 직접 호출하지 않습니다.

### process() 처리 순서

```
process(smsList: List<SmsInput>, onProgress) → SyncResult
  │
  ├── Step 0: SmsPreFilter.filter(smsList)
  │   └ 비결제 SMS 사전 제거 (광고, 인증번호 등)
  │
  ├── Step 1: SmsIncomeFilter.classifyAll(filtered)
  │   └ (paymentCandidates, incomeCandidates, skipped)
  │
  └── Step 2~5: SmsPipeline.process(paymentCandidates, skipPreFilter=true)
      └ → List<SmsParseResult>
```

### 책임 분리

| SmsSyncCoordinator가 하는 것 | 호출자(HomeViewModel)가 하는 것 |
|-----------------------------|-------------------------------|
| 사전 필터링 | SMS 읽기 (ContentResolver) |
| 수입/결제 분류 | 중복 제거 (기존 SMS ID) |
| 결제 파이프라인 실행 | DB 저장 (Expense/Income) |
| SyncResult 반환 | 카테고리 분류, 카드 등록 |

### 사용자 제외 키워드

```kotlin
coordinator.setUserExcludeKeywords(keywords)  // 동기화 전 1회 호출
```
→ SmsIncomeFilter에 위임

---

## 5. 사전 필터링 — SmsPreFilter

### 파일 위치
[`core/sms2/SmsPreFilter.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsPreFilter.kt)

### 필터링 기준

**1. 키워드 필터 — `isObviouslyNonPayment(body)`**

SMS 본문에 아래 키워드 중 하나라도 포함되면 비결제:

| 카테고리 | 키워드 예시 |
|---------|-----------|
| 인증/보안 | `인증번호`, `OTP`, `비밀번호`, `authentication` |
| 해외발신 | `국외발신`, `국제발신`, `해외발신` |
| 광고/마케팅 | `광고`, `수신거부`, `이벤트`, `프로모션`, `특가` |
| 청구/안내 | `결제내역`, `명세서`, `청구서`, `결제예정`, `카드대금` |
| 배송 | `배송`, `택배`, `운송장` |
| 금융광고 | `대출`, `투자`, `분양`, `모델하우스` |

**2. 구조 필터 — `lacksPaymentRequirements(body)`**

| 조건 | 판정 |
|------|------|
| 20자 미만 / 100자 초과 | 비결제 |
| 숫자 없음 | 비결제 |
| 2자리+ 연속 숫자 없음 | 비결제 |
| HTTP 링크 + 결제/승인 없음 | 비결제 |
| 결제 힌트 키워드도 없고 `숫자+원` 패턴도 없음 | 비결제 |

결제 힌트 키워드: `승인`, `결제`, `출금`, `이체`, `원`, `USD`, `JPY`, `EUR`, `카드`, `체크`, `CMS`

---

## 6. 수입/결제 분류 — SmsIncomeFilter

### 파일 위치
[`core/sms2/SmsIncomeFilter.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsIncomeFilter.kt)

### 분류 로직 — `classify(body) → SmsType`

```
1. 빈 SMS / 100자 초과 → SKIP
2. 제외 키워드 (광고, 안내) → SKIP
3. 사용자 제외 키워드 → SKIP
4. 금융기관 키워드 없음 → SKIP
5. 금액 패턴 없음 → SKIP
6. 취소 키워드 (출금취소, 승인취소 등) → INCOME
7. 결제 키워드 (결제, 승인, 사용, 출금) → PAYMENT
8. 수입 제외 키워드 (자동이체출금, 보험료 등) → SKIP
9. 수입 키워드 (입금, 급여, 송금 등) → INCOME
10. 그 외 (금융+금액 있지만 명시적 키워드 없음) → PAYMENT (벡터/LLM에 맡김)
```

### 금융기관 키워드 (46개)

KB, 국민, 신한, 삼성, 현대, 롯데, 하나, 우리, NH, 농협, BC, 카카오, 토스, 케이뱅크, IBK, SC제일, 수협, 광주은행, 전북은행, 경남은행, 부산은행, 대구은행, 새마을, 신협, 우체국, 저축은행 등

### 배치 분류 — `classifyAll(smsList)`

```kotlin
fun classifyAll(smsList: List<SmsInput>): Triple<List<SmsInput>, List<SmsInput>, List<SmsInput>>
// → (결제 후보, 수입 후보, 스킵)
```

---

## 7. 오케스트레이터 — SmsPipeline

### 파일 위치
[`core/sms2/SmsPipeline.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsPipeline.kt)

### process() 처리 순서

| Step | 담당 클래스 | 입력 | 출력 |
|------|-----------|------|------|
| 2 | SmsPreFilter | List<SmsInput> | List<SmsInput> (필터 통과분) |
| 3 | SmsTemplateEngine | List<SmsInput> | List<EmbeddedSms> |
| 4 | SmsPatternMatcher | List<EmbeddedSms> | (matched, unmatched) |
| 5 | SmsGroupClassifier | List<EmbeddedSms> | List<SmsParseResult> |

SmsSyncCoordinator 경유 시 Step 2는 `skipPreFilter=true`로 스킵.

### 설정 상수

| 상수 | 값 | 설명 |
|------|---|------|
| `EMBEDDING_BATCH_SIZE` | 100 | batchEmbedContents API 최대 개수 |
| `EMBEDDING_CONCURRENCY` | 10 | 임베딩 배치 병렬 동시 실행 수 |

---

## 8. 템플릿화 + 임베딩 — SmsTemplateEngine

### 파일 위치
[`core/sms2/SmsTemplateEngine.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsTemplateEngine.kt)

### 템플릿화 — `templateize(smsBody)`

변하는 부분을 플레이스홀더로 치환하여 **구조적 유사성**을 극대화합니다.

**치환 순서 (순서 중요):**

| 순서 | 원본 패턴 | 플레이스홀더 | 예시 |
|------|----------|-------------|------|
| 1 | 한 줄 SMS | 가상 줄바꿈 삽입 | `a / b / c` → `a\nb\nc` |
| 2 | `[가-힣]\*[가-힣]` (사용자 이름) | `{USER_NAME}` | 하*현 → {USER_NAME} |
| 3 | `\d+\*+\d+` (카드번호) | `{CARD_NUM}` | 801302**775 → {CARD_NUM} |
| 4 | 가게명 줄 (4줄+ SMS) | `{STORE}` | 스타벅스 → {STORE} |
| 5 | `[\d,]+원` | `{AMOUNT}원` | 15,000원 → {AMOUNT}원 |
| 6 | `\n[\d,]{3,}\n` | `\n{AMOUNT}\n` | \n11,940\n → \n{AMOUNT}\n |
| 7 | `\d{1,2}[/.-]\d{1,2}` | `{DATE}` | 02/05 → {DATE} |
| 8 | `\d{1,2}:\d{2}` | `{TIME}` | 22:47 → {TIME} |
| 9 | `잔액[\d,]+` | `잔액{BALANCE}` | 잔액45,091 → 잔액{BALANCE} |

**핵심 변경: 가게명 추출을 금액/날짜 치환보다 앞에서 수행.**
이유: 금액 치환 후 `{AMOUNT}원캐쉬백다이소`에서 `{`가 포함되어 가게명 판별 실패 방지.

### 가게명 판별 — `isLikelyStoreName(line)`

- 빈 줄, 2자 미만, 20자 초과 → false
- `{` 포함 (플레이스홀더) → false
- 구조 키워드 포함 (출금, 입금, 승인, 잔액 등) → false
- 숫자/콤마로만 구성 → false
- 한글/영문/`(`/`*`로 시작 → **true** (최대 1줄만)

### 한 줄 SMS 전처리

일부 카드사(신한 등)는 줄바꿈 없이 한 줄로 SMS를 보냄.
구분자(` / `)를 줄바꿈으로 변환하여 다중 줄 SMS와 동일하게 처리.

### 임베딩 생성 — `batchEmbed(templates)`

Gemini Embedding API (모델명은 Firebase RTDB에서 원격 관리, 기본 `gemini-embedding-001`, 3072차원).

| 메소드 | API | 용도 |
|--------|-----|------|
| `batchEmbed(templates)` | `batchEmbedContents` | 배치 임베딩 (최대 100건) |

### Rate Limit 처리 (429)
- `MAX_RETRIES = 3` (최대 3회 재시도)
- 지수 백오프: 2s → 4s
- Quota 초과 (`exceeded your current quota`) → 재시도 불가, 즉시 실패

---

## 9. 벡터 매칭 + Regex 파싱 — SmsPatternMatcher

### 파일 위치
[`core/sms2/SmsPatternMatcher.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsPatternMatcher.kt)

### 코사인 유사도 (자체 구현)

```
코사인 유사도 = (A·B) / (|A| × |B|)
범위: -1 ~ 1 (1에 가까울수록 유사)
```

V1의 `VectorSearchEngine`을 사용하지 않고, **코사인 유사도를 자체 구현**합니다.
RandomAccess 체크로 ArrayList boxing/unboxing 오버헤드 제거.

### 유사도 임계값

| 상수 | 값 | 용도 |
|------|---|------|
| `NON_PAYMENT_THRESHOLD` | **0.97** | 비결제 패턴 캐시 히트 → 즉시 제외 |
| `PAYMENT_MATCH_THRESHOLD` | **0.92** | 결제 패턴 매칭 → regex 파싱 시도 |

### matchPatterns() 처리 흐름

```
matchPatterns(embeddedSmsList)
  │
  ├── DB 패턴 로드 (비결제 + 결제, 각 1회 쿼리)
  ├── 원격 룰 로드 (RemoteSmsRuleRepository.loadRules(), 캐시 재사용)
  │
  ├── 각 SMS에 대해:
  │   ├─ [1] 비결제 패턴 우선 확인 (≥0.97) → 제외 (matched에도 unmatched에도 안 넣음)
  │   ├─ [2] 결제 패턴 매칭 (≥0.92) → parseWithPatternRegex()
  │   │   ├ 파싱 성공 → SmsParseResult(tier=2) → matched
  │   │   └ 파싱 실패 → unmatched (Step 5로)
  │   ├─ [3] 원격 룰 매칭 ★ 신규 2순위
  │   │   ├ 동일 발신번호 룰 필터 → 코사인 유사도 ≥ rule.minSimilarity(기본 0.94)
  │   │   ├ regex 파싱 성공 → SmsParseResult(tier=2) → matched
  │   │   ├ 로컬 패턴 DB에 승격(promoteToLocalPattern, parseSource="remote_rule")
  │   │   └ 파싱 실패 → unmatched
  │   └─ [4] 미매칭 (<0.92, 원격 룰도 없음) → unmatched
  │
  └── 반환: (matched, unmatched)
```

### Regex 파싱 체인 — `parseWithPatternRegex()`

```
패턴에 amountRegex + storeRegex가 있으면:
  → parseWithRegex(body, timestamp, amountRegex, storeRegex, cardRegex, fallbacks)
    ├ 금액: amountRegex group1 → 실패 시 fallbackAmount
    ├ 가게명: storeRegex group1 → sanitize → validate → 실패 시 fallbackStoreName
    ├ 카드: cardRegex group1 → validate → 실패 시 fallbackCardName
    ├ 날짜: extractDateTime(body, timestamp)
    └ 카테고리: fallbackCategory

regex 없거나 파싱 실패:
  → 패턴 캐시값(parsedAmount, parsedStoreName 등)으로 직접 구성

모든 폴백 실패 → null (미매칭 처리)
```

### 가게명/카드명 검증

| 검증 | 무효 조건 |
|------|----------|
| 가게명 | 2자 미만/30자 초과, 숫자만, 날짜/시간 형태, 구조 키워드(승인/결제 등) |
| 카드명 | 2자 미만/20자 초과, 숫자만, 발신 관련 키워드(web발신 등) |

### Regex 캐시
`ConcurrentHashMap<String, Regex>` — 같은 정규식 문자열의 재컴파일 방지

### 원격 룰 매칭 — RemoteSmsRule + RemoteSmsRuleRepository ★

로컬 DB 패턴 미매칭 시 **2순위**로 RTDB 원격 룰을 사용하여 매칭합니다.

#### 전체 파이프라인

```
[Step 5 LLM 처리]
  → collectSampleToRtdb() → /sms_samples/{sender}_{hash}/
    (PII 마스킹된 SMS + 임베딩 + regex + 카드명 + 발신번호)

[관리자 수동 처리] ← 아직 미자동화
  → sms_samples에서 임베딩 유사도 98%+ 그룹핑
  → 검증된 regex를 /sms_regex_rules/v1/{sender}/{ruleId}/ 에 배포

[다음 동기화 시 Step 4]
  → RemoteSmsRuleRepository.loadRules() — RTDB에서 전체 룰 로드
  → matchWithRemoteRules() — 발신번호 필터 + 유사도 ≥ 0.94 + regex 파싱
  → promoteToLocalPattern() — 로컬 DB에 parseSource="remote_rule"로 승격
  → 이후 동기화에서는 로컬 패턴으로 매칭 (RTDB 호출 불필요)
```

#### RemoteSmsRule 데이터 클래스

```kotlin
data class RemoteSmsRule(
    val ruleId: String,                    // RTDB 룰 고유 ID
    val normalizedSenderAddress: String,   // 정규화된 발신번호
    val embedding: List<Float>,            // 3072차원 임베딩 벡터
    val amountRegex: String,               // 금액 추출 정규식
    val storeRegex: String,                // 가게명 추출 정규식
    val cardRegex: String = "",            // 카드명 추출 정규식 (선택)
    val minSimilarity: Float = 0.94f,      // 최소 유사도 임계값
    val enabled: Boolean = true,           // 활성화 여부
    val updatedAt: Long = 0L               // 마지막 업데이트 시각
)
```

#### RemoteSmsRuleRepository

| 속성 | 값 | 설명 |
|------|---|------|
| RTDB 경로 | `sms_regex_rules/v1` | sender별 룰 그룹핑 |
| 캐시 TTL | 10분 | 동기화 중 반복 네트워크 호출 방지 |
| 안정성 | RTDB 실패 시 빈 맵 반환 | 예외 전파 금지, main thread 블로킹 없음 |
| 필터링 | `enabled=true` 룰만 로드 | 비활성 룰 자동 제외 |

#### promoteToLocalPattern() — 원격 룰 로컬 승격

원격 룰 매칭 성공 시 로컬 `SmsPatternEntity`로 승격하여 다음 동기화부터 RTDB 없이 매칭:

```kotlin
parseSource = "remote_rule"
confidence = 1.0f
isMainGroup = false
```

---

## 10. 그룹 분류 + LLM — SmsGroupClassifier

### 파일 위치
[`core/sms2/SmsGroupClassifier.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsGroupClassifier.kt)

### 설정 상수

| 상수 | 값 | 설명 |
|------|---|------|
| `LLM_BATCH_SIZE` | 20 | 한 번에 LLM에 보내는 그룹 수 |
| `LLM_CONCURRENCY` | 5 | LLM 병렬 동시 실행 수 |
| `REGEX_SAMPLE_SIZE` | 5 | regex 생성 시 사용할 샘플 수 |
| `REGEX_MIN_SAMPLES` | 3 | regex 생성 최소 멤버 수 |
| `GROUPING_SIMILARITY` | 0.95 | 벡터 클러스터링 임계값 |
| `SMALL_GROUP_MERGE_THRESHOLD` | 5 | 소그룹 병합 기준 멤버 수 |
| `SMALL_GROUP_MERGE_MIN_SIMILARITY` | 0.70 | 소그룹 병합 최소 유사도 |
| `REGEX_FAILURE_THRESHOLD` | 2 | regex 실패 쿨다운 기준 |
| `REGEX_FAILURE_COOLDOWN_MS` | 30분 | regex 실패 쿨다운 시간 |
| `RTDB_DEDUP_SIMILARITY` | 0.99 | RTDB 표본 중복 판정 |

### classifyUnmatched() 전체 흐름

```
classifyUnmatched(unmatchedList)
  │
  ├── [5-1] 그룹핑: groupByAddressThenSimilarity()
  │   ├ Level 1: 발신번호(address) 기준 분류 (O(n), API 없음)
  │   │   └ +82/하이픈 등 normalizeAddress() 정규화
  │   ├ Level 2: 같은 발신번호 내 벡터 유사도 ≥ 0.95 그리디 클러스터링
  │   │   └ groupBySimilarityInternal() — 50건마다 yield()
  │   └ Level 3: 소그룹 병합 — mergeSmallGroups()
  │       └ 멤버 ≤ 5 + 유사도 ≥ 0.70 → 최대 그룹에 흡수
  │
  ├── [5-1.5] 발신번호 단위 재집계 → SourceGroup
  │   └ 서브그룹을 발신번호별로 묶어서 메인/예외 분리
  │
  ├── [5-1.7] DB 메인 패턴 선조회 ★ 신규
  │   └ 각 발신번호별 smsPatternDao.getMainPatternBySender(address)
  │   └ 이전 동기화에서 isMainGroup=true로 등록된 패턴의 regex 참조
  │   └ dbMainPatterns Map에 캐시 → processSourceGroup에 전달
  │
  ├── [5-2] 발신번호 단위 처리: processSourceGroup(sourceGroup, dbMainPattern)
  │   ├ DB 메인 패턴 → dbMainContext (MainCaseContext) 구성
  │   ├ 서브그룹 1개: DB 메인 있으면 regex 참조로 전달
  │   ├ 서브그룹 2개+:
  │   │   ├ 메인 그룹(최대 서브그룹) 먼저 LLM 호출 (isMainGroup=true)
  │   │   ├ 메인 결과 → MainCaseContext (현재 세션 우선, 없으면 DB fallback)
  │   │   └ 예외 그룹들에 메인 컨텍스트 + 분포도 전달하여 LLM 호출
  │
  ├── [5-3] 단일 그룹 처리: processGroup(group, mainContext, isMainGroup)
  │   ├ LLM 배치 추출 (smsExtractor.extractFromSmsBatch)
  │   ├ 비결제 → registerNonPaymentPattern() → 종료
  │   ├ 결제 → regex 생성 시도:
  │   │   ├ mainContext에 regex 있으면 → MainRegexContext 구성 → 참조 전달
  │   │   ├ 멤버 ≥ 3 + 쿨다운 아님 → smsExtractor.generateRegexForGroup(mainRegexContext)
  │   │   │   ├ 성공 → parseSource = "llm_regex"
  │   │   │   └ 실패 → buildTemplateFallbackRegex()
  │   │   │       ├ 성공 → parseSource = "template_regex"
  │   │   │       └ 실패 → parseSource = "llm"
  │   │   └ 멤버 < 3 → 템플릿 폴백 시도
  │   ├ registerPaymentPattern(isMainGroup=true/false) → DB 등록
  │   └ collectSampleToRtdb() → RTDB 표본 수집
  │
  ├── [5-4] 그룹 전체 멤버 파싱
  │   ├ regex 있으면 → patternMatcher.parseWithRegex(member.body)
  │   │   └ 실패 멤버 → 개별 LLM 호출 (폴백)
  │   └ regex 없으면 → 멤버별 개별 LLM 호출 (대표 결과 복제 방지)
  │
  └── [5-5] GroupProcessResult 반환
      └ results + amountRegex/storeRegex/cardRegex → 호출자가 regex 참조 가능
```

### 발신번호 생태계 분석 (SourceGroup)

같은 발신번호의 모든 서브그룹을 **SourceGroup**으로 묶어서 처리:

```
SourceGroup (발신번호: 15881688, 총 85건)
├── mainGroup: 80건(94%) — [KB] 일반 승인
├── exceptionGroup1: 3건(4%) — [KB] 해외승인
└── exceptionGroup2: 2건(2%) — [KB] ATM출금
```

메인 케이스 먼저 처리 → 예외 케이스 LLM에 `[참조 정보]`로 메인 컨텍스트 전달.
LLM이 전체 그림을 보고 판단 → 오파싱/오분류 감소.

### DB 메인 패턴 선조회 (isMainGroup 시스템) ★

동기화 간 메인 그룹 regex를 재사용하여 LLM 정확도를 높이는 시스템.

**문제**: 같은 세션 내에서만 메인 그룹 regex가 예외 그룹에 전달됨.
다음 동기화에서는 메인 그룹이 Step 4에서 이미 매칭되어 Step 5에 도달하지 않으므로,
예외 그룹만 Step 5에 도달했을 때 메인 regex 참조가 없음.

**해결**: `SmsPatternEntity.isMainGroup` 필드로 메인 그룹 패턴을 DB에 표시.
Step 5 진입 시 `getMainPatternBySender(address)`로 DB에서 선조회.

```
[첫 동기화]
  Step 5 → 메인 그룹 LLM → isMainGroup=true로 DB 등록
  Step 5 → 예외 그룹 → 현재 세션 메인 regex 참조

[다음 동기화]
  Step 4 → 메인 그룹 SMS: DB 패턴 매칭 (≥0.92) → regex 파싱 성공 (Step 5 안 감)
  Step 5 → 예외 그룹 SMS만 도달
    → [5-1.7] DB에서 isMainGroup=true 패턴 조회
    → dbMainContext 구성 (cardName, template, regex 3종)
    → 예외 그룹 LLM regex 생성 시 MainRegexContext로 전달
```

**우선순위**: 현재 세션 메인 결과 > DB 메인 패턴 (DB는 fallback)

**senderAddress 정규화**: `registerPaymentPattern()`/`registerNonPaymentPattern()`에서
`normalizeAddress()`로 정규화된 주소를 저장하여, 조회 시 정확한 매칭 보장.

### MainRegexContext (메인 regex 참조 전달)

예외 그룹의 regex 생성 시 메인 그룹의 regex를 참조로 전달:

```kotlin
data class MainRegexContext(
    val amountRegex: String,  // 메인 그룹의 금액 정규식
    val storeRegex: String,   // 메인 그룹의 가게명 정규식
    val cardRegex: String,    // 메인 그룹의 카드명 정규식
    val sampleBody: String    // 메인 그룹의 SMS 샘플
)
```

LLM 프롬프트에 "같은 발신번호의 메인 형식은 이 regex를 사용" 정보를 포함하여,
변형 SMS에 대해서도 일관된 regex를 생성할 수 있도록 유도.

### GroupProcessResult (regex 반환)

`processGroup()`이 파싱 결과와 함께 생성된 regex를 반환하여
`processSourceGroup()`에서 메인 → 예외 regex 전달 체인을 구성:

```kotlin
data class GroupProcessResult(
    val results: List<SmsParseResult>,
    val amountRegex: String,   // 예외 그룹에 전달할 금액 regex
    val storeRegex: String,    // 예외 그룹에 전달할 가게명 regex
    val cardRegex: String      // 예외 그룹에 전달할 카드명 regex
)
```

### 템플릿 폴백 Regex

LLM regex 생성 실패 시, 템플릿의 `{STORE}`/`{AMOUNT}` 플레이스홀더를 기반으로 최소 regex:

```
amountRegex: "([\d,]{2,})원"   또는   "\n([\d,]{2,})\n"
storeRegex:  "\n([^\n]{2,30})\n(?:체크카드출금|출금|승인|결제)"
cardRegex:   "\[([^\]]+)\]"
```

### Regex 실패 쿨다운

같은 템플릿에 대해 regex 생성이 2회 이상 실패하면 30분 동안 재시도 스킵.

---

## 11. 수입 SMS 파싱 — SmsIncomeParser

### 파일 위치
[`core/sms2/SmsIncomeParser.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsIncomeParser.kt)

### 역할
SmsIncomeFilter가 INCOME으로 분류한 SMS에서 금액/유형/출처/날짜를 추출.
Object singleton으로 구현 (DI 불필요).

### 추출 메소드

| 메소드 | 추출 항목 | 추출 방법 |
|--------|----------|----------|
| `extractIncomeAmount(body)` | 입금 금액 (Int) | `숫자+원` 패턴, KB 스타일 줄바꿈 |
| `extractIncomeType(body)` | 입금 유형 (String) | 키워드 매칭 (급여/이체/환급/송금 등) |
| `extractIncomeSource(body)` | 송금인/출처 (String) | 3가지 패턴 순차 시도 |
| `extractDateTime(body, ts)` | 날짜/시간 (String) | MM/DD, M월 D일, HH:mm 패턴 |

### extractIncomeSource 패턴 (순서)

1. **KB 스타일 멀티라인**: `입금` 줄 위에서 출처 탐색 (카드번호/날짜/대괄호 제외)
2. **`OOO님으로부터`** 또는 **`OOO으로부터`** 패턴
3. **`입금 OOO`** 또는 **`OOO 입금`** 패턴 (같은 줄 내에서만)

---

## 12. LLM 추출 — GeminiSmsExtractor

### 파일 위치
[`core/sms2/GeminiSmsExtractor.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/GeminiSmsExtractor.kt) — **sms2 패키지에 위치**

### sms2에서의 사용
`SmsGroupClassifier`가 Step 5에서 LLM 호출 시 사용:
- `extractFromSmsBatch(bodies, timestamps)` → 결제 여부 + 정보 추출
- `generateRegexForGroup(bodies, timestamps, mainRegexContext?)` → regex 생성 + 메인 regex 참조

### 3종 모델 (Firebase RTDB 원격 관리)

| 용도 | Config 필드 | temperature | maxTokens |
|------|-------------|-------------|-----------|
| 단건 추출 | `smsExtractor` | 0.1 | 1024 |
| 배치 추출 | `smsBatchExtractor` | 0.1 | 4096 |
| regex 생성 | `smsRegexExtractor` | 0.0 | 8192 |

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

### 카테고리 정규화
LLM이 반환한 비표준 카테고리를 앱 17개 카테고리로 매핑:
`온라인쇼핑` → `쇼핑`, `편의점` → `식비`, `이체` → `계좌이체` 등

---

## 13. 동기화 흐름 — HomeViewModel.syncSmsV2

### 파일 위치
[`feature/home/ui/HomeViewModel.kt`](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeViewModel.kt)

### syncSmsV2 오케스트레이터 (5 Phase)

```
syncSmsV2(contentResolver, targetMonthRange, updateLastSyncTime)
  │
  ├── readAndFilterSms()
  │   ├ SmsReaderV2.readAllMessagesByDateRange(start, end) → List<SmsInput>
  │   └ 기존 SMS ID로 중복 제거 (expenseRepository + incomeRepository)
  │
  ├── processSmsPipeline()
  │   ├ categoryClassifierService.initCategoryCache()
  │   └ smsSyncCoordinator.process(smsInputs, onProgress) → SyncResult
  │
  ├── saveExpenses()
  │   ├ SmsParseResult → ExpenseEntity 변환 (카테고리 fallback 포함)
  │   └ DB_BATCH_INSERT_SIZE 단위 배치 삽입
  │
  ├── saveIncomes()
  │   ├ SmsIncomeParser.extractIncomeAmount/Type/Source/DateTime()
  │   └ IncomeEntity 변환 + 배치 삽입
  │
  └── postSyncCleanup()
      ├ categoryClassifierService.flushPendingMappings() + clearCategoryCache()
      ├ hybridSmsClassifier.cleanupStalePatterns()
      ├ updateLastSyncTime이면 settingsDataStore.saveLastSyncTime()
      └ Gemini 카테고리 분류 (미분류 항목)
```

### 호출 경로

| 호출부 | 메소드 | 설명 |
|--------|--------|------|
| HomeScreen (버튼) | `syncIncremental(cr)` | 증분 동기화 (lastSyncTime~now) |
| HomeScreen (자동) | `syncIncremental(cr)` | 자동 증분 동기화 |
| HomeViewModel (월별) | `syncSmsV2(cr, monthRange, false)` | 광고 시청 후 월별 동기화 |

`syncIncremental()`은 `calculateIncrementalRange()`로 시작~종료 시간을 계산한 뒤 `syncSmsV2()`를 호출합니다.

### calculateIncrementalRange() 범위 결정

| 조건 | 시작 시간 |
|------|----------|
| 첫 동기화 + 미해제 | 60일 전 (DEFAULT_SYNC_PERIOD_MILLIS) |
| 첫 동기화 + 해제 | 전체 (0L) |
| 증분 | lastSyncTime |
| DB 비어있는데 lastSyncTime > 0 | 리셋 후 60일 전 (Auto Backup 감지) |

---

## 14. 데이터 모델

### SmsInput — 파이프라인 입력

```kotlin
data class SmsInput(
    val id: String,       // SMS 고유 ID (중복 체크용)
    val body: String,     // ★ 원본 SMS 본문 (끝까지 보존)
    val address: String,  // 발신번호 (그룹핑 1차 키)
    val date: Long        // 수신 시간 (ms)
)
```

### EmbeddedSms — 임베딩 완료

```kotlin
data class EmbeddedSms(
    val input: SmsInput,         // 원본 포함
    val template: String,        // 플레이스홀더 템플릿
    val embedding: List<Float>   // 3072차원 벡터
)
```

### SmsParseResult — 파이프라인 출력

```kotlin
data class SmsParseResult(
    val input: SmsInput,                 // 원본 SMS
    val analysis: SmsAnalysisResult,     // 파싱 결과 (금액/가게명/카드명/카테고리/날짜)
    val tier: Int,                       // 2=벡터매칭, 3=LLM
    val confidence: Float                // 유사도(tier2) 또는 1.0(tier3)
)
```

### SyncResult — SmsSyncCoordinator 출력

```kotlin
data class SyncResult(
    val expenses: List<SmsParseResult>,  // 결제 확인 + 파싱 성공
    val incomes: List<SmsInput>,         // 수입 분류 (파싱 전)
    val stats: SyncStats                 // 처리 통계
)
```

### SmsType — 분류 결과

```kotlin
enum class SmsType { PAYMENT, INCOME, SKIP }
```

---

## 15. 데이터베이스 스키마

### sms_patterns 테이블 (SmsPatternEntity)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동 증가 ID |
| smsTemplate | String | 템플릿화된 SMS 본문 ({AMOUNT}, {DATE}, {TIME}, {STORE}, {BALANCE}, {CARD_NUM}) |
| senderAddress | String | 발신 번호 **(normalizeAddress로 정규화)** |
| embedding | List<Float> → JSON | 임베딩 벡터 (3072차원) |
| isPayment | Boolean | 결제 여부 (true: 결제, false: 비결제) |
| parsedAmount | Int | 캐시된 결제 금액 |
| parsedStoreName | String | 캐시된 가게명 |
| parsedCardName | String | 캐시된 카드사명 |
| parsedCategory | String | 캐시된 카테고리 |
| amountRegex | String | LLM 생성 금액 추출 정규식 (group1 캡처) |
| storeRegex | String | LLM 생성 가게명 추출 정규식 (group1 캡처) |
| cardRegex | String | LLM 생성 카드사 추출 정규식 (group1 캡처) |
| parseSource | String | 파싱 소스 |
| confidence | Float | 신뢰도 (0.0~1.0) |
| **isMainGroup** | **Boolean** | **해당 발신번호의 메인 그룹 패턴 여부 (v2→v3 추가)** |
| matchCount | Int | 매칭 횟수 |
| createdAt | Long | 생성 시간 (밀리초) |
| lastMatchedAt | Long | 마지막 매칭 시간 (밀리초) |

### getMainPatternBySender() 쿼리

```sql
SELECT * FROM sms_patterns
WHERE senderAddress = :address AND isMainGroup = 1 AND isPayment = 1
AND amountRegex != '' AND storeRegex != ''
ORDER BY matchCount DESC LIMIT 1
```

같은 발신번호의 메인 그룹 패턴 중 regex가 있고 가장 활발한(matchCount 최대) 1개를 반환.

### parseSource 값

| 값 | 의미 | confidence | 발생 위치 |
|----|------|-----------|----------|
| `llm` | LLM 추출 (regex 생성 실패) | 0.8 | SmsGroupClassifier.registerPaymentPattern() |
| `llm_regex` | LLM + regex 생성 성공 | 1.0 | SmsGroupClassifier.registerPaymentPattern() |
| `template_regex` | LLM 실패 + 템플릿 폴백 regex | 0.85 | SmsGroupClassifier.registerPaymentPattern() |
| `remote_rule` | RTDB 원격 룰 매칭 → 로컬 승격 | 1.0 | SmsPatternMatcher.promoteToLocalPattern() |

---

## 16. 성능 최적화

### 비용 최적화

| 최적화 | 효과 |
|--------|------|
| SmsPreFilter (키워드 + 구조) | 비결제 SMS를 임베딩/LLM 전에 조기 제거 |
| SmsIncomeFilter | 수입 SMS를 파이프라인 진입 전 분리 |
| 발신번호 기반 1차 그룹핑 | N그룹 → 소수 그룹으로 축소 |
| 벡터 캐시 (패턴 재사용) | 동일 형식 SMS는 LLM 없이 regex 파싱 |
| 비결제 패턴 캐싱 | LLM 비결제 판정도 벡터 DB에 등록 (≥0.97 히트) |
| 소그룹 병합 | 같은 카드사 변형 그룹 흡수 → LLM 호출 감소 |
| regex 실패 쿨다운 | 동일 템플릿 regex 반복 실패 시 30분 쿨다운 |

### 속도 최적화

| 최적화 | 효과 |
|--------|------|
| 임베딩 병렬화 (Semaphore 10) | 100건 배치 × N개 동시 실행 |
| LLM 병렬화 (Semaphore 5) | 배치 간 병렬 실행 |
| Regex 캐시 (ConcurrentHashMap) | 동일 정규식 재컴파일 방지 |
| 코사인 유사도 최적화 | FloatArray RandomAccess 체크 |
| NON_PAYMENT_KEYWORDS 사전 lowercase | filter() 호출 시 매번 변환 방지 |

---

## 17. Firebase RTDB SMS 표본 수집

### 목적
카드사별 SMS 형식 표본을 수집하여 향후 정규식 주입에 활용.

### 수집 시점
`SmsGroupClassifier.registerPaymentPattern()` → `collectSampleToRtdb()` 호출.
모든 parseSource (llm, llm_regex, template_regex)에서 수집. 단, regex는 `llm_regex`만 포함.

### RTDB 데이터 구조

```
sms_samples/
  └── {normalizedSenderAddress}_{templateHashCode}/
      ├── maskedBody: String                # PII 마스킹된 SMS 본문 (regex 작성/검증용)
      ├── cardName: String                  # 카드사명 (발신번호 내 카드 식별)
      ├── senderAddress: String             # 원본 발신번호 (표본 추적용)
      ├── normalizedSenderAddress: String   # 정규화된 발신번호 (룰 그룹핑 키)
      ├── parseSource: String               # 파싱 소스 (llm_regex만 regex 신뢰 가능)
      ├── embedding: List<Float>            # 3072차원 임베딩 (코사인 유사도 매칭 핵심)
      ├── groupMemberCount: Int             # 관측 SMS 수 (신뢰도 판단)
      ├── amountRegex: String?              # 검증된 금액 regex (llm_regex인 경우)
      ├── storeRegex: String?               # 검증된 가게명 regex
      └── cardRegex: String?                # 검증된 카드명 regex
```

### 중복 방지

```
sentSampleEmbeddings: MutableList<List<Float>>

새 SMS 임베딩 vs 기존 전송 임베딩 → 코사인 유사도 ≥ 0.99이면 스킵
```

### PII 마스킹 — `maskSmsBody(smsBody)`

| 순서 | 대상 | 결과 |
|------|------|------|
| 1 | 가게명 (4줄+ SMS) | `***` (길이 제한 10) |
| 2 | 카드번호 (`\d+\*+\d+`) | `****` |
| 3 | 날짜 (`MM/DD`) | `**/**` |
| 4 | 시간 (`HH:mm`) | `**:**` |
| 5 | 금액 (`1,234`) | `*,***` |
| 6 | 남은 숫자 | `***` |

---

## 18. V1 레거시 참조

sms2가 배치 동기화를 담당하지만, 일부 V1 컴포넌트는 여전히 사용됩니다.

| V1 컴포넌트 | 현재 상태 | 사용처 |
|------------|----------|--------|
| SmsReader | **V1 only** | SmsProcessingService (실시간 수신) |
| SmsParser | **V1 only** | SmsProcessingService (실시간 파싱) |
| HybridSmsClassifier | **V1 only** + `cleanupStalePatterns()` | 실시간 + 30일 미사용 패턴 정리 |
| SmsFilter | **V1+sms2 공유** | 발신자 필터 (shouldSkipBySender) |
| SmsBatchProcessor | **미사용** (삭제 대기) | — |
| VectorSearchEngine | **V1 only** | HybridSmsClassifier |
| GeneratedSmsRegexParser | **V1 only** | HybridSmsClassifier |
| SmsEmbeddingService | **V1 only** | HybridSmsClassifier |

> SmsProcessingService(실시간 SMS 수신)는 아직 V1 경로를 사용합니다.
> 향후 sms2로 전환 시 SmsSyncCoordinator.process()를 통해 동일한 파이프라인으로 처리 가능.

---

## 19. DB 마이그레이션 히스토리 (sms_patterns 관련)

| 버전 | 변경 내용 |
|------|----------|
| v1→v2 | amountRegex, storeRegex, cardRegex 컬럼 추가 |
| v2→v3 | isMainGroup 컬럼 추가 (메인 그룹 패턴 식별) |

---

*마지막 업데이트: 2026-02-20*
