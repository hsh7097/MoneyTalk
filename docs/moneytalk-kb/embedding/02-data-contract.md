---
type: data-contract
title: Embedding data contract
description: 로컬 vector 출력, 저장 호환, StoreEmbedding, 유사도 정책 contract를 정의한다.
tags: [moneytalk, embedding, data-contract, vector]
resource: app/src/main/java/com/sanha/moneytalk/core/database/entity/StoreEmbeddingEntity.kt
timestamp: 2026-07-12T15:20:00+09:00
status: verified
---

# Embedding data contract

## Local vector

| 항목 | 기준 |
|---|---|
| 서비스 | `SmsEmbeddingService` |
| 실행 | `Dispatchers.Default`, 네트워크 없음 |
| 정규화 | NFKC, Korean locale lowercase, 공백 제거 |
| 특징 | full text + boundary 포함 1~3 character n-gram |
| 해싱 | FNV-1a signed feature hashing |
| 차원 | `EMBEDDING_DIMENSION = 768` |
| norm | non-null output은 L2 1.0 |
| 결정성 | 같은 입력/알고리즘이면 같은 vector |
| 단건 실패 | `null` |
| batch | 입력과 같은 크기/순서의 nullable 목록 |
| store 전처리 | 등록 alias가 있으면 대표 상호를 embedding |

## Persisted vector compatibility

- 차원 수는 embedding 공간의 version이 아니다.
- 과거 Gemini vector와 local vector 또는 서로 다른 local 알고리즘 vector를 직접 비교하지 않는다.
- SMS pattern은 `smsTemplate`, StoreEmbedding은 `storeName`에서 재생성할 수 있어 schema column 없이 갱신한다.
- SMS 갱신은 id와 pattern metadata를 보존한다.
- Store 갱신은 id, storeName, category, source, confidence, matchCount, createdAt을 보존한다.
- 별도 `embeddingVersion` column을 추가할 경우 Room migration과 백업/복원 호환을 함께 설계한다.

## StoreEmbeddingEntity

| 필드 | 의미 | 보호 규칙 |
|---|---|---|
| `storeName` | unique 원본 거래처명 | alias 대표명으로 덮어쓰지 않음 |
| `category` | 적용 category | 사용자 정의 값 허용 |
| `embedding` | 768차원 local vector | 최초 cache load에서 현재 contract 확인 |
| `source` | gemini/user/local/propagated | `user` 자동 전파 보호 |
| `confidence` | 분류 신뢰도 | propagation policy와 함께 판단 |
| `matchCount` | 사용 횟수 | vector 갱신 시 유지 |
| `createdAt/updatedAt` | 생성/갱신 시각 | vector 변경 시 updatedAt만 갱신 |

## Similarity SSOT

| 정책 | autoApply | confirm | propagate | group |
|---|---:|---:|---:|---:|
| `StoreNameSimilarityPolicy` | 0.92 | 0.92 | 0.90 | 0.88 |
| `SmsPatternSimilarityPolicy` | 0.95 | 0.92 | 0 | 0.95 |
| `CategoryPropagationPolicy` | 0.92 | 0.92 | 0.90 | 0.88 |

수치 변경은 [project-context/02-threshold-registry.md](../project-context/02-threshold-registry.md)가 함께 바뀌어야 한다.
