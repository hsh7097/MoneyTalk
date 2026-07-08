---
type: package-reference
title: Home rendering/action
description: HomeScreen의 Composable 섹션, dialog, 클릭 action, 코치마크 연결을 설명한다.
tags: [moneytalk, home, compose, action]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Home rendering/action

## 주요 Composable

| Composable | 역할 | 액션 |
|---|---|---|
| `HomeScreen` | Home 탭 entry, state 수집, dialog/coachmark overlay | 월 이동, 분류 dialog, category detail, transaction edit |
| `HomePageContent` | 월별 page data 기반 전체 홈 content 조합 | 섹션별 callback 전달 |
| `MonthlyOverviewSection` | 월 지출/수입/예산 요약 | 월 이동 header와 함께 확인 |
| `SpendingTrendSection` | 누적/추세 차트 | `HomeSpendingTrendInfo` 확인 |
| `CategoryExpenseSection` | 카테고리별 지출 랭킹 | category chip 선택 또는 상세 이동 |
| `AiInsightCard` | 홈 AI 한줄 인사이트 | 홈 인사이트 모델/비용 확인 |
| `EmptyExpenseSection` | 데이터 없음 상태 | SMS 권한/동기화 CTA 확인 |

## Dialog와 overlay

| UI | 파일 | 역할 |
|---|---|---|
| 미분류 분류 dialog | `HomeScreen.kt` | 미분류 건이 있을 때 전체 분류 실행 유도 |
| Gemini API key 필요 dialog | `HomeScreen.kt` | 분류 기능에 API key가 필요할 때 표시 |
| Home coachmark | `HomeCoachMark.kt`, `CoachMarkOverlay.kt` | 첫 사용 시 홈 주요 영역 안내 |

## Navigation/action

- 카테고리 클릭은 `CategoryDetailActivity.open()`으로 상세 화면을 연다.
- 거래 클릭 또는 수정은 `TransactionEditActivity` 경로를 확인한다.
- 전체 월 동기화는 Activity-scoped `MainViewModel.showFullSyncAdDialog()`와 연결된다.
- `onRequestSmsPermission`은 Activity 권한 요청 callback이므로 화면 내부에서 직접 permission launcher를 만들지 않는다.
