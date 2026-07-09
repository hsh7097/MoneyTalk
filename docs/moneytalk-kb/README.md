---
type: kb-root
title: MoneyTalk KB
description: MoneyTalk Android 코드 작업을 위한 AI용 라우팅 및 구조 지식 베이스다.
tags: [moneytalk, kb, android, compose, room]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-09T03:20:00+09:00
status: draft
---

# MoneyTalk KB

> 상태: draft
> 기준: 2026-07-08 현재 `app/src/main/java/com/sanha/moneytalk` 소스와 기존 `docs/` 문서 확인

이 KB는 MoneyTalk Android 작업에서 AI가 필요한 문서만 골라 읽도록 돕는 코드 분석 문서다.
전체 소스를 매번 읽지 않고, 변경 파일 경로를 기준으로 도메인 또는 서브모듈 문서로 내려가는 것을 목표로 한다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-agent-routing.md](00-agent-routing.md) | 변경 파일 경로를 KB 문서로 연결한다. | 작업 시작 또는 자동화 실행 시 가장 먼저 본다. |
| [01-structure-map.md](01-structure-map.md) | 앱 전체 패키지 구조와 핵심 파일 위치를 정리한다. | 변경 파일이 어느 책임에 속하는지 판단할 때 본다. |
| [02-screen-entry-paths.md](02-screen-entry-paths.md) | 화면별 사용자 진입 경로, 코드 route, Activity entry를 정리한다. | 실기기 QA, 화면 이동, route/intent 변경 시 본다. |
| [screen-requirements/README.md](screen-requirements/README.md) | 화면별 요구사항, 작업 계획서, 개발 가능성 감사표를 정리한다. | 특정 화면을 KB만 보고 수정할 수 있는지 판단할 때 본다. |
| [00-change-index.md](00-change-index.md) | KB 변경 이력 색인이다. | KB가 왜 바뀌었는지 확인할 때 본다. |
| [source-docs/README.md](source-docs/README.md) | 기존 `docs/*.md`의 KB 흡수/참조 기준을 정리한다. | 루트 문서 내용이 어느 KB로 흡수됐는지 판단할 때 본다. |
| [app-shell/README.md](app-shell/README.md) | 앱 진입, 하단 탭 4개, NavGraph, Activity 전역 다이얼로그 KB다. | `MainActivity`, `MainViewModel`, `navigation/**` 작업 시 본다. |
| [home/README.md](home/README.md) | 홈 탭 도메인 KB 진입점이다. | `feature/home/ui/**` 또는 홈 월별 현황/카테고리/AI 인사이트 작업 시 본다. |
| [history/README.md](history/README.md) | 내역 화면 도메인 KB 진입점이다. | `feature/history/**` 또는 거래 목록/필터/달력/상세 작업 시 본다. |
| [chat/README.md](chat/README.md) | AI 채팅 기능/화면 KB 진입점이다. | `feature/chat/**`, `ChatCreditPolicy`, `DataQueryParser`, 토큰 비용 절감 경로를 볼 때 본다. |
| [settings/README.md](settings/README.md) | 설정 탭 도메인 KB 진입점이다. | `feature/settings/**`, 예산/API key/백업/카드 보유 작업 시 본다. |
| [transaction-edit/README.md](transaction-edit/README.md) | 거래 추가/수정 화면 KB다. | 아이템 변경, 카테고리 picker, 일괄 적용 작업 시 본다. |
| [category-detail/README.md](category-detail/README.md) | 카테고리 상세 화면 KB다. | 홈 카테고리 클릭 후 상세/정렬/월 이동 작업 시 본다. |
| [category-settings/README.md](category-settings/README.md) | 사용자 카테고리 설정 화면 KB다. | custom category 추가/수정/삭제 작업 시 본다. |
| [transaction-list/README.md](transaction-list/README.md) | 조건 기반 거래 상세 목록 화면 KB다. | 카테고리/조건별 거래 목록 상세 작업 시 본다. |
| [sms-settings/README.md](sms-settings/README.md) | SMS 제외 키워드/발신자 설정 화면 KB다. | SMS 제외/차단 설정 UI 작업 시 본다. |
| [store-rule-settings/README.md](store-rule-settings/README.md) | 거래처 규칙 설정 화면 KB다. | 거래처 규칙/가게명 매핑 설정 작업 시 본다. |
| [ai-credit-screen/README.md](ai-credit-screen/README.md) | AI 크레딧 화면 KB다. | 크레딧 잔액/원장 화면 작업 시 본다. |
| [onboarding/README.md](onboarding/README.md) | Splash, Intro, Permission 화면 KB다. | 초기 진입/권한 안내 작업 시 본다. |
| [sms-parsing/README.md](sms-parsing/README.md) | 문자 파싱 기능 KB 진입점이다. | 문자 읽기, 거래 추출, DB 저장, 화면 refresh 흐름을 볼 때 본다. |
| [category-classification/README.md](category-classification/README.md) | 카테고리 분류 기능 KB 진입점이다. | 자동/수동 카테고리 분류, Gemini, 벡터 캐시, 거래처 규칙을 볼 때 본다. |
| [embedding/README.md](embedding/README.md) | SMS/거래처 embedding, vector search, 유사도 정책 KB다. | `SmsEmbeddingService`, `VectorSearchEngine`, `StoreEmbedding*`, `core/similarity/**` 작업 시 본다. |
| [coachmark/README.md](coachmark/README.md) | 화면별 코치마크 기능 KB다. | `core/ui/coachmark/**`, `feature/**/coachmark/**` 작업 시 본다. |
| [filtering/README.md](filtering/README.md) | 내역 필터, 카드 숨김, 통계 제외, SMS 제외 기능 KB다. | 필터/노출 제외/집계 제외 작업 시 본다. |
| [transaction-mutation/README.md](transaction-mutation/README.md) | 거래 추가/수정/삭제와 일괄 적용 기능 KB다. | 아이템 변경 로직 또는 채팅 action 작업 시 본다. |
| [budget-credit-monetization/README.md](budget-credit-monetization/README.md) | 예산, AI 크레딧, 광고 정책 기능 KB다. | 크레딧/광고/예산 작업 시 본다. |
| [backup-restore/README.md](backup-restore/README.md) | 로컬/Google Drive 백업 복원 기능 KB다. | export/import/Drive/전체 삭제 작업 시 본다. |
| [notification-ingestion/README.md](notification-ingestion/README.md) | 금융앱 알림/RCS/비즈메시지 거래 수신 기능 KB다. | `core/notification/**`, 앱 알림 파싱 작업 시 본다. |
| [notification-display/README.md](notification-display/README.md) | 거래 저장 후 MoneyTalk 자체 앱 알림 표시 기능 KB다. | `SmsNotificationManager`, 거래 알림 toggle, 노티 표시/정리 작업 시 본다. |
| [data-refresh/README.md](data-refresh/README.md) | ViewModel 간 refresh event와 cache 갱신 기능 KB다. | `DataRefreshEvent` 또는 화면 stale data 작업 시 본다. |
| [app-functions/README.md](app-functions/README.md) | App Functions 기능 KB 진입점이다. | agent가 앱 데이터를 읽거나 일부 설정/거래를 수정하는 경로를 볼 때 본다. |
| [sms-pipeline/README.md](sms-pipeline/README.md) | SMS 파싱 파이프라인 서브모듈 KB 진입점이다. | `core/sms/**`, `core/sync/**`, `receiver/**` 변경 시 본다. |
| [finance-data/README.md](finance-data/README.md) | Room DB, DAO, Repository, 금융 데이터 서브모듈 KB 진입점이다. | `core/database/**`, `feature/home/data/**` 변경 시 본다. |

## 현재 포함 범위

| 패키지 | 기준 코드 경로 | 상태 | 설명 |
|---|---|---|---|
| `app-shell` | `MainActivity.kt`, `MainViewModel.kt`, `navigation/**`, `MainUiState.kt` | draft | 앱 진입, 하단 탭 4개, NavGraph, Activity 전역 dialog/snackbar |
| `home` | `feature/home/ui/**`, `feature/home/data/**` | draft | 홈 탭, 월별 현황, 카테고리 지출, AI 인사이트, 미분류 분류 CTA, 홈 코치마크 |
| `history` | `app/src/main/java/com/sanha/moneytalk/feature/history/` | draft | 내역 화면, 월별 pager, 필터, 달력, 거래 상세/수정 진입 |
| `settings` | `feature/settings/ui/**` | draft | 설정 탭, 예산, API key, 백업/복원, Google Drive, 카드 보유 |
| `transaction-edit` | `feature/transactionedit/ui/**` | draft | 지출/수입 추가·수정, 카테고리 picker, 일괄 적용, 삭제 |
| `category-detail` | `feature/categorydetail/ui/**` | draft | 카테고리 상세 화면, 월 이동, 정렬, 거래 목록 |
| `category-settings` | `feature/categorysettings/ui/**`, `CustomCategoryRepository` | draft | 사용자 카테고리 관리 |
| `transaction-list` | `feature/transactionlist/ui/**` | draft | 조건 기반 거래 상세 목록 |
| `sms-settings` | `feature/smssettings/ui/**`, SMS 제외/차단 repository | draft | SMS 제외 키워드/발신자 설정 |
| `store-rule-settings` | `feature/storerulesettings/ui/**`, `StoreRuleRepository` | draft | 거래처 규칙 설정 |
| `ai-credit-screen` | `feature/aicredit/ui/**`, `AiCreditRepository` | draft | AI 크레딧 잔액/원장 화면 |
| `onboarding` | `feature/splash/**`, `feature/intro/**` | draft | Splash, Intro, Permission 화면 |
| `sms-parsing` | `MainViewModel.kt`, `core/sms/**`, `core/sync/**`, `core/database/**` | draft | 문자 읽기, 파싱, 카테고리 선분류, 지출/수입 저장, refresh |
| `category-classification` | `feature/home/data/*Category*`, `StoreEmbedding*`, `StoreRule*` | draft | 4-tier 카테고리 분류, 수동 수정 학습, 수입 분류 |
| `chat` | `feature/chat/**`, `core/util/LocalChatQueryRouter.kt`, `core/util/ChatCreditPolicy.kt`, `core/util/DataQueryParser.kt`, `core/util/ChatContextBuilder.kt` | draft | Gemini 3-step 채팅, 로컬 단순 조회 우회, 앱 내부 DB 조회/분석, 토큰 비용 절감 구조 |
| `embedding` | `SmsEmbeddingService.kt`, `VectorSearchEngine.kt`, `StoreEmbedding*`, `core/similarity/**` | draft | SMS/거래처 embedding, vector search, 유사도 정책 |
| `coachmark` | `core/ui/coachmark/**`, `feature/**/coachmark/**` | draft | 화면별 온보딩 target registry와 overlay |
| `filtering` | `HistoryFilter.kt`, `CardVisibilityFilter.kt`, `StatsExclusionClassifier.kt`, SMS 제외 repository | draft | 내역 필터, 카드 숨김, 통계 제외, SMS 제외 |
| `transaction-mutation` | `TransactionEditViewModel.kt`, `HistoryDialogs.kt`, `ExpenseRepository`, `IncomeRepository`, `DataQueryParser` | draft | 거래 추가/수정/삭제, 일괄 적용, 채팅 action |
| `budget-credit-monetization` | `core/ad/**`, `AiCreditRepository`, `BudgetDao`, `ChatCreditPolicy` | draft | 예산, AI 크레딧, 보상형 광고, 배너 정책 |
| `backup-restore` | `DataBackupManager.kt`, `SettingsDataDialogs.kt`, `GoogleDriveHelper.kt` | draft | 로컬/Google Drive 백업 복원 |
| `notification-ingestion` | `core/notification/**`, `AppNotificationTransactionParser.kt`, `receiver/**` | draft | 금융앱 알림/RCS/비즈메시지 거래 수신 |
| `notification-display` | `SmsNotificationManager.kt`, `SmsInstantProcessor.kt`, `SettingsScreen.kt` | draft | 거래 저장 후 MoneyTalk 자체 알림 표시/정리 |
| `data-refresh` | `DataRefreshEvent.kt`, 주요 화면 ViewModel | draft | 화면 간 변경 통지와 page cache refresh |
| `app-functions` | `core/appfunctions/**` | draft | agent용 App Function 읽기/수정 함수, reader/model contract |
| `sms-pipeline` | `app/src/main/java/com/sanha/moneytalk/core/sms/` | draft | SMS/MMS/RCS 읽기, 사전 필터, 수입 분류, sender regex Fast Path, Vector/LLM 파싱 |
| `finance-data` | `app/src/main/java/com/sanha/moneytalk/core/database/`, `feature/home/data/` | draft | Room DB, DAO, Repository, 카테고리/거래처/크레딧 데이터 흐름 |
| `source-docs` | `docs/*.md` | draft | 기존 루트 문서의 KB 흡수/참조 위치와 계획/이력 문서 분리 기준 |

## 작성 원칙

1. 현재 코드와 기존 문서로 확인한 내용만 기록한다.
2. 계획, PRD, 미래 변경 목표는 KB 본문에 넣지 않는다.
3. 패키지별 상세 변경은 해당 `change-log.md`에 남긴다.
4. 루트 구조나 라우팅이 바뀌면 `00-change-index.md`에도 색인을 추가한다.
5. 문서와 코드가 다르면 코드 확인 결과를 기준으로 문서를 갱신한다.
