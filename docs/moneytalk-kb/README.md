---
type: kb-root
title: MoneyTalk KB
description: MoneyTalk Android 코드 작업을 위한 AI용 라우팅 및 구조 지식 베이스다.
tags: [moneytalk, kb, android, compose, room]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-08T00:00:00+09:00
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
| [00-change-index.md](00-change-index.md) | KB 변경 이력 색인이다. | KB가 왜 바뀌었는지 확인할 때 본다. |
| [history/README.md](history/README.md) | 내역 화면 도메인 KB 진입점이다. | `feature/history/**` 또는 거래 목록/필터/달력/상세 작업 시 본다. |
| [sms-parsing/README.md](sms-parsing/README.md) | 문자 파싱 기능 KB 진입점이다. | 문자 읽기, 거래 추출, DB 저장, 화면 refresh 흐름을 볼 때 본다. |
| [category-classification/README.md](category-classification/README.md) | 카테고리 분류 기능 KB 진입점이다. | 자동/수동 카테고리 분류, Gemini, 벡터 캐시, 거래처 규칙을 볼 때 본다. |
| [chat/README.md](chat/README.md) | AI 채팅 기능 KB 진입점이다. | `feature/chat/**`, `ChatCreditPolicy`, `DataQueryParser`, 토큰 비용 절감 경로를 볼 때 본다. |
| [app-functions/README.md](app-functions/README.md) | App Functions 기능 KB 진입점이다. | agent가 앱 데이터를 읽거나 일부 설정/거래를 수정하는 경로를 볼 때 본다. |
| [sms-pipeline/README.md](sms-pipeline/README.md) | SMS 파싱 파이프라인 서브모듈 KB 진입점이다. | `core/sms/**`, `core/sync/**`, `receiver/**` 변경 시 본다. |
| [finance-data/README.md](finance-data/README.md) | Room DB, DAO, Repository, 금융 데이터 서브모듈 KB 진입점이다. | `core/database/**`, `feature/home/data/**` 변경 시 본다. |

## 현재 포함 범위

| 패키지 | 기준 코드 경로 | 상태 | 설명 |
|---|---|---|---|
| `history` | `app/src/main/java/com/sanha/moneytalk/feature/history/` | draft | 내역 화면, 월별 pager, 필터, 달력, 거래 상세/수정 진입 |
| `sms-parsing` | `MainViewModel.kt`, `core/sms/**`, `core/sync/**`, `core/database/**` | draft | 문자 읽기, 파싱, 카테고리 선분류, 지출/수입 저장, refresh |
| `category-classification` | `feature/home/data/*Category*`, `StoreEmbedding*`, `StoreRule*` | draft | 4-tier 카테고리 분류, 수동 수정 학습, 수입 분류 |
| `chat` | `feature/chat/**`, `core/util/LocalChatQueryRouter.kt`, `core/util/ChatCreditPolicy.kt`, `core/util/DataQueryParser.kt`, `core/util/ChatContextBuilder.kt` | draft | Gemini 3-step 채팅, 로컬 단순 조회 우회, 앱 내부 DB 조회/분석, 토큰 비용 절감 구조 |
| `app-functions` | `core/appfunctions/**` | draft | agent용 App Function 읽기/수정 함수, reader/model contract |
| `sms-pipeline` | `app/src/main/java/com/sanha/moneytalk/core/sms/` | draft | SMS/MMS/RCS 읽기, 사전 필터, 수입 분류, sender regex Fast Path, Vector/LLM 파싱 |
| `finance-data` | `app/src/main/java/com/sanha/moneytalk/core/database/`, `feature/home/data/` | draft | Room DB, DAO, Repository, 카테고리/거래처/크레딧 데이터 흐름 |

## 아직 별도 패키지로 만들지 않은 영역

아래 영역은 루트 라우팅에는 포함하지만, 파일 역할을 더 확인한 뒤 별도 KB 패키지로 분리한다.

- `home`: `feature/home/**`
- `settings`: `feature/settings/**`, `feature/*settings/**`
- `notification`: `core/notification/**`, `receiver/**`

## 작성 원칙

1. 현재 코드와 기존 문서로 확인한 내용만 기록한다.
2. 계획, PRD, 미래 변경 목표는 KB 본문에 넣지 않는다.
3. 패키지별 상세 변경은 해당 `change-log.md`에 남긴다.
4. 루트 구조나 라우팅이 바뀌면 `00-change-index.md`에도 색인을 추가한다.
5. 문서와 코드가 다르면 코드 확인 결과를 기준으로 문서를 갱신한다.
