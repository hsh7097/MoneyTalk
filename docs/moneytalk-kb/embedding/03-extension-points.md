---
type: extension-points
title: Embedding extension points
description: 로컬 embedding 변경 지점과 저장 호환 위험을 정의한다.
tags: [moneytalk, embedding, extension-points]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/data/StoreEmbeddingRepositoryImpl.kt
timestamp: 2026-07-12T15:20:00+09:00
status: verified
---

# Embedding extension points

| 작업 | 우선 수정 | 반드시 같이 확인 |
|---|---|---|
| 정규화/특징/해싱/차원 | `SmsEmbeddingService` | SMS/Store 저장 vector 갱신, 단위 테스트, threshold |
| 거래처 alias | `StoreAliasManager` | store 전용 embedding, category grouping 오탐 |
| SMS template | `SmsTemplateEngine` | 기존 `SmsPatternEntity.smsTemplate` 재생성 결과 |
| store 자동 적용 | `StoreNameSimilarityPolicy` | classifier, repository, threshold registry |
| SMS pattern 판정 | `SmsPatternSimilarityPolicy` | matcher, group classifier, parser 표본 |
| category 전파 | `CategoryPropagationPolicy` | `source=user` 보호, 거래 수정 흐름 |
| local batch 처리량 | `EMBEDDING_BATCH_SIZE/CONCURRENCY` | CPU, memory, 전체 sync 시간 |

## 금지에 가까운 변경

- 임베딩에 사용자 입력 API key나 공개 RTDB key pool을 다시 연결하지 않는다.
- 같은 768차원이라는 이유로 저장 vector 재생성을 생략하지 않는다.
- alias 정규화 결과를 원본 거래처명에 저장하지 않는다.
- threshold를 `VectorSearchEngine` 안에 복제하지 않는다.
- embedding 실패 때문에 transaction 저장 전체를 실패시키지 않는다.

## 알고리즘 변경 절차

1. 새 결과의 결정성, norm, 차원, blank 처리 테스트를 추가한다.
2. 같은 SMS format의 공백/표기 변형과 다른 format의 separation을 검증한다.
3. 알려진 상호 alias/지점명과 무관 상호의 separation을 검증한다.
4. 기존 Room 행이 metadata를 유지한 채 재생성되는지 확인한다.
5. SMS origin audit와 full sync 결과를 이전 기준과 비교한다.
6. threshold를 바꿨다면 registry와 영향 KB를 동기화한다.
