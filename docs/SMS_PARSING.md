# SMS 파싱 시스템

> 카드 결제 문자에서 지출 정보를 자동 추출하는 3-tier 하이브리드 분류 시스템

---

## 1. 시스템 개요

MoneyTalk은 SMS 문자에서 결제 정보를 자동으로 추출하기 위해 **3단계 하이브리드 분류 시스템**을 사용합니다.

```
SMS/MMS/RCS 수신 (SmsReader)
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
     벡터 학습    │ Tier 2: Vector (벡터 유사도)│ ← 임베딩 API 1건, ~1초
                  │  └ 임베딩 생성             │
                  │  └ 코사인 유사도 검색       │
                  └──────┬──────────┬──────────┘
                   ≥0.95 │          │ ≥0.92
                         ▼          ▼
                    캐시 재사용   ┌──────────────────────┐
                                │ Tier 3: LLM (Gemini)  │ ← API 1건, ~2초
                                │  └ 결제 가능성 사전 체크│
                                │  └ 결제 여부 판정      │
                                │  └ 정보 추출           │
                                └──────┬────────────────┘
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
| ≥ 0.95 | 캐시 재사용 | 저장된 파싱 결과(가게명, 카드사, 카테고리) 그대로 사용. 금액/날짜만 현재 SMS에서 재추출 |
| ≥ 0.92 | 결제 확인 | 결제 문자로 판정, 세부 정보는 Tier 3(LLM)에 위임. LLM 실패 시 캐시 폴백 |
| < 0.92 | 미매칭 | 결제 가능성 체크 통과 시 Tier 3으로, 미통과 시 비결제로 판정 |

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

### Tier 3 LLM 호출 조건 (결제 가능성 사전 체크)

Tier 1~2 실패 시, 무조건 LLM을 호출하면 비용이 폭증합니다.
비결제 사전 필터링 후, `hasPotentialPaymentIndicators()` 메서드로 결제 가능성을 체크합니다.

**결제 가능성 판정 (3가지 지표 중 2개 이상 매칭 시 LLM 호출 허용):**

| 지표 | 체크 방법 | 예시 |
|------|----------|------|
| 금액 패턴 | `[\d,]+원` 정규식 | "15,000원" |
| 결제 키워드 | 승인/결제/출금/사용/이용/누적 등 | "승인" |
| 카드사 키워드 | `SmsParser.extractCardName()` ≠ "기타" | "신한" → true |

이 메커니즘으로:
- 광고, 인증번호 등 비결제 SMS → LLM 호출 차단 (비용 절감)
- 새 카드사나 새 형식의 결제 SMS → LLM 호출 허용 (커버리지 유지)
- 부트스트랩 모드(패턴 < 10개) 여부와 무관하게 항상 동작

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
식비, 카페, 배달, 술/유흥, 교통, 쇼핑, 구독, 의료/건강, 운동, 문화/여가, 교육, 주거, 생활, 보험, 계좌이체, 경조, 기타

### 분류 주의사항
- **계좌이체**: 명시적 계좌이체/타행이체/당행이체만 해당. "체크카드출금"은 일반 카드 결제이므로 가게명 기준 분류
- **보험**: 보험료 납부 전용 카테고리 (생활/기타와 구분)

### 참조 리스트 (CategoryReferenceProvider)
SMS 추출 프롬프트에 동적 참조 리스트가 추가됩니다. 사용자가 학습시킨 가게명→카테고리 매핑을 LLM 프롬프트에 주입하여 일관성을 높입니다.
- 파일: `core/util/CategoryReferenceProvider.kt`
- source="user" 매핑 우선, 카테고리당 최대 5개 예시

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

## 6. 메시지 읽기 (SmsReader)

### 파일 위치
`core/util/SmsReader.kt`

### 지원 메시지 유형

| 유형 | Content URI | 날짜 형식 | 비고 |
|------|-----------|----------|------|
| SMS | `content://sms/inbox` | 밀리초 | 표준 문자 |
| MMS | `content://mms/inbox` | **초** (×1000 변환) | 장문 문자 |
| RCS | `content://im/chat` | 밀리초 | 삼성 기기, JSON 본문 |

모든 읽기 메소드는 SMS + MMS + RCS를 통합(Unified) 조회합니다.

### RCS 메시지 본문 파싱

RCS 메시지의 body는 JSON 형식이며, 위젯 트리 구조로 텍스트가 포함됩니다:

```json
{
  "messageHeader": "[Web발신]",
  "layout": {
    "widget": "LinearLayout",
    "children": [
      {
        "widget": "TextView",
        "text": "신한카드(5146)승인 하*현 207,200원..."
      }
    ]
  }
}
```

**`extractRcsText()` 추출 순서:**
1. 직접 텍스트 필드 검색: `text`, `body`, `message`, `msg`, `content`
2. `layout` 필드의 위젯 트리를 재귀 탐색 (`extractTextsFromLayout`)
3. `widget == "TextView"` 인 노드에서 `text` 값 수집
4. 줄바꿈(`\n`)으로 조합하여 일반 텍스트 반환

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

---

## 7. 동기화 흐름 (HomeViewModel.syncSmsMessages)

```
syncSmsMessages(forceFullSync, todayOnly)
│
├── 1. Regex 분류 (readCardMessagesByDateRange → SmsParser)
│   └ 성공 → ExpenseEntity 저장 + regexLearningData 수집
│
├── 2. 미분류 SMS 추출 (readAllMessagesByDateRange)
│   └ SMS+MMS+RCS 통합 조회, 기간 제한: 전체 동기화 시 최근 1년
│
├── 3-a. 대량 (>50건 or 전체동기화)
│   └ SmsBatchProcessor.processBatch()
│
├── 3-b. 소량 (≤50건)
│   └ HybridSmsClassifier.batchClassify() (배치 처리)
│
├── 4. 수입 SMS 동기화 (readIncomeMessagesByDateRange)
│
├── 5. 벡터 DB 배치 학습 (백그라운드, 실패 시 사용자 알림)
│   └ hybridSmsClassifier.batchLearnFromRegexResults(regexLearningData)
│
└── 6. 오래된 패턴 정리 (30일 미사용 + 1회 매칭)
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

*마지막 업데이트: 2026-02-08*
