---
type: reference
title: Similarity And Pipeline Threshold Registry
description: MoneyTalk SMS/분류/채팅 관련 주요 임계값과 정책 소유자를 정리한다.
tags: [moneytalk, kb, threshold, similarity, sms, category]
resource: app/src/main/java/com/sanha/moneytalk/core/similarity/
timestamp: 2026-07-09T05:10:00+09:00
status: draft
---

# Similarity And Pipeline Threshold Registry

> 기준: 흡수된 프로젝트 컨텍스트 원문의 임계값 레지스트리를 KB용으로 재작성

임계값의 원칙은 `VectorSearchEngine`은 계산만 하고, 도메인별 `SimilarityPolicy` 구현체가 판단 기준을 소유한다는 것이다. 숫자를 바꿀 때는 이 문서를 먼저 보고 실제 구현체와 테스트를 함께 확인한다.

## SimilarityPolicy 계층

```text
VectorSearchEngine
-> cosineSimilarity/findTopK/findBestMatch
-> SimilarityPolicy
   -> SmsPatternSimilarityPolicy
   -> StoreNameSimilarityPolicy
   -> CategoryPropagationPolicy
-> Service/Repository 행동
```

## SMS 패턴 유사도

| 항목 | 값 | 의미 | 주요 소비자 |
|---|---:|---|---|
| `SmsPatternSimilarityPolicy.profile.confirm` | 0.92 | 결제 문자 패턴 판정 | `SmsPatternMatcher` |
| `SmsPatternSimilarityPolicy.profile.autoApply` | 0.95 | 캐시된 파싱 결과 재사용 | `SmsPatternMatcher` |
| `SmsPatternSimilarityPolicy.profile.group` | 0.95 | SMS 패턴 벡터 그룹핑 | `SmsGroupClassifier` |
| `NON_PAYMENT_CACHE_THRESHOLD` | 0.97 | 비결제 패턴 캐시 히트 | `SmsPatternMatcher` |
| `LLM_TRIGGER_THRESHOLD` | 0.80 | LLM 호출 후보 선별 | `SmsPatternMatcher` |
| `RemoteSmsRule.DEFAULT_MIN_SIMILARITY` | 0.94 | RTDB 원격 룰 매칭 최소 유사도 | `RemoteSmsRule` |
| `RemoteSmsRuleRepository.CACHE_TTL_MS` | 10분 | 원격 룰 메모리 캐시 TTL | `RemoteSmsRuleRepository` |

## SmsGroupClassifier 주요 상수

| 항목 | 값 | 의미 |
|---|---:|---|
| `GROUPING_SIMILARITY` | 0.95 | 벡터 그룹핑 유사도 |
| `SMALL_GROUP_MERGE_THRESHOLD` | 5 | 소그룹 병합 대상 멤버 수 상한 |
| `SMALL_GROUP_MERGE_MIN_SIMILARITY` | 0.90 | 소그룹 병합 시 대표 벡터 최소 유사도 |
| `SIMILARITY_GROUPING_MAX_SIZE` | 120 | 유사도 그룹핑 최대 SMS 수 |
| `LLM_CONCURRENCY` | 5 | LLM 병렬 동시 실행 수 |
| `LLM_FALLBACK_MAX_SAMPLES` | 10 | Step 4.5 배치 LLM chunk 크기 |
| `GROUP_CONTEXT_MAX_SAMPLES` | 10 | 그룹 LLM 컨텍스트 샘플 수 |
| `REGEX_SAMPLE_SIZE` | 5 | regex 생성 샘플 수 |
| `REGEX_MIN_SAMPLES` | 5 | regex 생성 최소 그룹 크기 |
| `REGEX_VALIDATION_MIN_PASS_RATIO` | 0.80 | regex 검증 최소 파싱 성공률 |
| `REGEX_NEAR_MISS_MIN_RATIO` | 0.60 | near-miss regex 재시도 기준 |
| `REGEX_FAILURE_THRESHOLD` | 2 | regex 생성 실패 쿨다운 기준 |
| `REGEX_FAILURE_COOLDOWN_MS` | 30분 | regex 생성 실패 쿨다운 |
| `GROUP_TIME_BUDGET_MS` | 10초 | 단일 그룹 처리 제한 |
| `STEP5_TIME_BUDGET_MS` | 20초 | Step 5 전체 제한 |
| `UNSTABLE_MEDIAN_MIN_SIMILARITY` | 0.92 | unstable 그룹 중앙값 기준 |
| `UNSTABLE_P20_MIN_SIMILARITY` | 0.88 | unstable 그룹 P20 기준 |
| `LEARNING_QUEUE_MAX_SIZE` | 1000 | 백그라운드 학습 큐 최대 크기 |

## SMS 파싱 source 신뢰도

| `SmsPatternEntity.parseSource` | 의미 | confidence |
|---|---|---:|
| `regex` | 하드코딩 정규식 파싱 | 1.0 |
| `llm_regex` | LLM 생성 정규식 파싱 성공 | 1.0 |
| `template_regex` | 같은 그룹 내 다른 SMS regex 재사용 | 0.85 |
| `llm` | LLM 직접 추출 | 0.8 |
| `llm_non_payment` | LLM 비결제 판정 | 0.8 |
| `remote_rule` | RTDB 원격 룰 매칭 후 로컬 승격 | 1.0 |

## 가게명/카테고리 유사도

| 항목 | 값 | 의미 | 주요 소비자 |
|---|---:|---|---|
| `StoreNameSimilarityPolicy.profile.autoApply` | 0.92 | 가게명 -> 카테고리 자동 적용 | `StoreEmbeddingRepository` |
| `StoreNameSimilarityPolicy.profile.confirm` | 0.92 | 가게명 매칭 확정 | `StoreEmbeddingRepository` |
| `StoreNameSimilarityPolicy.profile.propagate` | 0.90 | 유사 가게 카테고리 전파 | `StoreEmbeddingRepository` |
| `StoreNameSimilarityPolicy.profile.group` | 0.88 | 가게명 시맨틱 그룹핑 | `StoreNameGrouper` |
| `CategoryPropagationPolicy.profile.propagate` | 0.90 | 전파 유사도 기준 | `StoreEmbeddingRepository` |
| `CategoryPropagationPolicy.MIN_PROPAGATION_CONFIDENCE` | 0.6 | 낮은 confidence 전파 차단 | `StoreEmbeddingRepository` |

`MIN_PROPAGATION_CONFIDENCE` 0.6은 regex/remote rule/source=user처럼 신뢰도가 높은 결과만 전파하고, 낮은 품질의 LLM/heuristic 결과가 다수 거래로 번지는 것을 막기 위한 하한이다.

## 숫자별 의미

```text
0.97  비결제 패턴 캐시 재사용
0.95  결제 패턴 캐시 재사용, SMS 벡터 그룹핑
0.94  원격 룰 최소 유사도
0.92  결제 문자 판정, 가게명 자동 적용/확정
0.90  카테고리 전파, 소그룹 병합 최소 유사도
0.88  가게명 시맨틱 그룹핑, unstable P20 기준
0.85  template_regex confidence
0.80  LLM trigger, LLM 기본 confidence, regex 검증 pass ratio
0.60  카테고리 전파 confidence 차단, near-miss regex 재시도 기준
```
