---
type: structure-map
title: MoneyTalk 구조 지도
description: MoneyTalk 앱의 루트, 패키지 구조, 핵심 파일, AI 참조 순서를 정리한다.
tags: [moneytalk, kb, structure-map, android]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# MoneyTalk 구조 지도

> 상태: draft
> 기준: 2026-09-08 현재 소스의 진입/상태/기능 서비스 경계 확인

## Root

| 항목 | 경로 |
|---|---|
| repository root | `/Users/sanha/Documents/Android/MoneyTalk/MoneyTalk` |
| Windows repository root | `C:\Users\hsh70\project\android\MoneyTalk` |
| app module | `app/` |
| main source set | `app/src/main/java/com/sanha/moneytalk/` |
| resource metadata | `app/src/main/res/xml/app_functions_app_metadata.xml` |

## Package Groups

| package 또는 folder | 책임 | 대표 파일 |
|---|---|---|
| root | 앱 진입, Activity-scoped sync 상태, 전역 dialog/snackbar | `MainActivity.kt`, `MoneyTalkApp.kt`, `SmsSyncDialogs.kt`, `MainViewModel.kt`, `MoneyTalkApplication.kt` |
| `navigation` | bottom tab route와 Compose NavHost | `NavGraph.kt`, `Screen.kt`, `BottomNavItem.kt` |
| `feature/home` | 홈 탭, 월 요약, 카테고리 분류, 데이터 repository | `HomeScreen.kt`, `HomeViewModel.kt`, `feature/home/data/*Repository.kt` |
| `feature/history` | 내역 탭, 월별 pager, 목록/달력/필터, 상세/수정 진입 | `HistoryScreen.kt`, `HistoryViewModel.kt`, `HistoryFilter.kt`, `HistoryDialogs.kt` |
| `feature/chat` | AI 상담 탭, Gemini 상담, 채팅방 | `ChatScreen.kt`, `ChatRoomView.kt`, `ChatViewModel.kt`, `ChatQueryExecutor.kt`, `ChatActionExecutor.kt`, `ChatAnalyticsCalculator.kt`, `ChatRepositoryImpl.kt` |
| `feature/settings/ui` | 설정 탭, 기능별 메뉴/다이얼로그, 화면 상태와 액션 | `SettingsScreen.kt`, `SettingsContract.kt`, `SettingsViewModel.kt`, `Settings*Section.kt` |
| `feature/settings/data` | 백업 준비/복원과 전체 데이터 초기화의 기능 서비스 | `SettingsBackupService.kt`, `SettingsDataResetService.kt` |
| `feature/aicredit` | AI 크레딧 잔액/원장 화면 | `AiCreditScreen.kt`, `AiCreditViewModel.kt` |
| `feature/categorydetail` | 카테고리 상세 화면 | `CategoryDetailScreen.kt`, `CategoryDetailViewModel.kt` |
| `feature/*settings` | 카테고리/SMS/거래처 규칙 설정 Activity | `CategorySettingsActivity.kt`, `SmsSettingsActivity.kt`, `StoreRuleSettingsActivity.kt` |
| `feature/transactionedit` | 거래 추가/수정 화면 | `TransactionEditActivity.kt`, `TransactionEditArgs.kt`, `TransactionEditUiState.kt`, `TransactionEditViewModel.kt` |
| `feature/transactionlist` | 조건 기반 거래 상세 목록 | `TransactionDetailListActivity.kt`, `TransactionDetailListViewModel.kt` |
| `feature/intro`, `feature/splash` | 초기 온보딩, 권한 안내, Splash | `IntroActivity.kt`, `OnboardingScreen.kt`, `PermissionScreen.kt`, `SplashScreen.kt` |
| `core/database` | Room DB, DAO, Entity, DB-backed repository | `AppDatabase.kt`, `ExpenseDao.kt`, `IncomeDao.kt` |
| `core/sms` | SMS/MMS/RCS 파싱 파이프라인 | `SmsSyncCoordinator.kt`, `SmsPipeline.kt`, `SmsRegexRuleMatcher.kt`, `SmsSyncResultFilter.kt`, `StoredIncomeSourceRepairer.kt` |
| `core/sync` | 동기화 범위와 coverage 정책 | `SmsSyncRangeCalculator.kt`, `SyncCoverageRecorder.kt` |
| `core/appfunctions` | Android App Functions 노출 함수 | `MoneyTalkFinanceAppFunctions.kt`, `MoneyTalkChatAppFunctions.kt`, `MoneyTalkChatAppFunctionReader.kt`, `MoneyTalkChatAppFunctionActionExecutor.kt`, `MoneyTalkAppFunctionAnalyticsCalculator.kt` |
| `core/ui` | 공통 Compose UI와 snackbar/coachmark | `AppSnackbarBus.kt`, `TransactionCardCompose.kt` |
| `core/notification` | 금융앱 알림 접근/후보 분석/원격 목록, 거래 알림 표시 | `NotificationAccessHelper.kt`, `FinancialAppCandidateAnalyzer.kt`, `SmsNotificationManager.kt`, `TransactionNotificationIntents.kt` |
| `core/firebase` | Analytics, Crashlytics, Premium config | `AnalyticsHelper.kt`, `PremiumManager.kt` |
| `receiver` | SMS/MMS/RCS/notification 실시간 수신 보조 | `SmsReceiver.kt`, `MmsContentObserver.kt`, `NotificationTransactionService.kt` |

## Feature Slices

| 기능 | 진입 문서 | 기준 파일 |
|---|---|---|
| 앱 진입/하단 탭 | [app-shell/README.md](app-shell/README.md) | `MainActivity.kt`, `MainViewModel.kt`, `navigation/**` |
| 홈 화면 | [home/README.md](home/README.md) | `HomeScreen.kt`, `HomeViewModel.kt` |
| 설정 화면 | [settings/README.md](settings/README.md) | `SettingsScreen.kt`, `SettingsViewModel.kt` |
| 거래 아이템 변경 | [transaction-edit/README.md](transaction-edit/README.md), [transaction-mutation/README.md](transaction-mutation/README.md) | `TransactionEditViewModel.kt`, `ExpenseRepository.kt`, `IncomeRepository.kt` |
| 문자 파싱 | [sms-parsing/README.md](sms-parsing/README.md) | `MainViewModel.kt`, `core/sms/**`, `core/sync/**` |
| 카테고리 분류 | [category-classification/README.md](category-classification/README.md) | `CategoryClassifierServiceImpl.kt`, `GeminiCategoryRepositoryImpl.kt`, `StoreEmbeddingRepositoryImpl.kt` |
| AI 채팅 | [chat/README.md](chat/README.md), [chat/00-structure-map.md](chat/00-structure-map.md) | `feature/chat/**`, `core/util/LocalChatQueryRouter.kt`, `core/util/DataQueryParser.kt`, `core/util/ChatCreditPolicy.kt`, `core/util/ChatContextBuilder.kt` |
| 임베딩/유사도 | [embedding/README.md](embedding/README.md) | `SmsEmbeddingService.kt`, `VectorSearchEngine.kt`, `core/similarity/**` |
| 코치마크 | [coachmark/README.md](coachmark/README.md) | `core/ui/coachmark/**`, `feature/**/coachmark/**` |
| 필터링 | [filtering/README.md](filtering/README.md) | `HistoryFilter.kt`, `CardVisibilityFilter.kt`, `StatsExclusionClassifier.kt` |
| 크레딧/광고/예산 | [budget-credit-monetization/README.md](budget-credit-monetization/README.md) | `core/ad/**`, `AiCreditRepository.kt`, `BudgetDao.kt` |
| 백업/복원 | [backup-restore/README.md](backup-restore/README.md) | `DataBackupManager.kt`, `GoogleDriveHelper.kt`, `SettingsViewModel.kt` |
| 알림 거래 수신 | [notification-ingestion/README.md](notification-ingestion/README.md) | `core/notification/**`, `AppNotificationTransactionParser.kt` |
| 거래 알림 표시 | [notification-display/README.md](notification-display/README.md) | `SmsNotificationManager.kt`, `SmsInstantProcessor.kt`, `SettingsScreen.kt` |
| 데이터 refresh | [data-refresh/README.md](data-refresh/README.md) | `DataRefreshEvent.kt`, 주요 화면 ViewModel |
| App Functions | [app-functions/README.md](app-functions/README.md) | `core/appfunctions/**`, `app_functions_app_metadata.xml`, KSP `app_functions.xml` |
| 루트 문서 흡수/정리 | [source-docs/README.md](source-docs/README.md) | `docs/*.md` |

## 핵심 파일 역할

| 파일 | 역할 | 수정 시 함께 확인할 파일 |
|---|---|---|
| `MainActivity.kt` | 플랫폼 수명주기, SMS 권한, theme, 앱 root 부착 | `MainViewModel.kt`, `MoneyTalkApp.kt` |
| `MoneyTalkApp.kt` | 탭/NavHost/전역 dialog/snackbar 조합 | `SmsSyncDialogs.kt`, `navigation/NavGraph.kt` |
| `SmsSyncDialogs.kt` | 동기화 진행/결과 표시와 사용자 이벤트 | `MainDialogUiState`, `MoneyTalkApp.kt` |
| `MainViewModel.kt` | Activity-scoped SMS 동기화, 권한, 광고, coverage, resume sync orchestration | `core/sms/*`, `core/sync/*`, `feature/home/data/*Repository.kt` |
| `core/sms/SmsSyncResultFilter.kt` | 파싱 거래 시각 기준 월 저장 범위 필터 | `MainViewModel.kt`, `SyncResult` |
| `core/sms/StoredIncomeSourceRepairer.kt` | 원문이 있는 기존 수입의 잘못된 출처 보정 | `MainViewModel.kt`, `SmsIncomeParser.kt`, `IncomeRepository.kt` |
| `core/notification/TransactionNotificationIntents.kt` | 저장된 지출/수입 ID별 알림 진입과 뒤로가기 stack | `SmsNotificationManager.kt`, `TransactionEditArgs.kt` |
| `core/sms/SmsIngestionWriter.kt` | 화면/백그라운드 공통 수입·지출 저장과 보정 | `ExpenseDao`, `ExpenseRepository`, `IncomeRepository` |
| `core/sms/SmsFallback*.kt`, `receiver/SmsFallbackJobService.kt` | 미확정 금융 후보의 영속 큐와 화면 밖 후속 처리 | `ClassificationState`, `SmsSyncCoordinator`, `SmsIngestionWriter` |
| `navigation/NavGraph.kt` | Home, History, Chat, Settings route 연결 | 각 feature `*Screen.kt` |
| `core/database/AppDatabase.kt` | Room entity/DAO 등록과 migration 정의 | `core/database/dao/**`, `core/database/entity/**`, `core/di/DatabaseModule.kt` |
| `core/sms/SmsSyncCoordinator.kt` | SMS batch parsing 외부 진입점 | `SmsPreFilter.kt`, `SmsIncomeFilter.kt`, `SmsRegexRuleMatcher.kt`, `SmsPipeline.kt` |
| `feature/history/ui/HistoryViewModel.kt` | 월별 조회/cache, 필터 적용, 거래 action | `HistoryUiState.kt`, `HistoryIntent.kt`, `HistoryTransactionListMapper.kt` |
| `feature/history/ui/HistoryFilterSelection.kt` | 적용 전 선택 값과 유형/카테고리 전환 규칙 | `HistoryFilter.kt`, `HistoryFilterControls.kt`, `HistoryFilterPickers.kt` |
| `feature/home/ui/model/HomeCategoryExpenseInfo.kt` | 카테고리 순위/예산 표시 계산과 값 계약 | `component/CategoryExpenseSection.kt` |
| `feature/chat/data/ChatMessageObserver.kt` | 현재 세션의 메시지 Flow 하나만 관찰 | `ChatViewModel.kt`, `ChatRepository.kt` |
| `feature/settings/data/SettingsBackupService.kt`, `SettingsDataResetService.kt` | 저장소들을 거치는 백업/복원/삭제 실행 순서 | `SettingsViewModel.kt`, `DataBackupManager.kt` |
| `core/appfunctions/MoneyTalkChatAppFunctionReader.kt`, `MoneyTalkChatAppFunctionActionExecutor.kt`, `MoneyTalkAppFunctionAnalyticsCalculator.kt` | App Functions의 typed 조회/변경/계산 실행 분리 | `MoneyTalkChatAppFunctions.kt`, `MoneyTalkChatAppFunctionModels.kt` |
| `feature/transactionedit/ui/TransactionEditSnapshot.kt` | 실제 저장 입력의 변경 감지 계약 | `TransactionEditUiState.kt`, `TransactionEditViewModel.kt` |

## AI 참조 순서

| 작업 유형 | 참조 순서 |
|---|---|
| 변경 파일 분류 | `00-agent-routing.md` -> 이 문서 -> 해당 패키지 README |
| 화면/기능 구조 감사 | `project-context/03-screen-function-architecture-audit.md` -> 영향 화면 README -> `ui-map/01-screen-composable-index.md` |
| 화면 진입 경로/실기기 QA | `02-screen-entry-paths.md` -> 영향 화면 README -> 실제 `*Activity.kt`/`*Screen.kt` |
| 앱 진입/하단 탭 변경 | `app-shell/README.md` -> `app-shell/00-structure-map.md` -> `MainActivity.kt`/`navigation/**` |
| Home 화면 변경 | `home/README.md` -> `home/00-structure-map.md` -> `home/package-reference/README.md` |
| History 화면 변경 | `history/README.md` -> `history/00-structure-map.md` -> `history/package-reference/README.md` |
| Settings 화면 변경 | `settings/README.md` -> `settings/00-structure-map.md` -> `settings/package-reference/README.md` |
| 거래 아이템 변경 | `transaction-edit/README.md` -> `transaction-edit/package-reference/README.md` -> `transaction-mutation/README.md` |
| SMS 파싱 변경 | `sms-pipeline/README.md` -> `sms-pipeline/00-structure-map.md` -> `sms-pipeline/04-files-checklist.md` |
| SMS Fast Path 룰 변경 | `sms-pipeline/06-rule-json-guide.md` -> `sms-pipeline/04-files-checklist.md` |
| 문자 파싱 기능 변경 | `sms-parsing/README.md` -> `sms-parsing/01-feature-flow.md` -> `sms-pipeline/README.md` |
| 카테고리 분류 변경 | `category-classification/README.md` -> `category-classification/01-feature-flow.md` -> `embedding/README.md` -> `finance-data/README.md` |
| Chat 화면 변경 | `chat/README.md` -> `chat/00-structure-map.md` -> `chat/package-reference/README.md` |
| AI 채팅/토큰 비용 변경 | `chat/README.md` -> `chat/05-system-contract.md` -> `budget-credit-monetization/02-policy-and-plans.md` |
| DB schema/DAO 변경 | `finance-data/README.md` -> `finance-data/00-structure-map.md` -> `finance-data/04-files-checklist.md` |
| App Functions 변경 | `app-functions/README.md` -> `app-functions/06-function-catalog.md` -> `app-functions/07-operational-playbook.md` |
| 거래 알림 표시 변경 | `notification-display/README.md` -> `notification-display/01-feature-flow.md` -> `filtering/README.md` |
| 기존 루트 문서 정리 | `source-docs/README.md` -> `source-docs/01-consolidation-map.md` -> 관련 기능 KB |

## 갱신 규칙

구조가 바뀌면 함께 갱신한다.

- [00-agent-routing.md](00-agent-routing.md)
- 이 문서
- 관련 패키지 `README.md`
- 관련 패키지 `05-file-inventory.md`
- [00-change-index.md](00-change-index.md)
- 관련 패키지 `change-log.md`
