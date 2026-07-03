---
type: data-viewmodel
title: History Data ViewModel
description: HistoryViewModel의 state, repository, filter, page cache 흐름을 설명한다.
tags: [moneytalk, history, data, viewmodel]
resource: app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryViewModel.kt
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# 02 Data ViewModel

## 핵심 파일

- `HistoryViewModel.kt`
- `feature/home/data/ExpenseRepository.kt`
- `feature/home/data/IncomeRepository.kt`
- `core/database/OwnedCardRepository.kt`
- `core/datastore/SettingsDataStore.kt`
- `core/util/DataRefreshEvent.kt`

## State 흐름

`HistoryUiState`는 월별 `pageCache`, 선택 필터, 선택 연/월, 검색어, 정렬, 지출/수입 표시 여부를 가진다.
각 월별 데이터는 `HistoryPageData`로 분리되어 `HorizontalPager` 페이지 단위 렌더링에 사용된다.

```text
Repository/Settings/DataRefreshEvent
→ HistoryViewModel
→ HistoryUiState(pageCache, filters, selected month)
→ HistoryScreen
→ HistoryCalendar / LazyColumn / HistoryFilter / HistoryDialogs
```

## 거래 목록 모델

`TransactionListItem`은 LazyColumn에 렌더링할 플랫 리스트 모델이다.

- `Header`: 그룹 헤더
- `ExpenseItem`: 지출 거래 카드 정보
- `IncomeItem`: 수입 거래 카드 정보

## 수정 시 확인

- 필터 조건 추가: `HistoryUiState`, `HistoryFilter.kt`, `applyFilter` 계열 함수, page reload 로직을 함께 확인한다.
- 거래 삭제/메모/카테고리 변경: `HistoryIntent`, Repository 호출, `DataRefreshEvent` 영향 여부를 함께 확인한다.
- 카드 표시/숨김: `OwnedCardRepository`, `CardVisibilityFilter`, selected card state를 함께 확인한다.
