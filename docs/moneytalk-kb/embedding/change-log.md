---
type: changelog
title: Embedding KB 변경 로그
description: Embedding contract와 문서 변경 이력을 기록한다.
tags: [moneytalk, embedding, changelog]
resource: docs/moneytalk-kb/embedding/
timestamp: 2026-07-12T15:20:00+09:00
status: verified
---

# Embedding KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 |
|---|---|---|---|
| 2026-07-08 | 기존 Gemini embedding, vector search, store cache 확인 | 기능 KB 최초 작성 | 원격 API 비용/cache 중심 설명 |
| 2026-07-09 | entity/DAO/policy 확인 | contract/checklist 상세화 | 차원, threshold, `source=user` 보호 기록 |
| 2026-07-12 | Firebase AI Logic 전환과 local embedding 구현 검증 | KB 전체를 결정적 local vector contract로 갱신 | 네트워크/API key 제거, SMS 공백 불변성, store alias 정규화, Room legacy vector 최초 사용 갱신을 SSOT로 기록 |
