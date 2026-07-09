---
type: reference
title: SMS Ingestion Contract
description: SMS/MMS/RCS 읽기, 중복 제거, 파싱, 수입/지출 저장, 실시간 보완 경계를 설명한다.
tags: [moneytalk, sms, parsing, ingestion, sync]
resource: app/src/main/java/com/sanha/moneytalk/core/sms/
timestamp: 2026-07-09T05:50:00+09:00
status: draft
---

# SMS Ingestion Contract

> 기준: 흡수된 SMS 파싱 원문과 project-context SMS 파트를 KB용으로 재작성

이 문서는 SMS/MMS/RCS 원본이 앱의 지출/수입 거래로 저장되기까지의 end-to-end 계약을 설명한다. 내부 regex/vector/LLM 단계의 자세한 구현은 [../sms-pipeline/README.md](../sms-pipeline/README.md)를 본다.

## 전체 흐름

```text
trigger
-> SmsSyncRangeCalculator
-> SmsSyncMessageReader / SmsReaderV2
-> 중복 제거, 발신자 필터
-> SmsSyncCoordinator.process()
   -> SmsPreFilter
   -> SmsIncomeFilter
   -> SmsRegexRuleMatcher
   -> SmsPipeline
-> ExpenseEntity / IncomeEntity 저장
-> SyncCoverageRecorder
-> DataRefreshEvent
```

## 메시지 읽기

| 유형 | URI | 날짜 단위 | 비고 |
|---|---|---|---|
| SMS | `content://sms/inbox` | millisecond | 표준 문자 |
| MMS | `content://mms/inbox` | second | 읽은 뒤 millisecond로 보정 |
| RCS | `content://im/chat` | millisecond | 삼성 기기 provider, JSON 본문 |

`SmsReaderV2`는 기존 중간 모델 대신 `SmsInput`을 직접 반환한다. SMS provider 조회 실패를 빈 목록으로 취급하지 않고 실패로 전파해 `lastSyncTime`을 잘못 갱신하지 않도록 한다.

SMS ID는 `발신번호_수신시간_본문해시코드` 조합으로 만들고, 기존 지출/수입 `smsId`와 pending 즉시 저장 상태를 기준으로 중복 저장을 막는다.

## 발신자/본문 필터

| 단계 | 책임 |
|---|---|
| `SmsFilter.normalizeAddress()` | `+82`, 하이픈, 공백 제거 |
| `SmsFilter.shouldSkipBySender()` | 010/070 발신자라도 금융 힌트가 없으면 제외 |
| `SmsPreFilter` | 인증, 보안, 광고, 배송, 통신 단가 안내, 과도하게 긴 본문 등 비결제 제거 |
| 사용자 제외 키워드 | 설정의 SMS 제외 키워드를 `SmsIncomeFilter`에 전달 |

010/070 필터는 금융 힌트가 있으면 보존한다. 발신번호만 보고 결제 가능성을 완전히 버리면 실사용 문자 누락이 생길 수 있다.

## 수입/지출 분기

`SmsIncomeFilter`가 금융기관 키워드와 금액 패턴을 기준으로 `PAYMENT`, `INCOME`, `SKIP`을 분류한다.

| 분기 | 이후 처리 |
|---|---|
| PAYMENT | `SmsRegexRuleMatcher` Fast Path 후 미매칭만 `SmsPipeline`으로 보낸다. |
| INCOME | Fast Path 대상이 아니며 호출자가 `SmsIncomeParser`와 `SmsTransactionDateResolver`를 통해 `IncomeEntity`로 저장한다. |
| SKIP | 저장하지 않는다. |

## 결제 파싱 3-tier

| Tier | 책임 | 비용 |
|---|---|---|
| Regex Fast Path | sender 기반 asset seed + RTDB overlay regex 룰 매칭 | 무료 |
| Vector | 템플릿 embedding과 `SmsPatternEntity` 유사도 매칭 | embedding |
| Gemini LLM | 신규/불안정 패턴 batch 추출, regex 생성, 패턴 등록 | Gemini |

Fast Path 허용 타입은 `expense`, `cancel`, `overseas`, `payment`, `debit`다. `income` 룰은 asset에 두지 않는다.

## 날짜/시간 해석

`SmsTransactionDateResolver`가 SMS 본문의 거래 날짜/시간을 공통 해석한다. 본문에 full date가 있으면 우선하고, 연도 없는 `MM/DD`는 SMS 수신일과 가장 가까운 연도로 보정한다.

## 실시간 보완 경로

| 진입점 | 역할 |
|---|---|
| `SmsReceiver` | SMS broadcast를 `goAsync`로 받아 실시간 1건 처리 |
| `MmsContentObserver` | MMS provider 실시간 감시 |
| `RcsContentObserver` | RCS provider 실시간 감시 |
| `NotificationTransactionService` | 메시지 앱 알림에서 후보를 추출하고 provider 원문으로 보강 |
| `NotificationContentParser` | 알림 텍스트에서 거래 후보 추출 |

실시간 수신은 광고/크레딧과 무관하게 항상 무료로 동작해야 한다. 알림 기반 보완은 앱 자체 노티 표시와 별개로 메시지 앱 알림을 관찰해 SMS provider 누락을 줄이는 보조 경로다.

## Coverage와 CTA

월별 coverage는 `SyncCoverageRecorder`가 성공한 동기화 구간을 기록하고, `SyncCoveragePagePolicy`가 과거 월 CTA 표시 여부를 판단한다. 이미 coverage가 충분한 월은 같은 월을 다시 광고/크레딧으로 해제할 필요가 없다.

## 회귀 검증 기준

1. 월별 읽기 순서가 달라도 저장 대상 SMS ID 집합이 같아야 한다.
2. 거래월별 PAYMENT/INCOME 집계가 같아야 한다.
3. 수신월과 거래월이 다른 환불/취소 케이스를 포함해야 한다.
4. SMS provider 실패 시 `lastSyncTime`이 갱신되지 않아야 한다.
5. 실시간 저장과 배치 동기화가 같은 SMS를 중복 저장하지 않아야 한다.
