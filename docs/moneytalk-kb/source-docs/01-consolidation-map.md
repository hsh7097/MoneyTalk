---
type: map
title: Source Docs Consolidation Map
description: 기존 docs 루트 문서별 KB 흡수 위치와 현재 역할을 정리한다.
tags: [moneytalk, kb, source-docs, map]
resource: docs/
timestamp: 2026-07-09T06:25:00+09:00
status: draft
---

# 01 Consolidation Map

> 기준: 2026-07-09 현재 `docs/*.md` 확인. `docs/moneytalk-kb/**`, `docs/kb-scaffold/**` 제외

2026-07-09 기준 루트 `docs/*.md`는 모두 아래 KB 목적지로 흡수한 뒤 삭제했다. 루트 `docs/privacy-policy.html`은 Markdown 정리 대상이 아니라 유지한다.

| 원본 문서 | 현재 역할 | KB 목적지 | 상태 | 작업 시 판단 |
|---|---|---|---|---|
| `AI_CONTEXT.md` | 앱 전체 아키텍처, 임계값, Golden Flow, Gemini 모델/프롬프트 요약 | [../project-context/01-system-overview.md](../project-context/01-system-overview.md), [../project-context/02-threshold-registry.md](../project-context/02-threshold-registry.md) | 핵심 흡수 | 전체 맥락과 임계값은 project-context KB를 우선한다. |
| `ARCHITECTURE.md` | 패키지 구조와 시스템 개요 | [../project-context/01-system-overview.md](../project-context/01-system-overview.md), [../ui-map/01-screen-composable-index.md](../ui-map/01-screen-composable-index.md) | 핵심 흡수 | 구조 변경 시 project-context, ui-map, 관련 기능 KB를 같이 갱신한다. |
| `SMS_PARSING.md` | SMS/MMS/RCS 수집, 3-tier 파싱, 실시간 보완 | [../sms-parsing/06-ingestion-contract.md](../sms-parsing/06-ingestion-contract.md), [../sms-pipeline/README.md](../sms-pipeline/README.md), [../notification-ingestion/README.md](../notification-ingestion/README.md) | 핵심 흡수 | 파싱/저장/refresh 변경은 ingestion contract와 pipeline KB를 같이 본다. |
| `CATEGORY_CLASSIFICATION.md` | StoreRule/Room/Vector/Keyword/Gemini 분류 상세 | [../category-classification/06-classification-tiers.md](../category-classification/06-classification-tiers.md), [../embedding/README.md](../embedding/README.md) | 핵심 흡수 | 분류 순서, 임계값, 사용자 수정 전파는 classification tiers를 우선한다. |
| `CHAT_SYSTEM.md` | AI 채팅 Local Fast Path, 3-step, query/action/ANALYTICS | [../chat/05-system-contract.md](../chat/05-system-contract.md) | 핵심 흡수 | 쿼리/액션/ANALYTICS 변경 시 chat contract와 `DataQueryParser`를 확인한다. |
| `APP_FUNCTIONS.md` | Android App Functions 함수 목록, DB 점검 플레이북 | [../app-functions/06-function-catalog.md](../app-functions/06-function-catalog.md), [../app-functions/07-operational-playbook.md](../app-functions/07-operational-playbook.md) | 흡수 후 삭제 | App Functions 변경은 KB 카탈로그와 생성 metadata를 우선한다. |
| `AI_CREDIT_USAGE_PLAN.md` | 현재 AI 크레딧 단가 계획 | [../budget-credit-monetization/02-policy-and-plans.md](../budget-credit-monetization/02-policy-and-plans.md) | 핵심 흡수 | 실제 정책은 코드와 policy KB를 우선한다. |
| `AI_CREDIT_DEEP_ANALYSIS_PLAN.md` | JSON 기반 심층 상담과 결제 후속 계획 | [../budget-credit-monetization/02-policy-and-plans.md](../budget-credit-monetization/02-policy-and-plans.md), [../chat/05-system-contract.md](../chat/05-system-contract.md) | 계획 흡수 | 미구현 내용은 후속 계획으로만 취급한다. |
| `MONETIZATION.md` | 과금/광고/API 비용 전략 | [../budget-credit-monetization/02-policy-and-plans.md](../budget-credit-monetization/02-policy-and-plans.md), [../release-history/01-release-timeline.md](../release-history/01-release-timeline.md) | 핵심 흡수 | 현재 구현 정책과 미래 전략을 구분한다. |
| `COMPOSABLE_MAP.md` | 화면별 Composable 계층 | [../ui-map/01-screen-composable-index.md](../ui-map/01-screen-composable-index.md) | 핵심 흡수 | Composable 변경 시 ui-map과 해당 화면 KB를 같이 갱신한다. |
| `SCREEN_REQUIREMENTS.md` | 화면별 요구사항 | [../screen-requirements/01-screen-requirements-index.md](../screen-requirements/01-screen-requirements-index.md), 화면별 `package-reference/**` | 핵심 흡수 | UI 작업 전 요구사항 index와 해당 화면 KB를 우선한다. |
| `GIT_CONVENTION.md` | Git 브랜치/커밋/PR 운영 규칙 | [../project-operations/01-git-workflow.md](../project-operations/01-git-workflow.md) | 핵심 흡수 | 커밋/푸시/PR 요청 시 project-operations KB와 AGENTS 규칙을 따른다. |
| `CHANGELOG.md` | 앱 릴리스 변경 이력 | [../release-history/01-release-timeline.md](../release-history/01-release-timeline.md), 각 기능 `change-log.md` | 핵심 흡수 | 전체 히스토리보다 현재 기능 KB를 우선한다. |
| `AI_EXPERIENCE_AGENT_WORKLOG.md` | 과거 AI 경험 개선 작업 로그 | [../worklogs/01-agent-worklog-decisions.md](../worklogs/01-agent-worklog-decisions.md) | 결정 흡수 | 과거 계획은 현재 코드/KBI와 충돌하면 따르지 않는다. |
| `SEARCH_OPTIMIZATION_AGENT_WORKLOG.md` | 과거 검색/분류 최적화 작업 로그 | [../worklogs/01-agent-worklog-decisions.md](../worklogs/01-agent-worklog-decisions.md) | 결정 흡수 | HNSW/Redis 등 보류 사유를 확인할 때 본다. |
| `PLAY_CONSOLE_SMS_PERMISSION_DECLARATION.md` | SMS 권한 선언, 데이터 처리, 릴리스 게이트 | [../onboarding/01-sms-permission-policy.md](../onboarding/01-sms-permission-policy.md) | 흡수 후 삭제 | SMS 권한/Play Console 변경 시 onboarding policy를 우선한다. |
| `SMS_RULE_JSON_UPDATE_GUIDE.md` | `sms_rules_v1.json` 운영 갱신 가이드 | [../sms-pipeline/06-rule-json-guide.md](../sms-pipeline/06-rule-json-guide.md) | 흡수 후 삭제 | Fast Path 룰 추가/검증/RTDB 표본 작업 시 해당 KB를 우선한다. |

## 신규 문서 흡수 규칙

1. 구현 사실이면 해당 기능 KB 본문 또는 package-reference로 옮긴다.
2. 운영 절차면 기능 KB의 `*-playbook.md` 또는 `*-guide.md`로 둔다.
3. 계획이면 현재 구현과 분리해 계획 문서로만 둔다.
4. 과거 worklog면 결정/보류 사유만 남긴다.
5. 새 루트 Markdown 문서가 생기면 링크 검증 후 KB 목적지로 흡수하고 루트 원본은 제거한다.
