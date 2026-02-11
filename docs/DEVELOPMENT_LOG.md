# 머니톡 (MoneyTalk) - 개발 로그

> 개발 과정과 변경 사항을 기록하는 문서입니다.

---

## 2026-02-05 - 프로젝트 초기 설정

### 작업 내용

#### 1. 프로젝트 생성
- Android Studio에서 Empty Compose Activity로 생성
- Package: `com.sanha.moneytalk`
- Min SDK: 26 (Android 8.0)

#### 2. 의존성 추가
`gradle/libs.versions.toml` 및 `app/build.gradle.kts` 수정

추가된 라이브러리:
- Room 2.6.1
- Hilt 2.50
- Retrofit 2.9.0 + OkHttp 4.12.0
- Navigation Compose 2.7.7
- Coroutines 1.7.3
- DataStore 1.0.0

#### 3. 패키지 구조 생성
Clean Architecture 기반 구조:
- `data/` - 데이터 레이어 (local, remote, repository)
- `domain/` - 도메인 레이어 (model, usecase)
- `presentation/` - UI 레이어 (screens, viewmodels)
- `di/` - 의존성 주입
- `util/` - 유틸리티

#### 4. Room Database 구현
- `ExpenseEntity` - 지출 내역
- `IncomeEntity` - 수입 내역
- `BudgetEntity` - 예산
- `ChatEntity` - AI 대화 기록

각 Entity에 대한 DAO 구현 완료.

#### 5. Claude API 연동
- `ClaudeApi` 인터페이스 (Retrofit)
- `ClaudeRepository` 구현
- `PromptTemplates` - 문자 분석, 재무 상담 프롬프트

#### 6. SMS 수집 기능
- `SmsReceiver` - 실시간 문자 수신
- `SmsReader` - 기존 문자 읽기
- `SmsParser` - 카드 결제 문자 필터링 및 파싱

#### 7. UI 화면 구현
- `HomeScreen` - 월간 현황, 카테고리별 지출
- `HistoryScreen` - 지출 내역 목록, 필터링
- `ChatScreen` - AI 상담 채팅
- `SettingsScreen` - 설정

#### 8. Navigation 설정
- Bottom Navigation Bar (홈, 내역, 상담, 설정)
- NavGraph 구성

### 생성된 파일 목록

```
app/src/main/java/com/sanha/moneytalk/
├── MoneyTalkApplication.kt
├── MainActivity.kt
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── dao/ExpenseDao.kt
│   │   ├── dao/IncomeDao.kt
│   │   ├── dao/BudgetDao.kt
│   │   ├── dao/ChatDao.kt
│   │   ├── entity/ExpenseEntity.kt
│   │   ├── entity/IncomeEntity.kt
│   │   ├── entity/BudgetEntity.kt
│   │   └── entity/ChatEntity.kt
│   ├── remote/
│   │   ├── api/ClaudeApi.kt
│   │   └── dto/ClaudeModels.kt
│   └── repository/
│       ├── ClaudeRepository.kt
│       ├── ExpenseRepository.kt
│       └── IncomeRepository.kt
├── domain/model/Category.kt
├── presentation/
│   ├── navigation/Screen.kt
│   ├── navigation/BottomNavItem.kt
│   ├── navigation/NavGraph.kt
│   ├── home/HomeScreen.kt
│   ├── home/HomeViewModel.kt
│   ├── history/HistoryScreen.kt
│   ├── history/HistoryViewModel.kt
│   ├── chat/ChatScreen.kt
│   ├── chat/ChatViewModel.kt
│   └── settings/SettingsScreen.kt
├── di/DatabaseModule.kt
├── di/NetworkModule.kt
├── receiver/SmsReceiver.kt
└── util/
    ├── SmsParser.kt
    ├── SmsReader.kt
    ├── DateUtils.kt
    └── PromptTemplates.kt
```

### 다음 작업 예정
1. Gradle Sync 및 빌드 테스트
2. 빌드 오류 수정
3. 실기기 테스트
4. API 키 영구 저장 (DataStore)

---

## 2026-02-05 - 색상 리소스 추가

### 작업 내용

#### Color.kt 대폭 업데이트
가계부 앱에 어울리는 종합적인 색상 팔레트 추가

**추가된 색상 카테고리:**
- Primary Colors (초록 계열 - 메인 브랜드)
- Secondary Colors (블루 계열)
- Tertiary Colors (오렌지 계열)
- Error Colors (빨강 계열)
- Background & Surface Colors
- Semantic Colors (수입, 지출, 저축, 성공, 경고, 정보)
- Category Colors (카테고리별 10가지 색상)
- Chart Colors (차트용 10가지 팔레트)
- Gradient Colors (그라데이션용)
- Text Colors
- Grey Scale (50~900)

#### Theme.kt 업데이트
- 라이트/다크 테마에 새 색상 적용
- 상태바 색상 자동 설정 추가
- dynamicColor 기본값 false로 변경 (브랜드 색상 유지)

---

---

## 2026-02-08 - Phase 1 리팩토링 + 대규모 기능 추가

### 작업 내용

#### 1. VectorSearchEngine 책임 분리 (Phase 1)
- core/similarity/ 패키지 신설 (5개 파일)
- 임계값 상수를 SimilarityPolicy SSOT로 구조화
- Vector(연산) → Policy(판단) → Service(행동) 3계층 구조 확립

#### 2. SMS 파싱 버그 수정 3건
- 결제예정 금액 SMS가 지출로 잡히는 문제
- 신한카드 승인 SMS에서 이름이 가게명으로 파싱되는 문제
- 계좌이체 출금 내역 카테고리 미분류 문제

#### 3. 채팅 시스템 확장
- 채팅 액션 5개 추가 (delete_by_keyword, add_expense, update_memo, update_store_name, update_amount)
- FINANCIAL_ADVISOR 할루시네이션 방지 규칙
- 채팅방 나갈 때 대화 기반 자동 타이틀 설정

#### 4. 기타
- 메모 기능 (DB v1→v2), 보험 카테고리 복원
- 수입 내역 통합 표시, 채팅 UI 리팩토링
- Claude 레거시 코드 완전 제거 (Retrofit 포함)
- SMS 동기화/카테고리 분류 진행률 표시 개선

---

## 2026-02-09 - 데이터 관리 + ANALYTICS

### 작업 내용

#### 1. OwnedCard 시스템 (DB v2→v3)
- OwnedCardEntity/Dao/Repository 신설
- CardNameNormalizer로 25+ 카드사 명칭 정규화
- Settings에서 소유 카드 체크박스 관리

#### 2. SMS 제외 키워드 시스템 (DB v3→v4)
- SmsExclusionKeywordEntity/Dao/Repository 신설
- 기본/사용자/채팅 3가지 소스 구분
- 채팅 액션 2개 추가 (add_sms_exclusion, remove_sms_exclusion)

#### 3. ANALYTICS 쿼리 타입
- ChatViewModel에서 클라이언트 사이드 복합 분석
- 필터(10종 연산자) + 그룹핑(6종) + 집계(5종 메트릭)

#### 4. 기타
- 전역 스낵바 버스 도입
- API 키 설정 후 저신뢰도 항목 자동 재분류
- 프롬프트 XML 이전 (ChatPrompts.kt → string_prompt.xml)
- DB 성능 인덱스 추가 (v4→v5)

---

## 2026-02-11 - UI 공통화 + Intent 패턴

### 작업 내용

#### 1. UI 공통 컴포넌트
- TransactionCardCompose/Info: 지출/수입 통합 카드
- TransactionGroupHeaderCompose/Info: 날짜별/가게별/금액별 그룹 헤더
- SegmentedTabRowCompose/Info: 라운드 버튼 스타일 탭
- Preview 파일 debug/ 소스셋 배치

#### 2. HistoryScreen Intent 패턴
- HistoryIntent sealed interface 도입
- UI-비즈니스 로직 분리 (SelectExpense, DeleteExpense, ChangeCategory 등)

#### 3. 카테고리 아이콘
- 이모지 → 벡터 아이콘 교체 시도 → revert (이모지 유지)

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|-----------|
| 2026-02-05 | 0.1.0 | 프로젝트 초기 설정 및 기본 구조 완성 |
| 2026-02-05 | 0.1.1 | 색상 리소스 대폭 추가 (80+ 색상) |
| 2026-02-08 | 0.2.0 | Phase 1 리팩토링, 채팅 액션 확장, Claude 레거시 제거 |
| 2026-02-09 | 0.3.0 | OwnedCard, SMS 제외 키워드, ANALYTICS, DB v5 |
| 2026-02-11 | 0.4.0 | UI 공통화 (TransactionCard, GroupHeader, SegmentedTab), Intent 패턴 |
