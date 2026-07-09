# AGENTS.md - MoneyTalk 프로젝트 허브

> AI 에이전트가 이 프로젝트에서 작업을 시작할 때 **가장 먼저 읽는 문서**

---

## 프로젝트 요약

**MoneyTalk** = SMS 파싱 기반 자동 지출 추적 + Gemini AI 재무 상담 Android 앱

- Kotlin / Jetpack Compose / MVVM / Hilt DI / Room DB (v8, 19 entities)
- 3-tier SMS 분류 (Regex → Vector → Gemini LLM)
- 4-tier 카테고리 분류 (Room → Vector → Keyword → Gemini Batch)
- 3-step AI 채팅 (쿼리분석 → DB조회/액션/분석 → 답변생성) — 18 쿼리 + 13 액션
- 카드 화이트리스트 (OwnedCard) + SMS 제외 키워드 (블랙리스트)

---

## 경로

| 구분 | 경로 |
|------|------|
| **Windows** | `C:\Users\hsh70\project\android\MoneyTalk` |
| **macOS** | `/Users/sanha/Documents/Android/MoneyTalk/MoneyTalk` |

> 코드 수정, git, 빌드 모두 해당 OS의 프로젝트 경로에서 수행

---

## 빌드

**Windows**
```bash
cmd.exe /c "cd /d C:\Users\hsh70\project\android\MoneyTalk && .\gradlew.bat assembleDebug"
```

**macOS**
```bash
./gradlew assembleDebug
```

---

## 문서 가이드

| 문서 | 내용 | 언제 읽나 |
|------|------|----------|
| [docs/moneytalk-kb/project-context/01-system-overview.md](docs/moneytalk-kb/project-context/01-system-overview.md) | 패키지 구조, 핵심 시스템, DB/AI 운영 경계 | 구조 파악 시 |
| [docs/moneytalk-kb/project-context/02-threshold-registry.md](docs/moneytalk-kb/project-context/02-threshold-registry.md) | SMS/분류 유사도 임계값 레지스트리 | 임계값/정확도 조정 시 |
| [docs/moneytalk-kb/sms-parsing/06-ingestion-contract.md](docs/moneytalk-kb/sms-parsing/06-ingestion-contract.md) | SMS/MMS/RCS 읽기, 저장, 실시간 보완 계약 | SMS 관련 작업 시 |
| [docs/moneytalk-kb/category-classification/06-classification-tiers.md](docs/moneytalk-kb/category-classification/06-classification-tiers.md) | 카테고리 분류 티어와 학습/전파 기준 | 분류 관련 작업 시 |
| [docs/moneytalk-kb/chat/05-system-contract.md](docs/moneytalk-kb/chat/05-system-contract.md) | AI 채팅 Local Fast Path, 3-step, Query/Action/ANALYTICS 계약 | 채팅 관련 작업 시 |
| [docs/moneytalk-kb/app-functions/README.md](docs/moneytalk-kb/app-functions/README.md) | App Functions 조회/수정 함수 목록, DB 확인 플레이북 | 앱 함수/DB 조회 작업 시 |
| [docs/moneytalk-kb/budget-credit-monetization/02-policy-and-plans.md](docs/moneytalk-kb/budget-credit-monetization/02-policy-and-plans.md) | AI 크레딧/광고/결제 후속 정책 | AI 상담/수익화 고도화 작업 시 |
| [docs/moneytalk-kb/README.md](docs/moneytalk-kb/README.md) | AI 작업용 코드 분석 KB 루트. 변경 파일 경로를 도메인/기능/서브모듈 문서로 라우팅 | 개발 작업 시작 시, 특히 파일 위치/책임 경계가 애매할 때 |
| [docs/kb-scaffold/README.md](docs/kb-scaffold/README.md) | MoneyTalk KB 생성/갱신 스카폴드. 새 KB 패키지 작성 기준과 템플릿 | KB를 새로 만들거나 갱신할 때 |
| [docs/moneytalk-kb/ui-map/01-screen-composable-index.md](docs/moneytalk-kb/ui-map/01-screen-composable-index.md) | 화면별 Composable 계층과 담당 KB | UI 작업 시 |
| [docs/moneytalk-kb/release-history/01-release-timeline.md](docs/moneytalk-kb/release-history/01-release-timeline.md) | 구조 판단에 필요한 변경 이력 요약 | 변경 히스토리 확인 시 |
| [docs/moneytalk-kb/project-operations/01-git-workflow.md](docs/moneytalk-kb/project-operations/01-git-workflow.md) | Git 컨벤션 | 커밋/브랜치/푸시/PR 규칙 확인 시 |
| [docs/moneytalk-kb/budget-credit-monetization/02-policy-and-plans.md](docs/moneytalk-kb/budget-credit-monetization/02-policy-and-plans.md) | 과금 전략, 광고, API 비용 방어, 후속 결제 계획 | 과금/수익화 관련 작업 시 |
| [docs/moneytalk-kb/screen-requirements/01-screen-requirements-index.md](docs/moneytalk-kb/screen-requirements/01-screen-requirements-index.md) | 화면별 요구사항과 담당 KB | **모든 UI 작업 전 필수** |

---

## Git

- **커밋/푸시/PR/브랜치 규칙 SSOT**: [docs/moneytalk-kb/project-operations/01-git-workflow.md](docs/moneytalk-kb/project-operations/01-git-workflow.md)
- **원칙**: `AGENTS.md`에는 Git 상세 규칙을 중복 정의하지 않고, 항상 위 문서를 참조한다.
- **GitHub**: https://github.com/hsh7097/MoneyTalk.git

---

## 핵심 구조

> 이 프로젝트의 AI 핵심 로직은 **Vector(연산) → Policy(판단) → Service(행동)** 구조다.
> 모든 AI 시스템 프롬프트는 `res/values/string_prompt.xml`에서 관리한다.

## 핵심 규칙

1. **문서 먼저**: 코드 변경 전에 관련 문서를 읽고, 변경 후에 문서 갱신
   - 변경 파일 경로가 정해졌으면 `docs/moneytalk-kb/00-agent-routing.md`로 관련 KB를 먼저 고른다.
   - 새 KB를 만들거나 기존 KB 구조를 바꿀 때는 `docs/kb-scaffold/README.md`와 `docs/kb-scaffold/03-authoring-workflow.md`를 따른다.
2. **빌드 확인**: 모든 코드 변경 후 `assembleDebug` 빌드 성공 확인
3. **임계값 SSOT**: 임계값 수치는 `docs/moneytalk-kb/project-context/02-threshold-registry.md`와 `core/similarity/**` 구현체가 기준
4. **DB 스키마 불변**: Room entity 변경 시 마이그레이션 필수 (가급적 하지 않기)
5. **경로 주의**: OneDrive 경로가 아닌 `C:\Users\hsh70\project\android\MoneyTalk` 경로에서 작업
6. **셀프 리뷰 필수**: 모든 작업 완료 후 변경된 코드를 다시 읽고 셀프 리뷰 수행. 문제 발견 시 즉시 수정한 뒤 작업 완료 보고
7. **Composable 맵 동기화**: 커밋/푸시 시 Composable 함수의 추가·삭제·변경이 있으면 반드시 `docs/moneytalk-kb/ui-map/01-screen-composable-index.md`를 갱신한다
8. **Composable 분리 원칙**: Composable은 기능 단위로 분리한다. 하나의 Composable이 서로 다른 기능(예: 오늘 지출 카드 + 전월 비교 카드)을 포함하면 각각 독립 Composable로 분리하여 관리한다
9. **기능 단위 파일 분리**: 새로운 기능은 새로운 파일에서 작업한다. 기능 단위를 최대한 분리하여 데이터에 종속되지 않도록 한다. (예: 차트 컴포넌트는 차트 렌더링만 담당하고 도메인 데이터에 의존하지 않음)
10. **로깅**: 로그는 `MoneyTalkLogger`를 사용한다. `android.util.Log` 직접 사용 금지. `MoneyTalkLogger.i()`, `.w()`, `.e()` 사용
