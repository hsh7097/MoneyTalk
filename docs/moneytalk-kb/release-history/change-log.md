---
type: change-log
title: Release History KB Change Log
description: Release History KB 변경 이력
tags: [moneytalk, kb, release, changelog]
resource: docs/moneytalk-kb/release-history/
timestamp: 2026-09-14T22:50:00+09:00
status: draft
---

# Change Log

## 2026-09-14

- `04-release-1.0.5-readiness.md`에 최근 내부 변경과 Play 배포본의 차이, 수집·달력·월 경계 충돌 보완, 기능별 커밋과 검증 결과를 기록했다.
- 이미 반영된 작업 브랜치 정리와 미통합 실험의 태그·bundle 보존을 구분하고, 승인된 버전 변경·원격 통합·Play 제출의 실제 진행 상태를 기록했다.
- Console의 개발자 등록 완료, 운영 22의 API 36 대응, API 35가 남은 Alpha·내부 테스트 트랙을 구분했다.
- `4a69b6b`의 버전 23 최종 AAB/APK 해시, 업로드 인증서 일치, 운영 App Check·광고 설정, 네이티브 16KB 및 zipalign 검증 결과를 추가했다. 사전 검증용 22번 파일과 실제 제출할 23번 파일을 분리했다.
- 파일 선택 도구 오류 후 실제 Console에서 내부 초안 `4`의 23번 번들·노트 저장과 기존 16번 제외를 확인한 상태로 정정했다. 도구 오류와 실제 업로드 상태를 구분하며, Play 제출·출시와 보호 브랜치 통합·원격 푸시는 미수행으로 명시했다.

## 2026-09-08

- 현재 작업 트리의 `versionCode 22`와 과거 기록의 `21`을 구분하고, 1.0.4 APK 빌드·설치 확인과 Play 업로드 검증의 경계를 보강했다.

## 2026-07-24

- Google Play 대상 API 정책 대응을 위해 `targetSdk 36`, `versionCode 21`, `versionName 1.0.4`로 변경한 근거와 검증 경계를 `03-release-1.0.4-target-sdk-update.md`에 기록했다.
- Play Console 알림 해소 여부는 새 AAB 업로드 후 별도 확인해야 함을 명시했다.

## 2026-07-13

- `1.0.2` 태그부터 `fa07dda` 1.0.3 배포 후보까지 28개 커밋을 기능·안정화·검증·운영 결정으로 통합한 `02-release-1.0.3-development-summary.md`를 추가했다.
- 어제·오늘 작업의 상세 기록이 worklogs, SMS pipeline, category classification, embedding, app-shell, monetization KB에 존재하는지 대조하고 새 요약에서 해당 문서로 연결했다.
- `send_origin_message` 원본 포함 조건, false 상태의 masked 품질 데이터 수집, Play 서명본 AI 응답과 크레딧 영속 정산 잔여 검증을 명시했다.
- 루트 `README.md`와 `00-agent-routing.md`에 release-history 진입점을 연결해 태그 diff·릴리즈 노트 요청에서 새 요약을 우선 찾도록 했다.

## 2026-07-09

- 루트 `CHANGELOG.md`를 그대로 옮기지 않고 `01-release-timeline.md`로 구조 판단에 필요한 변경 축만 요약했다.
