# 머니톡 (MoneyTalk) - 개발 로그

> 개발 과정과 변경 사항을 기록하는 문서입니다.

---

## 2026-02-05 - AI 자연어 데이터 조회 및 가게명 별칭 시스템

### 작업 내용

#### 1. Gemini 기반 자연어 데이터 조회 시스템
- 2단계 쿼리 흐름 구현:
  1. Gemini가 사용자 질문 분석 → JSON 쿼리/액션 반환
  2. 앱이 로컬 DB에서 쿼리 실행
  3. Gemini가 결과 기반 답변 생성

- 지원 쿼리 타입 (10종):
  - `total_expense`, `total_income`, `expense_by_category`
  - `expense_list`, `expense_by_store`, `daily_totals`
  - `monthly_totals`, `monthly_income`, `uncategorized_list`
  - `category_ratio`

- 지원 액션 타입 (3종):
  - `update_category` - 특정 ID 카테고리 변경
  - `update_category_by_store` - 가게명 기준 일괄 변경
  - `update_category_by_keyword` - 키워드 포함 가게명 일괄 변경

#### 2. System Instruction 최적화
- 두 개의 전용 GenerativeModel 사용:
  - Query Analyzer (temperature: 0.3) - 정확한 JSON 반환
  - Financial Advisor (temperature: 0.7) - 친근한 재무 조언
- System Instruction에 스키마/규칙 포함으로 코드 정리

#### 3. 가게명 별칭 시스템 (StoreAliasManager)
- 영문/한글 가게명 자동 매핑
- 50개 이상 브랜드 기본 등록:
  - 쿠팡: coupang, 쿠페이, 쿠팡이츠, 로켓배송
  - 배달의민족: 배민, baemin
  - 스타벅스: starbucks, 스벅, 별다방
  - 등등
- 검색/카테고리 변경 시 모든 별칭 자동 적용

#### 생성/수정된 파일
```
app/src/main/java/com/sanha/moneytalk/
├── core/util/
│   ├── DataQueryParser.kt          # 쿼리/액션 모델 및 파서
│   └── StoreAliasManager.kt        # 가게명 별칭 매핑 (신규)
├── core/database/dao/
│   └── ExpenseDao.kt               # 가게명 검색, 카테고리 변경 쿼리 추가
├── feature/chat/
│   ├── data/GeminiRepository.kt    # System Instruction, 2단계 쿼리
│   └── ui/ChatViewModel.kt         # 쿼리/액션 실행 로직
└── feature/home/data/
    └── ExpenseRepository.kt        # 가게명 관련 메서드 추가
```

---

## 2026-02-05 - Gemini API 마이그레이션

### 작업 내용

#### Claude API → Gemini API 전환
- Retrofit 기반 Claude API 제거
- Google AI SDK (`com.google.ai.client.generativeai`) 추가
- `GeminiRepository` 신규 작성
- API 키 저장 방식 유지 (DataStore)

#### 변경 사유
- Claude API 400 에러 이슈
- Gemini의 무료 티어 활용

---

## 2026-02-05 - 결제 주기 기반 달력 뷰

### 작업 내용

#### 사용자 정의 결제일 지원
- 설정에서 결제일 선택 (1~28일)
- 결제일 기준으로 월 계산
- 예: 결제일 15일 → 1/15 ~ 2/14를 "2월"로 표시

#### 수정된 파일
```
├── core/datastore/SettingsDataStore.kt   # monthStartDay 저장
├── core/util/DateUtils.kt                # 결제 주기 계산 함수
└── feature/home/ui/HomeScreen.kt         # 월 선택기 UI
```

---

## 2026-02-05 - 내역 화면 리디자인 (뱅크샐러드 스타일)

### 작업 내용

#### HistoryScreen 전면 리디자인
- 날짜별 그룹핑 (일별 섹션 헤더)
- 카드/카테고리 필터 칩
- 지출 항목 클릭 시 상세 다이얼로그
- 원본 SMS 확인 기능

---

## 2026-02-05 - 데이터 백업/복원

### 작업 내용

#### Export/Import 기능
- JSON 형식으로 전체 지출 내역 내보내기
- JSON 파일에서 데이터 가져오기
- 설정 화면에서 접근

---

## 2026-02-05 - SMS 로컬 파싱 전환

### 작업 내용

#### API 호출 없이 로컬 정규식 파싱
- 카드사별 SMS 패턴 정의
- 금액, 가게명, 날짜/시간 추출
- 카테고리 자동 분류 (키워드 기반)

#### 지원 카드사
KB국민, 신한, 삼성, 현대, 롯데, 우리, 하나, NH농협, BC카드

---

## 2026-02-05 - 프로젝트 구조 리팩토링

### 작업 내용

#### presentation/ → feature/ 기반 모듈화
기존:
```
presentation/
├── home/
├── history/
├── chat/
└── settings/
```

변경:
```
feature/
├── home/
│   ├── ui/
│   └── data/
├── history/
│   └── ui/
├── chat/
│   ├── ui/
│   └── data/
├── settings/
│   └── ui/
└── splash/
    └── ui/
```

---

## 2026-02-05 - 프로젝트 초기 설정

### 작업 내용

#### 1. 프로젝트 생성
- Android Studio에서 Empty Compose Activity로 생성
- Package: `com.sanha.moneytalk`
- Min SDK: 26 (Android 8.0)

#### 2. 의존성 추가
추가된 라이브러리:
- Room 2.6.1
- Hilt 2.50
- Navigation Compose 2.7.7
- Coroutines 1.7.3
- DataStore 1.0.0
- Google AI SDK (Gemini)

#### 3. Room Database 구현
- `ExpenseEntity` - 지출 내역
- `IncomeEntity` - 수입 내역
- `ChatEntity` - AI 대화 기록

#### 4. UI 화면 구현
- `SplashScreen` - 앱 로딩
- `HomeScreen` - 월간 현황
- `HistoryScreen` - 지출 내역
- `ChatScreen` - AI 상담
- `SettingsScreen` - 설정

#### 5. Navigation 설정
- Bottom Navigation Bar (홈, 내역, 상담, 설정)
- 스플래시 → 홈 자동 전환
- 뒤로가기 두 번 눌러 종료

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|-----------|
| 2026-02-05 | 0.1.0 | 프로젝트 초기 설정 |
| 2026-02-05 | 0.2.0 | SMS 로컬 파싱 전환 |
| 2026-02-05 | 0.3.0 | 내역 화면 리디자인 |
| 2026-02-05 | 0.4.0 | 데이터 백업/복원 |
| 2026-02-05 | 0.5.0 | 결제 주기 기반 달력 뷰 |
| 2026-02-05 | 0.6.0 | Claude → Gemini 마이그레이션 |
| 2026-02-05 | 0.7.0 | 자연어 데이터 조회 시스템 |
| 2026-02-05 | 0.8.0 | 가게명 별칭 시스템 |

---

*마지막 업데이트: 2026-02-05*
