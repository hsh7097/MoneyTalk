---
type: structure-map
title: MoneyTalk 구조 지도
description: MoneyTalk 앱의 루트, 패키지 구조, 핵심 파일, AI 참조 순서를 정리한다.
tags: [moneytalk, kb, structure-map, android]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# MoneyTalk 구조 지도

> 상태: draft
> 기준: 2026-07-08 현재 코드 확인

## Root

| 항목 | 경로 |
|---|---|
| repository root | `/Users/sanha/Documents/Android/MoneyTalk/MoneyTalk` |
| app module | `app/` |
| main source set | `app/src/main/java/com/sanha/moneytalk/` |
| resource metadata | `app/src/main/res/xml/app_functions_app_metadata.xml` |

## Package Groups

| package 또는 folder | 책임 | 대표 파일 |
|---|---|---|
| root | 앱 진입, Activity-scoped sync 상태, 전역 dialog/snackbar | `MainActivity.kt`, `MainViewModel.kt`, `MoneyTalkApplication.kt` |
| `navigation` | bottom tab route와 Compose NavHost | `NavGraph.kt`, `Screen.kt`, `BottomNavItem.kt` |
| `feature/home` | 홈 탭, 월 요약, 카테고리 분류, 데이터 repository | `HomeScreen.kt`, `HomeViewModel.kt`, `feature/home/data/*Repository.kt` |
| `feature/history` | 내역 탭, 월별 pager, 목록/달력/필터, 상세/수정 진입 | `HistoryScreen.kt`, `HistoryViewModel.kt`, `HistoryFilter.kt`, `HistoryDialogs.kt` |
| `feature/chat` | AI 상담 탭, Gemini 상담, 채팅방 | `ChatScreen.kt`, `ChatViewModel.kt`, `ChatRepositoryImpl.kt` |
| `feature/settings` | 설정 탭, 백업/복원, 수입/예산/앱 설정 | `SettingsScreen.kt`, `SettingsViewModel.kt` |
| `feature/*settings` | 카테고리/SMS/거래처 규칙 설정 Activity | `CategorySettingsActivity.kt`, `SmsSettingsActivity.kt`, `StoreRuleSettingsActivity.kt` |
| `feature/transactionedit` | 거래 추가/수정 화면 | `TransactionEditActivity.kt`, `TransactionEditViewModel.kt` |
| `core/database` | Room DB, DAO, Entity, DB-backed repository | `AppDatabase.kt`, `ExpenseDao.kt`, `IncomeDao.kt` |
| `core/sms` | SMS/MMS/RCS 파싱 파이프라인 | `SmsSyncCoordinator.kt`, `SmsPipeline.kt`, `SmsRegexRuleMatcher.kt` |
| `core/sync` | 동기화 범위와 coverage 정책 | `SmsSyncRangeCalculator.kt`, `SyncCoverageRecorder.kt` |
| `core/appfunctions` | Android App Functions 노출 함수 | `MoneyTalkFinanceAppFunctions.kt`, `MoneyTalkChatAppFunctions.kt` |
| `core/ui` | 공통 Compose UI와 snackbar/coachmark | `AppSnackbarBus.kt`, `TransactionCardCompose.kt` |
| `core/firebase` | Analytics, Crashlytics, Premium config | `AnalyticsHelper.kt`, `PremiumManager.kt` |
| `receiver` | SMS/MMS/RCS/notification 실시간 수신 보조 | `SmsReceiver.kt`, `MmsContentObserver.kt`, `NotificationTransactionService.kt` |

## Feature Slices

| 기능 | 진입 문서 | 기준 파일 |
|---|---|---|
| 문자 파싱 | [sms-parsing/README.md](sms-parsing/README.md) | `MainViewModel.kt`, `core/sms/**`, `core/sync/**` |
| 카테고리 분류 | [category-classification/README.md](category-classification/README.md) | `CategoryClassifierServiceImpl.kt`, `GeminiCategoryRepositoryImpl.kt`, `StoreEmbeddingRepositoryImpl.kt` |
| AI 채팅 | [chat/README.md](chat/README.md) | `feature/chat/**`, `core/util/LocalChatQueryRouter.kt`, `core/util/DataQueryParser.kt`, `core/util/ChatCreditPolicy.kt`, `core/util/ChatContextBuilder.kt` |
| App Functions | [app-functions/README.md](app-functions/README.md) | `core/appfunctions/**`, `app_functions_app_metadata.xml` |

## 핵심 파일 역할

| 파일 | 역할 | 수정 시 함께 확인할 파일 |
|---|---|---|
| `MainActivity.kt` | 앱 진입, SMS 권한 요청, theme, 전역 dialog/snackbar shell | `MainViewModel.kt`, `navigation/NavGraph.kt` |
| `MainViewModel.kt` | Activity-scoped SMS 동기화, 권한, 광고, coverage, resume sync orchestration | `core/sms/*`, `core/sync/*`, `feature/home/data/*Repository.kt` |
| `navigation/NavGraph.kt` | Home, History, Chat, Settings route 연결 | 각 feature `*Screen.kt` |
| `core/database/AppDatabase.kt` | Room entity/DAO 등록과 migration 정의 | `core/database/dao/**`, `core/database/entity/**`, `core/di/DatabaseModule.kt` |
| `core/sms/SmsSyncCoordinator.kt` | SMS batch parsing 외부 진입점 | `SmsPreFilter.kt`, `SmsIncomeFilter.kt`, `SmsRegexRuleMatcher.kt`, `SmsPipeline.kt` |
| `feature/history/ui/HistoryViewModel.kt` | History 화면 state, 필터, 월별 page cache, 거래 action 처리 | `HistoryScreen.kt`, `HistoryFilter.kt`, `HistoryDialogs.kt` |

## AI 참조 순서

| 작업 유형 | 참조 순서 |
|---|---|
| 변경 파일 분류 | `00-agent-routing.md` -> 이 문서 -> 해당 패키지 README |
| History 화면 변경 | `history/README.md` -> `history/00-structure-map.md` -> `history/package-reference/README.md` |
| SMS 파싱 변경 | `sms-pipeline/README.md` -> `sms-pipeline/00-structure-map.md` -> `sms-pipeline/04-files-checklist.md` |
| 문자 파싱 기능 변경 | `sms-parsing/README.md` -> `sms-parsing/01-feature-flow.md` -> `sms-pipeline/README.md` |
| 카테고리 분류 변경 | `category-classification/README.md` -> `category-classification/01-feature-flow.md` -> `finance-data/README.md` |
| AI 채팅/토큰 비용 변경 | `chat/README.md` -> `docs/CHAT_SYSTEM.md` -> `docs/AI_CREDIT_DEEP_ANALYSIS_PLAN.md` |
| DB schema/DAO 변경 | `finance-data/README.md` -> `finance-data/00-structure-map.md` -> `finance-data/04-files-checklist.md` |
| App Functions 변경 | `app-functions/README.md` -> `app-functions/01-feature-flow.md` -> `docs/APP_FUNCTIONS.md` |

## 갱신 규칙

구조가 바뀌면 함께 갱신한다.

- [00-agent-routing.md](00-agent-routing.md)
- 이 문서
- 관련 패키지 `README.md`
- 관련 패키지 `05-file-inventory.md`
- [00-change-index.md](00-change-index.md)
- 관련 패키지 `change-log.md`
