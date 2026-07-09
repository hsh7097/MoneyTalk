---
type: package-reference
title: Category Settings entry screen
description: Category Settings 화면의 Settings 메뉴 진입과 Activity 계약을 정리한다.
tags: [moneytalk, category-settings, entry, activity]
resource: app/src/main/java/com/sanha/moneytalk/feature/categorysettings/ui/CategorySettingsActivity.kt
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Category Settings entry screen

## 진입 흐름

```text
SettingsScreen category settings row
-> CategorySettingsActivity.open(context)
-> CategorySettingsActivity
-> CategorySettingsScreen
-> CategorySettingsViewModel
```

Category Settings는 Settings 탭에서 여는 별도 Activity다.
현재 intent extra는 없고, 화면 안에서 `CategoryType.EXPENSE` 탭을 기본 선택한다.

## Activity 책임

| 파일 | 책임 |
|---|---|
| `SettingsScreen.kt` | 카테고리 설정 row를 표시하고 `CategorySettingsActivity.open(context)` 호출 |
| `CategorySettingsActivity.kt` | Activity entry, theme mode 수집, `CategorySettingsScreen(onBack = finish)` 연결 |
| `CategorySettingsScreen.kt` | top app bar back action을 `onBack`으로 위임 |

## 수정 기준

1. Settings row 노출 조건을 바꾸면 [../../settings/package-reference/05-menu-map.md](../../settings/package-reference/05-menu-map.md)를 먼저 갱신한다.
2. 초기 탭을 intent extra로 제어하려면 Activity `open()`과 ViewModel 초기 state를 동시에 추가한다.
3. Activity 진입점이 바뀌면 [../../02-screen-entry-paths.md](../../02-screen-entry-paths.md)와 [../../screen-requirements/01-screen-requirements-index.md](../../screen-requirements/01-screen-requirements-index.md)를 갱신한다.
4. category type이 늘어나면 `CategoryType.entries`, 기본 category list, custom category 저장값을 모두 확인한다.
