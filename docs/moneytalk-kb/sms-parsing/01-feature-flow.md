---
type: feature-flow
title: 문자 파싱 Feature Flow
description: 문자 파싱 기능의 trigger, 파싱, 저장, refresh 흐름을 설명한다.
tags: [moneytalk, sms-parsing, flow]
resource: app/src/main/java/com/sanha/moneytalk/MainViewModel.kt
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 01 Feature Flow

## Trigger

| trigger | 진입 파일 | 비고 |
|---|---|---|
| 앱 resume | `MainViewModel.onAppResume()` | 권한 확인 후 silent 증분 동기화 |
| `DataRefreshEvent.RefreshType.SMS_RECEIVED` | `MainViewModel.observeDataRefreshEvents()` | 실시간 수신 후 동기화 |
| 수동/디버그 동기화 | `MainViewModel` sync 호출부 | 전체/오늘/월별 범위 |

## End-to-End Flow

```text
trigger
→ MainViewModel.syncSmsV2(...)
→ syncSmsV2Internal(...)
→ SmsSyncMessageReader / SmsReaderV2
→ 기존 DB snapshot과 중복 제거
→ SmsSyncCoordinator.process(...)
→ categoryClassifierService.initCategoryCache()
→ saveExpenses(...) / saveIncomes(...)
→ settingsDataStore sync watermark 저장
→ dataRefreshEvent.emit(TRANSACTION_ADDED)
```

## 주요 분기

- 이미 동기화 중이면 silent 요청은 재실행 예약 또는 skip된다.
- SMS provider read 실패는 lastSyncTime 갱신 여부와 연결된다.
- 지출 저장 전 `classifyStoreNamesInMemory`가 카테고리를 채운다.
- 수입 저장은 `SmsIncomeParser`/분류 결과와 별도 경로를 따른다.

## Side Effects

- `ExpenseEntity`, `IncomeEntity` batch insert
- sync coverage/watermark 저장
- `DataRefreshEvent.TRANSACTION_ADDED`
- 필요 시 카테고리 분류 후 `CATEGORY_UPDATED`
