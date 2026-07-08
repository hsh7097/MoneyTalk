---
type: domain
title: Home 도메인
description: MoneyTalk 홈 탭의 월별 현황, 카테고리 지출, AI 인사이트, 미분류 분류 CTA, 홈 코치마크를 설명한다.
tags: [moneytalk, home, domain, compose]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Home 도메인

> 상태: draft
> 기준: 2026-07-08 현재 `feature/home/ui/**`, `feature/home/data/**`, `core/ui/coachmark/**` 확인

Home은 첫 번째 하단 탭이며 월별 지출/수입/예산 현황, 카테고리 지출 랭킹, 오늘 거래, AI 인사이트, 미분류 자동 분류 CTA를 보여준다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-structure-map.md](00-structure-map.md) | Home UI/data 파일 구조와 책임을 정리한다. | 변경 파일이 Home 내부 어느 책임인지 판단할 때 |
| [package-reference/README.md](package-reference/README.md) | entry/data/rendering/checklist 문서 인덱스다. | 실제 수정 위치를 더 좁힐 때 |
| [package-reference/01-entry-screen.md](package-reference/01-entry-screen.md) | Home 탭 진입, month pager, 권한/동기화/코치마크 시작점을 설명한다. | 화면 진입, 탭 재클릭, 월 이동 문제 |
| [package-reference/02-data-viewmodel.md](package-reference/02-data-viewmodel.md) | HomeViewModel, repositories, page cache, AI insight 흐름을 설명한다. | 데이터 로딩, 월별 cache, 카테고리 선택, 인사이트 |
| [package-reference/03-rendering-action.md](package-reference/03-rendering-action.md) | HomeScreen 구성, 섹션, 클릭 액션, dialog를 설명한다. | Compose UI, 카테고리 클릭, 미분류 분류, 오늘 거래 |
| [package-reference/04-files-checklist.md](package-reference/04-files-checklist.md) | 수정 전후 확인 파일과 검증 질문이다. | 리뷰 전 누락 점검 |
| [05-file-inventory.md](05-file-inventory.md) | Home 전체 파일 역할 인덱스다. | 파일 후보가 애매할 때 |
| [change-log.md](change-log.md) | Home KB 변경 로그다. | 문서 변경 이유 확인 |

## 기준 패키지

- `app/src/main/java/com/sanha/moneytalk/feature/home/ui/`
- `app/src/main/java/com/sanha/moneytalk/feature/home/data/`

## 핵심 흐름

```text
NavGraph -> HomeScreen
-> HomeViewModel.uiState/pageCache
-> ExpenseRepository / IncomeRepository / BudgetDao / SettingsDataStore
-> HomePageContent
   -> MonthlyOverviewSection
   -> SpendingTrendSection
   -> AiInsightCard
   -> CategoryExpenseSection
   -> today transaction list
-> CategoryDetailActivity or TransactionEditActivity
```

## 작업 판단

- 홈 탭 진입/월 이동/탭 재클릭은 `package-reference/01-entry-screen.md`를 본다.
- 지출/수입/예산/카테고리 합계, page cache, AI 인사이트는 `package-reference/02-data-viewmodel.md`를 본다.
- 카테고리 지출 섹션, 오늘 거래 카드, 미분류 분류 dialog, 홈 코치마크는 `package-reference/03-rendering-action.md`를 본다.
- 카테고리 분류 원리 자체는 [category-classification/README.md](../category-classification/README.md)를 같이 본다.
