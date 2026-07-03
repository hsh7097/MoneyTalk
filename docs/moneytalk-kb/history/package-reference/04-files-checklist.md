---
type: checklist
title: History Files Checklist
description: History 수정 전후에 확인할 파일과 검증 질문을 정리한다.
tags: [moneytalk, history, checklist]
resource: app/src/main/java/com/sanha/moneytalk/feature/history/ui/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# 04 Files Checklist

## 빠른 파일 찾기

| 작업 | 확인 파일 |
|---|---|
| 화면 진입 변경 | `navigation/NavGraph.kt`, `HistoryScreen.kt` |
| 필터/정렬 변경 | `HistoryViewModel.kt`, `HistoryFilter.kt` |
| 목록 렌더링 변경 | `HistoryScreen.kt`, 공통 transaction component |
| 달력 변경 | `HistoryCalendar.kt`, `HistoryViewModel.kt` |
| 상세/삭제/메모 변경 | `HistoryDialogs.kt`, `HistoryViewModel.kt` |
| 거래 수정 진입 | `TransactionEditActivity.kt`, `TransactionEditViewModel.kt` |

## 수정 전 질문

- 이 변경이 route, state, rendering, action 중 어디에 속하는가?
- `MainViewModel`의 Activity-scoped sync state와 관련 있는가?
- 지출/수입/이체성 수입을 모두 고려해야 하는가?
- 카드 숨김, 통계 제외, 고정 거래 필터에 영향이 있는가?

## 검증

- Compose 컴파일 대상 task를 실행한다.
- 필터/정렬/달력/목록 모드가 서로 같은 state를 보는지 확인한다.
- KB를 수정했다면 `history/change-log.md`를 갱신한다.
