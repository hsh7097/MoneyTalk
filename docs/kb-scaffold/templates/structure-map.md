---
type: structure-map
title: "<도메인명, 기능명 또는 서브모듈명> 구조 지도"
description: "폴더, 패키지, 핵심 파일, AI 참조 순서를 정리한다."
tags: [structure-map, kb, android, "<domain-or-module>"]
resource: "<target-source-root>"
timestamp: "<YYYY-MM-DDTHH:mm:ss+09:00>"
status: draft
---

# <도메인명, 기능명 또는 서브모듈명> 구조 지도

> 역할:
> - `<kb-root>/01-structure-map.md` 또는 `<package>/00-structure-map.md`를 만들기 위한 템플릿이다.
> - 폴더, 패키지, 핵심 파일, AI 참조 순서를 위치 중심으로 정리한다.
> - 루트 인스턴스와 패키지 인스턴스에서 사용할 섹션을 구분해 작성한다.

> 상태: draft
> 기준: `<YYYY-MM-DD 현재 코드 확인>`

이 문서는 AI가 작업 중 실제 파일을 찾을 때 기준으로 삼는 구조 지도다.
설명보다 위치와 참조 순서를 우선한다.
도메인 구조 지도는 도메인 내부 구현을 설명하고, 기능 구조 지도는 end-to-end 흐름 참여 파일을 설명하며, 서브모듈 구조 지도는 모듈 자체 구조만 설명한다.
서브모듈 구조 지도에 특정 상위 도메인의 비즈니스 예외를 넣지 않는다.

사용 기준:

- 도메인/기능/서브모듈 인스턴스: 아래 섹션을 그대로 쓰되, 해당 없는 섹션은 제거한다.
- 루트 인스턴스: Root 표의 repository root, 도메인/기능/서브모듈 배치, AI Reference Order 중심으로 쓰고 단일 패키지를 전제하는 항목은 줄인다.
- 확인하지 못한 항목은 행을 지우지 말고 `미확인`으로 표기해 “확인 안 됨”과 “존재하지 않음”을 구분한다.
- 해당 없음이 확인된 항목은 행을 제거한다.

## Root

| 항목 | 경로 |
|---|---|
| repository root | `<repo-root>` |
| module root | `<module-root>` |
| main source set | `<src/main/java 또는 src/main/kotlin>` |
| debug source set | `<src/debug/...>` |
| test source set | `<src/test/...>` |

## Folder Tree

```text
<module-root>/
├── build.gradle.kts
├── src/main/java/<package>/
│   ├── <entry>/
│   ├── <data>/
│   ├── <rendering>/
│   └── <action>/
└── src/debug/java/<package>/
```

## Package Groups

| package 또는 folder | 책임 | 대표 파일 |
|---|---|---|
| `<package.entry>` | 화면 진입, Activity, NavGraph, permission | `<EntryFile.kt>` |
| `<package.viewmodel>` | 상태, fetch, action event | `<ViewModel.kt>` |
| `<package.data>` | Repository, DAO, DataSource, Service, model | `<Repository.kt>` |
| `<package.rendering>` | Composable, UI state, dialog, card | `<Screen.kt>` |
| `<package.action>` | callback, navigation, side effect | `<Screen.kt>` |
| `<package.analytics>` | analytics helper, screen/click event | `<Analytics.kt>` |
| `<feature.orchestrator>` | 기능 trigger와 orchestration | `<Orchestrator.kt>` |
| `res/layout` 또는 `res/values` | layout, string, prompt, resource contract | `<resource file>` |

## Sub-Packages

서브모듈 내부가 여러 Gradle module 또는 책임별 package로 분리되어 있으면 여기에 하위 패키지와 문서 위치를 적는다.
도메인 KB이거나 단일 책임 모듈이면 생략할 수 있다.

| 하위 패키지 | 기준 코드 경로 | 책임 | 상세 문서 |
|---|---|---|---|
| `<sub-package-a>` | `<module>/<sub-package-a>/src/main/java/...` | `<책임>` | `<sub-package-a>/README.md` |
| `<sub-package-b>` | `<module>/<sub-package-b>/src/main/java/...` | `<책임>` | `<sub-package-b>/README.md` |

## Core Files And Roles

| 작업 영역 | 먼저 볼 파일 | 같이 볼 파일 |
|---|---|---|
| 진입 | `<file>` | `<file>` |
| 데이터 | `<file>` | `<file>` |
| 렌더링 | `<file>` | `<file>` |
| 액션 | `<file>` | `<file>` |
| analytics | `<file>` | `<file>` |
| 접근성 | `<file>` | `<file>` |

## File Responsibilities

| 파일 | 역할 | 주요 입력 | 주요 출력/효과 | 수정 시 함께 확인할 파일 |
|---|---|---|---|---|
| `<EntryFile.kt>` | `<화면 진입 또는 route 처리>` | `<Intent, argument>` | `<screen 생성, navigation>` | `<NavGraph.kt>, <Screen.kt>` |
| `<ViewModel.kt>` | `<상태 관리와 fetch orchestration>` | `<request, user intent>` | `<state, LiveData/Flow, effect>` | `<Repository.kt>, <Screen.kt>` |
| `<Repository.kt>` | `<remote/local data 조합>` | `<request>` | `<response/model>` | `<Dao.kt>, <Service.kt>` |
| `<Screen.kt>` | `<화면 state rendering과 UI event 연결>` | `<UiState>` | `<Composable tree, callbacks>` | `<ViewModel.kt>, component files>` |
| `<Dialog.kt>` | `<dialog state rendering과 confirm/cancel action 연결>` | `<dialog state>` | `<callback, state update>` | `<Screen.kt>, <ViewModel.kt>` |
| `<Analytics.kt>` | `<screen/click analytics side effect 처리>` | `<event context>` | `<analytics event>` | `<Screen.kt>, core analytics helper>` |

파일 역할은 “무엇을 하는 파일인지”보다 “작업 중 왜 이 파일을 봐야 하는지”가 드러나야 한다.
역할이 모호하면 AI가 잘못된 파일을 기준으로 코드를 생성하기 쉽다.
Core Files와 AI Reference Order에는 `데이터 모델/응답 매핑 파일`, `mapper`, `Composable`, `ViewModel`, `DAO`, `resource`의 실제 파일명을 채운다.
확인하지 못한 단계는 생략하지 말고 `미확인`으로 남겨 다음 검증 항목에 연결한다.

## File Inventory

전체 파일 목록은 `05-file-inventory.md`에 한 줄 역할로 인덱싱한다.
이 문서에는 핵심 파일만 상세히 적고, 나머지는 inventory에서 찾는다.

| 파일 인벤토리 | 역할 |
|---|---|
| `05-file-inventory.md` | 전체 파일 목록, 한 줄 역할, 언제 보는가, 핵심/보조 분류 |

## AI Reference Order

| 작업 유형 | 참조 순서 |
|---|---|
| 신규 화면/모듈 추가 | `00-agent-routing.md` -> `README.md` -> `00-structure-map.md` -> `package-reference/01-entry-screen.md` |
| API 필드 추가 | `00-agent-routing.md` -> `00-structure-map.md` -> `package-reference/02-data-viewmodel.md` |
| 신규 Composable/card/dialog 추가 | `00-agent-routing.md` -> `00-structure-map.md` -> 데이터/상태 파일(Core Files 데이터 행의 실제 파일명) -> Composable 파일(Core Files 렌더링 행의 실제 파일명) -> 실제 존재하면 `package-reference/03-rendering-action.md` |
| action/navigation 변경 | `00-agent-routing.md` -> `00-structure-map.md` -> `package-reference/03-rendering-action.md` |
| analytics 변경 | `00-agent-routing.md` -> `00-structure-map.md` -> analytics 문서 -> 도메인 rendering/action 문서 |
| package 이동 | `00-agent-routing.md` -> `00-structure-map.md` -> `04-files-checklist.md` -> `change-log.md` |

## Update Rules

구조가 바뀌면 함께 갱신한다.

- 루트 또는 패키지 `00-agent-routing.md`
- 이 문서
- `README.md`의 기준 패키지
- `package-reference/04-files-checklist.md`
- `00-change-index.md`
- 패키지 `change-log.md`
