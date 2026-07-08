---
type: feature-flow
title: Data Refresh 기능 흐름
description: DataRefreshEvent 발행자, 수집자, refresh type별 갱신 범위를 설명한다.
tags: [moneytalk, data-refresh, feature-flow]
resource: app/src/main/java/com/sanha/moneytalk/core/util/DataRefreshEvent.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Data Refresh 기능 흐름

## 흐름

```text
mutation or sync
-> DataRefreshEvent.emit(type)
-> HomeViewModel.observeDataRefreshEvents()
-> HistoryViewModel.observeDataRefreshEvents()
-> CategoryDetailViewModel.observeDataRefreshEvents()
-> current/adjacent page cache reload or clear
```

## RefreshType

| 타입 | 의미 |
|---|---|
| `ALL_DATA_DELETED` | 전체 데이터 삭제 또는 큰 복원 이후 전체 갱신 |
| `CATEGORY_UPDATED` | 카테고리 재분류/수정 완료 |
| `OWNED_CARD_UPDATED` | 카드 표시/숨김 설정 변경 |
| `TRANSACTION_ADDED` | 실시간 SMS 수신으로 거래 추가 |
| `SMS_RECEIVED` | SMS 수신 감지와 증분 동기화 trigger |
| `DEBUG_FULL_SYNC_ALL_MESSAGES` | debug 전체 메시지 동기화 trigger |
| `DEBUG_SYNC_TODAY_MESSAGES` | debug 오늘 메시지 동기화 trigger |

## 검증 질문

1. 새 mutation이 관련 화면에 refresh event를 발행하는가?
2. event 수집자가 중복 로딩 또는 무한 refresh를 만들지 않는가?
3. 월별 page cache를 clear할지 현재/인접 월만 reload할지 구분했는가?
4. debug 전용 refresh type이 release UX에 노출되지 않는가?
