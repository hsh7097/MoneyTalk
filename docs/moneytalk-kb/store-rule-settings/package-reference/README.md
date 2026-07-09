---
type: package-reference
title: Store Rule Settings package-reference
description: Store Rule Settings 화면 수정 위치를 entry, data, rendering, checklist로 나눈 세부 개발 문서 인덱스다.
tags: [moneytalk, store-rule, settings, package-reference]
resource: app/src/main/java/com/sanha/moneytalk/feature/storerulesettings/
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Store Rule Settings package-reference

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [01-entry-screen.md](01-entry-screen.md) | Settings에서 Store Rule Settings로 들어오는 Activity 진입과 코치마크 entry | 설정 메뉴 row, Activity 진입, guide 변경 |
| [02-data-viewmodel.md](02-data-viewmodel.md) | `StoreRuleSettingsViewModel`, `StoreRuleRepository`, `StoreRuleSyncService` 책임 | 규칙 CRUD, 소급 적용, category/fixed/stats rule 변경 |
| [03-rendering-action.md](03-rendering-action.md) | 규칙 목록, 추가/편집 dialog, category select, delete dialog, coachmark action | Composable, dialog, guide target 변경 |
| [04-files-checklist.md](04-files-checklist.md) | 수정 전후 확인 파일과 검증 질문 | 리뷰 전 누락 점검 |
