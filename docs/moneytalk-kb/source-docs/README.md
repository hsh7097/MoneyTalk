---
type: support
title: Source Docs Consolidation
description: 기존 docs 루트 문서의 내용이 어떤 MoneyTalk KB 문서로 흡수됐는지 정리한다.
tags: [moneytalk, kb, source-docs, documentation]
resource: docs/
timestamp: 2026-07-09T06:25:00+09:00
status: draft
---

# Source Docs Consolidation

> 기준: 2026-07-09 현재 `docs/*.md` 확인. `docs/moneytalk-kb/**`, `docs/kb-scaffold/**` 제외

이 패키지는 기존 루트 문서를 KB 하위로 옮겨 보관하는 곳이 아니다. 루트 문서의 정보를 어떤 KB 문서로 재작성했는지 추적하는 지도다.

## 사용 원칙

1. 현재 구현 사실과 코드 위치는 `docs/moneytalk-kb/**`가 우선이다.
2. 2026-07-09 기준 루트 `docs/*.md`는 KB 흡수 후 삭제했다. 루트에는 Markdown이 아닌 `privacy-policy.html`만 남긴다.
3. 문서끼리 충돌하면 현재 코드 확인 결과를 기준으로 KB를 갱신한다.
4. 계획 문서는 구현된 사실처럼 KB 본문에 섞지 않고 `후속/계획`으로 표시한다.
5. 새 루트 Markdown 문서가 생기면 해당 내용은 가능한 빨리 기능 KB로 흡수하고 이 지도를 갱신한다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [01-consolidation-map.md](01-consolidation-map.md) | 기존 `docs/*.md`별 KB 흡수 위치와 남은 역할 | 루트 문서를 삭제/정리할 수 있는지 판단 |
| [change-log.md](change-log.md) | source-docs KB 변경 로그 | consolidation 판단 변경 이유 확인 |

## 현재 흡수 완료 축

| 원본 문서 | KB 흡수 위치 | 상태 |
|---|---|---|
| `APP_FUNCTIONS.md` | [../app-functions/06-function-catalog.md](../app-functions/06-function-catalog.md), [../app-functions/07-operational-playbook.md](../app-functions/07-operational-playbook.md) | 핵심 내용 흡수 후 루트 문서 삭제 |
| `SMS_RULE_JSON_UPDATE_GUIDE.md` | [../sms-pipeline/06-rule-json-guide.md](../sms-pipeline/06-rule-json-guide.md) | 운영 절차 흡수 후 루트 문서 삭제 |
| `PLAY_CONSOLE_SMS_PERMISSION_DECLARATION.md` | [../onboarding/01-sms-permission-policy.md](../onboarding/01-sms-permission-policy.md) | 정책/릴리스 게이트 흡수 후 루트 문서 삭제 |
| `AI_CONTEXT.md`, `ARCHITECTURE.md` | [../project-context/01-system-overview.md](../project-context/01-system-overview.md), [../project-context/02-threshold-registry.md](../project-context/02-threshold-registry.md) | 핵심 구조/임계값 흡수 |
| `CHAT_SYSTEM.md` | [../chat/05-system-contract.md](../chat/05-system-contract.md) | 채팅 계약 흡수 |
| `CATEGORY_CLASSIFICATION.md` | [../category-classification/06-classification-tiers.md](../category-classification/06-classification-tiers.md) | 분류 티어 흡수 |
| `SMS_PARSING.md` | [../sms-parsing/06-ingestion-contract.md](../sms-parsing/06-ingestion-contract.md), [../sms-pipeline/README.md](../sms-pipeline/README.md) | 수집/저장 계약 흡수 |
| `COMPOSABLE_MAP.md` | [../ui-map/01-screen-composable-index.md](../ui-map/01-screen-composable-index.md) | 화면/Composable 라우팅 흡수 |
| `SCREEN_REQUIREMENTS.md` | [../screen-requirements/01-screen-requirements-index.md](../screen-requirements/01-screen-requirements-index.md) | 화면 요구사항 라우팅 흡수 |
| `GIT_CONVENTION.md` | [../project-operations/01-git-workflow.md](../project-operations/01-git-workflow.md) | 운영 기준 흡수 |
| `CHANGELOG.md` | [../release-history/01-release-timeline.md](../release-history/01-release-timeline.md) | 주요 변경 축 흡수 |
| `AI_CREDIT_*`, `MONETIZATION.md` | [../budget-credit-monetization/02-policy-and-plans.md](../budget-credit-monetization/02-policy-and-plans.md) | 현재 정책/후속 계획 분리 |
| `*WORKLOG.md` | [../worklogs/01-agent-worklog-decisions.md](../worklogs/01-agent-worklog-decisions.md) | 현재 유효한 결정/보류 사유 흡수 |

## 현재 루트 docs 상태

| 파일 | 처리 |
|---|---|
| `docs/*.md` | 모두 KB 흡수 후 삭제 |
| `docs/privacy-policy.html` | 웹 개인정보처리방침으로 유지. Markdown KB 정리 대상 아님 |
