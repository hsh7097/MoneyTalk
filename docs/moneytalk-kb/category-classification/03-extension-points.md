---
type: extension-points
title: 카테고리 분류 Extension Points
description: 카테고리 분류 기능의 확장 지점과 책임 경계를 정리한다.
tags: [moneytalk, category, extension]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/data/
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 03 Extension Points

| 확장 지점 | 파일 | 주의 |
|---|---|---|
| 로컬 pre-classify rule | `CategoryClassifierServiceImpl.kt` | Gemini 호출 전 비용 0 분류. 과매칭 주의 |
| Gemini prompt/model | `GeminiCategoryRepositoryImpl.kt`, `CategoryReferenceProvider.kt` | RTDB model config와 retry 영향 확인 |
| vector similarity | `StoreEmbeddingRepositoryImpl.kt`, `core/similarity/*` | threshold와 캐시 프로모션 영향 확인 |
| 사용자 거래처 규칙 | `StoreRuleRepository.kt`, `StoreRuleSyncService.kt` | 기존 거래 소급 적용 여부 확인 |
| custom category | `CustomCategoryRepository.kt`, App Functions, Settings | 표시/필터/요약 응답 영향 확인 |
