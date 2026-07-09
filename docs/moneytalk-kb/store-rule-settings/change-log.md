---
type: changelog
title: Store Rule Settings KB 변경 로그
description: Store Rule Settings KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, store-rule, changelog]
resource: docs/moneytalk-kb/store-rule-settings/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Store Rule Settings KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-07-09 | `StoreRuleSettingsActivity`, `StoreRuleSettingsViewModel`, `StoreRuleSettingsScreen`, `StoreRuleSyncService` 재확인 | Store Rule Settings package-reference 추가 | `package-reference/**`, `README.md` | 규칙 CRUD, 소급 적용, category select, 코치마크 target을 세부 문서로 분리. |
| 2026-07-08 | `feature/storerulesettings/ui/**`, `StoreRuleRepository` 확인 | Store Rule Settings 화면 KB 생성 | `README.md` | 거래처 규칙 화면과 카테고리 분류/거래 일괄 변경 영향 경계를 기록. |
