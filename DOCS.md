# MoneyTalk 개발 문서

## 목차
1. [아키텍처](#아키텍처)
2. [SMS 파싱](#sms-파싱)
3. [데이터베이스](#데이터베이스)
4. [API 연동](#api-연동)
5. [TODO](#todo)

---

## 아키텍처

### MVVM + Clean Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Presentation Layer                    │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │ HomeScreen  │  │ ChatScreen  │  │SettingsScreen│    │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘     │
│         │                │                │             │
│  ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐     │
│  │HomeViewModel│  │ChatViewModel│  │SettingsVM   │     │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘     │
└─────────┼────────────────┼────────────────┼─────────────┘
          │                │                │
┌─────────▼────────────────▼────────────────▼─────────────┐
│                      Domain Layer                        │
│  ┌─────────────────────────────────────────────────┐    │
│  │                  Repository                      │    │
│  │  ExpenseRepository  ClaudeRepository  IncomeRepo │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────┬───────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────┐
│                       Data Layer                         │
│  ┌──────────────┐              ┌──────────────┐         │
│  │  Room DB     │              │  Retrofit    │         │
│  │  (Local)     │              │  (Remote)    │         │
│  └──────────────┘              └──────────────┘         │
└─────────────────────────────────────────────────────────┘
```

### 의존성 주입 (Hilt)

```kotlin
// AppModule.kt - 싱글톤 의존성
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideDatabase(app: Application): AppDatabase

    @Provides @Singleton
    fun provideClaudeApi(): ClaudeApi
}
```

---

## SMS 파싱

### 파싱 흐름

```
SMS 수신 → 카드결제 필터링 → 로컬 파싱 → DB 저장 → UI 업데이트
```

### SmsParser.kt 주요 함수

| 함수 | 설명 | 반환값 |
|------|------|--------|
| `isCardPaymentSms()` | 카드 결제 문자인지 확인 | Boolean |
| `parseSms()` | SMS 전체 파싱 | SmsAnalysisResult |
| `extractAmount()` | 금액 추출 | Int? |
| `extractCardName()` | 카드사 추출 | String |
| `extractStoreName()` | 가게명 추출 | String |
| `extractDateTime()` | 날짜/시간 추출 | String (YYYY-MM-DD HH:mm) |
| `inferCategory()` | 카테고리 추론 | String |

### 정규식 패턴

```kotlin
// 금액 패턴
val amountPattern = Regex("""([\d,]+)원""")
// 예: "30,000원" → 30000

// 날짜 패턴 1: MM/DD, MM-DD, MM.DD
val datePattern1 = Regex("""(\d{1,2})[/.-](\d{1,2})""")
// 예: "12/25" → month=12, day=25

// 날짜 패턴 2: M월 D일
val datePattern2 = Regex("""(\d{1,2})월\s*(\d{1,2})일""")
// 예: "12월 25일" → month=12, day=25

// 시간 패턴: HH:mm
val timePattern = Regex("""(\d{1,2}):(\d{2})""")
// 예: "14:30" → hour=14, minute=30
```

### 카테고리 키워드

```kotlin
val categoryKeywords = mapOf(
    "식비" to listOf("치킨", "피자", "맥도날드", "배달의민족", ...),
    "카페" to listOf("스타벅스", "이디야", "커피", ...),
    "교통" to listOf("택시", "주유소", "KTX", ...),
    "쇼핑" to listOf("쿠팡", "편의점", "올리브영", ...),
    "구독" to listOf("넷플릭스", "멜론", "유튜브", ...),
    "의료/건강" to listOf("병원", "약국", "헬스", ...),
    "문화/여가" to listOf("CGV", "영화", "호텔", ...),
    "교육" to listOf("학원", "서점", "인강", ...),
    "생활" to listOf("통신", "보험", "미용실", ...)
)
```

---

## 데이터베이스

### ExpenseEntity

```kotlin
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Int,           // 금액
    val storeName: String,     // 가게명
    val category: String,      // 카테고리
    val cardName: String,      // 카드사
    val dateTime: Long,        // 결제 시간 (timestamp)
    val originalSms: String,   // 원본 SMS
    val smsId: String,         // SMS 고유 ID (중복 방지)
    val memo: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
```

### 주요 쿼리

```kotlin
// 기간별 총 지출
@Query("SELECT SUM(amount) FROM expenses WHERE dateTime BETWEEN :start AND :end")
suspend fun getTotalExpenseByDateRange(start: Long, end: Long): Int?

// 카테고리별 지출
@Query("SELECT category, SUM(amount) as total FROM expenses WHERE dateTime BETWEEN :start AND :end GROUP BY category")
suspend fun getExpenseSumByCategory(start: Long, end: Long): List<CategorySum>

// 중복 확인
@Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE smsId = :smsId)")
suspend fun existsBySmsId(smsId: String): Boolean
```

---

## API 연동

### Claude API (AI 상담용)

```kotlin
// ClaudeApi.kt
@POST("messages")
suspend fun sendMessage(
    @Header("x-api-key") apiKey: String,
    @Header("anthropic-version") version: String = "2023-06-01",
    @Body request: ClaudeRequest
): ClaudeResponse

// 모델: claude-3-haiku-20240307
```

### 프롬프트 템플릿

```kotlin
// PromptTemplates.kt
fun financialAdvice(
    monthlyIncome: Int,
    totalExpense: Int,
    categoryBreakdown: String,
    recentExpenses: String,
    userQuestion: String
): String
```

---

## TODO

### 진행 중
- [ ] Lottie 애니메이션 적용 (HomeScreen)
- [ ] 빌드 오류 해결

### 예정
- [ ] 실시간 SMS 수신 시 자동 파싱 (BroadcastReceiver)
- [ ] 지출 내역 수정/삭제 기능
- [ ] 월별 리포트 생성
- [ ] 예산 설정 및 알림
- [ ] 위젯 지원
- [ ] 다크 모드

### 완료
- [x] 로컬 SMS 파싱 기능 구현
- [x] 카테고리 자동 분류
- [x] Gradle 버전 호환성 수정
- [x] README.md 작성

---

## 버전 정보

| 라이브러리 | 버전 |
|-----------|------|
| AGP | 8.2.2 |
| Kotlin | 1.9.22 |
| KSP | 1.9.22-1.0.17 |
| Gradle | 8.5 |
| Compose BOM | 2024.04.01 |
| Compose Compiler | 1.5.10 |
| Hilt | 2.50 |
| Room | 2.6.1 |
| Retrofit | 2.9.0 |
| Lottie | 6.3.0 |
