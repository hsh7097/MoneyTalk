---
type: data-viewmodel
title: History Data ViewModel
description: HistoryViewModel의 state, repository, filter, page cache 흐름을 설명한다.
tags: [moneytalk, history, data, viewmodel]
resource: app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryViewModel.kt
timestamp: 2026-09-08T00:00:00+09:00
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

## 화면 책임 점검 (2026-09-08)

- MVVM + 기존 `HistoryIntent` 단방향 이벤트 처리를 유지한다. Repository 조회/월 캐시/refresh Job/거래 mutation은 `HistoryViewModel` 소유다.
- `HistoryUiState`, `HistoryIntent`, `TransactionListItem`을 각각 계약 파일로 분리했다. 기존 패키지와 타입 이름은 유지해 날짜 상세 등 소비자의 참조 계약을 보존한다.
- `HistoryTransactionListMapper`가 유형·고정 필터, 날짜/금액/사용처 정렬, 그룹 헤더 합계를 담당한다. DB/네트워크나 StateFlow 변경은 하지 않는다.
- 통계 제외 거래는 행에서 유지하고 합계에서 제외한다. 이체 입금은 지출 합계에서 빼고 수입 합계에 포함한다. 수입 전용 모드는 기존 최신순 날짜 그룹을 유지한다.
- 검증: `TransactionListMapperInstrumentedTest`의 혼합 유형/통계 제외/고정 필터/각 정렬/자정 경계, 기존 `TransactionDetailListFiltersTest`의 달력 상세 계약.
