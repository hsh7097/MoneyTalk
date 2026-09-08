---
type: domain
title: App Shell 도메인
description: MoneyTalk 앱 진입, 하단 탭 4개, NavGraph, Activity 전역 상태와 다이얼로그를 설명한다.
tags: [moneytalk, app-shell, navigation, bottom-tab, compose]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# App Shell 도메인

> 상태: draft
> 기준: 2026-09-08 `MoneyTalkApp.kt`의 금융 UI 하단바와 기존 `MainActivity.kt`, `MainViewModel.kt`, `navigation/**`, `MainUiState.kt` 계약 대조

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
| `MainActivity.kt` | Android Activity, 권한 요청, root Compose와 `MoneyTalkApp` 진입 |
| `MoneyTalkApp.kt` | Scaffold, 하단 탭 UI, NavGraph, 전역 스낵바/다이얼로그, 뒤로가기 |
| `MainViewModel.kt` | Activity-scoped sync, SMS 권한/동기화, 광고 unlock, refresh event 발행 |
| `MainUiState.kt` | Main 화면/다이얼로그 상태 model |
| `navigation/Screen.kt` | Home/History/Chat/Settings route 정의 |
| `navigation/BottomNavItem.kt` | 하단 탭 4개와 icon/title resource |
| `navigation/NavGraph.kt` | 하단 탭 route를 실제 screen Composable로 연결 |

## 하단 탭 4개

| 탭 | route | 화면 |
|---|---|---|
| 홈 | `home` | `HomeScreen` |
| 가계부 | `history?category={category}` | `HistoryScreen` |
| 인사이트 | `chat` | `ChatScreen` |
| 설정 | `settings` | `SettingsScreen` |

## 작업 판단

- 하단 탭 추가/삭제/라벨/아이콘 변경은 `BottomNavItem.kt`, `Screen.kt`, `NavGraph.kt`, `MoneyTalkApp.kt`, `MainActivity.kt`를 같이 본다.
- 하단바는 중립 표면의 `NavigationBar`와 표준 `NavigationBarItem.icon/label` 슬롯을 사용한다. 아이콘은 24dp이며 선택 시 Filled, 미선택 시 Outlined다. 선택 색/표시는 `primary`/`primaryContainer`, 비선택은 `onSurfaceVariant`를 따른다. 아이콘의 null 설명과 별도 label로 탭 이름 중복 낭독을 피한다.
- `MoneyTalkApp`은 IME가 보이면 하단바를 숨기고 닫히면 복구한다. 기존 saveState/restoreState, 홈·가계부 재클릭, 스낵바와 전역 동기화 상태를 보존한다. 시각 규칙은 [금융 UI 기준](../project-context/05-finance-ui-design-system-20260908.md)을 본다.
- 탭 재클릭 refresh는 `MoneyTalkApp.kt`에서 발행하는 `MainViewModel.homeTabReClickEvent`, `historyTabReClickEvent`와 각 화면의 수집부를 같이 본다.
- SMS 동기화/이전 월 문자 가져오기 크레딧 충전/성과 요약 다이얼로그는 `MainActivity.kt`, `MainViewModel.kt`, `MainUiState.kt`, `RewardAdManager`를 같이 본다.
- 이전 월 보상형 광고는 SMS 권한을 먼저 확인한다. 권한이 거부되면 광고와 sync를 시작하지 않고 다이얼로그를 유지한다. 광고를 실제로 띄울 때만 `MoneyTalkApp`이 다이얼로그 UI를 숨기고 `MainViewModel`의 pending sync epoch를 보존하며, 명시적 취소·광고 실패만 요청을 제거하고 보상 성공은 같은 epoch로 월별 sync를 계속한다.
- 화면별 세부 UI 수정은 이 문서에서 해당 화면 KB로 내려간다.
