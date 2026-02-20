# AI_HANDOFF.md - AI 에이전트 인수인계 문서

> AI 에이전트가 교체되거나 세션이 끊겼을 때, 새 에이전트가 즉시 작업을 이어받을 수 있도록 하는 문서
> **최종 갱신**: 2026-02-20

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

**Google AdMob + Firebase Crashlytics**: ✅ 완료 (2026-02-16~17)
- 리워드 광고 연동 (RewardAdManager, SettingsViewModel 광고 상태 관리)
- Firebase Crashlytics 연동 + Release signingConfig 설정

**알파 배포 준비**: ✅ 완료 (2026-02-18)
- 버전 1.0.0 설정 (versionCode=1, versionName="1.0.0")
- Firebase RTDB 기반 강제 업데이트 시스템 (ForceUpdateChecker + MainActivity 다이얼로그)
- 개인정보처리방침 웹 페이지 (docs/privacy-policy.html, GitHub Pages용)
- ProGuard 규칙 추가 (Hilt/Room/Gson/Firebase/Gemini/AdMob 등)
- GIT_CONVENTION.md에 커밋 본문 템플릿 + Kotlin Android 체크리스트 추가

**Firebase RTDB 원격 설정 마이그레이션**: ✅ 완료 (2026-02-18)
- Gemini API 키 풀링 (geminiApiKeys 배열 + 라운드로빈 분산)
- Gemini 모델명 원격 관리 (GeminiModelConfig 8개 모델, Firebase RTDB `/config/models/`)
- BuildConfig.GEMINI_API_KEY 폐기 → PremiumManager.getGeminiApiKey() 일원화
- 버전 1.1.0 (versionCode=2)

**ProGuard(R8) 활성화 + Firebase Analytics**: ✅ 완료 (2026-02-18)
- R8 minification + resource shrinking 활성화 (릴리스 빌드)
- ProGuard keep 규칙 보강 (Gson 모델 17개, Apache HTTP dontwarn, entity 경로 버그 수정)
- Firebase Analytics 화면 PV 트래킹 (LaunchedEffect 중앙 집중 방식)
- 클릭 이벤트 트래킹 (Home/Chat/History/Settings 4개 ViewModel)
- AnalyticsHelper + AnalyticsEvent 신규 파일, FirebaseModule에 Analytics DI 추가
- Gradle JVM heap 1024m → 2048m (R8 OOM 방지)

**HorizontalPager pageCache + 동기화 제한**: ✅ 완료 (2026-02-19)
- HorizontalPager beyondViewportPageCount=1 기반 월별 독립 페이지 캐시 (Home/History)
- MonthPagerUtils 유틸 (MonthKey, adjacentMonth, isFutureMonth)
- HomePageContent Composable 분리 (HorizontalPager 내부 렌더링)
- SwipeToNavigate 제거 → HorizontalPager 네이티브 스와이프 대체
- DonutChartCompose 애니메이션 제거 + displayLabel 추가
- SMS 100자 초과 필터를 HybridSmsClassifier batchClassify()에도 적용
- 초기 동기화 3개월 제한 + 리워드 광고 시청 후 전체 동기화 해제
- SettingsDataStore FULL_SYNC_UNLOCKED 키, RewardAdManager 전체 동기화 해제 메서드

**UX 개선 + 버그 수정 + CTA**: ✅ 완료 (2026-02-19)
- Android Auto Backup 복원 감지 (DataStore lastSyncTime stale → 0L 리셋)
- DataStore preferences 백업 제외 (backup_rules.xml, data_extraction_rules.xml)
- 홈/내역 탭 재클릭 시 오늘 페이지로 이동 (SharedFlow 이벤트)
- 홈 새로고침 깜빡임 수정 (캐시 클리어 제거 + 조건부 isLoading)
- "오늘 문자만 동기화" 메뉴 제거
- 내역 수입 0원 표시 (조건 제거)
- 빈 상태 "광고 보고 전체 데이터 가져오기" CTA (FullSyncCtaSection 공용 Composable)
- 광고 로드/표시 실패 시 보상 처리 (Home/History/Chat onFailed → 앱 이슈 = 유저 책임 아님)

**SMS 동기화 최적화 + 필터링 강화**: ✅ 완료 (2026-02-19)
- 초기 동기화 3개월 → 2개월 축소 (DEFAULT_SYNC_PERIOD_MILLIS=60일)
- 광고 시청 후 해당 월만 동기화 (syncMonthData + calculateMonthRange)
- 010/070 발신자 조건부 제외 (SmsFilter: normalizeAddress + hasFinancialHints + shouldSkipBySender)
- SMS/MMS/RCS 모든 채널에 발신자 필터 통일 적용
- LLM 트리거 0.80 정책 (벡터 유사도 0.80~0.92 → LLM 호출, 결제 판정 아님)
- Regex 오파싱 방어 (storeName='결제' → Tier 2/3 이관)
- 배치 분류 tier별 관측성 로그 추가
- core/sms 패키지 분리 (SmsParser, SmsReader, HybridSmsClassifier 등 7개 파일 이동)

**SMS 배치 처리 가드레일 + 그룹핑 최적화**: ✅ 완료 (2026-02-19)
- SmsBatchProcessor: 발신번호 기반 2레벨 그룹핑 (37그룹 → 2~4그룹)
- SmsBatchProcessor: LLM 배치 호출 병렬화 (async + Semaphore)
- SmsBatchProcessor: template_regex 신뢰도 1.0 → 0.85 하향
- SmsBatchProcessor: 소그룹 병합 시 코사인 유사도 검증 (≥0.70)
- SmsBatchProcessor: RTDB 업로드 품질 게이트 (검증된 소스만 정규식 포함)
- SmsBatchProcessor: Step1+2 임베딩 통합 (중복 API 호출 제거)
- SmsBatchProcessor: 멤버별 가게명 개별 추출 (대표 가게명 복제 방지)
- SmsEmbeddingService: 가게명 {STORE} 플레이스홀더 치환 추가
- GeminiSmsExtractor: 배치 추출 + 정규식 자동 생성 기능
- GeneratedSmsRegexParser: LLM 생성 정규식 파서 신규 추가
- SmsPatternEntity: amountRegex/storeRegex/cardRegex/parseSource 필드 추가 (DB v5→v6)
- 비결제 키워드 "결제내역" 추가
- 임베딩 차원 문서/주석 768 → 3072 수정

**레거시 FULL_SYNC_UNLOCKED 호환성**: ✅ 완료 (2026-02-19)
- 기존 FULL_SYNC_UNLOCKED=true 사용자가 업데이트 시 CTA가 다시 표시되는 regression 수정
- HomeUiState/HistoryUiState에 isLegacyFullSyncUnlocked 필드 추가
- isMonthSynced()/isPagePartiallyCovered()에서 레거시 전역 해제 상태 체크

**SmsParser KB 출금 유형 확장**: ✅ 완료 (2026-02-19)
- FBS출금 (카드/페이 자동이체), 공동CMS출 (보험 CMS) 지원 추가
- isKbWithdrawalLine() 헬퍼 도입으로 KB 스타일 출금 줄 판별 통합
- 보험 카테고리 키워드 추가 (삼성화, 현대해, 메리츠, DB손해, 한화손해, 흥국화)

**SMS 통합 파이프라인 (sms2) 마이그레이션**: ✅ 완료 (2026-02-19~20)
- core/sms2/ 패키지에 통합 파이프라인 11개 파일 (6개 골격 + 4개 신규 + GeminiSmsExtractor 이전)
- SmsReaderV2.kt: SMS/MMS/RCS 통합 읽기 → List<SmsInput> 직접 반환 (SmsMessage 중간 변환 제거)
- SmsIncomeParser.kt: 수입 SMS 파싱 (extractIncomeAmount/Type/Source/DateTime)
- SmsSyncCoordinator.kt: 유일한 외부 진입점 (process → SmsPreFilter → SmsIncomeFilter → SmsPipeline)
- SmsIncomeFilter.kt: PAYMENT/INCOME/SKIP 3분류 (financialKeywords 46개 기반)
- HomeViewModel: syncSmsMessages() 삭제 → syncSmsV2() 오케스트레이터 (5개 private 메소드)
- syncIncremental() + calculateIncrementalRange() 추가
- SmsBatchProcessor DI 제거, launchBackgroundHybridClassification() 삭제
- core/sms (V1)은 SmsProcessingService 실시간 수신 전용으로 유지

**DB 메인 그룹 패턴 저장 + 메인 regex 선조회**: ✅ 완료 (2026-02-20)
- SmsPatternEntity에 `isMainGroup: Boolean` 필드 추가 (DB v2→v3 마이그레이션)
- SmsPatternDao에 `getMainPatternBySender()` 쿼리 추가
- SmsGroupClassifier: Step 5 진입 시 DB에서 발신번호별 메인 패턴 선조회 → dbMainContext 구성
- 메인 그룹 processGroup() 호출 시 isMainGroup=true → DB에 메인 표시
- 예외 그룹 regex 생성 시 MainRegexContext로 메인 regex 참조 전달
- senderAddress normalizeAddress() 적용 (registerPaymentPattern/registerNonPaymentPattern)
- GeminiSmsExtractor: LLM 프롬프트 개선 (샘플 1~5건, 메인 regex 참조, 프롬프트 XML 이전)

### 대기 중인 작업

- `feature/proguard-analytics` 브랜치 PR 생성 및 develop 머지
- GitHub Pages 설정 (Settings → Pages → `/docs` 디렉토리) — 개인정보처리방침 URL 활성화용
- Google Play Console 알파 트랙 AAB 업로드 + SMS 권한 선언 양식 제출

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
- DB 스키마 변경 시 마이그레이션 필수 (현재 AppDatabase v3, sms_patterns v3)
- 임계값 수치 변경 시 [AI_CONTEXT.md](AI_CONTEXT.md) SSOT 먼저 업데이트
- `!!` non-null assertion 사용 금지

### 알려진 이슈
- `SmsGroupClassifier.kt`(sms2)의 그룹핑 임계값(0.95)과 `StoreNameGrouper.kt`(0.88)은 의도적으로 다름 (SMS 패턴 vs 가게명)
- ChatViewModel.kt가 대형 파일(~1717줄) — 향후 query/action 로직 분리 후보
- core/sms(V1)은 SmsProcessingService 실시간 수신에서만 사용, 배치 동기화는 sms2로 완전 전환

### Git 규칙
- 커밋/푸시/PR/브랜치 규칙 SSOT: [GIT_CONVENTION.md](GIT_CONVENTION.md)
- 병렬 브랜치 작업 주의 (2026-02-06 브랜치 꼬임 경험)

---

## 6. 최근 완료된 작업 (참고용)

| 날짜 | 작업 | 상태 |
|------|------|------|
| 2026-02-20 | DB 메인 그룹 패턴 저장 + Step 5 메인 regex 선조회 (isMainGroup, getMainPatternBySender, MainRegexContext) | 완료 |
| 2026-02-20 | sms2 마이그레이션 완료: SmsReaderV2/SmsIncomeParser/SmsSyncCoordinator/SmsIncomeFilter 신규 + syncSmsV2 오케스트레이터 + syncSmsMessages 삭제 | 완료 |
| 2026-02-19 | SMS 통합 파이프라인 sms2 패키지 6개 파일 생성 (SmsPipeline, SmsPatternMatcher 등) | 완료 |
| 2026-02-19 | SmsParser KB 출금 유형 확장 (FBS출금, 공동CMS출) + 보험 카테고리 키워드 | 완료 |
| 2026-02-19 | 레거시 FULL_SYNC_UNLOCKED 사용자 월별 동기화 호환성 수정 | 완료 |
| 2026-02-19 | SMS 배치 가드레일 + 그룹핑 최적화 + LLM 병렬화 + GeneratedSmsRegexParser 신규 | 완료 |
| 2026-02-19 | SMS 동기화 최적화 (2개월 축소 + 월별 동기화 + 발신자 필터 + LLM 0.80 트리거 + 오파싱 방어 + core/sms 패키지 분리) | 완료 |
| 2026-02-19 | 빈 상태 CTA + 광고 실패 보상 + 탭 재클릭 + Auto Backup 수정 + 깜빡임 수정 | 완료 |
| 2026-02-19 | HorizontalPager pageCache + 3개월 동기화 제한 + 리워드 광고 전체 해제 | 완료 |
| 2026-02-19 | SMS 100자 초과 필터 HybridSmsClassifier 적용 | 완료 |
| 2026-02-18 | ProGuard(R8) 활성화 + Firebase Analytics PV/클릭 트래킹 | 완료 |
| 2026-02-18 | Firebase RTDB 원격 설정 (API 키 풀링 + 모델명 관리) + 버전 1.1.0 | 완료 |
| 2026-02-18 | 알파 배포 준비 (강제 업데이트, 개인정보처리방침, ProGuard, 커밋 가이드) | 완료 |
| 2026-02-17 | Google AdMob 리워드 광고 + Firebase Crashlytics + Release 서명 | 완료 |
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

### Firebase / 서버 설정

| 파일 | 설명 |
|------|------|
| [`PremiumManager.kt`](../app/src/main/java/com/sanha/moneytalk/core/firebase/PremiumManager.kt) | Firebase RTDB 설정 실시간 감시 |
| [`PremiumConfig.kt`](../app/src/main/java/com/sanha/moneytalk/core/firebase/PremiumConfig.kt) | 서버 설정 data class (9개 필드) |
| [`ForceUpdateChecker.kt`](../app/src/main/java/com/sanha/moneytalk/core/firebase/ForceUpdateChecker.kt) | versionCode 비교 기반 강제 업데이트 판정 |
| [`CrashlyticsHelper.kt`](../app/src/main/java/com/sanha/moneytalk/core/firebase/CrashlyticsHelper.kt) | Crashlytics 래퍼 |
| [`AnalyticsHelper.kt`](../app/src/main/java/com/sanha/moneytalk/core/firebase/AnalyticsHelper.kt) | Firebase Analytics 래퍼 (@Singleton) |
| [`AnalyticsEvent.kt`](../app/src/main/java/com/sanha/moneytalk/core/firebase/AnalyticsEvent.kt) | 화면/클릭 이벤트 상수 |

### 데이터 레이어

| 파일 | 설명 |
|------|------|
| [`AppDatabase.kt`](../app/src/main/java/com/sanha/moneytalk/core/database/AppDatabase.kt) | Room DB 정의 (v6, 10 entities) |
| [`OwnedCardEntity.kt`](../app/src/main/java/com/sanha/moneytalk/core/database/entity/OwnedCardEntity.kt) | 카드 화이트리스트 Entity |
| [`SmsExclusionKeywordEntity.kt`](../app/src/main/java/com/sanha/moneytalk/core/database/entity/SmsExclusionKeywordEntity.kt) | SMS 제외 키워드 Entity |
| [`OwnedCardRepository.kt`](../app/src/main/java/com/sanha/moneytalk/core/database/OwnedCardRepository.kt) | 카드 관리 + CardNameNormalizer 연동 |
| [`SmsExclusionRepository.kt`](../app/src/main/java/com/sanha/moneytalk/core/database/SmsExclusionRepository.kt) | SMS 제외 키워드 관리 |

### SMS/분류 핵심

| 파일 | 설명 |
|------|------|
| [`HybridSmsClassifier.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/HybridSmsClassifier.kt) | 3-tier SMS 분류 |
| [`SmsBatchProcessor.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsBatchProcessor.kt) | SMS 배치 처리 |
| [`VectorSearchEngine.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/VectorSearchEngine.kt) | 순수 벡터 연산 |
| [`SmsFilter.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsFilter.kt) | 010/070 발신자 조건부 제외 |
| [`SmsReader.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsReader.kt) | SMS/MMS/RCS 읽기 |
| [`SmsParser.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsParser.kt) | SMS 정규식 파싱 |
| [`GeminiSmsExtractor.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/GeminiSmsExtractor.kt) | LLM 배치 추출 + 정규식 생성 |
| [`GeneratedSmsRegexParser.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/GeneratedSmsRegexParser.kt) | LLM 생성 정규식 파서 |
| [`SmsEmbeddingService.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsEmbeddingService.kt) | SMS 템플릿화 + 임베딩 생성 |
| [`CardNameNormalizer.kt`](../app/src/main/java/com/sanha/moneytalk/core/util/CardNameNormalizer.kt) | 카드명 정규화 |
| [`StoreAliasManager.kt`](../app/src/main/java/com/sanha/moneytalk/core/util/StoreAliasManager.kt) | 가게명 별칭 관리 |
| [`CategoryReferenceProvider.kt`](../app/src/main/java/com/sanha/moneytalk/core/util/CategoryReferenceProvider.kt) | 동적 참조 리스트 |
| [`CategoryClassifierService.kt`](../app/src/main/java/com/sanha/moneytalk/feature/home/data/CategoryClassifierService.kt) | 4-tier 카테고리 분류 |
| [`StoreEmbeddingRepository.kt`](../app/src/main/java/com/sanha/moneytalk/feature/home/data/StoreEmbeddingRepository.kt) | 가게명 벡터 캐시 + 전파 |

### SMS 통합 파이프라인 (sms2)

| 파일 | 설명 |
|------|------|
| [`SmsSyncCoordinator.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsSyncCoordinator.kt) | 유일한 외부 진입점 (process → PreFilter → IncomeFilter → Pipeline) |
| [`SmsReaderV2.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsReaderV2.kt) | SMS/MMS/RCS 통합 읽기 → List\<SmsInput\> 직접 반환 |
| [`SmsIncomeFilter.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsIncomeFilter.kt) | PAYMENT/INCOME/SKIP 3분류 (financialKeywords 46개) |
| [`SmsIncomeParser.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsIncomeParser.kt) | 수입 SMS 파싱 (금액/유형/출처/날짜시간) |
| [`SmsPipeline.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsPipeline.kt) | 오케스트레이터 (Step 2→3→4→5) |
| [`SmsPipelineModels.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsPipelineModels.kt) | 데이터 클래스 (SmsInput, EmbeddedSms, SmsParseResult, SyncResult) |
| [`SmsPreFilter.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsPreFilter.kt) | Step 2: 사전 필터링 (키워드 + 구조) |
| [`SmsTemplateEngine.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsTemplateEngine.kt) | Step 3: 템플릿화 + Gemini Embedding API |
| [`SmsPatternMatcher.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsPatternMatcher.kt) | Step 4: 벡터 매칭 + regex 파싱 |
| [`SmsGroupClassifier.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsGroupClassifier.kt) | Step 5: 그룹핑 + LLM + regex 생성 |

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
| [`MonthPagerUtils.kt`](../app/src/main/java/com/sanha/moneytalk/core/ui/component/MonthPagerUtils.kt) | HorizontalPager 월별 유틸 (MonthKey, adjacentMonth) |
| [`FullSyncCtaSection.kt`](../app/src/main/java/com/sanha/moneytalk/core/ui/component/FullSyncCtaSection.kt) | 전체 동기화 해제 CTA (빈 상태 공용) |
