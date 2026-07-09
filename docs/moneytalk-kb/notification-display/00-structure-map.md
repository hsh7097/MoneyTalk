---
type: structure-map
title: Notification Display 구조 지도
description: MoneyTalk 자체 거래 알림 표시 관련 파일과 책임 경계를 정리한다.
tags: [moneytalk, notification, display, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/core/notification/SmsNotificationManager.kt
timestamp: 2026-07-09T02:02:36+09:00
status: draft
---

# Notification Display 구조 지도

```text
MoneyTalkApplication
-> SmsNotificationManager.createNotificationChannel()

SmsReceiver / NotificationTransactionService / MmsContentObserver / RcsContentObserver
-> SmsInstantProcessor
-> ExpenseEntity or IncomeEntity 저장
-> SettingsDataStore notification toggle 확인
-> CardVisibilityFilter / 중복 치환 조건 확인
-> SmsNotificationManager.show*Notification()
-> MainActivity 진입 시 clearTransactionNotifications()
```

## 파일 책임

| 파일 | 분류 | 책임 |
|---|---|---|
| `core/notification/SmsNotificationManager.kt` | display manager | 채널 생성, 지출/수입 알림 생성, active transaction notification 정리 |
| `core/sms/SmsInstantProcessor.kt` | decision point | 저장 성공 여부, 설정 toggle, 제외 카드, 중복 치환에 따라 알림 표시 호출 |
| `MoneyTalkApplication.kt` | app startup | 앱 시작 시 알림 채널 생성 |
| `MainActivity.kt` | app foreground | 앱 진입 시 MoneyTalk 거래 알림 취소 |
| `feature/settings/ui/SettingsScreen.kt` | user setting | 거래 알림 toggle과 알림 접근 권한 row 표시 |
| `feature/settings/ui/SettingsViewModel.kt` | setting state | 거래 알림 enabled 상태 저장, notification listener 상태 로드 |
| `core/util/CardVisibilityFilter.kt` | filter | 제외 카드 거래의 지출 알림 표시 차단 |

## 책임 경계

| 책임 | 담당 KB |
|---|---|
| 외부 알림을 읽고 거래 후보를 찾음 | [../notification-ingestion/README.md](../notification-ingestion/README.md) |
| 저장 후 MoneyTalk 자체 거래 알림을 띄움 | 이 KB |
| 제외 카드/통계 제외/SMS 제외 정책 | [../filtering/README.md](../filtering/README.md) |
| 실시간 SMS/MMS/RCS 파싱 저장 | [../sms-pipeline/README.md](../sms-pipeline/README.md) |
