---
type: package-reference
title: Transaction List package-reference
description: Transaction Detail List 화면 수정 위치를 entry, data, rendering, checklist로 나눈 세부 개발 문서 인덱스다.
tags: [moneytalk, transaction-list, package-reference]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionlist/
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Transaction List package-reference

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [01-entry-screen.md](01-entry-screen.md) | 날짜별 거래 상세 목록의 Activity extra와 진입 계약 | 날짜 조건, route, Activity open 변경 |
| [02-data-viewmodel.md](02-data-viewmodel.md) | `TransactionDetailListViewModel`의 날짜 파싱, 지출/수입 조회, refresh 흐름 | 조회 조건, 필터, refresh 변경 |
| [03-rendering-action.md](03-rendering-action.md) | 목록 화면의 top app bar, empty/loading, 거래 카드, 편집 진입 | Composable 또는 거래 카드 action 변경 |
| [04-files-checklist.md](04-files-checklist.md) | 수정 전후 확인 파일과 검증 질문 | 리뷰 전 누락 점검 |
