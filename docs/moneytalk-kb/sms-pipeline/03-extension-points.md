---
type: extension-points
title: SMS Pipeline Extension Points
description: SMS Pipeline의 확장 지점과 책임 경계를 정리한다.
tags: [moneytalk, sms, extension]
resource: app/src/main/java/com/sanha/moneytalk/core/sms/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# 03 Extension Points

| 확장 지점 | 파일 | 주의 |
|---|---|---|
| sender regex Fast Path 룰 | `SmsRegexRuleMatcher.kt`, `SmsRegexRuleSyncService.kt` | 수입 SMS는 Fast Path 룰 대상이 아니다. |
| Asset/RTDB 룰 로드 | `SmsRegexRuleAssetLoader.kt`, `SmsRegexRemoteRuleLoader.kt` | cache와 overlay 우선순위를 함께 확인한다. |
| 신규 비거래성 필터 | `SmsPreFilter.kt`, `SmsNonTransactionNoticeFilter.kt` | 거래 후보를 과도하게 제거하지 않게 샘플 검증이 필요하다. |
| 날짜/시간 해석 | `SmsTransactionDateResolver.kt` | 지출/수입 파서가 함께 사용한다. |
| LLM 추출/regex 생성 | `GeminiSmsExtractor.kt`, `SmsGroupClassifier.kt` | 비용과 batch 처리 단위를 함께 확인한다. |

확장 후에는 `docs/SMS_PARSING.md`와 이 KB의 파일 인벤토리를 같이 갱신한다.
