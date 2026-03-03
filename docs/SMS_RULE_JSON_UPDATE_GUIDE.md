# SMS Rule JSON 업데이트 가이드

`app/src/main/assets/sms_rules_v1.json`를 원본 CSV 표본으로 갱신하는 운영 가이드입니다.
룰 생성, 반영, 검증, 성능 해석까지 포함합니다.

## 1. 핵심 요약

- 파싱 1차 경로는 `sender + type + priority` 기반 regex 매칭(Fast Path)이다.
- JSON 룰이 충분하면 Step1.5에서 대부분 처리되어 동기화가 빨라진다.
- JSON/RTDB 룰이 없으면 기존 임베딩+LLM 파이프라인으로 대부분 폴백되어 느려진다.
- 룰 키는 반드시 결정적 키(`ruleKey`)를 사용해 중복 누적을 방지한다.

## 2. 런타임 동작 방식

실행 순서:
1. Asset 룰 로드 (`sms_rules_v1.json`)
2. RTDB 룰 overlay (동일 키면 RTDB가 덮어씀)
3. 결제 후보 SMS에 Step1.5 Fast Path 적용
4. Fast Path miss만 기존 임베딩/벡터/LLM 파이프라인 진입

중요:
- 임베딩 파이프라인은 제거되지 않았고, Fast Path miss에 대해 그대로 동작한다.
- 따라서 JSON 룰을 비우면 파싱이 깨지지는 않지만 속도는 크게 느려진다.

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

주의:
- 결제 후보가 전부 Fast Path miss이면 실패 표본이 많이 올라간다.
- 이는 "전건 업로드"가 아니라 "미매칭 실패의 누적 수집"이다.

## 5. 입력 데이터

- 필수 파일: 백업 CSV (`moneytalk_backup_*.csv`)
- 필수 컬럼:
  - `유형` (지출/수입)
  - `전화번호`
  - `문자원본`
  - `카드/출처` (선택)

예시 헤더:

```text
유형,날짜,이름,카테고리,카드/출처,전화번호,메모,금액,문자원본
```

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
- `income`: 입금/이체입금
- `cancel`: 승인취소/취소완료
- `overseas`: 해외승인/해외결제

실행 엔진은 sender 내에서 `priority DESC`로 룰을 순차 시도하며, 첫 성공 룰의 type이 최종 타입이다.

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

1. CSV에서 대상 추출
- 우선 `유형=지출`부터 시작
- 전화번호별로 묶고 본문 구조별로 케이스 분리

2. 케이스별 대표 샘플 3~5건 선정
- 금액/가맹점/날짜값은 달라도 구조가 같으면 같은 케이스

3. regex 작성
- `amountGroup`, `storeGroup`은 필수
- 가능하면 named group 사용
- `store`가 숫자만 캡처되지 않도록 검증

4. `ruleKey`/`priority` 결정
- 결정적 키 생성
- 대표 형식 우선순위 상향

5. `sms_rules_v1.json` 반영
- 기존 동일 key가 있으면 update
- 신규면 add

6. 검증
- JSON 문법: `jq empty app/src/main/assets/sms_rules_v1.json`
- 빌드: `./gradlew :app:assembleDebug`
- 로그:
  - `Asset 룰 로드 완료: N건`
  - `Step1.5 SenderRegex: 매칭 X건, 폴백 Y건`
  - `Y` 감소 확인

## 11. 품질 체크리스트

- sender는 숫자 normalize 기준으로 키를 넣었는가
- `amountGroup`, `storeGroup`이 실제 캡처되는가
- 오탐 가능성이 큰 과도한 `.*`를 피했는가
- 동일 sender/type에서 중복 룰이 늘어나지 않는가
- 신규 룰 후 기존 주요 sender 매칭률이 유지/개선되는가

## 12. LLM 작업 요청 템플릿

아래 형식으로 전달하면 원본 데이터 + 문서만으로 룰 갱신 작업 재현이 가능하다.

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
