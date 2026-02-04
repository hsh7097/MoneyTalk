# 머니톡 - 빠른 시작 가이드

## Claude와 대화 이어가기

새 대화에서 이렇게 시작하세요:

```
머니톡 프로젝트를 이어서 작업하려고 해.
프로젝트 경로: C:\Users\hsh70\AndroidStudioProjects\MoneyTalk

아래 파일들을 먼저 읽어줘:
1. C:\Users\hsh70\AndroidStudioProjects\MoneyTalk\docs\PROJECT_CONTEXT.md
2. C:\Users\hsh70\AndroidStudioProjects\MoneyTalk\docs\DEVELOPMENT_LOG.md

그리고 [원하는 작업]을 해줘.
```

---

## 자주 사용하는 요청 예시

### 빌드 관련
```
프로젝트 빌드 오류 확인하고 수정해줘
```

### 기능 추가
```
API 키를 DataStore에 저장하는 기능 추가해줘
```

```
월 수입 등록 기능 완성해줘
```

```
홈 화면에 위젯 추가해줘
```

### 버그 수정
```
[오류 메시지 복사]
이 오류 수정해줘
```

### 코드 확인
```
HomeViewModel.kt 파일 확인해줘
```

---

## 프로젝트 주요 파일 위치

| 파일 | 경로 |
|------|------|
| 메인 액티비티 | `app/.../MainActivity.kt` |
| 홈 화면 | `app/.../presentation/home/HomeScreen.kt` |
| Claude API | `app/.../data/remote/api/ClaudeApi.kt` |
| SMS 파서 | `app/.../util/SmsParser.kt` |
| 의존성 설정 | `gradle/libs.versions.toml` |
| 앱 빌드 설정 | `app/build.gradle.kts` |

---

## Claude API 키 발급

1. https://console.anthropic.com 접속
2. 회원가입/로그인
3. API Keys → Create Key
4. 키 복사 (sk-ant-...)
5. 앱 설정에서 입력

---

## 테스트 방법

1. Android Studio에서 Gradle Sync
2. 에뮬레이터 또는 실기기 연결
3. Run 버튼 클릭
4. SMS 권한 허용
5. 설정에서 API 키 입력
6. 홈에서 동기화 버튼 클릭

---

*최종 업데이트: 2026-02-05*
