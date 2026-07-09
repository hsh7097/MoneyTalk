---
type: feature-flow
title: Notification Ingestion feature flow
description: 외부 알림 접근, 메시지 provider 재조회, 금융앱 후보 탐색, 앱 알림 거래 저장 흐름을 설명한다.
tags: [moneytalk, notification, ingestion, feature-flow]
resource: app/src/main/java/com/sanha/moneytalk/receiver/NotificationTransactionService.kt
timestamp: 2026-07-09T02:02:36+09:00
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

지원 앱 판단은 세 단계다.

| 단계 | 기준 | 파일 |
|---|---|---|
| 내장 목록 | 패키지명이 `FinancialAppPackageRegistry`에 있음 | `FinancialAppPackageRegistry.kt` |
| 원격 승인 목록 | Firebase RTDB approved app cache | `RemoteFinancialAppRepository.kt`, `FinancialAppDiscoveryRepository.kt` |
| 후보 분석 | 알 수 없는 금융 후보를 LLM으로 분석하고 cache/report | `FinancialAppLlmAnalyzer.kt`, `FinancialAppCandidateEntity.kt` |

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
