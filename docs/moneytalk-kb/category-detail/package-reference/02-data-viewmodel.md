---
type: package-reference
title: Category Detail data and ViewModel
description: Category Detail ViewModel의 월 cache, 데이터 조회, 필터, refresh, 거래 변경 흐름을 설명한다.
tags: [moneytalk, category-detail, viewmodel, data]
resource: app/src/main/java/com/sanha/moneytalk/feature/categorydetail/ui/CategoryDetailViewModel.kt
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Category Detail data and ViewModel

## State

| 상태 | 의미 | 변경 영향 |
|---|---|---|
| `pageCache: Map<MonthKey, CategoryDetailPageData>` | 월별 상세 데이터 cache | 월 이동, refresh, 정렬 |
| `selectedYear`, `selectedMonth` | 현재 표시 월 | `loadCurrentAndAdjacentPages()` 호출 기준 |
| `monthStartDay` | 사용자 설정 월 시작일 | `SettingsDataStore.monthStartDayFlow` 변경 시 cache 전체 삭제 |
| `categoryDisplayName`, `categoryEmoji` | 화면 title/hero 표시 | custom category와 기본 category 표시 |
| `sortOrder` | `DATE_DESC` 또는 `AMOUNT_DESC` | cache 안의 거래 목록 재정렬 |

## 데이터 흐름

```text
SavedStateHandle extras
-> categoryNames 계산
-> SettingsDataStore.monthStartDayFlow
-> loadCurrentAndAdjacentPages()
-> loadPageData(year, month)
-> ExpenseRepository / BudgetDao / SmsExclusionRepository / OwnedCardRepository
-> CategoryDetailExpenseFilters
-> CategoryDetailPageData
-> CategoryDetailScreen
```

## 핵심 dependency

| dependency | 역할 | 변경 시 같이 볼 KB |
|---|---|---|
| `ExpenseRepository` | 카테고리별 지출 조회, 삭제, 메모 수정 | [../../finance-data/README.md](../../finance-data/README.md) |
| `SettingsDataStore` | 월 시작일, theme mode | [../../settings/README.md](../../settings/README.md) |
| `DataRefreshEvent` | 거래/카테고리/카드 변경 후 cache refresh | [../../data-refresh/README.md](../../data-refresh/README.md) |
| `SmsExclusionRepository` | 제외 키워드 로딩 | [../../sms-settings/README.md](../../sms-settings/README.md) |
| `OwnedCardRepository` | 숨김 카드 목록 로딩 | [../../filtering/README.md](../../filtering/README.md) |
| `BudgetDao` | 카테고리 예산 조회 | [../../budget-credit-monetization/README.md](../../budget-credit-monetization/README.md) |
| `CategoryClassifierService` | 동일 거래처 카테고리 일괄 수정 | [../../category-classification/README.md](../../category-classification/README.md) |
| `RewardAdManager` | banner ad 노출 flow | [../../budget-credit-monetization/README.md](../../budget-credit-monetization/README.md) |

## 캐시 정책

- `PAGE_CACHE_RANGE = 2`로 현재 월 기준 앞뒤 2개월 바깥 cache와 load job을 정리한다.
- `loadCurrentAndAdjacentPages()`는 현재 월, 이전 월, 미래가 아닌 다음 월을 로드한다.
- `loadPageData()`는 이미 로드된 월이면 재진입하지 않는다. 강제 갱신이 필요하면 cache를 지운 뒤 다시 로드해야 한다.
- `setSortOrder()`는 네트워크/DB 재조회 없이 cache 안의 `transactionItems`만 다시 빌드한다.

## 필터와 집계 기준

| 단계 | 적용 함수 | 목적 |
|---|---|---|
| 표시 목록 | `CategoryDetailExpenseFilters.filterDisplayExpenses(...)` | SMS 제외 키워드와 숨김 카드 제외 |
| 집계/차트 | `CategoryDetailExpenseFilters.filterStatsExpenses(...)` | 통계 제외 거래를 제거 |
| 날짜별 header 합계 | `buildDateGroupedItems()` 안의 `filterStatsExpenses(dayExpenses)` | 날짜 그룹 합계에서 통계 제외 반영 |

## Refresh 반응

| RefreshType | 반응 |
|---|---|
| `ALL_DATA_DELETED` | 전체 cache 삭제 |
| `CATEGORY_UPDATED`, `TRANSACTION_ADDED`, `OWNED_CARD_UPDATED` | 전체 cache 삭제 후 현재/인접 월 재로드 |
| `SMS_RECEIVED` | Home에서 동기화를 처리하므로 무시 |
| debug sync events | MainViewModel이 이후 `TRANSACTION_ADDED`를 발생시키는 전제라 여기서는 무시 |

## 거래 변경 action

| action | 구현 | 주의 |
|---|---|---|
| 삭제 | `deleteExpense(expense)` -> `DeletedSmsTracker.markDeleted` -> `expenseRepository.delete` | 원본 SMS 재동기화로 되살아나지 않게 tracker 처리 필요 |
| 카테고리 변경 | `updateExpenseCategory(storeName, newCategory)` | 동일 거래처 전체 변경이므로 단일 거래 수정이 아니다. |
| 메모 변경 | `updateExpenseMemo(expenseId, memo)` | blank는 null로 저장한다. |
