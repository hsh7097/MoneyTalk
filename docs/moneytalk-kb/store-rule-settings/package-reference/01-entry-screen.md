---
type: package-reference
title: Store Rule Settings entry screen
description: Store Rule Settings 화면의 Settings 메뉴 진입, Activity 계약, 코치마크 entry를 정리한다.
tags: [moneytalk, store-rule, entry, activity, coachmark]
resource: app/src/main/java/com/sanha/moneytalk/feature/storerulesettings/ui/StoreRuleSettingsActivity.kt
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Store Rule Settings entry screen

## 진입 흐름

```text
SettingsScreen store rule row
-> StoreRuleSettingsActivity.open(context)
-> StoreRuleSettingsActivity
-> StoreRuleSettingsScreen
-> StoreRuleSettingsViewModel
-> CoachMarkOverlay(screenId = "store_rule")
```

Store Rule Settings는 Settings 탭에서 여는 별도 Activity다.
현재 intent extra는 없고, 화면 안에서 규칙 목록과 추가/편집 dialog를 처리한다.

## Activity 책임

| 파일 | 책임 |
|---|---|
| `SettingsScreen.kt` | 거래처 규칙 row를 표시하고 `StoreRuleSettingsActivity.open(context)` 호출 |
| `StoreRuleSettingsActivity.kt` | Activity entry, theme mode 수집, `StoreRuleSettingsScreen(onBack = finish)` 연결 |
| `StoreRuleSettingsScreen.kt` | top app bar back/add action, 코치마크 target 등록 |
| `StoreRuleCoachMark.kt` | Store Rule 화면 guide step 정의 |

## 코치마크 entry

| 항목 | 현재 값 | 사용 위치 |
|---|---|---|
| screen id | `store_rule` | `hasSeenScreenOnboardingFlow`, `markScreenOnboardingSeen` |
| target key | `store_rule_add` | top app bar add button modifier |
| steps | `storeRuleCoachMarkSteps()` | visible target만 필터링 후 overlay 표시 |

## 수정 기준

1. Settings row 노출 조건을 바꾸면 [../../settings/package-reference/05-menu-map.md](../../settings/package-reference/05-menu-map.md)를 갱신한다.
2. 규칙 편집 대상 id 같은 extra를 추가하려면 Activity `open()`과 ViewModel 초기 state를 동시에 추가한다.
3. 코치마크 target key를 바꾸면 `StoreRuleCoachMark.kt`와 `Modifier.onboardingTarget(...)` 값을 같이 바꾼다.
4. Activity 진입점이 바뀌면 [../../02-screen-entry-paths.md](../../02-screen-entry-paths.md)와 [../../screen-requirements/01-screen-requirements-index.md](../../screen-requirements/01-screen-requirements-index.md)를 갱신한다.
