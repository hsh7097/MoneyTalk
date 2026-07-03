---
type: file-inventory
title: Finance Data File Inventory
description: Finance Data 파일별 역할과 참조 시점을 인덱싱한다.
tags: [moneytalk, finance-data, file-inventory]
resource: app/src/main/java/com/sanha/moneytalk/core/database/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# Finance Data File Inventory

| 파일 | 역할 | 언제 보는가 | 분류 |
|---|---|---|---|
| `core/database/AppDatabase.kt` | Room DB 정의, entity/DAO/migration 등록 | DB schema, migration | 핵심 |
| `core/database/dao/ExpenseDao.kt` | 지출 DAO | 지출 query/CRUD | 핵심 |
| `core/database/dao/IncomeDao.kt` | 수입 DAO | 수입 query/CRUD | 핵심 |
| `core/database/dao/SmsPatternDao.kt` | SMS 패턴 DAO | 파싱 패턴 저장/조회 | 핵심 후보 |
| `core/database/entity/ExpenseEntity.kt` | 지출 entity | 지출 schema 변경 | 핵심 |
| `core/database/entity/IncomeEntity.kt` | 수입 entity | 수입 schema 변경 | 핵심 |
| `core/database/AiCreditRepository.kt` | AI 크레딧 잔액/원장 관리 | 크레딧 차감/충전 | 핵심 후보 |
| `core/database/OwnedCardRepository.kt` | 카드 화이트리스트 관리 | 카드 숨김/표시 | 핵심 후보 |
| `core/database/SmsExclusionRepository.kt` | SMS 제외 키워드 관리 | 제외 키워드 설정 | 핵심 후보 |
| `feature/home/data/ExpenseRepository.kt` | 지출 Repository | 화면 지출 데이터 | 핵심 |
| `feature/home/data/IncomeRepository.kt` | 수입 Repository | 화면 수입 데이터 | 핵심 |
| `feature/home/data/CategoryClassifierServiceImpl.kt` | 카테고리 분류 구현 | 자동 분류 | 핵심 후보 |
| `feature/home/data/StoreRuleRepository.kt` | 거래처 규칙 Repository | 거래처 규칙/소급 적용 | 핵심 후보 |
