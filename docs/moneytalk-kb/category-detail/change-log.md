---
type: changelog
title: Category Detail KB 변경 로그
description: Category Detail KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, category-detail, changelog]
resource: docs/moneytalk-kb/category-detail/
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# Category Detail KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-07-09 | `CategoryDetailActivity`, `CategoryDetailViewModel`, `CategoryDetailScreen`, `CategoryDetailExpenseFilters` 재확인 | Category Detail package-reference 추가 | `package-reference/**`, `README.md` | 월 cache, intent extra, 필터/집계, 정렬, 거래 mutation을 세부 문서로 분리. |
| 2026-07-08 | `feature/categorydetail/ui/**` 확인 | Category Detail 화면 KB 생성 | `README.md`, `00-structure-map.md` | 홈 카테고리 클릭 후 상세 화면의 월 이동, 정렬, 필터, 거래 수정 진입을 분리. |

## 2026-09-08 - 목록 표시 변환 분리

- 날짜 그룹/금액순/통계 합계 변환을 `CategoryTransactionListMapper`로 옮겼다.
- Activity/ViewModel/필터/차트 mapper의 기존 MVVM 경계를 점검하고 유지했다. 새 Android 목록 테스트로 지출/통계 제외/이체 입금의 표시·합계 계약을 확인한다.
