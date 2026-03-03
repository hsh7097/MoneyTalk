# SMS Rule JSON 업데이트 가이드

`app/src/main/assets/sms_rules_v1.json`를 원본 CSV 표본으로 갱신하는 절차입니다.
이 문서만으로도 신규 룰 추가/수정이 가능하도록 작성했습니다.

## 1. 목적

- 앱 배포 시 포함되는 기본 룰(`sms_rules_v1.json`)을 정기적으로 갱신한다.
- 발신번호(sender) + 타입(type) 기반 Fast Path 매칭률을 높여 동기화 속도/정확도를 개선한다.

## 2. 입력 데이터

- 필수 파일: 백업 CSV (`moneytalk_backup_*.csv`)
- 필수 컬럼:
  - `유형` (지출/수입)
  - `전화번호`
  - `문자원본`
  - `카드/출처` (선택, 카드명 추론용)

예시 헤더:

```text
유형,날짜,이름,카테고리,카드/출처,전화번호,메모,금액,문자원본
```

## 3. JSON 구조 (고정)

경로: `sms_rules/{sender}/{type}/{ruleKey}`

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

## 4. 업데이트 순서

1. CSV에서 파싱 대상만 추출
- `유형=지출`을 우선 대상으로 사용
- 전화번호별로 묶고, 같은 전화번호 내에서 본문 형식이 다른 케이스를 분리

2. 타입 분류
- `expense`: 출금/결제/승인
- `income`: 입금
- `cancel`: 승인취소/취소완료
- `overseas`: 해외승인/해외결제

3. 케이스별 대표 샘플 선정
- 같은 형식당 3~5건 대표 샘플 확보
- 금액/가맹점/날짜가 달라도 구조가 같으면 한 케이스로 묶음

4. regex 작성
- `bodyRegex`는 본문 전체 구조를 안정적으로 매칭
- 그룹은 가급적 named group 사용: `(?<amount>...)`, `(?<store>...)`
- 필수 추출값:
  - `amountGroup` (필수)
  - `storeGroup` (필수)
- 권장 추출값:
  - `cardGroup`, `dateGroup`

5. `ruleKey` 생성 (결정적 키)
- 동일 룰이 중복 저장되지 않도록 `ruleKey`를 랜덤이 아닌 결정적 값으로 생성
- 권장 생성식:
  - `keyInput = sender|type|bodyRegex|amountGroup|storeGroup|cardGroup|dateGroup|version`
  - `ruleKey = sha256(keyInput)`의 앞 24자리 hex

예시:

```bash
KEY_INPUT='16449999|expense|(?s)...|amount|store|card|date|1'
echo -n "$KEY_INPUT" | shasum -a 256 | cut -c1-24
```

6. 우선순위(priority) 부여
- 같은 sender/type에서 숫자가 높을수록 먼저 매칭
- 권장 기준:
  - 900~950: 빈도 높고 가장 안정적인 대표 형식
  - 750~890: 파생/예외 형식
  - 600~740: 희귀 형식

7. JSON 반영
- `app/src/main/assets/sms_rules_v1.json`에 sender/type/ruleKey 경로로 추가
- 기존 ruleKey와 같으면 내용 업데이트(덮어쓰기)

8. 검증
- JSON 문법 검증:
  - `jq empty app/src/main/assets/sms_rules_v1.json`
- 빌드 검증:
  - `./gradlew :app:assembleDebug`
- 실행 로그 검증:
  - `Asset 룰 로드 완료: N건`
  - `Step1.5 SenderRegex: 매칭 X건, 폴백 Y건` (Y 감소 확인)

## 5. 품질 체크리스트

- 전화번호 normalize(숫자만) 기준으로 sender 키를 사용했는가
- `amountGroup`, `storeGroup`이 실제 캡처되는가
- `store`가 숫자만 반환되는 케이스를 피했는가
- 과도하게 넓은 regex(`.*` 남발)로 오탐이 늘지 않는가
- 신규 룰 추가 후 기존 주요 sender 매칭률이 떨어지지 않는가

## 6. 운영 원칙

- Asset 룰은 배포 기본값, RTDB 룰은 운영 overlay로 사용
- 동일 `(sender, type, ruleKey)`는 중복 생성하지 않고 업데이트
- 실패 표본은 `sms_origin`에 누적되므로, 주기적으로 실패 상위 케이스를 룰로 승격

## 7. LLM에 전달할 작업 템플릿

아래 형식으로 전달하면 룰 업데이트 작업을 재현하기 쉽습니다.

```text
목표:
- moneytalk_backup_YYYYMMDD_HHMMSS.csv 기반으로 sms_rules_v1.json 업데이트

조건:
- 키 구조는 sms_rules/{sender}/{type}/{ruleKey}
- amount/store는 필수 캡처
- ruleKey는 sha256(keyInput)의 앞 24자리 hex
- 기존 룰과 충돌 시 동일 key는 업데이트

검증:
- jq 문법 검증
- assembleDebug 성공
- 로그에서 Step1.5 매칭률 개선 확인
```
