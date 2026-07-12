---
type: feature-flow
title: Embedding 기능 흐름
description: SMS와 거래처 vector의 생성, 저장 갱신, 검색, fallback을 설명한다.
tags: [moneytalk, embedding, feature-flow]
resource: app/src/main/java/com/sanha/moneytalk/core/sms/SmsEmbeddingService.kt
timestamp: 2026-07-12T15:20:00+09:00
status: verified
---

# Embedding 기능 흐름

## 1. 공통 생성

1. Unicode NFKC로 호환 문자를 정규화한다.
2. `Locale.KOREA` 기준 소문자화한다.
3. 모든 공백을 제거한다. 같은 SMS template의 띄어쓰기 차이는 같은 입력이 된다.
4. 전체 문자열과 경계 문자를 포함한 1~3글자 n-gram을 FNV-1a로 768 bucket에 signed hashing한다.
5. L2 norm을 1로 맞춰 cosine 비교가 가능하게 한다.
6. 빈 입력 또는 norm 0은 `null`이다. batch는 입력 순서를 보존한다.

## 2. SMS pattern

```text
prefilter 통과 SMS
-> templateize(amount/date/store placeholder)
-> local batch vector
-> 저장 pattern 최초 사용 갱신
-> sender 우선 후보 축소
-> non-payment / main payment / exception pattern 순서 비교
-> regex parse 성공: 결과 확정
-> 실패: asset/RTDB regex 또는 LLM tier
```

`storedEmbeddingsRefreshed`는 프로세스 단위 1회 guard다. 최초 실행에서 `SmsPatternEntity.smsTemplate`을 다시 embedding하고 달라진 행만 같은 id로 replace한다. 이후 새 pattern은 현재 query vector에서 직접 만들어지므로 추가 migration이 필요 없다.

## 3. Store/category

1. `StoreAliasManager.normalizeStoreName()`이 알려진 상호/영문명/지점명을 대표명으로 수렴한다.
2. alias가 없으면 원래 storeName으로 local store vector를 생성한다.
3. repository 최초 cache load에서 모든 저장 vector를 현재 contract와 비교한다.
4. 달라진 행은 category/source/confidence/matchCount/createdAt을 보존하고 vector와 updatedAt만 갱신한다.
5. `VectorSearchEngine`이 후보를 반환하고 policy가 자동 적용, grouping, propagation을 결정한다.
6. vector 후보가 없으면 exact mapping, local keyword 또는 Firebase AI Logic batch 분류 흐름이 계속된다.

## 4. 실패와 성능

- 네트워크, quota, 429 오류는 없다.
- 한 항목의 빈 입력은 그 항목만 `null`이며 전체 batch를 실패시키지 않는다.
- Room vector 갱신 실패는 앱 데이터 저장을 삭제하지 않는다. 해당 검색 tier가 후보를 만들지 못하면 다음 tier가 처리한다.
- local batch size와 concurrency는 API 제한이 아니라 CPU/sync 시간 제어값이다.
