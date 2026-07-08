---
type: log
title: MoneyTalk KB Change Index
description: MoneyTalk KB의 루트 변경 이력 색인이다.
tags: [moneytalk, kb, changelog, android]
resource: docs/moneytalk-kb/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# MoneyTalk KB Change Index

상세 설명은 각 패키지 `change-log.md`에 둔다.
이 파일은 전체 KB에서 어떤 영역이 바뀌었는지 찾기 위한 짧은 색인이다.

| 날짜 | 기준 | 영향 영역 | 갱신 문서 | 요약 |
|---|---|---|---|---|
| 2026-07-03 | 현재 소스와 기존 `docs/ARCHITECTURE.md`, `docs/SMS_PARSING.md`, `docs/APP_FUNCTIONS.md` 확인 | 초기 KB 생성 | `README.md`, `00-agent-routing.md`, `01-structure-map.md`, `history/**`, `sms-pipeline/**`, `finance-data/**` | 스카폴드 기준으로 MoneyTalk 초기 KB를 생성하고 핵심 라우팅, History 도메인, SMS pipeline, finance data 문서를 작성. |
| 2026-07-03 | 기능 단위 KB 요구 반영 | 기능 KB 추가 | `README.md`, `00-agent-routing.md`, `01-structure-map.md`, `sms-parsing/**`, `category-classification/**`, `app-functions/**` | 문자 파싱, 카테고리 분류, App Functions 기능을 도메인/서브모듈과 별도 end-to-end 기능 KB로 추가. |
| 2026-07-08 | `feature/chat/**`, `DataQueryParser`, `ChatCreditPolicy`, `ChatContextBuilder`, `docs/AI_CREDIT_DEEP_ANALYSIS_PLAN.md` 확인 | AI 채팅 KB 추가 | `README.md`, `00-agent-routing.md`, `01-structure-map.md`, `chat/**` | 단순 조회도 Gemini에 조회 결과를 다시 보내는 현재 비용 구조와 로컬 정형 조회 우회 설계를 `chat` KB로 분리. |
| 2026-07-08 | `LocalChatQueryRouter`, `ChatViewModel`, `ChatRepositoryImpl`, `LocalChatQueryRouterTest` 확인 | AI 채팅 로컬 조회 구현 | `chat/README.md`, `chat/change-log.md`, 일반 채팅/수익화 문서 | 총 지출, 카테고리 지출, 최근 지출, 예산 현황 등 안전한 단순 조회를 Gemini analyze/final/summary 밖에서 처리하는 1차 구현을 기록. |
