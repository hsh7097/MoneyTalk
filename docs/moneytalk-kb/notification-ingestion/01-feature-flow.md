---
type: feature-flow
title: Notification Ingestion feature flow
description: 외부 알림 접근, 메시지 provider 재조회, 금융앱 후보 탐색, 앱 알림 거래 저장 흐름을 설명한다.
tags: [moneytalk, notification, ingestion, feature-flow]
resource: app/src/main/java/com/sanha/moneytalk/receiver/NotificationTransactionService.kt
timestamp: 2026-09-07T22:00:00+09:00
status: draft
---

# Notification Ingestion feature flow

Notification Ingestion은 외부 알림을 읽어 거래 후보를 찾는 입력 경로다. MoneyTalk이 사용자에게 표시하는 자체 거래 알림은 [notification-display/README.md](../notification-display/README.md)를 본다.

## 전체 흐름

```text
SettingsScreen 알림 접근 권한 row
-> NotificationAccessHelper.openNotificationListenerSettings()
-> NotificationTransactionService.onNotificationPosted()
-> NotificationContentParser.parse()
-> looksLikeFinancialMessage()
-> 금융앱 알림 or 메시지앱 알림 분기
-> SmsInstantProcessor
-> DataRefreshEvent.TRANSACTION_ADDED or SMS_RECEIVED
```

## 메시지 앱 알림

메시지 앱 알림은 provider row를 찾기 위한 힌트로 쓴다.

```text
NotificationContentParser.isMessagePackage()
-> awaitRecentProviderMessage()
-> SMS/MMS/RCS provider를 500ms, 1500ms, 3000ms 지연 재조회
-> processProviderMessage()
-> SmsInstantProcessor.processAndSave()
```

| provider | channel | 실패 시 |
|---|---|---|
| `content://sms/inbox` | `sms_inbox` | SMS는 `SmsReceiver`가 주 경로이므로 추가 batch 강제 없음 |
| `content://mms/inbox` | `mms_inbox` | 즉시 처리 실패/스킵 시 `SMS_RECEIVED` refresh로 batch 보강 |
| `content://im/chat` | `rcs_im_chat` | 즉시 처리 실패/스킵 시 `SMS_RECEIVED` refresh로 batch 보강 |

## 금융앱 알림

금융앱 알림은 SMS provider row가 없으므로 알림 본문 자체를 파싱한다.

```text
FinancialAppDiscoveryRepository.isSupportedFinancialApp()
-> SmsInstantProcessor.processAppNotificationAndSave()
-> Regex rule match 우선
-> AppNotificationTransactionParser fallback
-> Expense/Income 저장
```

`AppNotificationTransactionParser`는 줄 시작의 명시적인 `가맹점:` 값을 금액 주변/계좌 대상/잔액 주변 상호 추정보다 먼저 사용한다. 콜론 앞뒤 공백과 전각 콜론을 허용하며 한 글자 상호도 기존 후보 검증을 거쳐 사용한다. 후보가 정확히 `금액`, `일시`, `가맹점`인 필드 이름이면 제외하되, 그 단어를 포함한 정상 상호까지 제외하지 않는다. 유효한 상호가 없으면 기존 앱 이름 fallback을 유지한다. 이 변경은 새 앱 알림의 fallback 파싱에 적용하며 기존 저장 거래를 다시 쓰지 않는다.

`AppNotificationTransactionParserTest`의 합성 SSGPAY 다중행 알림은 가맹점 필드 우선, 전각 콜론/짧은 상호, 빈 필드 이름 제외, `금액`을 포함한 정상 상호 보존을 검증한다. 기존 계좌 대상/누적 금액 뒤 상호/잔액 금액 제외/비거래 알림 테스트도 함께 실행한다.

지원 앱 판단은 세 단계다.

| 단계 | 기준 | 파일 |
|---|---|---|
| 내장 목록 | 패키지명이 `FinancialAppPackageRegistry`에 있음 | `FinancialAppPackageRegistry.kt` |
| 원격 승인 목록 | Firebase RTDB approved app cache | `RemoteFinancialAppRepository.kt`, `FinancialAppDiscoveryRepository.kt` |
| 후보 분석 | 알 수 없는 금융 후보를 LLM으로 분석하고 cache/report | `FinancialAppLlmAnalyzer.kt`, `FinancialAppCandidateEntity.kt` |

## MMS/RCS provider 변경과 본문 지연 저장

`MmsContentObserver`와 `RcsContentObserver`는 `ProviderChangeQueue`로 provider 변경을 직렬 처리한다. 현재 조회/파싱 중 도착한 변경은 버리지 않고 후속 조회 1회로 합친다. 한 문자에 여러 변경이 발생해도 대기 조회를 무한히 쌓지 않으며, 조회 시작 뒤 추가된 문자나 뒤늦게 완성된 본문을 다음 조회에서 다시 읽는다.

`ProviderBodyReader`는 최초 1회 읽은 뒤 각 backoff가 끝날 때마다 다시 읽는다. MMS는 `500, 1500, 3000ms`, RCS는 `300, 1000, 2500ms` 간격으로 총 최대 4회 조회하므로 마지막 대기 중 완성된 본문도 처리한다. 끝까지 본문을 읽지 못하면 observer의 처리 ID를 제거해 다음 변경에서 재시도한다.

이 큐는 앱 프로세스 내 observer용이다. 프로세스가 종료된 동안의 수신 복구나 OS의 SMS 자체 배달 지연을 보장하지 않는다. 실제 수신 지연을 조사할 때는 provider 수신 시각, 앱 감지 시각, DB 저장 시각, 본문에서 해석한 거래 시각을 구분한다.

회귀 테스트는 `ProviderChangeQueueTest`의 처리 중 새 문자 도착/연속 변경 병합과 `ProviderBodyReaderTest`의 마지막 backoff에서 본문 준비/즉시 준비/미준비를 사용한다.

## 금융 문자 로컬 미매칭의 백그라운드 재처리

`SmsInstantProcessor.Result.Deferred`는 사전 필터를 통과한 금융 PAYMENT 후보의 로컬 regex 미매칭이다. SMS broadcast, MMS/RCS observer, 메시지 앱 알림 provider 경로는 이 결과만 `SmsFallbackScheduler`에 영속 예약한다. 일반 `Skipped`(비거래·사용자 제외·삭제·중복)와 `Error`는 일괄 예약하지 않는다.

```text
local Deferred
-> SmsFallbackQueue (noBackupFilesDir, 원자 교체 저장)
-> persisted JobScheduler job (network ANY)
-> SmsFallbackProcessor / ClassificationState 삭제 gate
-> SmsSyncCoordinator.process(단건)
-> SmsIngestionWriter.write(기존 화면과 동일 저장 정책)
-> DB 확인 / 큐 제거 / TRANSACTION_ADDED
```

Job에 문자 원문이나 발신번호를 extras로 넣지 않는다. 파일 큐는 입력 시각·예약 시각·시도 횟수·삭제 세대를 보존하며, 프로세스 종료 전 저장된 후보는 재실행할 수 있다. 성공 또는 확정 비거래는 큐에서 제거한다. 미파싱/예외는 OS backoff로 초기 시도 포함 최대 3번의 파이프라인 실행을 허용한다. 각 실행 안의 기존 모델 재시도 정책과는 별개다. 시도 횟수는 파이프라인 시작 직전에 증가하므로 OS 중단·화면 동기화의 선점·프로세스 종료도 시작한 시도를 소모한다. 이미 시작된 API의 과금 여부를 클라이언트가 확정할 수 없어서 취소 시 횟수를 반환하지 않는다. 분류 소유권을 얻지 못한 실행은 시도를 소모하지 않는다. 마지막 시도가 시작되면 원문·발신번호를 디스크에서 제거하고 smsId의 SHA-256 해시만 소진 목록에 보존한다. 소진 항목은 반복 수신 이벤트로 시도 횟수가 초기화되지 않으며 전체 데이터 삭제 시 제거된다. 마지막 시도 중 프로세스가 종료되어도 자동 실행 횟수는 늘리지 않는다.

같은 입력의 반복 수신은 시도 횟수를 초기화하지 않는다. 실행 중 새 후보는 현재 작업 종료 시 잔여 큐를 보고 다시 예약하며, 신규 입력 없이 주기적으로 SMS함을 조회하지 않는다. `RECEIVE_BOOT_COMPLETED` 권한과 persisted Job으로 재부팅 후 예약을 유지하고, Application 시작에서는 큐가 있으나 OS 예약이 없을 때만 복원한다. 실제 실행 시점은 네트워크·Doze 등 OS 제한을 받으므로 즉시 실행을 보장하지 않는다.

전체 데이터 삭제는 `ClassificationState.withRegistrationsPaused` 안에서 `SmsFallbackScheduler.clearPending()`을 호출해 실행 작업 취소, 큐 삭제와 세대 증가, OS 예약 취소를 함께 수행해야 한다. 삭제 이전 요청 토큰과 이미 사용자가 삭제한 SMS ID는 다시 예약하지 않는다. Job은 일반 화면 동기화와 같은 삭제 gate에 등록하며 화면의 sync watermark/월별 coverage를 변경하지 않는다.

`SmsFallbackQueueTest`는 재생성 후 복구, 유한 시도, 반복 이벤트, 삭제 전후 세대, 성공·소진 후 원문 제거와 중단된 파일 교체를 검증한다. 완료 처리를 하지 않은 채 시도를 시작하고 큐를 재생성하는 테스트는 중단 후에도 횟수가 반환되지 않는 경계를 검증한다. 실제 OS의 Job 중단/재시작, UI 없는 DB 저장, 재부팅 스케줄 복원은 별도의 기기 검증 대상이다. `JobService.onStopJob`은 Binder에서 전달되는 `JobParameters` 객체 동일성을 가정하지 않고 jobId로 활성 작업을 취소하며, 내부 실행 세대로 이전 작업의 finally가 새 작업을 완료시키지 못하게 한다.

## 중복/재처리 보호

| 보호 장치 | 기준 |
|---|---|
| 알림 중복 처리 | `processedNotifications` + `DEDUP_TTL_MS` |
| 동일 smsId 동시 처리 | `SmsInstantProcessor.inFlightSmsIds` |
| SMS와 앱 알림 교차 중복 | `TransactionSemanticDedupe`와 app-generated smsId 판정 |
| listener 재연결 시 기존 active notification | `processActiveNotifications(showUserNotification = false)`로 저장만 보강 |

## 변경 시 체크

1. 외부 알림 수신 기능을 고칠 때는 `notification-ingestion`을 보고, MoneyTalk 자체 노티 표시는 `notification-display`를 함께 보는가?
2. 메시지 앱 알림에서 provider row를 못 찾은 경우 batch 보강 정책이 채널별로 맞는가?
3. 금융앱 후보 처리에서 내장 목록, RTDB 승인 목록, LLM 후보 cache 중 어느 단계가 바뀌는지 구분했는가?
4. listener 재연결 active notification은 사용자 노티를 다시 띄우지 않는가?
