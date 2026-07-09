---
type: extension-points
title: Embedding extension points
description: 임베딩 기능을 확장하거나 수정할 때 건드리는 지점과 위험 경계를 정리한다.
tags: [moneytalk, embedding, extension-points]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/data/StoreEmbeddingRepositoryImpl.kt
timestamp: 2026-07-09T02:02:36+09:00
status: draft
---

# Embedding extension points

## 확장 지점

| 작업 | 우선 수정 지점 | 같이 볼 파일 |
|---|---|---|
| embedding 모델/차원 변경 | `SmsEmbeddingService` | `StoreEmbeddingEntity`, `FloatListConverter`, [../project-context/02-threshold-registry.md](../project-context/02-threshold-registry.md) |
| 가게명 자동 카테고리 기준 변경 | `StoreNameSimilarityPolicy` | `CategoryClassifierServiceImpl`, `StoreEmbeddingRepositoryImpl` |
| SMS 패턴 캐시/LLM trigger 기준 변경 | `SmsPatternSimilarityPolicy` | `SmsPatternMatcher`, `SmsGroupClassifier`, `sms-pipeline` KB |
| 유사 가게 카테고리 전파 변경 | `CategoryPropagationPolicy`, `StoreEmbeddingRepositoryImpl.propagateCategoryToSimilarStores()` | `CategoryClassifierServiceImpl`, 거래 수정/일괄 적용 경로 |
| 배치 저장 처리량 변경 | `EMBEDDING_BATCH_SIZE`, `EMBEDDING_CONCURRENCY` | Gemini API rate limit, credit/cost 정책 |
| 저신뢰도 재분류 | `getLowConfidenceEmbeddings()` | category classification KB |

## 변경 금지에 가까운 경계

- `VectorSearchEngine`에 도메인 threshold 판단을 넣지 않는다. 이 파일은 순수 vector 연산으로 유지한다.
- `source=user` 임베딩을 자동 전파나 Gemini batch 결과로 덮어쓰지 않는다.
- 임베딩 실패를 이유로 거래 저장 자체를 실패시키지 않는다. 임베딩은 카테고리 보조 경로다.
- API key/model 설정은 `GeminiApiKeyProvider` 경로를 유지한다. 임의 하드코딩을 추가하지 않는다.

## 대표 영향 흐름

```text
CategoryClassifierServiceImpl
-> Room exact mapping
-> StoreEmbeddingRepository.generateEmbeddingVector()
-> findCategoryByStoreName() / findCategoryByGroup()
-> SmsParser local keyword
-> Gemini batch
-> StoreEmbeddingRepository.saveStoreEmbeddings()
```

카테고리 분류 품질을 바꾸는 작업은 반드시 [category-classification/README.md](../category-classification/README.md)와 같이 검토한다.
