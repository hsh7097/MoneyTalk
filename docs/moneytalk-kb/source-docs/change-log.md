---
type: log
title: Source Docs KB Change Log
description: 기존 docs 루트 문서 흡수/참조 기준 변경 이력을 기록한다.
tags: [moneytalk, kb, source-docs, changelog]
resource: docs/moneytalk-kb/source-docs/
timestamp: 2026-07-09T03:05:00+09:00
status: draft
---

# Source Docs Change Log

## 2026-07-09

- 기준: `docs/*.md` 확인. `docs/moneytalk-kb/**`, `docs/kb-scaffold/**` 제외
- 변경 근거: 기존 루트 문서가 많아 AI가 작업 시작 시 필요한 KB를 고르기 어렵다는 요구 반영
- 갱신한 KB:
  - `README.md`
  - `01-consolidation-map.md`
- 결정:
  - `APP_FUNCTIONS.md`, `SMS_RULE_JSON_UPDATE_GUIDE.md`, `PLAY_CONSOLE_SMS_PERMISSION_DECLARATION.md`의 핵심 운영 내용은 기능 KB로 흡수
  - 크레딧/심층 분석/수익화 문서는 구현 사실과 계획을 분리해 참조
  - worklog 문서는 현재 구현 근거가 아니라 이력 참조로 분류

## 2026-07-09 추가 정리

- 기준: 전체 Markdown 참조 검색과 KB 링크 검증
- 변경 근거: KB로 완전히 흡수된 루트 문서를 제거해 문서 진입점을 단순화
- 삭제한 루트 문서:
  - `docs/APP_FUNCTIONS.md`
  - `docs/SMS_RULE_JSON_UPDATE_GUIDE.md`
  - `docs/PLAY_CONSOLE_SMS_PERMISSION_DECLARATION.md`
- 추가 판단:
  - 루트 문서는 보존 여부를 전제로 하지 않고, 핵심 내용이 실제 KB 문서로 흡수됐는지와 링크 검증이 끝났는지를 기준으로 삭제 여부를 판단한다.
  - `AI_CONTEXT.md`, `ARCHITECTURE.md`, `CHAT_SYSTEM.md`, `CATEGORY_CLASSIFICATION.md`, `SMS_PARSING.md`, `COMPOSABLE_MAP.md`, `SCREEN_REQUIREMENTS.md`, `GIT_CONVENTION.md`, `CHANGELOG.md`, `AI_CREDIT_*`, `MONETIZATION.md`, worklog 문서는 각각 전용 KB 문서로 재작성했다.

## 2026-07-09 흡수 지도 재정렬

- 기준: 새 KB 목적지 재검토
- 변경 근거: KB 하위로 원문 파일을 이동한 것처럼 보이는 잘못된 링크를 제거하고, 실제 재작성된 KB 문서 목적지를 명시했다.
- 갱신한 KB: `README.md`, `01-consolidation-map.md`

## 2026-07-09 루트 Markdown 정리 완료

- 기준: 루트 `docs/*.md` 링크 검색
- 변경 근거: 루트 Markdown 원본의 핵심 내용이 KB 문서로 흡수됐고 직접 참조 링크가 없어져 진입점을 `docs/moneytalk-kb/**`로 일원화했다.
- 삭제한 루트 Markdown: `AI_CONTEXT.md`, `ARCHITECTURE.md`, `SMS_PARSING.md`, `CATEGORY_CLASSIFICATION.md`, `CHAT_SYSTEM.md`, `COMPOSABLE_MAP.md`, `SCREEN_REQUIREMENTS.md`, `GIT_CONVENTION.md`, `CHANGELOG.md`, `AI_CREDIT_*`, `MONETIZATION.md`, `*WORKLOG.md`, `APP_FUNCTIONS.md`, `PLAY_CONSOLE_SMS_PERMISSION_DECLARATION.md`, `SMS_RULE_JSON_UPDATE_GUIDE.md`
- 유지한 루트 파일: `privacy-policy.html`
