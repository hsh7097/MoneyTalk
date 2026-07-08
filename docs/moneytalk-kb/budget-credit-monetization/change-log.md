---
type: changelog
title: Budget Credit Monetization KB 변경 로그
description: 예산/크레딧/광고 KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, monetization, changelog]
resource: docs/moneytalk-kb/budget-credit-monetization/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Budget Credit Monetization KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-07-08 | `RewardAdManager`, `AiCreditRepository`, `MainViewModel`, `ChatCreditPolicy`, `CreditFeaturePolicy` 확인 | 크레딧 사용 정책 전환 | `README.md`, `01-feature-flow.md`, `docs/AI_CREDIT_USAGE_PLAN.md` | 채팅 1회 1크레딧, 이전 월 문자 가져오기 1크레딧, 광고 1회 2크레딧, 프리미엄 크레딧 비노출 정책을 반영. |
| 2026-07-08 | `core/ad/**`, `AiCreditRepository`, `BudgetDao`, `ChatCreditPolicy`, `SettingsViewModel` 확인 | 예산/크레딧/광고 기능 KB 생성 | `README.md`, `01-feature-flow.md` | 예산, AI 크레딧, 광고 release gate, 채팅 비용 정책을 기능 KB로 분리. |
