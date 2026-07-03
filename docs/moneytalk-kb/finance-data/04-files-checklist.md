---
type: checklist
title: Finance Data Files Checklist
description: Finance Data 수정 전후에 확인할 파일과 검증 질문을 정리한다.
tags: [moneytalk, finance-data, checklist]
resource: app/src/main/java/com/sanha/moneytalk/core/database/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# 04 Files Checklist

## 수정 전 질문

- DB schema가 바뀌는가?
- migration이 필요한가?
- DAO query만 바뀌는가, Repository API도 바뀌는가?
- 변경 후 `DataRefreshEvent` 또는 화면 state 갱신이 필요한가?
- History/Home/Chat/App Functions 중 누가 같은 데이터를 읽는가?

## 빠른 파일 찾기

| 작업 | 확인 파일 |
|---|---|
| DB schema | `AppDatabase.kt`, entity, DAO |
| 지출 | `ExpenseEntity.kt`, `ExpenseDao.kt`, `ExpenseRepository.kt` |
| 수입 | `IncomeEntity.kt`, `IncomeDao.kt`, `IncomeRepository.kt` |
| SMS 패턴 | `SmsPatternEntity.kt`, `SmsPatternDao.kt`, `core/sms/*` |
| 보유 카드 | `OwnedCardEntity.kt`, `OwnedCardDao.kt`, `OwnedCardRepository.kt` |
| 크레딧 | `AiCreditBalanceEntity.kt`, `AiCreditLedgerEntity.kt`, `AiCreditDao.kt`, `AiCreditRepository.kt` |
