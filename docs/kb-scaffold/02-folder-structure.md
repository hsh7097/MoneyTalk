---
type: guide
title: KB 폴더 구조
description: 루트, 도메인, 서브모듈, 하위 책임 패키지, 로그 문서의 권장 배치를 정의한다.
tags: [kb-scaffold, folder-structure, domain, module, android]
resource: docs/kb-scaffold/02-folder-structure.md
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# KB 폴더 구조

## 1. 기본 구조

권장 구조는 기존 화면 KB처럼 상위 KB 패키지 아래에 도메인별 패키지를 바로 두는 방식이다.
팀 공유 하네스에서는 `.claude/docs/kb/` 아래에 같은 구조를 둘 수 있다.

```text
.claude/docs/kb/<kb-name>/
├── README.md
├── 00-agent-routing.md
├── 00-change-index.md
├── 01-architecture.md
├── 01-structure-map.md
├── 02-development-common.md
├── home/
├── history/
├── chat/
├── settings/
├── transaction-edit/
├── sms-parsing/
├── category-classification/
├── sms-pipeline/
├── app-functions/
├── notification/
├── finance-data/
└── database/
```

로컬 전용 또는 자동화 전용 KB는 기존처럼 `docs/<kb-name>/` 또는 절대 경로 기반 로컬 문서로 둘 수 있다.
단, 공식 팀 공유 진입점은 `CLAUDE.md`와 `.claude/docs/kb/README.md`에 둔다.

도메인 패키지와 서브모듈 패키지는 같은 루트 아래에 둘 수 있다.
중요한 것은 “문서가 어디에 있느냐”보다 “도메인 KB와 서브모듈 KB의 책임을 섞지 않는 것”이다.

대안으로 `domains/`, `modules/`를 한 단계 더 둘 수 있지만, 대형 Android 프로젝트처럼 루트 README와 `00-agent-routing.md`가 강한 인덱스 역할을 하는 경우에는 도메인 패키지를 루트 바로 아래에 두는 편이 찾기 쉽다.

```text
docs/<kb-name>/
├── domains/
│   ├── home/
│   ├── history/
│   └── chat/
├── features/
│   ├── sms-parsing/
│   ├── category-classification/
│   └── app-functions/
└── modules/
    ├── sms-pipeline/
    ├── app-functions/
    └── finance-data/
```

두 방식 중 하나를 고르되, 한 KB 안에서는 섞지 않는다.

## 2. 상위 KB 패키지와 도메인 패키지

상위 KB 패키지는 전체 인덱스다.
기존 화면 KB의 `docs/<screen-reference>`처럼 루트에는 라우팅과 공통 기준만 둔다.

상위 KB 패키지에 둘 문서:

- `README.md`
- `00-agent-routing.md`
- `00-change-index.md`
- `01-structure-map.md`
- `01-architecture.md`
- `02-navigation.md` 또는 `02-development-common.md`
- `03-development-common.md`
- `04-app-policy-environment.md`

도메인 패키지는 특정 화면/업무 흐름의 상세 설명을 가진다.

도메인 패키지 예시:

```text
history/
├── README.md
├── 00-structure-map.md
├── 05-file-inventory.md
├── change-log.md
├── 1-list-calendar.md
├── 2-filter-dialog.md
├── 3-detail-edit.md
└── package-reference/
    ├── README.md
    ├── 01-entry-screen.md
    ├── 02-data-viewmodel.md
    ├── 03-rendering-action.md
    └── 04-files-checklist.md
```

도메인 패키지 규칙:

- `README.md`는 해당 도메인의 진입점이다.
- `00-structure-map.md`는 해당 도메인의 폴더 구조, 패키지 구조, 주요 파일과 AI 참조 순서를 설명한다.
- `change-log.md`는 해당 도메인 KB 수정 이력을 기록한다.
- 화면별 번호 문서는 화면/기능 단위 설명이다.
- `package-reference/`는 개발자가 수정 위치를 찾기 위한 상세 문서다.
- 도메인 내부 변경 로그는 `change-log.md`에 남긴다.
- 루트 `00-agent-routing.md`는 어떤 변경 파일이 어떤 도메인 패키지를 읽어야 하는지 연결한다.

## 3. 루트 문서

### README.md

전체 인덱스다.

포함할 내용:

- 문서 상태: `stub`, `draft`, `verified`, `stale`
- KB 목적
- 폴더 구조
- 도메인/서브모듈 바로가기
- 작업 시작 시 읽을 문서
- 각 문서가 어떤 파일인지 설명하는 `문서 / 역할 / 언제 보는가` 표
- YAML frontmatter
- 유지보수 규칙

README의 `먼저 볼 파일`은 단순 번호 목록으로 작성하지 않는다.
AI가 README만 보고 다음에 열 문서를 고를 수 있도록 아래 형식을 기본으로 한다.

```markdown
| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| `00-structure-map.md` | 폴더, 패키지, 핵심 파일, AI 참조 순서를 정리한다. | 변경 파일이 어느 책임에 속하는지 판단할 때 본다. |
| `05-file-inventory.md` | 파일별 역할과 함께 볼 파일을 인덱싱한다. | 파일 이름만으로 수정 후보가 애매할 때 본다. |
```

### 00-agent-routing.md

AI가 변경 파일 경로 또는 작업 키워드로 읽을 문서를 고르는 라우팅 문서다.

포함할 내용:

- 문서 상태
- 경로별 참조 문서 표
- 도메인별 참조 우선순위
- 공통 영향이 있을 때 읽을 문서
- 전체 KB를 매번 읽지 말라는 규칙

### 00-change-index.md

KB 변경 색인이다.
상세 설명을 길게 누적하지 않는다.

포함할 내용:

- 날짜
- 기준 commit/ref 또는 작업 링크
- 영향 영역
- 갱신한 문서
- 갱신하지 않은 이유

### 문서 상태

각 KB 문서 상단에는 상태를 둔다.

```markdown
> 상태: draft
> 기준: 2026-07-03 현재 코드 확인
```

상태 기준:

| 상태 | 의미 |
|---|---|
| `stub` | 자리만 있고 구현 사실로 쓰면 안 됨 |
| `draft` | 일부 코드 확인. 작업 전 재검증 필요 |
| `verified` | 코드 경로와 파일 역할 확인 완료 |
| `stale` | 현재 코드와 다를 수 있어 재검증 필요 |

문서 상태는 YAML frontmatter의 `status` 필드에 우선 기록한다.
사람이 바로 읽어야 하는 템플릿 문서에는 기존처럼 본문 상단의 `> 상태:` 표기를 함께 둘 수 있다.

### 01-structure-map.md

KB 루트 기준의 코드 구조 지도다.
`00-agent-routing.md`가 “어떤 문서를 읽을지”를 고르는 표라면, `01-structure-map.md`는 “실제 코드가 어디에 있고 어떤 폴더/패키지/파일이 어떤 책임인지”를 보여준다.

포함할 내용:

- repository 또는 module root path
- source set 구조
- Gradle module 구조
- Kotlin/Java package 구조
- 도메인/서브모듈별 주요 파일 목록
- 각 주요 파일의 역할과 책임
- 파일 수정 시 함께 확인할 관련 파일
- entry, data, rendering, action, analytics, utility 파일의 위치
- 작업 유형별 AI 참조 순서
- 구조가 바뀌었을 때 갱신해야 할 KB 문서

예시:

```text
source root:
<app-module>/src/main/java/<package>/<domain>/

package groups:
- activity/navigation: <DomainActivity>, <NavGraph route>
- viewmodel: <DomainViewModel>
- data: repository, datasource, service
- rendering: Compose screen, card, dialog, filter component

file roles:
- <DomainActivity>: 도메인 진입 Activity
- <DomainViewModel>: 도메인 상태와 API fetch orchestration
- <DomainScreen>: Compose container와 하위 컴포넌트 연결

AI reference:
- 신규 거래 카드 UI 추가: 00-agent-routing -> history/README -> history/00-structure-map -> package-reference/03-rendering-action
- DB 필드 추가: 00-agent-routing -> finance-data/00-structure-map -> 영향 도메인의 package-reference/02-data-viewmodel
```

## 4. 도메인 KB

도메인은 Home, History, Chat, Settings처럼 화면/업무 흐름을 기준으로 나눈다.

권장 구조:

```text
<domain>/
├── README.md
├── 00-structure-map.md
├── change-log.md
├── 1-entry.md
├── 2-main-flow.md
├── 3-sub-flow.md
└── package-reference/
    ├── README.md
    ├── 01-entry-screen.md
    ├── 02-data-viewmodel.md
    ├── 03-rendering-action.md
    └── 04-files-checklist.md
```

도메인 KB에 들어갈 내용:

- 현재 도메인의 폴더 구조
- Kotlin/Java package 구조
- README에서 각 문서가 어떤 파일인지 설명하는 라우팅 표
- 핵심 파일과 책임
- 파일 간 호출/참조 관계
- 파일 수정 시 함께 볼 관련 파일
- 작업 유형별 AI 참조 순서
- 화면 진입 흐름
- Activity/Composable 연결
- ViewModel/Repository/DataSource/API 연결
- Compose state/action/navigation/analytics 흐름
- 반복 실수와 수정 전 체크리스트

도메인 KB 시작 파일과 확장 파일:

| 파일 | 역할 |
|---|---|
| `README.md` | 도메인 인덱스. 작업자가 이 도메인에서 먼저 볼 문서와 기준 패키지를 찾는다 |
| `change-log.md` | 도메인 KB 변경 상세 로그 |
| `00-structure-map.md` | 확장 파일. 도메인 폴더/패키지/파일 역할 지도. AI가 실제 파일을 찾는 기준이다 |
| `package-reference/README.md` | 세부 개발 문서 인덱스 |
| `package-reference/01-entry-screen.md` | Activity, NavGraph, lifecycle, permission, screen 진입 |
| `package-reference/02-data-viewmodel.md` | ViewModel, Repository, DataSource, API, model, cache/paging |
| `package-reference/03-rendering-action.md` | Composable, state, dialog, action, navigation, analytics |
| `package-reference/04-files-checklist.md` | 신규/수정 작업 전 빠른 파일 찾기와 검증 질문 |
| `05-file-inventory.md` | 확장 파일. 도메인 전체 파일의 한 줄 역할과 참조 시점 인덱스 |

도메인 KB에 넣지 않을 내용:

- SMS 파이프라인 구현 원리 전체
- App Functions 공통 구현 전체
- Room Database/DAO 전체 설명
- 다른 도메인에만 적용되는 예외

## 5. 기능 KB

기능 KB는 화면 하나나 모듈 하나로 끝나지 않는 사용자 기능 흐름을 설명한다.
예를 들어 문자 파싱은 `MainViewModel`, `core/sms`, `core/sync`, `core/database`, 화면 refresh까지 이어지고, 카테고리 분류는 Home/History/Settings, Repository, Gemini, StoreEmbedding, custom category까지 이어질 수 있다.

후보:

- `sms-parsing`
- `category-classification`
- `app-functions`
- `backup-restore`
- `reward-credit`
- `store-rule-sync`

권장 구조:

```text
<feature>/
├── README.md
├── 00-structure-map.md
├── 01-feature-flow.md
├── 02-data-contract.md
├── 03-extension-points.md
├── 04-files-checklist.md
├── 05-file-inventory.md
└── change-log.md
```

기능 KB에 들어갈 내용:

- 기능 trigger
- orchestration entry point
- 관련 화면/도메인
- 관련 서브모듈
- data read/write 흐름
- DB/API/App Function contract
- UI refresh 또는 side effect
- 실패/권한/비용 정책
- 검증할 샘플 시나리오

기능 KB 시작 파일과 확장 파일:

| 파일 | 역할 |
|---|---|
| `README.md` | 기능 인덱스. 어떤 화면/모듈을 가로지르는지와 먼저 볼 문서를 제공한다 |
| `change-log.md` | 기능 KB 변경 상세 로그 |
| `00-structure-map.md` | 확장 파일. 기능에 참여하는 화면, ViewModel, Repository, 서브모듈, DB 파일 지도 |
| `01-feature-flow.md` | trigger부터 결과 반영까지 end-to-end 흐름 |
| `02-data-contract.md` | 주요 input/output model, DB/API/App Function contract |
| `03-extension-points.md` | 기능 확장 시 수정 지점과 책임 경계 |
| `04-files-checklist.md` | 기능 수정 전후 확인 파일과 검증 질문 |
| `05-file-inventory.md` | 기능 관련 전체 파일의 한 줄 역할 인덱스 |

기능 KB와 서브모듈 KB의 경계:

- 기능 KB는 “사용자 기능이 어떻게 끝까지 흐르는가”를 설명한다.
- 서브모듈 KB는 “공통 구현체가 어떤 API와 내부 구조를 갖는가”를 설명한다.
- 기능 KB에는 필요한 서브모듈 링크를 두되, 서브모듈 내부 구현 전체를 복사하지 않는다.
- 서브모듈 KB에는 특정 기능의 비즈니스 조건을 복사하지 않는다.

예시:

| 질문 | 먼저 볼 KB |
|---|---|
| 문자를 읽어 거래로 저장하기까지 어디를 보나? | `sms-parsing/README.md` |
| SMS parser 내부 단계가 어떻게 나뉘나? | `sms-pipeline/README.md` |
| 카테고리 자동 분류가 어디서 결정되나? | `category-classification/README.md` |
| 가게명 임베딩 repository 내부 파일은 어디인가? | `finance-data/README.md` |
| agent가 앱 데이터를 읽는 함수는 어디인가? | `app-functions/README.md` |

## 6. 서브모듈 KB

서브모듈은 여러 도메인 또는 앱에서 공통으로 쓰는 코드 단위다.

후보:

- `core/sms`
- `core/appfunctions`
- `core/database`
- `core/notification`
- `core/sync`
- `core/ui`
- `core/util`

권장 구조:

```text
<module>/
├── README.md
├── 00-structure-map.md
├── 01-purpose-architecture.md
├── 02-how-to-use.md
├── 03-extension-points.md
├── 04-files-checklist.md
├── 05-file-inventory.md
└── change-log.md
```

서브모듈 내부가 여러 책임 패키지로 분리되어 있으면 docs도 같은 책임 단위로 하위 패키지를 만든다.
예를 들어 `core/sms`가 reader, filter, fast path, pipeline, extractor, rule sync 책임으로 나뉜다면 `sms-pipeline/` KB도 해당 단위의 하위 패키지를 둘 수 있다.

```text
<module>/
├── README.md
├── 00-structure-map.md
├── 01-purpose-architecture.md
├── 02-how-to-use.md
├── 03-extension-points.md
├── 04-files-checklist.md
├── change-log.md
├── <sub-package-a>/
│   ├── README.md
│   ├── 00-structure-map.md
│   ├── 01-purpose-architecture.md
│   ├── 02-how-to-use.md
│   ├── 03-extension-points.md
│   ├── 04-files-checklist.md
│   ├── 05-file-inventory.md
│   └── change-log.md
└── <sub-package-b>/
    ├── README.md
    ├── 00-structure-map.md
    ├── 01-purpose-architecture.md
    ├── 02-how-to-use.md
    ├── 03-extension-points.md
    ├── 04-files-checklist.md
    ├── 05-file-inventory.md
    └── change-log.md
```

SMS pipeline 예시:

```text
sms-pipeline/
├── README.md
├── 00-structure-map.md
├── 01-purpose-architecture.md
├── 02-how-to-use.md
├── 03-extension-points.md
├── 04-files-checklist.md
├── 05-file-inventory.md
├── change-log.md
├── reader/
├── filter/
├── fast-path/
└── extraction/
```

상위 `sms-pipeline/`은 전체 모듈 인덱스와 하위 패키지 라우팅을 담당하고, `reader/`, `filter/`, `fast-path/`, `extraction/`은 각 책임 패키지의 상세 구조와 파일 역할을 담당한다.
하위 패키지가 생기면 각 하위 패키지도 `README.md`와 `change-log.md`로 시작한다.
구조가 복잡하거나 AI가 수정 위치를 놓치면 `00-structure-map.md`, 목적/사용법/확장 지점, 파일 체크리스트, 파일 인벤토리를 확장한다.

서브모듈 KB에 들어갈 내용:

- 모듈의 목적
- 모듈 폴더 구조와 Gradle module 구조
- Kotlin/Java package 구조
- 내부 책임 패키지 목록과 각 패키지의 역할
- 내부 책임 패키지별 README/구조 지도 위치
- public API, 확장 지점, 핵심 파일 위치
- 핵심 파일별 책임과 함께 확인할 관련 파일
- 전체 파일 인벤토리와 각 파일의 한 줄 역할
- 작업 유형별 AI 참조 순서
- 의존성 방향
- 주요 public API 또는 확장 지점
- 도메인에서 가져다 쓰는 방식
- 하면 안 되는 사용 방식
- 대표 파일 체크리스트

서브모듈 KB 시작 파일과 확장 파일:

| 파일 | 역할 |
|---|---|
| `README.md` | 서브모듈 인덱스. 모듈 목적, 주요 패키지, 읽을 문서를 제공한다 |
| `change-log.md` | 서브모듈 KB 변경 상세 로그 |
| `00-structure-map.md` | 확장 파일. 모듈 폴더/Gradle/source set/package/파일 역할 지도 |
| `01-purpose-architecture.md` | 모듈의 목적, 책임, 내부 구조, 의존성 방향 |
| `02-how-to-use.md` | 도메인 또는 consumer가 모듈을 사용하는 일반 방법 |
| `03-extension-points.md` | public API, extension point, 확장 시 지켜야 할 계약 |
| `04-files-checklist.md` | 모듈 수정 전 확인할 파일과 검증 질문 |
| `05-file-inventory.md` | 서브모듈 전체 파일을 한 줄 역할로 인덱싱 |

서브모듈 내부 책임 패키지 시작 파일과 확장 파일:

| 파일 | 역할 |
|---|---|
| `<module>/<sub-package>/README.md` | 하위 책임 패키지 인덱스. 예: `sms-pipeline/reader`, `sms-pipeline/fast-path` |
| `<module>/<sub-package>/change-log.md` | 하위 패키지 KB 변경 상세 로그 |
| `<module>/<sub-package>/00-structure-map.md` | 확장 파일. 하위 패키지 folder/package/source/file role 지도 |
| `<module>/<sub-package>/01-purpose-architecture.md` | 하위 패키지 목적과 내부 흐름 |
| `<module>/<sub-package>/02-how-to-use.md` | 하위 패키지 사용 방법 |
| `<module>/<sub-package>/03-extension-points.md` | 하위 패키지 확장 지점 |
| `<module>/<sub-package>/04-files-checklist.md` | 하위 패키지 수정 전 확인 파일 |
| `<module>/<sub-package>/05-file-inventory.md` | 하위 패키지 전체 파일의 한 줄 역할 인덱스 |

파일 정리 깊이 기준:

| 대상 | 문서화 깊이 | 위치 |
|---|---|---|
| 폴더/package | 책임과 대표 파일을 설명 | `00-structure-map.md` |
| 핵심 파일 | 역할, 주요 입력/출력, 함께 확인할 파일까지 설명 | `00-structure-map.md`, `03-extension-points.md`, `04-files-checklist.md` |
| 전체 파일 | 한 줄 역할과 “언제 보는가”만 인덱싱 | `05-file-inventory.md` |
| 복잡하거나 자주 수정되는 파일 | 필요하면 별도 상세 문서로 승격 | 하위 패키지 내부 추가 문서 |

`05-file-inventory.md`는 파일 수가 많거나 책임이 애매한 도메인/기능/서브모듈/하위 책임 패키지에 둔다.
작은 패키지는 README의 핵심 파일 표로 시작할 수 있다.

특히 아래 경우에는 파일 인벤토리의 정확도가 더 중요하다.

- 한 패키지에 파일이 많아 폴더 요약만으로 수정 위치를 찾기 어렵다.
- AI가 자주 엉뚱한 파일을 먼저 여는 영역이다.
- 핵심 파일은 아니지만 이름만으로 역할을 알기 어려운 파일이 많다.
- 파일 이동이나 책임 변경이 잦아 인덱스가 필요하다.

`05-file-inventory.md`에 넣을 내용:

- 파일 경로
- 한 줄 역할
- 언제 보는지
- 핵심 파일 여부
- 함께 확인할 문서 또는 파일

예시:

```markdown
| 파일 | 역할 | 언제 보는가 | 분류 |
|---|---|---|---|
| `core/sms/SmsSyncCoordinator.kt` | SMS 배치 파싱 외부 진입점 | 동기화/파싱 순서 변경 | 핵심 |
| `core/sms/SmsRegexRuleMatcher.kt` | sender regex Fast Path 매칭 | 룰 매칭/우회 문제 | 핵심 후보 |
| `core/appfunctions/MoneyTalkFinanceAppFunctions.kt` | 금융 조회/수정 App Functions | agent 노출 함수 변경 | 핵심 후보 |
| `core/database/AppDatabase.kt` | Room database 정의와 migration 기준 | DB schema 변경 | 핵심 |
```

모든 파일을 긴 문장으로 설명하지 않는다.
전체 파일은 인벤토리에서 짧게 찾을 수 있게 하고, 실제 작업에 자주 걸리는 핵심 파일만 상세 문서에서 깊게 설명한다.

서브모듈 KB에 넣지 않을 내용:

- 특정 도메인의 비즈니스 동작 전체
- 특정 화면만의 analytics 예외 전체
- 특정 도메인의 API field mapping
- 특정 도메인의 Compose 상태 조합 순서
- 미래 재설계 목표

서브모듈 KB는 상위 도메인에 의존하는 내용을 소유하지 않는다.
도메인별 특수 사용법이 필요하면 서브모듈 문서에는 링크만 두고, 실제 설명은 해당 도메인 KB에 둔다.

## 7. 로그 파일

각 KB 묶음은 변경 로그를 가진다.
변경사항이 생기면 해당 변경을 반드시 로그에 남긴다.

로그는 두 단계로 나눈다.

| 로그 | 위치 | 역할 |
|---|---|---|
| 전체 변경 색인 | `00-change-index.md` | 전체 KB에서 어떤 영역이 바뀌었는지 빠르게 찾는 색인 |
| 패키지 변경 로그 | `<domain>/change-log.md`, `<feature>/change-log.md`, `<module>/change-log.md` | 해당 도메인/기능/서브모듈의 상세 변경 이력 |

`00-change-index.md`는 기존 화면 KB처럼 짧은 색인 역할만 한다.
긴 설명은 패키지별 `change-log.md` 또는 실제 갱신 문서에 둔다.

로그에 남길 내용:

- 언제 갱신했는가
- 어떤 코드/diff/작업을 근거로 갱신했는가
- 어떤 문서를 바꿨는가
- 갱신하지 않은 항목은 왜 제외했는가
- 다음에 검증할 지점은 무엇인가

## 8. 로그 작성 규칙

변경이 발생하면 아래 순서로 기록한다.

1. 루트 `00-change-index.md`에 한 줄을 추가한다.
2. 변경된 도메인/서브모듈 패키지의 `change-log.md`에 상세 내용을 추가한다.
3. 구조가 바뀌었다면 루트 또는 패키지 `00-structure-map.md`를 갱신한다.
4. 실제 변경한 문서에는 필요한 내용만 반영한다.

예시:

```markdown
<!-- 00-change-index.md -->
| 날짜 | 기준 | 영향 영역 | KB 반영 |
|---|---|---|---|
| 2026-07-03 | `abc1234` | History 신규 filter routing | `00-agent-routing.md`, `history/package-reference/03-rendering-action.md` |
```

```markdown
<!-- history/change-log.md -->
## 2026-07-03

- 변경 근거: `HistoryFilter` 신규 조건 추가
- 갱신한 문서:
  - `history/package-reference/03-rendering-action.md`
  - `history/package-reference/04-files-checklist.md`
- 갱신하지 않은 문서:
  - `history/package-reference/01-entry-screen.md` — 화면 진입 구조 변화 없음
- 다음 검증: 신규 필터 조건으로 목록/달력/상세 다이얼로그 영향 확인
```

로그는 완성된 산출물보다 중요하다.
KB는 한 번에 완성되지 않고, 실제 작업을 시켜보며 회귀적으로 보강된다.
수동으로 KB를 수정할 때도 같은 규칙을 적용한다.
문서 본문만 수정하고 로그를 남기지 않으면 다음 작업자가 변경 의도를 추적할 수 없다.
