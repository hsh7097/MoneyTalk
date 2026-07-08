---
type: structure-map
title: Coachmark 구조 지도
description: 공통 overlay/state/target registry와 화면별 step 파일 역할을 정리한다.
tags: [moneytalk, coachmark, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/core/ui/coachmark/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Coachmark 구조 지도

```text
screen Composable
-> remember CoachMarkTargetRegistry + CoachMarkState
-> Modifier.onboardingTarget(targetKey, registry)
-> visible steps filter(targetKey in registry.targets)
-> CoachMarkOverlay(state, registry)
-> onComplete -> SettingsDataStore.markScreenOnboardingSeen(screenId)
```

| 파일 | 분류 | 역할 | 함께 볼 파일 |
|---|---|---|---|
| `CoachMarkOverlay.kt` | rendering | spotlight scrim, tooltip, next/skip/finish UI | `CoachMarkState.kt` |
| `CoachMarkState.kt` | state | step list, current index, visibility | screen Composable |
| `CoachMarkStep.kt` | model | target key, title/body, position, padding | 화면별 `*CoachMark.kt` |
| `OnboardingTargetModifier.kt` | modifier/registry | target bounds 수집 | screen Composable |
| `feature/**/coachmark/*CoachMark.kt` | config | 화면별 step 목록 | 해당 화면 `README.md` |
