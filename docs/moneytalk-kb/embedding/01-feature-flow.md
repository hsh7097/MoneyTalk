---
type: feature-flow
title: Embedding 기능 흐름
description: embedding 생성, cache 저장, vector 검색, category 적용 흐름을 설명한다.
tags: [moneytalk, embedding, feature-flow]
resource: app/src/main/java/com/sanha/moneytalk/core/sms/SmsEmbeddingService.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Embedding 기능 흐름

## Store embedding

```text
storeName
-> StoreEmbeddingRepository cache 조회
-> 없으면 SmsEmbeddingService.generateEmbedding(storeName)
-> StoreEmbeddingEntity 저장
-> VectorSearchEngine.findBestStoreMatch()
-> StoreNameSimilarityPolicy threshold 확인
-> category 후보로 사용
```

## SMS pattern embedding

SMS 패턴 유사도는 SMS pipeline 쪽 책임과 연결된다.
파싱 후보를 찾는 단계는 [sms-pipeline/README.md](../sms-pipeline/README.md)를 먼저 보고, threshold는 `core/similarity`를 확인한다.

## 검증 질문

1. embedding API 호출 전에 cache hit를 먼저 확인하는가?
2. batch embedding 실패 시 null 항목이 안전하게 처리되는가?
3. threshold 수치는 `AI_CONTEXT.md`의 registry와 정책 구현체가 일치하는가?
4. 수동 수정 category가 자동 embedding update로 덮이지 않는가?
5. 429 rate limit과 quota 초과를 구분하는가?
