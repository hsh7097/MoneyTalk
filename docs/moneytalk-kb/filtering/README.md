---
type: feature
title: Filtering 기능
description: 내역 필터, 카드 숨김, 통계 제외, SMS 제외 키워드/발신자 설정의 데이터 노출 필터 흐름을 설명한다.
tags: [moneytalk, filtering, history, card, sms]
resource: app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryFilter.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Filtering 기능

> 상태: draft
> 기준: 2026-07-08 현재 `HistoryFilter.kt`, `CardVisibilityFilter.kt`, `StatsExclusionClassifier.kt`, SMS 제외 repository 확인

Filtering 기능은 거래 데이터는 보존하되 화면 노출/집계/파싱 입력 단계에서 제외하거나 좁히는 규칙을 다룬다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [01-feature-flow.md](01-feature-flow.md) | filter trigger부터 화면/집계 반영까지 흐름을 설명한다. | 필터, 카드 숨김, 통계 제외, SMS 제외 작업 |
| [change-log.md](change-log.md) | Filtering KB 변경 로그다. | 문서 변경 이유 확인 |
| [../history/README.md](../history/README.md) | 내역 화면 KB다. | 내역 필터 UI 작업 |
| [../sms-settings/README.md](../sms-settings/README.md) | SMS 설정 화면 KB다. | 제외 키워드/발신자 UI 작업 |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `feature/history/ui/HistoryFilter.kt` | 내역 filter bottom sheet, 카드/카테고리 선택 sheet |
| `feature/history/ui/HistoryViewModel.kt` | filter state, query/list 반영 |
| `feature/categorydetail/ui/CategoryDetailExpenseFilters.kt` | category detail 전용 keyword/card 필터 |
| `core/util/CardVisibilityFilter.kt` | 제외 카드/선택 카드 필터 정책 |
| `core/util/StatsExclusionClassifier.kt` | 카드대금 납부 등 통계 제외 판정 |
| `core/database/SmsExclusionRepository.kt` | SMS 제외 키워드 저장/조회 |
| `core/database/SmsBlockedSenderRepository.kt` | 차단 발신자 저장/조회 |
