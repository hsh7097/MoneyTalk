---
type: package-reference
title: Category Settings package-reference
description: Category Settings 화면 수정 위치를 entry, data, rendering, checklist로 나눈 세부 개발 문서 인덱스다.
tags: [moneytalk, category-settings, package-reference]
resource: app/src/main/java/com/sanha/moneytalk/feature/categorysettings/
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Category Settings package-reference

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [01-entry-screen.md](01-entry-screen.md) | Settings에서 Category Settings로 들어오는 Activity 진입 계약 | 설정 메뉴 row, Activity 진입, back 처리 변경 |
| [02-data-viewmodel.md](02-data-viewmodel.md) | `CategorySettingsViewModel`, `CustomCategoryRepository`, `CategoryProvider` 책임 | custom category CRUD, validation, cache 무효화 변경 |
| [03-rendering-action.md](03-rendering-action.md) | category type tab, 기본/커스텀 목록, 추가/삭제 dialog action | Composable, dialog, list item 변경 |
| [04-files-checklist.md](04-files-checklist.md) | 수정 전후 확인 파일과 검증 질문 | 리뷰 전 누락 점검 |
