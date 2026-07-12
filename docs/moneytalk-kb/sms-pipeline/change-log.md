---
type: log
title: SMS Pipeline KB Change Log
description: SMS Pipeline KB 변경 상세 이력을 기록한다.
tags: [moneytalk, sms, changelog]
resource: docs/moneytalk-kb/sms-pipeline/
timestamp: 2026-07-11T00:00:00+09:00
status: draft
---

# SMS Pipeline Change Log

## 2026-07-11 - release SMS 개인정보 경로 보강

- release 표본 수집은 유지하고, 원문 포함은 RTDB `send_origin_message`로 제어한다.
- 외부 Gemini 처리와 마스킹 표본 저장 전에 사용자명, 계좌/카드 식별정보를 최소화한다.
- 같은 줄 이름과 공백 구분 전화/계좌 번호를 추가 마스킹하고, regex 생성 프롬프트가 `{USER_NAME}`, `{ACCOUNT_OR_CARD}`를 literal이 아닌 일반화 대상 토큰으로 처리하도록 명시했다.
- `이용금액`은 전역 비결제 키워드에서 제외했다. 출금예정 안내는 notice filter가 차단하고, `이용금액 ... 승인` 형태의 실제 카드 사용은 파싱 경로로 유지한다.
- 성공 표본은 sender/type별 세션 최대 3개로 제한하고, 실패 표본 정책 문구를 실제 fingerprint upsert 구현과 맞췄다.
- release warning/error 로그에서 SMS 원문과 생성 regex 본문을 제거했다.
- `send_origin_message=false`만으로 과거 row가 일괄 삭제되지는 않으므로 수집 종료 후 RTDB 원문 정리가 필요함을 명시했다.

## 2026-07-11

- 기준: 동일 RTDB export 192개 row/676건을 asset runtime 계약으로 전체 재파싱
- 룰 보정: 롯데법인 카드 표기/이용금액 출금/KRW 해외승인, 농협 음수 잔액 자동출금, 삼성 할부, 카카오뱅크 카드결제
- 감사 보정: sender 전체 허용 룰, `amount > 0`, 유효 store, sender/type별 ACTIVE 5개 상한을 앱과 동일하게 적용
- 1차 결과: 유효 후보 165개 row/597건 전부 파싱, `actionable_unmatched=0`, `policy_false_positive=0`
- 1차 잔여 분류: 비거래 7/38, 취소·수입 11/25, 외화 6/9, 완료 근거 부족 1/5, 0원 2/2
- 결정: 외화 금액은 통화 모델 도입 전 원화 `Int` Fast Path로 승격하지 않고, 완료 근거 없는 입금계좌 안내와 0원 승인도 거래로 만들지 않는다.
- 반복 개선: 0원 승인 2개와 미완료 입금 안내 1개를 고신뢰 SKIP으로 전환하고, 취소·입금 11개를 수입 경로 성공으로 검증했다.
- 최종 처리율: 지출 165 + 수입 11 + SKIP 10 = `186/192`(96.9%), 관측치 `667/676`(98.7%). 미처리는 외화 6/9뿐이다.
- 프롬프트 계약: KRW 해외승인만 결제로 허용하고 외화 단독 금액은 `isPayment=false`로 통일했다.

## 2026-07-10

- 기준: RTDB `sms_origin` local export, `scripts/sms_origin_rule_audit.py`, `sms_rules_v1.json`, `SmsOriginSampleCollector`, `GeminiSmsExtractor` regex prompt 확인
- 변경 근거: RTDB export 삭제 전 운영 표본의 유효 룰과 보류 판단을 KB/asset/test에 흡수해야 한다는 요구 반영
- 갱신한 KB:
  - `README.md`
  - `06-rule-json-guide.md`
  - `07-rtdb-sms-origin-import-log.md`
- 결정:
  - `sms_origin` failure sample의 `bodyRegex/ruleKey`는 후보 shape로만 보고 자동 승격하지 않는다.
  - 완료된 카드대금 출금은 실제 출금액을 저장하되 통계에서 제외한다. 예정/명세서/청구서는 저장하지 않는다.
  - 교통카드/하이패스 N건, KSNET 마이장부 N건, 매출접수 N건 집계는 입력과 LLM 이전 방어 단계에서 SKIP한다.
  - 취소/환불은 INCOME 경로로 처리하며 Fast Path 허용 타입에서 `cancel`을 제거한다.
  - 미지원/비거래 후보에는 collector가 `bodyRegex/ruleKey`를 남기지 않으며 기존 값도 삭제한다.
  - 당시 중간 audit은 `109 matched / 4 non-transaction / 9 unsupported / 28 actionable` row였으며, 2026-07-11의 금액/store 검증 포함 전체 재감사 결과로 대체됐다.
  - Step 1.5 overlay `/sms_rules`와 벡터 보조 원격 룰 `/sms_regex_rules/v1`를 구분해서 문서화한다.
  - RTDB export raw `originBody`는 KB와 git diff에 남기지 않는다.

## 2026-07-09

- 기준: `docs/SMS_RULE_JSON_UPDATE_GUIDE.md`, `core/sms/**`, `app/src/main/assets/sms_rules_v1.json` 운영 기준 확인
- 변경 근거: 문자 파싱 Fast Path 룰, RTDB 표본, embedding/LLM 폴백 관계를 KB에서 바로 찾을 수 있어야 한다는 요구 반영
- 갱신한 KB:
  - `README.md`
  - `06-rule-json-guide.md`
- 결정:
  - sender regex 룰 운영 문서는 pipeline KB로 흡수
  - 수입 SMS는 asset regex 룰이 아니라 `SmsIncomeFilter -> SmsIncomeParser` 경로로 본다.

## 2026-07-03

- 기준: `core/sms`, `core/sync`, `receiver` 파일 목록과 `docs/moneytalk-kb/sms-parsing/06-ingestion-contract.md` 확인
- 변경 근거: MoneyTalk KB 초기 생성
- 갱신한 KB:
  - `README.md`
  - `00-structure-map.md`
  - `01-purpose-architecture.md`
  - `02-how-to-use.md`
  - `03-extension-points.md`
  - `04-files-checklist.md`
  - `05-file-inventory.md`
- 다음 검증:
  - 신규 sender regex 룰 변경 작업에서 Fast Path 관련 파일을 정확히 찾는지 확인
