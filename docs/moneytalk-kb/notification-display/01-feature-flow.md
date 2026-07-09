---
type: feature-flow
title: Notification Display feature flow
description: MoneyTalk 자체 거래 알림의 채널 생성, 표시 조건, 스킵 조건, 정리 시점을 설명한다.
tags: [moneytalk, notification, display, feature-flow]
resource: app/src/main/java/com/sanha/moneytalk/core/notification/SmsNotificationManager.kt
timestamp: 2026-07-09T02:02:36+09:00
status: draft
---

# Notification Display feature flow

## 채널 생성

```text
MoneyTalkApplication.onCreate()
-> SmsNotificationManager.createNotificationChannel()
-> old channel sms_transaction 삭제
-> channel sms_transaction_v2 생성
```

`sms_transaction_v2`는 `NotificationManager.IMPORTANCE_HIGH`와 `NotificationCompat.PRIORITY_HIGH`를 사용한다. importance는 기존 채널에서 코드로 바꿀 수 없기 때문에 old channel을 삭제하고 v2 채널을 만든다.

## 지출 알림 표시

```text
SmsReceiver / NotificationTransactionService / RCS/MMS observer
-> SmsInstantProcessor.processAndSave() 또는 processAppNotificationAndSave()
-> ExpenseEntity 저장 성공
-> 중복 치환 여부 확인
-> shouldShowExpenseNotification()
   -> SettingsDataStore.isNotificationEnabled()
   -> OwnedCardRepository.getExcludedCardNames()
   -> CardVisibilityFilter.shouldShowExpenseNotification()
-> SmsNotificationManager.showExpenseNotification()
```

지출 알림 title은 `storeName`, body는 `amount` 형식이다. `cardName`은 표시 문구에는 직접 쓰이지 않지만 제외 카드 판단에 쓰인다.

## 수입 알림 표시

```text
SmsInstantProcessor.processIncomeEntity()
-> IncomeEntity 저장 성공
-> 복원 중복/환불 중복 치환 여부 확인
-> SettingsDataStore.isNotificationEnabled()
-> SmsNotificationManager.showIncomeNotification()
```

수입 알림 title은 `source + incomeType`이며 source가 비어 있으면 `입금`으로 표시한다.

## 표시하지 않는 조건

| 조건 | 대상 | 이유 |
|---|---|---|
| `showUserNotification=false` | 지출/수입 | active notification 재검사, silent 보강 처리 |
| `SettingsDataStore.isNotificationEnabled() == false` | 지출/수입 | 사용자 거래 알림 toggle off |
| Android 13+에서 `POST_NOTIFICATIONS` 권한 없음 | 지출/수입 | 시스템 권한 미승인 |
| 제외 카드에 매칭 | 지출 | 사용자가 숨긴 카드 거래는 노티도 숨김 |
| 앱 알림 중복이 SMS 거래로 치환됨 | 지출 | 같은 거래를 두 번 알리지 않기 위함 |
| 복원 중복 또는 환불 중복 치환 | 수입 | 기존 거래 복구/치환 과정에서 중복 알림 방지 |
| parsing result가 `Skipped`/`Error` | 지출/수입 | 저장된 거래가 없으므로 표시하지 않음 |

## 알림 정리

```text
MainActivity.onCreate()/진입 흐름
-> SmsNotificationManager.clearTransactionNotifications()
-> activeNotifications 중 channelId == sms_transaction_v2 취소
```

Android M 이상은 MoneyTalk 거래 알림 채널만 취소한다. 그 미만은 channel별 active notification 조회가 어려워 `cancelAll()`을 사용한다.

## 변경 시 체크

1. 노티 표시 조건을 바꾸면 설정 toggle, Android 13 권한, 제외 카드, 중복 치환 케이스를 모두 확인했는가?
2. 외부 알림을 읽는 `NotificationTransactionService` 수정과 자체 노티 표시 수정이 섞이지 않았는가?
3. 신규 거래 저장 없이 노티만 표시하는 경로를 만들지 않았는가?
4. 앱 진입 시 오래된 거래 알림이 정리되는가?
