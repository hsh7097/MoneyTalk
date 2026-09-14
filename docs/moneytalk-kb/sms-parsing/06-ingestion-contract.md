---
type: reference
title: SMS Ingestion Contract
description: SMS/MMS/RCS 읽기, 중복 제거, 파싱, 수입/지출 저장, 실시간 보완 경계를 설명한다.
tags: [moneytalk, sms, parsing, ingestion, sync]
resource: app/src/main/java/com/sanha/moneytalk/core/sms/
timestamp: 2026-07-11T00:00:00+09:00
status: draft
---

# SMS Ingestion Contract

> 기준: 흡수된 SMS 파싱 원문과 project-context SMS 파트를 KB용으로 재작성

이 문서는 SMS/MMS/RCS 원본이 앱의 지출/수입 거래로 저장되기까지의 end-to-end 계약을 설명한다. 내부 regex/vector/LLM 단계의 자세한 구현은 [../sms-pipeline/README.md](../sms-pipeline/README.md)를 본다.

## 전체 흐름

```text
trigger
-> SmsSyncRangeCalculator
-> SmsSyncMessageReader / SmsReaderV2
-> 중복 제거, 발신자 필터
-> SmsSyncCoordinator.process()
   -> SmsPreFilter
   -> SmsIncomeFilter
   -> SmsRegexRuleMatcher
   -> SmsPipeline
-> ExpenseEntity / IncomeEntity 저장
-> SyncCoverageRecorder
-> DataRefreshEvent
```

배치 저장 정책은 `SmsIngestionWriter`가 담당하며 화면 동기화와 백그라운드 재처리가 같은 경로를 사용한다. 즉시 처리에서 확정하지 못한 금융 후보(`Deferred`)는 아래 실시간 보완 경로로 넘긴다.

## 메시지 읽기

| 유형 | URI | 날짜 단위 | 비고 |
|---|---|---|---|
| SMS | `content://sms/inbox` | millisecond | 표준 문자 |
| MMS | `content://mms/inbox` | second | 읽은 뒤 millisecond로 보정 |
| RCS | `content://im/chat` | millisecond | 삼성 기기 provider, JSON 본문 |

`SmsReaderV2`는 기존 중간 모델 대신 `SmsInput`을 직접 반환한다. SMS provider 조회 실패를 빈 목록으로 취급하지 않고 실패로 전파해 `lastSyncTime`을 잘못 갱신하지 않도록 한다.

SMS ID는 `발신번호_수신시간_본문해시코드` 조합으로 만들고, 기존 지출/수입 `smsId`와 pending 즉시 저장 상태를 기준으로 중복 저장을 막는다.

수신시각만 다른 동일 원문을 기존 숫자 잔액·잔고·누적 및 60초 기준으로 중복 판정하면, 입력 필터에서 스킵되는 경우와 지출/수입 정본의 smsId를 보정하는 경우 모두 `DeletedSmsTracker`에 두 출처 ID를 연결한다. 이후 사용자가 거래를 삭제하면 앱 재시작 후에도 이미 확인된 두 출처의 재수집을 차단한다. 원문 근거 없이 같은 Room 행 ID를 지정한 경우에는 연결하지 않는다.

## 발신자/본문 필터

| 단계 | 책임 |
|---|---|
| `SmsFilter.normalizeAddress()` | `+82`, 하이픈, 공백 제거 |
| `SmsFilter.shouldSkipBySender()` | 010/070 발신자라도 금융 힌트가 없으면 제외 |
| `SmsPreFilter` | 인증, 보안, 광고, 배송, 통신 단가 안내, 교통/KSNET/매출접수 N건 요약, 과도하게 긴 본문 등 비결제 제거 |
| 사용자 제외 키워드 | 설정의 SMS 제외 키워드를 `SmsIncomeFilter`에 전달 |

010/070 필터는 금융 힌트가 있으면 보존한다. 발신번호만 보고 결제 가능성을 완전히 버리면 실사용 문자 누락이 생길 수 있다.

## 수입/지출 분기

`SmsIncomeFilter`가 금융기관 키워드와 금액 패턴을 기준으로 `PAYMENT`, `INCOME`, `SKIP`을 분류한다.

| 분기 | 이후 처리 |
|---|---|
| PAYMENT | 완료된 카드대금 출금을 포함한다. `SmsRegexRuleMatcher` Fast Path 후 미매칭만 `SmsPipeline`으로 보낸다. |
| INCOME | 입금과 취소/환불을 포함한다. Fast Path 대상이 아니며 `SmsIncomeParser`와 `SmsTransactionDateResolver`를 통해 `IncomeEntity`로 저장한다. |
| SKIP | 예정/명세서/요약 등 비거래이므로 저장하지 않는다. |

## 결제 파싱 3-tier

| Tier | 책임 | 비용 |
|---|---|---|
| Regex Fast Path | sender 기반 asset seed + RTDB overlay regex 룰 매칭 | 무료 |
| Vector | 템플릿 embedding과 `SmsPatternEntity` 유사도 매칭 | embedding |
| Gemini LLM | 신규/불안정 패턴 batch 추출, regex 생성, 패턴 등록 | Gemini |

Fast Path 허용 타입은 `expense`, `overseas`, `payment`, `debit`다. `income`, `cancel` 룰은 새로 추가하지 않는다. 기존 cancel asset 노드는 로더에서 제외되는 dormant 데이터다.

## 정산/요약/취소 계약

| 본문 유형 | 분기 | 저장 | 통계 |
|---|---|---|---|
| 카드대금/결제대금/이용대금/이용금액 실제 출금·인출 완료 + 원화 금액 | PAYMENT | `ExpenseEntity` 저장 | `StatsExclusionClassifier`로 제외 |
| 카드대금 예정, 명세서, 청구서, 결제일 안내 | SKIP | 저장 안 함 | 해당 없음 |
| 교통카드/하이패스 N건, KSNET 마이장부 N건, 매출접수 N건 집계 | SKIP | 저장 안 함 | 해당 없음 |
| 승인/결제/사용 금액 0원 | SKIP | 저장 안 함 | 해당 없음 |
| 쇼핑 금액과 입금계좌만 있고 완료 근거 없음 | SKIP | 저장 안 함 | 해당 없음 |
| 승인/결제/출금 취소, 환불 | INCOME | `IncomeEntity` 저장 | 수입 경로 기준 |

요약/0원/미완료 입금 안내 패턴은 `SmsPreFilter`, `SmsIncomeFilter`, 앱 알림 분류기에서 먼저 차단한다. 해당 단계가 우회되어도 LLM 프롬프트가 같은 정책으로 방어한다. 완료된 카드대금 출금은 `결제대금 중 실제 출금액`처럼 금액이 여러 개일 수 있으므로 최종 출금액 캡처와 통계 제외를 함께 검증한다.

## 날짜/시간 해석

`SmsTransactionDateResolver`가 SMS 본문의 거래 날짜/시간을 공통 해석한다. 본문에 full date가 있으면 우선하고, 연도 없는 `MM/DD`는 SMS 수신일과 가장 가까운 연도로 보정한다.

## 실시간 보완 경로

| 진입점 | 역할 |
|---|---|
| `SmsReceiver` | SMS broadcast를 `goAsync`로 받아 실시간 1건 처리 |
| `MmsContentObserver` | MMS provider 실시간 감시 |
| `RcsContentObserver` | RCS provider 실시간 감시 |
| `NotificationTransactionService` | 메시지 앱 알림에서 후보를 추출하고 provider 원문으로 보강 |
| `NotificationContentParser` | 알림 텍스트에서 거래 후보 추출 |

실시간 수신은 광고/크레딧과 무관하게 항상 무료로 동작해야 한다. 알림 기반 보완은 앱 자체 노티 표시와 별개로 메시지 앱 알림을 관찰해 SMS provider 누락을 줄이는 보조 경로다.

### 미확정 후보의 백그라운드 재처리 (2026-09-07)

- `SmsInstantProcessor.Result.Deferred`만 `SmsFallbackScheduler`에 넣는다. 비거래·삭제·이미 저장한 문자를 뜻하는 `Skipped`와 구분한다.
- `SmsFallbackQueue`는 앱 전용 no-backup 파일에 후보와 시도 횟수를 보존한다. 네트워크가 연결되면 `SmsFallbackJobService`가 화면 생명주기와 독립적으로 `SmsSyncCoordinator -> SmsIngestionWriter`를 실행한다.
- 같은 SMS ID의 반복 수신은 큐를 늘리지 않으며 최초 포함 최대 3회 파이프라인 실행을 시작한다(모델 내부 재시도와 별도, 시작 후 중단도 횟수 소모). 완료한 원문은 제거하고, 시도 소진 시 원문 없이 ID 해시만 남겨 반복 이벤트가 시도 한도를 초기화하지 못하게 한다.
- `ClassificationState` 소유권으로 화면 동기화와 카테고리 캐시 사용을 직렬화한다. 전체 삭제는 진행 작업 종료를 기다린 뒤 큐 세대를 바꾸고 예약 Job도 취소한다.
- JobScheduler의 네트워크·절전 정책은 적용된다. 모든 기기에서 즉시 실행되거나 강제 종료 상태에서도 수신된다는 보장은 아니다.
- MMS/RCS는 `ProviderChangeQueue`가 처리 도중 추가된 변경 신호를 보존해 한 차례 더 조회한다. `ProviderBodyReader`는 마지막 지연 이후에도 실제 본문을 다시 읽는다.

### 자동 지출 저장의 원자성

`ExpenseDao`의 자동 수집 전용 트랜잭션에서 정확한 SMS ID 조회, 동일 SMS 재전달과 SMS/앱 알림 교차 출처 확인, 정본 갱신 또는 삽입을 함께 수행한다. 즉시 처리와 배치 처리가 사전 조회를 동시에 통과해도 저장 시점에 재확인한다. 수동 입력과 백업 복원 API는 이 경로로 바꾸지 않는다.

ID가 다른 SMS의 fuzzy 비교는 같은 발신자·원문 전체와 숫자 잔액 근거(`잔액`, `잔고`, `누적` 뒤 금액)를 요구한다. 금액 뒤 `원` 생략과 콜론은 허용한다. DAO에서는 금액·카드·상호·거래시각·거래유형·방향도 모두 같고, 표준 smsId의 수신시각 차이가 60초 이내여야 `SKIPPED`로 처리한다. writer의 현재 배치/DB/pending 필터, 저장 보정과 pending 완료에도 같은 원문 근거를 사용한다. pending 원문은 DB snapshot에서 가져오며 원문이 없으면 정확한 ID만 비교한다.

분 단위 시각·상호·금액만 같은 정상 반복 결제는 본문이 같을 수 있으므로 잔액 근거가 없는 ID는 합치지 않는다. 원문의 잔액이 다른 거래도 보존한다. 실기기에서 확인한 재전달 16쌍은 모두 숫자 잔액을 포함해 이 보수적 조건에 해당했다. 잔액 근거 역시 provider 고유 ID처럼 완전한 거래 식별자는 아니며, 불확실한 중복을 덜 제거하더라도 정상 거래를 남기는 방향이다.

기존 중복 행의 일괄 삭제와 수동 중복 정리 기준은 별도 경로이며 이번 자동 수집 보완에서 변경하지 않는다. Room은 실제 분 단위 날짜 해석을 사용한 잔액 없는 동일 본문 2건을 DAO/배치/기존 DB/pending 경로에서 보존하고, 잔액이 같은 재전달은 계속 보정하는지 확인한다.

### 환불 수입 보정의 행 ID 보존

환불 안내 뒤 실제 입금 SMS를 정본으로 선택할 때 `SmsInstantProcessor`와 `SmsIngestionWriter`는 기존 수입 ID를 유지한다. 보유 중인 알림의 상세 대상이 삭제되지 않도록, 복원/정확 일치 행을 우선하고 없으면 교체할 환불 행의 ID·메모·생성시각·반복일을 사용한다. 다른 환불 중복 행만 제거하며 새로 보존한 ID는 삭제하지 않는다.

같은 배치에서 이미 정본으로 바꾼 ID를 뒤늦은 안내 원문으로 되돌리지 않는다. 아직 저장되지 않은 안내/입금 두 후보는 기존처럼 한 건만 신규 저장한다. 보정은 새 거래 알림을 추가로 발생시키지 않는다.

회귀 검증은 즉시 저장과 배치 저장의 ID/사용자 메타데이터 보존, 복원 행 자체의 잘못된 삭제 방지, 별도 안내 행 제거, 배치 입력 순서와 단일 신규 저장을 포함한다.

### 정본 교체 뒤 삭제한 거래의 다른 출처 재수집 방지

앱 알림 A가 SMS 정본 B로 바뀌면 거래 행 ID는 유지되지만 `smsId`는 달라진다. `DeletedSmsTracker`는 기존 SharedPreferences에 확인된 출처 ID 연결을 보존하고, 사용자가 B를 삭제하면 연결된 A도 삭제 대상으로 기록한다. SMS가 먼저 저장되어 앱 알림을 스킵한 경우에도 연결한다. 금액이나 행 ID만 같다는 이유로 연결하지 않으며, `ExpenseDao`의 기존 교차 출처/잔액 근거 재전달 판정과 즉시·배치 수입에서 실제로 대체·스킵하는 환불 출처만 연결한다. 환불 의미 중복 후보가 있더라도 현재 SMS ID 행이나 복원 행을 갱신하면서 다른 후보 행을 유지하는 분기에서는 연결하지 않는다. 임의의 같은 행 `smsId` 변경과 수동 입력/백업 삽입도 연결하지 않는다.

연결·삭제·초기화·전체 삭제는 같은 monitor로 직렬화한다. 삭제 ID와 출처 연결은 각각 독립 Set 스냅샷으로 만들고 같은 prefs editor에서 `apply()`한다. 병렬 삭제에서 예전 스냅샷이 나중에 저장되어 삭제 기록을 잃지 않도록, 스냅샷 생성과 `apply()` 호출까지 잠금 범위에 포함한다. 전체 데이터 삭제는 두 종류의 기록을 함께 초기화한다. 이미 삭제된 ID에 새로 확인된 출처가 연결되면 그 출처도 삭제 대상으로 전파한다.

출처 연결은 동일성 판정의 기록이며 DB 쓰기 성공이나 사용자 삭제를 의미하지 않는다. 따라서 Room 트랜잭션이 rollback되어도 확인된 연결은 prefs에 남을 수 있지만, 그것만으로 어느 거래도 삭제 대상으로 처리하지 않는다. 사용자 삭제 성공 뒤 `markDeleted`하는 기존 순서는 유지한다. 자동 지출 DAO와 배치 수입 저장은 트랜잭션 내부에서 삭제 여부를 다시 확인한다. 즉시 수입도 `IncomeDao.insertIngested`의 트랜잭션에서 DB 쓰기 차례를 얻은 뒤 삭제 기록을 확인하므로, 파싱을 마친 뒤 DB를 기다리는 동안 삭제된 수입을 삽입하지 않는다. Room commit과 prefs 저장은 하나의 원자적 트랜잭션이 아니며 그 사이의 프로세스 중단·극히 짧은 동시 저장 창을 완전히 제거한 것은 아니다. 배포 전에 이미 소실된 옛 출처 ID는 자동 복구하지 못한다. 다시 관찰되어 현재 정본과 중복 판정된 출처부터 연결된다.

`TransactionSourceDeletionInstrumentedTest`는 앱/SMS 양방향 수신 순서, 삭제 후 즉시·배치 DAO 재수집, prefs 재초기화, 연쇄 연결/전체 초기화, 다른 카드 거래 보존, 임의 행 ID 변경 제외, DB rollback, 병렬 삭제 스냅샷 순서, DB 쓰기 대기 중 수입 삭제 기록 재확인을 검증한다. `ExpenseIngestionInstrumentedTest`의 추가 환불 회귀는 즉시·배치 정본 교체와 중복 스킵 후 두 출처가 다시 저장되지 않는지, 별도 환불 행을 유지하는 분기에서 삭제 기록이 합쳐지지 않는지, 최종 저장 직전 실제 단건 삭제 서비스가 실행되어도 수입이 복원되지 않는지 확인한다. 테스트는 별도 prefs와 메모리 DB를 사용하며 검증 후 앱의 원래 tracker 저장소로 돌아간다.

## Coverage와 CTA

월별 coverage는 `SyncCoverageRecorder`가 성공한 동기화 구간을 기록하고, `SyncCoveragePagePolicy`가 과거 월 CTA 표시 여부를 판단한다. 이미 coverage가 충분한 월은 같은 월을 다시 광고/크레딧으로 해제할 필요가 없다.

## 회귀 검증 기준

1. 월별 읽기 순서가 달라도 저장 대상 SMS ID 집합이 같아야 한다.
2. 거래월별 PAYMENT/INCOME 집계가 같아야 한다.
3. 수신월과 거래월이 다른 환불/취소 케이스를 포함해야 한다.
4. SMS provider 실패 시 `lastSyncTime`이 갱신되지 않아야 한다.
5. 실시간 저장과 배치 동기화가 같은 SMS를 중복 저장하지 않아야 한다.
6. 완료 카드대금 출금은 저장되지만 통계에서 제외되어야 한다.
7. 교통/KSNET/매출접수 N건 요약은 batch와 실시간 앱 알림 경로 모두에서 SKIP되어야 한다.
8. 기존 cancel asset 룰이 있어도 `SmsRegexRuleMatcher`에서 지출로 파싱되지 않아야 한다.
9. 앱 알림과 SMS, 즉시 처리와 배치 저장을 동시에 실행해도 동일 거래는 한 건만 남아야 한다.
10. 마지막 본문 재시도 직전 준비된 MMS/RCS와 처리 도중 들어온 변경 신호를 놓치지 않아야 한다.
11. 큐 재생성으로 시도 횟수가 초기화되지 않고, 전체 삭제 전 후보가 나중에 복원되지 않아야 한다.
