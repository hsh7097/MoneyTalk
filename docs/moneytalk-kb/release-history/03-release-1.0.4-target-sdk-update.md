---
type: reference
title: Release 1.0.4 Target SDK Update
description: Google Play 대상 API 수준 정책 대응을 위한 1.0.4 빌드 설정 변경을 기록한다.
tags: [moneytalk, release, 1.0.4, target-sdk, android-16]
resource: app/build.gradle.kts
timestamp: 2026-09-08T00:30:00+09:00
status: verified
---

# 1.0.4 대상 API 수준 업데이트

## 배경

기존 기록의 프로덕션 번들 `20 (1.0.3)`은 대상 SDK 35다. 2026년 8월 31일부터 일반 Android 앱 업데이트를 제출하려면 Android 16(API 36) 이상을 타겟팅해야 한다. [Google Play 대상 API 요구사항](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)을 2026-09-08 재확인했다.

## 2026-07-24 변경 기록

- `compileSdk`: 36 유지
- `targetSdk`: 35 → 36
- `versionCode`: 20 → 21
- `versionName`: 1.0.3 → 1.0.4

대상 API·버전 설정 변경 자체는 기능 코드, DB 스키마, ProGuard, 서명 설정을 변경하지 않는다.

## 2026-07-24 검증 기록

- `:app:bundleRelease`로 새 AAB 생성
- 생성 산출물에서 `versionCode=21`, `versionName=1.0.4`, `targetSdkVersion=36` 확인
- Play Console 정책 알림 해소는 새 AAB를 테스트 또는 프로덕션 트랙에 업로드한 뒤 별도로 확인

## 현재 커밋 기준 (2026-09-08)

- 작업 트리에 이미 설정된 `versionCode=22`, `versionName=1.0.4`, `targetSdk=36`을 유지한다. 위의 `21`은 이전 기록이며 이번 커밋과 동일한 산출물을 뜻하지 않는다.
- debug/release APK 빌드 성공과 실기기 APK의 버전 22·대상 SDK 36을 확인했다. 문자 수집·화면·백업 수정은 별도 기능 커밋으로 관리한다.
- 이번 검증은 새 AAB의 Play 업로드·정책 알림 해소·정식 배포본 AI 인증 통과를 확인한 결과가 아니다.
