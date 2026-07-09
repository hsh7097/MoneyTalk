---
type: feature
title: Notification Display 기능
description: MoneyTalk이 거래 저장 후 사용자에게 표시하는 자체 앱 알림 채널, 표시 조건, 정리 흐름을 설명한다.
tags: [moneytalk, notification, display, sms]
resource: app/src/main/java/com/sanha/moneytalk/core/notification/SmsNotificationManager.kt
timestamp: 2026-07-09T02:02:36+09:00
status: draft
---

# Notification Display 기능

> 상태: draft
> 기준: 2026-07-09 현재 `SmsNotificationManager`, `SmsInstantProcessor`, `MoneyTalkApplication`, `MainActivity` 확인

Notification Display는 SMS/앱 알림/RCS 입력을 파싱해 거래를 저장한 뒤 MoneyTalk이 직접 띄우는 사용자 알림이다. 외부 알림을 읽어 거래 후보를 만드는 기능은 [notification-ingestion/README.md](../notification-ingestion/README.md)를 본다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-structure-map.md](00-structure-map.md) | 자체 거래 알림 표시 관련 파일과 책임 경계를 정리한다. | 변경 후보 파일이 알림 수신인지 표시인지 구분할 때 |
| [01-feature-flow.md](01-feature-flow.md) | 저장 성공부터 알림 표시/정리까지 조건 흐름을 설명한다. | 노티가 뜨지 않거나 중복 노티가 뜰 때 |
| [05-file-inventory.md](05-file-inventory.md) | 관련 파일 역할 인덱스다. | 변경 후보 파일이 애매할 때 |
| [change-log.md](change-log.md) | Notification Display KB 변경 로그다. | 문서 변경 이유 확인 |
| [../notification-ingestion/README.md](../notification-ingestion/README.md) | 외부 알림 수신/후보 탐색 KB다. | 알림 접근 권한, 금융앱 알림 파싱 작업 |
| [../filtering/README.md](../filtering/README.md) | 제외 카드/알림 표시 필터 KB다. | 제외 카드가 노티 표시를 막는지 확인 |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `core/notification/SmsNotificationManager.kt` | 알림 채널 생성, 지출/수입 알림 표시, 앱 진입 시 거래 알림 정리 |
| `core/sms/SmsInstantProcessor.kt` | 거래 저장 성공 후 사용자 알림 표시 여부 결정 |
| `MoneyTalkApplication.kt` | 앱 시작 시 알림 채널 생성 |
| `MainActivity.kt` | 앱 진입 시 MoneyTalk 거래 알림 정리 |
| `SettingsScreen.kt`, `SettingsViewModel.kt` | 거래 알림 사용 여부와 알림 접근 설정 row |

## 핵심 경계

- 알림 채널은 `sms_transaction_v2`이며 importance는 HIGH다.
- Android 13 이상은 `POST_NOTIFICATIONS` 권한이 없으면 표시하지 않는다.
- 지출 알림은 설정의 거래 알림 toggle과 제외 카드 정책을 모두 통과해야 한다.
- 수입 알림은 설정의 거래 알림 toggle을 통과해야 하며, 복원 중복/환불 중복 치환이면 표시하지 않는다.
- listener 재연결에서 active notification을 재처리할 때는 `showUserNotification=false`로 저장만 보강한다.
