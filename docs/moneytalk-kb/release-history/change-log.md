---
type: change-log
title: Release History KB Change Log
description: Release History KB 변경 이력
tags: [moneytalk, kb, release, changelog]
resource: docs/moneytalk-kb/release-history/
timestamp: 2026-07-13T01:11:00+09:00
status: draft
---

# Change Log

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
