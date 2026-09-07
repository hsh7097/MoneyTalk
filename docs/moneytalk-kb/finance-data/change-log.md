---
type: log
title: Finance Data KB Change Log
description: Finance Data KB 변경 상세 이력을 기록한다.
tags: [moneytalk, finance-data, changelog]
resource: docs/moneytalk-kb/finance-data/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# Finance Data Change Log

## 2026-09-08

- 커밋 전 리뷰에서 잔액 없는 동일 분·금액·상호의 정상 반복 결제가 60초 fuzzy 규칙으로 누락될 수 있음을 확인했다.
- DAO와 writer의 입력 필터/현재 배치/DB 보정/pending 완료 모두에 원문 전체 동일 및 숫자 잔액·잔고·누적 근거를 요구하도록 제한했다. 정확한 smsId 처리와 기존 SMS/앱 알림 교차 출처 정책은 유지한다.
- 합성 Room 회귀 3개는 분 단위 날짜 파싱 결과가 같은 두 입력을 수신시각만 20~39초 달리하여 보존하는지 검증한다. 기존 재전달 보정 fixture는 실제 관측 16쌍처럼 숫자 잔액을 포함하도록 보완했다. 근거 판정 unit 3개는 원 단위 생략, 필드 이름만 있는 경우, 원문 불일치를 구분한다.

## 2026-09-07

- 후속 실기기 백업 검사에서 같은 발신자·원문·거래 정보에 수신시각만 1~2초 다른 16쌍을 확인했다. 즉시 SMS에도 같은 transaction 안에서 원문 전체·거래 정보·표준 smsId 수신시각 60초 조건을 비교해 재전달을 스킵하도록 보완했다.
- 합성 문자 기반 Room 회귀 5개를 추가했다: 동시 재전달 30회, 배치/즉시 재전달, 잔액이 다른 정상 가능 거래 보존, 수신시각 60초 경계, 사용자 메타데이터 보존. 아래 16개 통과 결과는 이 추가 보완 전 기준이다.
- 변경 근거: 서로 다른 smsId인 SMS와 금융앱 알림의 중복 조회/저장이 분리되어 동시 수신 시 두 행이 저장될 수 있었다.
- 자동 수집 지출의 중복 판정과 저장을 `ExpenseDao.insertIngested()` transaction으로 통합했다. 기존 행 갱신/스킵에는 새 거래 알림을 보내지 않는다.
- MainViewModel의 저장/중복 보정 정책을 `SmsIngestionWriter`로 이동하여 백그라운드 재처리도 같은 정책과 삭제 epoch를 사용한다.
- 교차 타입 삭제+대체 저장을 transaction으로 묶고, 실제 지출 저장 결과 집계와 `handledSmsIds`로 신규/보정/정상 중복/미파싱을 구분한다.
- 사용자 입력/복원 API와 DB schema는 유지했다. DAO의 정상 동액 반복, 다른 카드, 일반 입금과 취소 수입은 별도 회귀 항목으로 두었다. 기존 Writer의 60초 원문 fuzzy 정책은 유지했다.
- 갱신 KB: `01-purpose-architecture.md`, `05-file-inventory.md`.
- 검증: Android 16 `MoneyTalk_Sms_QA_API_36`에서 `ExpenseIngestionInstrumentedTest` 16개 통과. SMS/앱 알림 동시 저장 30회 반복, 사용자 설정, 실제 건수/handled 결과와 양방향 교차 타입 삽입 실패 rollback을 포함한다. 앱/AndroidTest APK 빌드 성공, JVM 288개 통과. 실제 수신 경로처럼 정규화된 카드명으로 fixture를 구성했다.

## 2026-07-03

- 기준: `core/database`, `feature/home/data`, `docs/moneytalk-kb/project-context/01-system-overview.md` 확인
- 변경 근거: MoneyTalk KB 초기 생성
- 갱신한 KB:
  - `README.md`
  - `00-structure-map.md`
  - `01-purpose-architecture.md`
  - `02-how-to-use.md`
  - `03-extension-points.md`
  - `04-files-checklist.md`
  - `05-file-inventory.md`
- 다음 검증:
  - DB column 추가 작업에서 migration/DAO/Repository/화면 영향 파일을 정확히 찾는지 확인
