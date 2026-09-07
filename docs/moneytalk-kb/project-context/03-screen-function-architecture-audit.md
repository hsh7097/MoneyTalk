---
type: reference
title: Screen and Function Architecture Audit
description: 13개 화면 도메인과 앱 공통 기능의 상태·렌더링·실행 책임을 점검하고 2026-09-08 분리 결과와 검증 범위를 기록한다.
tags: [moneytalk, architecture, mvvm, screen, review]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-09-08T00:00:00+09:00
status: verified
---

# 화면과 기능 책임 감사

기준은 2026-09-08 현재 작업 트리다. `feature/`의 13개 화면 도메인, 앱 진입/전역 UI, 문자 저장/알림, 백업/복원, AI 실행의 경계를 확인했다. 상세 화면 동작 계약은 각 도메인 KB가 정본이며, 이 문서는 수정 위치와 분리 판단을 설명한다.

## 구조 선택

- 기존 Compose + MVVM + Repository/Service 구조를 유지한다. 화면 이벤트가 많은 History/Settings의 기존 Intent를 사용하며 별도의 MVI 프레임워크를 도입하지 않는다.
- Activity는 플랫폼 수명주기/권한/파일 선택 진입을 담당한다. Screen은 state 수집과 화면 조합, 기능별 Composable은 값/callback 기반 렌더링, ViewModel은 작업 수명과 화면 상태를 관리한다.
- 계산/그룹/필터는 mapper 또는 순수 계산기로, 여러 저장소를 거치는 실행은 기능 서비스로 분리한다. 큰 파일의 줄 수만 줄이기 위한 공통 기반 클래스나 모든 기능에 대한 일괄 use-case 계층은 추가하지 않는다.
- 하나의 화면에서만 쓰는 짧은 행/헤더/helper는 관련 기능 파일 안에 둔다. 기존 클래스의 패키지/공개 계약을 유지할 수 있으면 유지한다.

## 13개 화면 도메인

소스 경로는 `app/src/main/java/com/sanha/moneytalk/feature/` 기준이다.

| 도메인 | 현재 책임과 적용한 정리 | 유지 판단/확인할 경계 | 담당 문서 |
|---|---|---|---|
| `home` | `HomeScreen`에서 `HomePageContent`, 월 요약/카테고리/AI 카드 파일 분리. `HomeUiState` 계약과 `HomeCategoryExpenseMapper`의 순위·예산 표시 계산 분리. | ViewModel의 월 cache/조회 Job/분류 진행 상태 유지. 분류 서비스·누적 계산·차트 mapper는 이미 독립됨. | [Home 데이터](../home/package-reference/02-data-viewmodel.md), [렌더링](../home/package-reference/03-rendering-action.md) |
| `history` | `HistoryUiState`, `HistoryIntent`, `TransactionListItem` 계약과 `HistoryTransactionListMapper` 분리. 임시 필터를 불변 `HistoryFilterSelection`으로 묶고 Controls/Pickers를 별도 파일로 배치. | 월 조회/cache와 거래 mutation은 ViewModel. 달력/기간 헤더/dialog는 기존 기능 파일 유지. 적용 전 시트 선택은 저장 상태와 분리. | [History 데이터](../history/package-reference/02-data-viewmodel.md), [필터 렌더링](../history/package-reference/03-rendering-action.md) |
| `chat` | UI 상태/채팅방/메시지/가이드/진행/재시도/dialog 파일 분리. 18종 조회는 `ChatQueryExecutor`, 13종 변경은 `ChatActionExecutor`, 분석 계산은 `ChatAnalyticsCalculator`, 현재 방 관찰은 `ChatMessageObserver`로 분리. | ViewModel은 세션/전송/크레딧/응답 순서를 조율한다. 세션 전환 시 이전 메시지 구독을 취소하고 현재 방 결과만 반영. Repository/Gemini 호출 계약 유지. | [Chat 데이터](../chat/package-reference/02-data-viewmodel.md), [계약](../chat/05-system-contract.md) |
| `settings` | `SettingsContract`, 여섯 메뉴 Section, `SettingsDialogs`, 진행 overlay 분리. `data/SettingsBackupService`와 `SettingsDataResetService`가 저장소 실행 순서를 담당. | ViewModel은 예산/Drive 상태와 완료 메시지를 조율. 파일 picker/로그인 launcher는 Screen. 보유 카드 Flow는 한 번 구독. | [Settings 데이터](../settings/package-reference/02-data-viewmodel.md), [렌더링](../settings/package-reference/03-rendering-action.md) |
| `categorydetail` | 날짜 그룹/금액순/헤더 합계는 `CategoryTransactionListMapper`로 분리. | Activity extra/월 cache/표시 필터/차트 mapper는 기존 경계 유지. 상세 hero/월 이동/헤더는 기능별 Composable로 같은 화면 파일에 유지. | [Category Detail](../category-detail/package-reference/02-data-viewmodel.md) |
| `transactionedit` | `TransactionEditArgs`, `TransactionEditUiState`, 실제 저장 입력의 `TransactionEditSnapshot` 분리. hero/기본정보/자동화/규칙/원문/상하단 UI를 기능 파일로 나눔. | ViewModel은 저장/삭제와 일괄 적용, Screen은 picker와 결과를 처리. 저장된 거래 ID로 로드하며 없는 거래는 오류 화면으로 처리. | [Transaction Edit](../transaction-edit/README.md), [렌더링](../transaction-edit/package-reference/03-rendering-action.md) |
| `transactionlist` | Activity extra, `TransactionDetailFilter`, `TransactionDetailListFilters`, 조회 ViewModel, Screen을 점검. 실행 코드의 추가 분리는 하지 않음. | 단일 날짜 조회/필터/정렬 책임이 이미 분리됨. History 필터 타입의 패키지는 유지해 달력 조건 전달을 보존. | [Transaction List](../transaction-list/package-reference/02-data-viewmodel.md) |
| `categorysettings` | 기존 Activity/Screen/ViewModel/Repository 구조 점검. 현재 유형 조회 Job을 하나로 제한하고 이전 탭 결과 반영 차단. | 추가/중복 확인/삭제와 카테고리 cache 무효화 정책 유지. 상태 구독은 lifecycle에 맞춤. | [Category Settings](../category-settings/package-reference/02-data-viewmodel.md) |
| `smssettings` | `SmsSettingsMainContent`, 제외 문구/차단 발신자/제외 카드 관리 화면을 기능별 파일로 분리. | Screen은 내부 route/title/back과 callback 연결, ViewModel은 Repository 변경. 각 관리 화면의 행 helper는 해당 기능 안에 유지. | [SMS Settings](../sms-settings/README.md) |
| `storerulesettings` | 추가/편집 입력을 `StoreRuleEditorDialog.kt`의 `AddEditRuleDialog`로 분리. | Screen은 목록/코치마크/picker, ViewModel과 `StoreRuleSyncService`는 검증/저장/소급 적용. nullable 고정 규칙과 통계 제외 보존. | [Store Rule Settings](../store-rule-settings/package-reference/03-rendering-action.md) |
| `aicredit` | `AiCreditActivity`, `AiCreditScreen`, `AiCreditViewModel`과 원장 Repository/RewardAdManager 경계 점검. 실행 코드 변경 없음. | 잔액/최근 원장/feature flag를 ViewModel이 결합하고 Screen은 표시/광고 버튼을 연결. 짧은 단일 기능에 새 계층을 추가하지 않음. | [AI Credit](../ai-credit-screen/README.md) |
| `intro` | `IntroActivity`, `OnboardingScreen`, `PermissionScreen` 경계 점검. 실행 코드 변경 없음. | ActivityResult 권한 요청/온보딩 완료/캐시 기반 강제 업데이트 분기는 Activity 수명에 연결. 설명/권한 화면은 독립 Composable. 이 흐름을 억지로 ViewModel에 옮기지 않음. | [Onboarding](../onboarding/README.md) |
| `splash` | `SplashScreen`의 애니메이션과 완료 callback 책임 확인. 실행 코드 변경 없음. | 일회성 화면 애니메이션 상태는 Composable 내부에 유지하며 데이터 Repository나 ViewModel을 추가하지 않음. | [Onboarding](../onboarding/README.md) |

## 화면을 가로지르는 기능

| 기능 | 현재 실행 경계/변경 | 함께 볼 파일/문서 |
|---|---|---|
| 앱 전역 UI | `MainActivity`에서 `MoneyTalkApp`과 `SmsSyncDialogs`를 분리. Activity는 플랫폼 진입, App은 탭/전역 UI, Dialog는 값/이벤트 기반 진행 표시. | [App shell](../app-shell/README.md), [Composable 위치](../ui-map/01-screen-composable-index.md) |
| 문자 동기화 | `MainViewModel`에서 요청 거래 기간 선택을 `SmsSyncResultFilter`, 기존 수입 출처 보정을 `StoredIncomeSourceRepairer`로 분리. 동기화 Job/광고/coverage는 기존 조율 경계 유지. | [수집 계약](../sms-parsing/06-ingestion-contract.md), [SMS pipeline](../sms-pipeline/README.md) |
| 저장과 알림 클릭 | 저장 결과의 영속 거래 ID로 알림 대상 지정. `TransactionNotificationIntents`가 유형+Long ID URI로 PendingIntent를 구분하고 상세의 부모 stack을 구성. | [거래 알림](../notification-display/README.md), `TransactionEditArgs.kt` |
| 삭제된 알림 대상/변경 감지 | 존재하지 않는 거래와 로딩 상태를 명시하고, `TransactionEditSnapshot`은 실제 저장 입력만 비교. dialog/로딩 변화로 수정 여부가 바뀌지 않게 분리. | [거래 편집](../transaction-edit/README.md) |
| 백업/복원/초기화 | `SettingsBackupService`는 백업 준비·복원 순서, `SettingsDataResetService`는 수집 등록 중지/큐/저장 데이터 초기화 순서. 직렬화는 기존 `DataBackupManager`, 저장은 기존 Repository. | [백업/복원](../backup-restore/README.md), [설정 데이터](../settings/package-reference/02-data-viewmodel.md) |
| AI 조회/액션/분석 | `ChatQueryExecutor`/`ChatActionExecutor`/`ChatAnalyticsCalculator`/`ChatMessageObserver`로 실행/계산/관찰 수명 분리. 세션과 비용 정책은 ViewModel/기존 정책 객체. | [Chat 시스템 계약](../chat/05-system-contract.md) |
| App Functions 조회/변경/분석 | `MoneyTalkChatAppFunctions`의 조회는 `MoneyTalkChatAppFunctionReader`, 변경은 `MoneyTalkChatAppFunctionActionExecutor`, 분석 계산은 `MoneyTalkAppFunctionAnalyticsCalculator`로 분리. typed 응답 모델과 공개 함수 계약은 App Functions KB에서 관리. | [App Functions](../app-functions/README.md), [함수 목록](../app-functions/06-function-catalog.md) |
| 카테고리/임베딩 | `CategoryClassifierService`, `StoreEmbeddingRepository`, `core/similarity`의 기존 연산→판단→실행 구조 유지. 임계값·라이브러리·프롬프트 변경은 이번 구조 정리 범위에 없음. | [분류](../category-classification/README.md), [임계값](02-threshold-registry.md) |

## 공통 기반 기능의 유지 경계

화면 바깥 코드는 UI 패턴에 맞추려고 재작성하지 않는다. 아래 파일과 호출 경계를 점검했으며, 기존 기능 분리가 명확한 영역은 유지한다.

| 영역 | 현재 클래스/계층 책임 | 유지 판단/검증 경계 |
|---|---|---|
| SMS 수집/후속 처리 | receiver/provider 진입 → Reader/`SmsSyncCoordinator`/`SmsPipeline` → `SmsInstantProcessor`·`SmsIngestionWriter` → Fallback Queue/Scheduler/Processor | 수집 입력, 해석, 저장, 재시도가 분리되어 있다. 알림의 저장 ID 전파와 요청 월/출처 보정 helper만 변경하며 전체 파이프라인을 화면 패턴으로 바꾸지 않는다. |
| 카테고리/벡터 | `CategoryClassifierService` → `StoreEmbeddingRepository` → `core/similarity` 정책·`VectorSearchEngine`, 원격 분류는 `GeminiCategoryRepository` | 연산/판단/실행의 기존 정책 경계를 유지한다. 유사도/학습 전파 숫자는 임계값 레지스트리가 기준이다. |
| Firebase/AI 구성 | `FirebaseAiModelFactory`는 모델 구성, `GeminiConfigProvider`·`PremiumManager`는 RTDB 설정, debug/release `AppCheckInstaller`는 빌드별 검증 설치 | 인증/설정/모델 생성과 요청 차단 정책이 분리되어 있다. 인증 실패를 구조 변경으로 우회하거나 운영 설정을 변경하지 않는다. |
| 광고/크레딧 | `RewardAdManager`는 SDK 수명/광고 준비·표시, `CreditFeaturePolicy`·`BannerAdVisibilityPolicy`는 표시 판단, `AiCreditRepository`·`AiCreditDao`는 잔액/원장 트랜잭션 | 광고 callback과 잔액 변경 경계 유지. SDK 실제 응답·광고 수익을 로컬 구조 감사로 증명하지 않는다. |
| Room/DI | `AppDatabase`는 v8/19 entities, `DatabaseModule`은 1→8 migration, `RepositoryModule`과 기능별 Repository/DAO는 주입·조회·저장 | 이번 기능 분리에 새 entity/schema/migration을 도입하지 않는다. 알림 ID 반환 변경은 DAO 계약/Room 테스트로 확인한다. |
| 백업/전송 | `SettingsBackupService`는 조회/복원 순서, `DataBackupManager`는 JSON/CSV/URI 입출력, `GoogleDriveHelper`는 인증·전송 | 새 서비스는 기존 실행 순서를 보존한다. 백업 형식이나 클라우드 계정 정책을 변경하지 않는다. |
| 화면 갱신 | `DataRefreshEvent`는 이벤트 발행, 화면별 ViewModel은 유형별 cache 무효화/재조회 | 공통 버스가 UI 상태를 직접 소유하지 않는다. 전체 삭제/복원/거래 변경 시 영향 화면이 다시 읽는 계약을 유지한다. |

## 검증 기준과 현재 증거 경계

2026-09-08 최종 소스 기준으로 아래 검증을 완료했다. 이동 전후 함수 본문과 호출 계약을 대조한 독립 리뷰에서 차단 회귀를 발견하지 못했다.

- `testDebugUnitTest`: 60개 suite, 351개 테스트 통과. 실패/오류/건너뜀 0.
- `assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease`: 통합 실행 성공(6분 38초). 라이브러리/빌드 설정/서명/Room 스키마 변경 없음.
- Android 16 QA 에뮬레이터: 51개 instrumentation 통과(31.926초). 알림 8개, 수집/중복/환불 31개, 목록 mapper 7개, App Functions 실행/실제 Hilt 연결 5개.
- 실제 시스템 알림 탭: 프로세스가 없는 상태의 지출 알림과 상세 화면 실행 중인 수입 알림에서 각각 올바른 거래를 열고 뒤로가기로 홈에 복귀. 두 테이블의 동일 숫자 ID도 서로 다른 대상으로 유지.
- Galaxy Z Fold7(Android 16): 기존 서명이 일치하는 Release APK를 데이터 보존 업데이트. 홈/가계부/인사이트/설정, 기존 채팅방과 목록 복귀, 카테고리 3종 탭, 문자 제외 문구 화면, 거래처 규칙 입력 취소, 예산 시트, 카테고리 상세와 거래 편집 진입을 확인했다. 실제 거래를 편집·삭제하는 시험은 하지 않았다.
- QA 에뮬레이터: 고정 거래 필터 적용/해제, 달력 날짜 상세와 복귀, 합성 거래의 저장/삭제 대상 처리도 확인했다.
- 문서 228개, 상대 링크 859개, 명시적 소스 경로 205개 검사에서 끊어진 파일 경로 없음. 변경 파일의 공백 오류 검사 통과.

실행 로그와 화면 증거는 로컬 `artifacts/notification-architecture-20260908/`에 보관한다. `acceptance-build.log`, `acceptance-instrumentation.log`, `final-independent-review.md`, `final-doc-path-validation.json`이 최종 검증 근거다. 실기기 화면에는 개인 거래가 포함되므로 이 증거 폴더는 Git에 포함하지 않는다.

| 확인 대상 | 검증 수단 | 증명 범위 |
|---|---|---|
| 알림 거래 식별/백스택 | 알림 관련 Android instrumentation와 실제 알림 탭 | 지출/수입 같은 숫자 ID, 여러 알림, 앱 종료/실행 중, 잘못된 대상 |
| 저장 ID 전파 | Room ingestion instrumentation | 실제 삽입 결과 ID가 알림에 전달되는지, 중복/보정 저장 경계 |
| 편집 변경 감지 | `TransactionEditSnapshotTest` | 저장 필드 변경과 표시 전용 상태 분리 |
| History 필터 | `HistoryFilterSelectionTest` | 유형 재선택, 처음 카테고리 축소, 이후 다중 유형, 카드/고정 유지 |
| 목록 그룹/합계 | `TransactionListMapperInstrumentedTest`, 기존 날짜 상세/카테고리 필터 테스트 | 혼합 정렬, 통계 제외 행 보존, 이체 입금 합계, 자정 경계 |
| 홈 카테고리 표시 | `HomeCategoryExpenseMapperTest` | 순위/미분류 병합, 90%/100%/초과 예산, 0/빈 목록 |
| Chat 메시지/계산 | `ChatMessageObserverTest`, `ChatAnalyticsCalculatorTest` | 방 전환 뒤 이전 구독, 같은 방 재선택, 순수 집계 |
| App Functions 분리 | 공개 선언/metadata 대조, 순수 분석 계산 테스트, Room/실제 Hilt 실행 테스트, 빌드 | typed 응답/등록 함수/삭제 gate와 실행 위임, 필터·그룹·메트릭 계산 보존 |
| 화면 조합 | Debug/Release 빌드와 핵심 화면 기기 점검 | 함수 이관 후 연결, 상태/테마 구독, 필터/편집/뒤로가기. 최종 실기기 시험은 현재 기기의 글자 크기 기준 |

서버 AI 응답, 실제 광고 수익, 장시간 절전 중 문자 수신은 이 구조 정리와 단위 테스트만으로 검증되지 않는다. 기존 정책을 유지한 영역과 새로 실행 검증한 영역을 구분해 보고한다.

## 유지보수 시 읽는 순서

1. 이 문서의 화면/기능 행에서 담당 KB를 고른다.
2. [Composable 인덱스](../ui-map/01-screen-composable-index.md)에서 실제 렌더링 파일을 찾는다.
3. 관련 `package-reference/02-data-viewmodel.md`와 `03-rendering-action.md`에서 저장/표시 경계를 확인한다.
4. 필터·합계·세션·복원 순서를 변경하면 대응 회귀 테스트와 공통 소비 화면을 함께 확인한다.
