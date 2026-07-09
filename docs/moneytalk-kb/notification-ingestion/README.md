---
type: feature
title: Notification Ingestion 기능
description: 금융앱 알림 접근, 후보 탐색, 앱 알림 거래 파싱, 실시간 수신 보조 흐름을 설명한다.
tags: [moneytalk, notification, ingestion, rcs, feature]
resource: app/src/main/java/com/sanha/moneytalk/core/notification/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Notification Ingestion 기능

> 상태: draft
> 기준: 2026-07-08 현재 `core/notification/**`, `core/sms/AppNotificationTransactionParser.kt`, `receiver/**` 확인

Notification Ingestion은 SMS 외에 금융앱 알림/RCS/비즈메시지 경로에서 거래 후보를 감지하고 파싱하는 보조 기능이다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-structure-map.md](00-structure-map.md) | notification 관련 파일과 책임을 정리한다. | 알림 접근/후보 탐색/앱 알림 파싱 작업 |
| [01-feature-flow.md](01-feature-flow.md) | 외부 알림 접근, provider 재조회, 금융앱 후보 탐색, 저장 흐름을 설명한다. | 알림 수신/저장 경로 또는 RCS/금융앱 알림 문제 |
| [change-log.md](change-log.md) | Notification Ingestion KB 변경 로그다. | 문서 변경 이유 확인 |
| [../sms-pipeline/README.md](../sms-pipeline/README.md) | SMS pipeline KB다. | 거래 파싱/저장 흐름과 연결 |
| [../notification-display/README.md](../notification-display/README.md) | MoneyTalk 자체 거래 알림 표시 KB다. | 저장 후 사용자 노티 표시 조건 확인 |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `core/notification/NotificationAccessHelper.kt` | 알림 접근 권한 확인/설정 이동 |
| `core/notification/FinancialAppCandidateAnalyzer.kt` | 금융앱 후보 분석 |
| `core/notification/FinancialAppDiscoveryRepository.kt` | 후보 discovery repository |
| `core/notification/FinancialAppLlmAnalyzer.kt` | LLM 기반 금융앱 분석 |
| `core/notification/RemoteFinancialAppRepository.kt` | 원격 금융앱 목록 |
| `core/sms/AppNotificationTransactionParser.kt` | 앱 알림 텍스트 거래 파싱 |
| `core/sms/AppNotificationTypeClassifier.kt` | 앱 알림 타입 분류 |
| `receiver/**` | SMS/MMS/RCS/알림 수신 receiver |

## 작업 판단

- 외부 알림을 읽어 거래 후보를 만드는 작업은 이 KB를 본다.
- 거래 저장 후 MoneyTalk이 사용자에게 알림을 띄우는 조건은 [notification-display/README.md](../notification-display/README.md)를 본다.
