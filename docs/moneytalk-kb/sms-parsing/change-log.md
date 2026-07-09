---
type: log
title: 문자 파싱 KB Change Log
description: 문자 파싱 기능 KB 변경 상세 이력을 기록한다.
tags: [moneytalk, sms-parsing, changelog]
resource: docs/moneytalk-kb/sms-parsing/
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 문자 파싱 Change Log

## 2026-07-09

- 기준: 루트 `SMS_PARSING.md`, `AI_CONTEXT.md` SMS 파싱 파트 재검토
- 변경 근거: 원문 문서를 이동하지 않고 SMS/MMS/RCS 읽기, 중복 제거, 수입/지출 분기, 실시간 보완, coverage 계약을 KB 내부 문서로 흡수하기 위해 `06-ingestion-contract.md`를 추가했다.
- 갱신한 KB: `README.md`, `06-ingestion-contract.md`

## 2026-07-03

- 기준: `MainViewModel`, `core/sms`, `core/sync`, `06-ingestion-contract.md` 확인
- 변경 근거: 기능 단위 KB 요구 반영
- 갱신한 KB: `sms-parsing/**`
- 다음 검증: 문자 파싱 결과 필드 추가 작업에서 parser, 저장, refresh 파일을 정확히 찾는지 확인
