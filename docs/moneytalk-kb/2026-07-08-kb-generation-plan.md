---
type: work-plan
title: MoneyTalk 화면별/기능별 KB 생성 계획표
description: 2026-07-08 MoneyTalk KB 확장 작업의 분석 결과, 생성 범위, 진행 상태를 추적한다.
tags: [moneytalk, kb, plan, screens, features]
resource: docs/moneytalk-kb/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# MoneyTalk 화면별/기능별 KB 생성 계획표

> 상태: 진행 중
> 기준: `docs/kb-scaffold/README.md`, `docs/kb-scaffold/03-authoring-workflow.md`, 현재 `app/src/main/java/com/sanha/moneytalk` 소스 확인
> 제거: 작업 완료 후 사용자가 계획표 제거를 지시하면 삭제한다.

## 0. 선행 완료

| 항목 | 상태 | 근거 |
|---|---|---|
| 현재 작업 커밋 | 완료 | `105805e docs: MoneyTalk KB 스카폴드 기준 보강`, `6cacfc6 feat: Gemini 비용 방어와 로컬 단순 조회 추가` |
| 현재 작업 푸시 | 완료 | `codex/moneytalk-kb-scaffold` -> `origin/codex/moneytalk-kb-scaffold` |
| 검증 | 완료 | `./gradlew testDebugUnitTest assembleDebug` 성공, `git diff --check` 성공 |

## 1. 작성 원칙

| 원칙 | 적용 |
|---|---|
| 스카폴드 기준 | 새 KB는 `README.md` + `change-log.md`로 시작하고, 복잡한 화면/기능은 구조 지도, 파일 인벤토리, package-reference 또는 feature-flow를 확장한다. |
| 코드 근거 | 존재하는 source root와 실제 파일만 기준으로 작성한다. 없는 화면이나 미래 계획은 KB 본문에 넣지 않는다. |
| 화면 KB | 사용자가 화면 수정 시 entry, ViewModel/data, rendering/action, 파일 체크리스트를 빨리 찾도록 만든다. |
| 기능 KB | 화면 경계를 가로지르는 end-to-end 기능 흐름과 contract, 확장 지점을 설명한다. |
| 루트 연결 | 생성/확장한 패키지는 `README.md`, `00-agent-routing.md`, `00-change-index.md`, `01-structure-map.md`에 연결한다. |

## 2. 화면별 KB 생성 계획

| 우선순위 | KB 패키지 | 범위 | 기준 파일 | 스카폴드 수준 | 상태 |
|---:|---|---|---|---|---|
| 1 | `app-shell` | 앱 진입, 하단 탭 4개, NavGraph, Activity 전역 다이얼로그, 뒤로가기 | `MainActivity.kt`, `MainViewModel.kt`, `navigation/**`, `MainUiState.kt` | README, change-log, structure-map, file-inventory | 완료 |
| 2 | `home` | 홈 탭, 월별 현황, 카테고리 지출, AI 인사이트, 미분류 분류 CTA, 홈 코치마크 | `feature/home/ui/**`, `feature/home/data/**` | README, change-log, structure-map, package-reference, file-inventory | 완료 |
| 3 | `history` | 내역 탭, 검색, 달력, 필터, 거래 상세/추가 다이얼로그 | 기존 `history/**`, `HistoryFilter.kt`, `HistoryDialogs.kt` | 기존 KB + `filtering` 기능 KB 연결 | 완료 |
| 4 | `chat-screen` 또는 `chat` 보강 | 채팅 탭 UI, 채팅방 목록/방 내부, 가이드 질문, 광고 다이얼로그, 채팅 코치마크 | `feature/chat/ui/**`, 기존 `chat/**` KB | 기존 `chat` KB 유지 + `coachmark`/`budget-credit-monetization` 기능 KB 연결 | 완료 |
| 5 | `settings` | 설정 탭, 예산, API 키, 테마, 백업/복원, 카드 보유, Google Drive, 설정 코치마크 | `feature/settings/ui/**`, `SettingsViewModel.kt` | README, change-log, structure-map, package-reference, file-inventory | 완료 |
| 6 | `transaction-edit` | 지출/수입 추가·수정, 카테고리 picker, 아이템 변경, 일괄 적용, 삭제, 수정 코치마크 | `feature/transactionedit/ui/**` | README, change-log, structure-map, package-reference, file-inventory | 완료 |
| 7 | `transaction-list` | 카테고리/조건 기반 거래 목록 상세 화면 | `feature/transactionlist/ui/**` | README, change-log | 완료 |
| 8 | `category-detail` | 홈 카테고리 클릭 후 상세 화면, 정렬, 월 이동, 카테고리 거래 목록 | `feature/categorydetail/ui/**` | README, change-log, structure-map | 완료 |
| 9 | `category-settings` | 사용자 카테고리 추가/삭제/수정 화면 | `feature/categorysettings/ui/**`, `CustomCategoryRepository` | README, change-log | 완료 |
| 10 | `sms-settings` | SMS 제외 키워드/차단 발신자 설정 화면 | `feature/smssettings/ui/**`, SMS 설정 repository | README, change-log | 완료 |
| 11 | `store-rule-settings` | 거래처 규칙/가게명 매핑 설정 화면 | `feature/storerulesettings/ui/**`, `StoreRuleRepository` | README, change-log | 완료 |
| 12 | `ai-credit-screen` | AI 크레딧 잔액/원장 화면 | `feature/aicredit/ui/**`, `AiCreditRepository` | README, change-log | 완료 |
| 13 | `onboarding` | Splash, Intro, Permission, 초기 온보딩 | `feature/splash/**`, `feature/intro/**` | README, change-log | 완료 |

## 3. 기능별 KB 생성/보강 계획

| 우선순위 | KB 패키지 | 범위 | 기준 파일 | 스카폴드 수준 | 상태 |
|---:|---|---|---|---|---|
| 1 | `sms-parsing` 보강 | 문자 읽기, 파싱, 동기화, 거래 저장, 화면 refresh | 기존 `sms-parsing/**`, `core/sms/**`, `MainViewModel.kt` | 기존 KB 유지 + 신규 `data-refresh`/`notification-ingestion` 연결 | 완료 |
| 2 | `category-classification` 보강 | 카테고리 파싱/분류, 수동 수정 학습, custom category, Gemini batch | 기존 `category-classification/**`, `feature/home/data/*Category*` | 기존 KB 유지 + 신규 `embedding`/`store-rule-settings` 연결 | 완료 |
| 3 | `embedding` | SMS 패턴/거래처 벡터, 유사도 정책, StoreEmbedding, VectorSearch | `SmsEmbeddingService.kt`, `VectorSearchEngine.kt`, `StoreEmbeddingRepository*`, `core/similarity/**` | README, change-log, structure-map, feature-flow | 완료 |
| 4 | `coachmark` | 화면별 온보딩 target registry, overlay, screen seen 상태 | `core/ui/coachmark/**`, `feature/**/coachmark/**`, `SettingsDataStore` | README, change-log, structure-map, feature-flow | 완료 |
| 5 | `filtering` | 내역 필터, 카드 숨김, 통계 제외, SMS 제외 키워드/발신자 | `HistoryFilter.kt`, `CardVisibilityFilter.kt`, `StatsExclusionClassifier.kt`, `SmsExclusionRepository.kt` | README, change-log, feature-flow | 완료 |
| 6 | `transaction-mutation` | 거래 추가/수정/삭제, 카테고리 일괄 변경, 고정지출/통계 제외 일괄 적용 | `TransactionEditViewModel.kt`, `HistoryDialogs.kt`, `ExpenseRepository.kt`, `IncomeRepository.kt` | README, change-log, feature-flow | 완료 |
| 7 | `budget-credit-monetization` | 예산, AI 크레딧, 보상형 광고, 배너 광고 표시 정책 | `BudgetDao.kt`, `AiCreditRepository.kt`, `core/ad/**`, `RewardAdDialog` | README, change-log, feature-flow | 완료 |
| 8 | `backup-restore` | 로컬 export/import, Google Drive 백업/복원, 전체 삭제 | `DataBackupManager.kt`, `SettingsDataDialogs.kt`, `SettingsViewModel.kt`, `GoogleDriveHelper.kt` | README, change-log, feature-flow | 완료 |
| 9 | `notification-ingestion` | 알림 접근, 금융앱 후보 탐색, 앱 알림 거래 파싱 | `core/notification/**`, `AppNotificationTransactionParser.kt`, receiver | README, change-log, structure-map | 완료 |
| 10 | `data-refresh` | `DataRefreshEvent`, Home/History/CategoryDetail cache refresh, MainViewModel sync notify | `DataRefreshEvent.kt`, 각 ViewModel observe refresh | README, change-log, feature-flow | 완료 |

## 4. 검증 계획

| 검증 | 방법 | 완료 기준 |
|---|---|---|
| 스카폴드 준수 | 각 새 패키지에 `README.md`, `change-log.md` 존재 확인 | `find docs/moneytalk-kb -maxdepth 2 -name README.md -o -name change-log.md` |
| 루트 연결 | 루트 README/routing/structure/change-index에 새 패키지 링크 확인 | `rg "app-shell|home|settings|embedding|coachmark" docs/moneytalk-kb` |
| 링크/경로 검증 | 문서의 source path가 실제 존재하는지 표본 검사 | `test -e <source-path>` 및 `rg --files` |
| Markdown 위생 | trailing whitespace 검사 | `git diff --check` |
| 앱 빌드 영향 | 문서만 변경이면 빌드는 생략 가능하나, 코드 변경이 생기면 `./gradlew assembleDebug` 실행 | 코드 변경 없음 또는 빌드 성공 |

## 5. 진행 상태 요약

| 단계 | 상태 | 메모 |
|---|---|---|
| 분석 | 완료 | 화면/기능 source root 확인 완료 |
| 계획표 작성 | 완료 | 이 파일 |
| 화면 KB 생성 | 완료 | app-shell/home/settings/transaction-edit 상세 KB + 보조 화면 README/change-log |
| 기능 KB 생성 | 완료 | embedding/coachmark/filtering/transaction-mutation/budget-credit-monetization/backup-restore/notification-ingestion/data-refresh |
| 루트 라우팅 연결 | 완료 | README, agent-routing, structure-map, change-index 갱신 |
| 검증/커밋/푸시 | 진행 중 | 문서 경로/링크 검증 후 별도 커밋/푸시 |
