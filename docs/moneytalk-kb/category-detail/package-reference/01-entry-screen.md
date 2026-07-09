---
type: package-reference
title: Category Detail entry screen
description: Category Detail 화면의 사용자 진입 경로, Activity extra, SavedStateHandle 계약을 정리한다.
tags: [moneytalk, category-detail, entry, activity]
resource: app/src/main/java/com/sanha/moneytalk/feature/categorydetail/ui/CategoryDetailActivity.kt
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Category Detail entry screen

## 진입 흐름

```text
Home category expense surface
-> CategoryDetailActivity.open(context, category, year, month, includeSubcategories)
-> CategoryDetailActivity
-> CategoryDetailScreen
-> CategoryDetailViewModel(savedStateHandle)
```

Category Detail은 하단 탭 route가 아니라 별도 `ComponentActivity`다.
홈의 카테고리 지출 영역에서 특정 카테고리를 눌렀을 때 진입하며, 선택 월과 하위 카테고리 포함 여부를 intent extra로 넘긴다.

## Activity 계약

| 항목 | 현재 값 | 사용 위치 | 변경 시 같이 볼 파일 |
|---|---|---|---|
| `EXTRA_CATEGORY` | `extra_category` | `CategoryDetailActivity.open`, `CategoryDetailViewModel` | 홈 카테고리 클릭부, `Category.fromDisplayName` |
| `EXTRA_YEAR` | `extra_year` | 초기 `selectedYear` | Home 월 상태, 월 이동 UI |
| `EXTRA_MONTH` | `extra_month` | 초기 `selectedMonth` | Home 월 상태, 월 이동 UI |
| `EXTRA_INCLUDE_SUBCATEGORIES` | `extra_include_subcategories` | `categoryNames` 계산 | 상위/하위 카테고리 조회 정책 |

## Activity 책임

| 파일 | 책임 |
|---|---|
| `CategoryDetailActivity.kt` | `open()` helper, intent extra 전달, theme mode 수집, `CategoryDetailScreen(onBack = finish)` 연결 |
| `CategoryDetailViewModel.kt` | SavedStateHandle에서 extra를 읽어 category filter와 초기 월을 만든다. |
| `CategoryDetailScreen.kt` | 화면에서 back action과 월 pager action을 ViewModel에 연결한다. |

## 수정 기준

1. 새 extra를 추가하면 `CategoryDetailActivity.open()`과 `CategoryDetailViewModel`의 SavedStateHandle key를 동시에 갱신한다.
2. category displayName 의미를 바꾸면 `Category.fromDisplayName`, custom category 처리, `CategoryProvider.resolveEmoji()`까지 확인한다.
3. `includeSubcategories` 정책을 바꾸면 `category.displayNamesIncludingSub`, category detail 합계, 홈 카테고리 표면의 진입 인자를 같이 본다.
4. Activity 진입점이 바뀌면 [../../screen-requirements/01-screen-requirements-index.md](../../screen-requirements/01-screen-requirements-index.md)와 [../../02-screen-entry-paths.md](../../02-screen-entry-paths.md)를 갱신한다.
