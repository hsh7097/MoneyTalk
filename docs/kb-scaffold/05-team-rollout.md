---
type: guide
title: 팀 작업 진행 가이드
description: 여러 팀원이 도메인 또는 서브모듈 KB를 나눠 만들고 리뷰할 때의 산출물 기준을 설명한다.
tags: [kb-scaffold, team-rollout, review, collaboration, android]
resource: docs/kb-scaffold/05-team-rollout.md
timestamp: 2026-07-03T15:51:26+09:00
status: draft
---

# 팀 작업 진행 가이드

이 문서는 여러 팀원이 KB를 나눠 만들 때의 진행 방식과 산출물 기준을 정의한다.
목표는 모든 사람이 같은 문장 스타일을 쓰게 하는 것이 아니라, AI가 일관된 방식으로 문서를 찾아 읽고 작업할 수 있게 만드는 것이다.

## 1. 작업 단위

KB 작업은 도메인, 기능, 서브모듈 단위로 나눈다.

도메인 예시:

- Home
- History
- Chat
- Settings
- Transaction Edit
- Category Settings
- SMS Settings

서브모듈 예시:

- SMS Pipeline
- App Functions
- Finance Data / Room Database
- Notification
- Sync Coverage
- Core UI
- Core Util

기능 예시:

- 문자 파싱
- 카테고리 자동/수동 분류
- App Functions를 통한 기능 읽어오기
- 백업/복원
- 리워드 광고와 AI 크레딧
- 거래처 규칙 소급 적용

작업자는 하나의 단위를 맡아도 되고, 작은 모듈은 두세 개를 묶어도 된다.
단, 한 문서 안에서 도메인 지식, 기능 흐름, 서브모듈 코어 지식을 구분한다.

## 2. 담당자 산출물

각 담당자는 최소 아래 산출물을 만든다.

도메인 KB 담당자:

- `<domain>/README.md`
- `<domain>/00-structure-map.md`
- `<domain>/change-log.md`
- `<domain>/05-file-inventory.md`
- `<domain>/package-reference/README.md`
- `<domain>/package-reference/01-entry-screen.md`
- `<domain>/package-reference/02-data-viewmodel.md`
- `<domain>/package-reference/03-rendering-action.md`
- `<domain>/package-reference/04-files-checklist.md`

서브모듈 KB 담당자:

- `<module>/README.md`
- `<module>/00-structure-map.md`
- `<module>/01-purpose-architecture.md`
- `<module>/02-how-to-use.md`
- `<module>/03-extension-points.md`
- `<module>/04-files-checklist.md`
- `<module>/05-file-inventory.md`
- `<module>/change-log.md`

기능 KB 담당자:

- `<feature>/README.md`
- `<feature>/00-structure-map.md`
- `<feature>/01-feature-flow.md`
- `<feature>/02-data-contract.md`
- `<feature>/03-extension-points.md`
- `<feature>/04-files-checklist.md`
- `<feature>/05-file-inventory.md`
- `<feature>/change-log.md`

작은 모듈도 `README.md`, `00-structure-map.md`, `05-file-inventory.md`, `change-log.md`는 최소로 가진다.
세부 목적/사용법/확장 지점 문서는 한 파일 안에 합쳐 시작할 수 있지만, 자동화 라우팅 대상이 되거나 두 개 이상 도메인에서 사용되면 위 구조로 분리한다.

## 3. 작업 시작 체크리스트

작업 전에 아래를 확인한다.

- [ ] 이 작업은 도메인 KB인가, 기능 KB인가, 서브모듈 KB인가?
- [ ] 팀 공유 KB인가, 로컬 전용 KB인가?
- [ ] 팀 공유라면 `.claude/docs/kb/`에 둘 것인가?
- [ ] 대표 코드 경로는 어디인가?
- [ ] 여러 화면/모듈을 가로지르는 end-to-end 기능인가?
- [ ] 다른 도메인에서 재사용하는 공통 구현인가?
- [ ] 기존 화면 KB에 유사 구조가 있는가?
- [ ] 자동화 라우팅 대상 경로인가?
- [ ] 작업 결과를 검증할 샘플 과제가 있는가?

샘플 과제 예시:

- History 신규 필터를 추가한다면 어떤 파일을 찾아야 하는가?
- SMS 신규 sender regex Fast Path 룰이 내려오면 어디를 수정해야 하는가?
- 문자 파싱 결과가 저장되고 화면에 반영되는 전체 경로는 어디인가?
- 카테고리 자동 분류가 실패하면 어떤 service/repository/model을 봐야 하는가?
- agent가 앱 데이터를 읽어오는 기능은 어떤 App Function과 reader를 통하는가?
- Chat App Function을 추가하려면 어떤 function/reader/repository를 봐야 하는가?
- Analytics 로그를 추가할 때 core 문서와 도메인 문서 중 무엇을 먼저 읽어야 하는가?
- 공통 Compose 컴포넌트를 추가하려면 어떤 UI component와 호출 도메인을 봐야 하는가?

## 4. 작성 깊이 기준

작성 깊이는 “AI가 작업을 시작할 수 있을 정도”가 기준이다.

넣어야 하는 내용:

- 문서 상태와 기준 코드 확인일
- 폴더 구조, 패키지 구조, 핵심 파일 역할
- 현재 코드의 주요 진입점
- 데이터 흐름
- 렌더링 흐름
- action/receiver 흐름
- 기능 trigger, orchestrator, data contract, UI refresh 흐름
- analytics/navigation side effect 위치
- 신규 확장 시 수정할 파일 순서
- 반복 실수

넣지 않을 내용:

- `CLAUDE.md`에 들어갈 수준을 넘는 상세 구현
- 파일 전체 내용을 다시 설명하는 수준의 코드 주석
- 현재 구현과 무관한 미래 목표
- PRD 요구사항
- 작업별 구현 계획
- 코드 확인 없이 회의에서 나온 추정
- 서브모듈 문서 안의 특정 도메인/기능 비즈니스 예외

좋은 깊이:

```text
HistoryScreen(...)
→ HistoryViewModel uiState
→ ExpenseRepository/IncomeRepository
→ HistoryFilter/HistoryCalendar/HistoryDialogs
→ TransactionCardCompose 또는 상세/수정 화면 navigation
```

과한 깊이:

```text
각 if 문과 when 분기의 모든 라인을 자연어로 반복 설명
```

부족한 깊이:

```text
History는 HistoryScreen을 보면 된다.
```

## 5. 리뷰 기준

담당자가 만든 KB는 아래 기준으로 리뷰한다.

- [ ] 루트 `README.md` 또는 `00-agent-routing.md`에서 이 문서로 찾아갈 수 있는가?
- [ ] 문서 첫 화면에서 이 KB의 목적과 범위가 보이는가?
- [ ] 문서 상태가 `stub`, `draft`, `verified`, `stale` 중 하나로 표시되어 있는가?
- [ ] 대표 코드 경로가 실제 존재하는가?
- [ ] 도메인 지식과 서브모듈 지식이 섞이지 않았는가?
- [ ] 기능 KB가 end-to-end 흐름을 설명하되 서브모듈 내부 구현을 복사하지 않았는가?
- [ ] 서브모듈 KB에 상위 도메인 의존 정보가 들어가지 않았는가?
- [ ] PRD/스펙/미래 계획이 현재 구현처럼 적히지 않았는가?
- [ ] 신규 작업자가 어떤 파일부터 열지 알 수 있는가?
- [ ] 자동화가 변경 파일 경로로 이 문서를 찾을 수 있는가?
- [ ] `CLAUDE.md` 또는 `.claude/docs/kb/README.md`에서 얇은 인덱스로 연결되는가?
- [ ] change-log가 있는가?
- [ ] 수정 이력이 루트 `00-change-index.md` 또는 담당 패키지 `change-log.md` 중 올바른 위치에 남아 있는가?
- [ ] 검증 과제와 결과가 남아 있는가?

## 6. 비교 리뷰 방식

같은 영역을 두 명 이상이 각자 초안으로 뽑아 비교할 수 있다.
비교의 목적은 문장 스타일 통일이 아니라 “AI 작업에 필요한 정보가 빠졌는지” 확인하는 것이다.

비교할 항목:

- 두 초안이 고른 대표 entry point가 같은가?
- 데이터 흐름이 같은가?
- 기능 trigger와 결과 반영 지점이 같은가?
- Composable/state 생성 위치가 같은가?
- action/analytics/navigation 책임 위치가 같은가?
- 한쪽에만 있는 중요한 실수 방지 규칙이 있는가?
- 너무 세부적인 구현 설명이 들어가 있지는 않은가?
- 자동화 라우팅 표에 넣을 수 있는 경로가 정리되어 있는가?

## 7. 완료 정의

KB 작업은 문서를 만들었다고 끝나지 않는다.
최소 한 번은 AI에게 실제 작업을 시켜보고, 문서가 충분했는지 확인한다.

완료 조건:

- [ ] README 작성
- [ ] 문서 상태 표기
- [ ] 00-structure-map 작성
- [ ] 05-file-inventory 작성
- [ ] 세부 문서 작성 또는 최소 필수 문서 안에 세부 내용 통합
- [ ] change-log 작성
- [ ] 정해진 로그 위치에 변경 이력 기록
- [ ] `00-agent-routing.md` 연결
- [ ] 샘플 작업 1개 이상 검증
- [ ] 부족한 점 반영
- [ ] 팀 공유 가능한 요약 작성

## 8. 공유 형식

팀에 공유할 때 아래 형식으로 공유한다.

```markdown
## <KB 이름> 초안 공유

- 담당 영역:
- 문서 위치:
- 문서 상태:
- 대표 코드 경로:
- AI가 먼저 읽을 문서:
- 포함한 내용:
- 포함하지 않은 내용:
- 검증한 샘플 작업:
- 부족하거나 논의 필요한 점:
```

이 공유 형식은 PR 설명, Jira task comment, 팀 채팅 어디에든 사용할 수 있다.
