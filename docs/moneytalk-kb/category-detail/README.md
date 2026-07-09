---
type: domain
title: Category Detail 도메인
description: 홈 카테고리 클릭 후 열리는 카테고리별 상세 화면의 월 이동, 정렬, 거래 목록, 수정 진입을 설명한다.
tags: [moneytalk, category-detail, domain, compose]
resource: app/src/main/java/com/sanha/moneytalk/feature/categorydetail/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Category Detail 도메인

> 상태: draft
> 기준: 2026-07-08 현재 `feature/categorydetail/ui/**` 확인

Category Detail은 홈의 카테고리 지출 영역에서 특정 카테고리를 선택했을 때 열리는 상세 화면이다.
카테고리별 월 합계, 소비 추세, 정렬된 거래 목록, 거래 수정/삭제 진입을 담당한다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-structure-map.md](00-structure-map.md) | Category Detail 파일 구조와 핵심 흐름을 정리한다. | 화면 진입, 월 이동, 정렬, 거래 목록 변경 |
| [package-reference/README.md](package-reference/README.md) | entry/data/rendering/checklist로 나눈 세부 개발 문서 인덱스다. | Category Detail을 실제 수정하기 전 |
| [change-log.md](change-log.md) | Category Detail KB 변경 로그다. | 문서 변경 이유 확인 |
| [../transaction-edit/README.md](../transaction-edit/README.md) | 거래 수정 화면 KB다. | 상세 화면에서 거래 수정/삭제로 이어질 때 |
| [../finance-data/README.md](../finance-data/README.md) | DAO/Repository 데이터 KB다. | 카테고리별 합계/목록 조회 변경 |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `CategoryDetailActivity.kt` | category parameter를 받아 상세 화면 Activity를 연다. |
| `CategoryDetailScreen.kt` | 상세 화면 Composable, hero, list header, 월 이동, 거래 목록 렌더링 |
| `CategoryDetailViewModel.kt` | 월별 page cache, category filter, 정렬, delete/update action |
| `CategoryDetailExpenseFilters.kt` | keyword/card visibility 기반 지출 필터 |
| `ui/model/CategoryDetailPageData.kt` | 월별 상세 page data model |
| `ui/model/CategorySpendingTrendInfo.kt` | 카테고리 상세 chart mapper |

## 작업 판단

- 월 이동/cache는 `CategoryDetailViewModel.kt`를 본다.
- 정렬 옵션은 `CategorySortOrder`와 list builder를 본다.
- 카테고리별 필터/카드 숨김은 `CategoryDetailExpenseFilters.kt`와 [filtering/README.md](../filtering/README.md)를 같이 본다.
- 거래 수정/삭제는 [transaction-edit/README.md](../transaction-edit/README.md), [transaction-mutation/README.md](../transaction-mutation/README.md)를 같이 본다.
