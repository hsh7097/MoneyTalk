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

## 2026-07-09

- 기준: 루트 `CATEGORY_CLASSIFICATION.md`, `AI_CONTEXT.md` 카테고리 파트 재검토
- 변경 근거: 원문 문서를 이동하지 않고 StoreRule, Room, Vector 1.5a/1.5b, local keyword, Gemini batch, 사용자 수정 전파 기준을 KB 내부 문서로 흡수하기 위해 `06-classification-tiers.md`를 추가했다.
- 갱신한 KB: `README.md`, `06-classification-tiers.md`

## 2026-07-03

- 기준: `CategoryClassifierService`, `GeminiCategoryRepository`, `CategoryRepository`, `StoreEmbeddingRepository`, App Functions 관련 파일 확인
- 변경 근거: 기능 단위 KB 요구 반영
- 갱신한 KB: `category-classification/**`
- 다음 검증: 카테고리 룰 추가 작업에서 local rule, Gemini, vector cache, refresh 영향 파일을 정확히 찾는지 확인
