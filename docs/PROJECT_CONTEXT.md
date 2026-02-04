# 머니톡 (MoneyTalk) - 프로젝트 컨텍스트

> 이 문서는 Claude와 대화를 이어가기 위한 프로젝트 컨텍스트 파일입니다.
> 새 대화 시작 시 이 파일을 공유하면 이전 작업 내용을 이어갈 수 있습니다.

---

## 1. 프로젝트 개요

### 기본 정보
- **앱 이름**: 머니톡 (MoneyTalk)
- **슬로건**: "돈과 대화하다, AI와 함께"
- **패키지명**: `com.sanha.moneytalk`
- **프로젝트 경로**: `C:\Users\hsh70\OneDrive\문서\Android\MoneyTalk`

### 컨셉
카드 결제 문자를 자동 수집하고, Claude AI가 분석하여 맞춤 재무 상담을 제공하는 개인 재무 비서 앱

### 기술 스택
| 항목 | 기술 |
|------|------|
| 언어 | Kotlin |
| UI | Jetpack Compose + Material Design 3 |
| 로컬 DB | Room |
| DI | Hilt |
| 네트워크 | Retrofit + OkHttp |
| 비동기 | Coroutines + Flow |
| AI | Claude API (Anthropic) |
| 아키텍처 | MVVM + Clean Architecture |

---

## 2. 핵심 기능

### 2.1 문자 수집
- `ContentResolver`로 기존 카드 문자 읽기
- `BroadcastReceiver`로 실시간 문자 감지
- 카드사 문자 자동 필터링 (KB, 신한, 삼성 등)
- 중복 처리 방지 (smsId 기반)

### 2.2 AI 분석 (Claude)
- 문자에서 금액, 가게명, 날짜 자동 추출
- 카테고리 자동 분류 (식비, 교통, 쇼핑 등)
- 소비 패턴 분석

### 2.3 AI 재무 상담
- 자연어로 질문 가능
- 내 재무 데이터 기반 맞춤 조언
- 예: "이번 달 커피값 얼마야?", "식비 줄이려면?"

### 2.4 수입/예산 관리
- 월 수입 등록
- 카테고리별 예산 설정
- 잔여 예산 실시간 표시

---

## 3. 프로젝트 구조

```
app/src/main/java/com/sanha/moneytalk/
├── MoneyTalkApplication.kt          # Hilt Application
├── MainActivity.kt                   # 메인 액티비티 + Navigation
│
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt           # Room Database
│   │   ├── dao/
│   │   │   ├── ExpenseDao.kt        # 지출 DAO
│   │   │   ├── IncomeDao.kt         # 수입 DAO
│   │   │   ├── BudgetDao.kt         # 예산 DAO
│   │   │   └── ChatDao.kt           # 채팅 기록 DAO
│   │   └── entity/
│   │       ├── ExpenseEntity.kt     # 지출 엔티티
│   │       ├── IncomeEntity.kt      # 수입 엔티티
│   │       ├── BudgetEntity.kt      # 예산 엔티티
│   │       └── ChatEntity.kt        # 채팅 엔티티
│   │   └── SettingsDataStore.kt     # API 키, 수입 등 설정 저장
│   │
│   ├── remote/
│   │   ├── api/ClaudeApi.kt         # Claude API 인터페이스
│   │   └── dto/ClaudeModels.kt      # Request/Response DTO
│   │
│   └── repository/
│       ├── ClaudeRepository.kt      # Claude API 연동
│       ├── ExpenseRepository.kt     # 지출 데이터
│       └── IncomeRepository.kt      # 수입 데이터
│
├── domain/
│   └── model/
│       └── Category.kt              # 카테고리 enum
│
├── presentation/
│   ├── navigation/
│   │   ├── Screen.kt                # Screen sealed class
│   │   ├── BottomNavItem.kt         # 하단 네비 아이템
│   │   └── NavGraph.kt              # Navigation 그래프
│   │
│   ├── home/
│   │   ├── HomeScreen.kt            # 홈 화면 UI
│   │   └── HomeViewModel.kt         # 홈 ViewModel
│   │
│   ├── history/
│   │   ├── HistoryScreen.kt         # 지출 내역 화면
│   │   └── HistoryViewModel.kt
│   │
│   ├── chat/
│   │   ├── ChatScreen.kt            # AI 상담 화면
│   │   └── ChatViewModel.kt
│   │
│   └── settings/
│       ├── SettingsScreen.kt        # 설정 화면
│       └── SettingsViewModel.kt     # 설정 ViewModel
│
├── di/
│   ├── DatabaseModule.kt            # Room DI
│   └── NetworkModule.kt             # Retrofit DI
│
├── receiver/
│   └── SmsReceiver.kt               # SMS 수신 BroadcastReceiver
│
└── util/
    ├── SmsParser.kt                 # 카드 문자 파싱
    ├── SmsReader.kt                 # 문자 읽기
    ├── DateUtils.kt                 # 날짜 유틸
    └── PromptTemplates.kt           # Claude 프롬프트 템플릿
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

### IncomeEntity (수입)
```kotlin
@Entity(tableName = "incomes")
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Int,
    val type: String,          // 월급, 부수입 등
    val description: String,
    val isRecurring: Boolean,  // 고정 수입 여부
    val recurringDay: Int?,    // 매월 입금일
    val dateTime: Long
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

---

## 5. 화면 구성

| 화면 | 경로 | 설명 |
|------|------|------|
| 홈 | `home` | 월간 현황, 카테고리별 지출, 최근 내역 |
| 내역 | `history` | 전체 지출 내역, 필터링, 삭제 |
| 상담 | `chat` | Claude AI와 대화 |
| 설정 | `settings` | 수입/예산 설정, API 키 설정 |

---

## 6. API 연동

### Claude API
- **Base URL**: `https://api.anthropic.com/`
- **Endpoint**: `POST /v1/messages`
- **Model**: `claude-3-haiku-20240307` (비용 효율적)
- **API Key 저장 방식**:
  1. `local.properties`에 기본값 설정 → BuildConfig로 빌드 시 포함
  2. DataStore로 영구 저장 (사용자가 앱 내에서 변경 가능)
  3. 우선순위: DataStore > BuildConfig

### 프롬프트 템플릿
1. **문자 분석**: 카드 문자 → JSON (금액, 가게, 카테고리)
2. **재무 상담**: 사용자 질문 + 재무 데이터 → 맞춤 조언

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
- [x] build.gradle 의존성 추가 (Room, Hilt, Retrofit, Compose)
- [x] 패키지 구조 생성
- [x] Room Database (Entity, DAO, Database)
- [x] Claude API 연동 코드
- [x] SMS 수집 기능 (SmsReader, SmsReceiver, SmsParser)
- [x] UI 화면 (Home, History, Chat, Settings)
- [x] Navigation 설정
- [x] Hilt DI 모듈
- [x] API 키 저장 기능 (DataStore + BuildConfig)
- [x] SettingsDataStore 클래스 생성
- [x] SettingsViewModel 생성
- [x] 월 수입 저장 기능
- [x] 테마 색상 확장 (80+ 색상)

### ⏳ 다음 작업
- [ ] Android Studio에서 Gradle Sync
- [ ] 빌드 오류 수정
- [ ] 에뮬레이터/실기기 테스트
- [ ] 카테고리별 예산 설정 기능 완성
- [ ] 위젯 추가
- [ ] 다크 모드 테스트

---

## 9. 알려진 이슈 / TODO

1. ~~**API 키 저장**: 현재 메모리에만 저장됨 → DataStore로 영구 저장 필요~~ ✅ 완료
2. ~~**수입 등록**: 다이얼로그만 있고 실제 저장 로직 미구현~~ ✅ 완료
3. **예산 설정**: UI만 있고 기능 미구현
4. **백업/복원**: 기능 미구현
5. **위젯**: 미구현

---

## 10. 새 대화 시작 시 사용법

새 Claude 대화에서 다음과 같이 시작하세요:

```
이 프로젝트 컨텍스트 파일을 읽어줘:
C:\Users\hsh70\OneDrive\문서\Android\MoneyTalk\docs\PROJECT_CONTEXT.md

그리고 [원하는 작업]을 해줘.
```

예시:
- "빌드 오류 해결해줘"
- "API 키 저장 기능 추가해줘"
- "위젯 기능 만들어줘"

---

## 11. 참고 링크

- **Claude API 문서**: https://docs.anthropic.com/
- **Anthropic 콘솔**: https://console.anthropic.com/
- **기획서**: `C:\Users\hsh70\.claude\plans\drifting-booping-conway.md`

---

*마지막 업데이트: 2026-02-05*
