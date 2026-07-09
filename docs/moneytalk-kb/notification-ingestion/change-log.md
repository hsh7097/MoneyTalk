---
type: changelog
title: Notification Ingestion KB 변경 로그
description: Notification Ingestion KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, notification, changelog]
resource: docs/moneytalk-kb/notification-ingestion/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Notification Ingestion KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-07-08 | `core/notification/**`, `AppNotificationTransactionParser.kt`, `receiver/**` 확인 | Notification Ingestion 기능 KB 생성 | `README.md`, `00-structure-map.md` | 금융앱 알림/RCS/비즈메시지 수신 보조 경로를 SMS pipeline과 분리해 라우팅. |
| 2026-07-09 | `NotificationTransactionService`, `NotificationContentParser`, `FinancialAppDiscoveryRepository`, `SmsInstantProcessor` 확인 | 알림 수신 흐름 상세화 | `README.md`, `01-feature-flow.md` | 메시지 앱 provider 재조회, 금융앱 알림 직접 파싱, active notification silent 처리, 노티 표시 KB 분리 기준을 추가. |
