---
type: guide
title: SMS Rule JSON Guide
description: sms_rules_v1.json과 RTDB SMS 룰 overlay 갱신 기준, ruleKey, priority, 검증 절차를 정리한다.
tags: [moneytalk, sms, regex, fast-path, rtdb]
resource: app/src/main/assets/sms_rules_v1.json
timestamp: 2026-07-09T03:10:00+09:00
status: draft
---

# 06 Rule JSON Guide

> 기준: 2026-07-09 현재 흡수된 SMS 룰 JSON 운영 원문, `core/sms/**`, `app/src/main/assets/sms_rules_v1.json` 운영 기준 확인

이 문서는 SMS sender regex Fast Path 룰을 추가하거나 운영 표본으로 갱신할 때 보는 가이드다.
문자 파싱 전체 흐름은 [README.md](README.md)와 [../sms-parsing/README.md](../sms-parsing/README.md)를 먼저 본다.

## 핵심 원칙

- 파싱 1차 경로는 `sender + type + priority` 기반 regex 매칭이다.
- JSON/RTDB 룰이 충분하면 Step 1.5에서 대부분 처리되어 동기화가 빨라진다.
- JSON/RTDB 룰이 없거나 miss가 많으면 기존 embedding/vector/LLM 파이프라인으로 폴백된다.
- 룰 키는 결정적 `ruleKey`를 사용해 동일 룰 중복 누적을 막는다.
- Fast Path 룰은 결제 계열만 다룬다.
- 수입 SMS는 asset regex 룰이 아니라 `SmsIncomeFilter -> SmsIncomeParser` 경로로 처리한다.

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
`income` 타입 룰은 asset에 넣지 않는다. 기존 DB에 남아 있어도 Fast Path 허용 타입 필터와 payment 후보 입력 때문에 실행 대상이 아니다.

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
| `cancel` | 승인취소/취소완료 |
| `overseas` | 해외승인/해외결제 |
| `payment` | 운영/RTDB 호환용 결제 계열 타입 |
| `debit` | 운영/RTDB 호환용 결제 계열 타입 |

sender 내에서는 `priority DESC`로 룰을 순차 시도하고, 첫 성공 룰의 type이 최종 type이 된다.

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

`sms_origin`은 모든 SMS 전건 업로드가 아니라, 룰 개선에 필요한 성공/실패 표본을 제한적으로 수집하는 운영 데이터다.

| 표본 | 경로 | 정책 |
|---|---|---|
| 성공 표본 | `/sms_origin/{sender}/{type}/{sampleKey}` | regex 검증 성공 표본만 전송. sender+type 버킷당 세션 기준 고유 fingerprint 최대 3개 |
| 실패 표본 | `/sms_origin/{sender}/{type}/{sampleKey}` | Fast Path 실패 사유와 함께 fingerprint 기준 upsert. 동일 fingerprint 24시간 쿨다운, sender/type/failStage/failReason 버킷당 일 최대 5건 |

운영 갱신에서는 `outcome=fail` 표본을 `count` 높은 순서로 우선 처리한다.
분석 전 `scripts/sms_origin_rule_audit.py`로 현재 asset 커버 여부와 비거래 후보를 먼저 분리한다.

## 업데이트 절차

1. 입력 표본을 준비한다.
   - 초기 구축: `moneytalk_backup_*.csv`의 `유형`, `전화번호`, `문자원본`, 선택 `카드/출처`
   - 운영 갱신: Firebase RTDB `sms_origin` JSON export
2. sender/type별로 그룹화한다.
3. 같은 구조인데 가게명/금액/날짜만 다른 표본은 하나의 룰로 통합한다.
4. `failureTemplate` placeholder를 generic regex로 변환한다.
   - `{AMOUNT}` -> `(?<amount>[\\d,]+)`
   - `{DATE}` -> `(?<date>\\d{2}/\\d{2})`
   - `{TIME}` -> `\\d{2}:\\d{2}`
   - `{N}` -> `\\d+`
   - `{CARD_NO}` -> `\\d+\\*+\\d+`
   - 하드코딩 가게명 -> `(?<store>[^\\n]+)` 또는 `(?<store>.+?)`
5. `amountGroup`, `storeGroup`을 반드시 지정한다.
6. 가능하면 Java regex named group을 쓴다.
7. `(?s)` prefix로 DOTALL mode를 명시한다.
8. 결정적 `ruleKey`와 `priority`를 정한다.
9. `app/src/main/assets/sms_rules_v1.json`에 반영한다.
10. 검증 후 match 증가와 fallback 감소를 로그로 확인한다.

## 검증

```powershell
python scripts/sms_origin_rule_audit.py --origin <sms_origin-export.json> --asset app/src/main/assets/sms_rules_v1.json
.\gradlew.bat :app:testDebugUnitTest --tests com.sanha.moneytalk.core.sms.SmsRegexRuleAssetTest
.\gradlew.bat :app:assembleDebug
```

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
- type이 Fast Path 허용 타입인가?
- `ruleKey`가 결정적 입력식의 SHA-256 앞 24자리와 일치하는가?
- 오탐 가능성이 큰 과도한 `.*`를 피했는가?
- 동일 sender/type에서 중복 룰이 늘어나지 않는가?
- 신규 룰 후 기존 주요 sender 매칭률이 유지 또는 개선되는가?
- `dateGroup`이 없는 룰은 날짜 없는 SMS 구조에만 사용했는가?
- 같은 sender의 다른 type 룰과 오분류 가능성이 없는가?
