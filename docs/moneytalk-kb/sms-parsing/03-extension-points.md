---
type: extension-points
title: 문자 파싱 Extension Points
description: 문자 파싱 기능 확장 지점과 책임 경계를 정리한다.
tags: [moneytalk, sms-parsing, extension]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 03 Extension Points

| 확장 지점 | 파일 | 같이 볼 KB |
|---|---|---|
| 새 provider/channel 읽기 | `SmsReaderV2.kt`, `receiver/*` | [sms-pipeline](../sms-pipeline/README.md) |
| 새 파싱 단계 | `SmsSyncCoordinator.kt`, `SmsPipeline.kt` | [sms-pipeline](../sms-pipeline/README.md) |
| 저장 전 분류 정책 | `MainViewModel.saveExpenses`, `CategoryClassifierService.kt` | [category-classification](../category-classification/README.md) |
| 중복 제거/삭제 방지 | `MainViewModel.kt`, `DeletedSmsTracker.kt` | [sms-pipeline](../sms-pipeline/README.md) |
| 저장 모델 변경 | `ExpenseEntity.kt`, `IncomeEntity.kt` | [finance-data](../finance-data/README.md) |
