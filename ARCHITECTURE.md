# MoneyTalk 앱 구조

## 개요

MoneyTalk는 SMS 문자 메시지를 분석하여 지출을 자동으로 추적하는 Android 앱입니다.
Jetpack Compose, Hilt DI, Room Database, MVVM 아키텍처를 사용합니다.

## 패키지 구조

```
com.sanha.moneytalk/
├── MainActivity.kt                    # 앱 진입점
├── MoneyTalkApplication.kt            # Application 클래스 (Hilt)
│
├── core/                              # 공통 모듈
│   ├── database/                      # Room 데이터베이스
│   │   ├── AppDatabase.kt             # Room Database 정의
│   │   ├── dao/                       # Data Access Objects
│   │   │   ├── ExpenseDao.kt          # 지출 DAO
│   │   │   ├── IncomeDao.kt           # 수입 DAO
│   │   │   ├── ChatDao.kt             # 채팅 DAO
│   │   │   └── BudgetDao.kt           # 예산 DAO
│   │   └── entity/                    # Room Entities
│   │       ├── ExpenseEntity.kt       # 지출 Entity
│   │       ├── IncomeEntity.kt        # 수입 Entity
│   │       ├── ChatEntity.kt          # 채팅 Entity
│   │       └── BudgetEntity.kt        # 예산 Entity
│   │
│   ├── datastore/                     # DataStore 설정
│   │   └── SettingsDataStore.kt       # 앱 설정 저장소
│   │
│   ├── di/                            # Hilt DI 모듈
│   │   ├── DatabaseModule.kt          # DB 의존성 주입
│   │   └── NetworkModule.kt           # 네트워크 의존성 주입
│   │
│   ├── model/                         # 공통 모델
│   │   └── Category.kt                # 지출 카테고리 Enum
│   │
│   ├── theme/                         # Compose 테마
│   │   ├── Color.kt                   # 색상 정의
│   │   ├── Theme.kt                   # 테마 정의
│   │   └── Type.kt                    # 타이포그래피 정의
│   │
│   └── util/                          # 유틸리티
│       ├── DateUtils.kt               # 날짜/시간 유틸리티
│       ├── SmsParser.kt               # SMS 파싱 (정규식)
│       ├── SmsReader.kt               # SMS 읽기
│       └── PromptTemplates.kt         # AI 프롬프트 템플릿
│
├── feature/                           # 기능별 모듈
│   ├── home/                          # 홈 탭
│   │   ├── data/
│   │   │   ├── ExpenseRepository.kt   # 지출 Repository
│   │   │   └── IncomeRepository.kt    # 수입 Repository
│   │   └── ui/
│   │       ├── HomeScreen.kt          # 홈 화면 UI
│   │       └── HomeViewModel.kt       # 홈 ViewModel
│   │
│   ├── history/                       # 내역 탭
│   │   └── ui/
│   │       ├── HistoryScreen.kt       # 내역 화면 UI
│   │       └── HistoryViewModel.kt    # 내역 ViewModel
│   │
│   ├── chat/                          # AI 상담 탭
│   │   ├── data/
│   │   │   ├── ClaudeApi.kt           # Claude API 인터페이스
│   │   │   ├── ClaudeModels.kt        # API 모델 정의
│   │   │   └── ClaudeRepository.kt    # Claude Repository
│   │   └── ui/
│   │       ├── ChatScreen.kt          # 채팅 화면 UI
│   │       └── ChatViewModel.kt       # 채팅 ViewModel
│   │
│   └── settings/                      # 설정 탭
│       └── ui/
│           ├── SettingsScreen.kt      # 설정 화면 UI
│           └── SettingsViewModel.kt   # 설정 ViewModel
│
├── navigation/                        # 네비게이션
│   ├── NavGraph.kt                    # Navigation Graph
│   ├── Screen.kt                      # Screen 정의
│   └── BottomNavItem.kt               # 하단 탭 아이템
│
└── receiver/                          # BroadcastReceiver
    └── SmsReceiver.kt                 # SMS 수신 리시버
```

## 주요 기능

### 1. 홈 (Home)
- 월간 수입/지출 현황 표시
- 카테고리별 지출 요약
- 최근 지출 내역 (20건)
- SMS 동기화 버튼
- 월 이동 (이전/다음 월)
- 커스텀 월 시작일 지원 (월급일 기준)

### 2. 내역 (History)
- 전체 지출 내역 조회
- 월별 필터링
- 카드사별 필터링
- 일별/월별 합계
- 지출 삭제 기능

### 3. AI 상담 (Chat)
- Claude API를 통한 재정 상담
- 지출 패턴 분석
- 맞춤형 조언 제공

### 4. 설정 (Settings)
- 월 수입 설정
- 월 시작일 설정
- Claude API 키 설정
- 데이터 백업/복원 (예정)

## 기술 스택

- **UI**: Jetpack Compose, Material 3
- **Architecture**: MVVM, Repository Pattern
- **DI**: Hilt
- **Database**: Room
- **Preferences**: DataStore
- **Network**: Retrofit, OkHttp
- **Navigation**: Compose Navigation
- **Animation**: Lottie

## 데이터 흐름

```
SMS → SmsReader → SmsParser → ExpenseEntity → ExpenseDao → ExpenseRepository → ViewModel → UI
```

1. **SMS 읽기**: `SmsReader`가 ContentResolver를 통해 SMS를 읽음
2. **SMS 파싱**: `SmsParser`가 정규식으로 금액, 가게명, 카드사 추출
3. **데이터 저장**: `ExpenseEntity`로 변환하여 Room에 저장
4. **UI 표시**: ViewModel이 Repository를 통해 데이터를 Flow로 구독

## 커스텀 월 기간

월급일 기준으로 한 달을 계산하는 기능:
- 예: 월 시작일이 21일이면, 21일 ~ 다음달 20일이 한 달
- `DateUtils.getCustomMonthPeriod()` 함수로 계산
- `SettingsDataStore`에 월 시작일 저장

## 증분 동기화

SMS 동기화 최적화:
- 마지막 동기화 시간 저장 (`lastSyncTime`)
- 이후 메시지만 파싱하여 성능 향상
- 중복 체크 (`smsId`)

## 빌드 설정

- Gradle: 8.5
- AGP: 8.2.2
- Kotlin: 1.9.22
- KSP: 1.9.22-1.0.17
- compileSdk: 34
- minSdk: 26
- targetSdk: 34
