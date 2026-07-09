---
type: reference
title: AI Credit And Monetization Policy
description: AI 크레딧 차감/충전, 광고 gate, 유료화 후속 계획을 현재 구현과 계획으로 나눠 정리한다.
tags: [moneytalk, ai-credit, monetization, reward-ad, billing]
resource: app/src/main/java/com/sanha/moneytalk/core/ad/
timestamp: 2026-07-09T05:20:00+09:00
status: draft
---

# AI Credit And Monetization Policy

> 기준: 흡수된 AI 크레딧/심층 분석/수익화 원문 내용을 KB용으로 재작성

## 현재 구현 정책

| 기능 | 비용/보상 | 구현 기준 |
|---|---:|---|
| AI 채팅 1회 전송 | 1크레딧 차감 | 질문 유형별 차등 과금 없이 사용자 이해가 쉬운 단일 단가 |
| 이전 문자 기록 월 데이터 가져오기 | 월 1개당 1크레딧 차감 | 월별 coverage가 부족한 과거 월을 사용자가 요청할 때 |
| 보상형 광고 1회 시청 | 2크레딧 고정 지급 | RTDB `reward_ad_chat_count` 값과 무관하게 현재 앱 정책은 2크레딧으로 고정 |
| 앱 첫 실행 보상 | 5크레딧 1회 지급 | `initial_ai_credit_granted` flag 기준으로 1회만 지급 |
| 유료 플랜 사용자 | 크레딧 UI/차감 미노출 | `ServiceTier.PREMIUM` 기반으로 비활성 취급 |

## Gate 조건

| Gate | 의미 |
|---|---|
| release 빌드 | 비릴리즈 빌드에서는 광고 로드/표시, 크레딧 차감/충전, 레거시 마이그레이션 쓰기를 수행하지 않는다. |
| test override 빌드 | `-Pmoneytalk.monetizationTestOverride=true`로 빌드한 실기기 검증 APK에서만 debug/develop release gate를 우회한다. |
| RTDB `/config/credit_ad_enable=true` | AI 크레딧 UI와 차감/충전 기능 활성화 조건이다. 값이 없거나 false면 숨긴다. |
| RTDB `/config/reward_ad_enabled=true` | 보상형 광고와 광고 기반 충전 흐름 활성 조건이다. |
| FREE tier | FREE 사용자만 크레딧 차감/광고 충전 흐름을 탄다. PREMIUM은 차감하지 않는다. |

대표 RTDB 설정:

```json
{
  "credit_ad_enable": true,
  "reward_ad_enabled": true,
  "reward_ad_chat_count": 2
}
```

> `reward_ad_chat_count`는 기존 RTDB 필드로 남아 있을 수 있으나, 현재 앱의 AI 크레딧 보상 금액은 2크레딧 고정이다.

## 월별 SMS 동기화 CTA

초기/증분 동기화는 무료 기본 범위로 수행한다. 사용자가 과거 월로 이동했을 때 `SyncCoverageRepository` 기준 coverage가 부족하면 월별 CTA를 보여주고, 해당 월 custom range 동기화에 1크레딧을 차감한다.

크레딧이 부족하면 보상형 광고 1회 시청으로 기본 2크레딧을 충전한 뒤 다시 차감한다. 광고 로드/표시/보상 실패 시 과거 월 데이터 가져오기를 우회 실행하지 않는다.

## API 비용 방어 장치

| 장치 | 비용 영향 |
|---|---|
| SMS Regex Fast Path | 룰 커버 발신번호는 임베딩/LLM 없이 파싱 |
| SMS Vector/패턴 캐시 | 유사 패턴은 재사용 |
| `LocalChatQueryRouter` | 안전한 단순 조회는 Gemini analyzer/final/summary 호출 없음 |
| Gemini Flash Lite 기본값 | 운영 기본 모델 비용 억제 |
| 크레딧 단가 고정 | FREE 사용자 사용량을 체감 가능한 단위로 제한 |

## 후속 계획

| 계획 | 현재 상태 | 주의점 |
|---|---|---|
| Play Billing `premium_monthly_7900` | 후속 | 결제 토큰 서버 검증, 환불/중복 지급 방지 필요 |
| 프리미엄 구독 UI | 후속 | FREE/PREMIUM gate와 크레딧 미노출 정책 동기화 필요 |
| 결제 기반 크레딧 패키지 | 후속 | 서버 검증과 ledger idempotency가 먼저 필요 |
| JSON 기반 심층 재무 상담 | 후속 | 앱 계산 결과 JSON을 만들고 Gemini는 해석/문장화만 담당해야 함 |
| 유료/고크레딧 AI 기능 | 후속 | Pro/preview 모델은 내부 테스트 또는 고비용 기능에 제한 |

## 심층 분석 설계 원칙

- 원본 거래 전체를 모델에 던지고 계산을 맡기지 않는다.
- 앱이 기간/카테고리/카드/가게별 집계 JSON을 만든다.
- Gemini는 앱이 계산한 값만 인용하고, 비율/평균/증감률이 JSON에 없으면 새로 계산하지 않는다.
- 데이터가 부족한 경우 추정하지 않고 추가 데이터 필요 또는 판단 불가를 안내한다.

## 변경 시 검증 질문

1. debug/release gate가 뒤섞이지 않았는가?
2. RTDB 값이 없거나 false일 때 크레딧 UI와 차감 로직이 완전히 숨겨지는가?
3. 광고 실패 시 유료성 기능이 우회 실행되지 않는가?
4. PREMIUM 사용자에게 크레딧 부족/광고 충전 UI가 노출되지 않는가?
5. 채팅 로컬 조회가 Gemini 비용을 쓰지 않아도 사용자 과금 정책과 모순되지 않는가?
