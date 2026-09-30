# MoneyTalk KB 통합 변경 기록

## 2026-09-30 — 현재 코드 기준 진입점 추가

- 이유: 기존 KB 210개를 보존하면서 지금 조사한 구조·주요 흐름·개발 명령·실행 한계를 작은 묶음에서 추적한다.
- 코드 기준: `95b419e8b98e374a991f3210a194a88959937ee8`; 작성 도구: ClaudeGuide `2d2aa90c82a141e7440d3ee60b692911eb2ee5f1`.
- 확인한 로컬 코드 변경 없음. 앱 소스와 공용 스캐폴드는 수정하지 않았다.
- 생성한 문서: [현재 기준 묶음](project-context/current-baseline/README.md)과 그 [변경 기록](project-context/current-baseline/change-log.md). 기존 [변경 색인](00-change-index.md)도 유지한다.
- 연결한 문서: 이 KB의 [README](README.md), [라우팅](00-agent-routing.md), [프로젝트 컨텍스트](project-context/README.md), 프로젝트 README. 루트/컨텍스트 README의 최신 메타데이터는 이번 진입점 확인 범위이며 전체 기존 본문 재검증이 아니다.
- 근거: [MainViewModel](../../app/src/main/java/com/sanha/moneytalk/MainViewModel.kt)의 `syncSmsV2Internal`, [ChatViewModel](../../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatViewModel.kt)의 `sendMessage`, [AppDatabase](../../app/src/main/java/com/sanha/moneytalk/core/database/AppDatabase.kt)의 annotation/migrations.
- 검증 결과와 남은 확인: [검증 기록](project-context/current-baseline/validation.md). 기기·서버·분류 정확성·앱 컴파일을 성공으로 표시하지 않았다.
- 과거 변경을 찾을 때는 기존 `00-change-index.md`와 각 도메인의 `change-log.md`를 사용한다. 이 파일은 과거 전체 이력을 새로 작성한 것이 아니다.
