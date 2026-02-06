# SMS 파싱 시스템

> 카드 결제 문자에서 지출 정보를 자동 추출하는 3-tier 하이브리드 분류 시스템

---

## 1. 시스템 개요

MoneyTalk은 SMS 문자에서 결제 정보를 자동으로 추출하기 위해 **3단계 하이브리드 분류 시스템**을 사용합니다.

```
SMS 수신
  │
  ▼
┌─────────────────────────────────────┐
│ Tier 1: Regex (정규식)               │ ← 비용 0, 즉시 처리
│  └ SmsParser.isCardPaymentSms()     │
│  └ SmsParser.parseSms()             │
└────────────┬──────────┬─────────────┘
        성공 │          │ 실패
             ▼          ▼
     DB 저장 +    ┌──────────────────────────┐
     벡터 학습    │ Tier 2: Vector (벡터 유사도)│ ← API 1건, ~1초
                  │  └ 임베딩 생성             │
                  │  └ 코사인 유사도 검색       │
                  └──────┬──────────┬──────────┘
                   ≥0.97 │          │ ≥0.92
                         ▼          ▼
                    캐시 재사용   ┌──────────────────┐
                                │ Tier 3: LLM (Gemini)│ ← API 1건, ~2초
                                │  └ 결제 여부 판정   │
                                │  └ 정보 추출        │
                                └──────┬─────────────┘
                                  성공 │
                                       ▼
                                  DB 저장 + 벡터 학습
```

### 왜 3단계가 필요한가?

| 단계 | 커버리지 | 비용 | 속도 |
|------|---------|------|------|
| Regex | ~70% (표준 카드 문자) | 무료 | <1ms |
| Vector | ~90% (유사 패턴 재사용) | 임베딩 API 1회 | ~1초 |
| LLM | ~99% (비표준 형식 포함) | Gemini API 1회 | ~2초 |

정규식만으로는 모든 카드사의 다양한 SMS 형식을 커버할 수 없고, 모든 SMS를 LLM에 보내면 비용/속도가 문제됩니다.

---

## 2. Tier 1: 정규식 파싱 (SmsParser)

### 파일 위치
`core/util/SmsParser.kt`

### 결제 문자 판별 조건
1. 광고/안내 키워드가 **없어야** 함 (광고, 명세서, 청구서 등)
2. 카드사 키워드가 **있어야** 함 (KB, 신한, 삼성 등 50+ 키워드)
3. 결제 관련 키워드가 **있어야** 함 (결제, 승인, 사용, 출금, 이용)
4. 금액 패턴이 **있어야** 함 (`숫자+원` 또는 줄바꿈 사이 3자리+ 숫자)

### 추출 항목

| 항목 | 추출 방법 | 예시 |
|------|----------|------|
| 금액 | 정규식 (`숫자+원`, KB 줄바꿈 등) | 15,000원 → 15000 |
| 가게명 | 위치 기반 추출 (시간 뒤, 금액 앞/뒤) | 스타벅스 |
| 카드사 | 키워드 매칭 | KB → KB국민 |
| 날짜시간 | 패턴 (MM/DD HH:mm) | 12/25 14:30 → 2025-12-25 14:30 |
| 카테고리 | 키워드 기반 추론 | 스타벅스 → 카페 |

### KB 스타일 특수 처리
KB국민카드는 줄바꿈으로 구분된 독특한 형식을 사용합니다:
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

### 수입 문자 판별
별도 키워드 세트(입금, 이체입금, 급여, 월급 등)로 입금 문자도 감지합니다.
자동이체출금, 카드대금 등은 제외 키워드로 필터링됩니다.

---

## 3. Tier 2: 벡터 유사도 분류 (HybridSmsClassifier)

### 파일 위치
- `core/util/HybridSmsClassifier.kt` - 3-tier 오케스트레이터
- `core/util/SmsEmbeddingService.kt` - 임베딩 생성
- `core/util/VectorSearchEngine.kt` - 코사인 유사도 검색

### 동작 원리

#### 1단계: SMS 템플릿화
변하는 부분(금액, 날짜, 시간)을 플레이스홀더로 치환하여 구조적 유사성을 높입니다.

```
원본:  "[KB]12/25 14:30 스타벅스 15,000원 승인"
템플릿: "[KB]{DATE} {TIME} 스타벅스 {AMOUNT}원 승인"
```

치환 규칙:
- `숫자+원` → `{AMOUNT}원`
- `MM/DD` → `{DATE}`
- `HH:mm` → `{TIME}`
- `잔액숫자` → `잔액{BALANCE}`
- `카드번호` → `{CARD_NUM}`

#### 2단계: 임베딩 생성
Gemini Embedding API (`gemini-embedding-001`)로 768차원 벡터 생성

- 단일: `embedContent` 엔드포인트
- 배치: `batchEmbedContents` 엔드포인트

#### 3단계: 유사도 검색
코사인 유사도로 DB에 저장된 패턴과 비교

```
코사인 유사도 = (A·B) / (|A| × |B|)
```

| 유사도 | 판정 | 동작 |
|-------|------|------|
| ≥ 0.97 | 캐시 재사용 | 저장된 파싱 결과(가게명, 카드사, 카테고리) 그대로 사용. 금액/날짜만 현재 SMS에서 재추출 |
| ≥ 0.92 | 결제 확인 | 결제 문자로 판정, 세부 정보는 Tier 3(LLM)에 위임 |
| < 0.92 | 미매칭 | 부트스트랩 모드면 Tier 3으로, 아니면 비결제로 판정 |

### 비결제 패턴 사전 필터링
부트스트랩 모드에서 LLM 호출 전, 명백한 비결제 SMS를 사전 필터링합니다.

필터링 키워드:
- 인증/보안: `인증번호`, `OTP`, `authentication`, `verification`
- 해외: `국외발신`, `국제발신`, `해외발신`
- 광고: `[광고]`, `수신거부`, `프로모션`
- 안내: `명세서`, `청구서`, `납부안내`
- 배송: `택배`, `운송장`

### 비결제 패턴 벡터 캐싱
LLM이 비결제로 판정한 SMS도 벡터 DB에 `isPayment=false`로 등록됩니다.
다음에 유사한 SMS가 오면 Tier 2에서 바로 비결제로 판정하여 LLM 호출을 방지합니다.

벡터 검색 순서:
1. 비결제 패턴 우선 검색 (유사도 ≥ 0.97 → 즉시 비결제 판정)
2. 결제 패턴 검색 (기존 로직)

### 부트스트랩 모드
패턴 DB에 데이터가 10개 미만이면 활성화됩니다.
Regex 실패 시 사전 필터링을 거친 후 LLM을 호출하여 초기 패턴 데이터를 빠르게 축적합니다.

### 자가 학습
Tier 1(Regex), Tier 3(LLM)에서 성공적으로 파싱한 SMS는 자동으로 벡터 DB에 등록됩니다.
LLM이 비결제로 판정한 SMS도 벡터 DB에 등록하여 비결제 패턴도 학습합니다.
→ 시간이 지날수록 Tier 2의 커버리지가 높아지고, LLM 호출이 줄어듭니다.

---

## 4. Tier 3: LLM 추출 (GeminiSmsExtractor)

### 파일 위치
`core/util/GeminiSmsExtractor.kt`

### 사용 모델
`gemini-2.5-flash-lite` (temperature: 0.1)

### 프롬프트 전략
System Instruction에 한국 카드 결제 SMS 전문가 역할을 부여하고, JSON 형식으로 응답받습니다.

```json
{
  "isPayment": true,
  "amount": 15000,
  "storeName": "스타벅스",
  "cardName": "KB국민",
  "dateTime": "2025-01-15 14:30",
  "category": "카페"
}
```

### 카테고리 목록
식비, 카페, 교통, 쇼핑, 구독, 의료/건강, 문화/여가, 교육, 생활, 기타

---

## 5. 대량 배치 처리 (SmsBatchProcessor)

### 파일 위치
`core/util/SmsBatchProcessor.kt`

### 사용 시점
- 전체 동기화 (forceFullSync)
- 미분류 SMS가 50건 초과

### 4단계 파이프라인

```
Step 1: 기존 패턴 매칭
  → DB에 이미 학습된 패턴과 매칭 시도 (LLM 없이)

Step 2: 배치 임베딩 생성
  → 남은 SMS를 템플릿화 + 50건씩 배치 임베딩

Step 3: 벡터 유사도 그룹핑
  → 유사도 ≥ 0.95인 SMS를 하나의 그룹으로 묶음
  → 그리디 클러스터링 (첫 SMS가 그룹 중심)

Step 4: 대표 샘플 LLM 검증
  → 각 그룹에서 대표 1건만 Gemini에 전송
  → 결제 확인 시 그룹 전체 멤버에 파싱 결과 전파
```

### 효율성
- **입력**: 수만 건의 미분류 SMS
- **LLM 호출**: 수십 건 (그룹 수만큼)
- **비용 절감**: 90%+ LLM 호출 감소

### 설정 상수
| 상수 | 값 | 설명 |
|------|---|------|
| `GROUPING_SIMILARITY_THRESHOLD` | 0.95 | 같은 그룹으로 묶는 유사도 |
| `EMBEDDING_BATCH_SIZE` | 50 | 배치 임베딩 최대 개수 |
| `LLM_DELAY_MS` | 1000 | LLM 호출 간 딜레이 (Rate Limit) |
| `MAX_UNCLASSIFIED_TO_PROCESS` | 500 | 한 번에 처리할 최대 SMS 수 |

---

## 6. SMS 읽기 (SmsReader)

### 파일 위치
`core/util/SmsReader.kt`

### 제공 메소드

| 메소드 | 용도 | 필터링 |
|-------|------|-------|
| `readAllCardSms()` | 전체 동기화 (결제) | 정규식 필터 |
| `readCardSmsByDateRange()` | 증분 동기화 (결제) | 기간 + 정규식 필터 |
| `readAllSmsByDateRange()` | 하이브리드 분류용 | 필터링 없음 (전체) |
| `readAllIncomeSms()` | 전체 동기화 (수입) | 입금 키워드 필터 |
| `readIncomeSmsByDateRange()` | 증분 동기화 (수입) | 기간 + 입금 필터 |

### SMS ID 생성
`발신번호_수신시간_본문해시코드` → 중복 저장 방지

---

## 7. 동기화 흐름 (HomeViewModel.syncSmsMessages)

```
syncSmsMessages(forceFullSync)
│
├── 1. Regex 분류 (readCardSmsByDateRange → SmsParser)
│   └ 성공 → ExpenseEntity 저장 + 벡터 DB 학습
│
├── 2. 미분류 SMS 추출 (readAllSmsByDateRange)
│   └ 기간 제한: 전체 동기화 시 최근 1년
│
├── 3-a. 대량 (>50건 or 전체동기화)
│   └ SmsBatchProcessor.processBatch()
│
├── 3-b. 소량 (≤50건)
│   └ HybridSmsClassifier.classify() (개별 처리)
│
├── 4. 수입 SMS 동기화
│
└── 5. 오래된 패턴 정리 (30일 미사용 + 1회 매칭)
```

---

## 8. 데이터베이스 스키마

### sms_patterns 테이블 (SmsPatternEntity)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동 증가 ID |
| smsTemplate | String | 템플릿화된 SMS 본문 |
| senderAddress | String | 발신 번호 |
| embedding | List<Float> → JSON | 768차원 임베딩 벡터 |
| isPayment | Boolean | 결제 문자 여부 |
| parsedAmount | Int | 캐시된 결제 금액 |
| parsedStoreName | String | 캐시된 가게명 |
| parsedCardName | String | 캐시된 카드사명 |
| parsedCategory | String | 캐시된 카테고리 |
| parseSource | String | 파싱 소스 (regex/llm/manual) |
| confidence | Float | 신뢰도 (0.0~1.0) |
| matchCount | Int | 매칭 횟수 |
| createdAt | Long | 생성 시간 |
| lastMatchedAt | Long | 마지막 매칭 시간 |

---

*마지막 업데이트: 2026-02-07*
