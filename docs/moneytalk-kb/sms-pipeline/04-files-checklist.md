---
type: checklist
title: SMS Pipeline Files Checklist
description: SMS Pipeline 수정 전후에 확인할 파일과 검증 질문을 정리한다.
tags: [moneytalk, sms, checklist]
resource: app/src/main/java/com/sanha/moneytalk/core/sms/
timestamp: 2026-07-11T00:00:00+09:00
status: draft
---

# 04 Files Checklist

## 수정 전 질문

- 이 변경이 원본 읽기, 필터, Fast Path, Vector/LLM, 실시간 수신 중 어디에 속하는가?
- 지출과 수입 경로가 분리되어 있는가?
- 동기화 범위 또는 coverage 기록에 영향이 있는가?
- DB 저장, 중복 제거, UI refresh까지 이어지는 변경인가?
- sender regex Fast Path 룰 변경이면 [06-rule-json-guide.md](06-rule-json-guide.md)를 먼저 확인했는가?
- SMS가 Gemini/RTDB로 전송되면 직접 식별정보 최소화, 권한 고지, `send_origin_message` 운영값을 함께 확인했는가?

## 빠른 파일 찾기

| 작업 | 확인 파일 |
|---|---|
| 배치 파싱 순서 | `SmsSyncCoordinator.kt`, `SmsPipeline.kt` |
| Fast Path | `SmsRegexRuleMatcher.kt`, `SmsRegexRuleSyncService.kt`, `SmsRegexRuleRepository.kt` |
| Fast Path 룰 운영 | [06-rule-json-guide.md](06-rule-json-guide.md), `app/src/main/assets/sms_rules_v1.json` |
| 수입 | `SmsIncomeFilter.kt`, `SmsIncomeParser.kt` |
| 원본 읽기 | `SmsReaderV2.kt`, `SmsSyncMessageReader.kt` |
| 실시간 | `SmsInstantProcessor.kt`, `receiver/*` |
| 외부 AI/RTDB 표본 | `SmsSensitiveDataSanitizer.kt`, `SmsOriginSampleCollector.kt`, `PremiumManager.kt`, [06-rule-json-guide.md](06-rule-json-guide.md) |
| 저장 결과 | `MainViewModel.kt`, `finance-data` KB |
