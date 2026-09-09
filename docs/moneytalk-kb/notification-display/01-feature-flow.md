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

## 알림 삭제/통계 제외 액션 (2026-09-09)

- 지출 알림에는 `통계에서 제외`, `삭제` 순서로 액션을 제공한다. 수입 모델에는 통계 제외 필드가 없으므로 수입 알림은 `삭제`만 제공한다. 별도 수정 액션 없이 본문 클릭은 기존 `TaskStackBuilder` 편집 진입을 유지한다.
- 삭제는 Activity를 열지 않는 explicit immutable broadcast PendingIntent다. URI는 `moneytalk://transaction/expense/{Long ID}/delete` 또는 `income/{Long ID}/delete`이며, 본문/삭제 동작과 테이블별 동일 숫자 ID가 서로 섞이지 않는다.
- 통계 제외는 `moneytalk://transaction/expense/{Long ID}/exclude-from-stats` URI와 별도 action을 사용한다. 수입 URI는 거절한다. 기존 `TransactionQuickActionService.update`에 `isExcludedFromStats=true`만 전달하여 거래 내역과 다른 필드는 유지한다. 반복 요청은 포함 상태로 되돌리지 않으며 SMS 삭제 마킹이나 동일 거래처 규칙을 만들지 않는다. 되돌리기는 기존 편집 화면에서 가능하다.
- `TransactionNotificationActionReceiver`는 manifest에서 `exported=false`이고 action·scheme·authority·path·양수 Long ID를 검증한다. 잠금 화면에서는 지원 OS(Android 12 이상)의 알림 인증을 요구한다.
- `goAsync()` 동안 IO에서 `TransactionQuickActionService.delete`를 호출한다. 기존 단건 삭제와 SMS 삭제 마킹, 화면 갱신 이벤트를 재사용하며 원본 휴대폰 문자를 지우지 않는다.
- 삭제/통계 제외 성공 또는 이미 없는 거래이면 해당 `expense:{ID}`/`income:{ID}`의 알림 ID 0만 취소한다. 다른 거래/알림은 유지한다. 처리 예외 또는 지원하지 않는 변경이면 알림을 남겨 재시도할 수 있게 하며, 완료 여부와 무관하게 broadcast pending result를 종료한다.
- 알림을 쓸어 지우는 `deleteIntent`에는 연결하지 않는다. 거래 변경은 명시적으로 액션 버튼을 누른 경우만 수행한다.
- 기존 삭제 서비스의 DB 삭제 후 SMS 마킹 경계를 그대로 사용한다. 이번 변경이 이미 진행 중인 동기화와의 원자성까지 새로 보장하지는 않는다.

검증 기준: `TransactionNotificationActionInstrumentedTest`에서 삭제/통계 제외 액션 단건성, 동일 큰 Long ID의 테이블 구분, 삭제 시 SMS 마킹, 통계 제외 시 다른 필드/기록 유지, 없는/중복 요청, 비공개 receiver, 잘못된 URI를 확인한다. 기존 본문 편집은 `TransactionNotificationInstrumentedTest`로 유지 검증한다.

검증 결과 (2026-09-09):

- `assembleDebug`, `assembleDebugAndroidTest` 성공, JVM 테스트 424개 통과.
- `Codex_Fold_API_36` 에뮬레이터에서 신규 알림 액션 9개 + 기존 본문 편집 8개 통과. 수동 fixture 전용 1개는 자동 실행에서 건너뛰고 별도로 실행했다.
- 실제 알림 창에서 지출의 `통계에서 제외`/`삭제`, 수입의 `삭제` 표시를 확인했다. 지출 두 액션은 앱을 열지 않고 대상 알림만 정리했고, 남은 수입 본문 클릭은 해당 거래 편집으로 이동했다.
- 통계 제외한 합성 지출 12,340원은 가계부에 `통계 제외`로 남고 합계에 포함되지 않는 것을 화면으로 확인했다. 자동 검사에서도 정확히 해당 금액만 합계에서 줄고 다른 필드/거래/알림은 유지됐다.
- 화면 확인 후 이번 합성 거래 3건을 정리했고 에뮬레이터 해상도/밀도를 원래 값으로 복구했다. 실기기·잠금 인증 화면은 이번 검증 범위에 포함하지 않았다.
- 로컬 증적: `artifacts/notification-delete-20260909/`의 빌드/계측 로그, `notification-before.png`, `notification-income-remaining.png`, `notification-body-edit.png`, `excluded-in-history.png`.
