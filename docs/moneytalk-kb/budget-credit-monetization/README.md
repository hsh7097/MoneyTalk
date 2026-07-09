---
type: feature
title: Budget Credit Monetization KB
description: 예산, AI 크레딧, 보상형 광고, 배너 광고, 유료화 후속 계획의 현재 구현 경계를 설명한다.
tags: [moneytalk, budget, ai-credit, monetization, ads]
resource: app/src/main/java/com/sanha/moneytalk/core/ad/
timestamp: 2026-07-09T05:20:00+09:00
status: draft
---

# Budget Credit Monetization KB

> 상태: draft
> 기준: 2026-07-09 현재 `core/ad/**`, `AiCreditRepository`, `BudgetDao`, `SettingsViewModel`, `ChatCreditPolicy`, 루트 수익화 문서 내용을 KB 기준으로 재작성

이 KB는 예산 설정, AI 크레딧 차감/충전, 보상형 광고, 배너 광고, 프리미엄 후속 계획을 다룬다. 원문 기획 문서를 그대로 링크하지 않고, 현재 구현 기준과 후속 계획을 분리해 기록한다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [01-feature-flow.md](01-feature-flow.md) | 예산/크레딧/광고 trigger부터 UI 반영까지 흐름 | 예산, AI 크레딧, 광고 표시 정책 작업 |
| [02-policy-and-plans.md](02-policy-and-plans.md) | 현재 크레딧 정책, RTDB gate, release gate, 후속 결제/심층 분석 계획 | 차감 단가, 보상 수량, 프리미엄/광고 정책 변경 |
| [change-log.md](change-log.md) | 기능 KB 변경 로그 | 변경 이유 확인 |
| [../settings/README.md](../settings/README.md) | 설정 화면 메뉴/진입점 | 예산 설정, AI 크레딧 화면 진입 |
| [../chat/README.md](../chat/README.md) | 채팅 1회 1크레딧, 로컬 조회와 Gemini 경계 | 채팅 비용 정책 검토 |
| [../sms-parsing/README.md](../sms-parsing/README.md) | 이전 문자 월 데이터 가져오기와 SMS 동기화 | 월별 동기화 크레딧 차감 검토 |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `core/ad/CreditFeaturePolicy.kt` | AI 크레딧/광고 기능 활성 조건 |
| `core/ad/BannerAdVisibilityPolicy.kt` | 배너 광고 표시 정책 |
| `core/ad/RewardAdManager.kt` | 보상형 광고 로드/표시/보상 callback |
| `core/database/AiCreditRepository.kt` | 크레딧 balance/ledger repository |
| `core/database/dao/AiCreditDao.kt` | 크레딧 DB DAO |
| `core/database/dao/BudgetDao.kt` | 월별/카테고리 예산 DAO |
| `core/util/ChatCreditPolicy.kt` | 채팅 1회 비용 산정 |
| `feature/settings/ui/BudgetBottomSheet.kt` | 예산 설정 UI |
| `feature/aicredit/ui/**` | AI 크레딧 화면 |

## 현재 정책 한 줄 요약

release 빌드이고 RTDB gate가 켜진 FREE 사용자에게만 크레딧 UI/차감/충전/광고가 노출된다. 채팅 1회 전송은 1크레딧, 과거 월 문자 가져오기는 월 1개당 1크레딧, 보상형 광고 1회는 기본 2크레딧을 지급한다.
