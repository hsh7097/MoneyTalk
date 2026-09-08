---
type: file-inventory
title: Home 파일 인벤토리
description: Home 도메인 파일의 역할, 확인 시점, 함께 볼 파일을 정리한다.
tags: [moneytalk, home, file-inventory]
resource: app/src/main/java/com/sanha/moneytalk/feature/home/
timestamp: 2026-09-09T00:00:00+09:00
status: draft
---

# Home 파일 인벤토리

| 파일 | 역할 | 언제 보는가 | 분류 | 함께 볼 파일 |
|---|---|---|---|---|
| `feature/home/ui/HomeScreen.kt` | 탭 진입, state 수집, pager/dialog/coachmark 조율 | 홈 UI, 카테고리 클릭, 분류 CTA | 핵심 | `HomeViewModel.kt` |
| `feature/home/ui/HomeUiState.kt` | HomeUiState/HomePageData 계약 | 상태 필드 변경 | 핵심 | `HomeViewModel.kt`, `HomePageContent.kt` |
| `feature/home/ui/HomePageContent.kt` | CTA→그라데이션 요약→차트→소비 비교→카테고리→오늘 전체→고정 예상 조합, 활성 페이지의 실제 회계 시작일을 차트에 전달 | 페이지 표시/날짜 선택 초기화 조건 | 핵심 | `HomeScreen.kt`, `component/**` |
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
| `feature/home/ui/component/SpendingTrendSection.kt` | 누적 차트 연결, 홈만 `showCard = false`/`scaleToVisibleLines = true`와 실제 `inspectionPeriodStart` 전달 | 차트 UI/데이터 mapper 변경, CategoryDetail 기본 null 경계 | 핵심 후보 | `HomeSpendingTrendInfo.kt`, `CumulativeTrendSection.kt` |
| `feature/home/ui/model/HomeSpendingTrendInfo.kt` | 비교 조건·월 범례·전월/평균/예산 토글을 공통 차트 정보로 변환, 연도 경계 계산 유지 | 차트 계산/표시 변경 | 보조 | `SpendingTrendSection.kt` |
| `feature/home/ui/model/HomeSpendingComparison.kt` | 당월 같은 경과일·과거 전체·실제 0원·수집 상태의 순수 비교 | 미래 날짜·짧은 전월·미수집 경계 | 핵심 | `HomeSpendingTrendInfo.kt`, 대응 JVM 테스트 |
| `core/ui/component/chart/CumulativeTrendSection.kt` | 공통 차트·토글 조합, Home 선택 날짜 상태와 원본 표시 곡선으로 조회 패널 구성 | 날짜 선택/닫기·기간 변경 초기화, 기존 Canvas 경계 | 공통 | `SpendingTrendSection.kt`, `CumulativeInspectionReadout.kt` |
| `core/ui/component/chart/VicoCumulativeChart.kt` | 기존 Vico 렌더링에 선택 가이드·점과 opt-in 터치 연결 | plot 좌표와 원본 금액 표시 일치 | 공통 | `CumulativeInspectionDecoration.kt`, `CumulativeInspectionGesture.kt` |
| `core/ui/component/chart/CumulativeChartInspection.kt` | 실제 날짜·선택 범위·곡선별 원본 Long 계산, plot 좌표→경과일 변환 | 당월 미래 생략, 비교선 조회, 짧은 전월·실제 0원·회계 시작일 | 공통 | `CumulativeChartInspectionTest.kt` |
| `core/ui/component/chart/CumulativeInspectionGesture.kt` | 탭 선택과 롱프레스 뒤 드래그, 그 전 부모 이동 보존 | 홈 세로 스크롤/가로 월 pager 충돌 | 공통 | `VicoCumulativeChart.kt` |
| `core/ui/component/chart/CumulativeInspectionDecoration.kt` | 실제 plot 위치의 선택 가이드와 곡선별 점 | 표시 좌표·선 색상·주 곡선 강조 | 공통 | `VicoCumulativeChart.kt`, `CumulativeChartInspection.kt` |
| `core/ui/component/chart/CumulativeInspectionReadout.kt` | 차트 아래 날짜/경과일·곡선별 파랑 지출 금액·누적 범위 안내와 닫기 | 조회 패널·큰 글자·선택 해제 | 공통 | `CumulativeTrendSection.kt`, `CumulativeInspectionInteractionTest.kt` |
| `app/src/main/res/values/strings_chart_inspection.xml`, `app/src/main/res/values-en/strings_chart_inspection.xml` | 차트 선택 안내·실제 날짜/경과일·닫기·당월 표시 범위 문구 | 선택 UI 한국어/영어 변경 | 리소스 | `CumulativeInspectionReadout.kt` |
| `feature/home/data/ExpenseRepository.kt` | 지출 조회/저장/수정/삭제 | 홈/내역/채팅 지출 데이터 | 핵심 | `ExpenseDao.kt` |
| `feature/home/data/IncomeRepository.kt` | 수입 조회/저장/수정/삭제 | 홈/내역 수입 데이터 | 핵심 | `IncomeDao.kt` |
| `feature/home/data/CategoryRepository.kt` | 카테고리 관련 조회 | 카테고리 목록/분류 | 핵심 후보 | `core/model/Category.kt` |
| `feature/home/data/CategoryClassifierService*.kt` | 카테고리 자동 분류 orchestration | 미분류 분류/학습 | 핵심 | `category-classification/README.md` |
| `feature/home/data/StoreEmbeddingRepository*.kt` | 거래처 embedding 저장/검색 | 유사도/embedding 분류 | 핵심 후보 | `embedding/README.md` |
| `feature/home/data/StoreRuleRepository.kt` | 사용자 거래처 규칙 저장/조회 | 수동 규칙/일괄 분류 | 핵심 후보 | `store-rule-settings/README.md` |
| `feature/home/data/StoreRuleSyncService.kt` | 규칙 동기화/적용 보조 | 거래처 규칙 동기화 | 보조 | `StoreRuleRepository.kt` |

- `feature/weeklyevidence/WeeklyEvidenceRequest.kt`, `WeeklyEvidenceFilter.kt`, `ui/WeeklyEvidenceActivity.kt`, `WeeklyEvidenceViewModel.kt`, `WeeklyEvidenceScreen.kt`: 브리핑의 정확한 기간·금액 근거를 독립 화면으로 제공한다.
