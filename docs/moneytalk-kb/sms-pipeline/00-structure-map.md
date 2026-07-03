---
type: structure-map
title: SMS Pipeline 구조 지도
description: SMS Pipeline의 파일 구조, 책임, AI 참조 순서를 정리한다.
tags: [moneytalk, sms, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/core/sms/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# SMS Pipeline 구조 지도

## Package Groups

| group | 책임 | 대표 파일 |
|---|---|---|
| reader | SMS/MMS/RCS 원본 읽기 | `SmsReaderV2.kt`, `SmsSyncMessageReader.kt` |
| coordinator | 배치 파싱 외부 진입점 | `SmsSyncCoordinator.kt` |
| filter | 사전 필터와 결제/수입/스킵 분류 | `SmsPreFilter.kt`, `SmsIncomeFilter.kt` |
| fast path | sender regex 룰 로드/매칭/동기화 | `SmsRegexRuleMatcher.kt`, `SmsRegexRuleAssetLoader.kt`, `SmsRegexRuleSyncService.kt` |
| vector/llm | 임베딩, 패턴 매칭, LLM 추출, regex 생성 | `SmsPipeline.kt`, `SmsPatternMatcher.kt`, `SmsGroupClassifier.kt`, `GeminiSmsExtractor.kt` |
| realtime | 실시간 1건 처리 | `SmsInstantProcessor.kt`, `receiver/*` |
| sync coverage | 동기화 기간과 coverage 기록 | `core/sync/*` |

## 핵심 파일

| 파일 | 역할 | 함께 볼 파일 |
|---|---|---|
| `SmsSyncCoordinator.kt` | 배치 파싱 외부 진입점. PreFilter, IncomeFilter, Fast Path, Pipeline 순서를 조율한다. | `SmsPipeline.kt`, `SmsPipelineModels.kt` |
| `SmsPipeline.kt` | Fast Path 미매칭 결제 후보의 Vector/LLM fallback 오케스트레이션 | `SmsPatternMatcher.kt`, `SmsGroupClassifier.kt` |
| `SmsRegexRuleMatcher.kt` | 결제 후보 sender 기반 regex Fast Path 매칭 | `SmsRegexRuleRepository.kt`, `SmsRegexRuleSyncService.kt` |
| `SmsReaderV2.kt` | SMS/MMS/RCS provider에서 원본을 읽어 `SmsInput`으로 변환 | `SmsSyncMessageReader.kt`, `receiver/*` |
| `SmsTransactionDateResolver.kt` | 본문 날짜/시간 해석 공통 유틸 | `SmsIncomeParser.kt`, `SmsParser.kt` |
| `SmsInstantProcessor.kt` | 실시간 1건 처리 | `receiver/SmsReceiver.kt`, `NotificationTransactionService.kt` |

## AI Reference Order

| 작업 유형 | 참조 순서 |
|---|---|
| 파싱 순서 변경 | `README.md` -> `01-purpose-architecture.md` -> `SmsSyncCoordinator.kt` |
| Fast Path 룰 변경 | `README.md` -> `03-extension-points.md` -> `SmsRegexRuleMatcher.kt` |
| LLM 추출 변경 | `README.md` -> `00-structure-map.md` -> `GeminiSmsExtractor.kt` |
| 실시간 수신 변경 | `README.md` -> `05-file-inventory.md` -> `SmsInstantProcessor.kt` -> `receiver/*` |
