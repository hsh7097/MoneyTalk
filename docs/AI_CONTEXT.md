# AI_CONTEXT.md - MoneyTalk 프로젝트 컨텍스트

> AI 에이전트가 MoneyTalk 프로젝트를 이해하고 작업하기 위한 핵심 컨텍스트 문서
> **최종 갱신**: 2026-02-08

---

## 1. 프로젝트 정의

**MoneyTalk**는 SMS 파싱 기반 자동 지출 추적 + AI 재무 상담 Android 앱입니다.

- **언어**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **아키텍처**: MVVM + Hilt DI + Room DB
- **AI**: Google Gemini (2.5-pro/2.5-flash)
- **Min SDK**: 26 (Android 8.0)
- **Package**: `com.sanha.moneytalk`

---

## 2. 핵심 아키텍처

### 2-1. 패키지 구조
```
app/src/main/java/com/sanha/moneytalk/
├── core/
│   ├── database/          # Room DB (entity, dao)
│   ├── datastore/         # DataStore (설정값)
│   ├── di/                # Hilt DI 모듈
│   ├── model/             # Category enum 등
│   ├── ui/component/      # 공통 UI 컴포넌트
│   ├── similarity/        # 유사도 판정 정책 (SimilarityPolicy 구현체)
│   └── util/              # 핵심 유틸 (SMS파싱, 벡터엔진, 분류기, CategoryReferenceProvider 등)
├── feature/
│   ├── home/              # 홈 화면 (월간 현황, SMS 동기화)
│   │   ├── data/          # Repository (Expense, Income, StoreEmbedding)
│   │   └── ui/            # HomeScreen, HomeViewModel
│   ├── history/           # 내역 화면 (지출/수입 목록, 캘린더)
│   │   └── ui/            # HistoryScreen, HistoryViewModel
│   ├── chat/              # AI 상담 (채팅방 리스트/내부)
│   │   ├── data/          # GeminiRepository, ChatRepository
│   │   └── ui/            # ChatScreen, ChatViewModel
│   └── settings/          # 설정
└── MoneyTalkApplication.kt
```

### 2-2. 핵심 시스템 3종

| 시스템 | 설명 | 핵심 파일 |
|--------|------|-----------|
| SMS 파싱 (3-tier) | Regex → Vector → Gemini LLM | HybridSmsClassifier.kt, VectorSearchEngine.kt |
| 카테고리 분류 (4-tier) | Room → Vector → Keyword → Gemini Batch | CategoryClassifierService.kt, StoreEmbeddingRepository.kt |
| AI 채팅 (2-phase) | 쿼리분석 → DB조회 → 답변생성 | ChatViewModel.kt, GeminiRepository.kt |

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
0.97 ─── 비결제 패턴 캐시 히트 (SmsPatternSimilarityPolicy.NON_PAYMENT_CACHE_THRESHOLD)
0.95 ─── 결제 패턴 캐시 재사용 (SmsPatternSimilarityPolicy.profile.autoApply)
       ─── SMS 배치 그룹핑 (SmsPatternSimilarityPolicy.profile.group)
0.92 ─── 결제 문자 판정 (SmsPatternSimilarityPolicy.profile.confirm)
       ─── 가게명 → 카테고리 자동 적용 (StoreNameSimilarityPolicy.profile.autoApply)
0.90 ─── 카테고리 전파 (StoreNameSimilarityPolicy.profile.propagate)
0.88 ─── 가게명 시맨틱 그룹핑 (StoreNameSimilarityPolicy.profile.group)
0.80 ─── LLM 고정 confidence
0.60 ─── confidence 차단 임계값 (CategoryPropagationPolicy.MIN_PROPAGATION_CONFIDENCE)
0.00 ─── 매칭 없음
```

---

## 4. 리팩토링 컨텍스트: VectorSearchEngine 책임 분리

### 4-1. Phase 1 완료 요약
`VectorSearchEngine`이 담당하던 **순수 벡터 연산**과 **도메인별 유사도 판정 정책**을 분리 완료.

→ Vector(연산) → Policy(판단) → Service(행동) 3계층 구조 확립

### 4-2. 완료된 변경 사항

| 파일 | 변경 내용 |
|------|----------|
| `core/util/VectorSearchEngine.kt` | 임계값 상수 5개 제거, 순수 벡터 연산만 유지 |
| `core/similarity/SimilarityPolicy.kt` | 유사도 판정 인터페이스 |
| `core/similarity/SimilarityProfile.kt` | 임계값 구조화 데이터 클래스 |
| `core/similarity/SmsPatternSimilarityPolicy.kt` | SMS 분류 정책 (0.92/0.95/0.97) |
| `core/similarity/StoreNameSimilarityPolicy.kt` | 가게명 매칭 정책 (0.88/0.90/0.92) |
| `core/similarity/CategoryPropagationPolicy.kt` | 카테고리 전파 정책 (confidence ≥ 0.6 차단) |
| `core/util/HybridSmsClassifier.kt` | SmsPatternSimilarityPolicy 참조 |
| `core/util/SmsBatchProcessor.kt` | SmsPatternSimilarityPolicy 참조 |
| `core/util/StoreNameGrouper.kt` | StoreNameSimilarityPolicy 참조 |
| `feature/home/data/StoreEmbeddingRepository.kt` | StoreNameSimilarityPolicy + CategoryPropagationPolicy 참조 |

---

## 5. Golden Flows (핵심 동작 흐름)

### 5-1. SMS → 지출 등록 흐름
```
SMS 수신 → SmsReceiver → classifyRegexOnly (Tier 1)
   → 성공: ExpenseEntity 생성 + 벡터 학습 큐에 추가
   → 실패: 로그만 (실시간은 Tier 1만)

SMS 동기화 (HomeViewModel.syncSmsMessages)
   → SmsReader.readAllSms()
   → HybridSmsClassifier.batchClassify() [Tier 1→2→3]
      → 벡터 매칭 → SmsPatternSimilarityPolicy 판단 → Tier 승격/차단
   → ExpenseRepository.insert() / IncomeRepository.insert()
   → batchLearnFromRegexResults() (벡터 학습)
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

### 5-3. AI 채팅 흐름
```
ChatViewModel.sendMessage(message)
   → ChatRepository.sendMessageAndBuildContext() [Rolling Summary + 윈도우]
   → GeminiRepository.analyzeQueryNeeds() [쿼리/액션 JSON 파싱]
   → executeQuery() / executeAction() [DB 조회/수정]
   → GeminiRepository.generateFinalAnswerWithContext() [최종 답변]
   → ChatRepository.saveAiResponseAndUpdateSummary() [요약 갱신]
```

---

## 6. 동기화 규칙 (Document Sync Rules)

### SSOT (Single Source of Truth) 원칙
- 임계값 수치 → 이 문서의 "임계값 레지스트리" 섹션이 SSOT
- 코드 변경 시 → 이 문서를 먼저 업데이트, 코드에 반영
- 문서 간 충돌 시 → AI_CONTEXT.md > 개별 문서 (SMS_PARSING.md 등)

### 갱신 타이밍
- 임계값 변경 시 → 즉시 갱신
- 새 파일/패키지 추가 시 → 패키지 구조 섹션 갱신
- 리팩토링 완료 시 → AI_TASKS.md 체크박스 갱신 + AI_CONTEXT.md 구조 반영
