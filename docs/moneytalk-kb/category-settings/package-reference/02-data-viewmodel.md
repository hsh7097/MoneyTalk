---
type: package-reference
title: Category Settings data and ViewModel
description: Category Settings ViewModel의 custom category CRUD, validation, category cache 흐름을 설명한다.
tags: [moneytalk, category-settings, viewmodel, data]
resource: app/src/main/java/com/sanha/moneytalk/feature/categorysettings/ui/CategorySettingsViewModel.kt
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Category Settings data and ViewModel

## State

| 상태 | 의미 |
|---|---|
| `selectedTab` | 현재 표시 category type. 기본값은 `CategoryType.EXPENSE` |
| `defaultCategories` | `Category.expenseEntries/incomeEntries/transferEntries`에서 읽은 기본 카테고리 |
| `customCategories` | DB에서 읽은 사용자 카테고리 목록 |
| `showAddDialog` | 추가 dialog 표시 여부 |
| `addEmoji`, `addName`, `addErrorResId` | 추가 입력값과 validation 오류 |
| `showDeleteConfirm` | 삭제 확인 dialog 대상 custom category id |

## 데이터 흐름

```text
CategorySettingsScreen action
-> CategorySettingsViewModel
-> CustomCategoryRepository
-> CustomCategoryDao / CustomCategoryEntity
-> CategoryProvider.invalidateCache()
-> loadCategories()
-> CategorySettingsUiState
```

## 핵심 dependency

| dependency | 역할 | 변경 시 같이 볼 KB |
|---|---|---|
| `CustomCategoryRepository` | custom category 저장/조회/삭제, 중복 검사 | [../../finance-data/README.md](../../finance-data/README.md) |
| `CategoryProvider` | 기본+custom category cache 제공, emoji/name resolve | [../../category-classification/README.md](../../category-classification/README.md) |
| `Category` | 기본 category entry 목록 | [../../category-classification/README.md](../../category-classification/README.md) |
| `CategoryType` | EXPENSE/INCOME/TRANSFER 탭과 저장 타입 | [../../finance-data/README.md](../../finance-data/README.md) |

## CRUD 정책

| action | 구현 | 주의 |
|---|---|---|
| 탭 선택 | `selectTab(type)` -> state 변경 -> `loadCategories()` | type별 기본/커스텀 목록을 다시 만든다. |
| 추가 dialog | `showAddDialog()` | emoji 기본값은 박스 이모지, name은 빈 값으로 초기화 |
| 이름 입력 | `updateAddName(name)` | 입력 시 오류를 지운다. |
| 추가 | `addCategory()` | blank name과 type별 중복 name을 차단한다. 성공 후 `CategoryProvider.invalidateCache()` 호출 |
| 삭제 | `deleteCategory(id)` | DB 삭제 후 `CategoryProvider.invalidateCache()` 호출 |

## 영향 범위

1. custom category 추가/삭제는 이후 category picker, 자동 분류 후보, Category Detail custom category 표시에도 영향을 준다.
2. 현재 코드는 기존 거래의 category displayName을 migration하지 않는다. 삭제 정책을 바꾸려면 거래 데이터 영향 정책을 별도 설계해야 한다.
3. `CustomCategoryRepository.isDuplicate(name, type)`는 같은 type 안에서만 중복을 본다.
4. `CategoryProvider.invalidateCache()`를 빼면 다른 화면의 category list가 stale 상태가 될 수 있다.
