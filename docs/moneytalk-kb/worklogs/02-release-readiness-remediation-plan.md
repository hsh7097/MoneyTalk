---
type: worklog
title: Release Readiness Remediation Plan
description: 2026-07-09 release readiness review에서 발견된 배포 보류 이슈를 처리하기 위한 작업 계획과 검증 기록.
tags: [moneytalk, release, lint, privacy, gemini, verification]
resource: app/
timestamp: 2026-07-09T00:00:00+09:00
status: draft
---

# Release Readiness Remediation Plan

> 기준: 2026-07-09 배포 품질 재검토 결과
> 목표: 배포 보류 블로커를 하나씩 닫고, 릴리즈 게이트를 다시 통과 가능한 상태로 만든다.

## 범위

| 항목 | 상태 | 처리 기준 | 검증 |
|---|---|---|---|
| Release lint 번역 누락 | 수정 완료 | `values-en/strings.xml`에 누락된 chat 문자열 4개 추가 | `:app:lintRelease` |
| 개인정보처리방침 HTML 불일치 | 수정 완료 | SMS, 알림 접근/RCS, Drive backup, Gemini, Firebase/AdMob, 삭제 권리와 앱 내부 고지를 일치시킴 | 문서 diff, `:app:lintRelease` |
| Gemini local BuildConfig key 제거 | 수정 완료 | RTDB `/config/gemini_api_keys` 및 `/config/gemini_api_key` 경로가 실제 key 공급 경로임을 확인하고 release client local key fallback 제거 | `rg BuildConfig.GEMINI`, Gradle build |
| 기타 local API key BuildConfig 노출 | 수정 완료 | 검증 중 `CLAUDE_API_KEY`도 release BuildConfig에 포함되는 것을 확인하여 빈 값으로 고정 | generated BuildConfig, APK/AAB binary search |
| Google Sign-In 의존성 경고 | 보류 | 이번 블로커 처리 범위에서는 버전 업그레이드하지 않음. 현재 AVD smoke에서 즉시 crash는 재현되지 않았으므로 별도 회귀 티켓으로 분리 | 추후 실제 기기 Google Drive sign-in |
| 계측 테스트 skipped | 보류 | AVD에서 skipped된 real-device monthly SMS/order 테스트는 실기기 조건이 준비된 별도 검증으로 분리 | 추후 실기기 connected test |

## 처리 순서

1. `strings.xml` / `values-en/strings.xml` release lint 오류를 닫는다.
2. `docs/privacy-policy.html`을 앱 내부 privacy dialog와 민감 권한 고지 기준에 맞춘다.
3. `app/build.gradle.kts`, `PremiumManager`, deprecated Gemini DataStore fallback에서 local Gemini key 주입을 제거한다.
4. `lintRelease`, `testDebugUnitTest`, `assembleRelease`, `bundleRelease`를 재실행한다.
5. 변경 diff를 다시 읽고 과잉 수정, stale 문구, API key 잔존 여부를 확인한다.

## 결정 사항

- Gemini API key는 release APK/AAB에 포함하지 않는다.
- Gemini key 공급은 Firebase RTDB `/config/gemini_api_keys` 배열과 `/config/gemini_api_key` 단일 fallback만 사용한다.
- `CLAUDE_API_KEY`도 공개 release client에 포함하지 않는다. 기존 사용자 입력 DataStore 경로는 유지하되 빌드 기본값은 빈 문자열로 둔다.
- `local.properties`에 남아 있는 개발자 로컬 키는 빌드 스크립트가 읽지 않도록 제거 대상에서 제외한다. 이 파일은 로컬 환경 파일이며 소스 변경 범위가 아니다.
- `play-services-auth` 버전 변경은 이번 수정에 포함하지 않는다. 의존성 업그레이드는 사용자가 요청한 Gemini key 제거와 별개이며, 별도 회귀 검증이 필요하다.

## 검증 기록

| 검증 | 결과 | 메모 |
|---|---|---|
| `:app:lintRelease` | 통과 | MissingTranslation 4건 해소 |
| `:app:testDebugUnitTest` | 통과 | Gradle 통합 검증에서 성공 |
| `:app:assembleRelease` | 통과 | release APK 생성 |
| `:app:bundleRelease` | 통과 | release AAB 생성 |
| `apksigner verify app-release.apk` | 통과 | v2 signing verified |
| APK/AAB local key binary search | 통과 | local `GEMINI_API_KEY`, `CLAUDE_API_KEY` 미검출 |
| release APK install/launch smoke | 통과 | `app-release.apk` 설치 후 런처 실행, 앱 프로세스 유지, fatal crash 미검출 |
| `:app:connectedDebugAndroidTest` | 통과 | SM-F966N(Android 16)에서 3개 테스트 실행, 0 skipped / 0 failed |
