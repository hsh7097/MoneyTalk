---
type: changelog
title: Transaction List KB 변경 로그
description: Transaction List KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, transaction-list, changelog]
resource: docs/moneytalk-kb/transaction-list/
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# Transaction List KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-09-07 | 실기기 1.0.3에서 우리 카드 달력 합계 선택 후 타 카드까지 날짜 상세에 표시되는 현상 재현 | 날짜 상세에 내역 필터와 정렬 전달, 표시 정책 및 기존 날짜 단독 intent 호환 유지 | `README.md`, `package-reference/**`, History/Filtering KB | primitive extra 복원과 필터/정렬 JVM 테스트 추가. 통합 빌드 및 실기기 재검증은 별도 수행. |
| 2026-07-09 | `TransactionDetailListActivity`, `TransactionDetailListViewModel`, `TransactionDetailListScreen` 재확인 | Transaction List package-reference 추가 | `package-reference/**`, `README.md` | 날짜 extra, 카드 숨김 필터, refresh, 지출/수입 편집 진입을 세부 문서로 분리. |
| 2026-07-09 | 실기기 `TransactionDetailListActivity` 진입 후 수입 카드 클릭 확인 | 수입/지출 카드 모두 `TransactionEditActivity`로 이동하는 진입 계약 보강 | `README.md` | 기존 요구사항 문서의 수입 편집 미지원 표현과 달리 현재 코드는 `incomeId` 진입을 지원. |
| 2026-07-08 | `feature/transactionlist/ui/**` 확인 | Transaction List 화면 KB 생성 | `README.md` | 조건 기반 거래 목록 상세 화면의 entry/data/edit 진입을 기록. |

## 2026-09-08 - 기능 경계 감사

- Activity extra, 필터 계약, 조회 ViewModel, 표시/정렬 helper, Screen을 점검했다. 현재 기능별 분리가 적절해 실행 코드를 변경하지 않았다.
- History의 상태 계약 파일 분리 후에도 날짜 상세의 정렬/고정 필터 참조와 테스트를 유지한다.
