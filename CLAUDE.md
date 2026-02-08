# CLAUDE.md - MoneyTalk 프로젝트 허브

> AI 에이전트가 이 프로젝트에서 작업을 시작할 때 **가장 먼저 읽는 문서**

---

## 프로젝트 요약

**MoneyTalk** = SMS 파싱 기반 자동 지출 추적 + Gemini AI 재무 상담 Android 앱

- Kotlin / Jetpack Compose / MVVM / Hilt DI / Room DB
- 3-tier SMS 분류 (Regex → Vector → Gemini LLM)
- 4-tier 카테고리 분류 (Room → Vector → Keyword → Gemini Batch)
- 2-phase AI 채팅 (쿼리분석 → DB조회 → 답변생성)

---

## 경로 (중요!)

| 구분 | 경로 |
|------|------|
| **코드 수정 경로** | `C:\Users\hsh70\AndroidStudioProjects\MoneyTalk` |
| CWD (git 전용) | `C:\Users\hsh70\OneDrive\문서\Android\MoneyTalk` |

> **반드시 AndroidStudioProjects 경로에서 코드를 수정할 것!**

---

## 빌드

```bash
cmd.exe /c "cd /d C:\Users\hsh70\AndroidStudioProjects\MoneyTalk && .\gradlew.bat assembleDebug"
```

---

## 문서 가이드

| 문서 | 내용 | 언제 읽나 |
|------|------|----------|
| [docs/AI_CONTEXT.md](docs/AI_CONTEXT.md) | 아키텍처, 임계값 레지스트리, 리팩토링 범위, Golden Flows | 프로젝트 이해 시 |
| [docs/AI_HANDOFF.md](docs/AI_HANDOFF.md) | 현재 작업 상태, 주의사항, 핵심 파일 위치 | 세션 교체 시 |
| [docs/AI_TASKS.md](docs/AI_TASKS.md) | 태스크 목록, 완료 기준, 리스크 | 작업 수행 시 |
| [docs/PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md) | 전체 프로젝트 개요 (원본) | 배경 지식 필요 시 |
| [docs/SMS_PARSING.md](docs/SMS_PARSING.md) | SMS 파싱 시스템 상세 | SMS 관련 작업 시 |
| [docs/CATEGORY_CLASSIFICATION.md](docs/CATEGORY_CLASSIFICATION.md) | 카테고리 분류 시스템 상세 | 분류 관련 작업 시 |
| [docs/CHAT_SYSTEM.md](docs/CHAT_SYSTEM.md) | AI 채팅 시스템 상세 | 채팅 관련 작업 시 |

---

## Git

- **브랜치 전략**: `master` (릴리스), `develop` (개발), 기능 브랜치는 `develop`에서 분기
- **GitHub**: https://github.com/hsh7097/MoneyTalk.git

---

## 핵심 구조

> 이 프로젝트의 AI 핵심 로직은 **Vector(연산) → Policy(판단) → Service(행동)** 구조다.

## 핵심 규칙

1. **문서 먼저**: 코드 변경 전에 관련 문서를 읽고, 변경 후에 문서 갱신
2. **빌드 확인**: 모든 코드 변경 후 `assembleDebug` 빌드 성공 확인
3. **임계값 SSOT**: 임계값 수치는 `AI_CONTEXT.md`의 레지스트리가 기준 (SimilarityPolicy 구현체)
4. **DB 스키마 불변**: Room entity 변경 시 마이그레이션 필수 (가급적 하지 않기)
5. **경로 주의**: OneDrive 경로가 아닌 AndroidStudioProjects 경로에서 작업
