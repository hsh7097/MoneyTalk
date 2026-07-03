---
type: data-contract
title: 카테고리 분류 Data Contract
description: 카테고리 분류 기능의 주요 저장 모델과 응답 contract를 설명한다.
tags: [moneytalk, category, data-contract]
resource: app/src/main/java/com/sanha/moneytalk/core/database/entity/CategoryMappingEntity.kt
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 02 Data Contract

| 데이터 | 역할 | 관련 파일 |
|---|---|---|
| `CategoryMappingEntity` | 가게명-카테고리 정확 매핑 | `CategoryRepository.kt`, `CategoryMappingDao.kt` |
| `StoreEmbeddingEntity` | 가게명 embedding과 category cache | `StoreEmbeddingRepositoryImpl.kt`, `StoreEmbeddingDao.kt` |
| `ExpenseEntity.category` | 지출 표시/통계 카테고리 | `ExpenseRepository.kt`, History/Home |
| `IncomeEntity.category` | 수입 카테고리 | `IncomeRepository.kt`, `IncomeCategoryMapper.kt` |
| Gemini batch result | 가게명/출처별 분류 결과 | `GeminiCategoryRepositoryImpl.kt` |

## 변경 시 주의

- 카테고리 표시명이 바뀌면 custom category, filter, App Functions 응답까지 확인한다.
- embedding model/threshold가 바뀌면 vector cache 품질과 재분류 경로를 확인한다.
- 수입 카테고리와 지출 카테고리는 mapper가 다를 수 있다.
