---
type: package-reference
title: Category Settings rendering and action
description: Category Settings 화면의 탭, 기본/커스텀 목록, 추가/삭제 dialog action을 정리한다.
tags: [moneytalk, category-settings, compose, action]
resource: app/src/main/java/com/sanha/moneytalk/feature/categorysettings/ui/CategorySettingsScreen.kt
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Category Settings rendering and action

## Composable 계층

```text
CategorySettingsScreen
-> Scaffold / TopAppBar
-> CategoryTypeTabRow
-> LazyColumn
   -> default category header/items
   -> custom category header/items
   -> add row
-> CategoryAddDialog
-> delete AlertDialog
```

## 주요 UI 블록

| 블록 | 구현 | 데이터/action |
|---|---|---|
| top app bar | `TopAppBar` | `onBack` |
| category type tab | `CategoryTypeTabRow` | `uiState.selectedTab`, `viewModel.selectTab` |
| 기본 목록 | `CategoryListItem(isCustom = false)` | `uiState.defaultCategories` |
| 커스텀 목록 | `CategoryListItem(isCustom = true)` | `uiState.customCategories`, delete button |
| 추가 row/dialog | `CategoryAddDialog` | emoji/name 입력, `viewModel.addCategory()` |
| 삭제 확인 | `AlertDialog` | `viewModel.deleteCategory(id)` |

## Action 연결

| 사용자 action | ViewModel 함수 | 영향 |
|---|---|---|
| 탭 선택 | `selectTab(type)` | 기본/커스텀 목록 재로드 |
| 추가 row 클릭 | `showAddDialog()` | 입력 state 초기화 |
| emoji/name 입력 | `updateAddEmoji`, `updateAddName` | dialog state 갱신 |
| 추가 저장 | `addCategory()` | validation, DB insert, category cache 무효화 |
| custom category 삭제 | `showDeleteConfirm(id)` -> `deleteCategory(id)` | DB delete, category cache 무효화 |

## 변경 시 주의

1. 기본 category는 삭제 버튼이 없다. 기본 category 수정/삭제를 추가하면 `Category` enum/list 정책부터 확인한다.
2. `CategoryAddDialog`는 공통 컴포넌트다. 필드나 validation UX를 바꾸면 다른 사용처가 있는지 확인한다.
3. custom category 편집 기능은 현재 없다. 편집을 추가하면 add dialog state를 edit state와 분리할지 먼저 정한다.
4. 화면 문구를 추가하면 `strings.xml` 계열 리소스를 사용한다.
