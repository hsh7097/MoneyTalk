---
type: domain
title: Transaction Edit 도메인
description: 거래 추가/수정 화면의 지출/수입 입력, 카테고리 picker, 일괄 적용, 삭제, 코치마크 흐름을 설명한다.
tags: [moneytalk, transaction-edit, domain, compose]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionedit/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Transaction Edit 도메인

> 상태: draft
> 기준: 2026-07-08 현재 `feature/transactionedit/ui/**`, `ExpenseRepository`, `IncomeRepository`, `CustomCategoryRepository` 확인

Transaction Edit는 지출/수입을 새로 추가하거나 기존 아이템을 수정/삭제하는 화면이다.
사용자가 말한 “아이템 변경” 작업은 대부분 이 도메인과 `transaction-mutation` 기능 KB를 함께 본다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-structure-map.md](00-structure-map.md) | Transaction Edit 구조와 핵심 파일을 정리한다. | 수정 파일 책임 판단 |
| [package-reference/README.md](package-reference/README.md) | entry/data/rendering/checklist 문서 인덱스다. | 실제 수정 위치를 더 좁힐 때 |
| [package-reference/01-entry-screen.md](package-reference/01-entry-screen.md) | Activity intent, 신규/기존 거래 진입을 설명한다. | 화면 진입/intent extra 문제 |
| [package-reference/02-data-viewmodel.md](package-reference/02-data-viewmodel.md) | ViewModel state, 저장/삭제/일괄 적용 흐름을 설명한다. | 거래 저장, 카테고리/고정/통계 제외 변경 |
| [package-reference/03-rendering-action.md](package-reference/03-rendering-action.md) | 화면 카드, picker, dialog, action 연결을 설명한다. | Compose UI와 사용자 액션 |
| [package-reference/04-files-checklist.md](package-reference/04-files-checklist.md) | 수정 전후 확인 파일과 검증 질문이다. | 리뷰 전 누락 점검 |
| [05-file-inventory.md](05-file-inventory.md) | 파일 역할 인덱스다. | 파일 후보가 애매할 때 |
| [change-log.md](change-log.md) | Transaction Edit KB 변경 로그다. | 문서 변경 이유 확인 |

## 핵심 흐름

```text
TransactionEditActivity
-> TransactionEditScreen
-> TransactionEditViewModel
-> ExpenseRepository / IncomeRepository / CustomCategoryRepository
-> DataRefreshEvent
```

## 관련 기능 KB

- [transaction-mutation/README.md](../transaction-mutation/README.md): 거래 추가/수정/삭제와 일괄 적용 기능 흐름
- [category-classification/README.md](../category-classification/README.md): 카테고리 수정 학습
- [coachmark/README.md](../coachmark/README.md): Transaction Edit 코치마크
