---
type: package-reference
title: Category Settings files checklist
description: Category Settings 수정 전후 확인할 파일과 검증 질문을 정리한다.
tags: [moneytalk, category-settings, checklist]
resource: app/src/main/java/com/sanha/moneytalk/feature/categorysettings/
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Category Settings files checklist

## 빠른 파일 찾기

| 변경 유형 | 먼저 볼 파일 | 함께 볼 파일 |
|---|---|---|
| Settings row/진입 | `SettingsScreen.kt` | `CategorySettingsActivity.kt`, settings menu map |
| 탭/type 변경 | `CategorySettingsViewModel.kt` | `CategoryType`, `Category`, `CategorySettingsScreen.kt` |
| custom category CRUD | `CategorySettingsViewModel.kt` | `CustomCategoryRepository.kt`, `CustomCategoryDao` |
| category 목록 stale | `CategoryProvider` | `CategorySettingsViewModel.kt`, category picker 사용 화면 |
| UI/dialog 변경 | `CategorySettingsScreen.kt` | `CategoryAddDialog`, string resources |
| 기존 거래 영향 | `ExpenseRepository`, `IncomeRepository` | [../../transaction-mutation/README.md](../../transaction-mutation/README.md) |

## 검증 질문

1. 지출/수입/이체 탭 전환 시 기본 목록과 custom 목록이 type에 맞게 바뀌는가?
2. 빈 이름과 같은 type 중복 이름이 차단되는가?
3. custom category 추가/삭제 후 category picker와 Category Detail emoji/name resolve가 갱신되는가?
4. 기본 category에는 삭제 action이 노출되지 않는가?
5. 삭제된 custom category를 쓰던 기존 거래의 displayName 정책이 의도와 맞는가?

## 권장 검증

- `.\gradlew.bat assembleDebug`
- Settings -> Category Settings 진입
- 지출/수입/이체 탭 전환
- custom category 추가, 중복 추가, 삭제
- Transaction Edit category picker와 Category Detail custom category 표시 확인
