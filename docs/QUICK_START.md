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
| RCS 메시지가 읽히지 않음 | 삼성 기기에서만 지원, 전체 다시 동기화 시도 |

---

## 5. Claude Code로 개발 이어가기

```
머니톡 프로젝트를 이어서 작업하려고 해.
프로젝트 경로: C:\Users\hsh70\AndroidStudioProjects\MoneyTalk

아래 파일을 먼저 읽어줘:
C:\Users\hsh70\AndroidStudioProjects\MoneyTalk\docs\PROJECT_CONTEXT.md

그리고 [원하는 작업]을 해줘.
```

### 주요 파일 위치

| 파일 | 설명 |
|------|------|
| `feature/home/ui/HomeViewModel.kt` | 동기화, 데이터 로딩 핵심 로직 |
| `core/util/HybridSmsClassifier.kt` | 3-tier SMS 분류기 |
| `core/util/SmsReader.kt` | SMS/MMS/RCS 읽기 |
| `core/util/SmsParser.kt` | 정규식 파싱 |
| `feature/chat/data/GeminiRepository.kt` | Gemini API 통신 |
| `feature/home/data/CategoryClassifierService.kt` | 4-tier 카테고리 분류 |
| `docs/` | 기술 문서 전체 |

---

*최종 업데이트: 2026-02-08*
