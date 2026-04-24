# SMS Rule JSON 업데이트 가이드

`app/src/main/assets/sms_rules_v1.json`를 원본 CSV 표본으로 갱신하는 운영 가이드입니다.
룰 생성, 반영, 검증, 성능 해석까지 포함합니다.

## 1. 핵심 요약

- 파싱 1차 경로는 `sender + type + priority` 기반 regex 매칭(Fast Path)이다.
- JSON 룰이 충분하면 Step1.5에서 대부분 처리되어 동기화가 빨라진다.
- JSON/RTDB 룰이 없으면 기존 임베딩+LLM 파이프라인으로 대부분 폴백되어 느려진다.
- 룰 키는 반드시 결정적 키(`ruleKey`)를 사용해 중복 누적을 방지한다.
- Fast Path 룰은 결제 계열만 다룬다. 수입 SMS는 `SmsIncomeFilter -> SmsIncomeParser` 경로로 파싱한다.

## 2. 런타임 동작 방식

실행 순서:
1. Asset 룰 로드 (`sms_rules_v1.json`)
2. RTDB 룰 overlay (동일 키면 RTDB가 덮어씀)
3. 결제 후보 SMS에 Step1.5 Fast Path 적용
   - 1차: `sms_regex_rules` (asset/rtdb/local_learned)
   - 2차: `sms_patterns`의 sender별 검증 regex (`amountRegex/storeRegex`) 로컬 보조 매칭
     - 단, 템플릿이 일치하는 패턴에만 적용(오파싱 방지)
4. Fast Path miss만 기존 임베딩/벡터/LLM 파이프라인 진입

중요:
- 임베딩 파이프라인은 제거되지 않았고, Fast Path miss에 대해 그대로 동작한다.
- 따라서 JSON 룰을 비우면 파싱이 깨지지는 않지만 속도는 크게 느려진다.
- `income` 타입 룰은 asset에 넣지 않는다. 기존 DB에 남아 있어도 `SmsRegexRuleMatcher`의 허용 타입 필터와 `paymentCandidates` 입력 때문에 실행되지 않는다.

## 3. 속도 해석 기준

로그에서 아래 3줄이 핵심이다.

```text
Asset 룰 로드 완료: N건
RTDB 룰 로드 완료: M건
Step1.5 SenderRegex: 매칭 X건, 폴백 Y건
```

판단:
- 빠른 상태: `X`가 크고 `Y`가 작음
- 느린 상태: `X=0`에 가까우며 `Y`가 결제 후보와 유사함

예:
- 룰 있음: `매칭 218, 폴백 1` -> 대부분 Fast Path 처리
- 룰 없음: `매칭 0, 폴백 219` -> 219건이 임베딩+LLM으로 이동

## 4. RTDB 표본 업로드 정책

`sms_origin`으로 업로드되며 "모든 SMS 전건" 전송은 아니다.

현재 정책:
1. 성공 표본(`outcome=success`)
- regex가 검증된 경우에만 전송
- 경로: `/sms_origin/{sender}/{type}/{sampleKey}`
- sender+type 버킷당 세션 기준 고유 fingerprint 최대 3개 유지

2. 실패 표본(`outcome=fail`)
- Fast Path 실패건은 실패 사유와 함께 전송
- fingerprint 기준 upsert(`count`, `lastSeenAt` 누적)
- 경로: `/sms_origin/{sender}/{type}/{sampleKey}`
- 업로드 제한:
  - 동일 fingerprint 재전송 쿨다운: 24시간
  - sender/type/failStage/failReason 버킷당 일 최대 5건

주의:
- 결제 후보가 전부 Fast Path miss이면 실패 표본이 많이 올라간다.
- 이는 "전건 업로드"가 아니라 "미매칭 실패의 누적 수집"이다.

## 5. 입력 데이터

두 가지 소스를 사용한다.

### 5-1. 백업 CSV (초기 룰 작성용)

- 파일: `moneytalk_backup_*.csv`
- 필수 컬럼: `유형`, `전화번호`, `문자원본`
- 선택 컬럼: `카드/출처`

```text
유형,날짜,이름,카테고리,카드/출처,전화번호,메모,금액,문자원본
```

### 5-2. RTDB sms_origin export (운영 중 갱신용)

- Firebase Console → RTDB → `sms_origin` 노드 → JSON 내보내기
- 구조: `{sender}/{type}/{fingerprint_hash}`
- 핵심 필드:
  - `outcome`: `fail` (미매칭) / `success` (매칭 성공)
  - `failureTemplate`: SMS 구조 템플릿 (플레이스홀더: `{AMOUNT}`, `{DATE}`, `{TIME}`, `{N}`, `{CARD_NO}`)
  - `failReason`: `no_active_rule` (룰 없음) / `no_regex_match` (룰은 있으나 미매칭)
  - `count`: 누적 발생 횟수

RTDB 기반 갱신 시 `outcome=fail` 표본만 대상으로, `count`가 높은 순서대로 우선 처리한다.

## 6. JSON 구조 (고정)

경로는 반드시 `sms_rules/{sender}/{type}/{ruleKey}`를 사용한다.

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

주의:
- JSON 주석 문법은 지원되지 않는다.
- 룰 비활성화가 필요하면 삭제 대신 `status: "INACTIVE"` 사용을 우선 고려한다.

## 7. 타입 분류 기준

- `expense`: 출금/결제/승인 (일시불/할부 포함)
- `cancel`: 승인취소/취소완료
- `overseas`: 해외승인/해외결제
- `payment`, `debit`: 운영/RTDB 호환용 결제 계열 타입

실행 엔진은 sender 내에서 `priority DESC`로 룰을 순차 시도하며, 첫 성공 룰의 type이 최종 타입이다.
Fast Path 허용 타입은 `expense`, `cancel`, `overseas`, `payment`, `debit`이다.
입금/급여/환급 등 수입 SMS는 regex asset 룰이 아니라 `SmsIncomeParser`에서 처리한다.

## 8. ruleKey 생성 규칙

같은 룰은 어떤 기기에서 생성해도 같은 key가 되어야 한다.

권장 생성식:
- `keyInput = sender|type|bodyRegex|amountGroup|storeGroup|cardGroup|dateGroup|version`
- `ruleKey = sha256(keyInput)` 앞 24자리 hex

예시:

```bash
KEY_INPUT='16449999|expense|(?s)...|amount|store|card|date|1'
echo -n "$KEY_INPUT" | shasum -a 256 | cut -c1-24
```

## 9. priority 부여 기준

같은 sender/type 내에서 값이 높을수록 먼저 매칭한다.

- 900~950: 빈도 높고 안정적인 대표 형식
- 750~890: 파생/예외 형식
- 600~740: 희귀 형식

운영 중 match/fail 통계에 따라 로컬에서 priority가 보정될 수 있으므로, 초기값은 위 기준으로 시작하면 된다.

## 10. 업데이트 절차

### A. CSV 기반 (초기 구축)

1. CSV에서 대상 추출
- 우선 `유형=지출`부터 시작
- 전화번호별로 묶고 본문 구조별로 케이스 분리

2. 케이스별 대표 샘플 3~5건 선정
- 금액/가맹점/날짜값은 달라도 구조가 같으면 같은 케이스

3. regex 작성 → ruleKey/priority 결정 → JSON 반영 → 검증 (아래 공통 절차)

### B. RTDB 기반 (운영 중 갱신)

1. Firebase Console에서 `sms_origin` 노드 JSON 내보내기
2. `outcome=fail` 표본을 sender/type별로 그룹화
3. `count` 높은 순으로 `failureTemplate`의 구조 분석
4. 같은 구조인데 가게명만 다른 표본은 하나의 룰로 통합
5. `failureTemplate`의 플레이스홀더를 generic regex로 변환:
   - `{AMOUNT}` → `(?<amount>[\\d,]+)`
   - `{DATE}` → `(?<date>\\d{2}/\\d{2})`
   - `{TIME}` → `\\d{2}:\\d{2}`
   - `{N}` → `\\d+`
   - `{CARD_NO}` → `\\d+\\*+\\d+`
   - 하드코딩된 가게명 → `(?<store>[^\\n]+)` 또는 `(?<store>.+?)`
   - 하드코딩된 이름 → `\\S+`
6. regex 작성 → ruleKey/priority 결정 → JSON 반영 → 검증 (아래 공통 절차)

### 공통: regex 작성 ~ 검증

1. regex 작성
- `amountGroup`, `storeGroup`은 필수
- 가능하면 named group 사용 (`(?<name>...)` — Java regex 문법)
- `store`가 숫자만 캡처되지 않도록 검증
- `(?s)` prefix 필수 (DOTALL mode)

2. `ruleKey`/`priority` 결정
- 결정적 키 생성 (섹션 8 참조)
- 대표 형식 우선순위 상향

3. `sms_rules_v1.json` 반영
- 기존 동일 key가 있으면 update
- 신규면 add

4. 검증
- JSON 문법: `jq empty app/src/main/assets/sms_rules_v1.json`
- 룰 테스트: `./gradlew :app:testDebugUnitTest --tests com.sanha.moneytalk.core.sms.SmsRegexRuleAssetTest`
- 빌드: `./gradlew :app:assembleDebug`
- 로그:
  - `Asset 룰 로드 완료: N건`
  - `Step1.5 SenderRegex: 매칭 X건, 폴백 Y건`
  - `Y` 감소 확인

## 11. 품질 체크리스트

- sender는 숫자 normalize 기준으로 키를 넣었는가
- `amountGroup`, `storeGroup`이 실제 캡처되는가
- `type`이 Fast Path 허용 타입(`expense/cancel/overseas/payment/debit`)인가
- `ruleKey`가 `sender|type|bodyRegex|amountGroup|storeGroup|cardGroup|dateGroup|version`의 SHA-256 앞 24자리와 일치하는가
- 오탐 가능성이 큰 과도한 `.*`를 피했는가
- 동일 sender/type에서 중복 룰이 늘어나지 않는가
- 신규 룰 후 기존 주요 sender 매칭률이 유지/개선되는가
- `dateGroup`이 비어있는 룰은 날짜 없는 SMS 구조에만 사용했는가
- 같은 sender의 다른 type 룰과 오분류(expense↔cancel 등) 가능성 없는가

## 12. LLM 작업 요청 템플릿

### A. CSV 기반 룰 생성

```text
목표:
- moneytalk_backup_YYYYMMDD_HHMMSS.csv 기반으로 sms_rules_v1.json 업데이트

조건:
- 키 구조: sms_rules/{sender}/{type}/{ruleKey}
- amount/store 필수 캡처
- ruleKey: sha256(keyInput) 앞 24자리 hex
- 기존과 동일 ruleKey면 update
- priority는 대표 형식 우선

검증:
- jq 문법 검증 통과
- assembleDebug 성공
- Step1.5 매칭률 개선 로그 확인
```

### B. RTDB 기반 룰 갱신

```text
목표:
- RTDB sms_origin export 기반으로 sms_rules_v1.json 갱신

입력:
- RTDB JSON: ~/Downloads/moneytalk-...-sms_origin-export.json
- 기존 룰: app/src/main/assets/sms_rules_v1.json

절차:
1. outcome=fail 표본을 sender/type별 그룹화, count DESC 정렬
2. failureTemplate 구조 분석 → 같은 구조는 하나의 룰로 통합
3. 하드코딩된 값(가게명/이름)은 generic regex로 일반화
4. 기존 룰과 중복 여부 확인 (이미 커버되는 역사적 fail 제외)
5. 신규 룰 작성 → ruleKey/priority 결정 → JSON 반영

검증:
- jq 문법 검증 통과
- assembleDebug 성공
- 기존 룰과 중복/충돌 없음

주의:
- RTDB fail은 역사적 누적이므로, 현재 asset 룰로 이미 매칭되는 표본은 건너뛴다
- RTDB에서 type이 잘못 분류된 경우(예: 환불이 expense로 분류)도 있으므로 내용 기준으로 type 판단
- RTDB 작업 완료 후 sms_origin 노드 데이터 삭제하여 다음 수집 주기에 깨끗한 상태로 시작
```

## 13. 현재 룰 현황

마지막 업데이트: 2026-04-24 (v1, 40개 룰)

| sender | 카드사 | cancel | expense | overseas | 합계 |
|--------|--------|--------|---------|----------|------|
| 16449999 | KB국민은행 | 2 | 5 | 1 | 8 |
| 15220080 | 스마일카드(현대) | - | 1 | - | 1 |
| 15776000 | 현대/스마일 | 2 | - | - | 2 |
| 15776200 | 현대카드 | 3 | 2 | - | 5 |
| 15447200 | 신한카드(단문) | 1 | 1 | - | 2 |
| 15447000 | 신한카드(장문) | 1 | 1 | - | 2 |
| 15881688 | KB국민카드 | 1 | 1 | 1 | 3 |
| 15888100 | 롯데카드 | 2 | 3 | - | 5 |
| 15889955 | 우리카드 | - | 3 | - | 3 |
| 15888900 | 삼성카드 | 2 | 3 | - | 5 |
| 15881600 | NH농협카드 | 2 | 2 | - | 4 |
| **합계** | | **16** | **22** | **2** | **40** |
