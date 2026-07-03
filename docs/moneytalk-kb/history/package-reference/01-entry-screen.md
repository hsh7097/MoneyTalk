---
type: entry-screen
title: History Entry Screen
description: History 화면 진입, route argument, Activity-scoped sync state 연결을 설명한다.
tags: [moneytalk, history, entry, navigation]
resource: app/src/main/java/com/sanha/moneytalk/navigation/NavGraph.kt
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# 01 Entry Screen

## 진입 경로

`NavGraph.kt`에서 `Screen.History.route`를 통해 `HistoryScreen`으로 진입한다.
route argument는 nullable `category`이고, Home 등 외부 화면에서 카테고리 필터를 전달할 수 있다.

```text
NavGraph
→ composable(Screen.History.route)
→ HistoryScreen(filterCategory = category)
→ LaunchedEffect(filterCategory)
→ HistoryViewModel.applyFilter(...)
```

## Activity-scoped 상태

`HistoryScreen`은 `ComponentActivity`를 `viewModelStoreOwner`로 사용해 `MainViewModel`의 `screenSyncUiState`를 읽는다.
따라서 SMS 권한, 동기화 상태, 월별 coverage CTA는 History 내부 ViewModel만 보면 안 된다.

## 수정 시 확인

- route argument를 바꾸면 `navigation/Screen.kt`, `navigation/NavGraph.kt`, 호출 화면의 navigation 코드를 함께 확인한다.
- 권한/동기화 CTA 표시가 바뀌면 `MainViewModel.screenSyncUiState`와 `HistoryScreen`의 Activity-scoped ViewModel 사용을 함께 확인한다.
