---
type: how-to-use
title: SMS Pipeline How To Use
description: SMS Pipeline 수정 시 작업 유형별 참조 순서를 정리한다.
tags: [moneytalk, sms, workflow]
resource: app/src/main/java/com/sanha/moneytalk/core/sms/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# 02 How To Use

| 작업 | 먼저 볼 문서 | 핵심 파일 |
|---|---|---|
| 배치 동기화 결과가 이상함 | `01-purpose-architecture.md` | `SmsSyncCoordinator.kt`, `SmsPipeline.kt`, `MainViewModel.kt` |
| SMS 원본 누락 | `00-structure-map.md` | `SmsReaderV2.kt`, `SmsSyncMessageReader.kt`, `core/sync/*` |
| Fast Path 룰 문제 | `03-extension-points.md` | `SmsRegexRuleMatcher.kt`, `SmsRegexRuleSyncService.kt`, `SmsRegexRuleRepository.kt` |
| 수입 파싱 문제 | `00-structure-map.md` | `SmsIncomeFilter.kt`, `SmsIncomeParser.kt`, `SmsTransactionDateResolver.kt` |
| 실시간 수신 문제 | `05-file-inventory.md` | `SmsInstantProcessor.kt`, `receiver/*` |

작업이 DB 저장 결과까지 이어지면 `finance-data` KB와 `MainViewModel.kt`를 함께 확인한다.
