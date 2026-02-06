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
| 로컬 DB | Room (moneytalk_v4.db) |
| DI | Hilt |
| 네트워크 | OkHttp (Embedding REST API) |
| 비동기 | Coroutines + Flow |
| AI (채팅/분류) | Gemini 2.5 Flash (Google AI SDK) |
| AI (SMS 추출) | Gemini 2.5 Flash Lite |
| AI (임베딩) | Gemini gemini-embedding-001 (벡터 유사도) |
| 아키텍처 | MVVM + Clean Architecture |

---

## 2. 핵심 기능

### 2.1 문자 수집
- `ContentResolver`로 기존 카드 문자 읽기
- `BroadcastReceiver`로 실시간 문자 감지
- 카드사 문자 자동 필터링 (KB, 신한, 삼성 등)
- 중복 처리 방지 (smsId 기반)

### 2.2 3-Tier 하이브리드 SMS 분류
- **Tier 1 (Regex)**: 정규식으로 빠르게 분류 (비용 0)
- **Tier 2 (Vector)**: 임베딩 벡터 유사도로 패턴 매칭
- **Tier 3 (LLM)**: Gemini로 비표준 SMS 추출
- 자가 학습: 성공 결과를 벡터 DB에 축적
- 대량 배치 처리: 그룹핑 + 대표 샘플링으로 LLM 호출 최소화
- 상세: [SMS_PARSING.md](./SMS_PARSING.md)

### 2.3 카테고리 자동 분류 (4-Tier)
- Tier 1: Room 매핑 캐시 → Tier 1.5: 벡터 유사도 → Tier 2: 로컬 키워드 → Tier 3: Gemini 배치
- 시맨틱 그룹핑으로 Gemini 호출 ~40% 절감
- 자가 학습: 사용자 수정 → 유사 가게 자동 전파
- 상세: [CATEGORY_CLASSIFICATION.md](./CATEGORY_CLASSIFICATION.md)

### 2.4 AI 재무 상담 (Gemini)
- 2-Phase 처리: 쿼리 분석 → 데이터 조회 → 답변 생성
- Rolling Summary로 긴 대화 맥락 유지
- 자연어로 데이터 조회 및 카테고리 변경 가능
- 상세: [CHAT_SYSTEM.md](./CHAT_SYSTEM.md)

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

### Gemini API (Google AI)
- **SDK**: `com.google.ai.client.generativeai` (Android SDK)
- **채팅 모델**: `gemini-2.5-flash` (쿼리 분석, 상담, 요약)
- **SMS 추출 모델**: `gemini-2.5-flash-lite` (결제 정보 JSON 추출)
- **임베딩 모델**: `gemini-embedding-001` (REST API, OkHttp 직접 호출)
- **API Key 저장**: DataStore 영구 저장 (설정 화면에서 입력)

### Gemini 사용 용도
1. **SMS 결제 정보 추출**: 비표준 SMS → JSON (금액, 가게명, 카드사, 카테고리)
2. **재무 상담**: 2-Phase 처리 (쿼리 분석 + 데이터 기반 답변)
3. **Rolling Summary**: 긴 대화의 과거 내용 요약
4. **카테고리 분류**: 미분류 가게명 일괄 AI 분류
5. **임베딩 생성**: SMS 벡터 유사도 검색용 768차원 벡터

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

*마지막 업데이트: 2026-02-07*
