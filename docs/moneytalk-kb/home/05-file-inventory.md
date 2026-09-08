---
type: file-inventory
title: Home 파일 인벤토리
description: Home 도메인 파일의 역할, 확인 시점, 함께 볼 파일을 정리한다.
tags: [moneytalk, home, file-inventory]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# Home 파일 인벤토리

| 파일 | 역할 | 언제 보는가 | 분류 | 함께 볼 파일 |
|---|---|---|---|---|
| `feature/home/ui/HomeScreen.kt` | 탭 진입, state 수집, pager/dialog/coachmark 조율 | 홈 UI, 카테고리 클릭, 분류 CTA | 핵심 | `HomeViewModel.kt` |
| `feature/home/ui/HomeUiState.kt` | HomeUiState/HomePageData 계약 | 상태 필드 변경 | 핵심 | `HomeViewModel.kt`, `HomePageContent.kt` |
| `feature/home/ui/HomePageContent.kt` | 기존 그라데이션 요약 바로 아래 차트, 최근 소비 비교/예상/카테고리/오늘 전체 목록과 CTA 조합 | 페이지 표시 조건 | 핵심 | `HomeScreen.kt`, `component/**` |
| `feature/home/ui/theme/HomeTheme.kt`, `HomeColors.kt` | 홈 전용 기존 색상·Typography·카드/CTA 색상 | 다른 화면 테마와 홈 복원 경계 | 보조 | 공용 `Theme.kt`, `HomeScreen.kt` |
| `feature/home/ui/component/HomeTransactionCard.kt` | 원래 홈 카드 표현, 큰 글자 금액 배치와 기존 Info/클릭/롱클릭 | 오늘 거래 렌더링 | 보조 | `TransactionCardInfo`, `HomePageContent.kt` |
| `feature/home/ui/component/HomeImportDataCta.kt`, `HomeFullSyncCta.kt` | 원래 홈 CTA 표현, 권한/부분 수집/광고 조건·진행 중 차단 유지 | 가져오기 표시/콜백 | 보조 | `HomePageContent.kt`, 공용 CTA |
| `feature/home/ui/component/MonthlyOverviewSection.kt` | 월 이동/월 지출·수입 hero | 월 요약 표시 | 보조 | `HomePageContent.kt` |
| `feature/home/ui/component/CategoryExpenseSection.kt` | 순위 펼치기/선택, 카테고리 행 | 카테고리 UI | 보조 | `HomeCategoryExpenseInfo.kt` |
| `feature/home/ui/component/AiInsightCard.kt` | 기존 인사이트 카드/마스코트 선언, 현재 홈에서 미호출 | 기존 UI 참조 | 보조 | AI 비용 정책 |
| `feature/home/ui/component/EmptyExpenseSection.kt` | 기존 빈 지출 섹션 | 빈 상태 표시 | 보조 | `HomePageContent.kt` |
| `feature/home/ui/model/HomeCategoryExpenseInfo.kt` | 카테고리 순위/예산 표시 계산 | 분류 병합/비율/경고 경계 | 핵심 | `CategoryExpenseSection.kt` |
| `feature/home/ui/HomeViewModel.kt` | 홈 state/cache/data/action | 월별 합계, cache, 로컬 계산, refresh | 핵심 | `ExpenseRepository.kt`, `IncomeRepository.kt` |
| `feature/home/briefing/SpendingBriefing.kt` | 필터 적용된 지출 입력과 계산 결과 | 브리핑 데이터 계약 | 핵심 | `SpendingBriefingCalculator.kt` |
| `feature/home/briefing/SpendingBriefingCalculator.kt` | 예산·오늘 포함 일수·두 주 비교 | 회계월/시간대/금액 경계 | 핵심 | 대응 JVM 테스트 |
| `feature/home/briefing/SpendingBriefingCard.kt`, `BriefingWeeklySection.kt` | 주간 비교가 있을 때 기록 안내·최근 소비 비교만 표시, 예산 UI 제거 | 브리핑 UI/카테고리 진입 | 핵심 | `HomePageContent.kt` |
| `feature/home/recurring/RecurringExpenseForecast.kt` | 예상 날짜/금액과 실제 근거 ID | 예상 데이터 계약 | 핵심 | `RecurringExpenseForecastCalculator.kt` |
| `feature/home/recurring/RecurringExpenseForecastCalculator.kt` | 연속 관측 월 판정·월말 보정 | 고정 지출 예상 정확성 | 핵심 | 대응 JVM 테스트 |
| `feature/home/recurring/RecurringExpenseForecastCard.kt` | 최대 3개와 전체 시트·실제 거래 진입 | 예상 UI | 핵심 | `HomePageContent.kt` |
| `feature/home/ui/coachmark/HomeCoachMark.kt` | 홈 코치마크 step | 온보딩 target 변경 | 핵심 후보 | `core/ui/coachmark/**` |
| `feature/home/ui/component/SpendingTrendSection.kt` | 누적 차트 연결, 홈만 `showCard = false`/`scaleToVisibleLines = true` | 차트 UI/데이터 mapper 변경 | 핵심 후보 | `HomeSpendingTrendInfo.kt` |
| `feature/home/ui/model/HomeSpendingTrendInfo.kt` | 비교 조건·연월 범례·전월/평균/예산 토글을 공통 차트 정보로 변환 | 차트 계산/표시 변경 | 보조 | `SpendingTrendSection.kt` |
| `feature/home/ui/model/HomeSpendingComparison.kt` | 당월 같은 경과일·과거 전체·실제 0원·수집 상태의 순수 비교 | 미래 날짜·짧은 전월·미수집 경계 | 핵심 | `HomeSpendingTrendInfo.kt`, 대응 JVM 테스트 |
| `feature/home/data/ExpenseRepository.kt` | 지출 조회/저장/수정/삭제 | 홈/내역/채팅 지출 데이터 | 핵심 | `ExpenseDao.kt` |
| `feature/home/data/IncomeRepository.kt` | 수입 조회/저장/수정/삭제 | 홈/내역 수입 데이터 | 핵심 | `IncomeDao.kt` |
| `feature/home/data/CategoryRepository.kt` | 카테고리 관련 조회 | 카테고리 목록/분류 | 핵심 후보 | `core/model/Category.kt` |
| `feature/home/data/CategoryClassifierService*.kt` | 카테고리 자동 분류 orchestration | 미분류 분류/학습 | 핵심 | `category-classification/README.md` |
| `feature/home/data/StoreEmbeddingRepository*.kt` | 거래처 embedding 저장/검색 | 유사도/embedding 분류 | 핵심 후보 | `embedding/README.md` |
| `feature/home/data/StoreRuleRepository.kt` | 사용자 거래처 규칙 저장/조회 | 수동 규칙/일괄 분류 | 핵심 후보 | `store-rule-settings/README.md` |
| `feature/home/data/StoreRuleSyncService.kt` | 규칙 동기화/적용 보조 | 거래처 규칙 동기화 | 보조 | `StoreRuleRepository.kt` |

- `feature/weeklyevidence/WeeklyEvidenceRequest.kt`, `WeeklyEvidenceFilter.kt`, `ui/WeeklyEvidenceActivity.kt`, `WeeklyEvidenceViewModel.kt`, `WeeklyEvidenceScreen.kt`: 브리핑의 정확한 기간·금액 근거를 독립 화면으로 제공한다.
