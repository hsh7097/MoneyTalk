---
type: package-reference
title: Category Detail package-reference
description: Category Detail 화면 수정 위치를 entry, data, rendering, checklist로 나눈 세부 개발 문서 인덱스다.
tags: [moneytalk, category-detail, package-reference]
resource: app/src/main/java/com/sanha/moneytalk/feature/categorydetail/
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Category Detail package-reference

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [01-entry-screen.md](01-entry-screen.md) | Home에서 Category Detail로 들어오는 Activity extra, SavedStateHandle 계약 | 진입 경로, intent extra, 월/하위 카테고리 조건 변경 |
| [02-data-viewmodel.md](02-data-viewmodel.md) | `CategoryDetailViewModel`의 page cache, repository, 필터, refresh 흐름 | 월 cache, 정렬, 카테고리 필터, 거래 mutation 변경 |
| [03-rendering-action.md](03-rendering-action.md) | `CategoryDetailScreen`의 pager, hero, 거래 목록, 정렬 tab, 액션 연결 | Composable, 거래 카드, 월 이동, 정렬 UI 변경 |
| [04-files-checklist.md](04-files-checklist.md) | 수정 전후 확인 파일과 검증 질문 | 리뷰 전 누락 점검 |
