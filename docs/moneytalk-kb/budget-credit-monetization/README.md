---
type: feature
title: Budget Credit Monetization 기능
description: 예산, AI 크레딧, 보상형 광고, 배너 광고 표시 정책과 release gate를 설명한다.
tags: [moneytalk, budget, ai-credit, monetization, ads]
resource: app/src/main/java/com/sanha/moneytalk/core/ad/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Budget Credit Monetization 기능

> 상태: draft
> 기준: 2026-07-08 현재 `core/ad/**`, `AiCreditRepository`, `BudgetDao`, `SettingsViewModel`, `ChatCreditPolicy` 확인

이 기능 KB는 월 예산/카테고리 예산, AI 크레딧, 보상형 광고 충전, 배너 광고 표시 정책을 함께 다룬다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [01-feature-flow.md](01-feature-flow.md) | 예산/크레딧/광고 trigger부터 UI 반영까지 흐름을 설명한다. | 예산, AI 크레딧, 광고 정책 작업 |
| [change-log.md](change-log.md) | 기능 KB 변경 로그다. | 문서 변경 이유 확인 |
| [../settings/README.md](../settings/README.md) | 설정 탭 KB다. | 예산 설정, AI 크레딧 화면 진입 |
| [../chat/README.md](../chat/README.md) | 채팅/크레딧 비용 KB다. | 채팅 1회 1크레딧, 로컬 조회 Gemini 우회 처리 |
| ../../MONETIZATION.md | 수익화 문서다. | 비용/수익 구조와 release 정책 확인 |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `core/ad/CreditFeaturePolicy.kt` | 크레딧/광고 기능 활성 조건 |
| `core/ad/BannerAdVisibilityPolicy.kt` | 배너 광고 표시 정책 |
| `core/ad/RewardAdManager.kt` | 보상형 광고 표시와 reward callback |
| `core/database/AiCreditRepository.kt` | 크레딧 balance/ledger repository |
| `core/database/dao/AiCreditDao.kt` | 크레딧 DB DAO |
| `core/database/dao/BudgetDao.kt` | 총/카테고리 예산 DB DAO |
| `core/util/ChatCreditPolicy.kt` | 채팅 1회 1크레딧 비용 산정 |
| `feature/settings/ui/BudgetBottomSheet.kt` | 예산 설정 UI |
| `feature/aicredit/ui/**` | AI 크레딧 화면 |
