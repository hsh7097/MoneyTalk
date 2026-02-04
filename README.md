# MoneyTalk

SMS 카드 결제 문자를 자동으로 분석하여 지출을 관리하는 Android 앱입니다.

## 주요 기능

- **SMS 자동 파싱**: 카드 결제 문자를 자동으로 읽어 지출 내역 추출
- **로컬 정규식 파싱**: API 호출 없이 앱 내에서 빠르게 처리
- **카테고리 자동 분류**: 가게명 기반 9개 카테고리 자동 분류
- **월간 지출 현황**: 수입/지출/잔여 예산 한눈에 확인
- **AI 재무 상담**: Claude AI를 활용한 맞춤형 재무 조언

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

### 지원 카테고리

| 카테고리 | 예시 |
|----------|------|
| 식비 | 배달의민족, 맥도날드, 식당 |
| 카페 | 스타벅스, 이디야, 투썸 |
| 교통 | 택시, 주유소, KTX |
| 쇼핑 | 쿠팡, 편의점, 올리브영 |
| 구독 | 넷플릭스, 멜론, 유튜브 |
| 의료/건강 | 병원, 약국, 헬스 |
| 문화/여가 | CGV, 영화, 호텔 |
| 교육 | 학원, 서점, 인강 |
| 생활 | 통신, 보험, 미용실 |

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
