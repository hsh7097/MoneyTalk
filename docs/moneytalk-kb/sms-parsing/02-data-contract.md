---
type: data-contract
title: 문자 파싱 Data Contract
description: 문자 파싱 기능의 주요 input/output model과 저장 contract를 설명한다.
tags: [moneytalk, sms-parsing, data-contract]
resource: app/src/main/java/com/sanha/moneytalk/core/sms/SmsPipelineModels.kt
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 02 Data Contract

| 단계 | input | output | 관련 파일 |
|---|---|---|---|
| 원본 읽기 | provider row | `SmsInput` | `SmsReaderV2.kt`, `SmsPipelineModels.kt` |
| 파싱 결과 | `List<SmsInput>` | `SyncResult` | `SmsSyncCoordinator.kt` |
| 지출 저장 | parse result expense | `ExpenseEntity` | `MainViewModel.saveExpenses`, `ExpenseRepository.kt` |
| 수입 저장 | parse result income | `IncomeEntity` | `MainViewModel.saveIncomes`, `IncomeRepository.kt` |
| 화면 갱신 | 저장 결과 | `DataRefreshEvent` | `DataRefreshEvent.kt` |

## 변경 시 주의

- `SmsInput` 필드가 바뀌면 reader, pipeline, dedupe를 같이 본다.
- `SyncResult` 필드가 바뀌면 `MainViewModel` 저장 로직을 같이 본다.
- Entity column이 바뀌면 [finance-data](../finance-data/README.md)와 migration을 확인한다.
