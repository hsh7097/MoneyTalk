---
type: package-reference
title: Transaction List data and ViewModel
description: Transaction Detail List ViewModel의 날짜 파싱, 지출/수입 조회, 카드 숨김, refresh 흐름을 설명한다.
tags: [moneytalk, transaction-list, viewmodel, data]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionlist/ui/TransactionDetailListViewModel.kt
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# Transaction List data and ViewModel

## State

| 상태 | 의미 |
|---|---|
| `isLoading` | 날짜 파싱/조회 중 표시 상태 |
| `dateString` | Activity extra로 받은 원본 날짜 문자열 |
| `monthStr`, `dayNum` | title 표시용 월/일 |
| `items` | 필터 및 정렬이 적용된 `TransactionDetailListItem.Expense/Income` 목록 |

## 데이터 흐름

```text
SavedStateHandle["extra_date"] + TransactionDetailFilter.fromSavedStateHandle()
-> parseDateAndLoad()
-> yyyy-MM-dd 파싱
-> 하루 start/end time 계산
-> OwnedCardRepository.getExcludedCardNames()
-> 달력 필터 진입이면 SmsExclusionRepository.getAllKeywordStrings()
-> ExpenseRepository.getExpensesByDateRangeOnce()
-> IncomeRepository.getIncomesByDateRangeOnce()
-> TransactionDetailListFilters.buildItems()
-> TransactionDetailListUiState
```

## 핵심 dependency

| dependency | 역할 | 변경 시 같이 볼 KB |
|---|---|---|
| `ExpenseRepository` | 하루 지출 조회 | [../../finance-data/README.md](../../finance-data/README.md) |
| `IncomeRepository` | 하루 수입 조회 | [../../finance-data/README.md](../../finance-data/README.md) |
| `OwnedCardRepository` | 숨김 카드 목록 | [../../filtering/README.md](../../filtering/README.md) |
| `SmsExclusionRepository` | 달력과 같은 SMS 제외 키워드 조회 | [../../filtering/README.md](../../filtering/README.md) |
| `CardVisibilityFilter` | 지출 목록에서 숨김 카드 제외 | [../../filtering/README.md](../../filtering/README.md) |
| `DataRefreshEvent` | 다른 화면의 거래 변경 후 재조회 | [../../data-refresh/README.md](../../data-refresh/README.md) |

## refresh 정책

- `observeRefreshEvents()`는 refresh type을 구분하지 않고 이벤트가 오면 `loadTransactions()`를 다시 호출한다.
- 거래 편집, 삭제, 전체 삭제, 카드 숨김 변경이 모두 같은 reload 경로를 탄다.
- 재조회할 때 전달받은 유형/카테고리/카드/고정/정렬 필터를 유지하고 현재 SMS 제외 키워드 및 숨김 카드 설정을 다시 읽는다.
- 조건이 늘어나면 refresh type을 구분해야 하는지 먼저 결정한다.

## 주의점

1. 지출은 숨김 카드 필터를 적용하지만 수입은 카드 숨김 필터 대상이 아니다.
2. 날짜 파싱 실패 시 `isLoading = false`만 처리하고 목록은 비어 있다.
3. 하루 범위는 local `Calendar` 기준 00:00:00.000부터 23:59:59.999까지다.
4. 달력 필터 진입은 지출/수입/이체 유형, 정확한 카테고리 집합, 선택 카드, 고정 거래 조건을 적용한다. 카드 선택 시 수입은 History와 동일하게 숨긴다.
5. 통계 제외 거래는 표시 목록에서 제거하지 않는다. 달력 합계에서만 빠지는 기존 정책을 유지한다.
6. 필터 없는 기존 날짜 단독 진입은 SMS 제외 키워드를 추가 적용하지 않고 수입 우선·각 유형 최신순을 유지한다. 달력 진입은 SMS 제외를 적용하고 최신순/금액순으로 유형을 통합하거나 사용처·수입 출처별로 정렬한다.
7. 수입만 보기 조건은 History처럼 정렬 선택과 무관하게 최신순을 유지한다.

## 화면 책임 점검 (2026-09-08)

- `TransactionDetailListActivity`는 진입 값, `TransactionDetailFilter`는 전달 계약, `TransactionDetailListFilters`는 표시/정렬, ViewModel은 날짜 조회와 refresh, Screen은 로딩/빈 상태/목록과 편집 진입을 이미 분리한다.
- 단일 날짜 보조 화면에 별도 MVI reducer나 Repository 추상화를 추가하지 않는다. 기존 `History` 정렬/고정 필터 타입은 계약 파일로 이동했지만 패키지와 값은 동일하다.
- 검증: `TransactionDetailListFiltersTest`의 선택 카드, 정확한 카테고리, 유형/고정 필터, 통계 제외 표시, 혼합 정렬. 날짜 클릭 후 수정/뒤로가기와 필터 유지도 기기 확인 대상이다.
