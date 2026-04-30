# 머니톡 - 사용 가이드

> 앱 설치부터 실사용까지의 전체 과정

---

## 1. 초기 설정

### Step 1: 앱 설치 & 실행
1. Android Studio에서 빌드 후 실기기에 설치
2. 앱 실행 → 스플래시 화면 (1.5초) → 홈 화면

### Step 2: SMS 권한 허용
1. 홈 화면에서 동기화 버튼 클릭
2. SMS 읽기/수신 권한 요청 팝업 → **허용**
3. 권한 거부 시: 설정 → 앱 → MoneyTalk → 권한에서 직접 허용

### Step 3: Gemini API 키 설정
1. [Google AI Studio](https://aistudio.google.com/) 접속
2. API 키 발급 (Get API Key)
3. 앱 **설정** 탭 → **Gemini API 키** → 키 입력 → 저장

### Step 4: 월 수입 설정
1. 설정 탭 → **월 수입** → 금액 입력 → 저장
2. (선택) 월 시작일 설정 → 카드 결제일에 맞춰 설정

---

## 2. 기본 사용법

### 문자 동기화

| 동기화 방식 | 사용 시점 |
|-----------|---------|
| 신규 내역만 | 일상적 사용 (마지막 동기화 이후 새 문자만) |
| 오늘 내역만 | 오늘 지출만 빠르게 확인할 때 |
| 전체 다시 | 최초 설치 시, 또는 누락된 내역 재수집 시 |

- 동기화 완료 후 미분류 항목이 있으면 AI 분류 다이얼로그 표시
- "확인" 클릭 시 Gemini가 자동 분류 실행

### 실시간 수신
- 카드 결제 문자 수신 시 자동으로 지출 기록 (별도 동기화 불필요)

### 지출 확인
- **홈 화면**: 월간 총 지출, 카테고리별 비율, 최근 내역
- **내역 화면**: 전체 내역 검색/필터/정렬, 달력 보기

### 카테고리 수정
1. 지출 항목 클릭 → 상세 다이얼로그
2. 카테고리 옆 연필 아이콘 클릭
3. 원하는 카테고리 선택
4. 유사한 가게명의 카테고리도 자동 전파됨

### AI 상담
1. **상담** 탭 이동
2. 가이드 질문 클릭 또는 직접 질문 입력
3. 예시: "이번 달 식비 얼마야?", "쿠팡을 쇼핑으로 바꿔줘"

---

## 3. 데이터 백업

### Google Drive 백업
1. 설정 → Google Drive 백업 → Google 로그인
2. 내보내기 → Google Drive 저장
3. 복원: Google Drive 백업 → 파일 선택 → 복원

### 로컬 백업
1. 설정 → 데이터 내보내기 → JSON/CSV 선택 → 로컬 저장
2. 복원: 설정 → 로컬 파일에서 복원 → 파일 선택

---

## 4. 문제 해결

| 증상 | 해결 방법 |
|------|---------|
| 문자가 동기화되지 않음 | SMS 권한 확인, 전체 다시 동기화 시도 |
| AI 분류가 안 됨 | 설정에서 Gemini API 키 확인 |
| 카테고리가 "미분류"로 남음 | 설정 → AI 카테고리 자동 분류 실행 |
| 상담 채팅이 안 됨 | API 키 설정 확인, 네트워크 연결 확인 |
| 중복 지출이 있음 | 설정 → 중복 데이터 삭제 |
| RCS 메시지가 읽히지 않음 | 메시지 앱 알림 접근 권한 확인, 앱 재실행 후 다시 시도, 필요 시 전체 다시 동기화 |

---

## 5. Claude Code로 개발 이어가기

### Windows 예시

```
머니톡 프로젝트를 이어서 작업하려고 해.
프로젝트 경로: C:\Users\hsh70\AndroidStudioProjects\MoneyTalk

아래 파일을 먼저 읽어줘:
C:\Users\hsh70\AndroidStudioProjects\MoneyTalk\CLAUDE.md
C:\Users\hsh70\AndroidStudioProjects\MoneyTalk\docs\AI_CONTEXT.md

그리고 [원하는 작업]을 해줘.
```

### macOS 예시

```
머니톡 프로젝트를 이어서 작업하려고 해.
프로젝트 경로: /Users/sanha/Documents/Android/MoneyTalk/MoneyTalk

아래 파일을 먼저 읽어줘:
/Users/sanha/Documents/Android/MoneyTalk/MoneyTalk/CLAUDE.md
/Users/sanha/Documents/Android/MoneyTalk/MoneyTalk/docs/AI_CONTEXT.md

그리고 [원하는 작업]을 해줘.
```

### 주요 파일 위치

| 파일 | 설명 |
|------|------|
| [`MainViewModel.kt`](../app/src/main/java/com/sanha/moneytalk/MainViewModel.kt) | SMS 동기화/권한/광고 전역 오케스트레이터 |
| [`feature/home/ui/HomeViewModel.kt`](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeViewModel.kt) | 홈 월별 데이터 로딩/캐시 |
| [`core/sms/SmsSyncCoordinator.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsSyncCoordinator.kt) | 배치 SMS 파싱 메인 진입점 |
| [`core/sms/SmsReaderV2.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsReaderV2.kt) | SMS/MMS/RCS 통합 읽기 |
| [`core/sms/SmsSyncMessageReader.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsSyncMessageReader.kt) | 동기화 대상 기간 SMS 원본 읽기 래퍼 |
| [`core/sms/SmsTransactionDateResolver.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsTransactionDateResolver.kt) | SMS 본문 거래 날짜/시간 공통 해석 |
| [`core/sync/SmsSyncRangeCalculator.kt`](../app/src/main/java/com/sanha/moneytalk/core/sync/SmsSyncRangeCalculator.kt) | 증분/월별 동기화 기간 계산 |
| [`core/sync/SyncCoveragePagePolicy.kt`](../app/src/main/java/com/sanha/moneytalk/core/sync/SyncCoveragePagePolicy.kt) | 월별 coverage/CTA 판정 |
| [`core/sms/SmsInstantProcessor.kt`](../app/src/main/java/com/sanha/moneytalk/core/sms/SmsInstantProcessor.kt) | 실시간 SMS/MMS/RCS 처리 |
| [`receiver/NotificationTransactionService.kt`](../app/src/main/java/com/sanha/moneytalk/receiver/NotificationTransactionService.kt) | 메시지 앱 알림 기반 RCS 보완 |
| [`feature/chat/data/GeminiRepository.kt`](../app/src/main/java/com/sanha/moneytalk/feature/chat/data/GeminiRepository.kt) | Gemini API 통신 |
| [`feature/home/data/CategoryClassifierService.kt`](../app/src/main/java/com/sanha/moneytalk/feature/home/data/CategoryClassifierService.kt) | 4-tier 카테고리 분류 |
| `docs/` | 기술 문서 전체 |

### SMS 동기화 검증 명령

월별 데이터 누락, 연말/연초 환불 날짜 보정, 월별 coverage/CTA 회귀를 확인할 때 아래 테스트를 우선 실행합니다.

```bash
./gradlew testDebugUnitTest --tests "com.sanha.moneytalk.core.sync.MonthlySmsSyncOrderRegressionTest"
```

실기기 SMS Provider 검증은 SMS 권한이 허용된 설치 상태에서 실행합니다.

```bash
./gradlew :app:installDebug :app:installDebugAndroidTest
adb -s <serial> shell pm grant com.sanha.moneytalk android.permission.READ_SMS
adb -s <serial> shell am instrument -w -r \
  -e class com.sanha.moneytalk.core.sync.RealDeviceMonthlySmsSyncOrderInstrumentedTest \
  com.sanha.moneytalk.test/androidx.test.runner.AndroidJUnitRunner
```

홈/가계부 월 이동까지 직접 검증해야 할 때는 UI 이동 instrumented test를 실행합니다.
현재 좌표 기반 검증은 `SM-F966N` 실기기에서만 수행되고, 다른 기기에서는 스킵됩니다.

```bash
adb -s <serial> shell am instrument -w -r \
  -e class com.sanha.moneytalk.core.sync.RealDeviceMonthlyPageNavigationInstrumentedTest \
  com.sanha.moneytalk.test/androidx.test.runner.AndroidJUnitRunner
```

---

*최종 업데이트: 2026-04-30*
