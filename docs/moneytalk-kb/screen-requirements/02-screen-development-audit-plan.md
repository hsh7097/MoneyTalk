---
type: plan
title: Screen Development Audit Plan
description: KB만 보고 화면 개발·수정에 들어갈 수 있는지 확인하기 위한 화면별 감사 계획이다.
tags: [moneytalk, screen, requirements, audit, plan]
resource: docs/moneytalk-kb/screen-requirements/
timestamp: 2026-07-09T06:40:00+09:00
status: draft
---

# Screen Development Audit Plan

> 기준: 2026-07-09 현재 `app/src/main/java/com/sanha/moneytalk/**` 화면 진입점과 `docs/moneytalk-kb/**` 문서 확인

이 문서는 화면 작업자가 KB만 보고 실제 개발·수정에 들어갈 수 있는지 확인하기 위한 감사 계획서다.
결과는 [03-screen-development-readiness-audit.md](03-screen-development-readiness-audit.md)에 기록한다.

## 성공 기준

화면별 KB는 아래 5개 질문에 답할 수 있으면 개발 가능으로 본다.

| 기준 | 확인 질문 |
|---|---|
| 진입점 | 사용자가 어디서 들어오며 코드상 route, Activity, `open()` 계약, intent extra가 어디에 있는가? |
| 화면 구조 | 최상위 Screen, 주요 Composable, dialog/bottom sheet/coachmark가 어디에 있는가? |
| 상태/데이터 | ViewModel, UiState, repository, DataStore, Room DAO 또는 외부 helper 책임이 분리되어 있는가? |
| 영향 범위 | 필터, 카드 숨김, 통계 제외, SMS 파싱, 임베딩, 알림, refresh event 등 교차 기능이 연결되어 있는가? |
| 검증 방법 | 수정 후 어떤 빌드, 화면 진입, DB/알림/필터 확인을 해야 하는지 적혀 있는가? |

## 화면 그룹

| 그룹 | 대상 | 감사 방식 |
|---|---|---|
| 앱 진입/탭 | Splash, Onboarding, Permission, MainActivity, Home, History, Chat, Settings | 코드 route와 화면 KB를 순차 확인한다. |
| 상세/편집 | Category Detail, Transaction Edit, Transaction Detail List | Activity extra, ViewModel load/save, 거래 수정 진입을 집중 확인한다. |
| 설정 하위 | Category Settings, Store Rule Settings, SMS Settings, AI Credit | Settings menu map과 각 하위 README를 병렬로 확인한다. |
| 전역 가이드 | Coachmark, filter bottom sheet, SMS 알림 표시, 알림 수신, 임베딩/파싱 | 화면 README와 기능 KB가 서로 연결되는지 확인한다. |

## 실행 순서

1. `docs/moneytalk-kb/README.md`와 `00-agent-routing.md`에서 화면 라우팅이 존재하는지 확인한다.
2. `screen-requirements/01-screen-requirements-index.md`에서 화면별 요구와 담당 KB를 확인한다.
3. 실제 코드의 `MainActivity`, `navigation/Screen.kt`, `navigation/NavGraph.kt`, 각 `*Activity.open()` 계약을 확인한다.
4. 하단 탭 4개는 `package-reference/**`까지 확인해 개발 가능 여부를 판정한다.
5. README만 있는 보조 화면은 핵심 파일, 상태, 검증 질문이 충분한지 확인하고 부족하면 audit 결과에 보완 지시를 남긴다.
6. 필터, 문자 파싱, 임베딩, 알림 표시처럼 화면 밖 영향이 있는 기능은 기능 KB와 화면 KB가 서로 연결되는지 확인한다.
7. 문서 수정 후 markdown 링크, diff whitespace, debug build, ADB 연결 상태를 검증한다.

## 병렬 확인 기준

아래 그룹은 코드 ownership이 겹치지 않아 병렬로 확인해도 된다.

| 병렬 단위 | 같이 보면 좋은 문서 |
|---|---|
| Home + Category Detail + Transaction Edit | `home/**`, `category-detail/**`, `transaction-edit/**`, `transaction-mutation/**` |
| History + Transaction Detail List + Filtering | `history/**`, `transaction-list/**`, `filtering/**` |
| Settings + 하위 설정 화면 | `settings/package-reference/05-menu-map.md`, `category-settings/**`, `store-rule-settings/**`, `sms-settings/**`, `ai-credit-screen/**` |
| Chat + App Functions + Credit | `chat/**`, `app-functions/**`, `budget-credit-monetization/**` |
| Onboarding + Notification + Coachmark | `onboarding/**`, `notification-ingestion/**`, `notification-display/**`, `coachmark/**` |

## 판정 등급

| 등급 | 의미 | 후속 조치 |
|---|---|---|
| 개발 가능 | KB가 진입점, 상태, 영향 범위, 검증 질문을 모두 제공한다. | 코드 변경 전 해당 KB만 읽고 시작할 수 있다. |
| 조건부 가능 | README로 핵심 파일은 찾을 수 있으나 세부 상태/intent/검증은 코드 확인이 필요하다. | 큰 변경 전 package-reference 추가가 좋다. |
| 보강 필요 | 화면 요구, 코드 진입점, 영향 범위 중 하나 이상이 누락되어 KB만으로 수정 위험이 크다. | 화면별 세부 문서를 먼저 작성한다. |
