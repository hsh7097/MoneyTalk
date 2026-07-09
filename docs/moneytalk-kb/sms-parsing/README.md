---
type: feature
title: 문자 파싱 기능
description: SMS/MMS/RCS 원본을 읽어 지출/수입 거래로 저장하고 화면에 반영하는 end-to-end 기능 KB다.
tags: [moneytalk, sms-parsing, feature, sync]
resource: app/src/main/java/com/sanha/moneytalk/MainViewModel.kt
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 문자 파싱 기능

> 상태: draft
> 기준: 2026-07-09 현재 `MainViewModel`, `core/sms`, `core/sync`, 루트 SMS 파싱 문서 내용을 KB 기준으로 재작성

문자 파싱 기능은 SMS/MMS/RCS 원본을 읽고, 파싱 결과를 지출/수입 DB에 저장한 뒤 화면 refresh까지 연결하는 end-to-end 기능이다.
파서 내부 단계는 [sms-pipeline](../sms-pipeline/README.md)이 담당하고, 저장 모델/DAO는 [finance-data](../finance-data/README.md)가 담당한다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-structure-map.md](00-structure-map.md) | 기능에 참여하는 파일과 책임을 정리한다. | 변경 파일이 문자 파싱 기능의 어느 단계인지 판단할 때 본다. |
| [01-feature-flow.md](01-feature-flow.md) | sync trigger부터 DB 저장/refresh까지 흐름을 설명한다. | 문자 파싱 결과가 어떻게 저장/표시되는지 따라갈 때 본다. |
| [02-data-contract.md](02-data-contract.md) | `SmsInput`, `SyncResult`, `ExpenseEntity`, `IncomeEntity` contract를 설명한다. | 파싱 결과 model이나 저장 필드가 바뀔 때 본다. |
| [03-extension-points.md](03-extension-points.md) | 기능 확장 지점과 주의할 책임 경계를 정리한다. | 새 reader, parser, dedupe, save 정책을 추가할 때 본다. |
| [04-files-checklist.md](04-files-checklist.md) | 수정 전후 확인할 파일과 검증 질문이다. | 리뷰 전 side effect를 점검할 때 본다. |
| [05-file-inventory.md](05-file-inventory.md) | 기능 관련 파일 역할 인덱스다. | 수정 후보 파일이 애매할 때 본다. |
| [06-ingestion-contract.md](06-ingestion-contract.md) | SMS/MMS/RCS 읽기, 중복 제거, 수입/지출 저장, 실시간 보완 경계를 설명한다. | 문자 수집/저장/coverage/notification 보완 경계 확인 |
| [change-log.md](change-log.md) | 기능 KB 변경 로그다. | 문서 변경 이유를 확인할 때 본다. |

## 기능 요약

```text
SMS 수신/앱 resume/수동 동기화
→ MainViewModel.syncSmsV2
→ SmsSyncMessageReader / SmsReaderV2
→ SmsSyncCoordinator
→ ExpenseEntity / IncomeEntity 저장
→ DataRefreshEvent.TRANSACTION_ADDED
→ Home/History refresh
```
