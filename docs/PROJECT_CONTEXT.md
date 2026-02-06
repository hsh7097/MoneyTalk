# 머니톡 (MoneyTalk) - 프로젝트 컨텍스트

> 이 문서는 Claude와 대화를 이어가기 위한 프로젝트 컨텍스트 파일입니다.
> 새 대화 시작 시 이 파일을 공유하면 이전 작업 내용을 이어갈 수 있습니다.

---

## 1. 프로젝트 개요

### 기본 정보
- **앱 이름**: 머니톡 (MoneyTalk)
- **슬로건**: "돈과 대화하다, AI와 함께"
- **패키지명**: `com.sanha.moneytalk`

### 컨셉
카드 결제 문자를 자동 수집하고, Gemini AI가 분석하여 맞춤 재무 상담을 제공하는 개인 재무 비서 앱

### 기술 스택
| 항목 | 기술 |
|------|------|
| 언어 | Kotlin |
| UI | Jetpack Compose + Material Design 3 |
| 로컬 DB | Room |
| DI | Hilt |
| 비동기 | Coroutines + Flow |
| AI | **Gemini API (Google)** |
| 아키텍처 | MVVM + Feature-based Modular Architecture |
| 설정 저장 | DataStore |

---

## 2. 핵심 기능

### 2.1 문자 수집 및 파싱 (Vector-First 파이프라인)
- `ContentResolver`로 기존 카드 문자 읽기
- **3단계 지능형 파싱**: Vector Match → Regex → Gemini Fallback
- Google text-embedding-004 기반 SMS 벡터화 + 코사인 유사도 매칭
- 자가 학습: 성공적 파싱 결과를 DB에 벡터 저장 → 향후 무비용 매칭
- 카드사 문자 자동 필터링 (KB, 신한, 삼성, 현대, 롯데, 우리, 하나, NH, BC 등)
- 중복 처리 방지 (smsId 기반)
- 증분 동기화 / 전체 동기화 선택 가능

### 2.2 AI 자연어 데이터 조회
- **2단계 쿼리 시스템**:
  1. Gemini가 사용자 질문 분석 → 필요한 DB 쿼리 JSON 반환
  2. 앱이 로컬 DB에서 데이터 조회
  3. Gemini가 조회된 데이터 기반으로 답변 생성
- 지원 쿼리 타입:
  - `total_expense` - 기간 내 총 지출
  - `expense_by_category` - 카테고리별 지출
  - `expense_by_store` - 특정 가게 지출
  - `category_ratio` - 수입 대비 비율 분석
  - `uncategorized_list` - 미분류 항목 조회
  - 등 10가지 쿼리 타입

### 2.3 AI 카테고리 관리
- 채팅으로 카테고리 일괄 변경 가능
- 예: "쿠팡은 쇼핑으로 분류해줘" → 자동 일괄 변경
- 지원 액션:
  - `update_category` - 특정 ID 카테고리 변경
  - `update_category_by_store` - 가게명 기준 일괄 변경
  - `update_category_by_keyword` - 키워드 포함 가게명 일괄 변경

### 2.4 가게명 별칭 시스템 (StoreAliasManager)
- 영문/한글 가게명 자동 매핑
- 예: `coupang` = `쿠팡` = `쿠페이` = `쿠팡이츠`
- 50개 이상 브랜드 기본 등록
- **사용자 정의 별칭 DataStore 영구 저장**

### 2.5 결제 주기 기반 달력 뷰
- 사용자 설정 결제일 기준 월 계산
- 예: 결제일 15일 → 1월 15일 ~ 2월 14일을 "2월"로 표시

### 2.6 데이터 백업/복원
- JSON 형식 내보내기/가져오기
- 외부 저장소 저장

---

## 3. 프로젝트 구조

```
app/src/main/java/com/sanha/moneytalk/
├── MoneyTalkApplication.kt              # Hilt Application
├── MainActivity.kt                       # 메인 액티비티 + Navigation + 권한
│
├── core/                                 # 공통 모듈
│   ├── database/
│   │   ├── AppDatabase.kt               # Room Database (v3)
│   │   ├── converter/
│   │   │   └── VectorConverters.kt      # FloatArray ↔ ByteArray
│   │   ├── dao/
│   │   │   ├── ExpenseDao.kt            # 지출 DAO
│   │   │   ├── IncomeDao.kt             # 수입 DAO
│   │   │   ├── BudgetDao.kt             # 예산 DAO
│   │   │   ├── ChatDao.kt               # 채팅 기록 DAO
│   │   │   ├── SmsPatternDao.kt         # SMS 패턴 벡터 DAO
│   │   │   └── MerchantVectorDao.kt     # 가맹점 벡터 DAO
│   │   └── entity/
│   │       ├── ExpenseEntity.kt         # 지출 엔티티
│   │       ├── IncomeEntity.kt          # 수입 엔티티
│   │       ├── BudgetEntity.kt          # 예산 엔티티
│   │       ├── ChatEntity.kt            # 채팅 엔티티
│   │       ├── SmsPatternEntity.kt      # SMS 패턴 벡터 엔티티
│   │       └── MerchantVectorEntity.kt  # 가맹점 벡터 엔티티
│   │
│   ├── datastore/
│   │   └── SettingsDataStore.kt         # API 키, 수입, 결제일 등 설정
│   │
│   ├── model/
│   │   └── Category.kt                  # 카테고리 enum (10종)
│   │
│   ├── theme/
│   │   ├── Color.kt                     # 색상 정의 (80+ 색상)
│   │   └── Theme.kt                     # 라이트/다크 테마
│   │
│   └── util/
│       ├── DateUtils.kt                 # 날짜 유틸리티
│       ├── DataQueryParser.kt           # Gemini 쿼리/액션 JSON 파싱
│       ├── StoreAliasManager.kt         # 가게명 별칭 매핑 (DI, DataStore 연동)
│       ├── SmartParserRepository.kt     # 지능형 파싱 파이프라인
│       ├── VectorUtils.kt               # 코사인 유사도 유틸
│       ├── EmbeddingRepository.kt       # Google Embedding API + 캐시
│       └── PromptTemplates.kt           # 프롬프트 템플릿
│
├── feature/                              # 기능별 모듈
│   ├── home/
│   │   ├── ui/
│   │   │   ├── HomeScreen.kt            # 홈 화면 (월간 현황, 카테고리별 지출)
│   │   │   └── HomeViewModel.kt
│   │   └── data/
│   │       ├── ExpenseRepository.kt
│   │       └── IncomeRepository.kt
│   │
│   ├── history/
│   │   └── ui/
│   │       ├── HistoryScreen.kt         # 뱅크샐러드 스타일 지출 내역
│   │       └── HistoryViewModel.kt
│   │
│   ├── chat/
│   │   ├── ui/
│   │   │   ├── ChatScreen.kt            # AI 상담 채팅
│   │   │   └── ChatViewModel.kt         # 2단계 쿼리 실행 로직
│   │   └── data/
│   │       └── GeminiRepository.kt      # Gemini API 연동 (System Instruction)
│   │
│   ├── settings/
│   │   └── ui/
│   │       ├── SettingsScreen.kt        # 설정 (API 키, 수입, 결제일, 백업)
│   │       └── SettingsViewModel.kt
│   │
│   └── splash/
│       └── ui/
│           └── SplashScreen.kt          # 스플래시 화면
│
├── navigation/
│   ├── Screen.kt                        # Screen sealed class
│   ├── BottomNavItem.kt                 # 하단 네비 아이템
│   └── NavGraph.kt                      # Navigation 그래프
│
├── di/
│   └── DatabaseModule.kt                # Room, DataStore DI
│
└── sms/
    ├── SmsParser.kt                     # 로컬 정규식 기반 SMS 파싱
    └── SmsReader.kt                     # 문자 읽기
```

---

## 4. 데이터 모델

### ExpenseEntity (지출)
```kotlin
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Int,           // 금액
    val storeName: String,     // 가게명
    val category: String,      // 카테고리
    val cardName: String,      // 카드사
    val dateTime: Long,        // 결제 시간
    val originalSms: String,   // 원본 문자
    val smsId: String,         // 문자 ID (중복 방지)
    val memo: String? = null
)
```

### Category (카테고리)
```kotlin
enum class Category(val emoji: String, val displayName: String) {
    FOOD("🍔", "식비"),
    CAFE("☕", "카페"),
    TRANSPORT("🚗", "교통"),
    SHOPPING("🛒", "쇼핑"),
    SUBSCRIPTION("📱", "구독"),
    HEALTH("💊", "의료/건강"),
    CULTURE("🎬", "문화/여가"),
    EDUCATION("📚", "교육"),
    LIVING("🏠", "생활"),
    ETC("📦", "기타")
}
```

### DataQueryRequest (AI 쿼리 요청)
```kotlin
data class DataQueryRequest(
    val queries: List<DataQuery> = emptyList(),
    val actions: List<DataAction> = emptyList()
)
```

---

## 5. AI 연동 (Gemini)

### API 설정
- **SDK**: `com.google.ai.client.generativeai`
- **모델**: `gemini-1.5-flash`
- **API Key 저장**: DataStore

### System Instruction 패턴
두 개의 전용 모델 사용:
1. **Query Analyzer Model** (temperature: 0.3)
   - 사용자 질문 → JSON 쿼리/액션 변환
   - 날짜 파싱 규칙, 쿼리 타입 정의 포함

2. **Financial Advisor Model** (temperature: 0.7)
   - 조회된 데이터 기반 재무 조언
   - 수입 대비 지출 분석 기준 포함

### 2단계 쿼리 흐름
```
사용자 질문
    ↓
[1단계] Gemini (Query Analyzer)
    ↓ JSON 쿼리/액션
[2단계] 로컬 DB 쿼리 실행
    ↓ 결과 데이터
[3단계] Gemini (Financial Advisor)
    ↓
최종 답변
```

---

## 6. 화면 구성

| 화면 | 경로 | 설명 |
|------|------|------|
| 스플래시 | `splash` | 앱 로딩 화면 |
| 홈 | `home` | 월간 현황, 카테고리별 지출, 최근 내역, 월 선택 |
| 내역 | `history` | 지출 목록, 검색, 수동 추가, 필터링 |
| 상담 | `chat` | Gemini AI와 자연어 대화 |
| 설정 | `settings` | API 키, 월 수입, 결제일, 카테고리 예산, 백업/복원, 개인정보 처리방침 |

---

## 7. 권한

```xml
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.INTERNET" />
```

---

## 8. 현재 진행 상황

### ✅ 완료된 작업
- [x] 프로젝트 생성 및 기본 설정
- [x] Room Database (Entity, DAO, Database)
- [x] SMS 로컬 파싱 (정규식 기반)
- [x] UI 화면 (Splash, Home, History, Chat, Settings)
- [x] Navigation + Bottom Nav
- [x] Hilt DI
- [x] Claude API → **Gemini API 마이그레이션**
- [x] System Instruction 기반 토큰 최적화
- [x] 2단계 자연어 데이터 조회 시스템
- [x] 가게명 별칭 매핑 (StoreAliasManager)
- [x] AI 카테고리 일괄 변경 기능
- [x] 결제 주기 기반 달력 뷰
- [x] 데이터 내보내기/가져오기
- [x] 스플래시 화면
- [x] 뒤로가기 두 번 눌러 종료
- [x] **벡터 기반 지능형 SMS 파싱 시스템** (Vector → Regex → Gemini)
- [x] **사용자 정의 별칭 DataStore 영구 저장**
- [x] **HistoryScreen 검색 + 수동 지출 추가**
- [x] **카테고리별 예산 설정 다이얼로그**
- [x] **개인정보 처리방침 다이얼로그**

### ⏳ 향후 작업
- [ ] 위젯 추가
- [ ] 다크 모드 테스트
- [ ] 알림 기능

---

## 9. 알려진 이슈 / TODO

1. **위젯**: 미구현
2. **다크 모드**: 테스트 필요
3. **알림 기능**: 미구현

---

## 10. 주요 파일 참조

| 기능 | 파일 |
|------|------|
| AI 쿼리 분석 | `feature/chat/data/GeminiRepository.kt` |
| 쿼리 실행 | `feature/chat/ui/ChatViewModel.kt` |
| 가게명 별칭 | `core/util/StoreAliasManager.kt` |
| 쿼리 모델 | `core/util/DataQueryParser.kt` |
| SMS 파싱 (정규식) | `core/util/SmsParser.kt` |
| SMS 파싱 (지능형) | `core/util/SmartParserRepository.kt` |
| 벡터 유틸 | `core/util/VectorUtils.kt` |
| 임베딩 API | `core/util/EmbeddingRepository.kt` |
| 홈 화면 | `feature/home/ui/HomeScreen.kt` |
| 설정 저장 | `core/datastore/SettingsDataStore.kt` |

---

*마지막 업데이트: 2026-02-06*
