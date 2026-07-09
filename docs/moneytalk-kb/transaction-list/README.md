---
type: domain
title: Transaction List 도메인
description: 조건 기반 거래 상세 목록 화면의 entry, 데이터 로딩, 거래 수정 진입을 설명한다.
tags: [moneytalk, transaction-list, domain, compose]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionlist/
timestamp: 2026-07-09T01:55:05+09:00
status: draft
---

# Transaction List 도메인

> 상태: draft
> 기준: 2026-07-09 현재 `feature/transactionlist/ui/**` 및 실기기 진입 확인

Transaction List는 특정 조건의 거래 목록을 별도 Activity로 보여주는 보조 화면이다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| 이 문서 | 화면 entry, 핵심 파일, 수정 시 주의점을 정리한다. | 거래 목록 상세 화면 작업 |
| [package-reference/README.md](package-reference/README.md) | entry/data/rendering/checklist로 나눈 세부 개발 문서 인덱스다. | Transaction Detail List를 실제 수정하기 전 |
| [change-log.md](change-log.md) | KB 변경 로그다. | 문서 변경 이유 확인 |
| [../transaction-edit/README.md](../transaction-edit/README.md) | 거래 수정 화면 KB다. | 목록에서 거래 수정으로 이동할 때 |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `TransactionDetailListActivity.kt` | 목록 상세 Activity entry와 intent extra |
| `TransactionDetailListScreen.kt` | 거래 목록 렌더링과 empty/loading/error UI |
| `TransactionDetailListViewModel.kt` | 조건별 지출/수입 목록 로딩, 카드 숨김 필터 |

## 작업 판단

- 조건 추가는 Activity extra, ViewModel query, Screen 표시 문구를 같이 본다.
- 카드 숨김 필터는 `CardVisibilityFilter`와 [filtering/README.md](../filtering/README.md)를 같이 본다.
- 거래 카드 클릭은 지출/수입 모두 `TransactionEditActivity.open(...)`으로 이동한다.
  수입 카드는 `incomeId`, 지출 카드는 `expenseId`를 전달하므로 [transaction-edit/README.md](../transaction-edit/README.md)와 intent extra 계약을 같이 본다.
