# MoneyTalk 현재 코드 기준 묶음 변경 기록

## 2026-09-30 — 기존 KB에 현재 확인 기준 통합

- 목적: 기존 MoneyTalk KB를 보존하면서 주요 흐름을 실제 코드·심볼·SHA에서 추적할 수 있는 작은 진입 묶음을 추가한다.
- 코드 기준: `95b419e8b98e374a991f3210a194a88959937ee8`; 공용 스캐폴드 기준: `2d2aa90c82a141e7440d3ee60b692911eb2ee5f1`.
- 조사 시작 시 작업트리 차이 없음. 앱 코드·공용 ClaudeGuide 수정 없음; 이번 차이는 KB와 필요한 진입 링크뿐이다.
- 문서: [README](README.md), [데이터 흐름](data-flows.md), [개발·실행](development.md), [검증 기록](validation.md).
- 근거: [Manifest](../../../../app/src/main/AndroidManifest.xml)의 launcher, [MainViewModel](../../../../app/src/main/java/com/sanha/moneytalk/MainViewModel.kt)의 `syncSmsV2Internal`, [ChatViewModel](../../../../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatViewModel.kt)의 `sendMessage`, [AppDatabase](../../../../app/src/main/java/com/sanha/moneytalk/core/database/AppDatabase.kt)의 version/entities.
- 반영: 배치·실시간 ingest 분리, 로컬 embedding, 로컬 채팅과 크레딧 gate, action/final 실패 경계, Room migration, 실제 SDK/Gradle/Firebase 설정 부재와 실행 한계.
- 기존 문서의 날짜/verified를 일괄 바꾸지 않고 재확인 묶음과 기존 상세의 범위를 구분했다. 원격 push/PR/deploy는 수행하지 않았다.
- 검증: 공용 toolkit 28 Markdown 오류 0, 검증기 회귀 37 tests OK. 현재 묶음 5 Markdown 오류 0, 변경 12문서 보충 검사 오류 0. 기존 전체 KB는 최초 210 Markdown/744 errors, 통합 후 216 Markdown/723 errors. 기존 오류는 보존한 구형 문서 범위이며 전체 통과를 주장하지 않는다.
- 탐색: 첫 컨텍스트 노출 실행은 무효로 분리하고 새 탐색자가 질문 6개를 정적 대조했다. 최초 유효 결과는 5개 통과와 Q2 부분 실패였다. 초기 동기화 refresh 예외를 보정하고 수입 저장/크레딧 원장 직접 링크 부족을 해소했다. 상세 결과·기준 해시는 [검증 기록](validation.md)에 남겼다.
- 미확인: Android 컴파일/테스트/기기 동작, Firebase·AI·광고·migration; wrapper 단계 실패를 빌드 성공으로 기록하지 않았다.
