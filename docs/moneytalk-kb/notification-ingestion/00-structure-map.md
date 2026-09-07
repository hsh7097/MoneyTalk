---
type: structure-map
title: Notification Ingestion 구조 지도
description: 금융앱 알림 접근, 후보 탐색, 원격 목록, 앱 알림 파싱 파일 역할을 정리한다.
tags: [moneytalk, notification, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/core/notification/
timestamp: 2026-09-07T22:00:00+09:00
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
| `FinancialAppLlmAnalyzer.kt` | AI | Firebase AI Logic/App Check 기반 금융앱 후보 LLM 분석 | `FirebaseAiModelFactory`, `GeminiConfigProvider` |
| `RemoteFinancialAppRepository.kt` | data | 원격 금융앱 정의 조회 | `RemoteFinancialAppSource.kt` |
| `AppNotificationTransactionParser.kt` | parser | 앱 알림 텍스트에서 거래 추출 | `SmsParser.kt` |
| `AppNotificationTypeClassifier.kt` | classifier | 앱 알림 타입 분류 | `AppNotificationTransactionParser.kt` |
| `receiver/MmsContentObserver.kt`, `receiver/RcsContentObserver.kt` | observer | provider 변경을 감지해 본문 재조회와 즉시 저장 수행 | `ProviderChangeQueue.kt`, `ProviderBodyReader.kt` |
| `receiver/ProviderChangeQueue.kt` | scheduling | 처리 중 들어온 변경을 후속 조회 1회로 보존·병합 | `MmsContentObserver.kt`, `RcsContentObserver.kt` |
| `receiver/ProviderBodyReader.kt` | reader | 최초 및 각 backoff 직후 본문 읽기 | `ProviderBodyReaderTest.kt` |
| `core/sms/SmsFallbackQueue.kt` | persistence | 금융 미매칭 입력/삭제 세대/최대 3회 시도를 백업 제외 파일에 보존 | `SmsFallbackQueueTest.kt` |
| `core/sms/SmsFallbackScheduler.kt` | scheduling | 네트워크 조건 persisted Job 예약, 전체 삭제 시 큐·예약 무효화 | `SmsFallbackJobService.kt`, `SettingsViewModel.kt` |
| `core/sms/SmsFallbackProcessor.kt` | processing | UI 없이 Coordinator 파싱과 공용 Writer 저장, 삭제 gate 참여 | `SmsIngestionWriter.kt` |
| `receiver/SmsFallbackJobService.kt` | lifecycle | OS start/stop/retry와 coroutine 수명 연결 | `SmsFallbackScheduler.kt`, `MoneyTalkApplication.kt` |
