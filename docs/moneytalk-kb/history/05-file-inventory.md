---
type: file-inventory
title: History File Inventory
description: History 도메인 파일별 역할과 참조 시점을 인덱싱한다.
tags: [moneytalk, history, file-inventory]
resource: app/src/main/java/com/sanha/moneytalk/feature/history/ui/
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# History File Inventory

| 파일 | 역할 | 언제 보는가 | 분류 | 함께 볼 파일 |
|---|---|---|---|---|
| `HistoryScreen.kt` | History 탭 루트 Composable, pager/list/calendar/filter/dialog 조합 | 화면 구조, 렌더링, 탭 재클릭, 거래 수정 진입 | 핵심 | `HistoryViewModel.kt`, `HistoryFilter.kt`, `HistoryDialogs.kt` |
| `HistoryViewModel.kt` | History UI state, page cache, 필터, 정렬, 거래 action 처리 | 데이터 로딩, 필터/정렬, 삭제/메모/카테고리 변경 | 핵심 | `ExpenseRepository.kt`, `IncomeRepository.kt`, `DeletedSmsTracker.kt` |
| `HistoryCalendar.kt` | 달력 모드 컴포넌트 | 월별 달력 UI, 일별 합계 표시 | 핵심 후보 | `HistoryViewModel.kt`, `HistoryScreen.kt` |
| `HistoryDialogs.kt` | 거래 상세/수정/삭제 다이얼로그 | 상세 보기, 삭제 확인, 메모/카테고리 변경 | 핵심 후보 | `HistoryViewModel.kt`, `TransactionEditActivity.kt` |
| `HistoryFilter.kt` | 필터 BottomSheet와 초기화 UI | 카테고리/카드/수입/지출/고정 거래 필터 | 핵심 | `HistoryViewModel.kt` |
| `HistoryHeader.kt` | 기간 요약 헤더 | 월/기간 요약, header 표시 문제 | 보조 | `HistoryScreen.kt` |
| `HistoryUiState.kt` | 화면/월 상태, 정렬/고정 필터 계약 | 상태 필드 변경 | 핵심 | `HistoryViewModel.kt` |
| `HistoryIntent.kt` | 거래 선택/수정/삭제 사용자 액션 | 사용자 이벤트 변경 | 핵심 | `HistoryViewModel.kt` |
| `TransactionListItem.kt` | 헤더/지출/수입 렌더링 계약 | 거래 행 표시 | 핵심 | `HistoryTransactionListMapper.kt` |
| `HistoryTransactionListMapper.kt` | 정렬·유형·고정 필터·그룹 합계 | 목록 순서/합계 | 핵심 | `HistoryViewModel.kt`, `TransactionListItem.kt` |
| `HistoryFilterSelection.kt` | 미적용 필터의 불변 값/전환 규칙 | 초기화/유형/카테고리 선택 | 핵심 | `HistoryFilter.kt` |
| `HistoryFilterControls.kt` | 필터용 타일/칩/요약/안내 렌더링 | 필터 시각 요소 | 보조 | `HistoryFilter.kt` |
| `HistoryFilterPickers.kt` | 전체 카드/카테고리 목록 선택 | 상세 선택 시트 | 보조 | `HistoryFilter.kt` |
