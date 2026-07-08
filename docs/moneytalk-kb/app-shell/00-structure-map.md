---
type: structure-map
title: App Shell 구조 지도
description: MoneyTalk 앱 진입, 하단 탭, NavGraph, Activity-scoped 상태 파일의 역할을 정리한다.
tags: [moneytalk, app-shell, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# App Shell 구조 지도

## 진입 흐름

```text
MainActivity.onCreate()
-> MoneyTalkTheme
-> MoneyTalkApp()
   -> rememberNavController()
   -> MainViewModel.dialogUiState
   -> AppSnackbarBus events
   -> screen view analytics
   -> Activity-level dialogs
   -> Scaffold(bottomBar = NavigationBar)
   -> NavGraph()
      -> HomeScreen
      -> HistoryScreen
      -> ChatScreen
      -> SettingsScreen
```

## 파일 역할

| 파일 | 분류 | 역할 | 함께 볼 파일 |
|---|---|---|---|
| `MainActivity.kt` | entry/rendering/action | root Compose, 권한 요청, 전역 snackbar, 하단 탭, sync/ad/update dialog, back press | `MainViewModel.kt`, `navigation/NavGraph.kt` |
| `MainViewModel.kt` | data/action | SMS sync orchestration, permission gate, full sync ad unlock, `DataRefreshEvent` 발행 | `MainUiState.kt`, `core/sms/**`, `feature/home/data/**` |
| `MainUiState.kt` | state | sync progress, dialog flags, engine summary, full sync ad dialog state | `MainViewModel.kt`, `MainActivity.kt` |
| `navigation/Screen.kt` | routing | route 문자열과 History category argument 생성 | `NavGraph.kt`, `BottomNavItem.kt` |
| `navigation/BottomNavItem.kt` | routing/rendering | 4개 bottom tab label/icon 정의 | `strings.xml`, `MainActivity.kt` |
| `navigation/NavGraph.kt` | routing | route별 screen Composable 연결과 Home/History tab event 전달 | 각 screen KB |

## AI 참조 순서

| 작업 | 참조 순서 |
|---|---|
| 하단 탭 추가/변경 | `app-shell/README.md` -> `Screen.kt` -> `BottomNavItem.kt` -> `NavGraph.kt` -> `MainActivity.kt` |
| 앱 시작/권한/강제 업데이트 | `MainActivity.kt` -> `ForceUpdateChecker`/`ForceUpdateDialog` -> `MainViewModel.kt` |
| 월별 전체 동기화 광고 | `MainActivity.kt` -> `MainViewModel.showFullSyncAdDialog()` -> `RewardAdManager` -> `sms-parsing` KB |
| 탭별 화면 세부 수정 | `app-shell/README.md` -> 해당 화면 KB (`home`, `history`, `chat`, `settings`) |

## 주의 경계

- `MainViewModel`은 Activity-scoped 공유 상태를 담당한다. 홈/내역 화면 내부 state를 직접 넣지 않는다.
- `NavGraph`는 하단 탭 route 연결만 담당한다. 개별 화면의 dialog/action 세부 구현은 각 화면 파일에 둔다.
- Bottom tab title은 `strings.xml`의 `nav_*` resource를 사용한다.
