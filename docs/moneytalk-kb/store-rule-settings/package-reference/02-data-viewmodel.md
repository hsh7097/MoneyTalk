---
type: package-reference
title: Store Rule Settings data and ViewModel
description: Store Rule Settings ViewModel의 규칙 CRUD, 소급 적용, category cache, 코치마크 seen 상태를 설명한다.
tags: [moneytalk, store-rule, viewmodel, data]
resource: app/src/main/java/com/sanha/moneytalk/feature/storerulesettings/ui/StoreRuleSettingsViewModel.kt
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Store Rule Settings data and ViewModel

## State

| 상태 | 의미 |
|---|---|
| `rules` | DB에서 Flow로 수집한 거래처 규칙 목록 |
| `showAddDialog` | 추가/편집 dialog 표시 여부 |
| `editingRule` | null이면 추가, 값이 있으면 편집 |
| `addKeyword`, `addCategory`, `addIsFixed`, `addErrorResId` | dialog 입력값과 validation 오류 |
| `showDeleteConfirm` | 삭제 확인 dialog 대상 rule id |
| `showCategorySelect` | category picker dialog 표시 여부 |
| `categoryEntries` | 지출 category picker에 노출할 category list |

## 데이터 흐름

```text
StoreRuleSettingsScreen action
-> StoreRuleSettingsViewModel
-> StoreRuleRepository.getAll() Flow
-> StoreRuleSyncService.applyRuleChange(previousRule, newRule)
-> StoreRuleRepository / ExpenseRepository
-> StoreRuleSettingsUiState
```

## 핵심 dependency

| dependency | 역할 | 변경 시 같이 볼 KB |
|---|---|---|
| `StoreRuleRepository` | 규칙 저장/조회/삭제, matching rule 계산 | [../../category-classification/README.md](../../category-classification/README.md) |
| `StoreRuleSyncService` | 규칙 변경을 기존 거래에 소급 적용 | [../../transaction-mutation/README.md](../../transaction-mutation/README.md) |
| `CategoryProvider` | 지출 category picker 목록 제공 | [../../category-settings/README.md](../../category-settings/README.md) |
| `SettingsDataStore` | screen onboarding seen 상태 저장 | [../../coachmark/README.md](../../coachmark/README.md) |

## CRUD 정책

| action | 구현 | 주의 |
|---|---|---|
| 목록 로딩 | `storeRuleRepository.getAll().collect` | Flow라 DB 변경 시 state가 갱신된다. |
| category 목록 | `categoryProvider.getExpenseEntries()` | 거래처 규칙은 지출 category만 선택한다. |
| 추가 | `showAddDialog()` -> `saveRule()` | keyword 필수, category/fixed/stats rule 중 하나 이상 필요 |
| 편집 | `showEditDialog(rule)` -> `saveRule()` | 기존 id/createdAt과 `isExcludedFromStats`를 유지한다. |
| 삭제 | `deleteRule(id)` | 현재 state의 rule을 찾아 `applyRuleChange(previousRule, null)` 호출 |

## 소급 적용 경계

`saveRule()`과 `deleteRule()`은 repository를 직접 upsert/delete하지 않고 `StoreRuleSyncService.applyRuleChange()`를 호출한다.
이 서비스는 rule 저장/삭제와 기존 거래의 category, fixed, stats excluded 값을 소급 반영한다.

주의할 점:

1. `isFixed`는 체크된 경우 `true`, 아니면 null로 저장한다. false rule이 아니다.
2. 현재 dialog는 `isExcludedFromStats`를 새로 입력받지 않는다. 편집 시 기존 값만 유지한다.
3. category/fixed/stats 중 하나도 없으면 저장하지 않는다.
4. matching 범위를 바꾸면 `StoreRuleRepository.findBestMatchingRule*`와 `StoreNameNormalizer`까지 확인한다.

## 코치마크 상태

- `hasSeenScreenOnboardingFlow(screenId)`와 `markScreenOnboardingSeen(screenId)`는 `SettingsDataStore`를 감싼 얇은 wrapper다.
- 화면에서는 `screenId = "store_rule"`을 사용한다.
