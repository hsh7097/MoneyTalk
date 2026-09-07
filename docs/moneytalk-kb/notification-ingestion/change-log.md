---
type: changelog
title: Notification Ingestion KB 변경 로그
description: Notification Ingestion KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, notification, changelog]
resource: docs/moneytalk-kb/notification-ingestion/
timestamp: 2026-09-07T22:00:00+09:00
status: draft
---

# Notification Ingestion KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-09-07 | 앱 알림에서 명시적 가맹점이 있는데 금액 필드 이름을 상호로 선택하는 경로 확인 | `가맹점:` 추출을 휴리스틱보다 우선하고 금액/일시/가맹점 필드 이름만 후보에서 제외 | `01-feature-flow.md` | 합성 SSGPAY 회귀 4개 추가. 기존 거래는 수정하지 않으며 Gradle 검증은 루트 통합 실행에서 수행한다. |
| 2026-09-07 | UI가 없는 동안 `SMS_RECEIVED` SharedFlow만으로는 미매칭 문자가 파싱되지 않는 경로 확인 | 금융 로컬 미매칭에 한해 private 영속 큐·OS Job·공용 Writer로 재처리, 최대 3회와 삭제 세대 보호 | `01-feature-flow.md`, `00-structure-map.md` | 서버·과금 설정·스키마·새 의존성 변경 없음. 큐 테스트와 JobService 수명 검토 수행, 통합 빌드/기기 검증 결과는 루트 작업에서 기록한다. |
| 2026-09-07 | MMS/RCS observer의 `tryLock` 실패 시 변경 폐기와 마지막 backoff 후 재조회 누락 확인 | 대기 provider 변경 1회 보존·병합, 본문 최대 4회 조회로 보완 | `01-feature-flow.md`, `00-structure-map.md` | `ProviderChangeQueueTest`, `ProviderBodyReaderTest`에 겹친 변경/마지막 본문 준비 회귀 추가. Gradle 통합 검증 및 실제 기기 수신 확인은 작업 결과에서 별도 기록한다. |
| 2026-07-08 | `core/notification/**`, `AppNotificationTransactionParser.kt`, `receiver/**` 확인 | Notification Ingestion 기능 KB 생성 | `README.md`, `00-structure-map.md` | 금융앱 알림/RCS/비즈메시지 수신 보조 경로를 SMS pipeline과 분리해 라우팅. |
| 2026-07-09 | `NotificationTransactionService`, `NotificationContentParser`, `FinancialAppDiscoveryRepository`, `SmsInstantProcessor` 확인 | 알림 수신 흐름 상세화 | `README.md`, `01-feature-flow.md` | 메시지 앱 provider 재조회, 금융앱 알림 직접 파싱, active notification silent 처리, 노티 표시 KB 분리 기준을 추가. |
