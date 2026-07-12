---
type: guide
title: SMS Rule JSON Guide
description: sms_rules_v1.json과 RTDB SMS 룰 overlay 갱신 기준, ruleKey, priority, 검증 절차를 정리한다.
tags: [moneytalk, sms, regex, fast-path, rtdb]
resource: app/src/main/assets/sms_rules_v1.json
timestamp: 2026-07-11T00:00:00+09:00
status: draft
---

# 06 Rule JSON Guide

> 기준: 2026-07-11 현재 `core/sms/**`, `sms_rules_v1.json`, RTDB `sms_origin` export와 런타임 테스트 확인

이 문서는 SMS sender regex Fast Path 룰을 추가하거나 운영 표본으로 갱신할 때 보는 가이드다.
문자 파싱 전체 흐름은 [README.md](README.md)와 [../sms-parsing/README.md](../sms-parsing/README.md)를 먼저 본다.

## 핵심 원칙

- 파싱 1차 경로는 `sender + type + priority` 기반 regex 매칭이다.
- JSON/RTDB 룰이 충분하면 Step 1.5에서 대부분 처리되어 동기화가 빨라진다.
- JSON/RTDB 룰이 없거나 miss가 많으면 기존 embedding/vector/LLM 파이프라인으로 폴백된다.
- 룰 키는 결정적 `ruleKey`를 사용해 동일 룰 중복 누적을 막는다.
- Fast Path 룰은 결제 계열만 다룬다.
- 수입 SMS는 asset regex 룰이 아니라 `SmsIncomeFilter -> SmsIncomeParser` 경로로 처리한다.
- 취소/환불 SMS도 수입 분기에서 처리한다. `cancel` asset 룰은 과거 호환 데이터일 뿐 Fast Path 실행 대상이 아니다.
- 정규식이 표본과 일치하는 것과 실제 런타임에서 해당 룰에 도달하는 것은 별도로 검증한다.

## 런타임 적용 순서

```text
sms_rules_v1.json asset load
-> RTDB sms_rules overlay
-> 결제 후보 SMS에 Step 1.5 SenderRegex 적용
   -> sms_regex_rules(asset/rtdb/local_learned)
   -> sms_patterns sender 보조 regex(amountRegex/storeRegex)
-> Fast Path miss만 SmsPipeline embedding/vector/LLM 폴백
```

RTDB overlay에서 동일 key가 있으면 RTDB 룰이 asset 룰을 덮어쓴다.
`income`, `cancel` 타입 룰은 새로 추가하지 않는다. 기존 asset/DB에 남은 `cancel` 룰은 `SmsRegexRuleMatcher` 허용 타입 필터에서 제외되며, 취소 본문은 그보다 앞선 `SmsIncomeFilter`와 `AppNotificationTypeClassifier`에서 `INCOME`으로 분기된다.

주의: MoneyTalk에는 RTDB regex 경로가 두 갈래 있다.
Step 1.5 Fast Path overlay는 `/sms_rules/{sender}/{type}/{ruleKey}` 구조를 쓰고, 벡터 매칭 보조 원격 룰은 `RemoteSmsRuleRepository`의 `/sms_regex_rules/v1/{sender}/{ruleId}` 구조를 쓴다.
`sms_rules_v1.json` 갱신 작업에서는 이 둘을 섞지 않는다.

`/sms_regex_rules/v1`의 `embedding`은 768차원이라는 사실만으로 현재 `SmsEmbeddingService`의 local feature-hash vector와 호환되지 않는다. Gemini embedding 시절 생성한 원격 vector는 유사도 점수가 의미 없으므로 enabled 운영 룰로 신뢰하지 않는다. 현재 release의 파싱 SSOT는 asset/RTDB `/sms_rules` sender regex와 이후 안전한 LLM fallback이다. 원격 벡터 룰을 다시 사용하려면 현재 local 알고리즘으로 전부 재생성하고, 향후 schema에 `embeddingContract` 같은 명시적 버전을 추가한 뒤 버전 불일치 룰을 loader에서 거부해야 한다.

## JSON 구조

경로는 `sms_rules/{sender}/{type}/{ruleKey}`를 사용한다.

```json
{
  "sms_rules": {
    "16449999": {
      "expense": {
        "edf33275a7a1edd209939b31": {
          "type": "expense",
          "priority": 915,
          "bodyRegex": "(?s)...",
          "amountGroup": "amount",
          "storeGroup": "store",
          "cardGroup": "card",
          "dateGroup": "date",
          "status": "ACTIVE",
          "source": "asset",
          "version": 1
        }
      }
    }
  }
}
```

JSON 주석은 사용할 수 없다.
룰 비활성화가 필요하면 삭제보다 `status: "INACTIVE"`를 먼저 고려한다.

## Fast Path 허용 타입

| type | 의미 |
|---|---|
| `expense` | 출금/결제/승인 |
| `overseas` | 해외승인/해외결제 |
| `payment` | 운영/RTDB 호환용 결제 계열 타입 |
| `debit` | 운영/RTDB 호환용 결제 계열 타입 |

sender 내에서는 `priority DESC`로 룰을 순차 시도하고, 첫 성공 룰의 type이 최종 type이 된다.
런타임은 sender/type별 ACTIVE 룰을 우선순위 순으로 최대 5개만 유지하므로, 감사 스크립트도 같은 상한을 적용한다.
`cancel`은 허용 타입이 아니다. 기존 JSON의 ACTIVE cancel 노드는 로더 단계에서 걸러지는 dormant 데이터이며, 정리 작업 전까지 구조 검증 대상으로만 남을 수 있다.

## ruleKey 생성

같은 룰은 어느 기기나 작업자에게서도 같은 key가 되어야 한다.

```text
keyInput = sender|type|bodyRegex|amountGroup|storeGroup|cardGroup|dateGroup|version
ruleKey = sha256(keyInput) 앞 24자리 hex
```

PowerShell에서는 별도 스크립트나 해시 유틸을 써도 되지만, 결과가 위 입력식과 일치해야 한다.

## priority 기준

| 범위 | 용도 |
|---|---|
| 900~950 | 빈도 높고 안정적인 대표 형식 |
| 750~890 | 파생/예외 형식 |
| 600~740 | 희귀 형식 |

운영 중 match/fail 통계에 따라 local learned 룰의 우선순위가 보정될 수 있다.
asset 초기값은 위 범위에서 시작한다.

## RTDB 표본 정책

`sms_origin`은 룰 개선에 필요한 성공/실패 표본을 제한적으로 수집하는 운영 진단 데이터다.
release 빌드도 표본을 수집하며, 원문 포함 여부는 RTDB `/config/send_origin_message`로 제어한다.

| 표본 | 경로 | 정책 |
|---|---|---|
| 성공 표본 | `/sms_origin/{sender}/{type}/{sampleKey}` | sender+type 버킷당 세션 기준 고유 fingerprint 최대 3개 |
| 실패 표본 | `/sms_origin/{sender}/{type}/{sampleKey}` | Fast Path 실패 사유와 함께 fingerprint 기준 upsert. 동일 fingerprint는 같은 row의 count/lastSeen을 갱신 |

template/masked body는 `SmsSensitiveDataSanitizer`를 거쳐 사용자명과 계좌/카드 식별정보를 최소화한다.
`send_origin_message=true`이면 `originBody` 원문도 포함하고, `false`이면 신규 원문 저장을 중단하며 다시 관측된 row의 `originBody`를 제거한다.
단, `false` 전환만으로 과거 모든 row가 즉시 삭제되지는 않으므로 수집 종료 후 RTDB 일괄 정리가 필요하다.

운영 갱신에서는 `outcome=fail` 표본을 `count` 높은 순서로 우선 처리한다.
분석 전 `scripts/sms_origin_rule_audit.py`로 현재 asset 커버 여부와 비거래 후보를 먼저 분리한다.
실제 import 이력과 보류 판단은 [07-rtdb-sms-origin-import-log.md](07-rtdb-sms-origin-import-log.md)를 본다.

### `sms_origin` 후보 승격 금지 조건

`sms_origin` failure sample에는 `bodyRegex`, `ruleKey`가 들어 있을 수 있지만 이는 collector가 만든 후보 shape일 뿐이다.
아래 조건 중 하나라도 해당하면 asset rule 또는 RTDB `/sms_rules`로 승격하지 않는다.

| 조건 | 이유 |
|---|---|
| 카드대금 예정/청구서/명세서/결제일 안내 | 실제 출금이 완료되지 않은 안내이므로 거래가 아님 |
| 교통카드 N건 합산, 출금예정 | 개별 거래가 아니라 합산 또는 예정 알림 |
| KSNET 마이장부, 매출접수/매출집계 | 사용자 지출이 아니라 가맹점/정산 요약 성격 |
| `storeGroup` 결과가 `출`, `결제대금`, `이용대금`, `교통카드`, `매출접수` 같은 흐름 토큰 | 상호가 아니므로 이후 카테고리/통계가 오염됨 |
| 마스킹 사용자명, 계좌번호, 카드번호, 특정 상호가 literal로 고정된 regex | 다른 사용자/다른 거래에서 miss 또는 개인정보성 패턴 고착 위험 |
| `USD`, `GBP` 등 외화 금액만 있는 해외승인 | 현재 원화 `Int` 모델에는 통화 코드/환율 필드가 없어 외화 금액을 원화로 오인할 수 있음 |
| 승인 금액이 `0원`인 알림 | 런타임 계약이 `amount > 0`이므로 고신뢰 비거래로 SKIP |
| 쇼핑 금액과 입금계좌만 있는 안내 | 승인/출금/사용 완료 근거가 없어 고신뢰 비거래로 SKIP |

### 카드대금 완료 출금 예외

카드대금/결제대금/이용대금/이용금액 문구를 전부 버리면 실제 계좌 출금 기록이 사라진다. 아래 조건을 모두 만족하는 완료 출금은 `expense` 룰로 허용한다.

1. 카드대금 계열 문구와 `출금` 또는 `인출` 완료 표현이 함께 있다.
2. 실제 출금액이 `원` 단위로 명시되어 있다.
3. `예정`, `명세서`, `청구서`, `납부안내`, `결제일` 문구가 없다.
4. 승인/일시불/할부/가맹점 사용 알림이 아니다.
5. 여러 금액이 있으면 총 청구액이 아니라 실제 완료 출금액을 `amount`로 캡처한다.
6. `storeGroup`과 `cardGroup`에는 `결제대금` 같은 흐름 토큰 대신 카드사 식별자를 사용한다.

이 거래는 저장하되 `StatsExclusionClassifier`가 `isExcludedFromStats=true`로 처리한다. 카드 승인 시점에 이미 소비로 잡힌 금액을 월 지출에 중복 합산하지 않기 위한 계약이다.

서비스 수수료처럼 실제 지출명이 흐름 키워드를 포함하는 예외는 별도 판단한다.
예: `입출금알림수수료`는 수수료 지출명으로 허용할 수 있지만, 일반 store 캡처가 `출금` 또는 `교통카드`만 잡히는 것은 허용하지 않는다.

## 업데이트 절차

1. 입력 표본을 준비한다.
   - 초기 구축: `moneytalk_backup_*.csv`의 `유형`, `전화번호`, `문자원본`, 선택 `카드/출처`
   - 운영 갱신: Firebase RTDB `sms_origin` JSON export
2. `SmsPreFilter -> SmsIncomeFilter` 기준으로 입력 정책을 먼저 분류한다. 취소/환불, 예정/요약 알림은 룰 후보에서 제외한다.
3. sender/type별로 그룹화한다.
4. 같은 구조인데 가게명/금액/날짜만 다른 표본은 하나의 룰로 통합한다.
5. `failureTemplate` placeholder를 generic regex로 변환한다. `{USER_NAME}`, `{ACCOUNT_OR_CARD}`는 샘플 literal이 아니라 비식별화 토큰이므로 실제 이름/숫자/마스킹 구조로 일반화한다.
   - `{AMOUNT}` -> `(?<amount>[\\d,]+)`
   - `{DATE}` -> `(?<date>\\d{2}/\\d{2})`
   - `{TIME}` -> `\\d{2}:\\d{2}`
   - `{N}` -> `\\d+`
   - `{CARD_NO}` -> `\\d+\\*+\\d+`
   - `{USER_NAME}` -> `\\S+`
   - `{ACCOUNT_OR_CARD}` -> 숫자/하이픈/공백/마스킹 기호를 허용하는 계좌·카드 패턴
   - 하드코딩 가게명 -> `(?<store>[^\\n]+)` 또는 `(?<store>.+?)`
6. `amountGroup`, `storeGroup`을 반드시 지정한다.
7. 가능하면 Java regex named group을 쓴다.
8. `(?s)` prefix로 DOTALL mode를 명시한다.
9. 결정적 `ruleKey`와 `priority`를 정한다.
10. `app/src/main/assets/sms_rules_v1.json`에 반영한다.
11. raw regex, runtime matcher, 입력 필터를 함께 검증한 뒤 match 증가와 fallback 감소를 확인한다.

## 검증

```powershell
python scripts/sms_origin_rule_audit.py --origin <sms_origin-export.json> --asset app/src/main/assets/sms_rules_v1.json
python scripts/sms_origin_rule_audit.py --origin <sms_origin-export.json> --asset app/src/main/assets/sms_rules_v1.json --all-outcomes --min-pipeline-success-rate 92
.\gradlew.bat :app:testDebugUnitTest --tests com.sanha.moneytalk.core.sms.SmsRegexRuleAssetTest --tests com.sanha.moneytalk.core.sms.SmsRegexRuleMatcherAssetIntegrationTest --tests com.sanha.moneytalk.core.sms.SmsNonTransactionNoticeFilterTest
.\gradlew.bat :app:assembleDebug
```

감사 결과는 표본 row와 `count` 누적 관측치를 구분해서 읽는다.

| 항목 | 의미 |
|---|---|
| `current_asset_matched` | regex 일치 후 금액 `> 0`과 유효 store 검증까지 통과한 표본 row 수 |
| `current_asset_matched_observations` | 위 row의 `count` 합 |
| `non_transaction` | 광고/요약/예정 등 비거래로 차단할 row 수 |
| `routed_income` | 취소/입금으로 판별되어 `SmsIncomeFilter -> SmsIncomeParser`에서 처리되는 row 수 |
| `unsupported_type` | `cancel`, `income` 등 Fast Path 미지원 row 수 |
| `unsupported_currency` | 현재 원화 `Int` 모델로 안전하게 저장할 수 없는 외화 row 수 |
| `insufficient_evidence` | 금액은 있으나 승인/출금 완료 근거가 없는 row 수 |
| `invalid_amount` | regex 구조는 맞지만 `amount <= 0`이라 거부한 row 수 |
| `policy_false_positive` | 제외 대상인데 asset 룰이 매칭한 오류 row 수. 반드시 0이어야 함 |
| `actionable_unmatched` | 위 분류 후 실제 수동 룰 검토가 필요한 row 수 |
| `valid_candidate_success_rate` | `matched / (matched + actionable_unmatched)` row 성공률 |
| `pipeline_handled` | 지출 Fast Path 성공 + 수입 처리 + 고신뢰 SKIP row 수 |
| `pipeline_success_rate` | 전체 audit scope 중 `pipeline_handled` 비율 |
| `pipeline_unhandled` | 어느 안전한 앱 경로에서도 처리하지 못한 row 수 |

`--all-outcomes`를 주면 RTDB의 과거 `outcome`과 무관하게 전체 snapshot을 현재 룰로 다시 평가한다.
지출 Fast Path 성공률과 전체 파이프라인 처리율을 구분한다. 취소는 수입, 0원/요약/미완료 안내는 SKIP으로 처리해야 하며, 이들을 지출 룰에 포함해 Fast Path 수치를 높이면 안 된다. 외화 단독 금액은 통화 모델이 생기기 전까지 `pipeline_unhandled`로 남긴다.
2026-07-11 기준 snapshot의 전체 파이프라인 처리율은 `186/192`(96.9%)이며, 92% 이상을 회귀 기준으로 사용한다.

로그에서는 아래 값을 본다.

```text
Asset 룰 로드 완료: N건
RTDB 룰 로드 완료: M건
Step1.5 SenderRegex: 매칭 X건, 폴백 Y건
```

좋은 상태는 `X`가 크고 `Y`가 작은 상태다.
`X=0`에 가깝고 `Y`가 결제 후보 수와 유사하면 Fast Path가 작동하지 않는 상태다.

## 품질 체크리스트

- sender는 normalize된 숫자 기준인가?
- `amountGroup`, `storeGroup`이 실제 캡처되는가?
- type이 `expense`, `overseas`, `payment`, `debit` 중 하나인가?
- `ruleKey`가 결정적 입력식의 SHA-256 앞 24자리와 일치하는가?
- 오탐 가능성이 큰 과도한 `.*`를 피했는가?
- 동일 sender/type에서 중복 룰이 늘어나지 않는가?
- 신규 룰 후 기존 주요 sender 매칭률이 유지 또는 개선되는가?
- `dateGroup`이 없는 룰은 날짜 없는 SMS 구조에만 사용했는가?
- 같은 sender의 다른 type 룰과 오분류 가능성이 없는가?
- 완료된 카드대금 출금은 실제 출금액을 잡고 통계 제외되는가?
- 외화 금액을 원화 `Int`로 오인하거나 0원 거래를 생성하지 않는가?
- `policy_false_positive=0`, `actionable_unmatched=0`인가?
- 취소/환불은 Fast Path가 아니라 수입 경로로 가는가?
- 교통/KSNET/매출접수 N건 요약이 입력 단계와 LLM 이전 방어 단계에서 모두 차단되는가?
