# AI_HANDOFF.md - AI 에이전트 인수인계 문서

> AI 에이전트가 교체되거나 세션이 끊겼을 때, 새 에이전트가 즉시 작업을 이어받을 수 있도록 하는 문서
> **최종 갱신**: 2026-02-08 (Phase 0 + Phase 1 완료)

---

## 1. 현재 작업 상태

### 완료: VectorSearchEngine 책임 분리 리팩토링

**Phase 0 (컨텍스트 패키징)**: ✅ 완료
- [x] 기존 문서 5종 읽기
- [x] docs/AI_CONTEXT.md, AI_HANDOFF.md, AI_TASKS.md, CLAUDE.md 생성

**Phase 1 (리팩토링)**: ✅ 완료
- [x] VectorSearchEngine 임계값 상수 5개 제거 → 순수 벡터 엔진
- [x] core/similarity/ 패키지 신설 (5개 파일)
- [x] 모든 호출자에서 SimilarityPolicy 참조로 치환
- [x] confidence < 0.6 전파 차단 정책 추가
- [x] 빌드 성공 확인

**벡터 그룹핑 + 참조 리스트 + 계좌이체 수정**: ✅ 완료
- [x] 계좌이체/출금 분류 버그 수정 (체크카드출금은 일반 카드 결제로 처리)
- [x] Tier 1.5b 그룹 기반 매칭 추가 (다수결, 유사도 ≥ 0.88)
- [x] 임베딩 1회 생성으로 Tier 1.5a/b 최적화 (이전: 2회 API 호출)
- [x] CategoryReferenceProvider 신설 (동적 참조 리스트 → 모든 LLM 프롬프트 주입)
- [x] 모든 프롬프트에 보험/계좌이체 카테고리 추가
- [x] SmsParser categoryKeywords 보험 매핑 수정 (기타→보험)
- [x] 문서 업데이트 (SMS_PARSING, CATEGORY_CLASSIFICATION, AI_CONTEXT, AI_HANDOFF)

### 대기 중인 작업: Phase 2 후보 (우선순위 미정)

- **2-A**: 부트스트랩 모드 게이트 제거 → 패턴 10개 후에도 LLM 호출 허용
- **2-B**: 캐시 재사용 임계값 조정 (0.97→0.95) → 동일 가게 변형 캐시 히트율 향상
- **2-C**: 벡터 학습 실패 시 사용자 알림 → fire-and-forget에서 에러 표시로 개선
- **2-D**: 채팅에서 카테고리 설정 시 CategoryReferenceProvider에 자동 추가

---

## 2. 프로젝트 경로 (중요!)

| 구분 | 경로 |
|------|------|
| **실제 작업 경로** | `C:\Users\hsh70\AndroidStudioProjects\MoneyTalk` |
| CWD 경로 (Claude Code) | `C:\Users\hsh70\OneDrive\문서\Android\MoneyTalk` |

> **코드 수정은 반드시 AndroidStudioProjects 경로에서!** OneDrive 경로는 git만 공유.

---

## 3. 빌드 방법

```bash
cmd.exe /c "cd /d C:\Users\hsh70\AndroidStudioProjects\MoneyTalk && .\gradlew.bat assembleDebug"
```

---

## 4. 필수 읽기 순서 (새 에이전트용)

1. **CLAUDE.md** (프로젝트 루트) → 허브, 전체 구조 파악
2. **docs/AI_CONTEXT.md** → 아키텍처, 임계값 레지스트리, 리팩토링 범위
3. **docs/AI_TASKS.md** → 현재 태스크 목록 + 완료 기준
4. **docs/AI_HANDOFF.md** (이 문서) → 현재 진행 상황 + 주의사항

---

## 5. 주의사항

### 절대 금지
- Phase 0 완료 전 리팩토링 코드 건드리지 않기
- DB 스키마 변경 없음
- 임계값 수치 자체를 변경하지 않음 (구조화만)
- 기존 SMS 파싱/분류 로직의 동작 변경 없음

### 알려진 이슈
- `SmsBatchProcessor.kt`의 `GROUPING_SIMILARITY_THRESHOLD = 0.95f`는 `VectorSearchEngine.GROUPING_SIMILARITY_THRESHOLD = 0.88f`과 다름 (의도적: SMS 패턴 그룹핑 vs 가게명 그룹핑)
- 리팩토링 시 두 값을 혼동하지 않도록 별도 SimilarityProfile로 관리 필요

### Git 규칙
- 브랜치 전략: `develop`에서 분기
- 커밋 메시지: 한국어 가능, 간결하게
- 병렬 브랜치 작업 주의 (2026-02-06 브랜치 꼬임 경험)

---

## 6. 최근 완료된 작업 (참고용)

| 날짜 | 작업 | 상태 |
|------|------|------|
| 2026-02-08 | SMS 파싱 버그 3건 수정 (결제예정 제외, 가게명 파싱, 계좌이체 카테고리) | 완료, 빌드 성공 |
| 2026-02-08 | 메모 기능 추가 (지출/수입 편집, 검색/채팅 참조, DB 마이그레이션 v1→v2) | 완료, 빌드 성공 |
| 2026-02-08 | 보험 카테고리 복원 + 홈 화면 삭제 기능 추가 | 완료, 빌드 성공 |
| 2026-02-08 | 수입 상세 다이얼로그 삭제 기능 추가 | 완료, 빌드 성공 |
| 2026-02-08 | 채팅방 나갈 때 대화 기반 자동 타이틀 설정 | 완료, 빌드 성공 |
| 2026-02-08 | 채팅 UI 리팩토링 (방 리스트/내부 분리) | 완료 |
| 2026-02-08 | 수입 내역 통합 표시 (목록 모드) | 완료 |
| 2026-02-08 | 벡터 그룹핑 (Tier 1.5b) + CategoryReferenceProvider + 계좌이체/보험 수정 | 완료 |
| 2026-02-08 | 채팅 액션 5개 추가 (delete_by_keyword, add_expense, update_memo, update_store_name, update_amount) | 완료, 빌드 성공 |
| 2026-02-08 | FINANCIAL_ADVISOR 할루시네이션 방지 규칙 추가 | 완료 |
| 2026-02-08 | SMS 동기화/카테고리 분류 다이얼로그 진행률 표시 개선 | 완료, 빌드 성공 |
| 2026-02-08 | Claude 레거시 코드 완전 제거 (4개 파일 삭제 + Retrofit 의존성 제거) | 완료, 빌드 성공 |
| 2026-02-08 | SmsAnalysisResult core/model/ 분리 + ExpenseRepo/Dao 미사용 메서드 제거 | 완료, 빌드 성공 |

---

## 7. 핵심 파일 위치 (빠른 참조)

### 리팩토링 대상
```
core/util/VectorSearchEngine.kt       ← 임계값 상수 + 벡터 연산 (분리 대상)
core/util/HybridSmsClassifier.kt      ← 3-tier SMS 분류
core/util/SmsBatchProcessor.kt        ← SMS 배치 처리 (로컬 GROUPING 0.95)
core/util/StoreNameGrouper.kt         ← 가게명 시맨틱 그룹핑 (GROUPING 0.88)
feature/home/data/StoreEmbeddingRepository.kt  ← 가게명 벡터 캐시 + 전파
feature/home/data/CategoryClassifierService.kt ← 4-tier 카테고리 분류
```

### 생성된 파일
```
core/similarity/SimilarityPolicy.kt           ← 인터페이스
core/similarity/SimilarityProfile.kt          ← 임계값 데이터 클래스
core/similarity/SmsPatternSimilarityPolicy.kt ← SMS 분류 정책
core/similarity/StoreNameSimilarityPolicy.kt  ← 가게명 매칭 정책
core/similarity/CategoryPropagationPolicy.kt  ← 카테고리 전파 정책
core/util/CategoryReferenceProvider.kt        ← 동적 참조 리스트 (프롬프트 주입)
```
