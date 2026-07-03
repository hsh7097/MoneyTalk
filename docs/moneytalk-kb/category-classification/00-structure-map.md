---
type: structure-map
title: 카테고리 분류 구조 지도
description: 카테고리 분류 기능에 참여하는 service, repository, DB 파일을 정리한다.
tags: [moneytalk, category, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/data/
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 카테고리 분류 구조 지도

| 단계 | 파일 | 책임 |
|---|---|---|
| orchestration | `CategoryClassifierService.kt`, `CategoryClassifierServiceImpl.kt` | 4-tier 분류, 수동 수정 학습, 수입 분류 |
| 저장 매핑 | `CategoryRepository.kt`, `CategoryMappingDao.kt` | 가게명-카테고리 Room mapping |
| Gemini | `GeminiCategoryRepository.kt`, `GeminiCategoryRepositoryImpl.kt` | batch 분류, 수입 출처 분류, retry |
| vector cache | `StoreEmbeddingRepository.kt`, `StoreEmbeddingRepositoryImpl.kt`, `StoreEmbeddingDao.kt` | 유사 가게명 매칭과 캐시 |
| 거래처 규칙 | `StoreRuleRepository.kt`, `StoreRuleSyncService.kt` | 사용자 정의 규칙과 소급 적용 |
| 화면 trigger | `HomeViewModel.kt`, `HistoryViewModel.kt` | 미분류 정리, 수동 category 변경 |

## 관련 KB

| KB | 이유 |
|---|---|
| [sms-parsing](../sms-parsing/README.md) | 저장 전 카테고리 분류 호출 |
| [finance-data](../finance-data/README.md) | mapping/entity/DAO |
| [history](../history/README.md) | 수동 카테고리 변경/필터 영향 |
