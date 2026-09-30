---
title: "MoneyTalk 현재 코드 기준 KB"
status: verified
last_checked: "2026-09-30"
source_ref: "95b419e8b98e374a991f3210a194a88959937ee8"
---

# MoneyTalk 현재 코드 기준 KB

## 범위

기존 [MoneyTalk KB](../../README.md)에 통합한 작은 현재 기준 묶음이다. 앱 진입, SMS→거래 저장→화면 갱신, 카테고리 분류, AI 채팅, DB 변경 영향, 개발 명령을 찾는 데 사용한다. 화면별 상세는 기존 도메인 KB를 필요한 경우에만 읽는다. 과거 문서의 실행 성공 이력은 이번 환경의 성공으로 인용하지 않는다.

- 코드 기준: `95b419e8b98e374a991f3210a194a88959937ee8`. 조사 시작 시 코드·문서 작업트리는 깨끗했으며 이번 변경은 KB와 문서 진입 링크뿐이다.
- 작성 도구 기준: ClaudeGuide `2d2aa90c82a141e7440d3ee60b692911eb2ee5f1`의 `docs/kb-scaffold/`. 프로젝트 안의 구형 스캐폴드를 덮어쓰지 않았다.
- **사실**은 해당 SHA의 파일·심볼을 읽어 확인한 구현이다. **추론**은 변경 영향 판단이며 별도 표시한다. **미검증**은 기기·서버·빌드에서 확인하지 못한 결과다.
- 이 묶음은 다른 프로젝트, 운영 서버 설정값, 실제 문자·거래·계정 데이터, 제품 성능·분류 정확도를 설명하지 않는다.

## 탐색

| 질문/변경 단서 | 읽을 위치 | 도달할 구현/확인점 |
|---|---|---|
| 앱을 켜면 어떤 화면과 초기화로 들어가는가? | 아래 구조·진입점 | Manifest → Application → Intro → Main → NavGraph |
| SMS가 누락되거나 중복 저장되는가? | [데이터 흐름](data-flows.md) | 배치 writer, 실시간 processor, fallback, 삭제 추적 |
| 가게 분류·거래 수정 뒤 화면 값이 다른가? | [데이터 흐름](data-flows.md) | StoreRule/분류 cache/DB/refresh 구분 |
| 로컬 AI 조회·분석·삭제·크레딧 정책을 바꾸는가? | 아래 AI 흐름·변경 경계 | router/executor/parser/prompt/credit gate |
| DB entity나 DI 구현을 바꾸는가? | 아래 구조·변경 경계 | AppDatabase, DatabaseModule, RepositoryModule |
| 빌드·테스트·설정·기기 실행을 준비하는가? | [개발·실행](development.md) | 실제 Gradle/SDK 기준, 설정 이름, 환경 한계 |
| 무엇을 검증했으며 기존 KB를 믿어도 되는가? | [검증 기록](validation.md), [변경 기록](change-log.md) | 정적 검사와 소스 탐색, 과거 문서 오류, 미실행 구분 |

### 구조와 진입점 — 사실

실제 코드는 `core/`와 `feature/`로 나뉘며 Gradle 앱 모듈은 `:app` 하나다. 저장소 README의 간략한 `data/domain/presentation` 예시는 현재 파일 위치 안내로 사용하지 않는다.

| 위치·대표 코드 | 책임과 연결 |
|---|---|
| [settings.gradle.kts](../../../../settings.gradle.kts), [앱 빌드](../../../../app/build.gradle.kts) | `include(":app")`; Compose, Hilt, Room KSP, Firebase 의존성 구성 |
| [AndroidManifest.xml](../../../../app/src/main/AndroidManifest.xml) | `MoneyTalkApplication`, LAUNCHER `IntroActivity`, 화면 Activity·SMS receiver·notification service 등록 |
| [MoneyTalkApplication.kt](../../../../app/src/main/java/com/sanha/moneytalk/MoneyTalkApplication.kt) | `onCreate`: 삭제 SMS 추적 복원, 알림 채널, MMS/RCS observer, Firebase 초기화 분기, fallback 복원; Hilt와 App Functions 등록 |
| [IntroActivity.kt](../../../../app/src/main/java/com/sanha/moneytalk/feature/intro/ui/IntroActivity.kt) | `onSplashFinished`/`onPermissionFlowDone` → `checkConfigAndNavigate` → `navigateToMain`; 보유 설정의 최소 버전 확인. RTDB 응답을 기다리지 않음 |
| [MainActivity.kt](../../../../app/src/main/java/com/sanha/moneytalk/MainActivity.kt), [MoneyTalkApp.kt](../../../../app/src/main/java/com/sanha/moneytalk/MoneyTalkApp.kt), [NavGraph.kt](../../../../app/src/main/java/com/sanha/moneytalk/navigation/NavGraph.kt) | `onCreate`에서 Compose 앱; `onResume` → `MainViewModel.onAppResume`; `NavGraph`는 홈부터 시작해 홈/내역/채팅/설정 4탭 연결. 상세/편집에는 별도 Activity도 있음 |
| [AppDatabase.kt](../../../../app/src/main/java/com/sanha/moneytalk/core/database/AppDatabase.kt), [DatabaseModule.kt](../../../../app/src/main/java/com/sanha/moneytalk/core/di/DatabaseModule.kt) | Room v8, 19 entities, `exportSchema=false`; `provideAppDatabase`의 migrations 1→8 등록. entity/DAO는 `core/database/` |
| [RepositoryModule.kt](../../../../app/src/main/java/com/sanha/moneytalk/core/di/RepositoryModule.kt) | `bindChatRepository`/`bindGeminiRepository`/`bindCategoryClassifierService` 등 interface→implementation Hilt 연결. 업무 저장소는 `feature/*/data/` |
| [HomeViewModel.kt](../../../../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeViewModel.kt), [HistoryViewModel.kt](../../../../app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryViewModel.kt) | repository와 `DataRefreshEvent`를 관찰해 `StateFlow`의 `uiState` 생성; feature UI가 lifecycle에 맞춰 구독 |

### AI 채팅 — 사실

1. [ChatViewModel.kt](../../../../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatViewModel.kt)의 `sendMessage`가 빈 입력·중복 전송을 제한하고 크레딧 gate/차감 후 `processSendMessage`를 90초 timeout으로 실행한다.
2. `processLocalSimpleLookup` → [LocalChatQueryRouter.kt](../../../../app/src/main/java/com/sanha/moneytalk/core/util/LocalChatQueryRouter.kt)의 `tryRoute` → [ChatQueryExecutor.kt](../../../../app/src/main/java/com/sanha/moneytalk/feature/chat/data/ChatQueryExecutor.kt)의 `execute` → `buildLocalLookupResponse` → [ChatRepositoryImpl.kt](../../../../app/src/main/java/com/sanha/moneytalk/feature/chat/data/ChatRepositoryImpl.kt)의 `saveLocalExchange`. 이 경로는 analyzer/final/summary 모델 호출을 생략한다. 조언·분석·미지원 기간 등은 Gemini 경로로 넘긴다.
3. 일반 경로는 `sendMessageAndBuildContext` → [ChatContextBuilder.kt](../../../../app/src/main/java/com/sanha/moneytalk/core/util/ChatContextBuilder.kt)의 `buildQueryAnalysisContext` → [GeminiRepositoryImpl.kt](../../../../app/src/main/java/com/sanha/moneytalk/feature/chat/data/GeminiRepositoryImpl.kt)의 `analyzeQueryNeeds` → [DataQueryParser.kt](../../../../app/src/main/java/com/sanha/moneytalk/core/util/DataQueryParser.kt)의 `parseQueryRequest` → query/action executor → `buildFinalAnswerPrompt` → `generateFinalAnswerWithContext` → 응답 저장이다. clarification과 분석 실패 분기는 ViewModel에서 별도로 처리한다.
4. `QueryType` 18개, `ActionType` 13개가 parser에 정의된다. `ANALYTICS`는 `ChatQueryExecutor`가 통계 대상 지출을 [ChatAnalyticsCalculator.kt](../../../../app/src/main/java/com/sanha/moneytalk/feature/chat/data/ChatAnalyticsCalculator.kt)의 `calculate`로 넘겨 필터/그룹/합계·평균·정렬한다. 수입 질문이 아니면 [ChatIncomeContextPolicy.kt](../../../../app/src/main/java/com/sanha/moneytalk/feature/chat/data/ChatIncomeContextPolicy.kt)의 `filterQueries`가 수입 관련 query를 제한한다.
5. 모델은 [FirebaseAiModelFactory.kt](../../../../app/src/main/java/com/sanha/moneytalk/core/firebase/FirebaseAiModelFactory.kt)의 `create`에서 Firebase AI Logic `googleAI()` backend로 생성한다. 기본 역할별 모델은 [PremiumConfig.kt](../../../../app/src/main/java/com/sanha/moneytalk/core/firebase/PremiumConfig.kt)의 `GeminiModelConfig`에 있고 RTDB override 가능이다. 이름은 코드 기본값이며 서버에서 제공·허용되는지는 미검증이다.

### 변경 경계·위험

- **사실:** [ChatCreditPolicy.kt](../../../../app/src/main/java/com/sanha/moneytalk/core/util/ChatCreditPolicy.kt)의 `estimate`는 모든 nonblank 메시지를 1 credit으로 평가한다. 실제 차감은 [CreditFeaturePolicy.kt](../../../../app/src/main/java/com/sanha/moneytalk/core/ad/CreditFeaturePolicy.kt)의 `canUseCreditRewardAd`와 [BuildVariantPolicy.kt](../../../../app/src/main/java/com/sanha/moneytalk/core/util/BuildVariantPolicy.kt), FREE tier, `credit_ad_enable`/`reward_ad_enabled` gate에 따른다. 로컬 조회도 gate를 먼저 통과한다.
- **사실:** 크레딧 잔액·원장 변경은 [RewardAdManager.kt](../../../../app/src/main/java/com/sanha/moneytalk/core/ad/RewardAdManager.kt)의 `consumeRewardChat`/`refundChatCredits` → [AiCreditRepository.kt](../../../../app/src/main/java/com/sanha/moneytalk/core/database/AiCreditRepository.kt)의 `spendForChat`/`refundCredits` → [AiCreditDao.kt](../../../../app/src/main/java/com/sanha/moneytalk/core/database/dao/AiCreditDao.kt)의 `spendCredits`/`grantCredits`에서 확인한다. 잔액·원장 transaction 성공은 기기에서 미검증이다.
- **사실:** [ChatActionExecutor.kt](../../../../app/src/main/java/com/sanha/moneytalk/feature/chat/data/ChatActionExecutor.kt)의 `execute`는 analyzer가 반환한 액션을 실행한다. 별도 사용자 확인 단계나 final 답변 실패 시 거래 변경 rollback을 이 흐름에서 확인하지 못했다. timeout/오류의 크레딧 환불은 거래 되돌리기가 아니다. **추론:** 삭제·분류 정책을 바꾸면 repository/DAO, 삭제 추적, refresh와 실패 후 데이터를 함께 검증해야 한다.
- **사실:** 프롬프트는 [string_prompt.xml](../../../../app/src/main/res/values/string_prompt.xml)의 `prompt_financial_advisor_system` 등에서 관리한다. 원본 리스트 직접 집계는 금지하고, 명시적 기간 비교 요청과 동일 지표 원본 2값이 있을 때만 방향/차이 계산 예외를 허용한다. 프롬프트 규칙이 실제 모델 응답에서 지켜지는지는 미검증이다.
- **추론:** schema 변경은 `AppDatabase` migration과 `DatabaseModule` 등록을 함께 변경·시험해야 기존 사용자 데이터를 보존할 수 있다. 화면 필터·사용자 월 시작일을 바꾸면 Home/History/Chat 집계 정의를 함께 확인한다.
- **추론:** query/analytics 확장은 parser enum, executor, calculator, prompt, 테스트를 함께 대조해야 한다. 파싱 성공만으로 실행 가능하거나 결과가 정확하다고 판단하지 않는다.

## 근거

위 상대 링크의 파일과 명시한 심볼을 `source_ref`의 실제 코드에서 대조했다. `AppDatabase` annotation의 entity 목록을 세었고 Gradle module 및 DI binding, launcher와 lifecycle 연결을 확인했다. 조사 코드에 미커밋 변경은 없다.

기존 [전체 구조 문서](../01-system-overview.md)에는 모델명 2.5 표와 3.1/3.5 본문이 함께 있다. 현재 `GeminiModelConfig`를 우선 확인한다. 데이터 파이프라인 주석과 실제 구현의 차이는 [데이터 흐름](data-flows.md)에 기록했다. 기존 상세 KB의 날짜·상태를 이번 조사 날짜로 일괄 갱신하지 않는다.

## 검증

정적 검사, 독립 탐색 질문, 환경 관측의 실제 결과는 [검증 기록](validation.md)에 남긴다. `verified`가 되더라도 문서의 링크·탐색·소스 대조 범위만 뜻하며 앱 빌드, Firebase 연결, SMS 실기기 수신, 데이터 migration, 광고 지급 성공을 뜻하지 않는다. 이 환경의 개발 제약과 재실행 명령은 [개발·실행](development.md)을 본다.
