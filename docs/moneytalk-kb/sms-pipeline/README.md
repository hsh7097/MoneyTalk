---
type: module
title: SMS Pipeline 서브모듈
description: SMS/MMS/RCS 파싱 파이프라인 작업의 진입점과 필수 문서를 안내한다.
tags: [moneytalk, sms, pipeline, module]
resource: app/src/main/java/com/sanha/moneytalk/core/sms/
timestamp: 2026-07-11T00:00:00+09:00
status: draft
---

# SMS Pipeline 서브모듈

> 상태: draft
> 기준: 2026-07-11 현재 `core/sms`, `core/sync`, `receiver`, RTDB `sms_origin` 감사 결과 확인

SMS Pipeline은 문자 원본을 읽고 지출/수입 거래로 변환하는 공통 파이프라인이다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-structure-map.md](00-structure-map.md) | 파일 구조, 책임, 핵심 파일을 정리한다. | 변경 파일이 어느 파이프라인 단계인지 판단할 때 본다. |
| [01-purpose-architecture.md](01-purpose-architecture.md) | 파이프라인 목적과 단계별 흐름을 설명한다. | 구조 변경 전 책임 경계를 확인할 때 본다. |
| [02-how-to-use.md](02-how-to-use.md) | 작업 유형별 참조 순서를 안내한다. | 실제 수정 전 어떤 파일을 열지 정할 때 본다. |
| [03-extension-points.md](03-extension-points.md) | 확장 지점과 주의할 계약을 정리한다. | 신규 룰, 필터, extractor, reader를 추가할 때 본다. |
| [04-files-checklist.md](04-files-checklist.md) | 수정 전후 파일 체크리스트다. | 리뷰 전 side effect를 점검할 때 본다. |
| [05-file-inventory.md](05-file-inventory.md) | 전체 파일 역할 인덱스다. | 파일 후보가 애매할 때 본다. |
| [06-rule-json-guide.md](06-rule-json-guide.md) | `sms_rules_v1.json`과 RTDB 룰 갱신 기준을 정리한다. | Fast Path sender regex 룰을 추가/검증할 때 본다. |
| [07-rtdb-sms-origin-import-log.md](07-rtdb-sms-origin-import-log.md) | RTDB `sms_origin` export 반영 이력과 보류 판단을 기록한다. | 운영 표본을 asset rule/파싱 개선으로 흡수한 근거를 볼 때 본다. |
| [change-log.md](change-log.md) | 서브모듈 KB 변경 로그다. | 문서 변경 이유를 확인할 때 본다. |

## 기준 패키지

- `app/src/main/java/com/sanha/moneytalk/core/sms/`
- `app/src/main/java/com/sanha/moneytalk/core/sync/`
- `app/src/main/java/com/sanha/moneytalk/receiver/`

## 핵심 흐름

```text
SmsSyncMessageReader / SmsReaderV2
→ SmsSyncCoordinator
→ SmsPreFilter
→ SmsIncomeFilter
→ SmsRegexRuleMatcher
→ SmsPipeline
→ SmsPatternMatcher / SmsGroupClassifier / GeminiSmsExtractor
→ SyncResult
```

Fast Path 룰 운영은 [06-rule-json-guide.md](06-rule-json-guide.md)를 기준으로 한다.
룰이 miss되면 embedding/vector/LLM 폴백이 계속 동작하므로, 파싱 실패와 성능 저하를 구분해서 본다.
RTDB `sms_origin` export를 반영한 실제 작업 이력은 [07-rtdb-sms-origin-import-log.md](07-rtdb-sms-origin-import-log.md)를 먼저 확인한다.
외부 Gemini 요청과 RTDB 마스킹 표본은 `SmsSensitiveDataSanitizer`로 직접 식별정보를 최소화한다.
원본 `originBody`는 live RTDB `/config/send_origin_message=true`일 때만 수집하며, 수집 종료 후 기존 원문은 별도 일괄 삭제한다.
취소/환불은 수입 경로, 완료 카드대금 출금은 저장 후 통계 제외, 교통/KSNET/매출접수 N건 요약은 입력 SKIP이 현재 계약이다.
2026-07-11 export 재감사 기준 지출 Fast Path 165개, 수입 경로 11개, 고신뢰 SKIP 10개로 전체 `186/192`(96.9%)를 처리한다. 미처리 6개는 통화 모델이 필요한 외화 승인이다.
