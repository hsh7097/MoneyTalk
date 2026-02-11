# 머니톡 (MoneyTalk) - 프로젝트 컨텍스트

> AI 기반 자동 지출 관리 앱: SMS 파싱 + 벡터 캐싱 + Gemini 재무 상담

---

## 1. 프로젝트 개요

### 기본 정보
- **앱 이름**: 머니톡 (MoneyTalk)
- **슬로건**: "돈과 대화하다, AI와 함께"
- **패키지명**: `com.sanha.moneytalk`
- **실제 작업 경로**: `C:\Users\hsh70\AndroidStudioProjects\MoneyTalk`
- **CWD 경로**: `C:\Users\hsh70\OneDrive\문서\Android\MoneyTalk` (Claude Code용, git만 공유)

### 앱의 목적
카드 결제 문자(SMS/MMS/RCS)를 자동으로 수집하고, AI가 지출 정보를 추출하여
사용자에게 맞춤 재무 상담을 제공하는 개인 재무 비서 앱입니다.

### 핵심 가치
1. **자동화**: 문자 수신만으로 지출이 자동 기록됨 (수동 입력 불필요)
2. **학습**: 사용할수록 AI 분류 정확도가 올라감 (벡터 캐싱 + 자가 학습)
3. **대화형 분석**: 자연어로 지출 분석 및 재무 상담 가능

### 기술 스택
| 항목 | 기술 |
|------|------|
| 언어 | Kotlin |
| UI | Jetpack Compose + Material Design 3 |
| 로컬 DB | Room |
| DI | Hilt |
| 네트워크 | OkHttp (Embedding REST API) |
| 비동기 | Coroutines + Flow |
| AI (채팅/분류) | Gemini 2.5 Pro (Google AI SDK) |
| AI (SMS 추출) | Gemini 2.5 Flash Lite |
| AI (요약) | Gemini 2.5 Flash |
| AI (임베딩) | gemini-embedding-001 (768차원 벡터) |
| 아키텍처 | MVVM + Clean Architecture |
| 백업 | Google Drive API |

---

## 2. 앱이 할 수 있는 것

### 2.1 문자 자동 수집 & 파싱
- SMS, MMS, RCS(삼성 기기) 메시지를 통합 읽기
- 실시간 SMS 수신 시 자동 감지 (BroadcastReceiver)
- 3-tier 하이브리드 분류: Regex → 벡터 유사도 → Gemini LLM
- 결제 금액, 가게명, 카드사, 날짜, 카테고리 자동 추출
- 수입 문자(입금, 급여 등)도 자동 감지
- 중복 방지 (smsId 기반)
- **상세**: [SMS_PARSING.md](./SMS_PARSING.md)

### 2.2 카테고리 자동 분류 (15개 카테고리)
- 4-tier 분류: Room 캐시 → 벡터 유사도 → 로컬 키워드 → Gemini 배치
- 사용자 수정 시 유사 가게에 자동 전파 (벡터 유사도 ≥ 0.90)
- 시맨틱 그룹핑으로 Gemini API 호출 ~40% 절감
- **상세**: [CATEGORY_CLASSIFICATION.md](./CATEGORY_CLASSIFICATION.md)

### 2.3 AI 재무 상담 (채팅)
- 자연어로 지출 데이터 조회 ("이번 달 식비 얼마야?")
- 자연어로 카테고리 변경 ("쿠팡은 쇼핑으로 바꿔줘")
- 수입 대비 지출 분석, 절약 조언 제공
- Rolling Summary로 긴 대화 맥락 유지
- **상세**: [CHAT_SYSTEM.md](./CHAT_SYSTEM.md)

### 2.4 수입/예산 관리
- 월 수입 등록 및 지출 대비 잔여 예산 표시
- 월 시작일 커스터마이즈 (카드 결제일 기준 설정 가능)
- 카테고리별 예산 설정 (계획 중)

### 2.5 데이터 관리
- JSON/CSV 형식으로 내보내기 (카드/카테고리 필터 가능)
- Google Drive 백업/복원
- 로컬 파일 복원
- 중복 데이터 일괄 삭제
- 전체 데이터 초기화

---

## 3. 카테고리 목록

| 이모지 | 카테고리 | 코드 | 비고 |
|--------|---------|------|------|
| 🍔 | 식비 | FOOD | |
| ☕ | 카페 | CAFE | |
| 🍺 | 술/유흥 | DRINKING | |
| 🚗 | 교통 | TRANSPORT | |
| 🛒 | 쇼핑 | SHOPPING | |
| 📱 | 구독 | SUBSCRIPTION | |
| 🏥 | 의료/건강 | HEALTH | |
| 💪 | 운동 | FITNESS | |
| 🎬 | 문화/여가 | CULTURE | |
| 📖 | 교육 | EDUCATION | |
| 🏠 | 주거 | HOUSING | |
| 🧺 | 생활 | LIVING | |
| 📋 | 경조 | EVENTS | |
| 💸 | 배달 | DELIVERY | |
| 💌 | 기타 | ETC | |
| 🛵 | 미분류 | UNCLASSIFIED | AI 분류 전 기본값 |

---

## 4. 화면 구성 & 기능 상세

### 4.1 홈 화면 (Home)

**월간 현황 카드**
- 월 선택기 (좌우 화살표로 이전/다음 달 이동)
- 수입 / 지출 / 잔여 예산 표시
- 수입 대비 지출 비율 프로그레스 바

**동기화 버튼 (3가지 모드)**

| 버튼 | 설명 |
|------|------|
| 신규 내역만 동기화 | 마지막 동기화 이후 새 문자만 처리 |
| 오늘 내역만 동기화 | 오늘 자정 이후 문자만 처리 |
| 전체 다시 동기화 | 기기의 모든 SMS/MMS/RCS를 재처리 |

**카테고리별 지출 목록**
- 카테고리 탭 → 해당 카테고리 지출만 필터
- 금액 기준 내림차순 정렬

**최근 지출 내역**
- 이모지 + 가게명 + 카테고리/카드 + 금액 표시
- 클릭 시 상세 다이얼로그 (카테고리 변경 가능)

**자동 분류 알림**
- 동기화 후 미분류 항목 발견 시 분류 다이얼로그 표시
- "확인" → AI 자동 분류 시작, "나중에" → 무시

---

### 4.2 내역 화면 (History)

**3가지 보기 모드**

| 모드 | 설명 |
|------|------|
| 목록 (List) | 날짜별 그룹핑된 지출 리스트 |
| 달력 (Calendar) | 달력 그리드 + 일별 합계 + 무지출일 표시 |
| 수입 (Income) | 수입 내역만 표시 |

**필터 & 정렬**
- 카드 필터 (전체 / 특정 카드)
- 카테고리 필터 (전체 / 특정 카테고리)
- 정렬: 최신순 / 금액 높은순 / 사용처별

**검색**
- 가게명, 금액, 카테고리로 검색

**수동 지출 추가**
- \+ 버튼으로 금액, 가게명, 카테고리, 결제수단 입력

**지출 상세 (클릭 시)**
- 가게명, 금액, 카테고리, 카드, 결제시간, 원본 SMS 확인
- 카테고리 변경 (연필 아이콘)
- 삭제 (빨간색 버튼, 확인 다이얼로그)

---

### 4.3 상담 화면 (Chat)

**세션 관리**
- 좌측 메뉴로 대화 목록 열기
- 새 대화 생성, 기존 대화 선택, 대화 삭제

**가이드 질문 (빈 세션에서 표시)**
- 🔍 지출 조회: "쿠팡에서 얼마 썼어?", "배달 음식 총 얼마야?"
- 📊 분석: "이번 달 식비 분석해줘", "지난달이랑 비교해줘"
- 🏷️ 카테고리 관리: "쿠팡을 쇼핑으로 바꿔줘", "미분류 항목 보여줘"

**AI 응답**
- 데이터 조회 → 분석 → 자연어 답변
- 카테고리 일괄 변경 등 액션도 실행 가능
- 실패 시 "다시 시도" 버튼

**컨텍스트 관리**
- 최근 3턴(6메시지) 전체 유지
- 이전 대화는 Rolling Summary로 압축
- 세션별 독립 관리

---

### 4.4 설정 화면 (Settings)

| 설정 항목 | 설명 |
|----------|------|
| 월 수입 | 매월 수입 금액 입력 |
| 월 시작일 | 카드 결제일 기준 시작일 설정 (1~31일) |
| Gemini API 키 | Google AI API 키 입력 |
| AI 카테고리 분류 | 미분류 항목 일괄 AI 분류 실행 |
| 데이터 내보내기 | JSON/CSV 형식, 카드/카테고리 필터 가능 |
| Google Drive 백업 | 클라우드 백업/복원 |
| 로컬 복원 | 백업 파일에서 복원 |
| 중복 삭제 | 동일 금액+가게+시간 중복 항목 제거 |
| 전체 삭제 | 모든 데이터 초기화 (확인 필요) |
| 버전 정보 | 앱 버전, 개발자 정보 |
| 개인정보 처리방침 | 수집 정보, 이용 목적 등 |

---

## 5. 프로젝트 구조

```
app/src/main/java/com/sanha/moneytalk/
├── MoneyTalkApplication.kt              # Hilt Application
├── MainActivity.kt                       # 메인 액티비티 + Navigation
│
├── core/
│   ├── database/
│   │   ├── AppDatabase.kt               # Room Database
│   │   ├── dao/
│   │   │   ├── ExpenseDao.kt            # 지출 DAO
│   │   │   ├── IncomeDao.kt             # 수입 DAO
│   │   │   ├── ChatDao.kt               # 채팅 기록 DAO
│   │   │   ├── SmsPatternDao.kt         # SMS 벡터 패턴 DAO
│   │   │   ├── StoreEmbeddingDao.kt     # 가게 임베딩 DAO
│   │   │   └── CategoryMappingDao.kt    # 카테고리 매핑 DAO
│   │   └── entity/
│   │       ├── ExpenseEntity.kt          # 지출 엔티티
│   │       ├── IncomeEntity.kt           # 수입 엔티티
│   │       ├── ChatEntity.kt             # 채팅 메시지 엔티티
│   │       ├── ChatSessionEntity.kt      # 채팅 세션 엔티티
│   │       ├── SmsPatternEntity.kt       # SMS 벡터 패턴 (768차원)
│   │       ├── StoreEmbeddingEntity.kt   # 가게 임베딩 (768차원)
│   │       └── CategoryMappingEntity.kt  # 카테고리 정확 매핑 캐시
│   │
│   ├── datastore/
│   │   └── SettingsDataStore.kt          # API 키, 수입, 설정 저장
│   │
│   └── util/
│       ├── SmsReader.kt                  # SMS/MMS/RCS 통합 읽기
│       ├── SmsParser.kt                  # 정규식 기반 SMS 파싱
│       ├── HybridSmsClassifier.kt        # 3-tier 하이브리드 분류기
│       ├── GeminiSmsExtractor.kt         # Gemini LLM SMS 추출
│       ├── SmsEmbeddingService.kt        # 임베딩 생성 (768차원)
│       ├── VectorSearchEngine.kt         # 코사인 유사도 검색
│       ├── SmsBatchProcessor.kt          # 대량 배치 처리 최적화
│       ├── StoreNameGrouper.kt           # 시맨틱 가게명 그룹핑
│       ├── ChatContextBuilder.kt         # 채팅 컨텍스트 조립
│       ├── DataQueryParser.kt            # 쿼리/액션 JSON 파싱
│       └── DateUtils.kt                  # 날짜 유틸리티
│
├── domain/
│   └── model/
│       └── Category.kt                   # 카테고리 enum (17개)
│
├── feature/
│   ├── home/
│   │   ├── ui/
│   │   │   ├── HomeScreen.kt            # 홈 화면 UI
│   │   │   └── HomeViewModel.kt         # 홈 ViewModel + 동기화
│   │   └── data/
│   │       ├── CategoryClassifierService.kt  # 4-tier 카테고리 분류
│   │       ├── StoreEmbeddingRepository.kt   # 가게 벡터 DB
│   │       └── CategoryRepository.kt         # 카테고리 매핑 DB
│   │
│   ├── history/
│   │   └── ui/
│   │       ├── HistoryScreen.kt         # 내역 화면 (목록/달력/수입)
│   │       └── HistoryViewModel.kt
│   │
│   ├── chat/
│   │   ├── ui/
│   │   │   ├── ChatScreen.kt            # AI 상담 화면
│   │   │   └── ChatViewModel.kt
│   │   └── data/
│   │       ├── GeminiRepository.kt       # Gemini API (3개 모델)
│   │       ├── ChatRepository.kt         # 채팅 데이터 인터페이스
│   │       ├── ChatRepositoryImpl.kt     # Rolling Summary 구현
│   │       └── ChatPrompts.kt            # 시스템 프롬프트 정의
│   │
│   └── settings/
│       └── ui/
│           ├── SettingsScreen.kt         # 설정 화면
│           └── SettingsViewModel.kt
│
├── di/
│   ├── DatabaseModule.kt                # Room DI
│   └── AppModule.kt                     # 앱 전역 DI
│
└── receiver/
    └── SmsReceiver.kt                   # SMS 실시간 수신
```

---

## 6. Gemini API 사용처

| 용도 | 모델 | temp | 설명 |
|------|------|------|------|
| SMS 결제 정보 추출 | gemini-2.5-flash-lite | 0.1 | JSON(금액/가게/카드/카테고리) |
| 쿼리 분석 (채팅) | gemini-2.5-pro | 0.3 | 사용자 질문→DB 쿼리 결정 |
| 재무 상담 (채팅) | gemini-2.5-pro | 0.7 | 데이터 기반 조언 생성 |
| 대화 요약 | gemini-2.5-flash | 0.3 | Rolling Summary 생성 |
| 카테고리 분류 | gemini-2.5-flash | -- | 미분류 가게명 배치 분류 |
| 임베딩 생성 | gemini-embedding-001 | -- | 768차원 벡터 (REST API) |

---

## 7. 권한

```xml
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

## 8. 참고 문서

| 문서 | 설명 |
|------|------|
| [SMS_PARSING.md](./SMS_PARSING.md) | SMS 파싱 & 벡터 캐싱 시스템 |
| [CATEGORY_CLASSIFICATION.md](./CATEGORY_CLASSIFICATION.md) | 카테고리 분류 & 벡터 전파 시스템 |
| [CHAT_SYSTEM.md](./CHAT_SYSTEM.md) | AI 채팅 상담 & 컨텍스트 관리 |
| [GIT_CONVENTION.md](./GIT_CONVENTION.md) | Git 브랜치 전략 |
| [DEVELOPMENT_LOG.md](./DEVELOPMENT_LOG.md) | 개발 이력 |

---

*마지막 업데이트: 2026-02-08*
