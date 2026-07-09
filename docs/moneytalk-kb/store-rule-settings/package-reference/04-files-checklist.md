---
type: package-reference
title: Store Rule Settings files checklist
description: Store Rule Settings 수정 전후 확인할 파일과 검증 질문을 정리한다.
tags: [moneytalk, store-rule, checklist]
resource: app/src/main/java/com/sanha/moneytalk/feature/storerulesettings/
timestamp: 2026-07-09T07:05:00+09:00
status: draft
---

# Store Rule Settings files checklist

## 빠른 파일 찾기

| 변경 유형 | 먼저 볼 파일 | 함께 볼 파일 |
|---|---|---|
| Settings row/진입 | `SettingsScreen.kt` | `StoreRuleSettingsActivity.kt`, settings menu map |
| 규칙 목록/입력 UI | `StoreRuleSettingsScreen.kt` | `StoreRuleSettingsViewModel.kt`, string resources |
| 규칙 CRUD | `StoreRuleSettingsViewModel.kt` | `StoreRuleRepository.kt`, `StoreRuleEntity` |
| 소급 적용 | `StoreRuleSyncService.kt` | `ExpenseRepository`, [../../transaction-mutation/README.md](../../transaction-mutation/README.md) |
| matching 정책 | `StoreRuleRepository.kt` | `StoreNameNormalizer`, category classification KB |
| 코치마크 | `StoreRuleSettingsScreen.kt` | `StoreRuleCoachMark.kt`, [../../coachmark/README.md](../../coachmark/README.md) |

## 검증 질문

1. keyword blank 저장이 차단되는가?
2. category/fixed/stats rule이 하나도 없을 때 저장이 차단되는가?
3. 새 rule 저장 후 기존 거래의 category/fixed/stats excluded 소급 적용이 의도대로 되는가?
4. rule 삭제 후 기존 거래 재분류/상태 복구 정책이 의도와 맞는가?
5. custom category 추가 후 category select dialog에 반영되는가?
6. guide reset 후 `store_rule_add` target 코치마크가 다시 표시되는가?

## 권장 검증

- `.\gradlew.bat assembleDebug`
- Settings -> Store Rule Settings 진입
- 규칙 추가, 편집, 삭제
- category만 있는 rule, fixed만 있는 rule, category+fixed rule 저장
- 기존 거래 데이터가 있는 거래처 keyword로 소급 적용 확인
- Settings의 가이드 다시 보기 후 Store Rule guide 표시 확인
