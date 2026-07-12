---
type: log
title: 카테고리 분류 KB Change Log
description: 카테고리 분류 기능 KB 변경 상세 이력을 기록한다.
tags: [moneytalk, category, changelog]
resource: docs/moneytalk-kb/category-classification/
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 카테고리 분류 Change Log

## 2026-07-12

- release sideload 실기기에서 App Check 실패가 같은 실행의 두 분류 단계와 다음 resume에서 반복 호출되는 문제를 확인했다.
- 첫 App Check 인증 실패 뒤 15분 인메모리 쿨다운을 적용하고, 대기 중인 카테고리/수입 배치를 추가 호출 없이 종료하도록 계약을 추가했다.
- 카테고리/수입 LLM batch를 하나의 공유 permit으로 직렬화해 회로 차단 확인과 다음 네트워크 요청 사이의 경쟁을 제거했다.
- SMS 동기화 전체 Job, 홈/설정 수동 분류, MainViewModel 백그라운드 분류가 `ClassificationState`에서 상호배제되며, 전체 삭제 gate가 중복 호출과 취소 cleanup까지 직렬로 기다리도록 명시했다.
- sync coverage와 카드 자동 등록을 동기화 소유권 안에 포함하고, 성공 후 잔여 분류는 sync Job completion 뒤 실행하도록 보완했다.
- 삭제 gate epoch로 삭제 전 증분·월별 sync 준비 요청과 resume 분류 검사를 무효화하고, stale epoch 요청이 새 Job을 취소하지 못하도록 소유권 교체 검사를 원자화했다.
- cooldown local-only 1회 표시는 로컬 1차 분류 호출이 정상 반환한 뒤에만 기록한다.
- cooldown은 원격 Tier 3만 차단하며, 로컬 규칙 결과와 Gemini 결과의 mapping/embedding source를 분리했다.
- 갱신한 KB: `06-classification-tiers.md`, `change-log.md`

## 2026-07-09

- 기준: 루트 `CATEGORY_CLASSIFICATION.md`, `AI_CONTEXT.md` 카테고리 파트 재검토
- 변경 근거: 원문 문서를 이동하지 않고 StoreRule, Room, Vector 1.5a/1.5b, local keyword, Gemini batch, 사용자 수정 전파 기준을 KB 내부 문서로 흡수하기 위해 `06-classification-tiers.md`를 추가했다.
- 갱신한 KB: `README.md`, `06-classification-tiers.md`

## 2026-07-03

- 기준: `CategoryClassifierService`, `GeminiCategoryRepository`, `CategoryRepository`, `StoreEmbeddingRepository`, App Functions 관련 파일 확인
- 변경 근거: 기능 단위 KB 요구 반영
- 갱신한 KB: `category-classification/**`
- 다음 검증: 카테고리 룰 추가 작업에서 local rule, Gemini, vector cache, refresh 영향 파일을 정확히 찾는지 확인
