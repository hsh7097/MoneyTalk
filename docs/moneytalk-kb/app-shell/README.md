---
type: domain
title: App Shell 도메인
description: MoneyTalk 앱 진입, 하단 탭 4개, NavGraph, Activity 전역 상태와 다이얼로그를 설명한다.
tags: [moneytalk, app-shell, navigation, bottom-tab, compose]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# App Shell 도메인

> 상태: draft
> 기준: 2026-07-08 현재 `MainActivity.kt`, `MainViewModel.kt`, `navigation/**`, `MainUiState.kt` 확인

App Shell은 앱이 실행된 뒤 Compose root, 하단 탭 4개, `NavGraph`, 전역 snackbar, SMS 동기화/크레딧 충전/업데이트 다이얼로그를 묶는 진입 도메인이다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-structure-map.md](00-structure-map.md) | App Shell 구조, 하단 탭, 전역 상태 흐름을 정리한다. | 앱 진입, 탭, NavGraph, 전역 다이얼로그 작업 |
| [05-file-inventory.md](05-file-inventory.md) | 관련 파일 역할 인덱스다. | 수정 후보가 `MainActivity`, `MainViewModel`, `navigation` 중 어디인지 애매할 때 |
| [change-log.md](change-log.md) | App Shell KB 변경 로그다. | 문서 변경 이유 확인 |

## 기준 파일

| 파일 | 책임 |
|---|---|
| `MainActivity.kt` | Android Activity, 권한 요청, root Compose, `MoneyTalkApp`, 하단 탭 UI, 전역 다이얼로그 |
| `MainViewModel.kt` | Activity-scoped sync, SMS 권한/동기화, 광고 unlock, refresh event 발행 |
| `MainUiState.kt` | Main 화면/다이얼로그 상태 model |
| `navigation/Screen.kt` | Home/History/Chat/Settings route 정의 |
| `navigation/BottomNavItem.kt` | 하단 탭 4개와 icon/title resource |
| `navigation/NavGraph.kt` | 하단 탭 route를 실제 screen Composable로 연결 |

## 하단 탭 4개

| 탭 | route | 화면 |
|---|---|---|
| 홈 | `home` | `HomeScreen` |
| 내역 | `history?category={category}` | `HistoryScreen` |
| 채팅 | `chat` | `ChatScreen` |
| 설정 | `settings` | `SettingsScreen` |

## 작업 판단

- 하단 탭 추가/삭제/라벨/아이콘 변경은 `BottomNavItem.kt`, `Screen.kt`, `NavGraph.kt`, `MainActivity.kt`를 같이 본다.
- 탭 재클릭 refresh는 `MainActivity.kt`의 `homeTabReClickEvent`, `historyTabReClickEvent`와 각 화면 ViewModel 수집부를 같이 본다.
- SMS 동기화/이전 월 문자 가져오기 크레딧 충전/성과 요약 다이얼로그는 `MainActivity.kt`, `MainViewModel.kt`, `MainUiState.kt`, `RewardAdManager`를 같이 본다.
- 화면별 세부 UI 수정은 이 문서에서 해당 화면 KB로 내려간다.
