---
type: module
title: SMS Pipeline 서브모듈
description: SMS/MMS/RCS 파싱 파이프라인 작업의 진입점과 필수 문서를 안내한다.
tags: [moneytalk, sms, pipeline, module]
resource: app/src/main/java/com/sanha/moneytalk/core/sms/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# SMS Pipeline 서브모듈

> 상태: draft
> 기준: 2026-07-03 현재 `core/sms`, `core/sync`, `receiver` 소스와 `docs/SMS_PARSING.md` 확인

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
