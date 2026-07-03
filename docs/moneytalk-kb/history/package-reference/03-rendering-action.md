---
type: rendering-action
title: History Rendering Action
description: History 화면의 Composable, action, navigation 연결을 설명한다.
tags: [moneytalk, history, rendering, action, compose]
resource: app/src/main/java/com/sanha/moneytalk/feature/history/ui/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# 03 Rendering Action

## 렌더링 구성

```text
HistoryScreen
├── HorizontalPager
├── LazyColumn 거래 목록
├── HistoryCalendar
├── HistoryFilter
├── HistoryDialogs
├── TransactionCardCompose
└── TransactionGroupHeaderCompose
```

## Action 흐름

- 외부 카테고리 필터는 `HistoryScreen`의 `LaunchedEffect(filterCategory)`에서 한 번 소비된다.
- 거래 클릭/삭제/메모/카테고리 변경은 `HistoryIntent`를 통해 `HistoryViewModel`로 전달된다.
- `+` 버튼 또는 수정 진입은 `TransactionEditActivity`를 확인한다.
- 동기화 CTA, 권한 상태는 `MainViewModel.screenSyncUiState`를 통해 표시된다.

## 수정 시 확인

| 작업 | 먼저 볼 파일 | 함께 볼 파일 |
|---|---|---|
| 목록 카드 UI 변경 | `HistoryScreen.kt` | `core/ui/component/transaction/card/*` |
| 그룹 헤더 변경 | `HistoryScreen.kt` | `core/ui/component/transaction/header/*` |
| 달력 UI 변경 | `HistoryCalendar.kt` | `HistoryViewModel.kt` |
| 필터 UI 변경 | `HistoryFilter.kt` | `HistoryViewModel.kt` |
| 상세/삭제/메모 dialog 변경 | `HistoryDialogs.kt` | `HistoryViewModel.kt` |
