---
type: architecture
title: Finance Data Purpose Architecture
description: Finance Data의 목적, 책임, 의존성 방향을 설명한다.
tags: [moneytalk, finance-data, architecture]
resource: app/src/main/java/com/sanha/moneytalk/core/database/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# 01 Purpose Architecture

Finance Data는 로컬 Room DB와 이를 감싸는 Repository 계층이다.
화면 ViewModel은 DAO를 직접 다루기보다 Repository 또는 service를 통해 데이터를 읽고 쓴다.

## 의존성 방향

```text
Feature ViewModel
→ Repository / Service
→ DAO
→ Entity
→ AppDatabase
```

## 책임

- `AppDatabase`: 모든 entity/DAO 등록과 migration 정의
- `dao`: DB query와 transaction 단위
- `entity`: 저장 모델과 DB column 정의
- `feature/home/data`: 앱 기능에서 사용하는 repository와 분류 service
- `core/database/*Repository`: 카드, SMS 제외, 크레딧처럼 여러 도메인에서 쓰는 DB-backed repository

DB schema가 바뀌면 migration과 영향 repository를 같이 본다.

## 자동 수집 지출 저장

- SMS 즉시 저장과 금융앱 알림은 `ExpenseRepository.insertIngested()`를 사용한다. 배치 동기화와 백그라운드 재처리는 `SmsIngestionWriter.write()`에서 같은 API로 연결한다.
- `ExpenseDao.insertIngested()`의 Room transaction 안에서 정확한 smsId/기존 id 조회, 동일 SMS 재전달 및 SMS·앱 알림 교차 중복 판정과 insert를 완료한다. 호출자가 조회 후 별도로 insert하면 두 수신 경로가 동시에 통과할 수 있다.
- 같은 smsId를 다시 수신하면 새 알림을 보내지 않는다. 배치의 `reconcileExisting=true`는 기존 id, 메모, 통계 제외, 생성 시각을 보존하며 파싱 결과를 갱신한다.
- 교차 소스 중복이면 SMS를 정본으로 유지한다. 먼저 저장된 앱 알림 행을 SMS로 바꿀 때 id와 메모/고정/통계 제외 값을 보존하고, 이미 SMS가 있으면 앱 알림을 스킵한다.
- 배치 저장은 각 행의 `INSERTED/UPDATED/SKIPPED` 결과를 반환한다. Writer는 실제 삽입만 신규 건수로 세고, SMS가 앱 알림을 대체한 경우 보정 건수로 센다.
- SMS·앱 알림 교차 중복은 기존 `TransactionSemanticDedupe`의 금액·카드·거래처·카드 끝자리·거래시각 60초 기준이다.
- 일반 SMS끼리는 `isSameSmsRedelivery()`로 발신자와 원문 전체, 금액·카드·상호·거래시각·거래유형·이체 방향이 모두 같을 때만 재전달을 판정한다. 원문에 `잔액`, `잔고`, `누적` 뒤 숫자 금액이 있어야 하며 `원` 단위 생략과 콜론은 허용한다. 두 smsId가 해당 발신자·본문 해시의 표준 형식이고, ID에 담긴 **수신시각** 차이가 60초 이내여야 한다. 거래시각이 같아도 수신 간격이 60초를 넘거나 잔액 등 원문이 다르면 이 규칙은 합치지 않는다. 잔액 근거가 없는 같은 분의 동일 금액·상호 결제는 별도 거래로 보존한다.
- 동일 SMS 재전달은 `SKIPPED`로 기존 행과 사용자 메타데이터를 보존한다. 이미 저장된 과거 중복 행을 자동으로 삭제하는 작업은 아니다.
- 수동 입력과 백업 복원은 기존 `insert()/insertAll()`을 유지한다. Room entity와 schema version은 변경하지 않는다.

## 화면 밖 SMS 저장

`SmsIngestionWriter`는 MainViewModel에서 사용하던 정확/fuzzy snapshot, 사용자 삭제 목록, StoreRule, 사전 카테고리 분류, 수입 복원/환불·교차 타입 보정 정책을 공유한다. UI 상태 대신 `SaveProgress`를 전달한다.

동일 smsId는 기존처럼 정확히 비교한다. ID가 다른 fuzzy 비교는 입력 필터, 현재 배치, DB 저장 보정, pending 완료에서 모두 같은 원문 전체와 숫자 잔액 근거를 요구한다. pending 후보도 DB snapshot의 원문으로 검증하며 원문을 조회하지 못한 후보는 정확한 ID로만 처리한다. 본문 해시는 검색 인덱스이고 중복 판정의 충분조건이 아니다.

호출자는 `ClassificationState`에 Job을 등록한 동안 `registrationEpoch`를 전달해야 한다. Writer는 실행 시작과 DB 쓰기 전 현재 coroutine과 epoch를 확인한다. 전체 삭제로 취소된 요청은 새 내역을 저장할 수 없다. 분류 cache 초기화/flush/clear와 화면 refresh는 호출자 책임이다.

교차 타입과 중복 수입의 원본 삭제·대체 저장은 같은 Room transaction에 포함한다. 저장 실패나 취소로 transaction이 완료되지 않으면 기존 행도 보존된다. 파싱/AI 분류는 이 transaction 밖에서 수행한다.

`WriteResult.handledSmsIds`는 실제 저장 또는 명확한 중복 판정까지 끝난 입력 ID를 반환한다. 백그라운드 호출자는 이 집합으로 중복 스킵과 미파싱을 구분해 불필요한 LLM 재시도를 막는다. 금액을 추출하지 못한 수입은 이 집합에 포함하지 않는다.

실제 Room 회귀 검증은 `app/src/androidTest/java/com/sanha/moneytalk/core/database/ExpenseIngestionInstrumentedTest.kt`에서 동시 SMS/앱 알림, 배치/즉시 수신, 원문 재전달, 사용자 설정 보존, 일반 입금과 취소 수입, 삭제 epoch, 양방향 교차 타입 저장 실패 rollback 및 handled 결과를 확인한다. 동일 SMS의 수신시각만 2초 다른 동시 저장을 30회 반복하고, 잔액이 다른 동일 분 거래, 수신 60초 경계, 재전달 시 사용자 메타데이터 보존을 검증한다. 잔액이 없는 동일 본문 두 건은 실제 분 단위 날짜 파서로 같은 거래시각을 만들고 수신시각만 20~39초 달리하여 DAO, writer의 현재 배치·기존 DB·pending 완료에서 별도로 보존하는지 확인한다. 잔액 기반 판정도 provider 고유 ID를 대체하는 절대적 거래 식별자는 아니다.
