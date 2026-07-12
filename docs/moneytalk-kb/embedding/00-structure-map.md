---
type: structure-map
title: Embedding 구조 지도
description: 로컬 vector 생성, 저장 호환, 검색, 판정 책임을 연결한다.
tags: [moneytalk, embedding, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-12T15:20:00+09:00
status: verified
---

# Embedding 구조 지도

## SMS pattern

```text
SMS body
-> SmsTemplateEngine.templateize()
-> SmsEmbeddingService.generateEmbedding(s)
-> SmsPatternMatcher.refreshStoredEmbeddingsIfNeeded()
-> SmsPatternDao / SmsPatternEntity
-> cosine similarity
-> cached regex 또는 다음 SMS tier
```

## Store/category

```text
storeName
-> StoreAliasManager.normalizeStoreName()
-> SmsEmbeddingService.generateStoreEmbedding(s)
-> StoreEmbeddingRepositoryImpl
-> 최초 cache load 시 legacy vector 재생성
-> StoreEmbeddingDao / StoreEmbeddingEntity
-> VectorSearchEngine
-> StoreNameSimilarityPolicy / CategoryPropagationPolicy
-> CategoryClassifierService
```

| 파일 | 분류 | 책임 | 함께 볼 파일 |
|---|---|---|---|
| `core/sms/SmsEmbeddingService.kt` | local service | SMS/store 결정적 768차원 vector | `SmsEmbeddingServiceTest.kt` |
| `core/sms/SmsTemplateEngine.kt` | transform | SMS의 가변값을 placeholder template으로 변환 | `SmsPipeline.kt` |
| `core/sms/SmsPatternMatcher.kt` | search/data | legacy pattern vector 갱신, sender별 pattern 검색 | `SmsPatternDao.kt` |
| `core/sms/VectorSearchEngine.kt` | utility | cosine, best/similar store 검색 | `core/similarity/**` |
| `core/util/StoreAliasManager.kt` | normalize | 알려진 거래처 alias를 대표명으로 수렴 | `StoreNameNormalizer.kt` |
| `feature/home/data/StoreEmbeddingRepositoryImpl.kt` | data | cache, 갱신, 저장, category propagation | `StoreEmbeddingDao.kt` |
| `core/similarity/*.kt` | policy | autoApply/confirm/propagate/group threshold | threshold registry |

의존 방향은 local transform/vector -> pure search -> policy -> domain action 순서다. 검색 utility가 category나 SMS 저장을 직접 판단하지 않는다.
