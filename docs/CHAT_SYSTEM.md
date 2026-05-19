# AI 채팅 상담 시스템

> Gemini 기반 재무 상담 AI: 3-Step 쿼리 분석 + Rolling Summary 대화 맥락 관리

---

## 1. 시스템 개요

MoneyTalk의 채팅 시스템은 사용자의 자연어 질문을 분석하여 실제 지출 데이터를 조회하고,
데이터 기반의 맞춤 재무 상담을 제공합니다.

```
사용자 질문: "이번 달 식비 얼마야?"
  │
  ▼
┌────────────────────────────────────────────┐
│ Step 1: 쿼리 분석 (queryAnalyzerModel)      │
│  └ "식비 카테고리의 이번 달 합계가 필요하군"    │
│  └ JSON: {type: "expense_by_category",     │
│           category: "식비", ...}            │
└────────────┬───────────────────────────────┘
             │
             ▼
┌────────────────────────────────────────────┐
│ Step 2: 데이터 조회/액션 실행 (Room DB)        │
│  └ ExpenseDao.getExpenseSumByCategory()    │
│  └ 또는 executeAnalytics() (클라이언트 집계)   │
│  └ 결과: 식비 350,000원                     │
└────────────┬───────────────────────────────┘
             │
             ▼
┌────────────────────────────────────────────┐
│ Step 3: 답변 생성 (financialAdvisorModel)   │
│  └ [Rolling Summary + 최근 대화 + 데이터]    │
│  └ "이번 달 식비는 35만원이야! 수입 대비      │
│     15%로 적정 수준이네 👍"                  │
└────────────────────────────────────────────┘
```

---

## 2. 3-Step 처리 아키텍처

### Step 1: 쿼리/액션 분석

**파일**: [`feature/chat/data/GeminiRepository.kt`](../app/src/main/java/com/sanha/moneytalk/feature/chat/data/GeminiRepository.kt) — `analyzeQueryNeeds()`
**모델**: `gemini-2.5-pro` (temperature: 0.3)
**프롬프트**: [`res/values/string_prompt.xml`](../app/src/main/res/values/string_prompt.xml) — `prompt_query_analyzer_system`

사용자의 자연어 질문을 분석하여 필요한 DB 쿼리와 액션을 JSON으로 결정합니다.
질문이 모호한 경우 쿼리 대신 `clarification` 응답을 반환하여 사용자에게 추가 정보를 요청합니다.

#### Clarification 응답 (모호한 질문 처리)

질문이 모호하여 정확한 쿼리를 생성할 수 없을 때 반환:
- 기간 불명확: "얼마 썼어?" (어느 기간?)
- 대상 불명확: "줄여야 할까?" (무엇을?)
- 다중 해석: "카드 정리해줘" (조회? 삭제? 변경?)

```json
{"clarification": "어떤 카테고리의 절약을 알아볼까요? 예: 카페, 배달, 쇼핑 등"}
```

대화 맥락으로 의도가 파악 가능하면 clarification 없이 바로 쿼리 생성.

#### 쿼리 타입 (18종)

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

#### 액션 타입 (13종)

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

#### ANALYTICS 쿼리 (클라이언트 사이드 실행)

ChatViewModel에서 인메모리로 실행되는 복합 분석 기능:

| 구성 요소 | 지원 값 |
|----------|---------|
| **필터 연산자** | ==, !=, >, >=, <, <=, contains, not_contains, in, not_in |
| **필터 필드** | category, storeName, cardName, amount, memo, dayOfWeek |
| **그룹핑** | category, storeName, cardName, date, month, dayOfWeek |
| **집계 메트릭** | sum, avg, count, max, min |
| **정렬** | asc, desc |
| **topN** | 상위 N개 결과 |
| **includeSubcategories** | 하위 카테고리 포함 여부 |

#### 날짜 해석 규칙
- "이번달" → 이번달 1일 ~ 오늘
- "지난달" → 전월 1일 ~ 말일
- "3개월간" → 최근 3개월
- 연도 미지정 → 올해로 가정

### Step 2: 데이터 조회 / 액션 실행

**파일**: [`feature/chat/ui/ChatViewModel.kt`](../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatViewModel.kt)

- `executeQuery()`: DB 쿼리 실행 (Room DAO 호출)
- `executeAction()`: DB 수정 액션 실행
- `executeAnalytics()`: 클라이언트 사이드 복합 분석 (필터 → 그룹핑 → 집계)

쿼리 분석 실패 시 기본 폴백:
- `TOTAL_EXPENSE` + `EXPENSE_BY_CATEGORY` + `EXPENSE_LIST` (최근 10건)

### Step 3: 답변 생성

**모델**: `gemini-2.5-pro` (temperature: 0.7)
**프롬프트**: [`res/values/string_prompt.xml`](../app/src/main/res/values/string_prompt.xml) — `prompt_financial_advisor_system`

System Instruction에 재무 상담사 역할이 정의되어 있으며, 다음 데이터를 바탕으로 답변합니다:

- 월 수입 정보
- Step 2에서 조회한 데이터 / ANALYTICS 계산 결과
- 액션 실행 결과
- Rolling Summary (과거 대화 맥락)
- 최근 대화 메시지

#### 답변 규칙 (할루시네이션 방지)
- 조회된 데이터와 ANALYTICS 계산 결과만 사용 (직접 계산 금지)
- 실행한 액션에 대해서만 결과 보고 (실행 안 한 액션 언급 금지)
- 삭제/수정 작업의 되돌리기는 불가능하다는 안내

#### 수치 정확성 규칙 (Karpathy Guidelines 적용)
- **직접 계산 금지**: 리스트 항목 합산/평균 금지 → ANALYTICS 결과값만 인용
- **비율 계산 금지**: 직접 나눗셈으로 % 산출 금지 → 데이터에 비율이 있으면 사용, 없으면 미제시
- **교차 계산 금지**: 서로 다른 쿼리 결과끼리 합산/비교하여 새 수치 생성 금지
- **불확실하면 인정**: "데이터에서 확인된 값은 ~입니다"로 범위 한정

#### 데이터 부족/표본 부족 처리
- `DB 조회 결과 없음`, `조회된 데이터 없음`, `해당 기간 ... 없습니다`, `0건`, `필터 후 총 건수: 0건`은 분석 근거 부족으로 처리
- 합계가 0원일 때는 "확인된 값은 0원"으로만 표현하고, 실제 무소비/동기화 누락/권한 문제를 단정하지 않음
- ANALYTICS 결과의 `필터 후 총 건수`가 1~2건이면 확인된 사실만 말하고 패턴/습관/추세 판단은 보류
- 비교 질문에서 한쪽 기간이 비어 있으면 증감 방향을 일반화하지 않고 원본 값과 한계를 함께 안내
- 월 수입 0원은 "수입 없음"이 아니라 미설정으로 보고, 수입 대비 적정성 판단 전에 월 수입 설정을 안내
- 예산 데이터가 없으면 예산 초과/잔여 판단을 하지 않고 예산 설정 안내
- 금융 판단에 필요한 기간, 카테고리, 거래 내역, 월 수입, 예산이 부족하면 값을 추정하지 않고 추가 데이터를 요청하거나 판단 불가를 안내

#### 답변 스타일
- 한국어 존댓말
- 이모지 적절히 사용
- 구체적 숫자와 실용적 조언
- 간결하고 응원하는 톤

#### 지출 적정 기준
| 카테고리 | 수입 대비 적정 비율 |
|---------|-----------------|
| 식비 | 15-20% |
| 주거비 | 25-30% |
| 교통비 | 5-10% |
| 카페/간식 + 문화/여가 | 5-10% |
| 저축 | 20% 이상 권장 |
| 총 지출 | 80% 이하면 건강 |

---

## 3. Rolling Summary (대화 맥락 관리)

### 문제
AI 모델은 컨텍스트 제한이 있어 긴 대화를 모두 전달할 수 없습니다.
하지만 "아까 물어본 식비 말인데..." 같은 맥락 참조가 필요합니다.

### 해결: Rolling Summary + Windowed Context 전략

**핵심 설정:**
- `WINDOW_SIZE_TURNS = 3` (최근 3턴 유지)
- `WINDOW_SIZE_MESSAGES = 6` (3턴 = 6개 메시지)

```
대화 1~6번 (3턴): 전체 텍스트로 전달
                    │
                    │ 7번째 메시지 추가 시 (윈도우 초과)
                    ▼
        ┌──────────────────────────┐
        │ 1~6번 중 윈도우 밖 메시지   │
        │ 를 Gemini로 요약           │
        │ → currentSummary에 저장   │
        └──────────────────────────┘
                    │
                    ▼
AI에 전달되는 내용:
  [이전 대화 요약: 요약본]
  + [최근 대화: 최근 6개 메시지]
  + [현재 질문]
```

### 요약 모델 설정
- **모델**: `gemini-2.5-flash` (temperature: 0.3)
- **프롬프트**: [`res/values/string_prompt.xml`](../app/src/main/res/values/string_prompt.xml) — `prompt_summary_system`
- **규칙**: 200자 이내, 한국어, 요약체

### 저장 위치
`ChatSessionEntity.currentSummary` — 세션별로 독립적으로 관리

---

## 4. Gemini 모델 구성

### 3개 모델 분리 운영

| 모델 | 역할 | Gemini 모델 | temperature | topK | topP | maxTokens |
|------|------|-----------|-------------|------|------|-----------|
| queryAnalyzerModel | 쿼리/액션 분석 | gemini-2.5-pro | 0.3 | 20 | 0.9 | 10000 |
| financialAdvisorModel | 재무 상담 답변 | gemini-2.5-pro | 0.7 | 40 | 0.95 | 10000 |
| summaryModel | Rolling Summary | gemini-2.5-flash | 0.3 | 20 | 0.9 | 10000 |

> 모델 운영 기준(2026-04): Gemini 3.1/3 계열 preview가 최신 계열이지만, 앱 배포 기본값은 안정판 2.5 계열을 유지한다. 내부 테스트는 Firebase RTDB `/config/models` override로 진행한다.

### 프롬프트 위치 (XML 리소스)

> 채팅 관련 AI 요청 프롬프트는 [`res/values/string_prompt.xml`](../app/src/main/res/values/string_prompt.xml)에서 관리

| 프롬프트 그룹 | XML key | 사용처 |
|-------------|---------|-------|
| 쿼리 분석 시스템 | `prompt_query_analyzer_system` | GeminiRepository (queryAnalyzerModel) |
| 쿼리 분석 유저 템플릿 | `prompt_query_analyzer_user` | GeminiRepository, ChatContextBuilder |
| 최종 답변 시스템 | `prompt_financial_advisor_system` | GeminiRepository (financialAdvisorModel) |
| 최종 답변 유저 템플릿 | `prompt_final_answer_*` | GeminiRepository, ChatContextBuilder |
| 홈 한줄 인사이트 | `prompt_home_insight_*` | GeminiRepository.generateInsight() |
| 대화 요약/제목 | `prompt_summary_system`, `prompt_rolling_summary_*`, `prompt_chat_title_user` | GeminiRepository (summaryModel) |
| SMS/카테고리 계열 | `prompt_sms_*`, `prompt_category_classification`, `prompt_income_classification` | GeminiSmsExtractor, GeminiCategoryRepository |

관리 원칙:

- `string_prompt.xml`에는 모델에 전달되는 시스템 instruction/user prompt template만 둔다.
- 프롬프트 조립에 필요한 섹션 라벨, 방향값, 상태값은 `strings.xml`의 `ai_*` key에 둔다.
- Kotlin은 사용자 질문, 조회 결과, 액션 결과처럼 런타임 데이터만 조립한다.
- 카테고리 예시는 실제 displayName을 사용한다. 예: 카페는 `카페/간식`, 배달앱은 `식비`.

### API 키 관리
- `SettingsDataStore`에 저장
- 모든 모델이 같은 API 키 공유
- 키 변경 시 모든 모델 인스턴스 재생성

---

## 5. 채팅 데이터 구조

### 세션 (ChatSessionEntity)
```
chat_sessions 테이블
├── id: Long (PK)
├── title: String ("새 대화")
├── currentSummary: String? (Rolling Summary)
├── createdAt: Long
└── updatedAt: Long
```

### 메시지 (ChatEntity)
```
chat_history 테이블
├── id: Long (PK)
├── sessionId: Long (FK → chat_sessions.id, CASCADE)
├── message: String
├── isUser: Boolean
└── timestamp: Long
```

### 관계
`ChatSession (1) ←→ (N) ChatEntity`
세션 삭제 시 소속 메시지도 자동 삭제 (CASCADE)

---

## 6. 채팅 흐름 상세

```
사용자 메시지 입력
│
├── 1. ChatEntity 저장 (isUser = true)
│
├── 2. ChatContextBuilder로 컨텍스트 구성
│   ├── Rolling Summary 조회
│   ├── 최근 N개 메시지 조회 (ASC)
│   └── 통합 프롬프트 생성
│
├── 3. Step 1: analyzeQueryNeeds()
│   └── JSON 파싱 → DataQueryRequest (쿼리 + 액션 + clarification)
│
├── 3-1. Clarification 분기 (질문이 모호한 경우)
│   ├── isClarification = true → 확인 질문을 AI 응답으로 저장
│   └── Step 4~6 건너뜀 → 사용자 추가 입력 대기
│
├── 4. 데이터 조회 (DataQueryParser → ExpenseDao 등)
│   └── QueryResult 목록 생성
│   └── ANALYTICS 쿼리 시 executeAnalytics() (클라이언트 사이드)
│
├── 5. 액션 실행 (있는 경우)
│   └── ActionResult 목록 생성
│   └── StoreAliasManager로 가게 별칭 포함 일괄 처리
│
├── 6. Step 3: generateFinalAnswerWithContext()
│   └── [요약 + 최근 대화 + 데이터 + 질문] → Gemini
│
├── 7. AI 응답 ChatEntity 저장 (isUser = false)
│
└── 8. Rolling Summary 업데이트 (필요 시)
    └── 윈도우 밖 메시지가 있으면 요약 갱신
```

### 추가 기능
- **Clarification 루프**: 질문이 모호하면 쿼리 생성 대신 확인 질문 반환 → 사용자 추가 입력 후 재분석
- **자동 타이틀 생성**: 채팅방 나갈 때 대화 내용 기반 LLM 타이틀 생성 (15자 제한)
- **재시도**: AI 응답 실패 시 canRetry 플래그로 재시도 허용
- **Mutex**: sendMutex로 동시 메시지 전송 방지

---

## 7. 질문 예시와 처리 흐름

### 데이터 조회 질문
| 질문 | 쿼리 타입 |
|------|----------|
| "이번 달 총 지출이 얼마야?" | `total_expense` |
| "카테고리별 지출 보여줘" | `expense_by_category` |
| "쿠팡에서 얼마 썼어?" | `expense_by_store` |
| "최근 10건 지출 내역" | `expense_list (limit: 10)` |
| "식비가 수입 대비 적절해?" | `category_ratio` + `expense_by_category` |
| "주말에 가장 많이 쓴 카테고리는?" | `analytics` (dayOfWeek 필터 + category 그룹핑) |
| "예산 얼마 남았어?" | `budget_status` |
| "이번 달 예산 현황" | `budget_status` |

### 액션 요청
| 질문 | 액션 타입 |
|------|----------|
| "쿠팡은 쇼핑으로 분류해줘" | `update_category_by_store` |
| "배달 포함된건 식비로 바꿔" | `update_category_by_keyword` |
| "광고 문자 제외해줘" | `add_sms_exclusion` |
| "점심 12000원 추가해줘" | `add_expense` |
| "식비 예산 20만원 설정해줘" | `set_budget` |
| "전체 예산 100만원" | `set_budget` |

### 일반 상담
| 질문 | 처리 |
|------|------|
| "소비 습관 어때?" | 데이터 조회 + 분석 답변 |
| "저축 목표 세워줘" | 상담 모드 답변 |

---

## 8. 관련 파일 목록

| 파일 | 역할 |
|------|------|
| [`feature/chat/data/GeminiRepository.kt`](../app/src/main/java/com/sanha/moneytalk/feature/chat/data/GeminiRepository.kt) | Gemini API 통신 (3개 모델) |
| [`feature/chat/data/ChatRepository.kt`](../app/src/main/java/com/sanha/moneytalk/feature/chat/data/ChatRepository.kt) | 채팅 데이터 관리 인터페이스 |
| [`feature/chat/data/ChatRepositoryImpl.kt`](../app/src/main/java/com/sanha/moneytalk/feature/chat/data/ChatRepositoryImpl.kt) | 채팅 데이터 관리 구현 |
| [`feature/chat/data/ChatPrompts.kt`](../app/src/main/java/com/sanha/moneytalk/feature/chat/data/ChatPrompts.kt) | 프롬프트 키 참조 (실제 내용은 string_prompt.xml) |
| [`feature/chat/ui/ChatViewModel.kt`](../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatViewModel.kt) | 채팅 UI 상태 + 쿼리/액션/분석 실행 |
| [`feature/chat/ui/ChatScreen.kt`](../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatScreen.kt) | 채팅 UI (Compose) |
| [`core/util/DataQueryParser.kt`](../app/src/main/java/com/sanha/moneytalk/core/util/DataQueryParser.kt) | JSON → 쿼리/액션/clarification 파싱 + QueryType/ActionType enum |
| [`core/util/StoreAliasManager.kt`](../app/src/main/java/com/sanha/moneytalk/core/util/StoreAliasManager.kt) | 가게명 별칭 관리 (일괄 처리 지원) |
| [`core/database/dao/ChatDao.kt`](../app/src/main/java/com/sanha/moneytalk/core/database/dao/ChatDao.kt) | 세션/메시지 DAO |
| [`core/database/entity/ChatEntity.kt`](../app/src/main/java/com/sanha/moneytalk/core/database/entity/ChatEntity.kt) | 메시지 엔티티 |
| [`core/database/entity/ChatSessionEntity.kt`](../app/src/main/java/com/sanha/moneytalk/core/database/entity/ChatSessionEntity.kt) | 세션 엔티티 |
| [`res/values/string_prompt.xml`](../app/src/main/res/values/string_prompt.xml) | AI 시스템/유저 프롬프트 템플릿 |
| [`res/values/strings.xml`](../app/src/main/res/values/strings.xml) | 프롬프트 조립용 `ai_*` 보조 문자열 |

---

## 9. 벡터 기반 기능 현황

| 기능 | 사용 여부 | 설명 |
|------|----------|------|
| 대화 히스토리 벡터 검색 | ❌ 미사용 | 대화 기록은 벡터화하지 않음 |
| RAG (Retrieval Augmented Generation) | ❌ 미사용 | 과거 대화에서 관련 내용 검색하는 기능 없음 |
| Rolling Summary | ✅ 사용 | LLM 기반 요약으로 맥락 압축 (벡터 불필요) |
| 지출 데이터 컨텍스트 | ✅ 사용 | Room DB 직접 쿼리 (SQL, 벡터 불필요), 통계 제외 거래는 집계 제외 |
| ANALYTICS 인메모리 분석 | ✅ 사용 | 클라이언트 사이드 필터/그룹핑/집계, `isExcludedFromStats` 제외 |

현재 채팅 시스템은 **순차적 맥락 관리**(Rolling Summary)만 사용합니다.
벡터 임베딩은 SMS 분류(`SmsPatternEntity`)와 카테고리 분류(`StoreEmbeddingEntity`)에만 활용됩니다.

---

*마지막 업데이트: 2026-02-15*
