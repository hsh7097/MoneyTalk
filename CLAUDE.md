# CLAUDE.md - MoneyTalk 프로젝트 허브

> AI 에이전트가 이 프로젝트에서 작업을 시작할 때 **가장 먼저 읽는 문서**

---

## 프로젝트 요약

**MoneyTalk** = SMS 파싱 기반 자동 지출 추적 + Gemini AI 재무 상담 Android 앱

- Kotlin / Jetpack Compose / MVVM / Hilt DI / Room DB (v5, 10 entities)
- 3-tier SMS 분류 (Regex → Vector → Gemini LLM)
- 4-tier 카테고리 분류 (Room → Vector → Keyword → Gemini Batch)
- 3-step AI 채팅 (쿼리분석 → DB조회/액션/분석 → 답변생성) — 17 쿼리 + 12 액션
- 카드 화이트리스트 (OwnedCard) + SMS 제외 키워드 (블랙리스트)

---

## 경로

| 구분 | 경로 |
|------|------|
| **Windows** | `C:\Users\hsh70\AndroidStudioProjects\MoneyTalk` |
| **macOS** | `/Users/sanha/Documents/Android/MoneyTalk/MoneyTalk` |

> 코드 수정, git, 빌드 모두 해당 OS의 프로젝트 경로에서 수행

---

## 빌드

**Windows**
```bash
cmd.exe /c "cd /d C:\Users\hsh70\AndroidStudioProjects\MoneyTalk && .\gradlew.bat assembleDebug"
```

**macOS**
```bash
./gradlew assembleDebug
```

---

## 문서 가이드

| 문서 | 내용 | 언제 읽나 |
|------|------|----------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 패키지 구조, 파일 트리, 기술 스택, 데이터 흐름 | 구조 파악 시 |
| [docs/AI_CONTEXT.md](docs/AI_CONTEXT.md) | 아키텍처, 임계값 레지스트리, 리팩토링 범위, Golden Flows | 프로젝트 이해 시 |
| [docs/AI_HANDOFF.md](docs/AI_HANDOFF.md) | 현재 작업 상태, 주의사항, 핵심 파일 위치 | 세션 교체 시 |
| [docs/AI_TASKS.md](docs/AI_TASKS.md) | 태스크 목록, 완료 기준, 리스크 | 작업 수행 시 |
| [docs/PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md) | 전체 프로젝트 개요 (원본) | 배경 지식 필요 시 |
| [docs/SMS_PARSING.md](docs/SMS_PARSING.md) | SMS 파싱 시스템 상세 | SMS 관련 작업 시 |
| [docs/CATEGORY_CLASSIFICATION.md](docs/CATEGORY_CLASSIFICATION.md) | 카테고리 분류 시스템 상세 | 분류 관련 작업 시 |
| [docs/CHAT_SYSTEM.md](docs/CHAT_SYSTEM.md) | AI 채팅 시스템 상세 | 채팅 관련 작업 시 |
| [docs/COMPOSABLE_MAP.md](docs/COMPOSABLE_MAP.md) | 화면별 Composable 계층 구조 + IDE 점프 링크 | UI 작업 시 |
| [docs/CHANGELOG.md](docs/CHANGELOG.md) | 버전별 변경 이력 | 변경 히스토리 확인 시 |
| [docs/DEVELOPMENT_LOG.md](docs/DEVELOPMENT_LOG.md) | 날짜별 개발 로그 | 작업 기록 확인 시 |
| [docs/GIT_CONVENTION.md](docs/GIT_CONVENTION.md) | Git 컨벤션 | 커밋/브랜치/푸시/PR 규칙 확인 시 |
| [docs/QUICK_START.md](docs/QUICK_START.md) | 빠른 시작 가이드 | 환경 설정 시 |
| [docs/APP_MAP.md](docs/APP_MAP.md) | 앱 진입점/네비게이션 맵 | 라우팅 파악 시 |

---

## Git

- **커밋/푸시/PR/브랜치 규칙 SSOT**: [docs/GIT_CONVENTION.md](docs/GIT_CONVENTION.md)
- **원칙**: `CLAUDE.md`에는 Git 상세 규칙을 중복 정의하지 않고, 항상 위 문서를 참조한다.
- **GitHub**: https://github.com/hsh7097/MoneyTalk.git

---

## 핵심 구조

> 이 프로젝트의 AI 핵심 로직은 **Vector(연산) → Policy(판단) → Service(행동)** 구조다.
> 모든 AI 시스템 프롬프트는 `res/values/string_prompt.xml`에서 관리한다.

## 핵심 규칙

1. **문서 먼저**: 코드 변경 전에 관련 문서를 읽고, 변경 후에 문서 갱신
2. **빌드 확인**: 모든 코드 변경 후 `assembleDebug` 빌드 성공 확인
3. **임계값 SSOT**: 임계값 수치는 `AI_CONTEXT.md`의 레지스트리가 기준 (SimilarityPolicy 구현체)
4. **DB 스키마 불변**: Room entity 변경 시 마이그레이션 필수 (가급적 하지 않기)
5. **경로 주의**: OneDrive 경로가 아닌 AndroidStudioProjects 경로에서 작업
6. **셀프 리뷰 필수**: 모든 작업 완료 후 변경된 코드를 다시 읽고 셀프 리뷰 수행. 문제 발견 시 즉시 수정한 뒤 작업 완료 보고
7. **Composable 맵 동기화**: 커밋/푸시 시 Composable 함수의 추가·삭제·변경이 있으면 반드시 `docs/COMPOSABLE_MAP.md`를 갱신한다
8. **Composable 분리 원칙**: Composable은 기능 단위로 분리한다. 하나의 Composable이 서로 다른 기능(예: 오늘 지출 카드 + 전월 비교 카드)을 포함하면 각각 독립 Composable로 분리하여 관리한다
