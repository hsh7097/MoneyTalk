---
type: architecture
title: SMS Pipeline Purpose Architecture
description: SMS Pipeline의 목적과 단계별 책임을 설명한다.
tags: [moneytalk, sms, architecture]
resource: app/src/main/java/com/sanha/moneytalk/core/sms/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# 01 Purpose Architecture

SMS Pipeline은 문자 원본을 거래 데이터로 바꾸는 공통 엔진이다.
핵심 구조는 Regex Fast Path, Vector, LLM fallback을 순서대로 적용하는 것이다.

## 단계

1. `SmsReaderV2` / `SmsSyncMessageReader`: provider 원본 읽기
2. `SmsPreFilter`: 비거래성 SMS 제거
3. `SmsIncomeFilter`: PAYMENT / INCOME / SKIP 분류
4. `SmsRegexRuleMatcher`: 결제 후보 sender regex Fast Path
5. `SmsPipeline`: Fast Path 미매칭 대상 처리
6. `SmsPatternMatcher`: 기존 패턴 벡터 매칭
7. `SmsGroupClassifier` / `GeminiSmsExtractor`: 신규 패턴 LLM 추출과 regex 생성

## 경계

- 수입 SMS는 Fast Path 룰 대상이 아니며 수입 파서 경로를 확인한다.
- 동기화 범위와 coverage는 `core/sync`가 담당한다.
- DB 저장과 중복 보정은 `MainViewModel`과 Repository 경로도 함께 봐야 한다.
