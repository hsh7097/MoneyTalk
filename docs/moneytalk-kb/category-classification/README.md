---
type: feature
title: 카테고리 분류 기능
description: 지출/수입 카테고리 자동 분류, 수동 수정 학습, 벡터 캐시, Gemini 분류 흐름을 설명한다.
tags: [moneytalk, category, classification, feature]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/data/CategoryClassifierService.kt
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 카테고리 분류 기능

> 상태: draft
> 기준: 2026-07-03 현재 `CategoryClassifierService`, `CategoryRepository`, `GeminiCategoryRepository`, `StoreEmbeddingRepository`, `StoreRuleRepository` 확인

카테고리 분류 기능은 가게명/수입 출처를 카테고리로 매핑하고, 사용자 수정 결과를 학습해 다음 분류에 반영한다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-structure-map.md](00-structure-map.md) | 분류 기능 관련 파일과 책임을 정리한다. | 변경 파일이 분류 기능의 어느 단계인지 판단할 때 본다. |
| [01-feature-flow.md](01-feature-flow.md) | 자동/수동 분류 end-to-end 흐름을 설명한다. | 분류 결과가 어떻게 정해지고 저장되는지 볼 때 본다. |
| [02-data-contract.md](02-data-contract.md) | category mapping, embedding, income category contract를 설명한다. | 저장 model이나 분류 응답이 바뀔 때 본다. |
| [03-extension-points.md](03-extension-points.md) | 새 분류 룰, Gemini prompt, custom category 확장 지점을 정리한다. | 분류 로직을 확장할 때 본다. |
| [04-files-checklist.md](04-files-checklist.md) | 수정 전후 확인 파일과 검증 질문이다. | 리뷰 전 side effect 점검 |
| [05-file-inventory.md](05-file-inventory.md) | 관련 파일 역할 인덱스다. | 수정 후보가 애매할 때 본다. |
| [change-log.md](change-log.md) | 기능 KB 변경 로그다. | 변경 이유 확인 |

## 기능 요약

```text
storeName/source
→ CategoryClassifierService
→ StoreRule / Room mapping / vector embedding / local keyword / Gemini
→ CategoryMappingEntity, StoreEmbeddingEntity, ExpenseEntity 또는 IncomeEntity 반영
→ DataRefreshEvent.CATEGORY_UPDATED
```
