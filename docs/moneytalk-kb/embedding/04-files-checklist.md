---
type: checklist
title: Embedding files checklist
description: 임베딩 관련 수정 전후 확인할 파일과 검증 질문이다.
tags: [moneytalk, embedding, checklist]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-09T02:02:36+09:00
status: draft
---

# Embedding files checklist

## 수정 전 확인

| 변경 대상 | 먼저 확인 |
|---|---|
| API 호출/모델/차원 | `SmsEmbeddingService.kt`, `GeminiApiKeyProvider.kt` |
| vector search | `VectorSearchEngine.kt`, `core/similarity/**` |
| 가게명 cache | `StoreEmbeddingRepository.kt`, `StoreEmbeddingRepositoryImpl.kt`, `StoreEmbeddingDao.kt` |
| 저장 model | `StoreEmbeddingEntity.kt`, `FloatListConverter.kt`, Room migration 영향 |
| 카테고리 분류 연계 | `CategoryClassifierServiceImpl.kt`, `category-classification` KB |
| SMS 패턴 유사도 | `SmsPatternMatcher.kt`, `SmsGroupClassifier.kt`, `sms-pipeline` KB |

## 검증 질문

1. embedding API key가 없거나 quota/rate limit이 발생해도 앱 기능이 graceful degrade 되는가?
2. 단건과 배치 함수의 실패 반환 형태가 호출부 기대와 맞는가?
3. cache invalidation이 저장/업데이트/전파 후 호출되는가?
4. 유사도 threshold 변경이 자동 적용, 확정, 전파, 그룹핑 의미를 섞지 않는가?
5. 사용자 수동 카테고리 수정 결과가 자동 분류보다 우선하는가?
6. 기존 Room schema를 바꾸는 경우 migration을 작성했는가?
