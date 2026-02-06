# 머니톡 (MoneyTalk) - 개발 로그

> 개발 과정과 변경 사항을 기록하는 문서입니다.

---

## 2026-02-06 - TODO 항목 구현 (UI 기능 완성)

### 작업 내용

#### 1. StoreAliasManager DataStore 영구 저장
- `object` → `@Singleton class`로 리팩토링 (Hilt DI)
- 사용자 정의 별칭을 DataStore에 영구 저장/로드
- SettingsDataStore에 `saveCustomAliases()`, `getCustomAliases()` 추가
- 정적 호환 메서드 `normalizeStoreNameStatic()` 제공

#### 2. HistoryScreen 수입 추적 연동
- IncomeRepository를 HistoryViewModel에 주입
- 월별 수입 로드 및 UI 표시

#### 3. HistoryScreen 검색 기능
- AnimatedVisibility 기반 검색 바
- 가게명, 카테고리, 카드명 in-memory 필터링

#### 4. HistoryScreen 수동 지출 추가
- AddExpenseDialog composable 구현
- 금액, 가게명, 카테고리 칩, 카드명 입력
- HistoryViewModel에 `addExpense()` 메서드 추가

#### 5. SettingsScreen 카테고리별 예산 설정
- CategoryBudgetDialog composable 구현
- BudgetDao를 SettingsViewModel에 통합
- 카테고리별 예산 추가/삭제 UI
- 기존 BudgetEntity/BudgetDao 활용

#### 6. SettingsScreen 개인정보 처리방침
- PrivacyPolicyDialog composable 구현
- 스크롤 가능한 개인정보 처리방침 전문 표시

#### 7. 버그 수정
- `DateUtils.formatTimestamp()` → `DateUtils.formatDateTime()` 컴파일 에러 수정

#### 생성/수정된 파일
```
app/src/main/java/com/sanha/moneytalk/
├── core/util/
│   └── StoreAliasManager.kt        # object → @Singleton class, DataStore 연동
├── core/datastore/
│   └── SettingsDataStore.kt         # 사용자 정의 별칭 저장 메서드 추가
├── feature/history/ui/
│   ├── HistoryScreen.kt             # 검색 바, 수동 지출 추가 다이얼로그
│   └── HistoryViewModel.kt          # 수입 연동, 검색, 수동 추가 기능
├── feature/settings/ui/
│   ├── SettingsScreen.kt            # CategoryBudgetDialog, PrivacyPolicyDialog
│   └── SettingsViewModel.kt         # BudgetDao 통합, 예산 관리 메서드
├── feature/chat/ui/
│   └── ChatViewModel.kt             # StoreAliasManager DI 주입
└── res/values/
    ├── strings.xml                   # 새 문자열 리소스 추가
    └── values-en/strings.xml        # 영어 문자열 리소스 추가
```

---

## 2026-02-06 - 벡터 기반 지능형 파싱 시스템

### 작업 내용

#### 1. Vector-First 파싱 파이프라인
- SmartParserRepository: Vector → Regex → Gemini 3단계 파이프라인
- Google text-embedding-004 API를 이용한 SMS 벡터화
- 코사인 유사도 기반 로컬 패턴 매칭 (임계값: SMS 0.98, 가맹점 0.90)
- 자가 학습: 성공적 파싱 결과를 DB에 저장하여 향후 무비용 매칭

#### 2. Room DB 벡터 저장
- SmsPatternEntity: 학습된 SMS 패턴 + 벡터 (BLOB)
- MerchantVectorEntity: 가맹점 벡터 + 카테고리 매핑
- VectorConverters: FloatArray ↔ ByteArray TypeConverter
- DB v2 → v3 마이그레이션

#### 3. 유틸리티
- VectorUtils: 코사인 유사도, 유사 패턴 검색
- EmbeddingRepository: Google Embedding API + LRU 캐시 (200개)

#### 4. HomeViewModel 통합
- SmartParserRepository를 SMS 동기화에 통합
- 파싱 소스별 통계 로깅 (Vector/Regex/Gemini)

#### 생성/수정된 파일
```
app/src/main/java/com/sanha/moneytalk/
├── core/database/
│   ├── converter/VectorConverters.kt    # FloatArray ↔ ByteArray (신규)
│   ├── entity/
│   │   ├── SmsPatternEntity.kt          # SMS 패턴 벡터 엔티티 (신규)
│   │   └── MerchantVectorEntity.kt      # 가맹점 벡터 엔티티 (신규)
│   ├── dao/
│   │   ├── SmsPatternDao.kt             # SMS 패턴 DAO (신규)
│   │   └── MerchantVectorDao.kt         # 가맹점 벡터 DAO (신규)
│   └── AppDatabase.kt                   # v3 마이그레이션, 새 엔티티/DAO 등록
├── core/util/
│   ├── VectorUtils.kt                   # 코사인 유사도 유틸 (신규)
│   ├── EmbeddingRepository.kt           # 임베딩 API + 캐시 (신규)
│   └── SmartParserRepository.kt         # 지능형 파싱 파이프라인 (신규)
├── core/di/
│   └── DatabaseModule.kt                # 새 DAO 프로바이더 추가
└── feature/home/ui/
    └── HomeViewModel.kt                 # SmartParserRepository 통합
```

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
| 2026-02-06 | 0.9.0 | 벡터 기반 지능형 파싱 시스템 |
| 2026-02-06 | 0.10.0 | TODO 항목 구현 (UI 기능 완성) |

---

*마지막 업데이트: 2026-02-06*
