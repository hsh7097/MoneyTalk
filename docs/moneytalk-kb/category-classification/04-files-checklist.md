---
type: checklist
title: 카테고리 분류 Files Checklist
description: 카테고리 분류 기능 수정 전후에 확인할 파일과 검증 질문을 정리한다.
tags: [moneytalk, category, checklist]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/data/
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 04 Files Checklist

## 빠른 파일 찾기

| 작업 | 확인 파일 |
|---|---|
| 자동 분류 단계 변경 | `CategoryClassifierServiceImpl.kt` |
| Gemini 분류 변경 | `GeminiCategoryRepositoryImpl.kt`, `CategoryReferenceProvider.kt` |
| 벡터 캐시 변경 | `StoreEmbeddingRepositoryImpl.kt`, `StoreEmbeddingDao.kt` |
| 수동 수정 학습 | `CategoryClassifierService.updateExpenseCategory`, `HistoryViewModel.kt` |
| 카테고리 저장 | `CategoryRepository.kt`, `CategoryMappingDao.kt` |
| App Functions 영향 | `MoneyTalkChatAppFunctions.kt`, `MoneyTalkChatAppFunctionReader.kt` |

## 수정 전 질문

- 자동 분류인지 수동 수정인지 구분했는가?
- 지출과 수입 카테고리 경로를 모두 확인했는가?
- custom category와 기본 category가 함께 동작하는가?
- 변경 후 `CATEGORY_UPDATED` refresh가 필요한가?
