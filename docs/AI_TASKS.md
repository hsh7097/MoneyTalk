# AI_TASKS.md - 작업 목록 및 완료 기준

> VectorSearchEngine 책임 분리 리팩토링의 세부 태스크 목록
> **최종 갱신**: 2026-02-08 (Phase 1 완료)

---

## Phase 0: 컨텍스트 패키징 ✅ 완료

- [x] **0-0** 기존 문서 5종 읽기
- [x] **0-1** `docs/AI_CONTEXT.md` 생성
- [x] **0-2** `docs/AI_HANDOFF.md` 생성
- [x] **0-3** `docs/AI_TASKS.md` 생성 (이 문서)
- [x] **0-4** `CLAUDE.md` 생성

---

## Phase 1: VectorSearchEngine 책임 분리 ✅ 완료

### 1-1. VectorSearchEngine 책임 축소 ✅
- [x] 임계값 상수 5개 제거 (PAYMENT, CACHE_REUSE, STORE, PROPAGATION, GROUPING)
- [x] findBestMatch, findTopK 등의 minSimilarity 기본값 제거 → 호출자가 명시
- [x] 순수 벡터 연산만 남기기: cosineSimilarity, findTopK, findBestMatch, findBestStoreMatch, findSimilarStores
- [x] SearchResult, StoreSearchResult 데이터 클래스 유지

### 1-2. core/similarity/ 패키지 신설 ✅
- [x] `SimilarityPolicy.kt` 인터페이스 생성
- [x] `SimilarityProfile.kt` 데이터 클래스 생성
- [x] `SmsPatternSimilarityPolicy.kt` 구현 (autoApply=0.95, confirm=0.92, group=0.95, NON_PAYMENT_CACHE=0.97)
- [x] `StoreNameSimilarityPolicy.kt` 구현 (autoApply=0.92, propagate=0.90, group=0.88)
- [x] `CategoryPropagationPolicy.kt` 구현 (propagate=0.90, MIN_PROPAGATION_CONFIDENCE=0.6)

### 1-3. SimilarityProfile 도입 + 하드코딩 임계값 치환 ✅
- [x] `HybridSmsClassifier.kt`: VectorSearchEngine 상수 → SmsPatternSimilarityPolicy 참조
- [x] `SmsBatchProcessor.kt`: 로컬 GROUPING_SIMILARITY_THRESHOLD → SmsPatternSimilarityPolicy.shouldGroup()
- [x] `StoreNameGrouper.kt`: VectorSearchEngine.GROUPING_SIMILARITY_THRESHOLD → StoreNameSimilarityPolicy.shouldGroup()
- [x] `StoreEmbeddingRepository.kt`: STORE/PROPAGATION 상수 → StoreNameSimilarityPolicy.profile 참조
- [x] 비결제 패턴 캐시 히트 임계값 → SmsPatternSimilarityPolicy.NON_PAYMENT_CACHE_THRESHOLD

### 1-4. confidence 정책 로직 반영 ✅
- [x] `CategoryPropagationPolicy`에 shouldPropagateWithConfidence() 구현
- [x] `StoreEmbeddingRepository.propagateCategoryToSimilarStores()`에 confidence 파라미터 + 체크 추가
- [x] 기존 동작 영향 없음 확인 (기본값 confidence=1.0f)

### 1-5. 영향 범위 점검 + 빌드 확인 ✅
- [x] `gradlew assembleDebug` 빌드 성공
- [x] VectorSearchEngine에 도메인 상수 0개 확인
- [x] SmsBatchProcessor에 로컬 임계값 상수 0개 확인
- [x] 모든 임계값이 SimilarityPolicy 계층을 통해 접근 확인
- [x] AI_CONTEXT.md 갱신
- [x] AI_HANDOFF.md 갱신
- [x] AI_TASKS.md 갱신 (이 문서)

---

## 리팩토링 결과 요약

### 변경된 파일 (7개)

| 파일 | 변경 내용 |
|------|----------|
| `core/util/VectorSearchEngine.kt` | 임계값 상수 5개 제거, KDoc 업데이트 |
| `core/util/HybridSmsClassifier.kt` | SmsPatternSimilarityPolicy 참조로 변경 |
| `core/util/SmsBatchProcessor.kt` | 로컬 GROUPING 상수 제거, SmsPatternSimilarityPolicy 참조 |
| `core/util/StoreNameGrouper.kt` | StoreNameSimilarityPolicy 참조로 변경 |
| `feature/home/data/StoreEmbeddingRepository.kt` | StoreNameSimilarityPolicy + CategoryPropagationPolicy 참조, confidence 파라미터 추가 |

### 새로 생성된 파일 (5개)

| 파일 | 역할 |
|------|------|
| `core/similarity/SimilarityProfile.kt` | 임계값 구조화 데이터 클래스 |
| `core/similarity/SimilarityPolicy.kt` | 유사도 판정 정책 인터페이스 |
| `core/similarity/SmsPatternSimilarityPolicy.kt` | SMS 패턴 분류 정책 (0.92/0.95/0.97) |
| `core/similarity/StoreNameSimilarityPolicy.kt` | 가게명 매칭 정책 (0.88/0.90/0.92) |
| `core/similarity/CategoryPropagationPolicy.kt` | 카테고리 전파 정책 (confidence ≥ 0.6 차단) |

---

## Phase 2: 후보 태스크 (미착수)

> Phase 1 리팩토링 기반 위에서 SMS 분류 정확도/효율을 개선하는 작업들

### 2-A. 부트스트랩 모드 게이트 제거
- [ ] `HybridSmsClassifier.classify()`에서 `isBootstrap` 조건 제거
- [ ] `HybridSmsClassifier.batchClassify()`에서 `isBootstrap` 조건 제거
- [ ] `hasPotentialPaymentIndicators()` 비용 통제 메서드 추가
- [ ] 기존 regex 파싱 정상 동작 확인
- [ ] 빌드 성공 확인

### 2-B. 캐시 재사용 임계값 조정
- [ ] `SmsPatternSimilarityPolicy.profile.autoApply` 0.95 유지 검토
- [ ] `NON_PAYMENT_CACHE_THRESHOLD` 0.97→0.95 완화 검토
- [ ] 동일 가게 변형 캐시 히트율 테스트

### 2-C. 벡터 학습 실패 시 사용자 알림
- [ ] `HomeViewModel.syncSmsMessages()`에서 학습 실패 시 `_uiState.errorMessage` 갱신
- [ ] 실패 시 로그 레벨 `Log.w` → `Log.e` 격상
- [ ] UI에서 부분 실패 메시지 표시
