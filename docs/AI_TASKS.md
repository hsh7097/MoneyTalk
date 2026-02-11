# AI_TASKS.md - 작업 목록 및 완료 기준

> 작업 진행 상황 추적 문서
> **최종 갱신**: 2026-02-11

---

## Phase 0: 컨텍스트 패키징 ✅ 완료

- [x] 기존 문서 5종 읽기
- [x] docs/AI_CONTEXT.md, AI_HANDOFF.md, AI_TASKS.md, CLAUDE.md 생성

---

## Phase 1: VectorSearchEngine 책임 분리 ✅ 완료 (2026-02-08)

- [x] VectorSearchEngine 임계값 상수 5개 제거 → 순수 벡터 엔진
- [x] core/similarity/ 패키지 신설 (5개 파일)
- [x] 모든 호출자에서 SimilarityPolicy 참조로 치환
- [x] confidence < 0.6 전파 차단 정책 추가
- [x] 빌드 성공 확인

---

## 채팅 시스템 확장 ✅ 완료 (2026-02-08~09)

- [x] 채팅 액션 5개 추가 (delete_by_keyword, add_expense, update_memo, update_store_name, update_amount)
- [x] SMS 제외 채팅 액션 2개 추가 (add_sms_exclusion, remove_sms_exclusion)
- [x] ANALYTICS 쿼리 타입 추가 (클라이언트 사이드 필터/그룹핑/집계)
- [x] FINANCIAL_ADVISOR 할루시네이션 방지 규칙 추가
- [x] 프롬프트 XML 이전 (ChatPrompts.kt → string_prompt.xml)

---

## 데이터 관리 기능 ✅ 완료 (2026-02-09)

- [x] OwnedCard 시스템 (카드 화이트리스트 + CardNameNormalizer + DB v2→v3)
- [x] SMS 제외 키워드 시스템 (블랙리스트 + DB v3→v4)
- [x] DB 성능 인덱스 추가 (expenses/incomes + DB v4→v5)
- [x] 전역 스낵바 버스 도입
- [x] API 키 설정 후 저신뢰도 항목 자동 재분류
- [x] SMS 동기화/카테고리 분류 다이얼로그 진행률 표시 개선

---

## UI 공통화 ✅ 완료 (2026-02-11)

- [x] TransactionCardCompose/Info (지출/수입 통합 카드)
- [x] TransactionGroupHeaderCompose/Info (날짜별/가게별/금액별 그룹 헤더)
- [x] SegmentedTabRowCompose/Info (라운드 버튼 탭)
- [x] HistoryScreen Intent 패턴 적용 (HistoryIntent sealed interface)
- [x] Preview 파일 debug/ 소스셋 배치
- [x] 카테고리 아이콘: 벡터 아이콘 교체 시도 → revert, 이모지 유지

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

### 2-D. 채팅에서 카테고리 설정 시 자동 추가
- [ ] 채팅 액션에서 카테고리 변경 시 CategoryReferenceProvider 캐시 자동 무효화
- [ ] 새 매핑 추가 시 벡터 DB에도 반영
