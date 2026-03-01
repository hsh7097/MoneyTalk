# 화면별 요구사항 명세서

> **목적**: AI 에이전트가 작업 시 요구사항을 놓치지 않도록, 화면별 현재 구현 상태와 스펙을 코드 기준으로 정리한 문서
>
> **최종 갱신**: 2026-02-24 | **기준 브랜치**: develop (62a0c45)

---

## 목차

1. [인트로 (Splash + Permission)](#1-인트로)
2. [홈 화면](#2-홈-화면)
3. [가계부 (History)](#3-가계부-history)
4. [AI 채팅](#4-ai-채팅)
5. [설정](#5-설정)
6. [카테고리 상세](#6-카테고리-상세)
7. [공통 컴포넌트](#7-공통-컴포넌트)
8. [글로벌 시스템](#8-글로벌-시스템)

---

## 1. 인트로

**파일**: `feature/intro/ui/IntroActivity.kt`, `feature/splash/ui/SplashScreen.kt`, `feature/intro/ui/OnboardingScreen.kt`, `feature/intro/ui/PermissionScreen.kt`

### 1.1 스플래시

| 항목 | 스펙 |
|------|------|
| 애니메이션 | 지갑 아이콘 + 앱명 + 태그라인, scale/alpha 500ms + 1.5s 딜레이 |
| 배경 | Primary 그라데이션 (PrimaryLight → Primary → PrimaryDark) |
| 전환 | 총 2초 후 자동 전환 |
| 분기 | onboardingCompleted → 바로 MainActivity / 아니면 → Onboarding |

### 1.2 온보딩 인트로

| 항목 | 스펙 |
|------|------|
| UI | 3페이지 HorizontalPager (전체 화면 스와이프) |
| 배경 | Primary 그라데이션 (스플래시와 동일) |
| 페이지 구성 | 이모지(72sp) + 제목(26sp Bold) + 설명(15sp) + feature bullets 3줄 (반투명 카드) |
| 페이지 1 | 💳 문자만으로 자동 가계부 — SMS 자동 인식, 금액·가맹점 추출, 수입 분류 |
| 페이지 2 | 🤖 AI 분류·분석 — 18개 카테고리, 자연어 조회, 소비 패턴 분석 |
| 페이지 3 | 🔒 로컬 데이터 보호 — 서버 전송 없음, Google Drive 백업, 전체 삭제 가능 |
| 인디케이터 | 3개 dot (하단 고정, 활성/비활성 색상 애니메이션) |
| 건너뛰기 | 상단 우측 TextButton, 마지막 페이지에서 fade out |
| 시작하기 | 마지막 페이지에서만 AnimatedVisibility로 노출 (fadeIn + slideInVertically) |
| 전환 | 시작하기/건너뛰기 → PermissionScreen |
| 재진입 | onboardingCompleted=true이면 스킵 (기존 사용자 영향 없음) |

### 1.3 권한 요청

| 항목 | 스펙 |
|------|------|
| UI | 딤 배경 + 카드형 모달 |
| 요청 권한 | READ_SMS, RECEIVE_SMS (Android 13+: POST_NOTIFICATIONS 추가) |
| 버튼 | "동의함" / "동의안함" (둘 다 진행 가능) |
| 완료 후 | onboardingCompleted=true → RTDB 설정 로드 (3초 타임아웃) → MainActivity |

### 1.4 강제 업데이트

| 항목 | 스펙 |
|------|------|
| 조건 | RTDB의 minVersionCode > 현재 versionCode |
| UI | AlertDialog (취소 불가, Predictive Back 방어) + 업데이트 메시지 (RTDB에서 가져옴) |
| 동작 | Play Store로 이동 |

---

## 2. 홈 화면

**파일**: `feature/home/ui/HomeScreen.kt` (1156줄), `feature/home/ui/HomeViewModel.kt` (1717줄)

### 2.1 월 네비게이션

| 항목 | 스펙 |
|------|------|
| 방식 | HorizontalPager (1200 페이지, 중앙 = 현재월) |
| 프리로드 | beyondViewportPageCount = 1 |
| 캐시 | 현재 ± 2개월만 유지, 벗어나면 evict |
| 월 시작일 | Settings의 monthStartDay (1~31) 반영 |
| 기간 레이블 | "12/19 ~ 1/18" 형식 (커스텀 월 시작일 시) |
| 미래 월 | 이동 가능하나 데이터 없음 |

### 2.2 Hero 섹션 (MonthlyOverviewSection)

| 항목 | 스펙 |
|------|------|
| 배경 | Navy 그라데이션 카드 |
| 주 지표 | 월간 총 지출 (30sp Bold) |
| 부 지표 | 월간 총 수입 (흰색 점 + 금액) |
| 전월 비교 | 배지: "₩금액(N%) 더 쓰고 있어요" / "덜 쓰고 있어요" |
| 색상 | 증가=빨강, 감소=초록 |
| 월 전환 | 좌우 화살표 + 캘린더 레이블 |

### 2.3 CTA 섹션

| 조건 | CTA 종류 | 동작 |
|------|----------|------|
| 현재월 + 권한없음/데이터없음 | ImportDataCtaSection | SMS 권한 요청 + 증분 동기화 |
| 과거월 + 미동기화 | FullSyncCtaSection | 무료 잔여 시 바로 동기화 / 소진 후 광고 시청 → 동기화 |
| 과거월 + 부분 커버리지 | FullSyncCtaSection (variant) | 무료 잔여 시 바로 동기화 / 소진 후 광고 시청 → 동기화 |

### 2.4 누적 추이 차트 (SpendingTrendSection)

| 항목 | 스펙 |
|------|------|
| 차트 라이브러리 | Vico (AxisValueOverrider.fixed, 수평 스크롤 제거) |
| 메인 라인 | 이번 달 일별 누적 지출 (항상 표시) |
| 토글 라인 | 전월 누적 **(기본 ON)**, 3개월 평균, 6개월 평균, 월 예산 (기본 OFF) |
| X축 | 5일 간격 레이블, addExtremeLabelPadding=true |
| Y축 | 표시 라인 전체 기준 max (독립 계산) |
| 전월 비교 텍스트 | "같은 기간 대비 ₩X 더/덜 쓰고 있어요" |
| 갱신 | 동기화 데이터 변경 시 LaunchedEffect keys로 새로고침 |

### 2.5 카테고리별 지출 (CategoryExpenseSection)

| 항목 | 스펙 |
|------|------|
| 차트 | 수평 바 차트 (상위 5개 카테고리) |
| 접기/펼치기 | 5개 초과 시 "전체보기/접기" 토글 |
| 행 구성 | 카테고리 아이콘 + 이름 + 금액 + 비율(%) |
| 예산 표시 | 설정된 경우: "₩사용 / ₩예산" + 잔여/초과 |
| 색상 | 1위=Primary, 나머지=카테고리별 색상, 예산초과=Error |
| 클릭 | 카테고리 선택 → CategoryDetailActivity 진입 |

### 2.6 AI 인사이트 카드

| 항목 | 스펙 |
|------|------|
| 생성 | GeminiRepository.generateHomeInsight() |
| 내용 | 소비 분석 + 전월 비교 한줄 코멘트 |
| 중복 방지 | input hash 기반 (동일 데이터면 재호출 안 함) |
| 호출 시점 | 선택된 월에서만 (불필요 API 호출 방지) |

### 2.7 오늘의 가계부

| 항목 | 스펙 |
|------|------|
| 표시 조건 | 현재 월 페이지에서만 |
| 헤더 | "오늘의 가계부" + 오늘 총 지출 + 건수 |
| 목록 | 지출 + 수입 통합, 시간 역순 |
| 카드 | TransactionCardCompose (공용) |
| 클릭 | 지출 → ExpenseDetailDialog / 수입 → IncomeDetailDialog |
| 빈 상태 | "오늘 거래 내역이 없습니다" 메시지 |

### 2.8 스크롤 FAB

| 항목 | 스펙 |
|------|------|
| 표시 조건 | 200dp 이상 스크롤 또는 첫 아이템 지남 |
| 색상 | Income 색상 (Green) |
| 동작 | 탭 → 최상단 스크롤 |
| 애니메이션 | fade in/out |

### 2.9 다이얼로그

| 다이얼로그 | 용도 |
|-----------|------|
| ExpenseDetailDialog | 지출 상세 (카테고리 변경, 메모, 삭제) |
| IncomeDetailDialog | 수입 상세 (메모, 삭제) |
| SMS Sync Progress | 증분/전체 동기화 진행 (취소 불가) |
| Category Classification | 미분류 항목 분류 진행 |
| Full Sync Ad Dialog | 광고 시청 → 과거 월 동기화 잠금 해제 |

### 2.10 SMS 동기화

| 항목 | 스펙 |
|------|------|
| 증분 동기화 | lastSyncTime - 5분 ~ 현재 (경계 SMS 안전 마진) |
| 초기 동기화 | 전월 1일 ~ 현재 (monthStartDay > 1이면 2개월 전부터) |
| 전체 동기화 | 처음 N회 무료 (RTDB `free_sync_count`, 기본 3) → 이후 광고 시청 필요 |
| 실시간 수신 | SmsProcessingService (BroadcastReceiver, 항상 무료) |
| Auto Backup 감지 | lastSyncTime > 0 but DB 비어있음 → 초기로 리셋 |
| 최대 범위 | 현재일 - 60일 |
| Silent 모드 | 다이얼로그 없이 백그라운드 (새 데이터 시 스낵바) |
| 진행 표시 | 상태 텍스트 + determinate bar (total > 0 시) |

### 2.11 자동 분류

| 항목 | 스펙 |
|------|------|
| 트리거 | onResume 시 미분류 항목 존재하면 백그라운드 |
| Phase 1 | 상위 50개 가게 (금액 기준) |
| Phase 2 | 나머지 항목 (최대 3라운드) |
| 수동 분류 | 같은 가게명 전체 일괄 변경 + 유사 가게 전파 (벡터 ≥ 0.90) |

### 2.12 [구현 상세] 데이터 로드 & 기간 계산

#### 기간 계산 공식 (`DateUtils.getCustomMonthPeriod`)

```
monthStartDay = 1일 경우:
  시작: month/1 00:00:00
  종료: month/말일 23:59:59.999

monthStartDay > 1일 경우 (예: 21):
  시작: (month-1)/monthStartDay 00:00:00
  종료: month/(monthStartDay-1) 23:59:59.999
  예) 3월 조회 → 2/21 00:00:00 ~ 3/20 23:59:59.999
```

#### 데이터 로드 시점 & 트리거

| 시점 | 함수 | 동작 |
|------|------|------|
| **앱 시작** | `refreshData()` | SMS 동기화(다이얼로그) + loadCurrentAndAdjacentPages() |
| **화면 복귀 (Resume)** | `refreshData()` | SMS 동기화(silent) + 자동 분류 시도 |
| **HorizontalPager 스와이프** | `setMonth()` → `loadCurrentAndAdjacentPages()` | 현재+이전+다음 월 로드 + 범위 밖 evict |
| **DataRefreshEvent** | `refreshCurrentPages(forceReload=true)` | 캐시 유지하며 덮어쓰기 |
| **Pull-to-Refresh** | `refresh()` | 현재 + 인접 3개월 데이터만 갱신 |

#### 캐시 전략 (PAGE_CACHE_RANGE = 2)

```
캐시 유지: 현재 월 ± 2개월 = 최대 5개월
Evict 시점: 매월 이동 시 → abs(대상 - 현재) > 2이면 Job 취소 + 캐시 삭제

forceReload=false: 캐시에 isLoading=false인 데이터 있으면 스킵
forceReload=true:  캐시 유지하되 데이터 덮어쓰기
```

#### monthStartDay 변경 시 재계산 흐름

```
settingsDataStore.monthStartDayFlow (distinctUntilChanged)
  → _uiState.copy(monthStartDay = newDay)
  → settingsDataStore.resetSyncedMonths()  // 광고 기록 초기화
  → clearAllPageCache()                     // 전체 캐시 삭제
  → loadCurrentAndAdjacentPages()           // 새 기간으로 재로드
```

#### SMS 동기화 날짜 범위 (`calculateIncrementalRange`)

```
증분 동기화:
  시작 = max(lastSyncTime - 5분, now - 60일 - monthStartDay마진)
  종료 = now

초기 동기화 (lastSyncTime = 0):
  monthStartDay = 1 → 전월 1일 00:00
  monthStartDay > 1 → 2개월 전 monthStartDay 00:00

전체 동기화 (광고 잠금 해제):
  시작 = getCustomMonthPeriod(year, month).first
  종료 = getCustomMonthPeriod(year, month).second
  ※ lastSyncTime 갱신 안 함

Auto Backup 감지:
  savedSyncTime > 0 AND dbCount == 0 → syncTime을 0으로 리셋 → 초기 동기화
```

| 상수 | 값 | 용도 |
|------|------|------|
| `DEFAULT_SYNC_PERIOD_MILLIS` | 60일 | 기본 증분 동기화 커버리지 |
| `OVERLAP_MARGIN_MILLIS` | 5분 | 네트워크 지연 안전 마진 |
| `DB_BATCH_INSERT_SIZE` | 100 | DB 배치 삽입 크기 |

### 2.13 [구현 상세] 차트 Y축 & 토글 처리

#### Y축 최대값 계산 (`ceilToNiceValue`)

```kotlin
fun ceilToNiceValue(rawMax: Long): Long {
    if (rawMax <= 0L) return 1L
    val unit = 2_000_000L  // 200만원 단위
    if (rawMax <= 1_000_000L) return 1_000_000L  // 최소 100만원
    return ((rawMax + unit - 1) / unit) * unit   // 200만원 단위 올림
}
// 가능한 값: 100만, 200만, 400만, 600만, 800만, 1000만, ...
```

#### Y축 고정 전략

```
Y축 max = ceilToNiceValue(max(primary의 max, 모든 toggleable의 max))
→ 초기화 시점에 모든 라인(토글 OFF 포함) 기준으로 고정
→ 토글 ON/OFF 시 Y축 변동 없음 (AxisValueOverrider.fixed)
```

#### 토글 라인 ON/OFF 애니메이션

```
Toggle ON:
  1. alpha → 즉시 1f (snapTo)
  2. Vico가 0 → 실제값 애니메이션 (Vico 내장)

Toggle OFF:
  1. Vico가 실제값 → 0 애니메이션 (350ms)
  2. delay(350ms) 후 alpha → 0f (150ms, tween)

핵심: 라인을 Series에서 제거하지 않고 points를 [0, 0, 0, ...] 으로 치환
→ Series 개수 유지 → Vico diff 애니메이션 안정
```

#### 전월 비교 텍스트 생성

```
referencePoint = now가 이번달 범위 안이면 now, 아니면 monthEnd
elapsedDays = (referencePoint - monthStart) / DAY_MS

전월 같은 기간:
  lastMonthSamePoint = lastMonthStart + (elapsedDays * DAY_MS)
  → 전월의 동일 경과일까지만 조회하여 비교

비교 텍스트:
  차이 < 3% → "비슷한 수준이에요"
  차이 > 0  → "₩X를 더 썼어요 (N%)" (isOverSpending=true)
  차이 < 0  → "₩X를 덜 썼어요 (N%)" (isOverSpending=false)
```

#### 3개월/6개월 평균 누적 계산

```
buildAvgNMonthCumulative(n, year, month, monthStartDay, daysInMonth):
  1. 과거 n개월 각각 getCustomMonthPeriod()로 조회
  2. 하나라도 데이터 없으면 → 빈 리스트 반환 (차트 미표시)
  3. 짧은 월(28일 등) → 마지막 누적값을 carry-forward
  4. 일별 평균 = sum(각 월의 해당 일 누적) / n
  5. 반환 크기 = daysInMonth + 1 (시작점 0원 포함)
```

#### 예산 선형 누적

```
buildBudgetCumulativePoints(monthlyBudget, daysInMonth):
  Day 0: 0원
  Day k: monthlyBudget * k / daysInMonth  (일별 균등 분배)
  Day last: monthlyBudget
```

### 2.14 [구현 상세] AI 인사이트 호출 조건

```
호출 조건:
  1. 현재 선택된 월(selectedYear/Month)의 Flow 첫 emit에서만
  2. insightLoaded 플래그로 1회 실행 제한

중복 호출 방지 (Hash):
  inputHash = hash(monthlyExpense, lastMonthExpense, todayExpense, top3Categories, lastMonthTop3)
  if (inputHash == lastInsightInputHash && 기존 인사이트 존재) → 스킵

월 변경 시:
  setMonth() → lastInsightInputHash.set(0)  // 해시 리셋
  → 새 월에서 자연스럽게 재생성

응답 검증:
  AI 응답 도착 시점에 selectedYear/Month가 여전히 요청 월과 일치해야만 반영
```

### 2.15 [구현 상세] 카테고리 그룹핑 & 예산 비교

```
카테고리 그룹핑:
  expenses.groupBy { expense →
    val cat = Category.fromDisplayName(expense.category)
    cat.parentCategory?.displayName ?: cat.displayName  // 부모 있으면 부모로 통합
  }
  → sortedByDescending(total)

예산 비교:
  BudgetEntity 조회 (yearMonth = "YYYY-MM")
  → "전체" → monthlyBudget
  → 나머지 → Map<String, Int> (카테고리별)
  Composable에서: spent vs budget → 잔여/초과 + 퍼센트 계산
```

### 2.16 [구현 상세] 이벤트 구독 & 반응

| 이벤트 | HomeViewModel 반응 |
|--------|-------------------|
| `ALL_DATA_DELETED` | classificationState.cancel → clearAllPageCache → loadSettings 재시작 |
| `CATEGORY_UPDATED` | refreshCurrentPages(forceReload=true) — 캐시 유지, 데이터 덮어쓰기 |
| `OWNED_CARD_UPDATED` | refreshCurrentPages(forceReload=true) |
| `TRANSACTION_ADDED` | refreshCurrentPages(forceReload=true) |
| `SMS_RECEIVED` | calculateIncrementalRange → syncSmsV2(silent=true) |
| `monthSyncEvent` | calculateMonthRange → syncSmsV2(updateLastSyncTime=false) |
| `incrementalSyncEvent` | consumeIncrementalSync → syncIncremental |

---

## 3. 가계부 (History)

**파일**: `feature/history/ui/HistoryScreen.kt`, `HistoryViewModel.kt`, `HistoryHeader.kt`, `HistoryFilter.kt`, `HistoryCalendar.kt`, `HistoryDialogs.kt`

### 3.1 뷰 모드

| 모드 | 설명 |
|------|------|
| LIST (기본) | 날짜별 그룹핑, 최신순, 지출+수입 통합 |
| CALENDAR | 월간 그리드, 일별 합계, 무지출일 표시 |

### 3.2 헤더 (HistoryHeader)

| 컴포넌트 | 스펙 |
|----------|------|
| SearchBar | OutlinedTextField + clear 버튼, 실시간 검색 |
| PeriodSummaryCard | 좌우 화살표 + 기간 레이블 (커스텀 월 시작일 반영) |
| 금액 표시 | 총 지출(빨강) + 총 수입(초록) 우측 정렬 |
| 미래 월 화살표 | 비활성화 |

### 3.3 필터 탭 (FilterTabRow)

| 항목 | 스펙 |
|------|------|
| 탭 | LIST / CALENDAR 세그먼트 토글 |
| 필터 칩 | 아이콘 + 텍스트, 필터 활성 시 하이라이트 |
| 우측 아이콘 | 검색 + 추가(+) |
| 필터 클릭 | FilterBottomSheet 열기 |

### 3.4 필터 BottomSheet

| 항목 | 스펙 |
|------|------|
| 정렬 | 날짜순(기본) / 금액순 / 가게빈도순 — 3개 칩 |
| 거래 유형 | 지출 / 수입 체크박스 (상호 비배타적, 둘 다 해제 불가) |
| 카테고리 | 3열 FlowRow 그리드, "전체" + 부모 카테고리들 |
| 초기화 | 기본값과 다를 때만 표시 |
| 적용 | 하단 고정 버튼 |
| 상단 여백 | 100dp |

### 3.5 거래 목록 (TransactionListView)

| 항목 | 스펙 |
|------|------|
| 렌더링 | LazyColumn + scroll-to-top FAB |
| 그룹핑 (DATE_DESC) | "15일 (월)" 헤더 + 시간 역순 항목 |
| 그룹핑 (AMOUNT_DESC) | 단일 헤더 + 금액 내림차순 전체 목록 |
| 그룹핑 (STORE_FREQ) | "스타벅스 (5회)" 헤더 + 빈도순 그룹 |
| CTA (빈 상태) | 현재월 권한없음/데이터없음 → ImportDataCTA |
| CTA (빈 과거) | 과거월 미동기화 → FullSyncCTA (광고) |
| CTA (부분) | 데이터 있으나 불완전 → FullSyncCTA |
| 스크롤 리셋 | 필터/월 변경 시 scrollResetKey로 최상단 |

### 3.6 캘린더 뷰 (BillingCycleCalendarView)

| 항목 | 스펙 |
|------|------|
| 기간 | monthStartDay 기반 (예: 19일~18일) |
| 그리드 | 일~토 주간 그리드, 이전/다음 달 날짜로 패딩 |
| 셀 구성 | 날짜(원형) + 일별 지출 합계(빨강 소텍스트) |
| 오늘 | 파랑 배경 원형 |
| 선택 | 연한 파랑 배경, 미래/범위외 클릭 불가 |
| 주간 합계 | 주 우측에 합계 (빨강, >0일 때만) |
| 무지출 배너 | surfaceVariant 카드, "N월 무지출" + "총 N일 >" (오늘까지 기준) |
| 상세 패널 | 날짜 클릭 → 해당 일 지출 인라인 표시 (토글) |
| 카드 | TransactionCardCompose → 클릭 시 ExpenseDetailDialog |

### 3.7 수동 지출 추가 (AddExpenseDialog)

| 항목 | 스펙 |
|------|------|
| 필드 | 금액(숫자만), 가게명(자유텍스트), 카테고리(드롭다운), 결제수단(기본="현금") |
| 기본 카테고리 | Category.ETC |
| 확인 조건 | 금액 > 0 AND 가게명 입력 |

### 3.8 수입 상세 (IncomeDetailDialog)

| 항목 | 스펙 |
|------|------|
| 표시 | 아이콘(💰), 유형, 출처, 금액, 시간, 반복여부 |
| 메모 | 편집 가능 (인라인 또는 다이얼로그) |
| 원문 SMS | 카드형 표시 (읽기전용) |
| 삭제 | 빨강 버튼 + 확인 다이얼로그 |

### 3.9 외부 카테고리 필터

| 항목 | 스펙 |
|------|------|
| 소스 | Home 화면 카테고리 행 클릭 → filterCategory 파라미터 |
| 동작 | 1회성 필터 적용 (해당 카테고리만 표시) |
| 리셋 | 탭 재클릭 시 기본 상태로 복원 |

### 3.10 동기화 연동

| 항목 | 스펙 |
|------|------|
| 진행 표시 | HomeViewModel의 syncProgress Flow 구독 |
| 다이얼로그 | 취소 불가 AlertDialog + 진행바 |
| 광고 잠금 해제 | 과거 월 per-month 광고 시청 |
| 이벤트 구독 | DataRefreshEvent → clearAllPageCache + 재로드 |

### 3.11 [구현 상세] 데이터 로드 & 필터 적용

#### 기간 계산

```
HistoryViewModel.loadPageData(year, month):
  val (startTime, endTime) = DateUtils.getCustomMonthPeriod(year, month, state.monthStartDay)
  → Home과 동일한 기간 계산 공식 사용
```

#### 필터 적용 순서

```
1. DB 쿼리: expenseRepository.getExpensesByDateRange(startTime, endTime) [Flow]
2. 제외 키워드: expenses.filter { e → 키워드 불포함 }
3. 카테고리 필터: selectedCategory != null → 해당 카테고리만
4. 검색어 필터: searchQuery.isNotBlank → storeName/category/memo 매칭
5. 정렬 적용: DATE_DESC / AMOUNT_DESC / STORE_FREQ
```

#### 캐시 전략

```
HistoryViewModel도 PAGE_CACHE_RANGE = 2 사용
캐시 key = MonthKey(year, month)
clearAllPageCache(): 전체 캐시 삭제 (이벤트 수신 시)
scrollResetKey: 필터/월 변경 시 최상단 리셋 (UUID 기반)
```

#### 캘린더 뷰 데이터

```
BillingCycleCalendarView 입력 데이터:
  - billingStartDay: monthStartDay
  - dailyExpenseMap: Map<LocalDate, Int> (일별 합계)
  - 무지출일 계산: billingStart ~ min(today, billingEnd) 중 지출=0인 날 카운트
  - 주간 합계: weekStart~weekEnd 내 dailyExpenseMap 합산
```

---

## 4. AI 채팅

**파일**: `feature/chat/ui/ChatScreen.kt`, `ChatRoomListView.kt`, `ChatComponents.kt`, `ChatViewModel.kt` (1972줄)

### 4.1 세션 목록 (ChatRoomListView)

| 항목 | 스펙 |
|------|------|
| 헤더 | "AI 재무 상담" + API 상태 서브타이틀 |
| 새 대화 | 우측 상단 + 버튼 |
| API 키 없음 | 설정 버튼 표시 |
| 빈 상태 | 💬 아이콘 + "대화 내역이 없습니다" + "새 대화 시작" 버튼 |
| 세션 카드 | 제목(1줄) + 최종 업데이트 시간 + 삭제 버튼(빨강) |
| 정렬 | updatedAt DESC (최신순) |
| 삭제 | 확인 다이얼로그 후 세션 + 메시지 cascade 삭제 |

### 4.2 채팅방 (ChatRoomView)

| 항목 | 스펙 |
|------|------|
| 헤더 | ← 뒤로 + 세션 제목 + API 상태/남은 횟수 |
| 메시지 목록 | LazyColumn, 자동 최하단 스크롤 |
| 사용자 버블 | 우측 정렬, Primary 색상, 둥근 모서리 |
| AI 버블 | 좌측 정렬, Secondary 색상, 텍스트 선택 가능 |
| 타임스탬프 | 버블 하단 소텍스트 |
| 최대 너비 | 300dp |
| 로딩 | TypingIndicator (바운싱 3점 + "생각 중...") |
| 실패 | RetryButton (빨강, 새로고침 아이콘) |

### 4.3 가이드 질문

| 항목 | 스펙 |
|------|------|
| 표시 조건 | 채팅 비어있을 때 |
| 그룹 (3개) | 📊 분석 / 🔍 조회 / 🏷 관리 |
| 질문 수 | 총 11개 사전 정의 |
| API 키 없음 | 질문 비활성화 (회색 50% 투명) + 에러 메시지 |
| 클릭 | 해당 질문을 직접 전송 |

### 4.4 입력 섹션

| 항목 | 스펙 |
|------|------|
| 텍스트 필드 | OutlinedTextField, 최대 3줄 |
| 비활성화 | API 키 없음 OR 로딩 중 |
| 전송 버튼 | 텍스트 비어있지 않을 때만 활성 |

### 4.5 3-Step AI 파이프라인

| 단계 | 모델 | Temperature | 역할 |
|------|------|-------------|------|
| Step 1: Query Analysis | Gemini 2.5-pro | 0.3 | 사용자 질문 → JSON {queries, actions, clarification} |
| Step 2: Data Execution | Local (Room DB) | - | 18개 쿼리 + 13개 액션 실행 |
| Step 3: Final Answer | Gemini 2.5-pro | 0.7 | 컨텍스트 + DB 결과 → 자연어 응답 |

### 4.6 쿼리 타입 (18개)

| 타입 | 설명 |
|------|------|
| TOTAL_EXPENSE | 기간 내 총 지출 (카테고리 선택적) |
| TOTAL_INCOME | 기간 내 총 수입 |
| EXPENSE_BY_CATEGORY | 카테고리별 지출 |
| EXPENSE_LIST | 최근 지출 목록 |
| EXPENSE_BY_STORE | 가게별 지출 (별칭 해석) |
| EXPENSE_BY_CARD | 카드별 지출 |
| DAILY_TOTALS | 일별 지출 합계 |
| MONTHLY_TOTALS | 월별 지출 |
| MONTHLY_INCOME | 설정된 월 수입 |
| UNCATEGORIZED_LIST | 미분류 항목 |
| CATEGORY_RATIO | 수입 대비 분석 |
| SEARCH_EXPENSE | 키워드 전문 검색 |
| CARD_LIST | 사용 카드 목록 |
| INCOME_LIST | 수입 거래 목록 |
| DUPLICATE_LIST | 중복 감지 목록 |
| SMS_EXCLUSION_LIST | SMS 블랙리스트 |
| BUDGET_STATUS | 예산 사용 현황 |
| ANALYTICS | 복합 필터/그룹핑/집계 |

### 4.7 액션 타입 (13개)

| 타입 | 설명 |
|------|------|
| UPDATE_CATEGORY | 단일 항목 카테고리 변경 |
| UPDATE_CATEGORY_BY_STORE | 가게별 일괄 변경 |
| UPDATE_CATEGORY_BY_KEYWORD | 키워드 기반 일괄 변경 |
| DELETE_EXPENSE | 단일 삭제 |
| DELETE_BY_KEYWORD | 키워드 기반 일괄 삭제 |
| DELETE_DUPLICATES | 중복 전체 삭제 |
| ADD_EXPENSE | 수동 지출 추가 |
| UPDATE_MEMO | 메모 수정 |
| UPDATE_STORE_NAME | 가게명 수정 |
| UPDATE_AMOUNT | 금액 수정 |
| ADD_SMS_EXCLUSION | SMS 제외 키워드 추가 |
| REMOVE_SMS_EXCLUSION | SMS 제외 키워드 삭제 |
| SET_BUDGET | 카테고리 예산 설정 |

### 4.8 ANALYTICS 쿼리 (복합)

| 항목 | 스펙 |
|------|------|
| 필터 | category, storeName, cardName, amount, memo, dayOfWeek |
| 연산자 | ==, !=, >, >=, <, <=, contains, not_contains, in, not_in |
| 그룹핑 | category, storeName, cardName, date, month, dayOfWeek |
| 메트릭 | sum, avg, count, max, min |
| TopN | 결과 제한 |
| 정렬 | asc / desc |
| 소 카테고리 | includeSubcategories 지원 |

### 4.9 명확화 루프 (Clarification)

| 항목 | 스펙 |
|------|------|
| 조건 | AI가 JSON에 clarification 필드 반환 시 |
| 동작 | 질문을 AI 메시지로 저장, 쿼리/액션 건너뜀 |
| 사용자 | 추가 답변 후 재분석 |

### 4.10 Rolling Summary

| 항목 | 스펙 |
|------|------|
| 윈도우 | 최근 6개 메시지 (3턴 = 사용자+AI 쌍) |
| 초과 시 | 윈도우 밖 메시지 → Gemini Flash로 요약 |
| 저장 | ChatSessionEntity.currentSummary |
| 컨텍스트 | summary + 최근 6개 → AI에게 전달 |

### 4.11 세션 관리

| 항목 | 스펙 |
|------|------|
| 자동 생성 | 세션 없으면 "새 대화" 생성 |
| 자동 제목 | 채팅방 나갈 때 최근 6개 메시지로 생성 (fire-and-forget) |
| 폴백 제목 | 첫 사용자 메시지 30자 |
| 타임스탬프 | 매 메시지마다 updatedAt 갱신 |
| 삭제 | cascade (세션 + 전체 메시지) |

### 4.12 보상 광고

| 항목 | 스펙 |
|------|------|
| 게이팅 | remaining=0 시 메시지 시도하면 광고 다이얼로그 |
| 대기 메시지 | 임시 저장 → 광고 시청 후 재전송 |
| 횟수 표시 | 서브타이틀: "남은 무료 상담: N회" |
| 프리로드 | rewardAdEnabled 변경 시 자동 프리로드 |
| 실패 처리 | 광고 로드/표시 실패 → 보상 적용 (사용자 친화) |

### 4.13 에러 처리

| 항목 | 스펙 |
|------|------|
| 타임아웃 | 90초 |
| 재시도 | 마지막 2개 메시지 삭제 + 재전송 |
| Mutex | 동시 전송 방지 |
| 폴백 | Gemini 실패 → 에러 메시지 + retry 버튼 |

### 4.14 이벤트 전파

| 이벤트 | 대상 |
|--------|------|
| CATEGORY_UPDATED | Home, History 탭 새로고침 |
| TRANSACTION_ADDED | Home, History 탭 새로고침 |

### 4.15 [구현 상세] 3-Step 파이프라인 흐름

```
Step 1 (쿼리 분석):
  입력: 사용자 메시지 + rollingSummary + 최근 6개 메시지 + 시스템 프롬프트
  모델: Gemini 2.5-pro (temperature=0.3)
  출력: JSON { queries: [...], actions: [...], clarification: "..." }

  clarification 있으면 → 질문을 AI 메시지로 저장, Step 2/3 건너뜀

Step 2 (데이터 실행):
  queries 배열 순회:
    → DateUtils로 기간 파싱
    → Room DAO 쿼리 실행 (18 타입)
    → 결과를 contextBuilder에 누적

  actions 배열 순회:
    → 유효성 검증 (expenseId 존재 여부 등)
    → Repository CRUD 실행 (13 타입)
    → 결과를 contextBuilder에 누적
    → CATEGORY_UPDATED / TRANSACTION_ADDED 이벤트 전파

Step 3 (최종 답변):
  입력: 시스템 프롬프트 + Step 2 컨텍스트 + 사용자 질문
  모델: Gemini 2.5-pro (temperature=0.7)
  출력: 자연어 답변 → AI 메시지로 저장
```

#### Rolling Summary 관리

```
윈도우: 최근 6개 메시지 (3턴)
DB 메시지 > 6개 → 초과분을 Gemini Flash로 요약
요약 → ChatSessionEntity.currentSummary에 저장
다음 대화 시: summary + 최근 6개 → AI 컨텍스트로 전달
```

#### 날짜 파싱 전략

```
Step 1 프롬프트에 포함:
  현재 날짜, 현재 월, monthStartDay, 커스텀 기간 시작/끝
  → AI가 "이번 달", "지난 달" 등을 정확한 날짜로 변환
```

---

## 5. 설정

**파일**: `feature/settings/ui/SettingsScreen.kt` (685줄), `SettingsViewModel.kt` (1248줄), `BudgetBottomSheet.kt` (460줄)

### 5.1 화면 설정 섹션

| 항목 | 스펙 |
|------|------|
| 테마 | SYSTEM / LIGHT / DARK (ThemeModeDialog, 라디오 선택) |
| 저장 | SettingsDataStore.themeModeFlow |

### 5.2 기간/예산 섹션

#### 월 시작일

| 항목 | 스펙 |
|------|------|
| 범위 | 1~31 |
| 기본값 | 1 |
| 입력 | MonthStartDayDialog (숫자 입력 + "일" 접미사) |
| 영향 | Home/History/CategoryDetail 전체 기간 계산 |

#### 예산 설정 (BudgetBottomSheet)

| 항목 | 스펙 |
|------|------|
| 전체 예산 | 원 단위 직접 입력 |
| 카테고리 예산 | Category.parentEntries (UNCLASSIFIED 제외) |
| 입력 모드 | 금액 모드 / % 모드 토글 (전체 예산 > 0 시만) |
| 금액 모드 | 직접 원 입력 + 자동 % 표시 |
| % 모드 | % 입력 + 자동 원 계산 |
| 모드 전환 | 기존 값 자동 변환 |
| 초기화 | 카테고리별만 리셋 (전체 예산 유지) |
| 저장 | BudgetEntity (yearMonth + category 복합키) |
| 시트 높이 | 화면높이 - 300dp |
| 하단 | 고정 저장 버튼 |

### 5.3 AI 섹션

| 항목 | 스펙 |
|------|------|
| 미분류 정리 | 버튼 (hasApiKey && unclassified > 0 && !classifying) |
| 진행 표시 | 인라인 프로그레스 + "단계명 (X/Y)" |
| 백그라운드 감지 | HomeViewModel 분류 상태 모니터링 |

### 5.4 데이터 관리 섹션

#### SMS 제외 키워드

| 항목 | 스펙 |
|------|------|
| 표시 | "N개 사용자 키워드 / N개 기본 키워드" |
| 다이얼로그 | 입력필드 + "추가" 버튼 + 키워드 목록 (LazyColumn, max 350dp) |
| 사용자 키워드 | Primary 색상 헤더 + 삭제(X) 버튼 |
| 기본 키워드 | 회색 헤더 + 삭제 불가 |
| 출처 | "default" (삭제불가) / "user" (설정UI) / "chat" (AI 채팅) |
| 정규화 | lowercase + trim |

#### 데이터 내보내기 (ExportDialog)

| 항목 | 스펙 |
|------|------|
| 형식 | JSON (전체 필드) / CSV (간소화) |
| 데이터 유형 | 지출 포함/제외, 수입 포함/제외 |
| 카드 필터 | 멀티 선택 |
| 카테고리 필터 | 멀티 선택 (최대 10개 표시) |
| 저장 방식 | "다운로드" (로컬) / "구글 드라이브에 저장" |

#### Google Drive 연동

| 항목 | 스펙 |
|------|------|
| 로그인 | Interactive OAuth / Silent Sign-in |
| 파일 목록 | 이름 + 날짜 + 복원/삭제 아이콘 |
| 복원 | 다운로드 → JSON 파싱 → 기존 데이터와 병합 |
| 로그아웃 | 드라이브 서비스 초기화 |

#### 로컬 복원

| 항목 | 스펙 |
|------|------|
| 방식 | 파일 피커 → URI 선택 → 확인 다이얼로그 → 병합 |
| 경고 | "기존 데이터와 합쳐집니다" |

#### 중복 삭제

| 항목 | 스펙 |
|------|------|
| 동작 | ExpenseRepository.deleteDuplicates() |
| 결과 | 삭제 건수 스낵바 |

#### 전체 데이터 삭제

| 항목 | 스펙 |
|------|------|
| 확인 | 파괴적 경고 다이얼로그 필수 |
| 삭제 대상 | 지출, 수입, 채팅, 예산, 카테고리 매핑, 카드 |
| 보존 | SmsPatternEntity, StoreEmbeddingEntity (학습 벡터) |
| 리셋 | monthlyIncome=0, monthStartDay=1, syncedMonths 초기화 |
| 이벤트 | ALL_DATA_DELETED 전파 |

### 5.5 앱 정보 섹션

| 항목 | 스펙 |
|------|------|
| 버전 | BuildConfig.VERSION_NAME |
| 앱 정보 | 설명, 개발자, 연락처, 라이선스 |
| 개인정보 정책 | 스크롤 가능 다이얼로그 (7개 섹션) |

---

## 6. 카테고리 상세

**파일**: `feature/categorydetail/ui/CategoryDetailActivity.kt`, `CategoryDetailScreen.kt`, `CategoryDetailViewModel.kt`

### 6.1 진입

| 항목 | 스펙 |
|------|------|
| 방식 | Intent (Home 화면 카테고리 행 클릭) |
| 파라미터 | EXTRA_CATEGORY (displayName), EXTRA_YEAR, EXTRA_MONTH |

### 6.2 레이아웃

| 항목 | 스펙 |
|------|------|
| TopAppBar | ← 뒤로 + 카테고리 이모지 + 이름 |
| 월 네비게이션 | 좌우 화살표 + 년월 + 기간 레이블 |
| 미래 월 | 비활성화 |

### 6.3 누적 추이 차트

| 항목 | 스펙 |
|------|------|
| 필터 | Category.displayNamesIncludingSub (소 카테고리 포함) |
| 메인 라인 | 해당 카테고리 이번 달 일별 누적 |
| 토글 라인 | 전월, 3개월 평균, 6개월 평균, 카테고리 예산 |
| 제외 키워드 | SmsExclusionRepository로 동적 필터링 |

### 6.4 거래 목록

| 항목 | 스펙 |
|------|------|
| 그룹핑 | 날짜별 역순 |
| 헤더 | 날짜 + 요일 + 일별 소계 |
| 카드 | TransactionCardCompose |
| 클릭 | ExpenseDetailDialog |

### 6.5 CRUD

| 동작 | 스펙 |
|------|------|
| 삭제 | deleteExpense() |
| 카테고리 변경 | updateExpenseCategory() (같은 가게 전체) |
| 메모 수정 | updateExpenseMemo() |

### 6.6 데이터 관리

| 항목 | 스펙 |
|------|------|
| 페이지 캐시 | 현재 ± 2개월 |
| Flow 데이터 | 이번 달 지출 실시간 갱신 |
| 1회성 데이터 | 전월/3/6개월 평균 (고정), 예산, daysInMonth |
| 이벤트 | DataRefreshEvent 구독 → 전역 새로고침 |

### 6.7 페이지 데이터 (CategoryDetailPageData)

| 필드 | 설명 |
|------|------|
| isLoading | 로딩 상태 |
| monthlyExpense | 월간 총 지출 |
| dailyCumulativeExpenses | 일별 누적 (차트) |
| lastMonthDailyCumulative | 전월 누적 |
| avgThreeMonthDailyCumulative | 3개월 평균 |
| avgSixMonthDailyCumulative | 6개월 평균 |
| categoryBudget | 카테고리 예산 |
| daysInMonth, todayDayIndex | 차트 X축 계산 |
| transactionItems | 거래 목록 |
| periodLabel | 기간 레이블 |

### 6.8 [구현 상세] 데이터 로드 & 차트 차이점

#### Home 차트와의 차이점

| 항목 | Home | CategoryDetail |
|------|------|----------------|
| 필터 | 전체 지출 | `Category.displayNamesIncludingSub` (소카테고리 포함) |
| 비교 텍스트 | "₩X 더/덜 썼어요" | 없음 (빈 문자열) |
| 예산 라인 | 전체 월 예산 | 해당 카테고리 예산만 |
| Mapper | HomeSpendingTrendInfo | CategorySpendingTrendInfo |
| Y축 | 전체 지출 기준 | 카테고리 지출 기준 (더 작은 scale) |

#### 카테고리 필터 로직

```
val categoryNames = Category.displayNamesIncludingSub(categoryDisplayName)
→ 부모 카테고리 + 모든 하위 카테고리 displayName 리스트

예) "식비" → ["식비", "외식", "카페", "배달", "간식"]
DB 쿼리: WHERE category IN (categoryNames) AND dateTime BETWEEN start AND end
```

#### 차트 공유 구조

```
SpendingTrendInfo (interface)
  ├─ HomeSpendingTrendInfo (Home 전용 Mapper, @Composable from() 팩토리)
  └─ CategorySpendingTrendInfo (CategoryDetail 전용 Mapper, @Composable from() 팩토리)
      ↓
  CumulativeTrendSection → VicoCumulativeChart
  (Y축 고정, 토글 애니메이션, 라벨 모두 동일 공유)
```

---

## 7. 공통 컴포넌트

**경로**: `core/ui/component/`

### 7.1 TransactionCardCompose

| 항목 | 스펙 |
|------|------|
| 좌측 | 카테고리 아이콘 (원형 배경) 또는 수입 이모지 (💰) |
| 중앙 | 가게명(Bold) + 카테고리 태그 칩 + 시간/카드명(소, 회색) |
| 우측 | 금액 (지출=빨강, 수입=초록, Bold) |
| 스타일 | 12dp radius, 1dp 테두리, 가벼운 elevation |
| Interface | TransactionCardInfo (title, subtitle, amount, isIncome, category, etc.) |

### 7.2 TransactionGroupHeaderCompose

| 항목 | 스펙 |
|------|------|
| 좌측 | 제목 (날짜/가게명 등) |
| 우측 | +수입(초록) -지출(빨강) (0이면 숨김) |

### 7.3 ExpenseDetailDialog

| 항목 | 스펙 |
|------|------|
| 아이콘 | 카테고리 아이콘 (원형) |
| 제목 | 가게명 (중앙) |
| 필드 | 금액(읽기전용), 카테고리(편집), 카드명(읽기전용), 시간(읽기전용), 메모(편집), 원문SMS(읽기전용) |
| 삭제 | 빨강 버튼 + 확인 다이얼로그 |
| 카테고리 | CategoryPickerDialog |
| 메모 | AlertDialog + OutlinedTextField |

### 7.4 CumulativeTrendSection (공유 차트)

| 항목 | 스펙 |
|------|------|
| 라이브러리 | Vico |
| 입력 | SpendingTrendInfo interface |
| 라인 | Primary + N개 토글 가능 라인 |
| 범례 | 라인별 색상 + 라벨 |
| 사용처 | Home, CategoryDetail |

### 7.5 SegmentedTabRowCompose

| 항목 | 스펙 |
|------|------|
| 스타일 | 둥근 버튼형 탭 |
| 아이콘 | 지원 |
| Interface | SegmentedTabRowInfo |

### 7.6 CategoryIcon

| 항목 | 스펙 |
|------|------|
| 형태 | 이모지 + 원형 배경 |
| 매핑 | Category enum → 이모지 |

### 7.7 FullSyncCtaSection

| 항목 | 스펙 |
|------|------|
| 위치 | History 화면 전용 (Home에서 제거됨) |
| 내용 | 무료 잔여 시 "X월 데이터 가져오기" / 소진 후 "광고 보고 X월 데이터 가져오기" |
| 동작 | 무료 잔여(N회) → 바로 동기화 / 소진 후 → 광고 시청 → 동기화 |

### 7.8 SettingsItemCompose / SettingsSectionCompose

| 항목 | 스펙 |
|------|------|
| 행 구성 | 아이콘 + 제목/부제 + 화살표 |
| 높이 | 최소 56dp (Material 3) |
| 파괴적 | 빨강 아이콘/텍스트 (isDestructive) |

---

## 8. 글로벌 시스템

### 8.1 디자인 시스템

| 구성 | 파일 | 내용 |
|------|------|------|
| Color | Color.kt | Navy Primary, Gray Neutral, Red Expense, Blue/Green Income, Dark theme (Green+Orange) |
| Typography | Type.kt | 9-tier (SuitVariable 폰트), display/headline/title/body/label |
| Spacing | Dimens.kt | 4/8/12/16/20/24/32dp, radius 8/12/16dp, elevation 0/1/2dp |

### 8.2 데이터 새로고침 이벤트

| 이벤트 | 트리거 | 구독자 |
|--------|--------|--------|
| CATEGORY_UPDATED | 카테고리 변경, SMS 제외 키워드 변경 | Home, History |
| TRANSACTION_ADDED | 지출/수입 추가 | Home, History |
| OWNED_CARD_UPDATED | 카드 소유 변경 | Home, History |
| ALL_DATA_DELETED | 전체 삭제 | Home, History, Chat |

### 8.3 앱 전역 스낵바

| 항목 | 스펙 |
|------|------|
| 구현 | AppSnackbarBus (MutableSharedFlow.tryEmit) |
| 스레드 | thread-safe, 어디서든 호출 가능 |
| 자동 해제 | 3초 |

### 8.4 SMS 파싱 파이프라인

| 단계 | 설명 |
|------|------|
| SmsPreFilter | 100자 이상 + 제외 키워드 필터 |
| SmsIncomeFilter | 결제/수입/SKIP 3분류 (46개 키워드) |
| SmsPipeline Step 1 | SmsTemplateEngine (템플릿 + Embedding 배치) |
| SmsPipeline Step 2 | SmsPatternMatcher (벡터 코사인 유사도 ≥ 0.92) |
| SmsPipeline Step 3 | SmsGroupClassifier (3레벨 그룹핑 → LLM 배치) |

### 8.5 카테고리 분류 (4-Tier)

| Tier | 방식 | 정확도 | 비용 |
|------|------|--------|------|
| 1 | Room DB 정확 매칭 | 100% | $0 |
| 1.5a | 벡터 유사도 (단일) | 92%+ | $0 |
| 1.5b | 벡터 그룹 투표 | 88%+ | $0 |
| 2 | 로컬 키워드 매칭 | 70%+ | $0 |
| 3 | Gemini 배치 분류 | 95%+ | $0.001/건 |

### 8.6 Room DB 스키마 (v6, 10 entities)

| Entity | PK | 주요 필드 |
|--------|----|----------|
| ExpenseEntity | auto | amount, cardName, storeName, category, dateTime, memo, smsId(UNIQUE) |
| IncomeEntity | auto | amount, source, type, dateTime, memo, smsId(UNIQUE) |
| BudgetEntity | yearMonth+category | monthlyLimit |
| ChatEntity | auto | sessionId(FK), message, isUser, timestamp |
| ChatSessionEntity | auto | title, currentSummary, createdAt, updatedAt |
| CategoryMappingEntity | storeName | category, confidence |
| SmsPatternEntity | embedding | amountRegex, storeRegex, cardRegex, senderAddress |
| StoreEmbeddingEntity | storeName | embedding(768), category, confidence |
| OwnedCardEntity | cardName | isOwned, seenCount, source |
| SmsExclusionKeywordEntity | keyword | source, createdAt |

### 8.7 보상 광고 시스템

| 항목 | 스펙 |
|------|------|
| 전체 동기화 | 과거 월 per-month: 처음 N회 무료 (RTDB `free_sync_count`) → 이후 광고 |
| AI 채팅 | remaining=0 시 광고 → N회 무료 |
| 실시간 수신 | 항상 무료 (BroadcastReceiver) |
| 프리로드 | 자동 |
| 실패 처리 | 사용자 친화 (보상 적용) |

### 8.8 Firebase 연동

| 서비스 | 용도 |
|--------|------|
| RTDB | 원격 SMS 규칙, API 키 풀링, 모델 버전, 최소 버전 |
| Analytics | 화면 PV + 클릭 이벤트 (7개) |
| Crashlytics | Release 빌드 크래시 모니터링 |
| 강제 업데이트 | minVersionCode 비교 → AlertDialog |

---

## 변경 이력

| 날짜 | 변경 내용 |
|------|----------|
| 2026-02-24 | 최초 작성 (코드 기반 전수 조사) + 구현 상세 추가 + 리뷰 기반 오기재 6건 수정 (토글 기본값, 쿼리/액션 개수, 문자열 3건) |
