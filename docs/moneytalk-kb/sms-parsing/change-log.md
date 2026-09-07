---
type: log
title: 문자 파싱 KB Change Log
description: 문자 파싱 기능 KB 변경 상세 이력을 기록한다.
tags: [moneytalk, sms-parsing, changelog]
resource: docs/moneytalk-kb/sms-parsing/
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 문자 파싱 Change Log

## 2026-09-08 - 환불 보정 후 알림 상세 대상 유지

- 알림 클릭이 수입 ID를 사용하므로 안내 → 입금 정본 보정 시 `SmsInstantProcessor`와 `SmsIngestionWriter`가 기존 ID와 메모/생성시각/반복일을 보존한다.
- 복원 행이 우선 선택되면 별도 안내 행만 삭제하고, 선택한 행 자체는 삭제하지 않는다. 같은 배치의 늦은 안내가 보정된 ID를 다시 덮는 것도 막는다.
- 즉시 3개·배치 4개 Room 회귀를 추가했다. 통합 실행 결과는 작업 QA 기록에 남긴다.

## 2026-09-08

- 커밋 전 리뷰에서 잔액 없는 같은 분의 정상 반복 결제가 누락될 수 있는 경계를 수정했다. 다른 smsId를 합칠 때 숫자 잔액·잔고·누적 근거와 원문 전체 동일을 DAO 및 writer의 모든 fuzzy 비교에 요구한다.
- pending 후보도 기존 DB 원문으로 검증하며, 원문 없는 후보는 정확한 ID로만 완료한다. 분 단위 시각은 그대로 두고 수신시각만 20~39초 다른 정상 반복 입력의 Room 회귀 3개와 숫자 잔액 근거 unit 3개를 추가했다.

## 2026-09-07

- 후속 실기기 감사에서 확인한 동일 SMS 재전달을 즉시 저장 단계에서도 차단한다. 발신자·원문 전체·거래 정보가 같고 표준 smsId의 수신시각 차이가 60초 이내인 경우만 DAO transaction에서 스킵하며, 잔액이 다른 원문은 보존한다.
- 동일/상이 본문과 수신시각 경계의 합성 Room 회귀 5개를 추가했다. 아래 기존 검증 수치는 이 추가 보완 전 기준이다.
- 실시간 로컬 미매칭을 `Deferred`로 구분하고 화면 밖 영속 큐/Job 재처리를 연결했다.
- `SmsIngestionWriter`로 기존 저장 정책을 공유하고 지출 자동 수집 조회·저장 및 교차 유형 삭제·대체를 트랜잭션으로 보호했다.
- MMS/RCS 처리 중 변경 신호 누락과 마지막 본문 재조회 누락을 수정했다.
- 전체 삭제 시 pending 큐/Job도 비우며 기존 60초 본문 fuzzy 정책은 유지한다.
- 검증 범위와 새 파일 책임을 `06-ingestion-contract.md`, `01-feature-flow.md`, `05-file-inventory.md`에 반영했다.
- 검증 완료: 앱 빌드, JVM 288개, Android 16 실제 Room 16개 통과. 삼성 전용 provider와 실제 OS 절전/재부팅 수신은 별도 확인 대상이다.

## 2026-07-09

- 기준: 루트 `SMS_PARSING.md`, `AI_CONTEXT.md` SMS 파싱 파트 재검토
- 변경 근거: 원문 문서를 이동하지 않고 SMS/MMS/RCS 읽기, 중복 제거, 수입/지출 분기, 실시간 보완, coverage 계약을 KB 내부 문서로 흡수하기 위해 `06-ingestion-contract.md`를 추가했다.
- 갱신한 KB: `README.md`, `06-ingestion-contract.md`

## 2026-07-03

- 기준: `MainViewModel`, `core/sms`, `core/sync`, `06-ingestion-contract.md` 확인
- 변경 근거: 기능 단위 KB 요구 반영
- 갱신한 KB: `sms-parsing/**`
- 다음 검증: 문자 파싱 결과 필드 추가 작업에서 parser, 저장, refresh 파일을 정확히 찾는지 확인
