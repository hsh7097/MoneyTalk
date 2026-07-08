---
type: feature
title: Embedding 기능
description: SMS 패턴/거래처 임베딩, 벡터 검색, 유사도 정책, StoreEmbedding 캐시 흐름을 설명한다.
tags: [moneytalk, embedding, vector, similarity, feature]
resource: app/src/main/java/com/sanha/moneytalk/core/sms/SmsEmbeddingService.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Embedding 기능

> 상태: draft
> 기준: 2026-07-08 현재 `SmsEmbeddingService`, `VectorSearchEngine`, `StoreEmbeddingRepository`, `core/similarity/**` 확인

Embedding 기능은 SMS 본문 또는 거래처명을 벡터로 바꿔 유사 SMS 패턴과 유사 거래처 카테고리 분류에 활용한다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-structure-map.md](00-structure-map.md) | embedding 관련 파일과 책임을 정리한다. | 변경 파일이 embedding/similarity 어느 책임인지 판단 |
| [01-feature-flow.md](01-feature-flow.md) | trigger부터 vector 생성/검색/적용까지 흐름을 설명한다. | 유사도 분류, embedding 비용, cache 문제 |
| [change-log.md](change-log.md) | Embedding KB 변경 로그다. | 문서 변경 이유 확인 |
| [../category-classification/README.md](../category-classification/README.md) | 카테고리 분류 기능 KB다. | StoreEmbedding이 분류에 쓰이는 위치 확인 |
| [../sms-pipeline/README.md](../sms-pipeline/README.md) | SMS pipeline KB다. | SMS 패턴 유사도/파싱 흐름 확인 |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `core/sms/SmsEmbeddingService.kt` | Gemini Embedding API 호출, 단건/배치 embedding 생성 |
| `core/sms/VectorSearchEngine.kt` | cosine similarity 계산과 StoreEmbedding 검색 |
| `core/similarity/*.kt` | 도메인별 threshold, grouping, propagation policy |
| `feature/home/data/StoreEmbeddingRepository*.kt` | 거래처명 embedding 저장/조회/카테고리 업데이트 |
| `core/database/dao/StoreEmbeddingDao.kt` | embedding DB 접근 |
| `core/database/entity/StoreEmbeddingEntity.kt` | embedding vector와 category/source 저장 model |

## 주의 경계

- `VectorSearchEngine`은 순수 연산만 담당한다. threshold 판단은 `SimilarityPolicy` 구현체가 담당한다.
- embedding API 호출은 비용/쿼터가 있으므로 batch, cache, retry 경계를 확인한다.
- 사용자가 수동 수정한 `source=user` 성격의 학습 데이터는 자동 전파와 충돌하지 않게 확인한다.
