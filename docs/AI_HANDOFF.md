# AI_HANDOFF.md - AI 에이전트 인수인계 문서

> AI 에이전트가 교체되거나 세션이 끊겼을 때, 새 에이전트가 즉시 작업을 이어받을 수 있도록 하는 문서
> **최종 갱신**: 2026-03-03 (SMS regex Fast Path 전환 완료)

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

**LLM 생성 regex 샘플 검증**: ✅ 완료 (2026-02-20)
- SmsGroupClassifier: regex 생성 후 `validateRegexAgainstSamples()`로 샘플 SMS에 실제 적용 검증
- 50%+ 파싱 성공 시만 `llm_regex`로 채택, 실패 시 템플릿 폴백 + 쿨다운 기록
- `REGEX_VALIDATION_MIN_PASS_RATIO = 0.50` 상수 추가

**DB 메인 그룹 패턴 저장 + 메인 regex 선조회**: ✅ 완료 (2026-02-20)
- SmsPatternEntity에 `isMainGroup: Boolean` 필드 추가 (DB v2→v3 마이그레이션)
- SmsPatternDao에 `getMainPatternBySender()` 쿼리 추가
- SmsGroupClassifier: Step 5 진입 시 DB에서 발신번호별 메인 패턴 선조회 → dbMainContext 구성
- 메인 그룹 processGroup() 호출 시 isMainGroup=true → DB에 메인 표시
- 예외 그룹 regex 생성 시 MainRegexContext로 메인 regex 참조 전달
- senderAddress normalizeAddress() 적용 (registerPaymentPattern/registerNonPaymentPattern)
- GeminiSmsExtractor: LLM 프롬프트 개선 (샘플 1~5건, 메인 regex 참조, 프롬프트 XML 이전)

**RTDB 원격 regex 룰 매칭 시스템**: ✅ 완료 (2026-02-20)
- `RemoteSmsRule.kt`: 원격 regex 룰 데이터 클래스 (ruleId, embedding, regex 3종, minSimilarity=0.94)
- `RemoteSmsRuleRepository.kt`: RTDB 룰 로드 + 메모리 캐시(TTL 10분) + sender별 그룹핑
- `SmsPatternMatcher.kt`: 로컬 패턴 미매칭 시 2순위 원격 룰 매칭 + 로컬 DB 승격 (parseSource="remote_rule")
- `SmsGroupClassifier.kt`: RTDB 표본 수집 필드 정리 (불필요 필드 제거 + 주석 추가 + embedding/normalizedSenderAddress 포함)
- sms2 파일 수 10→12개 (RemoteSmsRule, RemoteSmsRuleRepository 추가)

**PR #23 코드 리뷰 반영 + UI 버그 수정**: ✅ 완료 (2026-02-21)
- RemoteSmsRuleRepository: 빈 룰 결과도 TTL 캐시 (cachedRules nullable 전환)
- SmsPatternMatcher: 파싱 실패 시 차순위 룰 순차 시도 + 중복 승격 방지 (promotedRuleIds)
- SmsGroupClassifier: RTDB 표본 수집 디버그 로그 강화
- MainActivity: NavigationBar windowInsets 분리로 하단 탭 64dp 보장 + 탭 전환 시 초기화
- HistoryFilter: 카테고리 그리드 weight 기반으로 변경 → 큰 글꼴에서 적용 버튼 항상 노출

**누적 추이 차트 UX 대폭 개선**: ✅ 완료 (2026-02-21)
- 범례: 체크박스 → 채워진/테두리 원형 토글
- X축: 6등분 균등 분할 (실제 말일 기준) + 스트레칭 위치 매칭
- Y축: 토글 무관 고정 + 200만 단위 올림 + 5등분
- 0원 시작점: 모든 곡선이 0에서 시작 (daysInMonth+1개 포인트)
- 6개월 평균 곡선 추가 (N개월 전체 데이터 존재 시만 표시)
- 누적 커브 하락 방지 (짧은 월 carry-forward)
- History/Chat → Home 차트 DataRefreshEvent 동기화
- PR #24 리뷰: onCategorySelected 핸들러 복원

**카테고리 상세 화면 (CategoryDetailActivity)**: ✅ 완료 (2026-02-21)
- 신규 Activity: 카테고리별 월간 지출 추이 차트 + 거래 내역 리스트
- 홈 카테고리 리스트에서 클릭 시 진입 (Intent 기반, NavGraph 외부)
- CumulativeTrendSection 재사용 (당월 vs 전월 비교 곡선)
- 다크 모드 완전 지원 (차트 색상 surfaceVariant 기반)

**Budget BottomSheet**: ✅ 완료 (2026-02-21)
- BudgetBottomSheet.kt 신규: 전체 예산 + 카테고리별(18개) 예산 일괄 설정
- 전체/카테고리 독립 설정 가능, % 자동 계산 표시
- HistoryFilter.kt: 동일 패턴 적용 (100dp 상단 마진 + 고정 하단 버튼 + FlowRow)
- SettingsViewModel: SaveBudgets Intent + saveBudgets() 일괄 저장
- BudgetDao: getBudgetsByMonthOnce suspend 함수 추가

**SMS 증분 동기화 5분 오버랩**: ✅ 완료 (2026-02-21)
- 증분 동기화 시 lastSyncTime - 5분 오버랩으로 경계 SMS 누락 방지

**데이터 삭제 시 광고 동기화 초기화 + resume silent 동기화**: ✅ 완료 (2026-02-22)
- SettingsDataStore.clearSyncedMonths(): SYNCED_MONTHS + FULL_SYNC_UNLOCKED 동시 초기화
- deleteAllData() 연동 (SettingsViewModel)
- HomeViewModel.refreshData(): SMS 권한 확인 후 silent syncSmsV2 자동 호출
- silent 모드: 애널리틱스 이벤트 스킵 + 진행 UI 미표시

**예산 관리 확장 (홈 UI + AI 채팅 연동)**: ✅ 완료 (2026-02-22)
- 홈 카테고리 리스트: 예산 대비 사용률 프로그레스바 + 잔여/초과 표시 + 초과 시 빨간색 강조
- AI 채팅 예산 조회: QueryType.BUDGET_STATUS + executeBudgetStatusQuery()
- AI 채팅 예산 설정: ActionType.SET_BUDGET + DataAction.category + executeSetBudget()
- 프롬프트 갱신: budget_status 쿼리 + set_budget 액션 + 분석 규칙 + 패턴 예시

**홈 화면 Phase1 리디자인 + 디자인 시스템**: ✅ 완료 (2026-02-24)
- 디자인 시스템 정립 (Color 팔레트 4색 체계, Typography 스케일 9단계, Dimens 여백 체계)
- MonthlyOverviewSection Hero 카드 리디자인 (큰 숫자 강조, 전월 비교 뱃지)
- Vico 라이브러리 도입 — 누적 차트를 금융앱 스타일로 교체 (VicoCumulativeChart)
- SpendingTrendInfo Contract 확장 (금액, 비교문구, 초과여부 필드)
- 다크 테마 Green/Orange 기반으로 복원
- DESIGN_PLAN.md 리디자인 계획서 작성

**Vico 차트 수정 + 홈 UI 간소화**: ✅ 완료 (2026-02-24)
- Vico 차트 가로 스크롤 제거 (Zoom.Content + AxisValueOverrider.fixed maxX)
- X축 라벨 표시 수정 (ItemPlacer spacing=5, addExtremeLabelPadding=true)
- 동기화 후 차트 업데이트 안되는 이슈 수정 (LaunchedEffect keys에 daysInMonth, todayDayIndex 추가)
- Y축 토글 무관 고정 (yAxisMax를 전체 toggleableLines 기준으로 CumulativeTrendSection에서 계산)
- 1월 CTA 잘못 표시 수정 (isMonthSynced → isPagePartiallyCovered 전환)
- TodayAndComparisonSection 제거 → 오늘 지출 정보를 "오늘 내역" 헤더에 통합
- FullSyncCtaSection 홈에서 제거 (HistoryScreen에서는 유지)
- buildComparisonText 형식 변경: "N% 더 쓰고 있어요" → "₩금액(N%) 더 쓰고 있어요"
- Auto Backup 복원 규칙 수정 (backup_rules.xml, data_extraction_rules.xml)

**Activity-scoped MainViewModel 도입**: ✅ 완료 (2026-02-25)
- 동기화/권한/광고 통합 관리를 MainActivity-scoped ViewModel로 이관
- PR #33 생성 + Codex 리뷰 P2 확인 (SMS 권한 없을 때 resume 시 DataRefreshEvent 미발행)
- 결정: 현재 상태 유지 — SMS 권한 없는 사용자의 편집 화면은 자체 refresh 이벤트 발행하므로 실질적 문제 낮음
- 매 진입 시 권한 재요청 팝업 검토 → 기각 (UX 피로감 + Google Play 정책 위반 가능성)

**임베딩 차원 축소 (3072 → 768)**: ✅ 완료 (2026-02-25)
- Gemini Embedding API `outputDimensionality=768` 설정 (SmsEmbeddingService, SmsTemplateEngine)
- Matryoshka Representation Learning 활용 — MTEB 벤치마크 기준 품질 손실 0.26%
- DB v3→v4 마이그레이션 (기존 3072 차원 sms_patterns, store_embeddings 삭제)
- 코드 주석(8개 파일) + 문서(6개 md) 3072→768 갱신
- 효과: 임베딩 저장/전송 크기 75% 감소, 코사인 유사도 연산 75% 감소

**SmsGroupClassifier 품질 개선**: ✅ 완료 (2026-02-26)
- 소그룹 병합 유사도 0.70→0.90 상향 (서로 다른 SMS 형식 과도 병합 방지)
- RTDB 표본 수집: template 필드 추가 + regex 존재 시만 수집 (amountRegex+storeRegex 비어있는 무용 표본 제거)
- LLM 프롬프트 개선: "예외 케이스" → "메인 케이스 참조" (같은 발신번호 메인 형식 참고하여 분석)
- MainCaseContext.samples: 단일 sample → 3건 리스트 전달 (LLM 분석 정확도 향상)
- buildContextualLlmInput: 메인 템플릿 + 메인 샘플 3건 + 분포도 요약을 LLM에 전달
- 로그 추가: GeminiSmsExtractor에 프롬프트 디버깅 로그

**SMS 파싱 파이프라인 순서/효율 개선**: ✅ 완료 (2026-02-26)
- SmsPreFilter 수입 보호 화이트리스트 추가 (INCOME_PROTECTION_KEYWORDS)
- SmsIncomeFilter classify() 순서 수정 (incomeExcludeKeywords → paymentKeywords)
- SmsIncomeParser 취소/환불 분기 추가 (extractIncomeType → "환불")
- SmsPatternMatcher parseWithRegex() 미사용 fallbackCategory 파라미터 제거
- SmsIncomeFilter/SmsPreFilter 중복 코드 방어용 주석 추가

**generateRegexForGroup 정규식 생성 성공률 개선**: ✅ 완료 (2026-02-26)
- repair 1회 활성화 (REGEX_REPAIR_MAX_RETRIES 0→1)
- 검증 일원화: Extractor에서 샘플 성공률 게이트 제거 → Classifier.validateRegexAgainstSamples() 1곳에서만 판정
- 프롬프트 개선: JSON escape 규칙 명시, 멀티라인 SMS 예시 추가, 날짜 prefix 제거, 샘플 수 1~5→1~10
- ultraCompact 3차 폴백 활성화 (primary → compact → ultraCompact)
- compact 프롬프트에 cardRegex 힌트 추가
- repair 프롬프트에 인간 친화적 실패 사유 매핑 (toHumanFriendlyReason)
- 미사용 코드 정리 (REGEX_MIN_AMOUNT, NON_DIGIT_PATTERN, STORE_* 패턴, isValidRegexStoreName, tryExtractGroup1, RegexValidationResult.successRatio)
- 하드코딩 문자열 리소스화 (strings.xml + values-en/strings.xml)

**SMS Step5 성능 최적화 + Step4.5 배치 LLM 복구 경로**: ✅ 완료 (2026-02-27)
- PR #38 (`optimize/sms-step5-performance` → `develop`) 머지 완료
- **Step5 성능 최적화** (커밋 b339245):
  - GeminiSmsExtractor: LLM 타임아웃 60초, CancellationException 조기 캐치, isPayment=false 스킵, 디버그 로깅
  - SmsGroupClassifier: REGEX_MIN_SAMPLES=5, REGEX_TIME_BUDGET_MS=15초, REGEX_NEAR_MISS_MIN_RATIO=0.4f, dead code 제거
  - SmsPipeline: Step5 진행률 콜백 연동
- **Step4.5 regex 실패건 배치 LLM 복구** (커밋 43de46e):
  - SmsPatternMatcher: `MatchResult` 3-way 분할 (matched/regexFailed/unmatched)
  - SmsGroupClassifier: `batchExtractRegexFailed()` — 발신번호별 그룹 + chunk(10) + 배치 LLM 추출
  - SmsPipeline: Step4→Step4.5→Step5 체인, `PipelineResult.regexFailedRecoveredCount`
  - SmsPipelineModels: `SyncStats.regexFailedRecoveredCount`
  - SmsSyncCoordinator/MainViewModel: stats 매핑
- **근본 원인**: KB출금 26건이 벡터매칭 OK + regex 파싱 실패(STORE_INVALID_KEYWORDS에 "출금","카드") → 매 동기화 25초 Step5 무한 루프 → Step4.5에서 ~3초 처리
- CLAUDE.md에 MoneyTalkLogger 로깅 규칙 추가 (커밋 b745945)

**PR #40: Step5 SMS 파싱 정책/예산/학습 분리 고도화**: ✅ 완료 (2026-02-27)
- PR #40 (`optimize/step5-phase1` → `develop`) 머지 완료, 7 커밋, SmsGroupClassifier.kt 단일 파일
- **Phase1**: regex 정책 강화 (REGEX_MIN_SAMPLES=5, near-miss 재시도), 그룹별/전체 시간 예산 (GROUP_TIME_BUDGET_MS=10초, STEP5_TIME_BUDGET_MS=20초), 계측 로그
- **Phase2**: 예산 초과 시 스킵→강등 정책 (regex 생성 포기하고 LLM 직접 추출로 전환, 데이터 누락 방지)
- **Phase3**: unstable 그룹 정책 (중앙값 0.92/P20 0.88 미달 시 그룹 해체→개별 LLM), cohesion gate, E-lite 모델 분리
- **Phase4**: 백그라운드 학습 큐 (DeferredLearningTask, learningQueue, LEARNING_QUEUE_MAX_SIZE=1000, dedup)
- **Phase5**: outcome 집계 로그 + 학습 큐 in-flight dedup 중복 방지

**PR #41: Step4.5 복구 경로 최적화 및 프롬프트 안정화**: ✅ 완료 (2026-02-27)
- PR #41 (`codex/step4-5-optimize` → `develop`) 머지 완료, 9 커밋, 9 파일
- **Step4.5 병렬화 + 품질 게이트**: Semaphore(LLM_CONCURRENCY) + async/awaitAll, isRecoverableRegexFailedSms() 복구 가능성 판정, isGroundedRegexFailedRecovery() 근거 검증, 실패 쿨다운 (sender:templateHash, 2회→30분)
- **KB 멀티라인 출금 휴리스틱**: tryParseKbDebitFallback() + tryHeuristicParse() 헬퍼 추출 (4곳 중복 제거)
- **Step5 경로 분리**: 5-A (unmatched, full regex policy) vs 5-B (regexFailedFallback, forceSkipRegexAll=true)
- **프롬프트 강화**: 근거 부족 문자 처리 규칙(rules 7-10), SMS_EXTRACT_PROMPT_VERSION/SMS_BATCH_PROMPT_VERSION 로깅
- **누락 SMS 드롭 경로 진단**: SmsPipeline에서 parsedIds vs embedded 차집합 추적 로그
- **Home insight 재시도**: HOME_INSIGHT_MAX_ATTEMPTS=3, 503/UNAVAILABLE 시 선형 백오프
- **TransactionCard 메모 표시**: memoText 필드 + buildAnnotatedString (alpha=0.72f)
- **상수 추출**: DEBIT_FALLBACK_STORE_NAME (SmsPatternMatcher), outcome 코드 REGEX_FAILED_HEURISTIC_KB 구분

**SMS Regex Fast Path 전환 (sms2)**: ✅ 완료 (2026-03-03)
- `sms_regex_rules` 테이블 추가 (DB v6→v7, 복합PK: senderAddress+type+ruleKey)
- 3-tier 파싱: Step 1.5 sender regex Fast Path → Vector → LLM
- Asset seed (`sms_rules_v1.json`, 8 sender 16룰) + RTDB overlay
- `SmsRegexRuleMatcher`: 매칭 통계 자동 갱신, 저품질 룰 자동 비활성화, sender/type별 5개 상한
- `SmsOriginSampleCollector`: 성공/실패 표본 분리 수집 (SHA-256 결정적 키)
- Fallback 안전 연결: `distinctBy { input.id }` 중복 제거, SyncStats 분리 지표
- `docs/SMS_RULE_JSON_UPDATE_GUIDE.md` 운영 가이드 추가
- 상세: `docs/TEMP_SMS_REGEX_RULE_MIGRATION_PLAN.md` (Phase 0~6 + 추가 작업 완료)

**v1.1.0 출시 준비**: 🔄 진행 중 (2026-03-01)
- **targetSdk 34→35 (API 35 마이그레이션)**: compileSdk/targetSdk 35, AGP 8.2.2→8.7.3, Gradle 8.5→8.9
  - 모든 Activity에 `enableEdgeToEdge()` 적용 확인됨
  - Theme.kt statusBarColor에 `@Suppress("DEPRECATION")` 추가 (API 35에서 deprecated)
  - OnboardingScreen/ChatScreen의 deprecated 패딩 함수는 기술 부채로 유지 (동작에 문제 없음)
- **AdMob 테스트→실제 ID 전환**: APPLICATION_ID, 리워드 광고, 배너 광고(HOME/HISTORY/CATEGORY_DETAIL) 3화면
  - BannerAdCompose에 adUnitId 파라미터 추가, BannerAdIds object로 화면별 ID 관리
  - AD_SERVICES_CONFIG property 추가 (AdMob+Firebase Analytics 충돌 해결)
- **STT/RECORD_AUDIO 완전 제거**: Play Console 권한 플래그 대응
  - ChatScreen.kt에서 ~150줄 삭제 (SpeechRecognizer, RecognitionListener, 음성 버튼)
  - ChatViewModel.kt에서 showVoiceHint/observeVoiceHintSeen/markVoiceHintSeen 제거
  - SettingsDataStore에서 CHAT_VOICE_HINT_SEEN 키 제거
  - strings.xml(ko/en)에서 STT 관련 9개 문자열 제거
- **CTA 1월 버그 수정**: HomePageData/HistoryPageData 캐시 미적재 시 isLoading=false fallback (CTA 즉시 평가)
- **ForceUpdateDialog Predictive Back 방어**: DialogProperties(dismissOnBackPress=false) 추가
- **문자열 수정**: "최근 14일간"→"최근 2개월간" (engine_summary_sms_analyzed)
- **versionCode 2→3**: 릴리스 빌드 준비

### 대기 중인 작업

- 위 변경사항 커밋 + 릴리스 빌드(assembleRelease) 검증
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
- DB 스키마 변경 시 마이그레이션 필수 (현재 AppDatabase v7, sms_patterns v3)
- 임계값 수치 변경 시 [AI_CONTEXT.md](AI_CONTEXT.md) SSOT 먼저 업데이트
- `!!` non-null assertion 사용 금지

### 알려진 이슈
- `SmsGroupClassifier.kt`(sms2)의 그룹핑 임계값(0.95)과 소그룹 병합(0.90), `StoreNameGrouper.kt`(0.88)은 의도적으로 다름 (SMS 패턴 vs 가게명)
- ChatViewModel.kt가 대형 파일(~1717줄) — 향후 query/action 로직 분리 후보
- core/sms(V1)은 SmsProcessingService 실시간 수신에서만 사용, 배치 동기화는 sms2로 완전 전환

### Git 규칙
- 커밋/푸시/PR/브랜치 규칙 SSOT: [GIT_CONVENTION.md](GIT_CONVENTION.md)
- 병렬 브랜치 작업 주의 (2026-02-06 브랜치 꼬임 경험)

---

## 6. 최근 완료된 작업 (참고용)

| 날짜 | 작업 | 상태 |
|------|------|------|
| 2026-03-03 | SMS Regex Fast Path 전환: DB v7(sms_regex_rules), Step1.5 sender regex, Asset seed+RTDB overlay, 표본 수집, 룰 자동 최적화, 운영 가이드 — Phase 0~6 + 추가 작업 완료 | 완료 |
| 2026-03-01 | v1.1.0 출시 준비: targetSdk 35, AdMob 실제 ID, STT/RECORD_AUDIO 제거, CTA 버그 수정, ForceUpdateDialog 백키 방어, versionCode 3 | 진행 중 |
| 2026-02-27 | PR #42: 문자설정 Activity + 수신거부 번호 관리 + 영어 번역 보강 | 완료 |
| 2026-02-27 | PR #41: Step4.5 복구 경로 최적화 — 병렬화+품질게이트, KB 휴리스틱, Step5 경로 분리(5-A/5-B), 프롬프트 강화, insight 재시도, 메모 표시, 상수 정리 | 완료 |
| 2026-02-27 | PR #40: Step5 정책/예산/학습 고도화 — 그룹/전체 시간 예산, 스킵→강등, unstable 그룹 해체, cohesion gate, 백그라운드 학습 큐+dedup | 완료 |
| 2026-02-27 | SMS Step5 성능 최적화 + Step4.5 배치 LLM 복구 경로 — KB출금 regex 실패 무한 루프 해결, MatchResult 3-way 분할, batchExtractRegexFailed, PR #38 머지 | 완료 |
| 2026-02-26 | generateRegexForGroup 정규식 생성 성공률 개선 — repair 1회 활성, 검증 일원화, 프롬프트 개선(JSON escape/멀티라인/날짜제거), ultraCompact 3차 폴백, 미사용 코드 정리 | 완료 |
| 2026-02-26 | SmsGroupClassifier 품질 개선 — 소그룹 병합 0.70→0.90, RTDB 표본에 template 추가, LLM 프롬프트 메인 케이스 참조, MainCaseContext 3건 샘플 | 완료 |
| 2026-02-25 | 임베딩 차원 3072→768 축소 — outputDimensionality=768 설정, DB v3→v4 마이그레이션, 코드/문서 16개 파일 갱신 | 완료 |
| 2026-02-25 | PR #33 MainViewModel 리팩토링 — Codex P2 리뷰 확인, 권한 없을 때 resume DataRefreshEvent 미발행 지적 → 현행 유지 결정 | 완료 |
| 2026-02-24 | Vico 차트 수정(스크롤/X축/Y축/동기화) + 홈 UI 간소화(TodayAndComparisonSection·FullSyncCtaSection 제거, 비교문구 가격+% 형식) | 완료 |
| 2026-02-24 | 홈 화면 Phase1 리디자인 — 디자인 시스템(Color/Type/Dimens) + Hero 카드 + Vico 차트 + 다크 테마 복원 | 완료 |
| 2026-02-22 | 예산 관리 확장 — 홈 카테고리 예산 UI + AI 채팅 예산 조회(BUDGET_STATUS)/설정(SET_BUDGET) | 완료 |
| 2026-02-22 | 데이터 삭제 시 광고 시청 기록 초기화 (clearSyncedMonths) + resume 시 silent 증분 동기화 (권한 체크 + 애널리틱스 스킵) | 완료 |
| 2026-02-21 | Budget BottomSheet (전체+카테고리별 예산 일괄 설정) + HistoryFilter BottomSheet 개선 (100dp 마진, 고정 하단 버튼) | 완료 |
| 2026-02-21 | 카테고리 상세 화면 (CategoryDetailActivity) — 월간 추이 차트 + 거래 리스트 + 다크 모드 | 완료 |
| 2026-02-21 | SMS 증분 동기화 5분 오버랩 + 다크 모드 차트 색상 수정 | 완료 |
| 2026-02-21 | 차트 UX 개선 (범례 원형 토글, X축 5일 간격 31일 고정, Y축 200만 단위 5등분, 6개월 평균, carry-forward, DataRefreshEvent 동기화) | 완료 |
| 2026-02-21 | PR #23 코드 리뷰 반영 (빈 룰 캐싱, 차순위 룰 시도, 중복 승격 방지) + UI 버그 수정 (하단 탭 높이, 탭 전환 초기화, 필터 적용 버튼) | 완료 |
| 2026-02-20 | LLM 생성 regex 샘플 검증 (validateRegexAgainstSamples, 50%+ 파싱 성공률 기준) | 완료 |
| 2026-02-20 | RTDB 원격 regex 룰 매칭 시스템 (RemoteSmsRule, RemoteSmsRuleRepository, 로컬 승격, RTDB 표본 필드 정리) | 완료 |
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
| [`AppDatabase.kt`](../app/src/main/java/com/sanha/moneytalk/core/database/AppDatabase.kt) | Room DB 정의 (v7, 11 entities, sms_patterns v3) |
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
| [`SmsPipeline.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsPipeline.kt) | 오케스트레이터 (Step 2→3→4→4.5→5) |
| [`SmsPipelineModels.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsPipelineModels.kt) | 데이터 클래스 (SmsInput, EmbeddedSms, SmsParseResult, SyncResult) |
| [`SmsPreFilter.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsPreFilter.kt) | Step 2: 사전 필터링 (키워드 + 구조) |
| [`SmsTemplateEngine.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsTemplateEngine.kt) | Step 3: 템플릿화 + Gemini Embedding API |
| [`SmsPatternMatcher.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsPatternMatcher.kt) | Step 4: 벡터 매칭 + 원격 룰 매칭 + regex 파싱 |
| [`SmsGroupClassifier.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsGroupClassifier.kt) | Step 5: 그룹핑 + LLM + regex 생성 + RTDB 표본 수집 |
| [`RemoteSmsRule.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/RemoteSmsRule.kt) | 원격 SMS regex 룰 데이터 클래스 |
| [`RemoteSmsRuleRepository.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/RemoteSmsRuleRepository.kt) | 원격 룰 리포지토리 (RTDB + 메모리 캐시) |
| [`SmsRegexRuleMatcher.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsRegexRuleMatcher.kt) | Step 1.5 Fast Path: sender regex 룰 매칭 + 통계 갱신 + 자동 최적화 |
| [`SmsOriginSampleCollector.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/SmsOriginSampleCollector.kt) | RTDB 성공/실패 표본 수집 (SHA-256 결정적 키) |
| [`SmsRegexRuleAssetLoader.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/rules/SmsRegexRuleAssetLoader.kt) | Asset JSON 기본 룰 시드 로더 |
| [`SmsRegexRemoteRuleLoader.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/rules/SmsRegexRemoteRuleLoader.kt) | RTDB overlay 룰 로더 (10분 캐시) |
| [`SmsRegexRuleSyncService.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms2/rules/SmsRegexRuleSyncService.kt) | Asset seed + RTDB overlay 병합 서비스 |

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
