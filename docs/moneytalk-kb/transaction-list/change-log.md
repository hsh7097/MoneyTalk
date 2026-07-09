---
type: changelog
title: Transaction List KB 변경 로그
description: Transaction List KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, transaction-list, changelog]
resource: docs/moneytalk-kb/transaction-list/
timestamp: 2026-07-09T01:55:05+09:00
status: draft
---

# Transaction List KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-07-09 | `TransactionDetailListActivity`, `TransactionDetailListViewModel`, `TransactionDetailListScreen` 재확인 | Transaction List package-reference 추가 | `package-reference/**`, `README.md` | 날짜 extra, 카드 숨김 필터, refresh, 지출/수입 편집 진입을 세부 문서로 분리. |
| 2026-07-09 | 실기기 `TransactionDetailListActivity` 진입 후 수입 카드 클릭 확인 | 수입/지출 카드 모두 `TransactionEditActivity`로 이동하는 진입 계약 보강 | `README.md` | 기존 요구사항 문서의 수입 편집 미지원 표현과 달리 현재 코드는 `incomeId` 진입을 지원. |
| 2026-07-08 | `feature/transactionlist/ui/**` 확인 | Transaction List 화면 KB 생성 | `README.md` | 조건 기반 거래 목록 상세 화면의 entry/data/edit 진입을 기록. |
