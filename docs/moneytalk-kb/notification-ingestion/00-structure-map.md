---
type: structure-map
title: Notification Ingestion 구조 지도
description: 금융앱 알림 접근, 후보 탐색, 원격 목록, 앱 알림 파싱 파일 역할을 정리한다.
tags: [moneytalk, notification, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/core/notification/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Notification Ingestion 구조 지도

```text
notification access
-> FinancialApp discovery/candidate analysis
-> AppNotificationTypeClassifier
-> AppNotificationTransactionParser
-> MainViewModel / SMS pipeline save path
```

| 파일 | 분류 | 역할 | 함께 볼 파일 |
|---|---|---|---|
| `NotificationAccessHelper.kt` | permission | 알림 접근 권한 확인/설정 이동 | `SettingsViewModel.kt` |
| `FinancialAppCandidateAnalyzer.kt` | analysis | 설치 앱/알림 후보 분석 | `FinancialAppAnalysis.kt` |
| `FinancialAppDiscoveryRepository.kt` | data | 후보 앱 discovery 저장/조회 | `FinancialAppCandidateDao.kt` |
| `FinancialAppLlmAnalyzer.kt` | AI | 금융앱 후보 LLM 분석 | `GeminiApiKeyProvider` |
| `RemoteFinancialAppRepository.kt` | data | 원격 금융앱 정의 조회 | `RemoteFinancialAppSource.kt` |
| `AppNotificationTransactionParser.kt` | parser | 앱 알림 텍스트에서 거래 추출 | `SmsParser.kt` |
| `AppNotificationTypeClassifier.kt` | classifier | 앱 알림 타입 분류 | `AppNotificationTransactionParser.kt` |
