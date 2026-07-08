---
type: feature-flow
title: Filtering 기능 흐름
description: 화면 필터, 카드 숨김, 통계 제외, SMS 제외 설정이 데이터 노출과 파싱에 반영되는 흐름을 설명한다.
tags: [moneytalk, filtering, feature-flow]
resource: app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryFilter.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Filtering 기능 흐름

## 내역 화면 필터

```text
HistoryScreen
-> FilterBottomSheet
-> HistoryViewModel filter state
-> ExpenseRepository/IncomeRepository query or in-memory filter
-> TransactionListView render
```

## 카드 숨김

```text
Settings card ownership/exclusion
-> OwnedCardRepository / SettingsDataStore
-> CardVisibilityFilter
-> Home/History/CategoryDetail/TransactionList 화면 노출과 집계
```

## 통계 제외

```text
SMS parsing or user edit
-> ExpenseEntity.isExcludedFromStats
-> Home/History/Chat/CategoryDetail 집계에서 제외
```

## SMS 제외

```text
SmsSettingsScreen
-> SmsExclusionRepository / SmsBlockedSenderRepository
-> SmsFilter / SmsPreFilter
-> 신규 sync 또는 실시간 수신 필터링
```

## 검증 질문

1. 원본 거래 삭제가 아니라 노출/집계 제외인지 구분했는가?
2. 제외 카드 변경 후 Home/History/CategoryDetail이 refresh되는가?
3. 통계 제외 거래가 목록에는 보이되 합계에서 빠지는지 확인했는가?
4. SMS 제외 설정은 신규 파싱 입력에만 영향을 주는지, 기존 데이터 표시에도 영향을 주는지 명확한가?
