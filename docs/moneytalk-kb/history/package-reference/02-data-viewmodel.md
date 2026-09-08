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
- 거래 삭제/메모/카테고리 변경: 기존 `HistoryIntent`/상세 편집 경로와 공용 롱클릭 삭제 서비스의 Repository 호출, `DataRefreshEvent` 영향을 함께 확인한다.
- 카드 표시/숨김: `OwnedCardRepository`, `CardVisibilityFilter`, selected card state를 함께 확인한다.

## 화면 책임 점검 (2026-09-08)

- MVVM + 기존 `HistoryIntent` 단방향 이벤트 처리를 유지한다. Repository 조회/월 캐시/refresh Job과 기존 mutation은 `HistoryViewModel` 소유다. 롱클릭 메뉴 상태와 삭제는 `TransactionQuickActionViewModel`과 `TransactionQuickActionService`가 담당하고, 수정 입력/저장은 기존 상세 편집 화면이 담당한다.
- `HistoryUiState`, `HistoryIntent`, `TransactionListItem`을 각각 계약 파일로 분리했다. 기존 패키지와 타입 이름은 유지해 날짜 상세 등 소비자의 참조 계약을 보존한다.
- `HistoryTransactionListMapper`가 유형·고정 필터, 날짜/금액/사용처 정렬, 그룹 헤더 합계를 담당한다. DB/네트워크나 StateFlow 변경은 하지 않는다.
- 통계 제외 거래는 행에서 유지하고 합계에서 제외한다. 이체 입금은 지출 합계에서 빼고 수입 합계에 포함한다. 수입 전용 모드는 기존 최신순 날짜 그룹을 유지한다.
- 검증: `TransactionListMapperInstrumentedTest`의 혼합 유형/통계 제외/고정 필터/각 정렬/자정 경계, 기존 `TransactionDetailListFiltersTest`의 달력 상세 계약.

## 지출/수입 검색

- `searchTransactions(query)`는 지출 검색과 `IncomeRepository.searchIncomes()`를 함께 실행한다. 수입 검색은 설명·유형·출처·카테고리·메모를 대상으로 하는 `IncomeDao.searchIncomes()`를 사용한다.
- 검색은 선택 월 제한 없이 조회하고 결과를 현재 월의 `HistoryPageData`에 담는다. `incomes`, 수입 합계/일별 합계와 통합 목록을 같이 갱신한다.
- 수입에는 SMS 제외 키워드, 선택 수입 카테고리, 고정/변동 필터를 적용한다. 수입 숨김 또는 카드 필터 선택 시 수입 결과를 비운다. 지출 검색의 기존 카드/고정 필터 동작과 구분해 회귀 확인한다.
- 직전 검색 Job과 해당 페이지 로딩 Job을 취소하고 새 검색 Job을 페이지 Job으로도 등록한다. 결과 쓰기 전 취소를 확인하며 `CancellationException`을 일반 오류로 처리하지 않는다. 빈 검색어/검색 종료/화면 재조회에서 이전 검색이 월 목록을 덮어쓰지 않아야 한다.
- 합계 명칭의 기존 경계: `TRANSFER`의 `DEPOSIT` 행을 수입으로 합치는 혼합 목록과 `IncomeEntity`만 집계하는 월/일별 수입이 다를 수 있다. 이 정책은 이번 검색 수정에서 임의로 통일하지 않았다. [제품 검토의 합계 정의 후속](../../project-context/04-product-improvements-20260908.md)을 본다.
