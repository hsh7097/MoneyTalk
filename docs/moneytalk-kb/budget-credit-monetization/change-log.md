---
type: changelog
title: Budget Credit Monetization KB 변경 로그
description: 예산/크레딧/광고 KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, monetization, changelog]
resource: docs/moneytalk-kb/budget-credit-monetization/
timestamp: 2026-07-11T00:00:00+09:00
status: draft
---

# Budget Credit Monetization KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-07-11 | `BuildVariantPolicy.shouldUseProductionAdUnits`, release override/normal APK DEX, Codex_Fold_API_36와 SM-F966N 확인 | release 서명을 유지하는 테스트 override에서도 Google 테스트 광고 단위만 선택하도록 보강하고 AVD/실기기 보상 흐름 검증 | `RewardAdManager.kt`, `BannerAdCompose.kt`, `BuildVariantPolicyTest.kt` | override APK에는 Google rewarded/banner 테스트 ID만, 최종 normal release APK/AAB에는 운영 광고 ID만 남는 것을 확인했다. AVD는 잔액 `4 -> 6`, SM-F966N은 `5 -> 7`과 원장 `보상형 광고 +2`를 확인했다. 실기기 광고에는 `테스트 광고`, 완료 후 `리워드 지급됨`이 표시됐고 crash는 없었다. 최종 normal release에서 override는 `false`이며 RTDB `credit_ad_enable=false` 기준 AI Credit 진입점도 숨겨진다. |
| 2026-07-10 | `BuildVariantPolicy`, `RewardAdManager`, `AiCreditRepository`, `AiCreditScreen` 확인 | 초기 5크레딧 지급과 테스트 전용 monetization override 반영 | `README.md`, `02-policy-and-plans.md`, `../ai-credit-screen/README.md` | 앱 첫 실행 보상은 1회만 지급하고, 광고 보상은 RTDB 값과 무관하게 2크레딧 고정이다. 실기기 검증 APK에서만 debug/develop gate를 우회한다. |
| 2026-07-09 | `AI_CREDIT_USAGE_PLAN.md`, `AI_CREDIT_DEEP_ANALYSIS_PLAN.md`, `MONETIZATION.md` 내용 재검토 | 원문 링크 대신 구현 정책/후속 계획 KB화 | `README.md`, `02-policy-and-plans.md` | 채팅 1크레딧, 과거 월 동기화 1크레딧, 광고 2크레딧, release/RTDB/PREMIUM gate를 현재 정책으로 정리하고 Play Billing/심층 분석은 후속으로 분리. |
| 2026-07-08 | `RewardAdManager`, `AiCreditRepository`, `MainViewModel`, `ChatCreditPolicy`, `CreditFeaturePolicy` 확인 | 크레딧 사용 정책 전환 | `README.md`, `01-feature-flow.md`, `02-policy-and-plans.md` | 채팅 1회 1크레딧, 이전 월 문자 가져오기 1크레딧, 광고 1회 2크레딧, 프리미엄 크레딧 비노출 정책을 반영. |
| 2026-07-08 | `core/ad/**`, `AiCreditRepository`, `BudgetDao`, `ChatCreditPolicy`, `SettingsViewModel` 확인 | 예산/크레딧/광고 기능 KB 생성 | `README.md`, `01-feature-flow.md` | 예산, AI 크레딧, 광고 release gate, 채팅 비용 정책을 기능 KB로 분리. |
