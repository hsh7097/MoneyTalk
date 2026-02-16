# AI_HANDOFF.md - AI 에이전트 인수인계 문서

> AI 에이전트가 교체되거나 세션이 끊겼을 때, 새 에이전트가 즉시 작업을 이어받을 수 있도록 하는 문서
> **최종 갱신**: 2026-02-16

---

## 1. 현재 작업 상태

### 완료된 주요 작업

**Phase 1 리팩토링**: ✅ 완료 (2026-02-08)
- VectorSearchEngine 임계값 상수 제거 → 순수 벡터 엔진
- core/similarity/ 패키지 신설, SimilarityPolicy SSOT 확립

**채팅 시스템 확장**: ✅ 완료 (2026-02-08~09)
- 채팅 액션 확장 (12종), ANALYTICS 쿼리 타입 추가
- FINANCIAL_ADVISOR 할루시네이션 방지 규칙
- 프롬프트 XML 이전 (ChatPrompts.kt → string_prompt.xml)

**데이터 관리 기능**: ✅ 완료 (2026-02-09)
- OwnedCard 시스템 (카드 화이트리스트 + CardNameNormalizer)
- SMS 제외 키워드 시스템 (블랙리스트)
- DB 인덱스 추가 (v4→v5)
- 전역 스낵바 버스 도입
- API 키 설정 후 저신뢰도 항목 자동 재분류

**UI 공통화**: ✅ 완료 (2026-02-11)
- TransactionCard/GroupHeader/SegmentedTabRow 공통 컴포넌트
- HistoryScreen Intent 패턴 적용
- 카테고리 이모지 → 벡터 아이콘 교체 → 원복 (이모지 유지)

**채팅 프롬프트 개선**: ✅ 완료 (2026-02-13)
- Karpathy Guidelines 적용: clarification 루프 + 수치 정확성 규칙
- query_analyzer에 clarification 응답 타입 추가 (모호한 질문 시 확인 질문 반환)
- financial_advisor에 수치 정확성 필수 규칙 추가 (직접 계산/비율/교차 계산 금지)
- DataQueryRequest에 clarification 필드 추가
- ChatViewModel에 clarification 분기 처리

**Phase 2: SMS 분류 정확도/효율 개선**: ✅ 완료 (2026-02-14)
- 2-A: 부트스트랩 모드 게이트 제거 (미사용 상수 정리)
- 2-B: 캐시 재사용 임계값 검토 → 현행 0.97 유지 결정
- 2-C: 벡터 학습 실패 시 스낵바 알림 추가
- 2-D: 채팅 카테고리 변경 시 CategoryReferenceProvider 캐시 자동 무효화

**History 필터 초기화 버튼**: ✅ 완료 (2026-02-14)
- FilterBottomSheet 상단에 조건부 "초기화" 버튼 추가

**추가 개선** (2026-02-14 이후)
- safe-commit 스킬 추가 (.claude/skills/safe-commit/SKILL.md)
- 홈→내역 카테고리 네비게이션 + AI 인사이트 분리
- 달력 뷰 카테고리 필터 적용 버그 수정
- AI 인사이트 프롬프트에 전월 카테고리별 비교 데이터 추가
- 동일 가맹점 카테고리 변경 시 일괄 업데이트
- Compose Stability 최적화 + 릴리스 DB 안전성 개선
- 대형 파일 분할 + Repository 추상화 + 하드코딩 문자열 제거

**안정화/품질 개선**: ✅ 완료 (2026-02-16)
- ClassificationState를 activeJob 기반으로 정리하여 백그라운드 분류 취소/종료 경합 안정화
- 전체 데이터 삭제 이벤트에서 진행 중 분류 작업 즉시 취소 처리
- SmsReader cursor column index 가드 추가로 Lint Range 이슈 해소
- AndroidManifest telephony feature optional 처리로 ChromeOS 관련 lint 이슈 해소
- 문자열 포맷 positional 정리(`history_day_header`) + values-en 누락 키 보강

### 대기 중인 작업

- 현재 대기 중인 필수 작업 없음 (2026-02-16 기준)

---

## 2. 프로젝트 경로 (중요!)

| 구분 | 경로 |
|------|------|
| **Windows** | `C:\Users\hsh70\AndroidStudioProjects\MoneyTalk` |
| **macOS** | `/Users/sanha/Documents/Android/MoneyTalk/MoneyTalk` |

> 코드 수정, git, 빌드는 OS에 맞는 실제 프로젝트 경로에서 수행

---

## 3. 빌드 방법

**Windows**
```bash
cmd.exe /c "cd /d C:\Users\hsh70\AndroidStudioProjects\MoneyTalk && .\gradlew.bat assembleDebug"
```

**macOS**
```bash
./gradlew assembleDebug
```

---

## 4. 필수 읽기 순서 (새 에이전트용)

1. **[CLAUDE.md](../CLAUDE.md)** (프로젝트 루트) → 허브, 전체 구조 파악
2. **[docs/AI_CONTEXT.md](AI_CONTEXT.md)** → 아키텍처, 임계값 레지스트리, 쿼리/액션 전체 목록
3. **[docs/AI_TASKS.md](AI_TASKS.md)** → 현재 태스크 목록 + 완료 기준
4. **[docs/AI_HANDOFF.md](AI_HANDOFF.md)** (이 문서) → 현재 진행 상황 + 주의사항

---

## 5. 주의사항

### 절대 금지
- DB 스키마 변경 시 마이그레이션 필수 (현재 v5)
- 임계값 수치 변경 시 [AI_CONTEXT.md](AI_CONTEXT.md) SSOT 먼저 업데이트
- `!!` non-null assertion 사용 금지

### 알려진 이슈
- `SmsBatchProcessor.kt`의 그룹핑 임계값(0.95)과 `StoreNameGrouper.kt`(0.88)은 의도적으로 다름 (SMS 패턴 vs 가게명)
- ChatViewModel.kt가 대형 파일(~1717줄) — 향후 query/action 로직 분리 후보

### Git 규칙
- 브랜치 전략: `develop`에서 분기
- 커밋 메시지: 한국어 가능, 간결하게
- 병렬 브랜치 작업 주의 (2026-02-06 브랜치 꼬임 경험)

---

## 6. 최근 완료된 작업 (참고용)

| 날짜 | 작업 | 상태 |
|------|------|------|
| 2026-02-16 | 분류 Job 경합 안정화 + lint 이슈 정리 + 문서 동기화 | 완료 |
| 2026-02-15 | 문서 갱신 (ARCHITECTURE, AI_CONTEXT, AI_HANDOFF, PROJECT_CONTEXT 등) | 완료 |
| 2026-02-14~ | safe-commit 스킬, 홈→내역 네비게이션, 달력 필터 버그 수정, AI 인사이트, 가맹점 일괄 업데이트, Compose Stability, 리팩토링 | 완료 |
| 2026-02-14 | Phase 2 전체 완료 + History 필터 초기화 버튼 | 완료 |
| 2026-02-13 | 채팅 프롬프트 Karpathy Guidelines 적용 + Clarification 루프 구현 | 완료 |
| 2026-02-11 | HistoryScreen UI 공통화 + Intent 패턴 적용 | 완료 |
| 2026-02-11 | 카테고리 이모지 → 벡터 아이콘 교체 → revert (이모지 원복) | 완료 |
| 2026-02-09 | ANALYTICS 쿼리 + 채팅 할루시네이션 개선 | 완료 |
| 2026-02-09 | API 키 설정 후 저신뢰도 항목 자동 재분류 | 완료 |
| 2026-02-09 | 전역 스낵바 버스 도입 | 완료 |
| 2026-02-08 | SMS 파싱 버그 3건 수정 | 완료 |
| 2026-02-08 | 메모 기능 추가 (DB v1→v2) | 완료 |
| 2026-02-08 | 보험 카테고리 복원 + 홈 화면 삭제 기능 | 완료 |
| 2026-02-08 | 채팅 UI 리팩토링 (방 리스트/내부 분리) | 완료 |
| 2026-02-08 | 수입 내역 통합 표시 (목록 모드) | 완료 |
| 2026-02-08 | 벡터 그룹핑 (Tier 1.5b) + CategoryReferenceProvider | 완료 |
| 2026-02-08 | 채팅 액션 5개 추가 + SMS 제외 액션 2개 | 완료 |
| 2026-02-08 | FINANCIAL_ADVISOR 할루시네이션 방지 규칙 | 완료 |
| 2026-02-08 | SMS 동기화/카테고리 분류 진행률 표시 개선 | 완료 |
| 2026-02-08 | Claude 레거시 코드 완전 제거 + Retrofit 의존성 제거 | 완료 |
| 2026-02-08 | SmsAnalysisResult core/model/ 분리 | 완료 |

---

## 7. 핵심 파일 위치 (빠른 참조)

### 데이터 레이어

| 파일 | 설명 |
|------|------|
| [`AppDatabase.kt`](../app/src/main/java/com/sanha/moneytalk/core/database/AppDatabase.kt) | Room DB 정의 (v5, 10 entities) |
| [`OwnedCardEntity.kt`](../app/src/main/java/com/sanha/moneytalk/core/database/entity/OwnedCardEntity.kt) | 카드 화이트리스트 Entity |
| [`SmsExclusionKeywordEntity.kt`](../app/src/main/java/com/sanha/moneytalk/core/database/entity/SmsExclusionKeywordEntity.kt) | SMS 제외 키워드 Entity |
| [`OwnedCardRepository.kt`](../app/src/main/java/com/sanha/moneytalk/core/database/OwnedCardRepository.kt) | 카드 관리 + CardNameNormalizer 연동 |
| [`SmsExclusionRepository.kt`](../app/src/main/java/com/sanha/moneytalk/core/database/SmsExclusionRepository.kt) | SMS 제외 키워드 관리 |

### SMS/분류 핵심

| 파일 | 설명 |
|------|------|
| [`HybridSmsClassifier.kt`](../app/src/main/java/com/sanha/moneytalk/core/util/HybridSmsClassifier.kt) | 3-tier SMS 분류 |
| [`SmsBatchProcessor.kt`](../app/src/main/java/com/sanha/moneytalk/core/util/SmsBatchProcessor.kt) | SMS 배치 처리 |
| [`VectorSearchEngine.kt`](../app/src/main/java/com/sanha/moneytalk/core/util/VectorSearchEngine.kt) | 순수 벡터 연산 |
| [`CardNameNormalizer.kt`](../app/src/main/java/com/sanha/moneytalk/core/util/CardNameNormalizer.kt) | 카드명 정규화 |
| [`StoreAliasManager.kt`](../app/src/main/java/com/sanha/moneytalk/core/util/StoreAliasManager.kt) | 가게명 별칭 관리 |
| [`CategoryReferenceProvider.kt`](../app/src/main/java/com/sanha/moneytalk/core/util/CategoryReferenceProvider.kt) | 동적 참조 리스트 |
| [`CategoryClassifierService.kt`](../app/src/main/java/com/sanha/moneytalk/feature/home/data/CategoryClassifierService.kt) | 4-tier 카테고리 분류 |
| [`StoreEmbeddingRepository.kt`](../app/src/main/java/com/sanha/moneytalk/feature/home/data/StoreEmbeddingRepository.kt) | 가게명 벡터 캐시 + 전파 |

### 유사도 정책

| 파일 | 설명 |
|------|------|
| [`SimilarityPolicy.kt`](../app/src/main/java/com/sanha/moneytalk/core/similarity/SimilarityPolicy.kt) | 인터페이스 |
| [`SimilarityProfile.kt`](../app/src/main/java/com/sanha/moneytalk/core/similarity/SimilarityProfile.kt) | 임계값 데이터 클래스 |
| [`SmsPatternSimilarityPolicy.kt`](../app/src/main/java/com/sanha/moneytalk/core/similarity/SmsPatternSimilarityPolicy.kt) | SMS 분류 정책 |
| [`StoreNameSimilarityPolicy.kt`](../app/src/main/java/com/sanha/moneytalk/core/similarity/StoreNameSimilarityPolicy.kt) | 가게명 매칭 정책 |
| [`CategoryPropagationPolicy.kt`](../app/src/main/java/com/sanha/moneytalk/core/similarity/CategoryPropagationPolicy.kt) | 카테고리 전파 정책 |

### AI 채팅

| 파일 | 설명 |
|------|------|
| [`GeminiRepository.kt`](../app/src/main/java/com/sanha/moneytalk/feature/chat/data/GeminiRepository.kt) | Gemini API (3개 모델) |
| [`ChatRepositoryImpl.kt`](../app/src/main/java/com/sanha/moneytalk/feature/chat/data/ChatRepositoryImpl.kt) | 채팅 데이터 + Rolling Summary |
| [`ChatViewModel.kt`](../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatViewModel.kt) | 채팅 UI + 쿼리/액션/분석 실행 |
| [`string_prompt.xml`](../app/src/main/res/values/string_prompt.xml) | 모든 AI 프롬프트 (6종) |

### UI 공통 컴포넌트 (13개 파일)

| 파일 | 설명 |
|------|------|
| [`AppSnackbarBus.kt`](../app/src/main/java/com/sanha/moneytalk/core/ui/AppSnackbarBus.kt) | 전역 스낵바 이벤트 버스 |
| [`ClassificationState.kt`](../app/src/main/java/com/sanha/moneytalk/core/ui/ClassificationState.kt) | 분류 상태 관리 |
| [`CategoryIcon.kt`](../app/src/main/java/com/sanha/moneytalk/core/ui/component/CategoryIcon.kt) | 카테고리 이모지 아이콘 |
| [`ExpenseItemCard.kt`](../app/src/main/java/com/sanha/moneytalk/core/ui/component/ExpenseItemCard.kt) | 지출 항목 카드 |
| [`SettingsItemCompose.kt`](../app/src/main/java/com/sanha/moneytalk/core/ui/component/settings/SettingsItemCompose.kt) | 설정 항목 |
| [`SettingsItemInfo.kt`](../app/src/main/java/com/sanha/moneytalk/core/ui/component/settings/SettingsItemInfo.kt) | 설정 항목 Contract |
| [`SettingsSectionCompose.kt`](../app/src/main/java/com/sanha/moneytalk/core/ui/component/settings/SettingsSectionCompose.kt) | 설정 섹션 |
| [`TransactionCardCompose.kt`](../app/src/main/java/com/sanha/moneytalk/core/ui/component/transaction/card/TransactionCardCompose.kt) | 거래 카드 |
| [`TransactionCardInfo.kt`](../app/src/main/java/com/sanha/moneytalk/core/ui/component/transaction/card/TransactionCardInfo.kt) | 거래 카드 Contract |
| [`TransactionGroupHeaderCompose.kt`](../app/src/main/java/com/sanha/moneytalk/core/ui/component/transaction/header/TransactionGroupHeaderCompose.kt) | 그룹 헤더 |
| [`TransactionGroupHeaderInfo.kt`](../app/src/main/java/com/sanha/moneytalk/core/ui/component/transaction/header/TransactionGroupHeaderInfo.kt) | 그룹 헤더 Contract |
| [`SegmentedTabRowCompose.kt`](../app/src/main/java/com/sanha/moneytalk/core/ui/component/tab/SegmentedTabRowCompose.kt) | 탭 버튼 (아이콘 지원) |
| [`SegmentedTabInfo.kt`](../app/src/main/java/com/sanha/moneytalk/core/ui/component/tab/SegmentedTabInfo.kt) | 탭 Contract |
