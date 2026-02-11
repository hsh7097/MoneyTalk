# MoneyTalk

SMS 카드 결제 문자를 자동으로 분석하여 지출을 관리하는 Android 앱입니다.

## 주요 기능

- **SMS 자동 파싱**: 카드 결제 문자를 자동으로 읽어 지출/수입 내역 추출
- **로컬 정규식 파싱**: API 호출 없이 앱 내에서 빠르게 처리
- **카테고리 자동 분류**: Gemini AI + 로컬 키워드 매칭으로 14개 카테고리 자동 분류
- **월간 지출 현황**: 수입/지출/잔여 예산 한눈에 확인
- **AI 재무 상담**: Gemini AI를 활용한 맞춤형 재무 조언
- **수입 자동 인식**: 입금 문자 자동 파싱 (급여, 이체, 환급 등)

## 스크린샷

| 홈 화면 | 카테고리별 지출 | AI 상담 |
|---------|----------------|---------|
| 월간 현황 | 지출 분석 | 재무 조언 |

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Kotlin |
| UI | Jetpack Compose, Material3 |
| Architecture | MVVM, Clean Architecture |
| DI | Hilt |
| Database | Room |
| Network | Retrofit, OkHttp |
| Async | Coroutines, StateFlow |
| Animation | Lottie |

## 프로젝트 구조

```
app/src/main/java/com/sanha/moneytalk/
├── data/
│   ├── local/          # Room DB, DAO, Entity
│   ├── remote/         # Retrofit API, DTO
│   └── repository/     # Repository 구현체
├── domain/
│   └── model/          # 도메인 모델
├── presentation/
│   ├── home/           # 홈 화면
│   ├── chat/           # AI 상담 화면
│   └── settings/       # 설정 화면
├── di/                 # Hilt 모듈
├── receiver/           # SMS BroadcastReceiver
└── util/               # 유틸리티 (SmsParser, SmsReader 등)
```

## SMS 파싱 방식

로컬 정규식을 사용하여 API 호출 없이 빠르게 처리합니다.

```kotlin
// SmsParser.kt
fun parseSms(message: String, smsTimestamp: Long): SmsAnalysisResult {
    val amount = extractAmount(message)      // 금액 추출
    val cardName = extractCardName(message)  // 카드사 추출
    val storeName = extractStoreName(message) // 가게명 추출
    val dateTime = extractDateTime(message)   // 날짜/시간 추출
    val category = inferCategory(storeName)   // 카테고리 추론
    // ...
}
```

### 지원 카테고리 (14개)

| 카테고리   | 이모지 | 예시 |
|--------|--------|------|
| 식비     | 🍔 | 배달의민족, 맥도날드, 식당 |
| 카페     | ☕ | 스타벅스, 이디야, 투썸 |
| 술/유흥   | 🍺 | 포차, 호프, 노래방, 클럽 |
| 교통     | 🚗 | 택시, 주유소, KTX, 버스 |
| 쇼핑     | 🛒 | 쿠팡, 편의점, 올리브영 |
| 구독     | 📱 | 넷플릭스, 멜론, 유튜브 |
| 의료/건🏥 | 💊 | 병원, 약국, 의료기기 |
| 운동     | 💪 | 헬스장, 피트니스, 필라테스 |
| 문화/여가  | 🎬 | CGV, 영화, 호텔, 여행 |
| 교육     | 📖 | 학원, 서점, 인강 |
| 주거     | 🏠 | 월세, 전세, 관리비 |
| 생활     | 🧺 | 통신비, 미용실, 세탁소 |
| 경조     | 📋 | 축의금, 조의금, 선물 |
| 기타     | 💸 | 분류 불가 항목 |

## Gemini AI 카테고리 분류

Gemini API를 사용하여 가게명을 자동으로 카테고리로 분류합니다.

### 프롬프트 예시

```
당신은 가계부 앱의 카테고리 분류 전문가입니다.
아래 가게명들을 반드시 주어진 카테고리 목록 중 하나로만 분류해주세요.

## 사용 가능한 카테고리:
1. 식비
2. 카페
3. 술/유흥
4. 교통
5. 쇼핑
6. 구독
7. 의료/건강
8. 운동
9. 문화/여가
10. 교육
11. 주거
12. 생활
13. 경조
14. 기타

## 중요 규칙:
1. 반드시 위 14개 카테고리 중 정확히 하나만 사용하세요.
2. 괄호나 추가 설명 없이 카테고리명만 작성하세요.
3. 보험회사(삼성화재, 현대해상 등)는 "기타"로 분류하세요.

## 응답 형식:
가게명: 카테고리

예시:
스타벅스: 카페
이마트: 쇼핑
서울대학교병원: 의료/건강
```

### 응답 처리

- 괄호 안 추가 정보 자동 제거: `보험 (의료/건강)` → `보험` → `기타`
- 카테고리 정규화: `의료` → `의료/건강`, `문화` → `문화/여가`
- 유효하지 않은 응답은 `기타`로 처리

---

## 설치 및 실행

### 요구사항

- Android Studio Hedgehog 이상
- JDK 17
- Android SDK 34
- minSdk 26

### 빌드

```bash
# 클론
git clone https://github.com/hsh7097/MoneyTalk.git

# 프로젝트 열기
cd MoneyTalk

# (선택) Claude API 키 설정
echo "CLAUDE_API_KEY=your_api_key" >> local.properties

# 빌드
./gradlew assembleDebug
```

## 권한

앱에서 사용하는 권한:

```xml
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.INTERNET" />
```

## 라이선스

MIT License

## 기여

이슈와 PR은 언제나 환영합니다!
