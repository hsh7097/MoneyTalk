---
type: feature-flow
title: 카테고리 분류 Feature Flow
description: 카테고리 자동/수동 분류와 학습 흐름을 설명한다.
tags: [moneytalk, category, flow]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/data/CategoryClassifierServiceImpl.kt
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 01 Feature Flow

## 자동 분류 흐름

```text
storeName/source
→ StoreRule match
→ Room CategoryMapping
→ StoreEmbedding vector match
→ SmsParser/local keyword
→ StoreNameGrouper + Gemini batch
→ CategoryRepository / StoreEmbeddingRepository 저장
```

## SMS 저장 전 분류

```text
MainViewModel.saveExpenses(...)
→ categoryClassifierService.initCategoryCache()
→ categoryClassifierService.classifyStoreNamesInMemory(...)
→ ExpenseEntity.category 반영
→ expenseRepository.insertAll(...)
```

## 사용자 수동 수정 학습

```text
History/Home action
→ CategoryClassifierService.updateExpenseCategory(...)
→ CategoryRepository mapping update
→ ExpenseEntity update
→ StoreEmbeddingRepository source=user 저장
→ 유사 가게명 전파
→ DataRefreshEvent.CATEGORY_UPDATED
```

## 수입 분류

```text
unclassified incomes
→ IncomeCategoryMapper pre-classify
→ GeminiCategoryRepository.classifyIncomeSources(...)
→ IncomeRepository update
```
