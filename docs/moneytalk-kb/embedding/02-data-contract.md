---
type: data-contract
title: Embedding data contract
description: 임베딩 API 출력, StoreEmbedding 저장 모델, 유사도 정책 contract를 정리한다.
tags: [moneytalk, embedding, data-contract, vector]
resource: app/src/main/java/com/sanha/moneytalk/core/database/entity/StoreEmbeddingEntity.kt
timestamp: 2026-07-09T02:02:36+09:00
status: draft
---

# Embedding data contract

## API contract

| 항목 | 기준 |
|---|---|
| 서비스 | `SmsEmbeddingService` |
| API | Gemini REST `embedContent`, `batchEmbedContents` |
| 모델 | `GeminiApiKeyProvider.modelConfig.embedding`에서 원격 설정 |
| 출력 차원 | `SmsEmbeddingService.EMBEDDING_DIMENSION = 768` |
| 단건 실패 | `null` 반환 |
| 배치 실패 | 입력 text 수만큼 `null` 반환 |
| 429 rate limit | 최대 3회, 2초/4초 지수 백오프 |
| quota 초과 | 재시도하지 않고 전체 null 처리 |

## StoreEmbeddingEntity

| 필드 | 의미 | 주의 |
|---|---|---|
| `storeName` | unique 가게명 key | 같은 가게명은 replace/update 대상 |
| `category` | 적용할 카테고리 | 사용자 정의 카테고리 문자열도 들어갈 수 있다. |
| `embedding` | 768차원 vector | `FloatListConverter`로 Room 저장 |
| `source` | `gemini`, `user`, `local`, `propagated` | `source=user`는 전파 업데이트로 덮지 않는다. |
| `confidence` | 분류 신뢰도 | 전파 허용 최소값은 `CategoryPropagationPolicy.MIN_PROPAGATION_CONFIDENCE` |
| `matchCount` | vector match 사용 횟수 | 추천/품질 판단용 보조 지표 |
| `createdAt`, `updatedAt` | 생성/갱신 시각 | category update 시 갱신 |

## SimilarityProfile 기준

| 정책 | autoApply | confirm | propagate | group | 용도 |
|---|---:|---:|---:|---:|---|
| `StoreNameSimilarityPolicy` | 0.92 | 0.92 | 0.90 | 0.88 | 가게명 카테고리 자동 적용/전파/그룹핑 |
| `SmsPatternSimilarityPolicy` | 0.95 | 0.92 | 0 | 0.95 | SMS 패턴 캐시 재사용/결제 판정/그룹핑 |
| `CategoryPropagationPolicy` | 0.92 | 0.92 | 0.90 | 0.88 | 사용자/LLM 카테고리 전파 |

`VectorSearchEngine`은 cosine similarity만 계산한다. threshold 수치와 의미는 `core/similarity/**` 정책이 결정한다.

## 변경 시 체크

1. embedding 차원이나 모델을 바꾸면 저장된 `StoreEmbeddingEntity.embedding`과 기존 데이터 호환성을 검토했는가?
2. threshold를 바꾸면 `docs/moneytalk-kb/project-context/02-threshold-registry.md`와 정책 구현체를 함께 맞췄는가?
3. `source=user`를 자동 전파/일괄 저장이 덮어쓰지 않는가?
4. 배치 API 실패가 전체 분류 실패로 전파되지 않고 null 항목으로 안전하게 처리되는가?
