---
type: inventory
title: Embedding file inventory
description: 로컬 vector 생성, 저장, 검색, 정책 관련 파일 인덱스다.
tags: [moneytalk, embedding, inventory]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-12T15:20:00+09:00
status: verified
---

# Embedding file inventory

| 파일 | 역할 | 함께 볼 문서 |
|---|---|---|
| `core/sms/SmsEmbeddingService.kt` | local SMS/store vector 생성 | [02-data-contract.md](02-data-contract.md) |
| `core/sms/SmsTemplateEngine.kt` | SMS templateize와 batch embedding 위임 | [../sms-pipeline/README.md](../sms-pipeline/README.md) |
| `core/sms/SmsPatternMatcher.kt` | legacy pattern vector 갱신, cached pattern/remote rule 검색 | [01-feature-flow.md](01-feature-flow.md) |
| `core/sms/VectorSearchEngine.kt` | pure cosine, store 후보 검색 | [03-extension-points.md](03-extension-points.md) |
| `core/util/StoreAliasManager.kt` | 대표 상호/alias 정규화 | [01-feature-flow.md](01-feature-flow.md) |
| `core/util/StoreNameGrouper.kt` | store 전용 vector로 LLM batch grouping | category-classification KB |
| `feature/home/data/StoreEmbeddingRepository.kt` | store embedding contract | [02-data-contract.md](02-data-contract.md) |
| `feature/home/data/StoreEmbeddingRepositoryImpl.kt` | cache, legacy 갱신, save/search/propagation | [03-extension-points.md](03-extension-points.md) |
| `core/database/entity/SmsPatternEntity.kt` | SMS template vector와 regex cache | sms-pipeline KB |
| `core/database/dao/SmsPatternDao.kt` | pattern read/replace/count | sms-pipeline KB |
| `core/database/entity/StoreEmbeddingEntity.kt` | store vector/category metadata | [02-data-contract.md](02-data-contract.md) |
| `core/database/dao/StoreEmbeddingDao.kt` | store vector CRUD/query | [02-data-contract.md](02-data-contract.md) |
| `core/similarity/*.kt` | domain threshold policy | threshold registry |
| `app/src/test/.../SmsEmbeddingServiceTest.kt` | 결정성/정규화/alias 회귀 테스트 | [04-files-checklist.md](04-files-checklist.md) |
