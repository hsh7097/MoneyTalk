---
type: package-reference
title: Store Rule Settings rendering and action
description: Store Rule Settings 화면의 목록, dialog, category select, coachmark action을 정리한다.
tags: [moneytalk, store-rule, compose, action]
resource: app/src/main/java/com/sanha/moneytalk/feature/storerulesettings/ui/StoreRuleSettingsScreen.kt
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Store Rule Settings rendering and action

## Composable 계층

```text
StoreRuleSettingsScreen
-> Scaffold / TopAppBar(add target)
-> LazyColumn
   -> description
   -> empty text or StoreRuleListItem
   -> add row
-> CoachMarkOverlay
-> AddEditRuleDialog
-> CategorySelectDialog
-> delete AlertDialog
```

## 주요 UI 블록

| 블록 | 구현 | 데이터/action |
|---|---|---|
| top app bar | `TopAppBar` | back, add icon, `store_rule_add` target |
| 설명/empty | LazyColumn item | `store_rule_settings_description`, `store_rule_settings_empty` |
| 규칙 item | `StoreRuleListItem` | keyword, category, fixed, stats excluded label |
| 추가/편집 dialog | `AddEditRuleDialog` | keyword, category select/reset, fixed checkbox |
| category select | `CategorySelectDialog` | `CategoryType.EXPENSE`, custom category 포함 목록 |
| 삭제 확인 | `AlertDialog` | `viewModel.deleteRule(id)` |
| guide overlay | `CoachMarkOverlay` | visible step 완료 시 seen 저장 |

## Action 연결

| 사용자 action | ViewModel 함수 | 영향 |
|---|---|---|
| add icon/row 클릭 | `showAddDialog()` | dialog state 초기화 |
| 규칙 item 클릭 | `showEditDialog(rule)` | 기존 rule 값으로 dialog 열기 |
| keyword 입력 | `updateKeyword` | 오류 초기화 |
| category 선택/해제 | `showCategorySelect`, `updateCategory(null/selected)` | category rule 변경 |
| fixed checkbox | `updateIsFixed` | fixed rule 변경 |
| 저장 | `saveRule()` | validation 후 `StoreRuleSyncService.applyRuleChange` |
| 삭제 | `showDeleteConfirm(id)` -> `deleteRule(id)` | rule 삭제와 소급 적용 |
| guide 완료 | `markScreenOnboardingSeen("store_rule")` | 다음 진입부터 guide 숨김 |

## 변경 시 주의

1. dialog에 stats excluded 입력을 추가하면 `StoreRuleSettingsUiState`, `StoreRuleEntity`, `StoreRuleSyncService` 적용 정책을 같이 바꾼다.
2. category picker는 현재 지출 category만 사용한다. 수입/이체 규칙을 추가하려면 `CategoryType` 정책부터 분리한다.
3. `StoreRuleListItem`은 category/fixed/stats label을 조건부 표시한다. 새 rule 필드를 추가하면 목록 label과 dialog 입력을 같이 갱신한다.
4. 코치마크는 target registry에 등록된 target만 표시하므로 target key 누락 시 guide가 안 뜬다.
