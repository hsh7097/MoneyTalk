---
type: worklog
title: RTDB sms_origin Import Log
description: Firebase RTDB sms_origin export를 sms_rules_v1.json과 SMS 파이프라인 개선에 반영한 이력을 기록한다.
tags: [moneytalk, sms, rtdb, regex, import-log]
resource: app/src/main/assets/sms_rules_v1.json
timestamp: 2026-07-11T00:00:00+09:00
status: draft
---

# 07 RTDB sms_origin Import Log

> 기준: 2026-07-11 로컬 export, `scripts/sms_origin_rule_audit.py`, 런타임 필터/매처 테스트

이 문서는 RTDB `sms_origin` export를 삭제하기 전, 앱 asset rule과 파싱 정책에 어떤 정보만 흡수했는지 남기는 작업 로그다.
원본 SMS 본문(`originBody`)은 개인정보 가능성이 있으므로 문서와 git diff에 남기지 않는다.
release 빌드도 `sms_origin` 표본을 업로드한다. 원문은 `/config/send_origin_message=true`인 수집 기간에만 포함하며, 수집 종료 후 기존 `originBody`를 별도 정리해야 한다.

## 입력 데이터와 검증 범위

| 항목 | 내용 |
|---|---|
| RTDB URL | `https://moneytalk-258e5-default-rtdb.firebaseio.com` |
| 직접 조회 결과 | `/sms_rules.json?shallow=true`는 `null`, `/sms_origin.json?shallow=true`는 인증 필요로 401 |
| Firebase CLI | 설치되어 있으나 현재 로컬 로그인 계정 없음 |
| 사용 export | `C:\Users\hsh70\Downloads\moneytalk-258e5-default-rtdb-sms_origin-export.json` |
| export SHA-256 | `E9674D9E65F6A1A9DF11EB9AC07D001E27C175156A045F76C494BF24B639885C` |
| 검증 한계 | live RTDB 상태가 아니라 위 export snapshot을 기준으로 검증 |

로컬 export 기준 표본 수와 `count` 누적 관측치는 다음과 같다.

| 구분 | row | 관측치 합 |
|---|---:|---:|
| 전체 | 192 | 676 |
| `outcome=fail` | 150 | 624 |
| `outcome=success` | 42 | 52 |
| `type=expense` | 172 | 639 |
| `type=cancel` | 10 | 24 |
| `type=overseas` | 9 | 12 |
| `type=income` | 1 | 1 |

## 최종 반영 룰

`sms_rules_v1.json`에는 RTDB failure sample의 후보 `bodyRegex`를 그대로 승격하지 않고, 실제 본문 구조가 명확하고 runtime PAYMENT 경로에 도달하는 sender/type만 수동 일반화했다.

| sender | type | ruleKey | 판단 |
|---|---|---|---|
| `18001111` | `expense` | `5bef52a9f844cbfc0339c1ac` | 하나카드 금액/카드/사용처/거래시간 멀티라인 |
| `15998800` | `expense` | `91f95b7d2bf2d200abe8f317` | 롯데법인/롯데법인카드 승인 멀티라인과 카드명 뒤 선택 공백 |
| `15884000` | `expense` | `1565223af5283ac74b5f60f6` | 부산BC 사용 멀티라인. N건 매출접수 요약은 별도 필터가 차단 |
| `15446200` | `expense` | `0d149abfd63bb02706af8c69` | 계좌 출금 메모형 |
| `15882100` | `expense` | `4e421ce19f9d3d552dc3acfa` | 농협 출금형 |
| `15881688` | `expense` | `a37ad62d9240a88ad4beaabd` | KB국민카드 앱 승인 멀티라인 |
| `15661000` | `expense` | `1d1af6a754a74f7e9e6a3aca` | 씨티카드 승인 멀티라인 |
| `15881600` | `expense` | `24d30563f49ec9994ff38023` | NH카드 신용승인 멀티라인 |
| `15999000` | `expense` | `01bb9b9616169381fa57cc4b` | 새마을금고 입출금알림수수료 출금 |
| `15888100` | `expense` | `7b3f44c22a4da8f431401199` | 롯데카드 결제대금 중 실제 완료 출금액 캡처, 카드사 식별자를 store/card로 사용 |
| `15447000` | `expense` | `2992755ac37835d696874c8a` | 신한카드 결제대금 중 실제 완료 인출액 캡처, 카드사 식별자를 store/card로 사용 |
| `15888900` | `expense` | `34ad98c69ef3361f0929cf8d` | 삼성 승인에서 일시불과 N개월 할부를 모두 허용 |
| `15882100` | `expense` | `cb3d3557b756915e5e8c8103` | 농협 자동출금에서 양수/음수 잔액 표기를 모두 허용 |
| `15998800` | `expense` | `2714e3fa1d66add7b5ae1090` | 롯데법인 이용금액의 기업은행 실제 출금액을 캡처 |
| `15998800` | `overseas` | `c8a381599123686479685267` | KRW로 명시된 롯데법인 해외승인만 원화 Fast Path 처리 |
| `15993333` | `expense` | `b8b27604d590f5fea1de7cc2` | 카카오뱅크 카드결제 멀티라인 |

기존 완료 카드대금 출금 5개 failure row, 관측치 204건과 롯데법인 이용금액 출금 1개 row, 1건은 완료 출금 룰로 커버된다. 저장된 거래는 `StatsExclusionClassifier`에 의해 통계에서 제외되므로 카드 승인 소비와 중복 합산되지 않는다.

## 승격하지 않은 유형

| 유형 | 처리 | 이유 |
|---|---|---|
| 교통카드/하이패스 N건 합산 | `SKIP` | 개별 거래가 아닌 합산 알림 |
| KSNET 마이장부 N건 승인 | `SKIP` | 사용자 지출이 아닌 가맹점 매출 요약 |
| 매출접수 N건 집계 | `SKIP` | 개별 거래 근거와 유효 store가 없음 |
| 카드대금 예정/명세서/청구서 | `SKIP` | 실제 출금 완료 전 안내 |
| 취소/환불 | `INCOME` | 반환 금액 경로이며 PAYMENT Fast Path 대상이 아님 |
| 날짜/가맹점 근거가 없는 단문 | 수동 보류 | 금액만으로 거래 확정 불가 |
| `USD`, `GBP` 외화 해외승인 | 모델 보강 전 보류 | 현재 금액 모델은 원화 `Int`이며 통화 코드/환율을 저장하지 못함 |
| 승인 금액 `0원` | `SKIP` | runtime `amount > 0` 계약에 따라 고신뢰 비거래로 차단 |
| 무료체험/가입 상품권 광고 | `SKIP` | 거래 완료가 아닌 프로모션 |
| 홈쇼핑 금액/입금계좌 단문 | `SKIP` | 승인·출금·사용 완료 문구가 없는 결제 안내 |
| `storeGroup`이 한 글자/흐름 토큰 | 수동 보류 | `출`, `결제대금`, `매출접수` 등은 상호가 아님 |

최초 반영안의 스마일카드 `cancel` 신규 룰은 제거했다. asset에 기존 ACTIVE `cancel` 노드 16개가 남아 있지만 `SmsRegexRuleMatcher`가 허용 타입에서 제외하므로 runtime에서는 dormant다. 별도 자산 정리 시 다른 소비자가 없는지 다시 확인한 뒤 제거한다.

## 함께 반영한 파싱 개선

- `SmsNonTransactionNoticeFilter`가 교통/KSNET/매출접수 N건 요약을 고신뢰 비거래로 판별한다.
- 무료체험/가입 상품권 프로모션은 `지급` 수입 보호 키워드보다 먼저 고신뢰 비거래로 차단한다.
- 0원 승인과 승인/출금 완료 없는 쇼핑 입금계좌 안내도 LLM 전에 고신뢰 SKIP한다.
- `cancel`/`income` 11개 row는 지출 룰이 아니라 `SmsIncomeFilter -> SmsIncomeParser`에서 금액과 환불/입금 유형을 처리한다.
- KB 숫자 단독 입금의 출처에 포함된 균형 괄호를 보존한다.
- 완료된 카드대금 출금은 위 필터의 예외로 보존하고, `SmsIncomeFilter`가 `PAYMENT/cardBillDebit`로 분기한다.
- `SmsGroupClassifier`는 위 요약 패턴을 LLM 호출과 소그룹 template fallback 전에 다시 차단한다.
- 거래 추출, regex 생성, compact/ultra, repair 프롬프트를 같은 카드대금/요약 정책으로 통일했다.
- `SmsRegexRuleMatcher` Fast Path 허용 타입에서 `cancel`을 제거했다.
- `SmsOriginSampleCollector`는 미지원 타입과 비거래 template에 rule shape를 만들지 않고, 기존 RTDB row의 `bodyRegex/ruleKey`도 null update로 삭제한다.
- 감사 스크립트는 runtime 허용 타입만 매칭하고 row 수와 `count` 가중 관측치를 함께 출력한다.
- 감사 스크립트 출력은 Windows 기본 콘솔에서도 중단되지 않도록 ASCII 구분자를 사용한다.

## 최종 감사 결과

감사 스크립트는 앱과 같이 sender의 허용 타입 룰을 priority 순으로 시도하고, regex 일치뿐 아니라 `amount > 0`과 유효 store까지 검증한다. 전체 snapshot을 현재 룰로 재평가한 결과는 다음과 같다.

```text
sample_scope: all
samples: 192
observations: 676
current_asset_matched: 165
current_asset_matched_observations: 597
non_transaction: 10 / 45 observations
routed_income: 11 / 25 observations
unsupported_type: 0 / 0 observations
unsupported_currency: 6 / 9 observations
insufficient_evidence: 0 / 0 observations
invalid_amount: 0 / 0 observations
policy_false_positive: 0 / 0 observations
actionable_unmatched: 0 / 0 observations
valid_candidate_success_rate: 100.0%
valid_candidate_observation_success_rate: 100.0%
pipeline_handled: 186
pipeline_handled_observations: 667
pipeline_unhandled: 6
pipeline_unhandled_observations: 9
pipeline_success_rate: 96.9%
pipeline_observation_success_rate: 98.7%
```

지출 Fast Path만 보면 `165/192`(85.9%), `597/676`(88.3%)다. 하지만 앱의 실제 성공은 지출 저장만이 아니라 취소/입금의 수입 저장과 비거래 SKIP도 포함한다. 세 경로를 합친 전체 처리율은 `186/192`(96.9%), 관측치는 `667/676`(98.7%)로 목표 92%를 넘는다.

미처리 6개 row, 9건은 모두 USD/GBP 외화 승인이다. `SmsAnalysisResult.amount`와 `ExpenseEntity.amount`가 원화 `Int`만 저장하므로 통화 코드/외화 금액/환율 필드 없이 승격하면 금액이 오염된다. 외화 모델과 Room 마이그레이션을 도입하기 전까지 의도적으로 미처리한다.

과거 `outcome=fail` 150개 row만 보면 지출 129, 비거래 SKIP 7, 수입 9로 145개 row/616건을 처리한다. 남은 외화는 5개 row/8건이다.

## 검증 명령

```powershell
python scripts/sms_origin_rule_audit.py --origin C:\Users\hsh70\Downloads\moneytalk-258e5-default-rtdb-sms_origin-export.json --asset app\src\main\assets\sms_rules_v1.json --limit 30
python scripts/sms_origin_rule_audit.py --origin C:\Users\hsh70\Downloads\moneytalk-258e5-default-rtdb-sms_origin-export.json --asset app\src\main\assets\sms_rules_v1.json --all-outcomes --min-pipeline-success-rate 92 --limit 30
.\gradlew.bat :app:testDebugUnitTest --tests com.sanha.moneytalk.core.sms.SmsRegexRuleAssetTest --tests com.sanha.moneytalk.core.sms.SmsRegexRuleMatcherAssetIntegrationTest --tests com.sanha.moneytalk.core.sms.SmsNonTransactionNoticeFilterTest
.\gradlew.bat :app:assembleDebug
```

합성 표본은 원문을 그대로 복사하지 않고 구조만 재현한다. raw regex 테스트와 `SmsRegexRuleMatcher` runtime 통합 테스트를 모두 통과해야 한다.

## 다음 갱신 시 주의

1. failure sample의 `bodyRegex/ruleKey`는 후보일 뿐이며 자동 승격하지 않는다.
2. regex 일치율만 보지 말고 `SmsPreFilter -> SmsIncomeFilter -> SmsRegexRuleMatcher` 도달 순서를 확인한다.
3. `outcome=success`라도 원본이 청구 예정/합산/매출집계 알림이면 거래로 보지 않는다.
4. 카드대금은 완료 출금과 예정 안내를 구분하고, 완료 출금은 통계 제외까지 검증한다.
5. 취소/환불 규칙은 `/sms_rules`에 추가하지 않고 수입 파서를 검증한다.
6. RTDB Step 1.5 overlay `/sms_rules`와 벡터 보조 원격 룰 `/sms_regex_rules/v1`를 구분한다.
7. live RTDB 검증이 필요하면 인증된 Firebase CLI 또는 읽기 권한을 먼저 확보한다.
8. 외화 Fast Path를 추가하려면 통화 코드와 환산 기준을 데이터 모델에 먼저 도입한다. 현재 `Int amount`에 외화 숫자를 넣지 않는다.
