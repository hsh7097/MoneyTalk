# SMS 카드사 룰 커버리지

`app/src/main/assets/sms_rules_v1.json`의 sender 기반 Fast Path 룰 운영 현황입니다.
공개 웹 예시는 서비스 존재 확인에만 사용하고, regex 생성은 `sms_origin outcome=fail` 또는 마스킹 CSV 표본을 기준으로 합니다.

## 운영 기준

- Fast Path 룰 타입은 `expense`, `cancel`, `overseas`, `payment`, `debit`만 사용합니다.
- 수입 SMS는 `SmsIncomeFilter -> SmsIncomeParser` 경로를 사용하며 asset regex 룰에 넣지 않습니다.
- 신규 카드사 룰은 sender/type별 대표 표본 3~5건을 확보한 뒤 추가합니다.
- 룰 추가 후 `SmsRegexRuleAssetTest`에 synthetic 회귀 샘플을 함께 추가합니다.

## 현재 커버리지

| 카드사 | sender | type | 필요 표본 수 | 확보 상태 | 룰 반영 상태 | 테스트 상태 |
|--------|--------|------|--------------|-----------|--------------|-------------|
| KB국민 | 16449999, 15881688 | expense, cancel, overseas | 확보 완료 | asset seed | 반영 완료 | synthetic 회귀 있음 |
| 신한 | 15447200, 15447000 | expense, cancel | 확보 완료 | asset seed | 반영 완료 | synthetic 회귀 있음 |
| 현대 | 15776200 | expense, cancel | 확보 완료 | asset seed | 반영 완료 | synthetic 회귀 있음 |
| 삼성 | 15888900 | expense, cancel | 확보 완료 | asset seed | 반영 완료 | synthetic 회귀 있음 |
| 롯데 | 15888100 | expense, cancel | 확보 완료 | asset seed | 반영 완료 | synthetic 회귀 있음 |
| 우리 | 15889955 | expense | 확보 완료 | asset seed | 반영 완료 | synthetic 회귀 있음 |
| NH농협 | 15881600 | expense, cancel | 확보 완료 | asset seed | 반영 완료 | synthetic 회귀 있음 |
| 스마일카드 | 15220080, 15776000 | expense, cancel | 확보 완료 | asset seed | 반영 완료 | synthetic 회귀 있음 |

## 우선 확장 대상

| 카드사 | sender | type | 필요 표본 수 | 확보 상태 | 룰 반영 상태 | 테스트 상태 |
|--------|--------|------|--------------|-----------|--------------|-------------|
| 하나 | 미확인 | expense, cancel | sender/type별 3~5건 | 필요 | 미반영 | 없음 |
| BC | 미확인 | expense, cancel | sender/type별 3~5건 | 필요 | 미반영 | 없음 |
| IBK기업 | 미확인 | expense, cancel | sender/type별 3~5건 | 필요 | 미반영 | 없음 |
| 카카오뱅크 | 미확인 | expense, cancel | sender/type별 3~5건 | 필요 | 미반영 | 없음 |
| 토스뱅크 | 미확인 | expense, cancel | sender/type별 3~5건 | 필요 | 미반영 | 없음 |
| 씨티 | 미확인 | expense, cancel | sender/type별 3~5건 | 필요 | 미반영 | 없음 |

## 표본 수집 우선순위

1. `sms_origin`의 `outcome=fail`, `failReason=no_active_rule`를 sender별 count 내림차순으로 확인합니다.
2. 동일 sender/type에서 `failureTemplate`이 같은 표본은 하나의 구조로 묶습니다.
3. 표본의 원문이 필요한 경우 마스킹 CSV를 사용하고, 사용자 이름/카드번호/가맹점/금액은 테스트에 더미값으로 치환합니다.
4. 신규 룰은 `ruleKey = sha256(sender|type|bodyRegex|amountGroup|storeGroup|cardGroup|dateGroup|version).take(24)`로 생성합니다.
