---
type: change-log
title: Worklogs KB Change Log
description: Worklogs KB 변경 이력
tags: [moneytalk, kb, worklog, changelog]
resource: docs/moneytalk-kb/worklogs/
timestamp: 2026-07-12T23:14:00+09:00
status: draft
---

# Change Log

## 2026-07-12

- SM-F966N 앱 데이터 초기화 후 최근 2개월 SMS 재동기화, JSON 집계, 화면·시스템 하위 경로, local/AI 인사이트 결과를 `02-release-readiness-remediation-plan.md`에 추가했다.
- App Check 실패 반복 호출 격리, 분류 Job 상호배제, 최신 257개 테스트와 APK/AAB fingerprint를 기록했다.
- 동기화 후처리·카드 등록의 삭제 race, sync completion 전 잔여 분류 누락, batch 요청 시작 경쟁과 중복 삭제 gate를 추가 보완했다.
- 삭제 gate epoch와 월별 sync 실제 차감 결과 기반 환불을 추가해 삭제 전 준비 요청이 데이터·watermark·크레딧을 되살리거나 유실하지 않도록 보완했다.
- SMS 권한을 보상형 광고보다 먼저 확인하고, 광고 시작 시 pending 월별 sync epoch를 조기 삭제하던 회귀를 수정했으며 UI 숨김/요청 취소 계약과 프로세스 강제 종료 시 비영속 크레딧 정산 리스크를 기록했다.
- clean-sync 실기기 APK와 후속 실패 격리 패치 산출물의 provenance를 분리하고, 최신 실기기 덮어 설치가 ADB 재연결 대기임을 명시했다.

## 2026-07-09

- 루트 `AI_EXPERIENCE_AGENT_WORKLOG.md`, `SEARCH_OPTIMIZATION_AGENT_WORKLOG.md`를 그대로 옮기지 않고 현재 유효한 결정과 보류 사유만 `01-agent-worklog-decisions.md`로 정리했다.

## 2026-07-10

- 릴리즈 준비 보완 작업계획 문서 `02-release-readiness-remediation-plan.md`를 worklogs 진입점에 연결했다.
