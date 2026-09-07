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

## 거래 상세로 이동 (2026-09-08)

- `ExpenseDao.insertIngested`는 신규/갱신 결과에 실제 저장 ID를 반환한다. `SmsInstantProcessor`는 새 거래에만 이 ID를 사용해 알림을 게시한다. 수입도 `IncomeRepository.insert` 반환 ID를 사용한다.
- `TransactionNotificationIntents`가 `TransactionEditActivity.createIntent`의 기존 지출/수입 extra 계약을 재사용한다. `moneytalk://transaction/expense/{Long ID}` / `income/{Long ID}` URI는 PendingIntent 식별용이며 공개 deep link가 아니다.
- extras만 다른 PendingIntent는 같은 객체가 될 수 있으므로 타입·ID URI로 구분한다. notification tag도 `expense:{ID}` / `income:{ID}`로 나누어 프로세스 재시작과 서로 다른 테이블의 동일 ID에 영향을 받지 않는다.
- `TaskStackBuilder`와 manifest의 `parentActivityName=.MainActivity`가 홈 → 거래 편집 스택을 만든다. 다른 거래의 편집 ViewModel을 재사용하지 않으며 닫기/저장/뒤로가기는 홈으로 돌아간다. 알림 진입은 새 back stack을 구성한다.
- 이미 삭제되거나 다른 유형으로 전환된 거래는 찾을 수 없다는 안내를 표시한다. 존재하지 않는 ID를 새 거래 입력으로 바꾸지 않고 저장도 차단한다.
- `clearTransactionNotifications`는 `(tag, id)`로 취소하여 새 알림과 기존 숫자 ID 알림을 모두 정리한다. 기존 설정·권한·카드 숨김·중복 알림 억제 조건은 유지한다.

검증 기준: `TransactionNotificationInstrumentedTest`(실제 앱 DB/Activity/PendingIntent, 테스트 AVD 전용), `ExpenseIngestionInstrumentedTest`(원자적 저장 ID), 실제 알림 창 cold/warm 탭. 실행 결과는 구조 감사의 통합 검증 기록을 참조한다.

알림 back stack 구성은 [Android 공식 안내](https://developer.android.com/develop/ui/views/notifications/navigation)를 따른다.
