---
type: how-to-use
title: Finance Data How To Use
description: Finance Data 수정 시 작업 유형별 참조 순서를 정리한다.
tags: [moneytalk, finance-data, workflow]
resource: app/src/main/java/com/sanha/moneytalk/core/database/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# 02 How To Use

| 작업 | 먼저 볼 문서 | 핵심 파일 |
|---|---|---|
| 새 entity/column 추가 | `00-structure-map.md`, `04-files-checklist.md` | `AppDatabase.kt`, entity, DAO, migration |
| 지출 query 변경 | `05-file-inventory.md` | `ExpenseDao.kt`, `ExpenseRepository.kt`, 영향 ViewModel |
| 수입 query 변경 | `05-file-inventory.md` | `IncomeDao.kt`, `IncomeRepository.kt`, 영향 ViewModel |
| 카드 숨김/보유 카드 변경 | `05-file-inventory.md` | `OwnedCardDao.kt`, `OwnedCardRepository.kt`, `CardVisibilityFilter.kt` |
| 카테고리 분류 변경 | `03-extension-points.md` | `CategoryClassifierServiceImpl.kt`, `CategoryRepository.kt` |

화면 표시 문제라면 해당 도메인 KB도 같이 본다.
예를 들어 History 목록 필터 문제는 `history` KB와 함께 확인한다.
