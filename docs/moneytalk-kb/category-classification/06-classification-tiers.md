---
type: reference
title: Category Classification Tiers
description: StoreRule, Room, Vector, Keyword, Gemini Batch로 이어지는 카테고리 자동 분류 계약을 설명한다.
tags: [moneytalk, category, classification, vector, gemini]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/data/CategoryClassifierService.kt
timestamp: 2026-07-12T20:30:00+09:00
status: draft
---

# Category Classification Tiers

> 기준: 흡수된 카테고리 분류 원문과 project-context 카테고리 파트를 KB용으로 재작성

MoneyTalk의 지출 카테고리 분류는 가게명과 사용자 학습 데이터를 기준으로 점진적으로 비용이 낮은 경로부터 시도한다. 수동 수정 결과는 다음 자동 분류와 채팅 참조에도 반영된다.

## 처리 순서

```text
storeName
-> Tier 0: StoreRule
-> Tier 1: Room exact mapping
-> Tier 1.5a: Vector best match
-> Tier 1.5b: Vector group majority
-> Tier 2: Local keyword
-> Tier 3: Gemini batch classification
-> CategoryMappingEntity / StoreEmbeddingEntity / ExpenseEntity 반영
```

## Tier별 계약

| Tier | 책임 | 비용 | 저장/학습 |
|---|---|---|---|
| 0 StoreRule | 사용자가 만든 거래처 규칙을 최우선 적용 | 무료 | 규칙에 category, fixed, stats 제외 값을 보존 |
| 1 Room exact mapping | 이미 확정된 `storeName -> category` 정확 매핑 조회 | 무료 | `CategoryMappingEntity` |
| 1.5a Vector best match | 가게명 임베딩 최고 매칭이 0.92 이상이면 자동 적용 | embedding 1회 | 성공 시 Room mapping으로 promotion |
| 1.5b Vector group majority | 0.88 이상 유사 가게 그룹의 다수결 카테고리 적용 | 1.5a 임베딩 재사용 | 분류 범위 확장, Room promotion |
| 2 Local keyword | `SmsParser.inferCategory()` 키워드 사전 매칭 | 무료 | `source=local` mapping 저장 |
| 3 Gemini batch | 미분류 가게를 그룹핑해 대표만 Gemini 분류 | Gemini 호출 | mapping/embedding 저장, 지출 업데이트 |

## 핵심 저장 모델

| 모델 | 역할 |
|---|---|
| `CategoryMappingEntity` | 정규화된 가게명 exact mapping. `source=local/gemini/user/vector` |
| `StoreEmbeddingEntity` | 가게명 embedding, category, source, confidence, matchCount |
| `StoreRuleEntity` | 키워드 기반 category/fixed/stats 제외 규칙 |
| `CustomCategoryEntity` | 사용자 정의 카테고리 |

## 유사도 기준

| 기준 | 값 | 의미 |
|---|---:|---|
| 자동 적용 | 0.92 | 유사 가게명 카테고리를 바로 적용 |
| 그룹 다수결 | 0.88 | 여러 유사 가게의 다수 카테고리로 분류 |
| 전파 | 0.90 | 사용자 수정 결과를 유사 가게에 전파 |
| 전파 confidence 하한 | 0.6 | 낮은 신뢰도 결과의 대량 전파 차단 |

숫자의 SSOT는 [../project-context/02-threshold-registry.md](../project-context/02-threshold-registry.md)와 `core/similarity/**` 구현체다.

## Local keyword 범위

`SmsParser.inferCategory()`는 식비/배달/카페/교통/쇼핑/구독/의료/운동/문화/교육/보험/생활/계좌이체 등 키워드 기반 분류를 담당한다. `배달`은 식비의 하위 leaf 카테고리이며, 카테고리별 화면/필터에서는 leaf로 분리하고 채팅에서 `식비`를 명시 조회할 때만 하위 포함 집계를 사용한다.

## Gemini batch 최적화

- 미분류 가게명 전체를 그대로 보내지 않는다.
- `StoreNameGrouper`가 embedding으로 유사 가게를 그룹핑한다.
- 그룹 대표만 Gemini에 전송하고 결과를 멤버에게 전파한다.
- 최대 10라운드 반복하고, 진전이 없으면 조기 종료한다.

## AI 실패 격리와 동시 실행 계약

- Firebase AI Logic 운영 플래그가 꺼져 있거나 App Check 인증 실패 쿨다운 중이면 Tier 3만 건너뛴다. StoreRule, Room, vector, local keyword 결과와 이미 저장된 거래 집계는 유지한다.
- `Firebase App Check token is invalid`처럼 App Check 토큰·attestation 오류가 확인되면 첫 실패 뒤 해당 프로세스의 카테고리 AI 호출을 15분간 차단한다. 카테고리/수입 LLM batch는 하나의 permit으로 직렬 실행하므로 이미 슬롯을 기다리던 나머지 배치는 추가 네트워크 호출 없이 종료한다.
- 429/quota 오류는 App Check 실패와 구분하며 기존 `FirebaseAiRateLimitPolicy`의 retry-after, 지수 백오프, 요청 간격 제한을 적용한다.
- SMS 동기화, 백그라운드 자동 분류, 홈/설정의 수동 카테고리 정리는 `ClassificationState` 소유권으로 하나만 실행한다. 동기화는 기존 분류의 취소 완료를 기다리고 DB 저장, sync coverage, 카드 자동 등록까지 바깥 Job 하나로 소유한다. 성공 후 잔여 분류는 이 Job의 completion으로 소유권이 해제된 뒤 시작한다.
- 전체 삭제 gate는 중복 호출도 직렬화하며, 진행 Job의 취소 cleanup을 끝까지 기다리는 동안 신규 등록을 거부한다. gate epoch가 삭제 전에 생성된 증분·월별 sync와 resume 분류 검사를 무효화하며, 동기화 취소 시 진행 UI와 예약된 silent 재동기화도 함께 정리한 뒤 DB를 비운다.
- cooldown 중 수동 정리를 실행해도 StoreRule/vector/local keyword 단계는 계속 동작한다. 이 결과는 `source=local`로 저장하고 Gemini 결과·embedding만 `source=gemini`로 저장한다.
- release sideload는 Play Integrity attestation을 만족하지 못할 수 있다. 이 경우 로컬 분류와 화면 회귀를 검증하고, AI 정상 응답은 Play 배포본에서 별도로 확인한다.

## 동적 참조 리스트

`CategoryReferenceProvider`는 사용자 학습 매핑을 SMS 추출, 카테고리 분류, AI 채팅 프롬프트에 주입한다.

| 사용처 | 목적 |
|---|---|
| SMS 추출 | 가게명/카테고리 문맥 보강 |
| 카테고리 분류 | 사용자 학습 예시 반영 |
| AI 채팅 | 채팅 답변에서 앱의 카테고리 체계 유지 |

## 변경 시 주의

1. StoreRule이 가장 먼저 적용되어야 한다.
2. Room exact mapping 성공 시 embedding/Gemini 비용이 발생하면 안 된다.
3. Vector 성공 결과는 다음 조회 비용 절감을 위해 Room mapping으로 promotion해야 한다.
4. 사용자 수동 수정은 `source=user`로 보존하고 전파 confidence 기준을 통과한 경우에만 유사 가게에 반영한다.
5. 프롬프트가 실제 카테고리 displayName에 없는 축약명을 만들지 않도록 유지한다.
6. App Check 실패를 일반적인 빈 분류 결과로만 취급해 resume마다 재호출하지 않는다.
