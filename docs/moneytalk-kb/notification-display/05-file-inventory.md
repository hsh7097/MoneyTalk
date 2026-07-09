---
type: inventory
title: Notification Display file inventory
description: MoneyTalk 자체 거래 알림 표시와 직접 관련된 파일 목록이다.
tags: [moneytalk, notification, display, inventory]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-09T02:02:36+09:00
status: draft
---

# Notification Display file inventory

| 파일 | 역할 | 수정 시 같이 볼 문서 |
|---|---|---|
| `core/notification/SmsNotificationManager.kt` | 알림 채널 생성, 지출/수입 알림 표시, 거래 알림 취소 | `01-feature-flow.md` |
| `core/sms/SmsInstantProcessor.kt` | 저장 성공 후 알림 표시 여부 결정 | [../sms-pipeline/README.md](../sms-pipeline/README.md), [../filtering/README.md](../filtering/README.md) |
| `MoneyTalkApplication.kt` | 앱 시작 시 알림 채널 생성 | [../app-shell/README.md](../app-shell/README.md) |
| `MainActivity.kt` | 앱 진입 시 거래 알림 정리 | [../app-shell/README.md](../app-shell/README.md) |
| `feature/settings/ui/SettingsScreen.kt` | 거래 알림 toggle, 알림 접근 설정 row | [../settings/README.md](../settings/README.md) |
| `feature/settings/ui/SettingsViewModel.kt` | 알림 toggle 저장, 알림 listener enabled 상태 로드 | [../settings/package-reference/05-menu-map.md](../settings/package-reference/05-menu-map.md) |
| `core/datastore/SettingsDataStore.kt` | `notificationEnabled` 저장 | [../settings/package-reference/02-data-viewmodel.md](../settings/package-reference/02-data-viewmodel.md) |
| `core/util/CardVisibilityFilter.kt` | 제외 카드의 지출 노티 표시 차단 | [../filtering/02-filter-surfaces.md](../filtering/02-filter-surfaces.md) |
| `receiver/NotificationTransactionService.kt` | 외부 알림 수신 경로에서 `showUserNotification` 전달 | [../notification-ingestion/README.md](../notification-ingestion/README.md) |
