---
type: log
title: History KB Change Log
description: History 도메인 KB 변경 상세 이력을 기록한다.
tags: [moneytalk, history, changelog]
resource: docs/moneytalk-kb/history/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# History Change Log

## 2026-09-07 - 달력 날짜 상세 필터 유지

- 근거: 실기기 1.0.3에서 선택 카드의 달력 합계와 날짜 상세 거래 범위가 다른 현상 재현.
- 변경: 달력은 날짜 callback을 전달하고 HistoryScreen이 현재 필터를 날짜 상세 Activity로 함께 전달한다. 원본 거래는 변경하지 않는다.
- 관련: `package-reference/03-rendering-action.md`, `../transaction-list/**`, `../filtering/02-filter-surfaces.md`
- 검증: `TransactionDetailListFiltersTest` 추가. 통합 빌드 및 실기기에서 선택 카드/카테고리/유형/고정 필터 후 날짜 진입 재확인이 필요하다.

## 2026-07-03

- 기준: 현재 `feature/history/ui` 소스 확인
- 변경 근거: MoneyTalk KB 초기 생성
- 갱신한 KB:
  - `README.md`
  - `00-structure-map.md`
  - `05-file-inventory.md`
  - `package-reference/*`
- 다음 검증:
  - 신규 필터 조건 추가 작업에서 `HistoryFilter.kt`와 `HistoryViewModel.kt`를 정확히 찾는지 확인
