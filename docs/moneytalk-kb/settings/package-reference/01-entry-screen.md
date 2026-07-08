---
type: package-reference
title: Settings entry/screen
description: Settings 탭 진입, 보조 설정 Activity 이동, screen onboarding 진입을 설명한다.
tags: [moneytalk, settings, entry]
resource: app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsScreen.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Settings entry/screen

## 진입

```text
MainActivity.MoneyTalkApp
-> NavGraph(Screen.Settings.route)
-> SettingsScreen()
```

## 보조 화면 진입

Settings 탭은 여러 Activity의 허브 역할을 한다.

| 대상 | 기준 KB |
|---|---|
| 카테고리 설정 | [category-settings/README.md](../../category-settings/README.md) |
| SMS 설정 | [sms-settings/README.md](../../sms-settings/README.md) |
| 거래처 규칙 설정 | [store-rule-settings/README.md](../../store-rule-settings/README.md) |
| AI 크레딧 | [ai-credit-screen/README.md](../../ai-credit-screen/README.md) |

## 코치마크

- Settings 코치마크 step은 `SettingsCoachMark.kt`에 있다.
- 사용 여부 저장은 `SettingsDataStore`의 screen onboarding state를 통해 처리한다.
- 전체 코치마크 구조는 [coachmark/README.md](../../coachmark/README.md)를 본다.
