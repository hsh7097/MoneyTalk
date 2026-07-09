---
type: domain
title: AI Credit Screen 도메인
description: AI 크레딧 잔액/원장 화면과 보상형 광고/크레딧 정책 연결을 설명한다.
tags: [moneytalk, ai-credit, screen, monetization]
resource: app/src/main/java/com/sanha/moneytalk/feature/aicredit/
timestamp: 2026-07-09T01:55:05+09:00
status: draft
---

# AI Credit Screen 도메인

> 상태: draft
> 기준: 2026-07-09 현재 `feature/aicredit/ui/**`, `AiCreditRepository`, `core/ad/**`, debug build 실기기 Settings 노출 조건 확인

AI Credit Screen은 AI 크레딧 잔액과 사용/충전 원장을 보여주는 보조 화면이다.
설정 탭의 AI 크레딧 row는 `CreditFeaturePolicy`가 허용할 때만 노출된다.
일반 debug build에서는 release gate가 false라 실기기 smoke에서 row가 숨겨질 수 있다.
실기기 검증 전용 APK는 `-Pmoneytalk.monetizationTestOverride=true`로 빌드하면 debug/develop release gate를 우회해 AI 크레딧 화면을 직접 확인할 수 있다.
AI 크레딧 기능이 활성화되면 앱 첫 실행 보상 5크레딧이 1회 지급되고 원장에는 초기 실행 보상으로 표시된다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| 이 문서 | 화면 entry, 핵심 파일, 수익화 정책 연결을 정리한다. | AI 크레딧 화면 작업 |
| [change-log.md](change-log.md) | KB 변경 로그다. | 문서 변경 이유 확인 |
| [../budget-credit-monetization/README.md](../budget-credit-monetization/README.md) | 예산/크레딧/광고 기능 KB다. | 크레딧 정책, 광고 보상, release gate 확인 |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `AiCreditActivity.kt` | AI 크레딧 Activity entry |
| `AiCreditScreen.kt` | 잔액/원장 UI |
| `AiCreditViewModel.kt` | 잔액/원장 state 로딩 |
| `core/database/AiCreditRepository.kt` | 크레딧 balance/ledger repository |
| `core/ad/CreditFeaturePolicy.kt` | 수익화 활성 조건 |
| `core/ad/RewardAdManager.kt` | 보상형 광고 표시 |
