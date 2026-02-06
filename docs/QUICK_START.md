# 머니톡 - 빠른 시작 가이드

## 프로젝트 구조 요약

```
app/src/main/java/com/sanha/moneytalk/
├── core/           # 공통 모듈 (DB, DataStore, 유틸리티)
├── feature/        # 기능별 모듈 (home, history, chat, settings, splash)
├── navigation/     # 네비게이션
├── di/             # Hilt DI
└── sms/            # SMS 파싱
```

---

## 주요 기능 파일 위치

| 기능 | 파일 |
|------|------|
| AI 쿼리 분석 | `feature/chat/data/GeminiRepository.kt` |
| 쿼리 실행 | `feature/chat/ui/ChatViewModel.kt` |
| 가게명 별칭 | `core/util/StoreAliasManager.kt` |
| 쿼리 모델 | `core/util/DataQueryParser.kt` |
| SMS 파싱 | `sms/SmsParser.kt` |
| 홈 화면 | `feature/home/ui/HomeScreen.kt` |
| 설정 저장 | `core/datastore/SettingsDataStore.kt` |

---

## AI 기능 사용 예시

### 자연어 질문
```
"이번 달 쿠팡에서 얼마 썼어?"
"식비가 수입 대비 적절해?"
"지난 3개월 지출 패턴 분석해줘"
```

### 카테고리 관리
```
"쿠팡은 쇼핑으로 분류해줘"
"배달의민족 포함된건 식비로 바꿔줘"
"미분류 항목 보여줘"
```

---

## Gemini API 키 발급

1. https://aistudio.google.com 접속
2. "Get API key" 클릭
3. 프로젝트 선택 후 키 생성
4. 키 복사 (AIza...)
5. 앱 설정에서 입력

---

## 지원 가게명 별칭 (일부)

| 메인 이름 | 별칭 |
|----------|------|
| 쿠팡 | coupang, 쿠페이, 쿠팡이츠 |
| 배달의민족 | 배민, baemin |
| 스타벅스 | starbucks, 스벅, 별다방 |
| 네이버 | naver, 네이버페이 |
| 카카오 | kakao, 카카오페이 |

전체 목록: `core/util/StoreAliasManager.kt` 참조

---

## 테스트 방법

1. Android Studio에서 Gradle Sync
2. 에뮬레이터 또는 실기기 연결
3. Run 버튼 클릭
4. SMS 권한 허용
5. 설정에서 Gemini API 키 입력
6. 홈에서 동기화 버튼 클릭
7. 채팅에서 자연어 질문 테스트

---

## 주요 문서

| 문서 | 설명 |
|------|------|
| `PROJECT_CONTEXT.md` | 프로젝트 전체 컨텍스트 |
| `DEVELOPMENT_LOG.md` | 개발 이력 |
| `GIT_CONVENTION.md` | Git 커밋 규칙 |

---

*최종 업데이트: 2026-02-05*
