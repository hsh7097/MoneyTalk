---
type: guide
title: KB 스카폴드 가이드
description: Android 서비스 팀이 AI 작업용 KB를 만들 때 따를 구조, 작성 원칙, 템플릿 사용법을 설명한다.
tags: [kb-scaffold, guide, android, moneytalk, okf]
resource: docs/kb-scaffold/
timestamp: 2026-07-03T15:51:26+09:00
status: draft
---

# KB 스카폴드 가이드 초안

이 문서는 Android 서비스 팀이 AI 작업용 KB를 만들 때 공통으로 따를 기본 구조와 작성 원칙을 정리한다.
목표는 사람이 모든 문서를 읽기 좋게 만드는 것이 아니라, AI가 작업 범위에 맞는 문서만 찾아 읽고 코드 작업의 맥락을 빠르게 복원하게 만드는 것이다.

## 이 가이드의 위치

```text
docs/kb-scaffold/
├── README.md
├── 00-change-index.md
├── 01-principles.md
├── 02-folder-structure.md
├── 03-authoring-workflow.md
├── 04-automation-package-routing.md
├── 05-team-rollout.md
└── templates/
    ├── domain-readme.md
    ├── module-readme.md
    ├── agent-routing.md
    ├── file-inventory.md
    ├── structure-map.md
    ├── change-log.md
    └── package-reference/
        ├── README.md
        ├── 01-entry-screen.md
        ├── 02-data-viewmodel.md
        ├── 03-rendering-action.md
        └── 04-files-checklist.md

```

## 먼저 합의할 기준

1. KB는 현재 코드 구현 상태를 설명한다.
2. PRD, 개발 스펙, 미래 마이그레이션 목표는 KB와 분리한다.
3. 루트 문서는 상세 구현 설명이 아니라 읽을 문서를 고르는 라우팅 역할을 한다.
4. 상세 지식은 도메인 또는 서브모듈 단위 문서로 나눈다.
5. 모든 KB 묶음은 변경 로그를 가진다.
6. 각 KB 패키지는 현재 코드의 폴더 구조, 패키지 구조, 핵심 파일 위치를 가진다.
7. 핵심 파일 목록에는 각 파일의 책임과 변경 시 확인할 주변 파일을 함께 적는다.
8. 작업 유형별로 AI가 어떤 구조 정보와 세부 문서를 어떤 순서로 참조할지 명시한다.
9. 확인되지 않은 내용을 추정해서 쓰지 않는다.
10. 운영 문서와 코드 분석 KB를 분리한다.
11. 항상 로드할 문서와 필요할 때 읽을 문서를 구분한다.
12. 각 README에는 생성되는 KB 파일이 어떤 파일인지, 언제 읽어야 하는지 표로 명시한다.
13. 처음 목표는 AI가 Compose 화면, ViewModel, Repository, 공통 파이프라인의 확장 지점을 찾고 최소 변경을 시작할 수 있게 하는 수준이다.
14. 각 Markdown 문서 상단에는 YAML frontmatter를 두고 `type`, `title`, `description`, `tags`, `resource`, `timestamp`, `status`를 기본 메타데이터로 기록한다.

## Claude Code 문서 구조와의 관계

저장소에 Claude Code용 문서 구조를 둘 때는 `CLAUDE.md`를 얇은 진입점으로 두고, 상세 지식은 `.claude/docs/`와 `.claude/docs/kb/` 아래로 분리한다.
이 스카폴드는 KB 작성에 필요한 문서 배치 원칙만 다룬다.

```text
CLAUDE.md
.claude/
├── guidelines.md
├── settings.json
├── settings.local.json
├── docs/
│   ├── architecture.md
│   ├── build-and-run.md
│   ├── branch-strategy.md
│   ├── conventions.md
│   ├── ci-cd.md
│   ├── jira.md
│   ├── confluence.md
│   └── kb/
└── skills/
```

문서 구분:

| 구분 | 위치 | 내용 |
|---|---|---|
| 진입점 | `CLAUDE.md` | 프로젝트 요약, 얇은 인덱스, 항상 지킬 규칙 |
| 행동 가이드 | `.claude/guidelines.md` | 항상 로드해도 되는 짧은 작업 규칙 |
| 운영 문서 | `.claude/docs/` | 빌드, 브랜치, CI, Jira, Confluence, 컨벤션 |
| 코드 분석 KB | `.claude/docs/kb/` | 도메인/서브모듈 코드 흐름, 파일 역할, 수정 위치 |
| 로컬 실험 KB | `docs/...` 또는 로컬 전용 경로 | Git에 올리지 않는 실험/자동화용 KB |

`CLAUDE.md`에는 KB 상세를 직접 쓰지 않는다.
새 KB가 생기면 `CLAUDE.md` 또는 `.claude/docs/kb/README.md`에는 한 줄 인덱스만 추가한다.
KB 본문은 항상 라우팅 후 필요한 문서만 읽는다.

이 스카폴드에서 다루는 Claude Code 문서 구조 범위는 아래로 제한한다.

- `CLAUDE.md`는 프로젝트 진입점과 문서 인덱스 역할만 한다.
- `.claude/guidelines.md`는 항상 로드해도 되는 짧은 행동 규칙만 둔다.
- `.claude/docs/`는 빌드, 브랜치, CI, 컨벤션 같은 운영 문서를 둔다.
- `.claude/docs/kb/`는 코드 분석 KB의 공식 위치로 사용할 수 있다.
- `settings.local.json`처럼 개인 환경 파일은 공유 문서 기준에 포함하지 않는다.

## 권장 패키지 방식

기존 화면 KB처럼 상위 KB 패키지 아래에 도메인별 패키지를 둔다.

```text
docs/<kb-name>/
├── README.md
├── 00-agent-routing.md
├── 00-change-index.md
├── 01-structure-map.md
├── home/
├── history/
├── chat/
├── settings/
├── sms-pipeline/
├── finance-data/
└── app-functions/
```

팀 공유/커밋 대상 KB라면 기본 위치는 `.claude/docs/kb/`를 우선 검토한다.
기존 로컬 KB나 자동화 전용 KB처럼 Git에 올리지 않는 문서는 `docs/...` 또는 절대 경로 기반 로컬 KB로 둘 수 있다.
두 위치가 함께 존재하면 `CLAUDE.md`와 `.claude/docs/kb/README.md`가 공식 진입점이고, 로컬 KB는 보조 자료다.

이 방식의 핵심은 루트가 전체 설명을 모두 들고 있지 않고, 작업 성격에 따라 도메인 패키지로 내려가게 만드는 것이다.
예를 들어 History 작업이면 `history/README.md`와 `history/package-reference/`만 읽고, Chat이나 Settings 문서는 읽지 않게 한다.

서브모듈 KB가 필요하면 같은 원칙으로 별도 패키지를 추가한다.

```text
docs/<kb-name>/
├── sms-pipeline/
├── app-functions/
├── notification/
└── finance-data/
```

도메인 패키지는 화면/업무 흐름을 설명하고, 서브모듈 패키지는 여러 도메인에서 공통으로 쓰는 구현체의 목적과 사용법을 설명한다.

## 파일 구성과 역할

| 문서 | 역할 |
|---|---|
| [README.md](README.md) | 스카폴드 진입점. KB 작성 목적, 기본 구조, 공식/로컬 KB 위치, 자동화 관계를 요약한다. |
| [00-change-index.md](00-change-index.md) | 스카폴드 자체의 변경 이력 색인. 규칙이 바뀐 이유와 영향 문서를 짧게 기록한다. |
| [01-principles.md](01-principles.md) | KB 작성 철학, 금지사항, PRD/스펙/KB 경계 |
| [02-folder-structure.md](02-folder-structure.md) | 루트, 도메인, 서브모듈, 로그 문서 구조 |
| [03-authoring-workflow.md](03-authoring-workflow.md) | 실제 작성/검증/갱신 절차 |
| [04-automation-package-routing.md](04-automation-package-routing.md) | 자동화에서 패키지/경로 라우팅을 갱신하는 규칙 |
| [05-team-rollout.md](05-team-rollout.md) | 팀원이 KB를 나눠 만들고 리뷰하는 진행 방식 |
| [templates/](templates/) | 도메인/서브모듈/패키지 레퍼런스 템플릿 묶음 |

## 템플릿 역할

| 템플릿 | 역할 |
|---|---|
| [templates/agent-routing.md](templates/agent-routing.md) | 변경 파일 경로를 기준으로 어떤 KB 문서를 읽을지 결정하는 라우팅 문서 템플릿 |
| [templates/change-log.md](templates/change-log.md) | 도메인/서브모듈/패키지별 변경 로그 템플릿 |
| [templates/domain-readme.md](templates/domain-readme.md) | Home, History, Chat 같은 화면/도메인 KB의 README 템플릿 |
| [templates/file-inventory.md](templates/file-inventory.md) | 파일별 역할, 확인 시점, 함께 볼 파일을 정리하는 인벤토리 템플릿 |
| [templates/module-readme.md](templates/module-readme.md) | SMS pipeline, App Functions, notification 같은 독립 모듈 또는 서브모듈 KB의 README 템플릿 |
| [templates/package-reference/README.md](templates/package-reference/README.md) | 도메인 내부의 entry/data/rendering/checklist 문서 묶음 인덱스 템플릿 |
| [templates/package-reference/01-entry-screen.md](templates/package-reference/01-entry-screen.md) | 화면 진입, routing, lifecycle 문서 템플릿 |
| [templates/package-reference/02-data-viewmodel.md](templates/package-reference/02-data-viewmodel.md) | ViewModel, Repository, DataSource, API 문서 템플릿 |
| [templates/package-reference/03-rendering-action.md](templates/package-reference/03-rendering-action.md) | Composable, UI state, dialog, action, navigation, analytics 문서 템플릿 |
| [templates/package-reference/04-files-checklist.md](templates/package-reference/04-files-checklist.md) | 수정 전후 파일 확인과 검증 질문 템플릿 |
| [templates/structure-map.md](templates/structure-map.md) | 폴더, 패키지, 핵심 파일, AI 참조 순서를 정리하는 구조 지도 템플릿 |

## 기존 화면 KB와의 관계

기존 화면 KB가 있다면 이 스카폴드의 좋은 예시로 삼을 수 있다.
특히 아래 구조를 재사용한다.

- `README.md`: 전체 인덱스
- `00-agent-routing.md`: 변경 파일 경로별 읽을 문서 라우팅
- `00-change-index.md`: 변경 이력과 반영 문서 색인
- `01-structure-map.md`: 루트 폴더/패키지/파일 구조와 AI 참조 순서
- 도메인 폴더: `home/`, `history/`, `chat/`, `settings/` 등
- 도메인 내부: `README.md` + 화면별 번호 문서 + `package-reference/`

다만 이 가이드는 특정 서비스의 화면 KB에만 한정하지 않는다.
SMS pipeline, App Functions, notification, finance data 같은 서브모듈 KB에도 같은 원칙을 적용한다.

## 자동화와의 관계

예약된 자동화는 KB를 최신 상태로 유지하는 운영 장치다.
자동화는 원격 배포 기준 ref의 diff를 보고 어떤 패키지/도메인 문서를 갱신할지 판단한다.
따라서 KB 스카폴드는 자동화가 읽고 갱신할 수 있는 형태여야 한다.

자동화가 안정적으로 동작하려면 각 KB는 아래 파일을 갖추는 것이 좋다.

- `00-agent-routing.md`: 변경 파일 경로를 도메인/서브모듈 문서로 연결한다.
- `00-change-index.md`: 기준 ref, SHA, 영향 영역, 갱신 문서를 짧게 기록한다.
- `01-structure-map.md` 또는 패키지별 구조 문서: 현재 코드의 폴더/패키지/핵심 파일 구조를 제공한다.
- 각 도메인/서브모듈의 `README.md`: 해당 패키지 진입점이다.
- 도메인 KB의 `package-reference/`: entry/data/rendering/checklist를 나눠 세부 수정 위치를 안내한다.
- 각 도메인/서브모듈의 `05-file-inventory.md`: 전체 파일 역할과 수정 후보를 빠르게 좁히는 필수 인덱스다.

패키지 이동이나 클래스 이동이 발생하면 KB 라우팅 문서만 바꾸는 것으로 끝내지 않는다.
그 변경이 다음 자동화 실행의 분류 기준에도 영향을 주면 자동화 프롬프트의 경로 분류 규칙도 같이 갱신해야 한다.
자세한 절차는 [04-automation-package-routing.md](04-automation-package-routing.md)를 따른다.

## 이번 초안의 결정 범위

이 초안은 최종 규격이 아니라 팀에 공유해 논의하기 위한 출발점이다.
다음 항목은 팀 논의 후 확정한다.

- KB 루트 위치를 `docs/`로 고정할지 별도 repository로 분리할지
- `CLAUDE.md`/`AGENTS.md`와 KB 루트 문서의 연결 방식
- `.claude/docs/kb`와 로컬 전용 `docs/...` KB의 운영 경계
- 로그 파일 형식
- KB 검증을 위한 표준 샘플 작업
- 담당자별 산출물 리뷰 방식
