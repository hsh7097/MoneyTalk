---
type: extension-points
title: Finance Data Extension Points
description: Finance Data의 확장 지점과 변경 시 주의할 계약을 정리한다.
tags: [moneytalk, finance-data, extension]
resource: app/src/main/java/com/sanha/moneytalk/core/database/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# 03 Extension Points

| 확장 지점 | 파일 | 주의 |
|---|---|---|
| 신규 저장 모델 | `core/database/entity/*`, `AppDatabase.kt` | DB version과 migration 필요 여부를 먼저 판단한다. |
| 신규 DAO query | `core/database/dao/*Dao.kt` | Repository와 ViewModel 사용처를 함께 갱신한다. |
| Repository API | `feature/home/data/*Repository.kt`, `core/database/*Repository.kt` | UI refresh event 또는 기존 도메인 state 영향 확인 |
| AI 크레딧 | `AiCreditRepository.kt`, `AiCreditDao.kt` | 원장과 잔액 일관성 확인 |
| 카테고리/거래처 분류 | `CategoryClassifierServiceImpl.kt`, `StoreRuleRepository.kt` | 기존 거래 소급 적용 여부 확인 |
