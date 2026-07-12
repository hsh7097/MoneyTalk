---
type: checklist
title: Embedding files checklist
description: 로컬 embedding 변경 전후 파일과 검증 질문을 제공한다.
tags: [moneytalk, embedding, checklist]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-12T15:20:00+09:00
status: verified
---

# Embedding files checklist

## 변경별 파일

| 변경 | 확인 파일 |
|---|---|
| 정규화/해싱/차원 | `SmsEmbeddingService.kt`, `SmsEmbeddingServiceTest.kt` |
| SMS 저장 호환 | `SmsPatternMatcher.kt`, `SmsPatternDao.kt`, `SmsPatternEntity.kt` |
| store 정규화/cache | `StoreAliasManager.kt`, `StoreEmbeddingRepositoryImpl.kt`, DAO/entity |
| cosine/search | `VectorSearchEngine.kt`, `core/similarity/**` |
| category 연결 | `CategoryClassifierServiceImpl.kt`, category-classification KB |
| SMS 연결 | `SmsPipeline.kt`, `SmsGroupClassifier.kt`, sms-pipeline KB |

## 검증

1. 네트워크/API key 없이 단건과 batch가 동작하는가?
2. output이 768차원, L2 norm 1, 동일 입력에 결정적인가?
3. blank는 `null`, batch 순서와 크기는 입력과 같은가?
4. SMS template 공백 차이가 같은 vector로 수렴하는가?
5. 알려진 거래처 한글/영문/지점명이 같은 store vector로 수렴하는가?
6. 다른 SMS format과 무관 거래처가 threshold를 넘지 않는가?
7. 기존 Room vector가 최초 사용 시 재생성되는가?
8. 재생성 시 id/category/source/confidence/matchCount가 보존되는가?
9. cache invalidation이 save/update/propagation 뒤 호출되는가?
10. threshold 구현과 registry가 일치하는가?
11. origin audit, unit test, release build가 통과하는가?
