---
type: package-reference
title: Transaction List files checklist
description: Transaction Detail List 수정 전후 확인할 파일과 검증 질문을 정리한다.
tags: [moneytalk, transaction-list, checklist]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionlist/
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Transaction List files checklist

## 빠른 파일 찾기

| 변경 유형 | 먼저 볼 파일 | 함께 볼 파일 |
|---|---|---|
| 진입 조건/extra | `TransactionDetailListActivity.kt` | `TransactionDetailListViewModel.kt`, History 날짜 클릭부 |
| 날짜 파싱/조회 | `TransactionDetailListViewModel.kt` | `DateUtils`, `ExpenseRepository`, `IncomeRepository` |
| 카드 숨김 반영 | `TransactionDetailListViewModel.kt` | `CardVisibilityFilter.kt`, `OwnedCardRepository` |
| 거래 카드 UI/action | `TransactionDetailListScreen.kt` | `TransactionEditActivity.kt`, transaction card 공통 컴포넌트 |
| refresh 정책 | `TransactionDetailListViewModel.kt` | `DataRefreshEvent`, 거래 수정/삭제 화면 |

## 검증 질문

1. `yyyy-MM-dd` date extra가 없거나 깨졌을 때 loading이 끝나고 empty처럼 보이는가?
2. 숨김 카드 지출이 목록에서 제외되는가?
3. 수입 카드 클릭 시 `incomeId`, 지출 카드 클릭 시 `expenseId`로 Transaction Edit에 들어가는가?
4. 거래 수정/삭제 후 돌아왔을 때 `DataRefreshEvent`로 목록이 갱신되는가?
5. 수입과 지출이 모두 있는 날짜에서 표시 순서가 의도와 맞는가?

## 권장 검증

- `.\gradlew.bat assembleDebug`
- History 달력 또는 날짜 영역에서 Transaction Detail List 진입
- 빈 날짜, 지출만 있는 날짜, 수입만 있는 날짜, 둘 다 있는 날짜 확인
- 카드 숨김 설정 후 지출 목록 반영 확인
- 각 카드에서 Transaction Edit 진입 확인
