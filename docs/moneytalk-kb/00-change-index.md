---
type: log
title: MoneyTalk KB Change Index
description: MoneyTalk KB의 루트 변경 이력 색인이다.
tags: [moneytalk, kb, changelog, android]
resource: docs/moneytalk-kb/
timestamp: 2026-07-13T01:11:00+09:00
status: draft
---

# MoneyTalk KB Change Index

상세 설명은 각 패키지 `change-log.md`에 둔다.
이 파일은 전체 KB에서 어떤 영역이 바뀌었는지 찾기 위한 짧은 색인이다.

| 날짜 | 기준 | 영향 영역 | 갱신 문서 | 요약 |
|---|---|---|---|---|
| 2026-07-03 | 현재 소스와 초기 루트 구조/SMS/App Functions 문서 확인 | 초기 KB 생성 | `README.md`, `00-agent-routing.md`, `01-structure-map.md`, `history/**`, `sms-pipeline/**`, `finance-data/**` | 스카폴드 기준으로 MoneyTalk 초기 KB를 생성하고 핵심 라우팅, History 도메인, SMS pipeline, finance data 문서를 작성. |
| 2026-07-03 | 기능 단위 KB 요구 반영 | 기능 KB 추가 | `README.md`, `00-agent-routing.md`, `01-structure-map.md`, `sms-parsing/**`, `category-classification/**`, `app-functions/**` | 문자 파싱, 카테고리 분류, App Functions 기능을 도메인/서브모듈과 별도 end-to-end 기능 KB로 추가. |
| 2026-07-08 | `feature/chat/**`, `DataQueryParser`, `ChatCreditPolicy`, `ChatContextBuilder`, 크레딧/심층 분석 계획 확인 | AI 채팅 KB 추가 | `README.md`, `00-agent-routing.md`, `01-structure-map.md`, `chat/**` | 단순 조회도 Gemini에 조회 결과를 다시 보내는 현재 비용 구조와 로컬 정형 조회 우회 설계를 `chat` KB로 분리. |
| 2026-07-08 | `LocalChatQueryRouter`, `ChatViewModel`, `ChatRepositoryImpl`, `LocalChatQueryRouterTest` 확인 | AI 채팅 로컬 조회 구현 | `chat/README.md`, `chat/change-log.md`, 일반 채팅/수익화 문서 | 총 지출, 카테고리 지출, 최근 지출, 예산 현황 등 안전한 단순 조회를 Gemini analyze/final/summary 밖에서 처리하는 1차 구현을 기록. |
| 2026-07-08 | `MainActivity`, `navigation/**`, `feature/home/**`, `feature/settings/**`, `feature/transactionedit/**`, 보조 화면 package 확인 | 화면별 KB 확장 | `app-shell/**`, `home/**`, `settings/**`, `transaction-edit/**`, `category-detail/**`, `category-settings/**`, `transaction-list/**`, `sms-settings/**`, `store-rule-settings/**`, `ai-credit-screen/**`, `onboarding/**` | 하단 탭 4개와 주요 보조 화면의 화면별 KB를 스카폴드 기준으로 생성. |
| 2026-07-08 | `SmsEmbeddingService`, `core/ui/coachmark/**`, `HistoryFilter`, `DataBackupManager`, `core/notification/**`, `DataRefreshEvent` 등 확인 | 기능별 KB 확장 | `embedding/**`, `coachmark/**`, `filtering/**`, `transaction-mutation/**`, `budget-credit-monetization/**`, `backup-restore/**`, `notification-ingestion/**`, `data-refresh/**` | 임베딩, 코치마크, 필터링, 거래 변경, 크레딧/광고/예산, 백업/복원, 알림 거래 수신, 화면 refresh 기능 KB를 추가. |
| 2026-07-08 | `feature/chat/ui/**`, `ChatViewModel`, `ChatRepositoryImpl`, `GeminiRepositoryImpl` 확인 | Chat 화면 KB 보강 | `chat/00-structure-map.md`, `chat/package-reference/**`, `chat/README.md`, `00-agent-routing.md` | 하단 탭 Chat도 화면별 KB로 탐색할 수 있게 entry/data/rendering/checklist 문서를 추가. |
| 2026-07-08 | `AndroidManifest.xml`, `navigation/**`, 주요 `*Activity.open()` 호출부, `SettingsScreen`, `SmsSettingsScreen` 확인 | 화면 진입 경로 색인 추가 | `02-screen-entry-paths.md`, `README.md`, `00-agent-routing.md`, `01-structure-map.md` | 실기기 QA와 route/intent 변경 시 볼 화면별 사용자 진입 경로와 코드 entry를 한 문서로 정리. |
| 2026-07-08 | `RewardAdManager`, `AiCreditRepository`, `MainViewModel`, `ChatCreditPolicy`, `CreditFeaturePolicy` 확인 | 크레딧 사용 정책 전환 | `chat/**`, `budget-credit-monetization/**`, 일반 docs | 채팅 1회 1크레딧, 과거 월 문자 가져오기 1크레딧, 광고 1회 2크레딧 정책과 프리미엄 크레딧 비노출 기반을 기록. |
| 2026-07-09 | SM-F966N debug APK 실기기 화면 진입 smoke, `TransactionEditActivity`, `TransactionDetailListScreen`, `CreditFeaturePolicy` 확인 | 화면 진입 KB 실기기 보정 | `transaction-edit/package-reference/01-entry-screen.md`, `transaction-edit/change-log.md`, `transaction-list/README.md`, `transaction-list/change-log.md`, `settings/package-reference/01-entry-screen.md`, `ai-credit-screen/README.md`, `ai-credit-screen/change-log.md` | 수입/지출 거래 편집 extra 계약과 debug build의 AI 크레딧 row 숨김 조건을 실기기 확인 결과로 보강. |
| 2026-07-09 | `HomeScreen`, `SettingsScreen`, `HistoryFilter`, `SmsNotificationManager`, `NotificationTransactionService`, `SmsEmbeddingService` 확인 | 화면/필터/알림/임베딩 KB 상세화 | `home/package-reference/05-surface-map.md`, `settings/package-reference/05-menu-map.md`, `filtering/02-filter-surfaces.md`, `notification-ingestion/01-feature-flow.md`, `notification-display/**`, `embedding/**` | 홈 블록, 설정 메뉴, 필터 계층, 외부 알림 수신, 자체 노티 표시, 임베딩 contract/checklist를 코드 기준으로 보강. |
| 2026-07-09 | `core/appfunctions/**`, KSP 생성 `app_functions.xml`, App Functions 원문 문서 확인 | App Functions KB 상세화 | `app-functions/06-function-catalog.md`, `app-functions/07-operational-playbook.md`, `app-functions/**` | 등록 함수 50개, disabled 삭제 함수 4개, DB 점검/복원 검증 플레이북을 KB에 흡수. |
| 2026-07-09 | 기존 `docs/*.md` 확인. `moneytalk-kb/**`, `kb-scaffold/**` 제외 | 루트 문서 흡수 기준 추가 | `source-docs/**`, `sms-pipeline/06-rule-json-guide.md`, `onboarding/01-sms-permission-policy.md`, `README.md`, `00-agent-routing.md`, `01-structure-map.md` | 기존 루트 문서를 기능 KB로 라우팅하고, SMS 룰 운영 가이드와 SMS 권한 release gate를 KB에 흡수. |
| 2026-07-09 | 화면 코드 진입점과 `moneytalk-kb/**` 문서 대조 | 화면 개발 가능성 감사 | `screen-requirements/02-screen-development-audit-plan.md`, `screen-requirements/03-screen-development-readiness-audit.md`, `screen-requirements/README.md` | KB만 보고 각 화면을 개발·수정할 수 있는지 확인하는 작업 계획서와 화면별 판정표를 추가. |
| 2026-07-09 | `categorydetail`, `transactionlist`, `categorysettings`, `storerulesettings` 화면 코드 재확인 | 보조 화면 package-reference 보강 | `category-detail/package-reference/**`, `transaction-list/package-reference/**`, `category-settings/package-reference/**`, `store-rule-settings/package-reference/**`, `00-agent-routing.md`, `screen-requirements/03-screen-development-readiness-audit.md` | 작은 수정 가능으로 남긴 보조 화면 4개를 entry/data/rendering/checklist 기준 세부 KB로 확장하고 개발 가능 판정으로 갱신. |
| 2026-07-13 | `1.0.2..fa07dda` 커밋과 2026-07-12~13 코드·검증 KB 대조 | 1.0.3 릴리즈 이력 통합 | `release-history/02-release-1.0.3-development-summary.md`, `release-history/README.md`, `release-history/01-release-timeline.md`, `release-history/change-log.md`, `README.md`, `00-agent-routing.md` | AI 크레딧, Firebase AI Logic, SMS 룰·표본 정책, local embedding, sync race, UI, 검증과 잔여 운영 확인을 한 진입점으로 통합하고 태그 diff 요청을 routing에 연결. |
