---
type: structure-map
title: Finance Data 구조 지도
description: Finance Data의 DB, DAO, Repository 구조와 핵심 파일을 정리한다.
tags: [moneytalk, finance-data, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/core/database/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# Finance Data 구조 지도

## Package Groups

| group | 책임 | 대표 파일 |
|---|---|---|
| database root | Room DB와 DB-backed repository | `AppDatabase.kt`, `AiCreditRepository.kt`, `OwnedCardRepository.kt` |
| dao | Room DAO | `ExpenseDao.kt`, `IncomeDao.kt`, `SmsPatternDao.kt` |
| entity | Room Entity와 extension | `ExpenseEntity.kt`, `IncomeEntity.kt`, `SmsPatternEntity.kt` |
| converter | Room type converter | `FloatListConverter.kt` |
| feature home data | 지출/수입/카테고리/거래처 repository와 분류 service | `ExpenseRepository.kt`, `IncomeRepository.kt`, `CategoryClassifierServiceImpl.kt` |
| di | Hilt 제공 모듈 | `DatabaseModule.kt`, `RepositoryModule.kt` |

## 핵심 파일

| 파일 | 역할 | 함께 볼 파일 |
|---|---|---|
| `core/database/AppDatabase.kt` | Room entity/DAO 등록, DB version, migration 정의 | `core/di/DatabaseModule.kt`, entity/dao 파일 |
| `core/database/dao/ExpenseDao.kt` | 지출 CRUD/query | `ExpenseEntity.kt`, `ExpenseRepository.kt` |
| `core/database/dao/IncomeDao.kt` | 수입 CRUD/query | `IncomeEntity.kt`, `IncomeRepository.kt` |
| `feature/home/data/ExpenseRepository.kt` | 지출 Repository | `ExpenseDao.kt`, History/Home ViewModel |
| `feature/home/data/IncomeRepository.kt` | 수입 Repository | `IncomeDao.kt`, History/Home ViewModel |
| `feature/home/data/CategoryClassifierServiceImpl.kt` | 카테고리 분류 서비스 구현 | Gemini/category repository, store embedding |

## AI Reference Order

| 작업 유형 | 참조 순서 |
|---|---|
| DB schema 변경 | `README.md` -> `00-structure-map.md` -> `AppDatabase.kt` -> entity/dao -> DI |
| DAO query 변경 | `README.md` -> `05-file-inventory.md` -> DAO -> Repository -> 영향 ViewModel |
| Repository 변경 | `README.md` -> `02-how-to-use.md` -> Repository -> DAO/entity -> 영향 도메인 |
| 카테고리/거래처 분류 변경 | `README.md` -> `03-extension-points.md` -> `feature/home/data/*Classifier*` |
