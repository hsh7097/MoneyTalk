---
type: package-reference
title: Home entry/screen
description: Home 탭 진입, 월 이동, 탭 재클릭, Activity-scoped 상태 연결을 설명한다.
tags: [moneytalk, home, entry, screen]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Home entry/screen

## 진입

```text
MainActivity.MoneyTalkApp
-> NavGraph(Screen.Home.route)
-> HomeScreen(onRequestSmsPermission, homeTabReClickEvent)
```

## entry contract

| 파라미터 | 의미 |
|---|---|
| `onRequestSmsPermission` | SMS 권한이 필요한 동기화/분류 흐름에서 Activity 권한 요청을 호출한다. |
| `homeTabReClickEvent` | 하단 홈 탭 재클릭 시 현재 페이지 refresh 또는 스크롤 복귀성 이벤트로 사용된다. |

## 월 이동

- `HomeScreen`은 `HomeViewModel`의 현재 year/month state와 page cache를 읽는다.
- 이전/다음 월 이동은 `HomeViewModel.previousMonth()`, `nextMonth()`, `setMonth()`로 들어간다.
- 커스텀 월 시작일은 `SettingsDataStore`와 `DateUtils` 계산에 영향을 준다.

## 전역 상태 연결

- SMS 동기화 dialog와 월별 full sync 광고 dialog는 `MainActivity`/`MainViewModel` 책임이다.
- Home은 필요한 시점에 `onRequestSmsPermission` 또는 `mainViewModel.showFullSyncAdDialog()`를 호출한다.
- 데이터 변경 후 화면 갱신은 `DataRefreshEvent`를 통해 HomeViewModel이 수집한다.
