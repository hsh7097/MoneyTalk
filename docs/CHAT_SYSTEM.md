# AI 채팅 상담 시스템

> Gemini 기반 재무 상담 AI: 2-Phase 쿼리 분석 + Rolling Summary 대화 맥락 관리

---

## 1. 시스템 개요

MoneyTalk의 채팅 시스템은 사용자의 자연어 질문을 분석하여 실제 지출 데이터를 조회하고,
데이터 기반의 맞춤 재무 상담을 제공합니다.

```
사용자 질문: "이번 달 식비 얼마야?"
  │
  ▼
┌────────────────────────────────────────────┐
│ Phase 1: 쿼리 분석 (queryAnalyzerModel)     │
│  └ "식비 카테고리의 이번 달 합계가 필요하군"    │
│  └ JSON: {type: "expense_by_category",     │
│           category: "식비", ...}            │
└────────────┬───────────────────────────────┘
             │
             ▼
┌────────────────────────────────────────────┐
│ 데이터 조회 (Room DB)                       │
│  └ ExpenseDao.getExpenseSumByCategory()    │
│  └ 결과: 식비 350,000원                     │
└────────────┬───────────────────────────────┘
             │
             ▼
┌────────────────────────────────────────────┐
│ Phase 2: 답변 생성 (financialAdvisorModel)  │
│  └ [Rolling Summary + 최근 대화 + 데이터]    │
│  └ "이번 달 식비는 35만원이야! 수입 대비      │
│     15%로 적정 수준이네 👍"                  │
└────────────────────────────────────────────┘
```

---

## 2. 2-Phase 처리 아키텍처

### Phase 1: 쿼리/액션 분석

**파일**: `feature/chat/data/GeminiRepository.kt` — `analyzeQueryNeeds()`
**모델**: `gemini-2.5-pro` (temperature: 0.3)

사용자의 자연어 질문을 분석하여 필요한 DB 쿼리와 액션을 JSON으로 결정합니다.

#### 사용 가능한 쿼리 타입

| 타입 | 설명 | 필수 파라미터 |
|------|------|-------------|
| `total_expense` | 기간 내 총 지출 | startDate, endDate |
| `total_income` | 기간 내 총 수입 | startDate, endDate |
| `expense_by_category` | 카테고리별 지출 합계 | startDate, endDate |
| `expense_list` | 지출 내역 리스트 | limit |
| `expense_by_store` | 특정 가게 지출 | storeName |
| `daily_totals` | 일별 지출 합계 | startDate, endDate |
| `monthly_totals` | 월별 지출 합계 | — |
| `monthly_income` | 설정된 월 수입 | — |
| `uncategorized_list` | 미분류 항목 | — |
| `category_ratio` | 수입 대비 비율 분석 | — |

#### 사용 가능한 액션 타입

| 타입 | 설명 | 필수 파라미터 |
|------|------|-------------|
| `update_category` | 특정 지출 카테고리 변경 | expenseId, newCategory |
| `update_category_by_store` | 가게명 기준 일괄 변경 | storeName, newCategory |
| `update_category_by_keyword` | 키워드 포함 일괄 변경 | searchKeyword, newCategory |

#### 날짜 해석 규칙
- "이번달" → 이번달 1일 ~ 오늘
- "지난달" → 전월 1일 ~ 말일
- "3개월간" → 최근 3개월
- 연도 미지정 → 올해로 가정

### Phase 2: 답변 생성

**모델**: `gemini-2.5-pro` (temperature: 0.7)

System Instruction에 재무 상담사 역할이 정의되어 있으며, 다음 데이터를 바탕으로 답변합니다:

- 월 수입 정보
- Phase 1에서 조회한 데이터
- 액션 실행 결과
- Rolling Summary (과거 대화 맥락)
- 최근 대화 메시지

#### 답변 스타일
- 한국어 반말
- 이모지 적절히 사용
- 구체적 숫자와 실용적 조언
- 간결하고 응원하는 톤

#### 지출 적정 기준
| 카테고리 | 수입 대비 적정 비율 |
|---------|-----------------|
| 식비 | 15-20% |
| 주거비 | 25-30% |
| 교통비 | 5-10% |
| 카페/여가 | 5-10% |
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

**예시 타임라인:**
```
메시지 1~6 (3턴):  Summary 없음, 윈도우=[1~6]
메시지 7 추가:     1~2번 요약 → Summary 생성, 윈도우=[3~7]
메시지 9 추가:     기존 Summary + 3~4번 요약 통합, 윈도우=[5~9]
```
→ 대화가 아무리 길어져도 컨텍스트 크기가 일정하게 유지됩니다.

### 요약 모델 설정
- **모델**: `gemini-2.5-flash` (temperature: 0.3)
- **최대 출력**: 512 토큰
- **규칙**: 200자 이내, 한국어, 요약체

### 누적 요약 업데이트
```
기존 요약 + 새로 밀려난 메시지 → Gemini → 통합 요약본
```

### 저장 위치
`ChatSessionEntity.currentSummary` — 세션별로 독립적으로 관리

---

## 4. Gemini 모델 구성

### 3개 모델 분리 운영

| 모델 | 역할 | Gemini 모델 | temperature | maxTokens |
|------|------|-----------|-------------|-----------|
| `queryAnalyzerModel` | 쿼리/액션 분석 | `gemini-2.5-pro` | 0.3 | 512 |
| `financialAdvisorModel` | 재무 상담 답변 | `gemini-2.5-pro` | 0.7 | 1024 |
| `summaryModel` | Rolling Summary 생성 | `gemini-2.5-flash` | 0.3 | 512 |

각 모델에 별도 System Instruction 적용. 요약은 비교적 단순 작업이므로 경량 모델(flash) 사용

### API 키 관리
- `SettingsDataStore`에 암호화 저장
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
├── 3. Phase 1: analyzeQueryNeeds()
│   └── JSON 파싱 → DataQueryRequest
│
├── 4. 데이터 조회 (DataQueryParser → ExpenseDao 등)
│   └── QueryResult 목록 생성
│
├── 5. 액션 실행 (있는 경우)
│   └── ActionResult 목록 생성
│
├── 6. Phase 2: generateFinalAnswerWithContext()
│   └── [요약 + 최근 대화 + 데이터 + 질문] → Gemini
│
├── 7. AI 응답 ChatEntity 저장 (isUser = false)
│
└── 8. Rolling Summary 업데이트 (필요 시)
    └── 윈도우 밖 메시지가 있으면 요약 갱신
```

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

### 액션 요청
| 질문 | 액션 타입 |
|------|----------|
| "쿠팡은 쇼핑으로 분류해줘" | `update_category_by_store` |
| "배달 포함된건 식비로 바꿔" | `update_category_by_keyword` |

### 일반 상담
| 질문 | 처리 |
|------|------|
| "소비 습관 어때?" | 데이터 조회 + 분석 답변 |
| "저축 목표 세워줘" | 상담 모드 답변 |

---

## 8. 관련 파일 목록

| 파일 | 역할 |
|------|------|
| `feature/chat/data/GeminiRepository.kt` | Gemini API 통신 (3개 모델) |
| `feature/chat/data/ChatRepository.kt` | 채팅 데이터 관리 인터페이스 |
| `feature/chat/data/ChatRepositoryImpl.kt` | 채팅 데이터 관리 구현 |
| `feature/chat/data/ChatContextBuilder.kt` | Rolling Summary + 컨텍스트 구성 |
| `feature/chat/ui/ChatViewModel.kt` | 채팅 UI 상태 관리 |
| `feature/chat/ui/ChatScreen.kt` | 채팅 UI (Compose) |
| `core/util/DataQueryParser.kt` | JSON → 쿼리 요청 파싱 |
| `core/database/dao/ChatDao.kt` | 세션/메시지 DAO |
| `core/database/entity/ChatEntity.kt` | 메시지 엔티티 |
| `core/database/entity/ChatSessionEntity.kt` | 세션 엔티티 |

---

## 9. 벡터 기반 기능 현황

| 기능 | 사용 여부 | 설명 |
|------|----------|------|
| 대화 히스토리 벡터 검색 | ❌ 미사용 | 대화 기록은 벡터화하지 않음 |
| RAG (Retrieval Augmented Generation) | ❌ 미사용 | 과거 대화에서 관련 내용 검색하는 기능 없음 |
| Rolling Summary | ✅ 사용 | LLM 기반 요약으로 맥락 압축 (벡터 불필요) |
| 지출 데이터 컨텍스트 | ✅ 사용 | Room DB 직접 쿼리 (SQL, 벡터 불필요) |

현재 채팅 시스템은 **순차적 맥락 관리**(Rolling Summary)만 사용합니다.
벡터 임베딩은 SMS 분류(`SmsPatternEntity`)와 카테고리 분류(`StoreEmbeddingEntity`)에만 활용됩니다.

---

*마지막 업데이트: 2026-02-08*
