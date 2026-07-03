---
type: routing
title: MoneyTalk Agent Routing
description: 변경 파일 경로와 작업 키워드를 기준으로 AI가 읽을 KB 문서를 결정한다.
tags: [moneytalk, kb, routing, android]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-03T16:30:00+09:00
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

## 경로별 문서 라우팅

| 변경 파일 경로 또는 키워드 | 우선 참조 문서 | 비고 |
|---|---|---|
| `app/src/main/java/com/sanha/moneytalk/MainActivity.kt`, `MainViewModel.kt`, `navigation/**` | [01-structure-map.md](01-structure-map.md), 영향 도메인 README | 앱 진입, 탭, Activity-scoped sync 상태 |
| `app/src/main/java/com/sanha/moneytalk/feature/history/**` | [history/README.md](history/README.md), [history/00-structure-map.md](history/00-structure-map.md), [history/package-reference/README.md](history/package-reference/README.md) | 내역 화면, 필터, 달력, 상세/수정 진입 |
| `app/src/main/java/com/sanha/moneytalk/core/sms/**` | [sms-pipeline/README.md](sms-pipeline/README.md), [sms-pipeline/00-structure-map.md](sms-pipeline/00-structure-map.md) | SMS 파싱, Fast Path, Vector/LLM extraction |
| `app/src/main/java/com/sanha/moneytalk/core/sync/**` | [sms-pipeline/README.md](sms-pipeline/README.md), [sms-pipeline/04-files-checklist.md](sms-pipeline/04-files-checklist.md) | 동기화 범위, coverage, 월별 CTA |
| `app/src/main/java/com/sanha/moneytalk/receiver/**` | [sms-pipeline/README.md](sms-pipeline/README.md), [sms-pipeline/05-file-inventory.md](sms-pipeline/05-file-inventory.md) | SMS/MMS/RCS/알림 실시간 수신 보조 |
| `app/src/main/java/com/sanha/moneytalk/core/database/**` | [finance-data/README.md](finance-data/README.md), [finance-data/00-structure-map.md](finance-data/00-structure-map.md) | Room DB, DAO, migration, entity |
| `app/src/main/java/com/sanha/moneytalk/feature/home/data/**` | [finance-data/README.md](finance-data/README.md), 영향 도메인 README | Expense/Income/Category/StoreRule repository |
| `app/src/main/java/com/sanha/moneytalk/core/appfunctions/**` | [01-structure-map.md](01-structure-map.md), `docs/APP_FUNCTIONS.md` | 별도 `app-functions` KB 후보 |
| `app/src/main/java/com/sanha/moneytalk/feature/chat/**` | [01-structure-map.md](01-structure-map.md), `docs/CHAT_SYSTEM.md` | 별도 `chat` KB 후보 |
| `app/src/main/java/com/sanha/moneytalk/feature/settings/**`, `feature/*settings/**` | [01-structure-map.md](01-structure-map.md), `docs/ARCHITECTURE.md` | 별도 `settings` KB 후보 |
| `app/src/main/java/com/sanha/moneytalk/core/ui/**`, `core/theme/**` | [01-structure-map.md](01-structure-map.md), 영향 도메인의 rendering/action 문서 | 공통 Compose UI |

## 패키지 이동 시 갱신

패키지 이동, 신규 패키지, 클래스 이동이 확인되면 함께 갱신한다.

- 이 문서의 경로별 라우팅 표
- [01-structure-map.md](01-structure-map.md)
- 관련 패키지 `README.md`
- 관련 패키지 `05-file-inventory.md`
- [00-change-index.md](00-change-index.md)
- 관련 패키지 `change-log.md`
