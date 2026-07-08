---
type: feature
title: Coachmark 기능
description: 화면별 온보딩 코치마크 overlay, target registry, screen seen 상태 저장 흐름을 설명한다.
tags: [moneytalk, coachmark, onboarding, feature, compose]
resource: app/src/main/java/com/sanha/moneytalk/core/ui/coachmark/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Coachmark 기능

> 상태: draft
> 기준: 2026-07-08 현재 `core/ui/coachmark/**`, `feature/**/coachmark/**`, `SettingsDataStore` 확인

Coachmark 기능은 화면별 주요 UI target을 등록하고 첫 진입 시 overlay와 tooltip으로 사용자를 안내한다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-structure-map.md](00-structure-map.md) | 공통 coachmark와 화면별 step 파일을 정리한다. | 코치마크 target/step/overlay 수정 |
| [01-feature-flow.md](01-feature-flow.md) | target 등록부터 완료 저장까지 흐름을 설명한다. | 코치마크가 안 뜨거나 위치가 틀릴 때 |
| [change-log.md](change-log.md) | Coachmark KB 변경 로그다. | 문서 변경 이유 확인 |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `core/ui/coachmark/CoachMarkOverlay.kt` | scrim, spotlight, tooltip card overlay |
| `core/ui/coachmark/CoachMarkState.kt` | step index, visibility, next/dismiss 상태 holder |
| `core/ui/coachmark/CoachMarkStep.kt` | step model, target key, tooltip 위치 |
| `core/ui/coachmark/OnboardingTargetModifier.kt` | target bounds를 registry에 등록하는 Modifier |
| `feature/*/ui/coachmark/*CoachMark.kt` | 화면별 step 목록 |
| `SettingsDataStore` | 화면별 onboarding seen 상태 저장 |

## 화면별 step

| 화면 | 파일 |
|---|---|
| Home | `feature/home/ui/coachmark/HomeCoachMark.kt` |
| History | `feature/history/ui/coachmark/HistoryCoachMark.kt`, `FilterCoachMark.kt` |
| Chat | `feature/chat/ui/coachmark/ChatCoachMark.kt` |
| Settings | `feature/settings/ui/coachmark/SettingsCoachMark.kt` |
| Store Rule | `feature/storerulesettings/ui/coachmark/StoreRuleCoachMark.kt` |
| Transaction Edit | `feature/transactionedit/ui/coachmark/TransactionEditCoachMark.kt` |
