---
type: changelog
title: Notification Display KB 변경 로그
description: Notification Display KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, notification, display, changelog]
resource: docs/moneytalk-kb/notification-display/
timestamp: 2026-07-11T00:00:00+09:00
status: draft
---

# Notification Display KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-07-11 | AVD 합성 SMS, `SmsReceiver`, `SmsInstantProcessor`, `dumpsys notification` 확인 | 거래 저장 후 자체 알림 표시 E2E 검증 기록 | `README.md`, `01-feature-flow.md` | 네트워크를 끈 AVD에서 신한카드 형식 합성 SMS를 수신해 `QA_MARKET 8,910원` 저장과 `sms_transaction_v2` 알림의 동일 제목/금액 표시를 확인했다. 거래 알림 toggle이 off이면 자체 알림이 생기지 않고, 테스트 후 다시 off로 복원했다. |
| 2026-07-09 | `SmsNotificationManager`, `SmsInstantProcessor`, `MoneyTalkApplication`, `MainActivity`, `SettingsScreen` 확인 | Notification Display 기능 KB 생성 | `README.md`, `00-structure-map.md`, `01-feature-flow.md`, `05-file-inventory.md` | 외부 알림 수신과 MoneyTalk 자체 거래 알림 표시를 분리해 문서화. |
