# MoneyTalk UI/UX 리디자인 계획서

> 작성일: 2026-02-24
> 컨셉: **Smart Ledger — Data First, Quiet AI, Clean Finance**

---

## 1. 현재 상태 진단

### GPT/Gemini/Claude 공통 진단

| 문제 | 현재 상태 | 영향 |
|------|----------|------|
| **정보 위계 부재** | MonthlyOverview, 차트, 카테고리, 오늘 지출이 동일 무게감 | 사용자가 어디를 봐야 할지 모름 |
| **숫자 강조 부족** | 금액이 bodyLarge(16sp) + Medium 수준 | 금융 앱의 핵심인 "금액"이 눈에 안 들어옴 |
| **여백 불일치** | 12dp/16dp/24dp 혼용, 카드 내부 패딩 불규칙 | 정돈되지 않은 느낌 |
| **차트 감성 부족** | 기능적 Canvas 차트, 축/눈금선 과다 | 개발자 대시보드 느낌 |
| **색상 과다** | Primary(Green)+Secondary(Blue)+Tertiary(Orange)+Error(Red)+Purple | 시선 분산, 신뢰감 저하 |
| **카드 스타일 혼재** | 12dp/8dp radius, 일부 border 있고 없고, elevation 불일치 | 통일감 없음 |

### Claude 추가 진단 (코드 분석 기반)

| 문제 | 코드 근거 |
|------|----------|
| **Typography 미정의** | Type.kt에 bodyLarge 1개만 정의, 나머지 Material 기본값 사용 |
| **MonthlyOverview가 Card가 아님** | 단순 Column — 배경 없이 텍스트만 나열 |
| **TodayExpense/MonthComparison 카드** | `surfaceVariant.copy(alpha=0.5f)` → 반투명으로 존재감 약함 |
| **AiInsight 카드** | `primaryContainer.copy(alpha=0.3f)` → 너무 연해서 안 보임 |
| **도넛차트 200dp + 36dp 스트로크** | 화면 절반 차지, 모바일 비율로 과대 |

---

## 2. 디자인 시스템 정의

### 2-1. 컬러 팔레트 (축소: 4색 체계)

**현재 → 개선 방향**: Green/Blue/Orange/Red/Purple → **Navy(Primary) + Gray + Red + Blue**

```
[Primary — Navy/Dark Blue 계열 (신뢰감)]
primary:         #1B2838  (Dark Navy — 주요 강조, 앱바, CTA 버튼)
primaryVariant:  #2C3E50  (Medium Navy — 카드 헤더, 섹션 제목)
primaryLight:    #E8EDF2  (Light Navy — 카드 배경 틴트, 선택 상태)

[Semantic — 수입/지출 (변경 없이 유지)]
expense:         #EF4444  (Red — 지출)
income:          #137FEC  (Blue — 수입, Light)
incomeDark:      #3AC977  (Green — 수입, Dark)

[Neutral — Gray Scale]
gray900:         #111827  (Primary text)
gray600:         #6B7280  (Secondary text)
gray400:         #9CA3AF  (Tertiary text, 힌트)
gray200:         #E5E7EB  (Divider, Border)
gray100:         #F3F4F6  (카드 배경)
gray50:          #F9FAFB  (앱 배경)

[Surface]
surface:         #FFFFFF  (카드)
surfaceVariant:  #F3F4F6  (보조 카드, 칩 배경)
```

**결정 근거**:
- Green Primary → Navy 변경: 금융 앱(토스, 뱅크샐러드) 트렌드. Green은 "환경/건강" 느낌, Navy는 "금융/신뢰" 느낌
- 색상 수 축소: 5색 → 4색. 차트 보조색은 Gray 톤으로 대체

### 2-2. 타이포그래피 (숫자 강조 중심)

```
[Display — 히어로 금액]
displayAmount:   32sp, Bold (700)     → 이번 달 총 지출
displaySub:      14sp, Medium (500)   → "이번 달 지출" 라벨

[Title — 카드 제목/섹션]
titleLarge:      20sp, SemiBold (600) → 섹션 제목 ("카테고리별 지출")
titleMedium:     16sp, SemiBold (600) → 카드 내 금액 ("오늘 12,500원")

[Body — 본문]
bodyLarge:       16sp, Normal (400)   → 가게명, 설명
bodyMedium:      14sp, Normal (400)   → 보조 설명, 날짜
bodySmall:       12sp, Normal (400)   → 시간, 카드사

[Label — 보조]
labelMedium:     12sp, Medium (500)   → 칩, 뱃지, 범례
labelSmall:      11sp, Medium (500)   → 미니 라벨

[Number — 금액 전용]
numberLarge:     28sp, Bold (700)     → 카드 내 메인 금액
numberMedium:    18sp, SemiBold (600) → 리스트 금액
numberSmall:     14sp, Medium (500)   → 차트 라벨
```

**결정 근거**: 현재 금액이 bodyLarge(16sp, Medium)로 표시되어 가게명과 동일 무게감. 금액 전용 스케일 필요.

### 2-3. Spacing 규칙 (8dp 그리드)

```
[기본 단위: 4dp]
xs:   4dp   — 인라인 간격 (아이콘-텍스트, 제목-부제)
sm:   8dp   — 요소 내부 간격
md:  12dp   — 카드 내부 패딩 (compact)
lg:  16dp   — 카드 내부 패딩 (standard), 화면 좌우 패딩
xl:  20dp   — 섹션 간 간격 (LazyColumn item spacing)
xxl: 24dp   — 히어로 영역 상하 패딩

[카드]
카드 내부 패딩:        16dp (uniform)
카드 간 간격:          16dp (현재 12dp → 16dp로 통일)
카드 corner radius:    16dp (현재 12dp → 16dp로 변경, 더 부드러운 느낌)
카드 elevation:        0dp  (현재 1dp → 그림자 제거, border로 구분)
카드 border:           1dp, gray200 (#E5E7EB)
```

**결정 근거**:
- 12dp radius → 16dp: 토스/뱅크샐러드 수준의 부드러운 카드 스타일
- elevation 0dp: 플랫 디자인 트렌드. 그림자 대신 border/배경색으로 구분
- 카드 간 간격 16dp: 현재 12dp는 빡빡함. 여유 있는 레이아웃

---

## 3. 홈 화면 구조 재설계

### 현재 구조 (7블록, 동일 위계)
```
[MonthlyOverview]    — 월 네비 + 총 지출 (Card 아님)
[SpendingTrend]      — 누적 추이 차트
[CategoryExpense]    — 도넛차트 + 카테고리 리스트
[AiInsight]          — AI 분석 (연한 카드)
[TodayAndComparison] — 오늘지출 + 전월비교 (2열)
[TodayTransactions]  — 오늘 거래 리스트
[EmptyState/CTA]     — 빈 상태
```

### 재설계 구조 (4블록, 명확한 위계)

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[BLOCK 1: Hero Summary]              ← 시선 집중 #1
 최상단, 카드 없이 넓은 여백.

 2월                          ← 24dp top
 ₩1,234,567                  ← 32sp Bold (displayAmount)
 이번 달 지출                  ← 14sp gray600

 ┌─ 수입 ₩2,500,000 ─┬─ 지난달 대비 -12% ─┐
 └─────────────────────┴────────────────────┘
   ↑ 인라인 뱃지 (한 줄, 간결하게)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[BLOCK 2: Quick Stats]               ← 시선 집중 #2
 2열 카드 (오늘 지출 + 전월 대비)

 ┌──────────────┐  ┌──────────────┐
 │ 오늘          │  │ vs 지난달     │
 │ ₩45,200      │  │ -₩123,000   │
 │ 3건          │  │ 12% 절약     │
 └──────────────┘  └──────────────┘
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[BLOCK 3: Spending Trend]            ← 분석 영역
 카드 안에 차트 포함.
 축 최소화, 오늘 마커 강조.

 ┌─ 누적 지출 추이 ────────────────┐
 │                                │
 │   ╭─────────╮ ← 실선 (이번달)  │
 │  ╱           ╲                 │
 │ ╱   - - - -   ╲ ← 점선 (지난달)│
 │╱               ╲               │
 │ 1    7    14    21    28       │
 │                                │
 │ ● 이번달  ○ 지난달  ○ 3개월평균│
 └────────────────────────────────┘
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[BLOCK 4: Category + AI Insight]     ← 상세 영역
 도넛차트 → 수평 바 차트로 변경.
 AI 인사이트를 카테고리 요약 하단에 자연스럽게 배치.

 ┌─ 카테고리별 지출 ───────────────┐
 │                                │
 │ 🍔 식비       ████████░░  45% │
 │ ☕ 카페       ████░░░░░░  22% │
 │ 🚗 교통       ███░░░░░░░  15% │
 │ 🛒 쇼핑       ██░░░░░░░░  10% │
 │ ···           ░░░░░░░░░░   8% │
 │                                │
 │ ─────────────────────────────  │
 │ 💡 식비가 지난달 대비 23% 증가  │
 │    카페 지출을 줄이면 월 5만원  │
 │    절약할 수 있어요             │
 └────────────────────────────────┘
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[BLOCK 5: Recent Transactions]       ← 타임라인
 오늘 거래 리스트 (기존과 유사하나 스타일 개선)
```

### 변경 전후 비교

| 항목 | Before | After |
|------|--------|-------|
| 블록 수 | 7개 (동일 위계) | 5개 (명확한 위계) |
| 히어로 금액 | headlineLarge (Material 기본) | 32sp Bold (커스텀) |
| 도넛 차트 | 200dp 원형 (화면 절반) | 수평 바 차트 (컴팩트) |
| AI 인사이트 | 별도 카드 (약한 배경) | 카테고리 카드 하단 통합 |
| Quick Stats 위치 | 5번째 (맨 아래) | 2번째 (히어로 직후) |
| 카드 radius | 12dp | 16dp |
| 카드 elevation | 1dp | 0dp (border만) |

---

## 4. 차트 리디자인

### 4-1. 누적 추이 차트 (CumulativeChartCompose)

**현재 문제**:
- Y축 5개 가이드라인 + 라벨 → 복잡
- X축 7개 라벨 → 과다
- 실선/점선 구분만으로 차트 목적 전달 부족

**개선 방향**:
```
[축 최소화]
- Y축: 가이드라인 3개 (0, 중간, 최대). 라벨은 최대값만 표시
- X축: 시작일, 오늘, 말일 3개만 표시
- 배경 가이드라인: alpha 0.05f (거의 안 보이게)

[강조]
- 이번달 라인: primary 컬러, 3dp stroke, 그라데이션 fill (아래쪽)
- 비교 라인: gray400, 1.5dp stroke, 점선
- 오늘 마커: 12dp 원 + 금액 라벨 (차트 위)

[카드화]
- 차트를 Card(16dp radius) 안에 배치
- 카드 상단: 제목 + 토글 범례
- 카드 하단: 차트
```

### 4-2. 카테고리 차트 (DonutChart → HorizontalBar)

**현재 문제**:
- 도넛차트 200dp: 화면 절반, 정보 밀도 낮음
- 범례가 차트 아래 별도 표시 → 시선 이동

**개선 방향**:
```
[도넛 → 수평 바]
- 각 카테고리를 한 줄로 표현
- 이모지 + 이름 + 바 + 금액 + 퍼센트
- 바 높이: 8dp, radius 4dp
- 바 색상: primary (1위만), gray300 (나머지)
- 상위 5개만 표시, "더보기" 접기/펼치기

[장점]
- 도넛 대비 50% 이상 공간 절약
- 금액과 카테고리가 한 줄에 → 비교 용이
- 1위 카테고리만 색상 강조 → "어디서 많이 썼는지" 즉시 파악
```

---

## 5. 구현 계획 (Phase별)

### Phase 1: 디자인 시스템 기반 (1-2일)
```
[변경 대상]
├── Color.kt          — 컬러 팔레트 재정의 (Navy 기반)
├── Type.kt           — Typography 확장 (숫자 전용 스케일)
├── Theme.kt          — 확장 컬러 업데이트
└── Dimens.kt (신규)  — Spacing/Radius 토큰 정의
```

### Phase 2: 홈 화면 구조 개편 (2-3일)
```
[변경 대상]
├── HomeScreen.kt              — LazyColumn 순서 재배치, 블록 간격 16dp
├── MonthlyOverviewSection     — Hero 스타일 (32sp 금액, 인라인 뱃지)
├── TodayAndComparisonSection  — Quick Stats로 위치 상승 (2번째)
├── CategoryExpenseSection     — 도넛 → 수평 바 + AI 인사이트 통합
└── AiInsightCard              — 독립 카드 → CategorySection 하단 통합
```

### Phase 3: 차트 개선 (1-2일)
```
[변경 대상]
├── CumulativeChartCompose.kt  — 축 최소화, 그라데이션 fill, 오늘 마커 강조
├── CumulativeTrendSection.kt  — 카드 래핑, 범례 인라인화
├── DonutChartCompose.kt       — 수평 바 차트로 교체 (또는 신규 파일)
└── HomeSpendingTrendInfo.kt   — 차트 컬러 Navy 기반으로 조정
```

### Phase 4: 공통 컴포넌트 스타일 통일 (1일)
```
[변경 대상]
├── TransactionCardCompose.kt       — radius 16dp, elevation 0dp, border만
├── TransactionGroupHeaderCompose.kt — 간격/타이포 조정
├── SettingsSectionCompose.kt       — 동일 카드 스타일 적용
└── FullSyncCtaSection.kt           — CTA 버튼 스타일 통일
```

---

## 6. 리스크 분석

| # | 리스크 | 발생 조건 | 대응 |
|---|--------|----------|------|
| 1 | Color 변경으로 다크 테마 깨짐 | Navy Primary가 다크 배경에서 안 보임 | 다크 테마용 별도 Navy 라이트 톤 정의 |
| 2 | 도넛→바 전환 시 기존 로직 손실 | DonutSlice 데이터 구조 변경 | 기존 DonutChartCompose 유지, HorizontalBarChart 신규 생성 |
| 3 | 홈 블록 순서 변경 시 스크롤 UX | Quick Stats가 위로 올라가면 차트가 fold 아래로 | 히어로+QuickStats가 초기 뷰포트에 들어오도록 높이 계산 |
| 4 | Typography 변경 범위 넓음 | 전체 앱에 Material 기본 타이포 사용 중 | Phase 1에서 토큰만 정의, 적용은 Phase별 점진적 |
| 5 | 수평 바에서 카테고리 10개+ 표시 | 카테고리가 많으면 리스트가 길어짐 | 상위 5개만 표시 + "더보기" 접기/펼치기 |

---

## 7. 합의 필요 사항

1. **Primary 컬러 변경 (Green → Navy)**: 앱 전체 분위기가 바뀜. 동의하는가?
2. **도넛차트 → 수평 바 차트**: 기존 도넛차트를 완전히 대체할 것인가, 아니면 토글로 전환 가능하게 할 것인가?
3. **AI 인사이트 통합**: 독립 카드에서 카테고리 하단으로 이동. AI 존재감이 줄어듦. 괜찮은가?
4. **Phase 순서**: Phase 1(디자인시스템)부터 순서대로 할 것인가, 홈 화면부터 눈에 보이는 변경을 할 것인가?
5. **다크 테마**: 동시에 맞출 것인가, 라이트 먼저 완성 후 다크 적용할 것인가?
