---
type: log
title: SMS Pipeline KB Change Log
description: SMS Pipeline KB 변경 상세 이력을 기록한다.
tags: [moneytalk, sms, changelog]
resource: docs/moneytalk-kb/sms-pipeline/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# SMS Pipeline Change Log

## 2026-07-09

- 기준: `docs/SMS_RULE_JSON_UPDATE_GUIDE.md`, `core/sms/**`, `app/src/main/assets/sms_rules_v1.json` 운영 기준 확인
- 변경 근거: 문자 파싱 Fast Path 룰, RTDB 표본, embedding/LLM 폴백 관계를 KB에서 바로 찾을 수 있어야 한다는 요구 반영
- 갱신한 KB:
  - `README.md`
  - `06-rule-json-guide.md`
- 결정:
  - sender regex 룰 운영 문서는 pipeline KB로 흡수
  - 수입 SMS는 asset regex 룰이 아니라 `SmsIncomeFilter -> SmsIncomeParser` 경로로 본다.

## 2026-07-03

- 기준: `core/sms`, `core/sync`, `receiver` 파일 목록과 `docs/moneytalk-kb/sms-parsing/06-ingestion-contract.md` 확인
- 변경 근거: MoneyTalk KB 초기 생성
- 갱신한 KB:
  - `README.md`
  - `00-structure-map.md`
  - `01-purpose-architecture.md`
  - `02-how-to-use.md`
  - `03-extension-points.md`
  - `04-files-checklist.md`
  - `05-file-inventory.md`
- 다음 검증:
  - 신규 sender regex 룰 변경 작업에서 Fast Path 관련 파일을 정확히 찾는지 확인
