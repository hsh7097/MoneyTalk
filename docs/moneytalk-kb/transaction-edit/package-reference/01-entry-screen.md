---
type: package-reference
title: Transaction Edit entry/screen
description: TransactionEditActivity 진입과 신규/기존 거래 화면 초기화 흐름을 설명한다.
tags: [moneytalk, transaction-edit, entry]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditActivity.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Transaction Edit entry/screen

## 진입

Transaction Edit는 하단 탭 route가 아니라 Activity로 열린다.
Home, History, CategoryDetail, TransactionList 같은 화면에서 거래 id/type 또는 신규 생성 intent를 전달한다.

```text
caller screen
-> TransactionEditActivity.open(...)
-> TransactionEditScreen
-> TransactionEditViewModel load/init
```

## 확인 포인트

- 지출과 수입은 로딩/저장 경로가 다르므로 intent extra와 `TransactionType`을 같이 확인한다.
- 신규 지출 추가와 기존 거래 수정은 초기 state 기본값이 다르다.
- Activity 결과를 caller에 직접 반환하기보다 repository 저장 후 `DataRefreshEvent`로 관련 화면을 갱신한다.
