---
type: routing
title: MoneyTalk Agent Routing
description: 변경 파일 경로와 작업 키워드를 기준으로 AI가 읽을 KB 문서를 결정한다.
tags: [moneytalk, kb, routing, android]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-13T01:11:00+09:00
status: draft
---

# 에이전트 작업 라우팅

이 문서는 MoneyTalk 작업에서 변경 파일 경로를 보고 읽을 KB 문서를 고르는 진입점이다.
전체 KB를 매번 읽지 않는다.

## 기본 원칙

1. 변경 파일 경로를 먼저 확인한다.
2. 이 문서에서 영향 도메인 또는 서브모듈을 고른다.
3. 해당 패키지의 `README.md`와 필요한 세부 문서만 읽는다.
4. 실제 판단은 현재 코드와 diff가 기준이다.
5. 문서와 코드가 다르면 코드 확인 결과로 문서를 갱신한다.

## 항상 먼저 보는 문서

- [README.md](README.md)
- [00-change-index.md](00-change-index.md)
- [01-structure-map.md](01-structure-map.md)
- 화면 진입/실기기 QA/route 확인 작업이면 [02-screen-entry-paths.md](02-screen-entry-paths.md)

## 경로별 문서 라우팅

| 변경 파일 경로 또는 키워드 | 우선 참조 문서 | 비고 |
|---|---|---|
| `app/src/main/java/com/sanha/moneytalk/MainActivity.kt`, `MainViewModel.kt`, `MainUiState.kt`, `navigation/**` | [app-shell/README.md](app-shell/README.md), [app-shell/00-structure-map.md](app-shell/00-structure-map.md) | 앱 진입, 하단 탭 4개, Activity-scoped sync/ad/dialog 상태 |
| `app/src/main/java/com/sanha/moneytalk/feature/home/ui/**` | [home/README.md](home/README.md), [home/00-structure-map.md](home/00-structure-map.md), [home/package-reference/README.md](home/package-reference/README.md) | 홈 탭, 월별 현황, 카테고리 지출, AI 인사이트, 홈 코치마크 |
| `app/src/main/java/com/sanha/moneytalk/feature/history/**` | [history/README.md](history/README.md), [history/00-structure-map.md](history/00-structure-map.md), [history/package-reference/README.md](history/package-reference/README.md) | 내역 화면, 필터, 달력, 상세/수정 진입 |
| `app/src/main/java/com/sanha/moneytalk/feature/settings/**` | [settings/README.md](settings/README.md), [settings/00-structure-map.md](settings/00-structure-map.md), [settings/package-reference/README.md](settings/package-reference/README.md) | 설정 탭, 예산, AI 서비스 상태, 백업/복원, 카드 보유, Google Drive |
| `app/src/main/java/com/sanha/moneytalk/feature/transactionedit/**`, 아이템 변경, 거래 수정, 거래 추가 | [transaction-edit/README.md](transaction-edit/README.md), [transaction-edit/package-reference/README.md](transaction-edit/package-reference/README.md), [transaction-mutation/README.md](transaction-mutation/README.md) | 지출/수입 추가·수정·삭제, 카테고리 picker, 일괄 적용 |
| `app/src/main/java/com/sanha/moneytalk/feature/categorydetail/**` | [category-detail/README.md](category-detail/README.md), [category-detail/00-structure-map.md](category-detail/00-structure-map.md), [category-detail/package-reference/README.md](category-detail/package-reference/README.md) | 카테고리 상세 화면, 월 이동, 정렬, 거래 목록 |
| `app/src/main/java/com/sanha/moneytalk/feature/categorysettings/**`, custom category | [category-settings/README.md](category-settings/README.md), [category-settings/package-reference/README.md](category-settings/package-reference/README.md), [category-classification/README.md](category-classification/README.md) | 사용자 카테고리 설정, 분류 기능 영향 |
| `app/src/main/java/com/sanha/moneytalk/feature/transactionlist/**` | [transaction-list/README.md](transaction-list/README.md), [transaction-list/package-reference/README.md](transaction-list/package-reference/README.md), [transaction-edit/README.md](transaction-edit/README.md) | 조건 기반 거래 상세 목록, 거래 수정 진입 |
| `app/src/main/java/com/sanha/moneytalk/feature/smssettings/**` | [sms-settings/README.md](sms-settings/README.md), [sms-parsing/README.md](sms-parsing/README.md), [filtering/README.md](filtering/README.md) | SMS 제외 키워드/발신자 설정 |
| `app/src/main/java/com/sanha/moneytalk/feature/storerulesettings/**` | [store-rule-settings/README.md](store-rule-settings/README.md), [store-rule-settings/package-reference/README.md](store-rule-settings/package-reference/README.md), [category-classification/README.md](category-classification/README.md), [transaction-mutation/README.md](transaction-mutation/README.md) | 거래처 규칙 설정, 분류/일괄 적용 영향 |
| `app/src/main/java/com/sanha/moneytalk/feature/aicredit/**` | [ai-credit-screen/README.md](ai-credit-screen/README.md), [budget-credit-monetization/README.md](budget-credit-monetization/README.md) | AI 크레딧 잔액/원장 화면 |
| `app/src/main/java/com/sanha/moneytalk/feature/intro/**`, `feature/splash/**` | [onboarding/README.md](onboarding/README.md), [app-shell/README.md](app-shell/README.md) | 초기 진입, 권한 안내, Splash |
| `syncSmsV2`, `SMS_RECEIVED`, 문자 파싱 결과 저장 | [sms-parsing/README.md](sms-parsing/README.md), [sms-parsing/01-feature-flow.md](sms-parsing/01-feature-flow.md) | 문자 파싱 기능 end-to-end |
| `app/src/main/java/com/sanha/moneytalk/core/sms/**` | [sms-parsing/README.md](sms-parsing/README.md), [sms-pipeline/README.md](sms-pipeline/README.md), [sms-pipeline/00-structure-map.md](sms-pipeline/00-structure-map.md) | SMS 파싱 기능과 pipeline 내부 구현 |
| `app/src/main/java/com/sanha/moneytalk/core/sync/**` | [sms-parsing/README.md](sms-parsing/README.md), [sms-pipeline/README.md](sms-pipeline/README.md), [sms-pipeline/04-files-checklist.md](sms-pipeline/04-files-checklist.md) | 동기화 범위, coverage, 월별 CTA |
| `app/src/main/java/com/sanha/moneytalk/receiver/**` | [sms-pipeline/README.md](sms-pipeline/README.md), [notification-ingestion/README.md](notification-ingestion/README.md), [sms-pipeline/05-file-inventory.md](sms-pipeline/05-file-inventory.md) | SMS/MMS/RCS/알림 실시간 수신 보조 |
| `app/src/main/java/com/sanha/moneytalk/core/database/**` | [finance-data/README.md](finance-data/README.md), [finance-data/00-structure-map.md](finance-data/00-structure-map.md) | Room DB, DAO, migration, entity |
| `CategoryClassifierService`, `GeminiCategoryRepository`, 카테고리 파싱, 카테고리 분류 | [category-classification/README.md](category-classification/README.md), [category-classification/01-feature-flow.md](category-classification/01-feature-flow.md) | 카테고리 분류 기능 |
| `StoreEmbedding`, `SmsEmbeddingService`, `VectorSearchEngine`, `core/similarity/**`, 임베딩 | [embedding/README.md](embedding/README.md), [embedding/00-structure-map.md](embedding/00-structure-map.md), [category-classification/README.md](category-classification/README.md) | embedding, vector search, 유사도 정책 |
| `StoreRule`, `StoreRuleRepository`, `StoreRuleSyncService` | [store-rule-settings/README.md](store-rule-settings/README.md), [category-classification/README.md](category-classification/README.md) | 거래처 규칙, 카테고리 분류 영향 |
| `app/src/main/java/com/sanha/moneytalk/feature/home/data/**` | [finance-data/README.md](finance-data/README.md), [home/README.md](home/README.md), [category-classification/README.md](category-classification/README.md) | Expense/Income/Category/StoreRule repository |
| `app/src/main/java/com/sanha/moneytalk/core/appfunctions/**` | [app-functions/README.md](app-functions/README.md), [app-functions/06-function-catalog.md](app-functions/06-function-catalog.md), [app-functions/07-operational-playbook.md](app-functions/07-operational-playbook.md) | agent 기능 읽기/수정 노출, DB 점검 플레이북 |
| `app/src/main/java/com/sanha/moneytalk/feature/chat/**` | [chat/README.md](chat/README.md), [chat/00-structure-map.md](chat/00-structure-map.md), [chat/package-reference/README.md](chat/package-reference/README.md), [chat/05-system-contract.md](chat/05-system-contract.md) | AI 채팅 UI, Gemini Repository, 채팅 세션/요약 |
| `LocalChatQueryRouter`, `ChatCreditPolicy`, `DataQueryParser`, `ChatContextBuilder`, `analyzeQueryNeeds`, `generateFinalAnswerWithContext`, 토큰 비용 | [chat/README.md](chat/README.md), [chat/05-system-contract.md](chat/05-system-contract.md), [budget-credit-monetization/02-policy-and-plans.md](budget-credit-monetization/02-policy-and-plans.md) | 단순 조회 로컬 처리, 앱 내부 계산, Gemini 입력 축소, 채팅 1회 1크레딧 정책 |
| `core/ui/coachmark/**`, `feature/**/coachmark/**`, 코치마크 | [coachmark/README.md](coachmark/README.md), [coachmark/00-structure-map.md](coachmark/00-structure-map.md), 영향 화면 README | 화면별 온보딩 overlay/target |
| `HistoryFilter`, `CardVisibilityFilter`, `StatsExclusionClassifier`, `SmsExclusion`, 필터 | [filtering/README.md](filtering/README.md), [filtering/01-feature-flow.md](filtering/01-feature-flow.md), 영향 화면 README | 내역 필터, 카드 숨김, 통계 제외, SMS 제외 |
| `BudgetDao`, `AiCreditRepository`, `core/ad/**`, `RewardAdManager`, 예산, 광고, 크레딧 | [budget-credit-monetization/README.md](budget-credit-monetization/README.md), [budget-credit-monetization/02-policy-and-plans.md](budget-credit-monetization/02-policy-and-plans.md) | 예산, AI 크레딧, 보상형 광고, 배너 정책, 월별 가져오기 1크레딧 |
| `DataBackupManager`, `GoogleDriveHelper`, 백업, 복원, 전체 삭제 | [backup-restore/README.md](backup-restore/README.md), [settings/README.md](settings/README.md), [finance-data/README.md](finance-data/README.md) | 로컬/Drive 백업 복원 |
| `SmsNotificationManager`, `showExpenseNotification`, `showIncomeNotification`, `clearTransactionNotifications`, 거래 알림 표시 | [notification-display/README.md](notification-display/README.md), [notification-display/01-feature-flow.md](notification-display/01-feature-flow.md), [filtering/README.md](filtering/README.md) | 거래 저장 후 MoneyTalk 자체 알림 표시/정리 |
| `app/src/main/java/com/sanha/moneytalk/core/notification/**`, `AppNotificationTransactionParser`, `NotificationTransactionService`, 알림 거래 수신 | [notification-ingestion/README.md](notification-ingestion/README.md), [notification-ingestion/00-structure-map.md](notification-ingestion/00-structure-map.md), [notification-display/README.md](notification-display/README.md) | 금융앱 알림/RCS/비즈메시지 거래 수신과 저장 후 노티 표시 구분 |
| `DataRefreshEvent`, `RefreshType`, stale cache, 화면 refresh | [data-refresh/README.md](data-refresh/README.md), [data-refresh/01-feature-flow.md](data-refresh/01-feature-flow.md), 영향 화면 README | 화면 간 데이터 변경 통지와 page cache refresh |
| `app/src/main/java/com/sanha/moneytalk/core/ui/**`, `core/theme/**` | [01-structure-map.md](01-structure-map.md), 영향 도메인의 rendering/action 문서 | 공통 Compose UI |
| `docs/*.md` 루트 문서, 기존 문서 흡수, 문서 정리 | [source-docs/README.md](source-docs/README.md), [source-docs/01-consolidation-map.md](source-docs/01-consolidation-map.md) | `moneytalk-kb/**`, `kb-scaffold/**` 제외. 구현 사실/계획/이력을 분리 |
| App Functions 문서 흡수 이력, agent DB 조회/수정 함수 목록 | [app-functions/README.md](app-functions/README.md), [app-functions/06-function-catalog.md](app-functions/06-function-catalog.md), [app-functions/07-operational-playbook.md](app-functions/07-operational-playbook.md) | 루트 문서 내용은 App Functions KB로 흡수 완료 |
| SMS 룰 JSON 운영 가이드, `sms_rules_v1.json` | [sms-pipeline/06-rule-json-guide.md](sms-pipeline/06-rule-json-guide.md), [sms-pipeline/README.md](sms-pipeline/README.md) | Fast Path sender regex 룰 운영 |
| SMS 권한 정책, Play Console release gate | [onboarding/01-sms-permission-policy.md](onboarding/01-sms-permission-policy.md), [sms-parsing/README.md](sms-parsing/README.md) | SMS 권한 고지, Play Console release gate |
| `1.0.2` 이후 변경, `1.0.3`, 버전별 개발 요약, 릴리즈 노트, 태그 diff | [release-history/02-release-1.0.3-development-summary.md](release-history/02-release-1.0.3-development-summary.md), [release-history/01-release-timeline.md](release-history/01-release-timeline.md) | 1.0.3 기능·안정화·검증·운영 결정과 28개 커밋 이력 |

## 패키지 이동 시 갱신

패키지 이동, 신규 패키지, 클래스 이동이 확인되면 함께 갱신한다.

- 이 문서의 경로별 라우팅 표
- [01-structure-map.md](01-structure-map.md)
- 관련 패키지 `README.md`
- 관련 패키지 `05-file-inventory.md`
- [00-change-index.md](00-change-index.md)
- 관련 패키지 `change-log.md`
