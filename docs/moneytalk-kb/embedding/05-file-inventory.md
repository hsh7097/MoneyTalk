---
type: inventory
title: Embedding file inventory
description: 임베딩 생성, 검색, 저장, 정책 관련 파일 역할 인덱스다.
tags: [moneytalk, embedding, inventory]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-09T02:02:36+09:00
status: draft
---

# Embedding file inventory

| 파일 | 역할 | 같이 볼 문서 |
|---|---|---|
| `core/sms/SmsEmbeddingService.kt` | Gemini embedding REST 단건/배치 호출, 768차원 output, 429 retry | `02-data-contract.md` |
| `core/sms/VectorSearchEngine.kt` | cosine similarity, best/similar store search | `03-extension-points.md` |
| `core/similarity/SimilarityPolicy.kt` | 유사도 정책 contract | `02-data-contract.md` |
| `core/similarity/StoreNameSimilarityPolicy.kt` | 가게명 자동 적용/전파/그룹핑 threshold | `02-data-contract.md` |
| `core/similarity/SmsPatternSimilarityPolicy.kt` | SMS 패턴 캐시/LLM trigger/그룹핑 threshold | [../sms-pipeline/README.md](../sms-pipeline/README.md) |
| `core/similarity/CategoryPropagationPolicy.kt` | confidence 포함 전파 허용 정책 | [../category-classification/README.md](../category-classification/README.md) |
| `feature/home/data/StoreEmbeddingRepository.kt` | 가게명 embedding repository contract | `01-feature-flow.md` |
| `feature/home/data/StoreEmbeddingRepositoryImpl.kt` | cache, batch save, vector search, category propagation 구현 | `03-extension-points.md` |
| `core/database/entity/StoreEmbeddingEntity.kt` | store embedding Room entity | `02-data-contract.md` |
| `core/database/dao/StoreEmbeddingDao.kt` | embedding CRUD/update/count/query | `02-data-contract.md` |
| `feature/home/data/CategoryClassifierServiceImpl.kt` | 4-tier 카테고리 분류에서 embedding Tier 사용 | [../category-classification/README.md](../category-classification/README.md) |
