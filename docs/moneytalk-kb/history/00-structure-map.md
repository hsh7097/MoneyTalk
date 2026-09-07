---
type: structure-map
title: History 구조 지도
description: History 도메인의 폴더, 핵심 파일, 역할, AI 참조 순서를 정리한다.
tags: [moneytalk, history, structure-map, compose]
resource: app/src/main/java/com/sanha/moneytalk/feature/history/ui/
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# History 구조 지도

## Folder Tree

```text
feature/history/ui/
├── HistoryScreen.kt
├── HistoryViewModel.kt
├── HistoryUiState.kt
├── HistoryIntent.kt
├── TransactionListItem.kt
├── HistoryTransactionListMapper.kt
├── HistoryCalendar.kt
├── HistoryDialogs.kt
├── HistoryFilter.kt
├── HistoryFilterSelection.kt
├── HistoryFilterControls.kt
├── HistoryFilterPickers.kt
└── HistoryHeader.kt
```

## Package Groups

| 파일 | 책임 |
|---|---|
| `HistoryScreen.kt` | 탭 화면 루트 Composable, pager/list/calendar/filter/dialog 조합, TransactionEditActivity 진입 |
| `HistoryViewModel.kt` | UI state, 월별 page cache, 필터/정렬, 거래 삭제/수정 intent 처리 |
| `HistoryCalendar.kt` | 달력 모드 렌더링 |
| `HistoryDialogs.kt` | 거래 상세/수정/삭제 관련 다이얼로그 |
| `HistoryFilter.kt` | 미적용 선택 상태와 필터 시트/코치마크 조합 |
| `HistoryFilterSelection.kt` | 불변 선택 값, 유형 전환, 카테고리 자동 축소 규칙 |
| `HistoryFilterControls.kt` | 유형 타일, 필터 옵션, 카테고리 칩/요약, 안내 카드 |
| `HistoryFilterPickers.kt` | 카드/카테고리 전체 목록 선택 시트 |
| `HistoryUiState.kt`, `HistoryIntent.kt`, `TransactionListItem.kt` | 화면/월 상태, 사용자 액션, 목록 계약 |
| `HistoryTransactionListMapper.kt` | 표시 필터, 정렬, 날짜/사용처 그룹 및 통계 합계 |
| `HistoryHeader.kt` | 기간 요약 헤더 |

## Core Files And Roles

| 작업 영역 | 먼저 볼 파일 | 같이 볼 파일 |
|---|---|---|
| 진입 | `navigation/NavGraph.kt`, `HistoryScreen.kt` | `Screen.kt`, `MainActivity.kt` |
| 데이터/state | `HistoryViewModel.kt` | `ExpenseRepository.kt`, `IncomeRepository.kt`, `OwnedCardRepository.kt` |
| 렌더링 | `HistoryScreen.kt` | `HistoryCalendar.kt`, `HistoryHeader.kt`, 공통 transaction component |
| 필터 | `HistoryFilter.kt`, `HistoryViewModel.kt` | `HistoryScreen.kt` |
| 다이얼로그/action | `HistoryDialogs.kt`, `HistoryViewModel.kt` | `TransactionEditActivity.kt`, `DeletedSmsTracker.kt` |

## AI Reference Order

| 작업 유형 | 참조 순서 |
|---|---|
| route/filterCategory 변경 | `README.md` -> `package-reference/01-entry-screen.md` -> `HistoryScreen.kt` |
| 필터/정렬/state 변경 | `README.md` -> `package-reference/02-data-viewmodel.md` -> `HistoryViewModel.kt` |
| 목록/달력 UI 변경 | `README.md` -> `package-reference/03-rendering-action.md` -> `HistoryScreen.kt` |
| 다이얼로그/삭제/수정 변경 | `README.md` -> `package-reference/03-rendering-action.md` -> `HistoryDialogs.kt` -> `HistoryViewModel.kt` |
| 영향 파일 확인 | `05-file-inventory.md` -> `package-reference/04-files-checklist.md` |
