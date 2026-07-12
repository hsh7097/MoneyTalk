---
type: feature
title: Embedding 기능
description: SMS pattern과 거래처의 결정적 로컬 vector 생성, 저장 호환, 검색 경계를 설명한다.
tags: [moneytalk, embedding, vector, similarity, feature]
resource: app/src/main/java/com/sanha/moneytalk/core/sms/SmsEmbeddingService.kt
timestamp: 2026-07-12T15:20:00+09:00
status: verified
---

# Embedding 기능

> 상태: verified
> 기준: 2026-07-12 로컬 구현, Room 저장 vector 갱신, 호출부와 단위 테스트 확인

MoneyTalk embedding은 네트워크나 API key 없이 SMS template과 거래처명을 768차원 vector로 바꾼다. 이 vector는 SMS pattern 재사용, 거래처 분류 cache, 유사 거래처 grouping과 category propagation의 후보 검색에 사용한다. LLM 판단은 Firebase AI Logic 경로이며 이 기능과 별도다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-structure-map.md](00-structure-map.md) | 코드 책임과 의존 방향 | 수정 파일을 찾을 때 |
| [01-feature-flow.md](01-feature-flow.md) | SMS/거래처 생성, 저장, 검색 흐름 | 결과가 기존 버전과 달라질 때 |
| [02-data-contract.md](02-data-contract.md) | 정규화, 해싱, 차원, 저장 호환 contract | 알고리즘/threshold 변경 시 |
| [03-extension-points.md](03-extension-points.md) | 허용된 확장 지점과 위험 경계 | 구조 변경 전 |
| [04-files-checklist.md](04-files-checklist.md) | 코드 리뷰와 검증 목록 | 커밋 전 |
| [05-file-inventory.md](05-file-inventory.md) | 실제 파일 인덱스 | 구현 위치가 애매할 때 |
| [change-log.md](change-log.md) | 문서와 contract 변경 이력 | 과거 판단 확인 시 |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `core/sms/SmsEmbeddingService.kt` | NFKC, 공백 제거, 1~3글자 n-gram, signed feature hashing, L2 정규화 |
| `core/sms/SmsPatternMatcher.kt` | 저장된 SMS pattern vector의 최초 사용 갱신과 cosine 검색 |
| `core/sms/VectorSearchEngine.kt` | 순수 cosine 계산과 store match 후보 반환 |
| `core/util/StoreAliasManager.kt` | 알려진 상호/영문명/지점명을 대표 거래처명으로 정규화 |
| `feature/home/data/StoreEmbeddingRepositoryImpl.kt` | StoreEmbedding cache, legacy vector 갱신, 저장, 전파 |
| `core/similarity/**` | SMS/store/category threshold SSOT |

## 불변 경계

- 임베딩에 API key, Firebase 모델명 또는 네트워크 호출을 추가하지 않는다.
- 768차원이 같아도 과거 Gemini vector와 현재 local vector는 비교할 수 없다. 최초 사용 갱신 경로를 유지한다.
- 원본 SMS, 원본 거래처명, category, `source=user` 학습 metadata는 vector 정규화 때문에 바꾸지 않는다.
- `VectorSearchEngine`은 계산만 담당하고 판정 수치는 `core/similarity/**`에서 관리한다.
- 알고리즘 또는 threshold 변경 시 [project-context/02-threshold-registry.md](../project-context/02-threshold-registry.md)와 테스트를 함께 갱신한다.

## 연결 KB

- SMS 단계: [sms-pipeline/README.md](../sms-pipeline/README.md)
- category 단계: [category-classification/README.md](../category-classification/README.md)
- threshold SSOT: [project-context/02-threshold-registry.md](../project-context/02-threshold-registry.md)
