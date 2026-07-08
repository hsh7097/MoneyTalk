---
type: file-inventory
title: App Shell 파일 인벤토리
description: 앱 진입과 하단 탭 관련 파일의 역할, 확인 시점, 함께 볼 파일을 정리한다.
tags: [moneytalk, app-shell, file-inventory]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# App Shell 파일 인벤토리

| 파일 | 역할 | 언제 보는가 | 분류 | 함께 볼 파일 |
|---|---|---|---|---|
| `MainActivity.kt` | Activity와 root Compose, 하단 탭, 전역 다이얼로그 | 앱 진입, 하단 탭, sync dialog, back press | 핵심 | `MainViewModel.kt`, `NavGraph.kt` |
| `MainViewModel.kt` | Activity-scoped sync/권한/광고/refresh orchestration | SMS 동기화, full sync ad, refresh event | 핵심 | `MainUiState.kt`, `sms-parsing/README.md` |
| `MainUiState.kt` | Main UI/dialog 상태 model | sync progress 또는 dialog 상태 필드 변경 | 핵심 후보 | `MainViewModel.kt` |
| `MoneyTalkApplication.kt` | Hilt app, App Functions 등록 | 앱 초기화, App Functions factory | 핵심 후보 | `app-functions/README.md` |
| `navigation/Screen.kt` | route 정의 | route argument, 새 탭 route | 핵심 | `NavGraph.kt` |
| `navigation/BottomNavItem.kt` | bottom tab label/icon | 하단 탭 표시 변경 | 핵심 | `strings.xml`, `MainActivity.kt` |
| `navigation/NavGraph.kt` | route별 Composable 연결 | 탭 화면 연결, argument 전달 | 핵심 | 각 화면 `README.md` |
| `core/ui/AppSnackbarBus.kt` | root snackbar event bus | 전역 snackbar 노출 | 보조 | `MainActivity.kt` |
| `core/ui/ForceUpdateDialog.kt` | 강제 업데이트 dialog | 앱 업데이트 정책 UI | 보조 | `ForceUpdateChecker.kt` |
