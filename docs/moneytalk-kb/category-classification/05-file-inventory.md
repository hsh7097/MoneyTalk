---
type: file-inventory
title: 카테고리 분류 File Inventory
description: 카테고리 분류 기능 관련 파일 역할과 참조 시점을 인덱싱한다.
tags: [moneytalk, category, file-inventory]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/data/
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 카테고리 분류 File Inventory

| 파일 | 역할 | 언제 보는가 | 분류 |
|---|---|---|---|
| `CategoryClassifierService.kt` | 분류 service contract | 기능 entry 확인 | 핵심 |
| `CategoryClassifierServiceImpl.kt` | 4-tier 분류 구현 | 자동/수동 분류 변경 | 핵심 |
| `CategoryRepository.kt` | category mapping 저장/조회 | Room mapping 문제 | 핵심 |
| `GeminiCategoryRepositoryImpl.kt` | Gemini batch 분류 | LLM 분류 품질/비용 | 핵심 |
| `StoreEmbeddingRepositoryImpl.kt` | vector cache와 유사도 매칭 | 유사 가게명 전파 | 핵심 |
| `StoreRuleRepository.kt` | 거래처 규칙 저장/조회 | 사용자 규칙 | 핵심 후보 |
| `StoreRuleSyncService.kt` | 규칙 변경 후 기존 거래 소급 | 소급 적용 | 핵심 후보 |
| `IncomeCategoryMapper.kt` | 수입 category 사전 분류 | 수입 분류 | 핵심 후보 |
| `CategoryReferenceProvider.kt` | Gemini prompt 참조 데이터 | prompt/reference 변경 | 핵심 후보 |
