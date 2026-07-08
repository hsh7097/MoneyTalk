---
type: feature
title: Data Refresh 기능
description: DataRefreshEvent 기반 화면 간 데이터 변경 통지와 page cache 갱신 흐름을 설명한다.
tags: [moneytalk, data-refresh, cache, feature]
resource: app/src/main/java/com/sanha/moneytalk/core/util/DataRefreshEvent.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Data Refresh 기능

> 상태: draft
> 기준: 2026-07-08 현재 `DataRefreshEvent`, `HomeViewModel`, `HistoryViewModel`, `CategoryDetailViewModel`, `MainViewModel` 확인

Data Refresh 기능은 ViewModel 간 직접 참조 없이 거래/카테고리/카드/동기화 변경을 화면들에 통지하는 shared event 흐름이다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [01-feature-flow.md](01-feature-flow.md) | event 발행/수집/cache refresh 흐름을 설명한다. | 화면 데이터가 stale하거나 refresh가 과한 경우 |
| [change-log.md](change-log.md) | Data Refresh KB 변경 로그다. | 문서 변경 이유 확인 |
| [../home/README.md](../home/README.md) | Home cache 수집 경로 | Home stale data |
| [../history/README.md](../history/README.md) | History cache 수집 경로 | History stale data |
| [../category-detail/README.md](../category-detail/README.md) | Category Detail cache 수집 경로 | 상세 stale data |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `core/util/DataRefreshEvent.kt` | shared refresh event bus와 `RefreshType` enum |
| `MainViewModel.kt` | SMS sync/수신 후 refresh event 발행 |
| `HomeViewModel.kt` | refresh event 수집 후 page cache 갱신 |
| `HistoryViewModel.kt` | refresh event 수집 후 list/calendar 갱신 |
| `CategoryDetailViewModel.kt` | refresh event 수집 후 category detail cache 갱신 |
| `SettingsViewModel.kt` | 카드 보유/전체 삭제/분류 등 설정 변경 후 event 발행 |
