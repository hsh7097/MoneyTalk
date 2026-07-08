---
type: feature-flow
title: Budget Credit Monetization 기능 흐름
description: 예산 저장, AI 크레딧 차감/충전, 보상형 광고, 배너 표시 정책 흐름을 설명한다.
tags: [moneytalk, budget, credit, ads, feature-flow]
resource: app/src/main/java/com/sanha/moneytalk/core/ad/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Budget Credit Monetization 기능 흐름

## 예산

```text
SettingsScreen
-> BudgetBottomSheet
-> SettingsViewModel.saveBudgets()
-> BudgetDao
-> Home/Chat budget status query
```

## AI 크레딧

```text
ChatViewModel.sendMessage()
-> ChatCreditPolicy.estimate()
-> AiCreditRepository reserve/confirm/refund
-> RewardAdDialog if insufficient
-> RewardAdManager
-> AiCreditLedger
```

## 광고 정책

- release/debug 차이는 `BuildVariantPolicy`, `CreditFeaturePolicy`, `BannerAdVisibilityPolicy`를 같이 확인한다.
- RTDB 설정은 `PremiumConfig`/`PremiumManager`에서 내려온다.
- 로컬 단순 조회는 `ChatCreditPolicy` 비용 0이며 `LocalChatQueryRouter`가 매칭하면 Gemini 호출도 생략한다.

## 검증 질문

1. debug 빌드에서 광고/차감이 비활성화되는가?
2. Gemini 실패 또는 clarification에서 예약 차감이 환불되는가?
3. 보상형 광고 실패 시 크레딧이 지급되지 않는가?
4. 예산 변경 후 Home/Chat 예산 현황이 최신 값을 읽는가?
