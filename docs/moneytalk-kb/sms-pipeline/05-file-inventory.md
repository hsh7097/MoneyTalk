---
type: file-inventory
title: SMS Pipeline File Inventory
description: SMS Pipeline 파일별 역할과 참조 시점을 인덱싱한다.
tags: [moneytalk, sms, file-inventory]
resource: app/src/main/java/com/sanha/moneytalk/core/sms/
timestamp: 2026-07-11T00:00:00+09:00
status: draft
---

# SMS Pipeline File Inventory

| 파일 | 역할 | 언제 보는가 | 분류 |
|---|---|---|---|
| `SmsSyncCoordinator.kt` | 배치 파싱 외부 진입점 | 파싱 순서, Fast Path fallback | 핵심 |
| `SmsPipeline.kt` | Vector/LLM fallback orchestration | 미매칭 처리, batch extraction | 핵심 |
| `SmsPipelineModels.kt` | 파이프라인 모델 | model field 변경 | 핵심 후보 |
| `SmsPreFilter.kt` | 비거래성 SMS 사전 필터 | 누락/과필터 문제 | 핵심 후보 |
| `SmsIncomeFilter.kt` | PAYMENT/INCOME/SKIP 분류 | 수입/결제 분류 문제 | 핵심 |
| `SmsRegexRuleMatcher.kt` | sender regex Fast Path | 룰 매칭 문제 | 핵심 |
| `SmsPatternMatcher.kt` | 벡터 패턴 매칭 | 유사도/regex fallback | 핵심 |
| `SmsGroupClassifier.kt` | 미매칭 그룹핑과 LLM regex 생성 | 신규 패턴 처리 | 핵심 |
| `GeminiSmsExtractor.kt` | LLM 추출과 regex 생성 | Gemini 추출 변경 | 핵심 |
| `SmsSensitiveDataSanitizer.kt` | 외부 처리 전 직접 식별정보 최소화 | Gemini/RTDB 개인정보 경로 | 핵심 |
| `SmsOriginSampleCollector.kt` | RTDB 성공/실패 표본과 원문 수집 gate | `sms_origin`, `send_origin_message` 변경 | 핵심 |
| `SmsReaderV2.kt` | SMS/MMS/RCS 원본 읽기 | provider read 문제 | 핵심 |
| `SmsSyncMessageReader.kt` | 동기화 기간 원본 읽기 래퍼 | 기간 동기화 문제 | 핵심 후보 |
| `SmsInstantProcessor.kt` | 실시간 1건 처리 | receiver/notification 즉시 저장 | 핵심 후보 |
| `SmsTransactionDateResolver.kt` | 거래 날짜/시간 해석 | 날짜 파싱 오류 | 핵심 |
| `SmsRegexRuleSyncService.kt` | Asset seed + RTDB overlay 병합 | 룰 동기화 | 핵심 후보 |
| `VectorSearchEngine.kt` | 벡터 유사도 계산 | similarity 계산 | 보조 |
| `DeletedSmsTracker.kt` | 삭제 SMS 재삽입 방지 | 삭제 후 재등장 문제 | 핵심 후보 |

## 운영 문서

| 파일 | 역할 |
|---|---|
| `06-rule-json-guide.md` | `sms_rules_v1.json`, RTDB overlay, ruleKey, priority, 검증 절차 |
| `../source-docs/01-consolidation-map.md` | 삭제된 레거시 루트 가이드와 현재 KB 목적지 매핑 |
