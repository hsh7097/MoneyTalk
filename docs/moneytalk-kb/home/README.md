---
type: domain
title: Home 도메인
description: MoneyTalk 홈 탭의 월별 현황, 로컬 소비 브리핑, 고정 지출 예상, 카테고리 지출, 오늘 거래를 설명한다.
tags: [moneytalk, home, domain, compose]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# Home 도메인

> 상태: draft
> 기준: 2026-09-08 현재 `feature/home/ui/**`, `briefing/**`, `recurring/**`, `data/**` 확인

Home은 첫 번째 하단 탭이며 월별 지출/수입, 남은 예산과 최근 7일 소비, 근거가 있는 고정 지출 예상, 카테고리 지출과 오늘 거래를 보여준다. 브리핑과 예상은 저장된 거래로 계산하며 홈 진입 시 Gemini 문장을 자동 요청하지 않는다. 미분류 자동 분류는 기존 별도 실행 흐름을 유지한다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-structure-map.md](00-structure-map.md) | Home UI/data 파일 구조와 책임을 정리한다. | 변경 파일이 Home 내부 어느 책임인지 판단할 때 |
| [package-reference/README.md](package-reference/README.md) | entry/data/rendering/checklist 문서 인덱스다. | 실제 수정 위치를 더 좁힐 때 |
| [package-reference/01-entry-screen.md](package-reference/01-entry-screen.md) | Home 탭 진입, month pager, 권한/동기화/코치마크 시작점을 설명한다. | 화면 진입, 탭 재클릭, 월 이동 문제 |
| [package-reference/02-data-viewmodel.md](package-reference/02-data-viewmodel.md) | HomeViewModel, repositories, page cache, 로컬 계산 계약을 설명한다. | 월별 cache, 예산·주간 비교·고정 지출 예상 |
| [package-reference/03-rendering-action.md](package-reference/03-rendering-action.md) | HomeScreen 구성, 섹션, 클릭 액션, dialog를 설명한다. | Compose UI, 카테고리 클릭, 미분류 분류, 오늘 거래 |
| [package-reference/04-files-checklist.md](package-reference/04-files-checklist.md) | 수정 전후 확인 파일과 검증 질문이다. | 리뷰 전 누락 점검 |
| [package-reference/05-surface-map.md](package-reference/05-surface-map.md) | Home 화면 블록별 표시 조건, 데이터 출처, 보조 화면 연결을 설명한다. | 홈 섹션 추가/삭제, CTA/필터 영향 점검 |
| [05-file-inventory.md](05-file-inventory.md) | Home 전체 파일 역할 인덱스다. | 파일 후보가 애매할 때 |
| [change-log.md](change-log.md) | Home KB 변경 로그다. | 문서 변경 이유 확인 |

## 기준 패키지

- `app/src/main/java/com/sanha/moneytalk/feature/home/ui/`
- `app/src/main/java/com/sanha/moneytalk/feature/home/data/`
- `app/src/main/java/com/sanha/moneytalk/feature/home/briefing/`
- `app/src/main/java/com/sanha/moneytalk/feature/home/recurring/`

## 핵심 흐름

```text
NavGraph -> HomeScreen
-> HomeViewModel.uiState/pageCache
-> ExpenseRepository / IncomeRepository / BudgetDao / SettingsDataStore
-> SpendingBriefingCalculator / RecurringExpenseForecastCalculator
-> HomePageContent
   -> MonthlyOverviewSection
   -> SpendingBriefingCard
   -> RecurringExpenseForecastCard (현재 회계월, 근거 있는 후보)
   -> SpendingTrendSection
   -> CategoryExpenseSection
   -> today transaction list
-> CategoryDetailActivity / TransactionEditActivity / TransactionQuickActionDialog
```

## 작업 판단

- 홈 탭 진입/월 이동/탭 재클릭은 `package-reference/01-entry-screen.md`를 본다.
- 지출/수입/예산/카테고리 합계, page cache, 브리핑과 예상은 `package-reference/02-data-viewmodel.md`를 본다.
- 카테고리 지출 섹션, 오늘 거래 카드, 미분류 분류 dialog, 홈 코치마크는 `package-reference/03-rendering-action.md`를 본다.
- 홈 내부 화면 블록과 보조 화면 연결을 한 번에 확인하려면 `package-reference/05-surface-map.md`를 본다.
- 카테고리 분류 원리 자체는 [category-classification/README.md](../category-classification/README.md)를 같이 본다.
- 오늘 거래 롱클릭의 단건 수정/삭제는 [transaction-mutation/01-feature-flow.md](../transaction-mutation/01-feature-flow.md)를 본다.
- 기능 선택 근거와 미검증 제품 효과는 [제품 개선 검토](../project-context/04-product-improvements-20260908.md)에 둔다.
