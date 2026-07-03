---
type: structure-map
title: 문자 파싱 기능 구조 지도
description: 문자 파싱 기능에 참여하는 entry, pipeline, 저장, refresh 파일을 정리한다.
tags: [moneytalk, sms-parsing, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 문자 파싱 기능 구조 지도

| 단계 | 파일 | 책임 |
|---|---|---|
| trigger/orchestrator | `MainViewModel.kt` | `syncSmsV2`, `syncSmsV2Internal`, 동시 실행 방지, 저장, refresh |
| 원본 읽기 | `core/sms/SmsSyncMessageReader.kt`, `SmsReaderV2.kt` | 기간 내 SMS/MMS/RCS 원본을 `SmsInput`으로 읽음 |
| 범위 계산 | `core/sync/SmsSyncRangeCalculator.kt`, `ProviderReadRangeCalculator.kt` | 증분/월별/provider scan 범위 계산 |
| 파싱 | `core/sms/SmsSyncCoordinator.kt`, `SmsPipeline.kt` | filter, Fast Path, Vector/LLM 파싱 |
| 카테고리 선분류 | `CategoryClassifierService.kt` | DB INSERT 전 가게명 카테고리 분류 |
| 저장 | `ExpenseRepository.kt`, `IncomeRepository.kt` | 지출/수입 batch insert |
| refresh | `DataRefreshEvent.kt` | `TRANSACTION_ADDED`, `CATEGORY_UPDATED` 등 화면 갱신 이벤트 |

## 관련 KB

| KB | 이유 |
|---|---|
| [sms-pipeline](../sms-pipeline/README.md) | 파서 내부 단계 |
| [finance-data](../finance-data/README.md) | DB, DAO, Repository |
| [category-classification](../category-classification/README.md) | 저장 전 카테고리 분류 |
| [history](../history/README.md) | 저장 후 내역 화면 표시 |
