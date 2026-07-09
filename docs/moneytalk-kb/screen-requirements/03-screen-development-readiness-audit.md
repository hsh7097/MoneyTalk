---
type: audit
title: Screen Development Readiness Audit
description: MoneyTalk 화면별 KB가 실제 개발·수정에 충분한지 코드 진입점과 대조한 결과다.
tags: [moneytalk, screen, requirements, audit, readiness]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-09T06:40:00+09:00
status: draft
---

# Screen Development Readiness Audit

> 기준: 2026-07-09 현재 화면 코드와 KB 문서 확인
> 실행 계획: [02-screen-development-audit-plan.md](02-screen-development-audit-plan.md)

## 결론

현재 KB만으로 하단 탭 4개, 거래 편집, 주요 기능 변경은 개발 진입이 가능하다.
이전 감사에서 README 중심이라 조건부 가능으로 남긴 Category Detail, Transaction Detail List, Category Settings, Store Rule Settings도 `package-reference`를 추가해 큰 UI/상태 변경 전 확인할 세부 문서를 갖췄다.

## 화면별 판정

| 화면 | 코드 진입점 | 담당 KB | 판정 | 근거와 보완 포인트 |
|---|---|---|---|---|
| Splash | `feature/splash/ui/SplashScreen.kt` | [onboarding](../onboarding/README.md) | 조건부 가능 | 초기 진입 문맥은 정리되어 있다. 강제 업데이트나 cache 정책 변경은 `MainActivity`/`app-shell`까지 같이 확인한다. |
| Onboarding | `feature/intro/ui/IntroActivity.kt`, `OnboardingScreen.kt` | [onboarding](../onboarding/README.md) | 개발 가능 | 온보딩 화면, 권한 안내, 완료 상태가 README와 permission policy에 연결되어 있다. |
| Permission | `feature/intro/ui/PermissionScreen.kt`, `IntroActivity.requestNotificationPermission()` | [onboarding](../onboarding/README.md), [notification-ingestion](../notification-ingestion/README.md) | 개발 가능 | SMS 권한 고지와 알림 접근 권한이 분리되어 있다. Play Console 고지 변경 시 [01-sms-permission-policy.md](../onboarding/01-sms-permission-policy.md)를 같이 갱신한다. |
| Main shell | `MainActivity.kt`, `MainViewModel.kt`, `navigation/Screen.kt`, `navigation/NavGraph.kt` | [app-shell](../app-shell/README.md), [02-screen-entry-paths](../02-screen-entry-paths.md) | 개발 가능 | 하단 탭 route 4개와 Activity-scoped 권한/동기화/광고/스낵바 책임이 라우팅되어 있다. |
| Home | `feature/home/ui/HomeScreen.kt`, `HomeViewModel.kt` | [home](../home/README.md), [home surface map](../home/package-reference/05-surface-map.md) | 개발 가능 | `package-reference`가 entry/data/rendering/checklist/surface map을 갖고 있어 월 이동, SMS CTA, 카테고리, AI 인사이트 수정 위치를 바로 찾을 수 있다. |
| History | `feature/history/ui/HistoryScreen.kt`, `HistoryViewModel.kt`, `HistoryFilter.kt` | [history](../history/README.md), [filtering](../filtering/README.md) | 개발 가능 | 목록/달력/수입 mode, 필터 bottom sheet, 카드 숨김/통계 제외 연결이 문서화되어 있다. |
| Chat | `feature/chat/ui/ChatScreen.kt`, `ChatViewModel.kt`, `feature/chat/data/**` | [chat](../chat/README.md), [chat contract](../chat/05-system-contract.md) | 개발 가능 | UI, 세션, Local Fast Path, Gemini 3-step, credit policy, App Functions 경계가 분리되어 있다. |
| Settings | `feature/settings/ui/SettingsScreen.kt`, `SettingsViewModel.kt` | [settings](../settings/README.md), [settings menu map](../settings/package-reference/05-menu-map.md) | 개발 가능 | 메뉴별 하위 Activity, dialog, DataStore/Repository 영향이 menu map에 정리되어 있다. |
| Category Detail | `CategoryDetailActivity.open(...)`, `CategoryDetailScreen.kt`, `CategoryDetailViewModel.kt` | [category-detail](../category-detail/README.md), [category-detail package-reference](../category-detail/package-reference/README.md) | 개발 가능 | Activity extra, page cache, category filter, 정렬, 거래 mutation을 package-reference로 분리했다. |
| Transaction Edit | `TransactionEditActivity.kt`, `TransactionEditScreen.kt`, `TransactionEditViewModel.kt` | [transaction-edit](../transaction-edit/README.md), [transaction-mutation](../transaction-mutation/README.md) | 개발 가능 | 거래 추가/수정, 수입/지출 extra, 카테고리 picker, 일괄 적용, 삭제 흐름이 package-reference로 나뉘어 있다. |
| Transaction Detail List | `TransactionDetailListActivity.open(context, date)`, `TransactionDetailListScreen.kt`, `TransactionDetailListViewModel.kt` | [transaction-list](../transaction-list/README.md), [transaction-list package-reference](../transaction-list/package-reference/README.md) | 개발 가능 | 날짜 extra, 카드 숨김 필터, refresh, 수입/지출 편집 진입을 package-reference로 분리했다. |
| SMS Settings | `SmsSettingsActivity.open(context)`, `SmsSettingsScreen.kt`, `SmsSettingsViewModel.kt` | [sms-settings](../sms-settings/README.md), [sms-parsing](../sms-parsing/README.md), [filtering](../filtering/README.md) | 개발 가능 | 제외 키워드/차단 발신자와 파싱 pre-filter 영향이 연결되어 있다. 저장소 변경은 SMS pipeline까지 확인한다. |
| AI Credit | `AiCreditActivity.open(context)`, `AiCreditScreen.kt`, `AiCreditViewModel.kt` | [ai-credit-screen](../ai-credit-screen/README.md), [budget-credit-monetization](../budget-credit-monetization/README.md) | 개발 가능 | 잔액/원장 화면과 feature gate, 보상형 광고 정책이 연결되어 있다. debug/release 노출 조건은 `CreditFeaturePolicy`를 확인한다. |
| Category Settings | `CategorySettingsActivity.open(context)`, `CategorySettingsScreen.kt`, `CategorySettingsViewModel.kt` | [category-settings](../category-settings/README.md), [category-settings package-reference](../category-settings/package-reference/README.md), [category-classification](../category-classification/README.md) | 개발 가능 | custom category CRUD, validation, CategoryProvider cache 무효화, 기존 거래 영향 검증 질문을 package-reference로 분리했다. |
| Store Rule Settings | `StoreRuleSettingsActivity.open(context)`, `StoreRuleSettingsScreen.kt`, `StoreRuleSettingsViewModel.kt` | [store-rule-settings](../store-rule-settings/README.md), [store-rule-settings package-reference](../store-rule-settings/package-reference/README.md), [transaction-mutation](../transaction-mutation/README.md) | 개발 가능 | 규칙 CRUD, 소급 적용, category select, 코치마크 target을 package-reference로 분리했다. |
| Coachmark/Guide | `core/ui/coachmark/**`, `feature/**/coachmark/*CoachMark.kt` | [coachmark](../coachmark/README.md) | 개발 가능 | target registry, visible step filtering, seen state, reset flow가 기능 KB에 정리되어 있다. |

## 교차 기능 판정

| 기능 | 코드 기준 | 담당 KB | 판정 | 확인 결과 |
|---|---|---|---|---|
| 필터/카드 숨김 | `HistoryFilter.kt`, `CardVisibilityFilter.kt`, `SettingsDataStore` | [filtering](../filtering/README.md), [history](../history/README.md) | 개발 가능 | History, Home, Category Detail, Transaction List가 카드 숨김/선택 필터를 참조한다. |
| 문자 파싱 | `core/sms/**`, `core/sync/**`, `MainViewModel.syncSmsV2` | [sms-parsing](../sms-parsing/README.md), [sms-pipeline](../sms-pipeline/README.md) | 개발 가능 | batch/instant SMS, sender regex, vector/LLM 경로가 기능 KB로 분리되어 있다. |
| 임베딩 | `SmsEmbeddingService.kt`, `VectorSearchEngine.kt`, `StoreEmbedding*` | [embedding](../embedding/README.md), [category-classification](../category-classification/README.md) | 개발 가능 | SMS 유사도와 거래처 embedding이 별도 KB로 분리되어 분류 영향 확인이 가능하다. |
| 자체 거래 알림 표시 | `SmsNotificationManager.kt`, `SmsInstantProcessor.kt`, `SettingsScreen.kt` | [notification-display](../notification-display/README.md) | 개발 가능 | 거래 저장 후 MoneyTalk 앱 알림 표시, 설정 toggle, 숨김 정책이 별도 KB로 연결되어 있다. |
| 외부 알림/RCS 수신 | `core/notification/**`, `NotificationTransactionService` | [notification-ingestion](../notification-ingestion/README.md) | 개발 가능 | 외부 금융앱 알림/RCS 수신과 자체 앱 노티 표시가 분리되어 있다. |
| 화면 refresh | `DataRefreshEvent`, 주요 ViewModel cache refresh | [data-refresh](../data-refresh/README.md) | 개발 가능 | 거래/카테고리/설정 변경 후 stale cache를 확인할 수 있는 기능 KB가 있다. |

## 보조 화면 보강 판단

`category-detail`, `transaction-list`, `category-settings`, `store-rule-settings`는 이제 README-only 상태가 아니다.
아래 변경은 각 패키지의 `package-reference/README.md`에서 entry/data/rendering/checklist를 먼저 본 뒤 진행한다.

| 화면 | 바로 가능한 변경 | 세부 변경 시 먼저 볼 문서 |
|---|---|---|
| Category Detail | 문구, 카드 표시, 단순 정렬 label, 거래 편집 진입 | [../category-detail/package-reference/README.md](../category-detail/package-reference/README.md) |
| Transaction Detail List | 날짜 header, empty/loading UI, 거래 카드 클릭 | [../transaction-list/package-reference/README.md](../transaction-list/package-reference/README.md) |
| Category Settings | add/delete UI 문구, validation 메시지 | [../category-settings/package-reference/README.md](../category-settings/package-reference/README.md) |
| Store Rule Settings | 규칙 목록/추가/삭제 UI | [../store-rule-settings/package-reference/README.md](../store-rule-settings/package-reference/README.md) |

## 다음 작업자가 보는 순서

1. 화면 이름으로 [01-screen-requirements-index.md](01-screen-requirements-index.md)를 본다.
2. 담당 화면 KB의 `README.md`를 본다.
3. `package-reference`가 있으면 `01-entry-screen.md`, `02-data-viewmodel.md`, `03-rendering-action.md`, `04-files-checklist.md` 순서로 본다.
4. 설정 하위 화면은 [settings menu map](../settings/package-reference/05-menu-map.md)에서 진입 row와 영향 데이터를 먼저 본다.
5. 필터, 문자 파싱, 임베딩, 알림 표시, refresh가 걸리면 화면 KB에서 연결된 기능 KB를 같이 본다.
