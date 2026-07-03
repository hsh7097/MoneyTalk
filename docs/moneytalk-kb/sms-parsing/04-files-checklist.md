---
type: checklist
title: 문자 파싱 Files Checklist
description: 문자 파싱 기능 수정 전후에 확인할 파일과 검증 질문을 정리한다.
tags: [moneytalk, sms-parsing, checklist]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 04 Files Checklist

## 빠른 파일 찾기

| 작업 | 확인 파일 |
|---|---|
| trigger/sync orchestration | `MainViewModel.kt` |
| 원본 읽기 | `SmsSyncMessageReader.kt`, `SmsReaderV2.kt` |
| parser 내부 | `SmsSyncCoordinator.kt`, `SmsPipeline.kt` |
| 저장 | `MainViewModel.saveExpenses`, `saveIncomes`, Repository |
| 카테고리 선분류 | `CategoryClassifierService.kt` |
| refresh | `DataRefreshEvent.kt`, Home/History ViewModel |

## 수정 전 질문

- 동기화 범위나 watermark가 바뀌는가?
- parser 결과 model과 DB 저장 model이 같이 바뀌는가?
- 지출/수입/환불/이체 경로를 모두 확인했는가?
- 저장 후 화면 refresh 이벤트가 필요한가?
