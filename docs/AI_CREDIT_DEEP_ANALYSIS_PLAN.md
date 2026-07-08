# AI Credit Deep Analysis Plan

> JSON 기반 심층 재무 상담과 AI 크레딧 수익화 후속 작업 계획서
> 작성일: 2026-06-02

---

## 1. 배경

현재 AI 채팅은 `쿼리 분석 -> DB 조회/액션/분석 -> 최종 답변` 3단계 구조로 동작한다.
쿼리 분석 단계는 이미 JSON을 사용하지만, 최종 답변 단계에 전달되는 데이터는 텍스트 중심이다.

이 구조에서는 다음 요구를 안정적으로 처리하기 어렵다.

- 4인 가족, 자녀 나이, 대출이자, 생활비 이체 같은 사용자 맥락을 반영한 재무 상담
- 특정 거래처/날짜/금액 조건을 분석 규칙으로 적용
- 스마일페이 등 분석 제외 조건을 반영한 월별 현금흐름 비교
- Gemini API 토큰 비용과 광고/결제 수익의 정렬

따라서 후속 작업에서는 자유 채팅을 단순 확장하지 않고, 앱 내부에서 계산한 구조화 JSON을
Gemini에 전달하는 심층 분석 모드와 AI 크레딧 모델을 도입한다.

---

## 2. 결정 사항

### 2.1 JSON 컨텍스트 방향

- 원본 거래 전체를 Gemini에 보내지 않는다.
- 앱에서 월별/카테고리별/거래처별 집계와 증감 원인을 먼저 계산한다.
- Gemini는 계산된 JSON을 해석해 상담 문장으로 변환한다.
- 금액, 비율, 평균, 증감값은 앱 계산 결과만 사용하도록 유지한다.

### 2.2 광고 방향

- 토큰 사용량에 따라 일반 전면 광고를 자동 노출하지 않는다.
- 채팅 중간 또는 답변 생성 중 광고를 띄우지 않는다.
- 심층 분석 실행 전, 사용자가 명확히 선택한 보상형 광고로 크레딧을 충전한다.
- RTDB `/config/credit_ad_enable`이 `true`일 때만 크레딧 UI와 차감/충전 로직을 활성화한다.
- `release`가 아닌 빌드에서는 광고 로드/표시와 크레딧 차감·충전·레거시 마이그레이션 쓰기를 모두 비활성화한다.

### 2.3 결제 방향

- 앱 안에서 쓰는 AI 크레딧은 디지털 재화이므로 Google Play Billing을 사용한다.
- 초기에는 소비형 일회성 상품(`INAPP`)으로 크레딧 팩을 제공한다.
- 구독은 크레딧 모델이 안정화된 뒤 별도 단계에서 검토한다.

---

## 3. 목표

1. 채팅 상담의 근거 데이터를 구조화 JSON으로 전달한다.
2. 심층 분석 요청은 앱 내부 집계 결과를 기준으로 답변한다.
3. AI 사용 비용을 크레딧 단위로 사용자에게 설명 가능하게 만든다.
4. 보상형 광고와 앱결제를 크레딧 충전 수단으로 통합한다.
5. 사용자가 크레딧 잔액과 사용 내역을 확인할 수 있는 페이지를 제공한다.

---

## 4. 비목표

- LLM이 거래 리스트를 직접 합산/평균/비율 계산하도록 만들지 않는다.
- 토큰 단가를 사용자에게 직접 노출하지 않는다.
- 일반 전면 광고를 채팅 응답 흐름에 끼워 넣지 않는다.
- Play Billing 서버 검증 없이 대규모 유료 판매를 먼저 확대하지 않는다.
- 기존 리워드 광고 구조를 한 번에 폐기하지 않는다.

---

## 5. 제안 아키텍처

```text
사용자 질문
  -> 쿼리 분석
  -> 분석 유형/필요 크레딧 산정
  -> 크레딧 충분 여부 확인
      -> 부족: 크레딧 충전 화면 표시
      -> 충분: 크레딧 예약 차감
  -> DB 조회 및 앱 내부 집계
  -> FinancialAnalysisContext JSON 생성
  -> Gemini 최종 답변 생성
  -> 성공: 크레딧 사용 확정
  -> 실패: 예약 차감 환불
```

---

## 6. JSON 컨텍스트 예시

```json
{
  "analysisType": "household_financial_review",
  "period": {
    "start": "2026-01-01",
    "end": "2026-05-31",
    "partialMonthExcluded": true
  },
  "householdProfile": {
    "members": 4,
    "childrenAges": [4, 7]
  },
  "analysisRules": [
    {
      "name": "loan_interest_transfer",
      "match": {
        "storeName": "하상현",
        "dayRange": [20, 23]
      },
      "treatAs": "대출이자"
    },
    {
      "name": "living_expense_transfer",
      "match": {
        "storeName": "민지혜"
      },
      "treatAs": "생활비"
    },
    {
      "name": "kakaopay_lunch",
      "match": {
        "storeName": "카카오페이",
        "amountMax": 30000
      },
      "treatAs": "점심값"
    },
    {
      "name": "exclude_smile_pay",
      "match": {
        "storeNameContains": "스마일"
      },
      "excludeFromAnalysis": true
    }
  ],
  "summary": {
    "averageIncome": 7577421,
    "averageExpense": 6550106,
    "averageNet": 1027315,
    "expenseRatio": 86.5
  },
  "monthlyCashflow": [],
  "topCategories": [],
  "topStores": [],
  "increaseDrivers": [],
  "warnings": []
}
```

개인 이름 또는 민감 거래처는 가능하면 최종 LLM 전송 전에 별칭으로 치환한다.

---

## 7. 크레딧 정책 초안

| 분석 유형 | 예시 | 크레딧 |
|----------|------|-------:|
| 단순 조회 | "이번 달 식비 얼마야?" | 0 |
| 가벼운 조언 | "카페 지출 줄일 방법 추천해줘" | 1 |
| 기본 기간 분석 | "이번 달 지출 요약해줘" | 3 |
| 월별 추세 분석 | "올해 어디서 지출이 늘었어?" | 3 |
| 장기 심층 리포트 | "6개월 흐름과 절약 계획 세워줘" | 10 |

실제 Gemini 토큰 사용량은 내부 로깅/비용 추적용으로만 사용하고, 사용자에게는 분석 유형별 크레딧으로 안내한다.
`LocalChatQueryRouter`가 처리하는 단순 조회는 크레딧 0일 뿐 아니라 Gemini query analyzer/final answer/Rolling Summary 호출도 생략한다.

---

## 8. 데이터 모델 초안

스키마 변경이 필요한 경우 Room migration을 추가한다.

### CreditBalance

- `id`
- `balance`
- `updatedAt`

### CreditLedger

- `id`
- `type`: `AD_REWARD`, `PURCHASE`, `SPEND`, `REFUND`, `ADMIN`
- `amount`
- `reason`
- `relatedSessionId`
- `relatedMessageId`
- `purchaseToken`
- `createdAt`

### AnalysisRule

- `id`
- `name`
- `storeName`
- `storeNameContains`
- `amountMin`
- `amountMax`
- `dayStart`
- `dayEnd`
- `treatAs`
- `excludeFromAnalysis`
- `isEnabled`
- `createdAt`
- `updatedAt`

---

## 9. UI 계획

### 9.1 채팅 화면

- 상단에 현재 크레딧 표시
- 심층 분석 질문 전송 시 필요 크레딧 안내
- 부족하면 크레딧 충전 BottomSheet 또는 페이지로 이동
- 답변 생성 실패 시 크레딧 환불 안내

### 9.2 크레딧 확인 페이지

- 현재 크레딧
- 광고 보고 충전
- 크레딧 구매
- 최근 사용/충전 내역
- 분석 유형별 크레딧 기준
- 프리미엄/구독 안내 영역

### 9.3 분석 규칙 설정

초기 버전에서는 채팅에서 얻은 규칙을 문맥에만 반영한다.
이후 별도 설정 화면 또는 거래처 규칙 확장으로 저장형 분석 규칙을 제공한다.

---

## 10. Play Billing 계획

초기 상품은 소비형 일회성 상품으로 시작한다.

| 상품 | 예시 크레딧 |
|------|------------:|
| `ai_credit_small` | 30 |
| `ai_credit_medium` | 100 |
| `ai_credit_large` | 300 |

구매 처리 원칙:

- `BillingClient`는 앱 시작/재개 시 단일 연결을 유지한다.
- 구매 완료 후 purchase token을 검증한다.
- 소비형 상품은 크레딧 지급 후 consume 처리해 재구매 가능하게 한다.
- 서버가 준비되면 Firebase/Cloud Functions 기반 검증으로 이전한다.
- 환불/취소/중복 지급을 방지하기 위해 ledger에 purchase token을 저장한다.

---

## 11. 단계별 작업 계획

### Phase 1. JSON 기반 심층 분석

- `FinancialAnalysisContext` 모델 추가
- `household_financial_review` 성격의 쿼리 타입 또는 분석 분기 추가
- 월별 수입/지출/잔여금/소비성향 앱 내부 계산
- 카테고리/거래처 TOP 및 증가 원인 계산
- 최종 답변 프롬프트에 JSON 컨텍스트 섹션 추가
- 단위 테스트: 집계 정확성, 제외 카드, 통계 제외, 분석 제외 조건

### Phase 2. AI 크레딧 기반 제한

- 기존 리워드 채팅 횟수 모델을 크레딧 모델로 확장 (1차 완료: 질문 유형별 0/1/3/10크레딧, 광고 충전 원장화)
- 분석 유형별 필요 크레딧 산정 (1차 완료: ChatCreditPolicy)
- 크레딧 예약 차감/성공 확정/실패 환불 흐름 추가 (1차 완료: 답변 실패/clarification 환불)
- 단순 조회 Gemini 비용 제거 (1차 완료: LocalChatQueryRouter로 총 지출/카테고리 지출/최근 지출/예산 현황 등 로컬 처리)
- 리워드 광고 보상을 크레딧 충전으로 전환 (1차 완료)
- UI 문구를 "무료 상담 횟수"에서 "AI 크레딧"으로 변경 (1차 완료)

### Phase 3. 크레딧 확인 페이지

- 설정 또는 채팅 상단에서 진입 (1차 완료: 설정 진입)
- 잔액, 충전, 사용 내역, 크레딧 기준 표시 (1차 완료: 광고 충전/원장/이용 가이드)
- Composable map 및 화면 요구사항 문서 갱신 (1차 완료)

### Phase 4. Play Billing 소비형 크레딧

- Billing Library 의존성 추가 검토
- Play Console one-time product 생성
- 구매 플로우 구현
- purchase token 중복 지급 방지
- consume 처리
- 테스트 계정/라이선스 테스트

### Phase 5. 서버 검증 및 구독 검토

- Firebase Auth 또는 익명 사용자 기준 계정 매핑 검토
- Cloud Functions에서 Play Developer API 검증
- 구독 상품 도입 여부 검토
- 프리미엄 혜택: 광고 제거, 월 크레딧 지급, 심층 분석 할인/무제한

---

## 12. 검증 항목

- 크레딧 부족 시 Gemini API가 호출되지 않는가
- `LocalChatQueryRouter` 매칭 성공 시 `analyzeQueryNeeds()`, `generateFinalAnswerWithContext()`, Rolling Summary 요약 모델이 호출되지 않는가
- Gemini 실패/네트워크 실패 시 크레딧이 환불되는가
- 보상형 광고 실패 시 크레딧이 지급되지 않고 대기 요청이 정리되는가
- 구매 토큰이 중복 지급되지 않는가
- 소비형 상품 consume 후 재구매가 가능한가
- 제외 카드/통계 제외/분석 제외 조건이 모든 집계에 동일하게 적용되는가
- LLM에 전달되는 JSON에 불필요한 개인정보가 포함되지 않는가

---

## 13. 오픈 질문

- 무료 사용자 기본 크레딧을 일 단위로 줄지, 설치 보너스로 줄지 결정 필요
- 광고 1회당 크레딧 수량은 eCPM/API 비용 실측 후 조정 필요
- 심층 분석 규칙을 거래처 규칙에 통합할지, 별도 AnalysisRule로 분리할지 결정 필요
- Play Billing 서버 검증을 첫 버전에 포함할지, 클라이언트 검증 후 단계적으로 이전할지 결정 필요
- 구독을 크레딧 월 지급형으로 할지, 광고 제거 + 심층 분석 무제한형으로 할지 결정 필요
