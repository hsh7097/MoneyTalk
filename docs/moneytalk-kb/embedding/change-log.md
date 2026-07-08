---
type: changelog
title: Embedding KB 변경 로그
description: Embedding KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, embedding, changelog]
resource: docs/moneytalk-kb/embedding/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Embedding KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-07-08 | `SmsEmbeddingService`, `VectorSearchEngine`, `StoreEmbeddingRepository`, `core/similarity/**` 확인 | Embedding 기능 KB 생성 | `README.md`, `00-structure-map.md`, `01-feature-flow.md` | 임베딩 생성/API 비용/cache/vector search와 카테고리 분류 연결을 별도 기능 KB로 분리. |
