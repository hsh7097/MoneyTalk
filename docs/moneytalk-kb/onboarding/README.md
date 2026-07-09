---
type: domain
title: Onboarding 도메인
description: Splash, Intro, Permission 화면과 초기 권한/온보딩 진입을 설명한다.
tags: [moneytalk, onboarding, splash, permission]
resource: app/src/main/java/com/sanha/moneytalk/feature/intro/
timestamp: 2026-07-09T03:15:00+09:00
status: draft
---

# Onboarding 도메인

> 상태: draft
> 기준: 2026-07-08 현재 `feature/splash/**`, `feature/intro/**`, `MainActivity` 권한 요청 흐름 확인

Onboarding은 앱 최초 진입, 소개 화면, SMS 권한 안내, Splash 화면을 포함한다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| 이 문서 | Splash/Intro/Permission 화면과 권한 진입 흐름을 정리한다. | 초기 진입/권한/온보딩 작업 |
| [01-sms-permission-policy.md](01-sms-permission-policy.md) | SMS 권한 요청 사유, 데이터 고지, Play Console release gate를 정리한다. | 권한 문구, 개인정보 고지, SMS permission release 작업 |
| [change-log.md](change-log.md) | KB 변경 로그다. | 문서 변경 이유 확인 |
| [../app-shell/README.md](../app-shell/README.md) | 앱 진입과 Activity 권한 요청 KB다. | 권한 요청 launcher 또는 root flow 변경 |
| [../coachmark/README.md](../coachmark/README.md) | 화면별 coachmark 기능 KB다. | 진입 이후 screen onboarding 변경 |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `feature/splash/ui/SplashScreen.kt` | Splash UI |
| `feature/intro/ui/IntroActivity.kt` | Intro/permission flow Activity |
| `feature/intro/ui/OnboardingScreen.kt` | 앱 소개/onboarding UI |
| `feature/intro/ui/PermissionScreen.kt` | SMS 권한 안내 UI |
| `MainActivity.kt` | 실제 권한 요청 launcher와 앱 root |

## 주의 경계

- 시스템 권한 요청은 Activity 경계에서 처리한다.
- 화면별 기능 코치마크는 이 도메인이 아니라 `coachmark` 기능 KB와 각 화면 KB에서 관리한다.
- SMS 권한 고지와 Play Console 선언은 [01-sms-permission-policy.md](01-sms-permission-policy.md)를 기준으로 실제 데이터 흐름과 동기화한다.
