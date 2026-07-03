---
type: file-inventory
title: 문자 파싱 File Inventory
description: 문자 파싱 기능 관련 파일 역할과 참조 시점을 인덱싱한다.
tags: [moneytalk, sms-parsing, file-inventory]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# 문자 파싱 File Inventory

| 파일 | 역할 | 언제 보는가 | 분류 |
|---|---|---|---|
| `MainViewModel.kt` | sync trigger, 파싱 호출, 저장, refresh orchestration | 기능 전체 변경 | 핵심 |
| `SmsSyncMessageReader.kt` | 동기화 범위 원본 읽기 래퍼 | 원본 누락 | 핵심 |
| `SmsReaderV2.kt` | SMS/MMS/RCS provider read | channel/provider 문제 | 핵심 |
| `SmsSyncCoordinator.kt` | batch parsing 외부 진입점 | 파싱 단계 순서 | 핵심 |
| `SmsPipeline.kt` | Vector/LLM fallback | 미매칭 처리 | 핵심 |
| `CategoryClassifierService.kt` | 저장 전/후 카테고리 분류 | 분류 누락 | 핵심 |
| `ExpenseRepository.kt` | 지출 batch insert/query | 저장 결과 문제 | 핵심 |
| `IncomeRepository.kt` | 수입 batch insert/query | 수입 저장 문제 | 핵심 |
| `DataRefreshEvent.kt` | 화면 갱신 이벤트 | 저장 후 화면 미반영 | 핵심 후보 |
