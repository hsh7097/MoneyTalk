---
type: guide
title: KB 작성 워크플로우
description: 새 코드 분석 KB를 만들고 검증하고 갱신할 때 따를 단계별 절차를 설명한다.
tags: [kb-scaffold, workflow, authoring, validation, android]
resource: docs/kb-scaffold/03-authoring-workflow.md
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# KB 작성 워크플로우

> 역할:
> - KB 생성, 검증, 갱신 절차를 단계별 실행 순서로 정리한다.
> - 실제 KB를 작성하는 AI나 사람이 작업 전에 따라야 할 체크리스트로 사용한다.
> - 검증 방식, 샘플 작업, 갱신 기준이 바뀌면 이 파일과 change-log 템플릿을 함께 확인한다.

## 1. 작성 전 확인

새 KB를 만들기 전에 아래를 확인한다.

가능하면 새 세션 또는 하위 에이전트처럼 이전 대화 맥락이 없는 깨끗한 컨텍스트에서 시작한다.
기존 세션에서 이어서 작업하더라도 개인 설정이나 이전 대화의 추정 지식보다 `docs/kb-scaffold/`, `AGENTS.md`, 현재 코드에서 확인한 사실을 우선한다.
문장 스타일은 달라질 수 있으므로, 리뷰 기준은 문체가 아니라 최소 파일 계약, 코드 근거, 검증 가능성이다.

1. 대상이 도메인인지, 기능인지, 서브모듈인지 정한다.
2. 코드 기준 root path를 확인한다.
3. 팀 공유 KB인지 로컬 전용 KB인지 정한다.
4. 팀 공유 KB라면 `.claude/docs/kb/` 아래에 둘지 확인한다.
5. 실제 폴더 구조, Gradle module 구조, Kotlin/Java package 구조를 확인한다.
6. 핵심 파일을 entry/data/rendering/action/analytics/utility 기준으로 분류한다.
7. 각 핵심 파일의 역할, 책임, 함께 확인할 파일을 적는다.
8. 기존 문서가 있는지 확인한다.
9. 문서 상태를 `stub`, `draft`, `verified`, `stale` 중 하나로 정한다.
10. 이번 문서의 목표 수준을 정한다.
11. 검증할 샘플 작업을 정한다.

대표 코드 경로가 실제로 존재하지 않으면 KB를 만들지 않는다.
AI는 없는 source root를 기준으로 문서를 추정 생성하지 말고, 확인한 경로와 중단 사유를 보고한다.

목표 수준 예시:

- 신규 Compose 화면 추가 시 필요한 Activity/NavGraph/ViewModel 확장 지점 찾기
- SMS 파이프라인의 reader/filter/Fast Path/extraction 책임 구분
- 문자 파싱 기능의 trigger부터 DB 저장, 화면 refresh까지 end-to-end 흐름 정리
- 카테고리 분류 기능의 자동/수동 분류, repository, Gemini, custom category 연결 정리
- App Functions 노출 함수와 Repository/DAO 사용 위치 구분
- Home/History/Chat 화면 진입과 Compose state 흐름 정리

## 2. 1차 생성 절차

새 KB 루트를 만들 때는 루트 문서를 먼저 만든다.

1. `templates/kb-readme.md` 또는 기존 루트 README 형식을 기준으로 `README.md`를 만들고, KB 목적, 도메인/기능/서브모듈 목록, 먼저 볼 문서를 채운다.
2. `templates/agent-routing.md` 또는 기존 라우팅 형식을 기준으로 `00-agent-routing.md`를 만든다.
3. `templates/change-index.md` 또는 기존 change-index 형식을 기준으로 루트 변경 색인을 만든다. MoneyTalk 기존 KB는 `00-change-index.md`를 사용한다.
4. 필요하면 `templates/structure-map.md`를 기준으로 루트 구조 지도를 만든다. MoneyTalk 기존 KB는 `01-structure-map.md`를 사용한다.
5. 템플릿을 복사한 문서는 frontmatter의 `type`을 실제 문서 타입으로 바꾸고 템플릿 전용 필드는 제거한다.
6. 실제 repository path와 source set을 확인해 루트 구조와 라우팅 표를 채운다.
7. 아직 확인하지 못한 도메인/기능/서브모듈은 문서 링크를 미리 만들지 않고 미분류 또는 후보로 남긴다.

도메인, 기능, 서브모듈 패키지는 작게 시작한다.

1. 패키지 `README.md`를 만든다.
2. 패키지 `change-log.md`를 만든다.
3. README에 기준 코드 경로, 핵심 파일, 먼저 볼 문서, 현재 생략한 확장 문서를 적는다.
4. 루트 `00-agent-routing.md`에 이 패키지로 들어오는 파일 경로 기준 라우팅을 추가한다.
5. 루트 `00-change-index.md`에 패키지 경계 또는 라우팅 변경 색인을 남긴다.
6. 샘플 작업으로 AI가 필요한 문서를 찾는지 검증한다.

처음부터 모든 파일을 완벽히 채우지 않는다.
AI가 필요한 문서를 찾을 수 있는 구조를 먼저 만든다.

## 2.0 확장 파일을 만드는 기준

아래 조건이 확인되면 확장 파일을 만든다.

| 조건 | 추가 문서 |
|---|---|
| 폴더/source set/package 구조가 복잡하다 | `00-structure-map.md` 또는 `01-structure-map.md` |
| 파일 이름만으로 수정 위치를 좁히기 어렵다 | `05-file-inventory.md` |
| 화면 진입, 데이터, 렌더링/액션을 반복해서 나눠 봐야 한다 | `package-reference/` |
| 기능 trigger, data contract, extension point가 커졌다 | `01-feature-flow.md`, `02-data-contract.md`, `03-extension-points.md` |
| 리뷰 전 반복 체크가 필요하다 | `04-files-checklist.md` 또는 `package-reference/04-files-checklist.md` |

확장 파일을 만들면 README의 `먼저 볼 파일` 표에 역할과 참조 시점을 추가한다.
아직 만들지 않은 확장 파일 링크는 README에 쓰지 않는다.

## 2.1 구조 맵 작성 순서

KB에는 현재 코드 구조를 찾을 수 있는 구조 맵이 있어야 한다.
구조 맵은 AI가 작업 중 실제 파일을 탐색할 때 기준점으로 쓰는 문서다.

권장 작성 순서:

1. module root와 source set을 적는다.
2. Gradle module 또는 submodule 관계를 적는다.
3. Kotlin/Java package 구조를 책임별로 나눈다.
4. 핵심 파일을 entry, data, rendering, action, analytics, utility로 분류한다.
5. 각 파일이 담당하는 역할과 수정 시 함께 확인할 파일을 적는다.
6. 작업 유형별로 AI가 참조할 문서 순서를 적는다.
7. 구조 변경 시 갱신할 문서를 적는다.

예시:

```text
작업: 신규 거래 필터 추가
참조 순서:
1. 00-agent-routing.md
2. <domain>/README.md
3. <domain>/00-structure-map.md
4. <domain>/package-reference/03-rendering-action.md
5. <domain>/package-reference/02-data-viewmodel.md
```

## 2.2 라우팅 문서 작성 순서

KB가 자동화와 함께 쓰일 수 있다면 `00-agent-routing.md`를 초기에 만든다.
이 문서가 없으면 AI와 자동화가 변경 파일을 보고 어느 문서를 읽어야 하는지 매번 추론하게 된다.

권장 작성 순서:

1. 대표 코드 경로를 모은다.
2. 경로를 도메인/기능/서브모듈 패키지로 분류한다.
3. 각 분류가 읽어야 할 README와 package-reference를 지정한다.
4. 공통 영향 경로를 별도로 분리한다.
5. 패키지 이동 시 갱신할 문서를 명시한다.

예시:

```text
<app-module>/src/main/java/<package>/<domain>/**
→ <domain>/README.md
→ <domain>/package-reference/README.md

<app-module>/src/main/java/<package>/core/sms/**
→ sms-parsing/README.md
→ sms-pipeline/README.md
→ 관련 도메인의 package-reference/02-data-viewmodel.md

<app-module>/src/main/java/<package>/feature/home/data/*Category*
→ category-classification/README.md
→ finance-data/README.md

<app-module>/src/main/java/<package>/core/appfunctions/**
→ app-functions/README.md
→ finance-data/README.md
```

라우팅 문서는 상세 설명을 담는 곳이 아니다.
어떤 작업에서 어떤 문서를 읽을지 결정하는 표가 중심이다.

## 2.3 확장 파일 생성 순서

도메인 KB가 README와 change-log만으로 부족하면 아래 순서로 확장한다.

1. `<domain>/00-structure-map.md` 또는 기존 KB 관례에 맞는 구조 지도
2. `<domain>/05-file-inventory.md`
3. `<domain>/package-reference/README.md`
4. `<domain>/package-reference/01-entry-screen.md`
5. `<domain>/package-reference/02-data-viewmodel.md`
6. `<domain>/package-reference/03-rendering-action.md`
7. `<domain>/package-reference/04-files-checklist.md`

서브모듈 KB가 README와 change-log만으로 부족하면 아래 순서로 확장한다.

1. `<module>/00-structure-map.md` 또는 기존 KB 관례에 맞는 구조 지도
2. `<module>/01-purpose-architecture.md`
3. `<module>/02-how-to-use.md`
4. `<module>/03-extension-points.md`
5. `<module>/04-files-checklist.md`
6. `<module>/05-file-inventory.md`

기능 KB가 README와 change-log만으로 부족하면 아래 순서로 확장한다.

1. `<feature>/00-structure-map.md` 또는 기존 KB 관례에 맞는 구조 지도
2. `<feature>/01-feature-flow.md`
3. `<feature>/02-data-contract.md`
4. `<feature>/03-extension-points.md`
5. `<feature>/04-files-checklist.md`
6. `<feature>/05-file-inventory.md`

서브모듈 내부가 책임별 패키지로 나뉘면 하위 패키지도 만든다.

1. `<module>/<sub-package>/README.md`
2. `<module>/<sub-package>/00-structure-map.md`
3. `<module>/<sub-package>/change-log.md`
4. `<module>/<sub-package>/01-purpose-architecture.md`
5. `<module>/<sub-package>/02-how-to-use.md`
6. `<module>/<sub-package>/03-extension-points.md`
7. `<module>/<sub-package>/04-files-checklist.md`
8. `<module>/<sub-package>/05-file-inventory.md`

예: `sms-pipeline`의 실제 책임 단위가 reader, filter, fast-path, extraction이라면 `sms-pipeline/reader`, `sms-pipeline/filter`, `sms-pipeline/fast-path`, `sms-pipeline/extraction`을 만든다.
상위 `<module>/README.md`와 `<module>/00-structure-map.md`에는 하위 패키지로 내려가는 라우팅과 요약만 둔다.

도메인, 기능, 서브모듈 모두 `README.md`와 `change-log.md`는 시작 파일이다.
README는 인덱스, change-log는 변경 의도 추적 장치다.
그 외 문서는 패키지 복잡도와 검증 결과에 따라 확장한다.

README를 만들거나 확장 파일을 추가할 때는 각 문서의 역할을 README에 다시 기록한다.
README의 `먼저 볼 파일` 섹션은 단순 링크 목록이 아니라 아래 표를 사용한다.

```markdown
| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| `<file>.md` | `<이 파일이 설명하는 정보>` | `<이 작업/변경 키워드에서 읽음>` |
```

작성 기준:

1. `역할`에는 문서의 주제를 쓴다. 예: 구조 지도, 데이터 흐름, 렌더링/액션, 파일 인벤토리.
2. `언제 보는가`에는 AI의 작업 판단 기준을 쓴다. 예: 진입 흐름 변경, API 필드 추가, Composable 추가, analytics 수정.
3. 실제 존재하는 모든 패키지 문서는 README의 표에 한 번 이상 등장해야 한다.
4. 하위 책임 패키지가 있으면 상위 README에는 하위 README 역할을 쓰고, 세부 파일 역할은 하위 README에 둔다.
5. README와 실제 파일 목록이 다르면 README를 갱신한다.

## 2.4 파일 인벤토리 작성 기준

파일 수가 많거나 수정 위치를 자주 놓치는 도메인/기능/서브모듈/하위 책임 패키지는 `05-file-inventory.md`를 만든다.
목표는 모든 파일을 길게 설명하는 것이 아니라, AI가 수정 위치를 빠르게 좁히도록 전체 파일을 짧게 인덱싱하는 것이다.
작은 패키지는 “파일이 적음”을 명시하고 핵심 파일만 짧게 적는다.

작성 순서:

1. `rg --files <target-src>`로 실제 파일 목록을 만든다.
2. 폴더/package 기준으로 묶는다.
3. 각 파일에 한 줄 역할을 적는다.
4. “언제 보는가”를 작업 키워드로 적는다.
5. 핵심 파일, 핵심 후보, 보조 파일로 분류한다.
6. 핵심 파일만 `00-structure-map.md`, `03-extension-points.md`, `04-files-checklist.md`에서 상세 설명한다.

권장 표:

```markdown
| 파일 | 역할 | 언제 보는가 | 분류 | 함께 볼 파일 |
|---|---|---|---|---|
| `<path/File.kt>` | `<한 줄 역할>` | `<작업 키워드>` | `핵심` | `<related.kt>` |
```

분류 기준:

| 분류 | 기준 |
|---|---|
| 핵심 | 작업 시작점이거나 수정 빈도가 높고 side effect가 큰 파일 |
| 핵심 후보 | 자주 수정되지는 않지만 문제 발생 시 반드시 확인해야 하는 파일 |
| 보조 | 이름과 위치만 알면 충분한 helper/model 파일 |

파일 인벤토리가 있어도 모든 파일을 읽으라는 뜻은 아니다.
AI는 먼저 README, structure-map, checklist를 읽고, 파일 선택이 애매할 때 inventory에서 후보를 좁힌다.

## 2.5 Claude Code 문서 구조 연결 기준

저장소에 Claude Code용 문서 구조를 둘 때는 문서 위치를 아래처럼 나눈다.

| 내용 | 위치 |
|---|---|
| 프로젝트 진입점과 문서 인덱스 | `CLAUDE.md` |
| 항상 지킬 행동 규칙 | `.claude/guidelines.md` |
| 저장소 운영 문서 | `.claude/docs/*.md` |
| 코드 분석 KB | `.claude/docs/kb/` |
| 로컬 전용 자동화 KB | `docs/...` 또는 로컬 절대 경로 |

`CLAUDE.md`에는 상세 KB를 직접 쓰지 않는다.
새 KB를 추가하면 `CLAUDE.md` 또는 `.claude/docs/kb/README.md`에는 한 줄 인덱스만 추가한다.
KB 본문은 `@import`로 항상 로드하지 않고, `00-agent-routing.md`를 통해 필요한 문서만 읽는다.

## 2.6 상태 전환 기준

| 전환 | 조건 |
|---|---|
| `stub` -> `draft` | 실제 코드 경로를 일부 확인하고 최소 설명을 채움 |
| `draft` -> `verified` | 코드 경로, 파일 역할, 함께 볼 파일, 샘플 작업 검증이 완료됨 |
| `verified` -> `stale` | 패키지 이동, 파일 책임 변경, 주요 diff가 확인됨 |
| `stale` -> `verified` | 현재 코드 재확인 후 문서와 로그를 갱신함 |

상태가 바뀌면 YAML frontmatter의 `status`와 `timestamp`를 함께 갱신한다.
본문에 `> 상태:` 표기를 둔 템플릿은 frontmatter와 본문 상태가 어긋나지 않게 같이 수정한다.

## 3. 코드 분석 기준

분석할 때 우선순위:

1. module root와 source set
2. package 구조와 책임 분리
3. 파일 인벤토리에서 전체 파일 역할 확인
4. entry point
5. 기능 trigger 또는 ViewModel/receiver/App Function
6. Repository/DataSource/API/DAO
7. data contract와 저장 위치
8. UI state mapping
9. Composable/dialog/card 또는 외부 노출 함수
10. navigation/action/analytics/refresh side effect
11. 테스트/빌드/검증 명령

Android 화면 작업에서는 Compose + MVVM 흐름을 우선 본다.

```text
Activity/NavGraph
→ ViewModel
→ Repository/DataSource/API
→ Room DAO 또는 remote source
→ UI state
→ Composable
→ dialog/action/navigation/analytics
```

## 4. 검증 방식

KB는 문서 자체로는 품질을 증명하기 어렵다.
실제 작업을 AI에게 시켜보고 부족한 부분을 갱신한다.

검증 예시:

- 신규 History 필터 조건 추가 위치 찾기
- 신규 SMS sender regex Fast Path 룰 연결 위치 찾기
- 문자 파싱 결과가 DB와 화면에 반영되는 전체 경로 찾기
- 카테고리 자동 분류 실패 시 확인할 파일 순서 찾기
- agent가 월간 지출 요약을 읽는 App Function 경로 찾기
- Chat App Function을 추가할 때 function/reader/repository 중 어디를 수정하는지 확인
- Analytics 로그 추가 시 공통 helper와 도메인 문서를 각각 어디까지 읽는지 확인
- 공통 Compose 컴포넌트 사용법을 잘못 추론하지 않는지 확인

검증 후에는 로그에 남긴다.

```text
## YYYY-MM-DD

- 검증 작업:
- 사용한 프롬프트:
- AI가 읽은 문서:
- 성공한 점:
- 부족한 점:
- 갱신한 문서:
- 다음 검증:
```

## 5. 갱신 기준

KB를 갱신하는 경우:

- 코드 경로가 이동했다.
- 폴더 구조, package 구조, source set 구조가 바뀌었다.
- 핵심 파일의 책임이 다른 파일로 이동했다.
- 파일 역할 또는 함께 확인해야 하는 관련 파일이 바뀌었다.
- 신규 확장 지점이 생겼다.
- AI가 반복해서 잘못된 문서를 읽었다.
- AI가 없는 규칙을 추론했다.
- 문서가 현재 코드와 달랐다.
- PR/리뷰에서 반복 실수가 발견됐다.
- 자동화가 잘못된 도메인/서브모듈 문서를 읽었다.
- 패키지 이동으로 `00-agent-routing.md`의 경로 분류가 오래됐다.

KB를 갱신한 경우 반드시 로그도 갱신한다.
로그 없이 문서만 바꾸면 다음 작업자가 왜 바뀌었는지 추적할 수 없다.

KB를 갱신하지 않는 경우:

- 단순 계획 또는 아이디어만 있다.
- 코드로 확인되지 않았다.
- 특정 작업의 임시 우회다.
- 도메인 문서가 아니라 작업 스펙에 들어갈 내용이다.

## 5.1 패키지 이동이 있을 때

패키지 이동은 단순 문장 수정이 아니라 라우팅 계약 변경이다.

수정 순서:

1. 실제 diff에서 old path와 new path를 확인한다.
2. `00-agent-routing.md`의 경로 분류를 수정한다.
3. 루트 또는 패키지 `00-structure-map.md`의 폴더/패키지/파일 구조를 수정한다.
4. 해당 도메인/서브모듈 `README.md`의 기준 패키지를 수정한다.
5. `package-reference/04-files-checklist.md`의 빠른 파일 찾기를 수정한다.
6. `00-change-index.md`에 전체 색인을 남긴다.
7. 해당 패키지 `change-log.md`에 상세 변경 이력을 남긴다.
8. 예약 자동화 프롬프트의 `갱신 대상 분류`에도 같은 경로 규칙이 들어 있다면 같이 수정한다.

자동화 프롬프트 수정 여부는 결과 보고에 반드시 적는다.

## 5.2 로그 작성 기준

변경 로그는 모든 변경을 길게 설명하는 문서가 아니다.
다음 작업자가 “왜 이 문서가 바뀌었는지”와 “어느 문서를 더 봐야 하는지”를 찾기 위한 추적 장치다.

루트 `00-change-index.md`:

- 전체 KB 변경 색인
- 기준 ref/SHA, 작업 링크, PR 번호 등 추적 키
- 영향 도메인/서브모듈
- 갱신한 KB 목록
- 갱신하지 않은 이유를 짧게 기록

패키지 `change-log.md`:

- 해당 도메인/서브모듈 안의 상세 변경 이력
- 바뀐 코드 경로
- 갱신한 문서
- 의도적으로 갱신하지 않은 문서와 이유
- 다음 검증 작업

로그 위치 결정:

| 작업 | 로그 위치 |
|---|---|
| 루트 라우팅, 폴더 구조, 패키지 경계 변경 | `00-change-index.md` |
| 도메인 KB 본문, 구조 맵, package-reference 변경 | `<domain>/change-log.md` |
| 서브모듈 KB 본문, 구조 맵, API/확장 지점 문서 변경 | `<module>/change-log.md` |
| 서브모듈 하위 책임 패키지 변경 | `<module>/<sub-package>/change-log.md` |
| 파일 인벤토리 변경 | 해당 패키지 `change-log.md` |
| 라우팅 의미가 바뀌는 도메인/서브모듈 변경 | `00-change-index.md`와 해당 `change-log.md` |

로그를 남겨야 하는 변경:

- 신규 KB 패키지 생성
- 구조 맵 생성 또는 수정
- 패키지/클래스 이동
- 라우팅 규칙 변경
- package-reference 수정
- 자동화 프롬프트의 경로 분류 변경
- AI 검증 결과로 문서 보강

로그를 생략할 수 있는 변경:

- 오타 수정
- 깨진 링크 수정
- 문서 제목만 바꾸는 단순 정리

단, 오타 수정이라도 라우팅 의미가 바뀌면 로그를 남긴다.

## 6. PRD, 스펙, KB 분리

| 문서 | 질문 | 예시 |
|---|---|---|
| PRD | 무엇을 원하는가? | “이 버튼을 누르면 쿠폰 레이어가 떠야 한다.” |
| 스펙 | 어떻게 만들 것인가? | “A 화면에 B ViewModel event를 추가한다.” |
| KB | 현재 코드는 어떻게 되어 있는가? | “거래 수정 action은 `TransactionEditViewModel`과 `TransactionEditScreen`이 처리한다.” |

스펙이 잘못 나오면 스펙을 고친다.
KB가 현재 코드를 잘못 설명하면 KB를 고친다.
둘을 섞어서 “앞으로 이렇게 만들 예정”을 KB에 넣지 않는다.

## 6.1 서브모듈과 도메인 경계 검증

서브모듈 KB를 작성할 때는 아래 질문을 통과해야 한다.

- 이 내용이 특정 도메인 이름 없이도 설명되는가?
- 이 내용이 모듈의 public API, extension point, 내부 파일 역할인가?
- 특정 도메인의 비즈니스 조건이 들어가 있다면 도메인 KB로 옮겼는가?
- 도메인별 예외는 링크만 남기고 상세 설명을 복사하지 않았는가?

예시:

| 내용 | 위치 |
|---|---|
| `SmsSyncCoordinator`가 PreFilter, IncomeFilter, Fast Path, Pipeline을 순서대로 호출한다 | 서브모듈 `sms-pipeline` KB |
| History 화면의 필터 조건이 어떤 state와 Composable에 연결된다 | History 도메인 KB |
| Firebase Analytics helper의 공통 전송 API | 서브모듈 `analytics` 또는 `core` KB |
| Chat 화면에서 특정 질문 유형이 어떤 credit 정책을 타는지 | Chat 도메인 KB |

## 7. 공유 전 체크리스트

- [ ] 루트 README만 읽어도 하위 문서 위치를 알 수 있는가?
- [ ] 폴더 구조, package 구조, 핵심 파일 위치가 구조 맵에 들어 있는가?
- [ ] 각 핵심 파일의 역할과 함께 확인할 파일이 명확한가?
- [ ] 작업 유형별 AI 참조 순서가 명시되어 있는가?
- [ ] 도메인 KB와 서브모듈 KB의 책임이 섞이지 않았는가?
- [ ] 변경 로그가 있는가?
- [ ] 이번 변경이 정해진 로그 위치에 기록됐는가?
- [ ] 도메인에는 `README.md`, `00-structure-map.md`, `05-file-inventory.md`, `change-log.md`, `package-reference/`가 있는가?
- [ ] 서브모듈에는 `README.md`, `00-structure-map.md`, `01-purpose-architecture.md`, `02-how-to-use.md`, `03-extension-points.md`, `04-files-checklist.md`, `05-file-inventory.md`, `change-log.md`가 있는가?
- [ ] 서브모듈 KB에 특정 도메인 의존 정보가 들어가지 않았는가?
- [ ] 실제 코드 경로가 들어 있는가?
- [ ] 추정 표현을 제거했는가?
- [ ] PRD/스펙/미래 목표가 KB에 섞이지 않았는가?
- [ ] 샘플 작업으로 한 번 이상 검증했는가?
- [ ] 사람이 보기 좋은 문서가 아니라 AI가 선택적으로 읽기 좋은 문서인가?
