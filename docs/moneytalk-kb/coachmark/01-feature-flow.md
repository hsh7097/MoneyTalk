---
type: feature-flow
title: Coachmark 기능 흐름
description: 화면별 target 등록, step 필터링, overlay 표시, 완료 저장 흐름을 설명한다.
tags: [moneytalk, coachmark, feature-flow]
resource: app/src/main/java/com/sanha/moneytalk/core/ui/coachmark/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Coachmark 기능 흐름

## 표시 흐름

1. 화면 Composable에서 `CoachMarkTargetRegistry`와 `CoachMarkState`를 `remember`한다.
2. 안내 대상 Composable에 target key를 등록한다.
3. 화면 데이터 로딩이 끝나고 필요한 target이 등록되면 visible step만 골라 `state.show()`를 호출한다.
4. `CoachMarkOverlay`가 target 좌표 기준으로 spotlight와 tooltip을 표시한다.
5. 완료/skip 시 screen onboarding seen 상태를 저장한다.

## 검증 질문

1. step의 `targetKey`와 실제 Modifier target key가 같은가?
2. 데이터가 없는 화면에서도 존재하지 않는 target step을 표시하려 하지 않는가?
3. overlay가 system bar/bottom navigation과 겹치지 않는가?
4. 완료 후 `hasSeenScreenOnboardingFlow(screenId)`가 true로 바뀌는가?
5. Settings의 reset onboarding 기능이 화면별 seen 상태를 초기화하는가?
