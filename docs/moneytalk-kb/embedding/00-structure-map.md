---
type: structure-map
title: Embedding 구조 지도
description: embedding 생성, 저장, 검색, 유사도 정책 파일의 역할을 정리한다.
tags: [moneytalk, embedding, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Embedding 구조 지도

```text
text/storeName
-> SmsEmbeddingService.generateEmbedding(s)
-> StoreEmbeddingRepository
-> StoreEmbeddingDao / StoreEmbeddingEntity
-> VectorSearchEngine.cosineSimilarity()
-> SimilarityPolicy threshold 판단
-> CategoryClassifierService 또는 category propagation
```

| 파일 | 분류 | 역할 | 함께 볼 파일 |
|---|---|---|---|
| `core/sms/SmsEmbeddingService.kt` | API/service | Gemini embedding REST 호출, 768차원 output, 429 retry | `GeminiApiKeyProvider`, `PremiumConfig` |
| `core/sms/VectorSearchEngine.kt` | utility | cosine similarity, best/similar store 검색 | `core/similarity/**` |
| `core/similarity/SimilarityPolicy.kt` | policy | 유사도 판단 contract | 구현체들 |
| `core/similarity/SmsPatternSimilarityPolicy.kt` | policy | SMS 패턴 유사도 threshold | `sms-pipeline` KB |
| `core/similarity/StoreNameSimilarityPolicy.kt` | policy | 거래처명 유사도 threshold | `CategoryClassifierService` |
| `core/similarity/CategoryPropagationPolicy.kt` | policy | 수동 수정 category 전파 규칙 | `StoreEmbeddingRepository` |
| `feature/home/data/StoreEmbeddingRepository*.kt` | data | 거래처 embedding cache 저장/검색/업데이트 | `StoreEmbeddingDao.kt` |
| `core/database/entity/StoreEmbeddingEntity.kt` | model | embedding vector, category, source 저장 | `FloatListConverter.kt` |
