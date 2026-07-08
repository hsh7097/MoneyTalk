---
type: guide
title: 자동화 패키지 라우팅 가이드
description: 예약 자동화가 원격 diff를 KB 패키지와 문서로 라우팅하고 변경 로그를 남기는 기준을 정의한다.
tags: [kb-scaffold, automation, routing, package, android]
resource: docs/kb-scaffold/04-automation-package-routing.md
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# 자동화 패키지/라우팅 갱신 가이드

이 문서는 예약 자동화가 KB를 갱신할 때 패키지/경로 정보를 어떻게 유지해야 하는지 정의한다.
팀원이 새 KB를 만들거나 기존 KB를 운영 자동화에 연결할 때 참고한다.

## 1. 자동화의 역할

예약 자동화는 매일 또는 정해진 주기로 원격 기준 브랜치의 변경사항을 확인한다.
변경 파일 경로를 기준으로 영향 도메인/기능/서브모듈을 분류하고, 필요한 KB 문서만 읽고 갱신한다.

자동화가 해야 하는 일:

1. 원격 기준 ref를 찾는다.
2. 직전 실행 SHA와 현재 SHA를 비교한다.
3. 변경 파일 목록을 수집한다.
4. 변경 파일 경로를 KB 패키지로 라우팅한다.
5. 필요한 README와 package-reference만 읽는다.
6. 실제 코드 diff로 확인된 내용만 문서에 반영한다.
7. 갱신한 문서와 갱신하지 않은 이유를 기록한다.
8. 문서 상태가 `stub`, `draft`, `stale`이면 현재 코드로 재검증하고 상태를 갱신한다.

자동화가 하면 안 되는 일:

- 전체 KB를 매번 전부 읽기
- KB 전체를 항상 로드하도록 `CLAUDE.md`에 직접 붙이기
- 코드 근거 없이 패키지 구조 추정하기
- 원격 ref에 `docs/`가 없다는 이유로 로컬 KB 갱신을 중단하기
- 앱 소스 코드 수정하기
- build.gradle, proguard, signing config, 새 라이브러리 수정하기

## 2. 자동화가 기대하는 KB 구조

자동화 대상 KB는 아래 구조를 갖추는 것이 좋다.
팀 공유 KB는 `.claude/docs/kb/` 아래에 둘 수 있고, 로컬 전용 KB는 기존 `docs/...` 경로를 사용할 수 있다.

```text
docs/<kb-name>/
├── README.md
├── 00-agent-routing.md
├── 00-change-index.md
├── 01-structure-map.md
├── <domain>/
│   ├── README.md
│   ├── 00-structure-map.md
│   ├── 05-file-inventory.md
│   ├── change-log.md
│   └── package-reference/
│       ├── README.md
│       ├── 01-entry-screen.md
│       ├── 02-data-viewmodel.md
│       ├── 03-rendering-action.md
│       └── 04-files-checklist.md
├── <feature>/
│   ├── README.md
│   ├── 00-structure-map.md
│   ├── 01-feature-flow.md
│   ├── 02-data-contract.md
│   ├── 03-extension-points.md
│   ├── 04-files-checklist.md
│   ├── 05-file-inventory.md
│   └── change-log.md
└── <module>/
    ├── README.md
    ├── 00-structure-map.md
    ├── 01-purpose-architecture.md
    ├── 02-how-to-use.md
    ├── 03-extension-points.md
    ├── 04-files-checklist.md
    ├── 05-file-inventory.md
    └── change-log.md
```

자동화 참조 파일:

아래 표는 자동화가 사용할 수 있는 문서 목록이다.
패키지 내부 확장 파일은 실제 존재할 때만 읽고, 없는 문서는 추정 생성하지 않는다.

| 파일 | 자동화에서 쓰는 용도 |
|---|---|
| `README.md` | KB 전체 인덱스와 도메인/기능/서브모듈 목록 확인 |
| `00-agent-routing.md` | 변경 파일 경로를 읽을 문서로 변환 |
| `00-change-index.md` | 반영 이력과 skip 사유 기록 |
| `01-structure-map.md` | 루트 기준 폴더/패키지/파일 구조 확인 |
| `<domain>/README.md` | 도메인 진입점 |
| `<domain>/00-structure-map.md` | 도메인 폴더/패키지/파일 역할 확인 |
| `<domain>/05-file-inventory.md` | 도메인 전체 파일 인덱스와 수정 후보 확인 |
| `<domain>/change-log.md` | 도메인 상세 변경 이력 |
| `<domain>/package-reference/README.md` | 상세 개발 문서 진입점 |
| `<domain>/package-reference/01-entry-screen.md` | 화면 진입/routing/lifecycle 변경 확인 |
| `<domain>/package-reference/02-data-viewmodel.md` | ViewModel/Repository/DataSource/API 변경 확인 |
| `<domain>/package-reference/03-rendering-action.md` | rendering/action/analytics 변경 확인 |
| `<domain>/package-reference/04-files-checklist.md` | 도메인 수정 전후 검증 질문 확인 |
| `<feature>/README.md` | 기능 end-to-end 흐름 진입점 |
| `<feature>/00-structure-map.md` | 기능 관련 화면/모듈/DB/App Function 파일 지도 |
| `<feature>/01-feature-flow.md` | trigger부터 결과 반영까지 기능 흐름 확인 |
| `<feature>/02-data-contract.md` | input/output model, DB/API/App Function contract 확인 |
| `<feature>/03-extension-points.md` | 기능 확장 지점 확인 |
| `<feature>/04-files-checklist.md` | 기능 수정 전후 검증 질문 확인 |
| `<feature>/05-file-inventory.md` | 기능 관련 파일 인덱스와 수정 후보 확인 |
| `<feature>/change-log.md` | 기능 상세 변경 이력 |
| `<module>/README.md` | 서브모듈 진입점 |
| `<module>/00-structure-map.md` | 서브모듈 폴더/패키지/파일 역할 확인 |
| `<module>/01-purpose-architecture.md` | 서브모듈 목적, 책임, 의존성 방향 확인 |
| `<module>/02-how-to-use.md` | consumer 사용 방법과 작업 유형별 참조 순서 확인 |
| `<module>/03-extension-points.md` | public API와 확장 지점 확인 |
| `<module>/04-files-checklist.md` | 서브모듈 수정 전후 검증 질문 확인 |
| `<module>/05-file-inventory.md` | 서브모듈 전체 파일 인덱스와 수정 후보 확인 |
| `<module>/change-log.md` | 서브모듈 상세 변경 이력 |
| 문서 상태 표기 | `stub`, `draft`, `verified`, `stale` 기준으로 신뢰도 판단 |

## 3. `00-agent-routing.md` 작성 규칙

`00-agent-routing.md`는 자동화의 라우팅 테이블이다.
문서 설명보다 표의 정확도가 중요하다.

권장 구조:

```markdown
# 에이전트 작업 라우팅

## 1. 기본 원칙

1. 작업 파일 경로를 먼저 확인한다.
2. 이 문서에서 영향 도메인, 기능 또는 서브모듈을 고른다.
3. 해당 패키지의 README와 실제 존재하는 확장 문서만 읽는다.
4. 실제 판단은 현재 코드와 diff가 기준이다.

## 2. 항상 먼저 보는 문서

- README.md
- 00-change-index.md

## 3. 경로별 문서 라우팅

| 변경 파일 경로 또는 키워드 | 우선 참조 문서 |
|---|---|
| `<path>/**` | `<domain>/README.md`, `<domain>/package-reference/README.md` |
| `<feature-path>/**`, `<feature-keyword>` | `<feature>/README.md`, `<feature>/01-feature-flow.md` |
```

라우팅 표 작성 기준:

- 경로는 가능한 실제 repository path를 쓴다.
- 클래스명 키워드는 path만으로 분류하기 어려울 때 보조로 쓴다.
- 도메인, 기능, 서브모듈 문서를 같이 읽어야 하면 모두 적는다.
- 공통 영향 경로는 도메인 문서와 공통 문서를 함께 적는다.
- 오래된 경로는 남겨두지 않는다. 이동이 확인되면 새 경로로 갱신한다.

## 4. 패키지 이동 감지 시 처리

패키지 이동, 신규 패키지, 클래스 이동, 도메인/기능 경계 변경은 자동화 계약 변경이다.

예시:

```text
old: <app-module>/src/main/java/<package>/old/domain/**
new: <app-module>/src/main/java/<package>/new/domain/**
```

이 경우 갱신 대상:

1. `00-agent-routing.md`
2. 루트 또는 관련 패키지 `00-structure-map.md`
3. 관련 도메인/기능/서브모듈 `README.md`
4. 관련 `package-reference/04-files-checklist.md`, 기능 `04-files-checklist.md` 또는 서브모듈 `04-files-checklist.md`
5. `00-change-index.md`
6. 관련 패키지 `change-log.md`
7. 예약 자동화 프롬프트의 `갱신 대상 분류`

자동화 프롬프트까지 갱신해야 하는 기준:

- 프롬프트에 old path가 하드코딩되어 있다.
- old path 때문에 다음 실행에서 잘못된 도메인을 고를 수 있다.
- 신규 도메인/기능/서브모듈 패키지가 생겨 자동화가 읽을 문서를 알아야 한다.
- 경로 이동이 단기 브랜치 실험이 아니라 기준 브랜치에 반영된 구조 변경이다.

자동화 프롬프트를 갱신하지 않는 기준:

- 일회성 임시 파일이다.
- 테스트 코드 또는 빌드 인프라 변경만 있다.
- 기존 라우팅 표로도 정확히 분류된다.
- 코드 구조 변경이 아니라 문서 문구만 바뀌었다.

## 5. 자동화 프롬프트에 넣을 내용

예약 자동화 프롬프트에는 너무 많은 구현 상세를 넣지 않는다.
다만 아래 계약은 프롬프트에 있어야 한다.

- 원격 ref 선택 규칙
- 직전 SHA 비교와 no-op skip 규칙
- 로컬 KB 절대 경로
- 팀 공유 KB와 로컬 전용 KB 경계
- 먼저 읽을 KB 문서
- 문서 상태 처리 규칙
- 패키지/경로 라우팅 갱신 규칙
- 갱신 대상 분류
- 수정 금지 영역
- 결과 보고 항목

패키지/경로 라우팅 갱신 문구 예시:

```text
원격 diff에서 패키지 이동, 신규 패키지, 클래스 이동, 도메인/기능 경계 변경이 확인되면
실제 코드 경로와 diff 근거를 기준으로 관련 KB 라우팅 문서
(`00-agent-routing.md`, 해당 도메인/기능 `README.md`, 필요한 `package-reference/` 또는 `01-feature-flow.md`)를 갱신한다.
변경된 패키지/경로가 다음 자동화 실행의 분류 기준에도 영향을 주면,
자동화 프롬프트의 `갱신 대상 분류`도 변경된 패키지/경로 기준으로 함께 갱신한다.
```

## 6. 결과 보고 형식

자동화는 패키지/라우팅 갱신 여부를 결과에 포함해야 한다.

필수 보고 항목:

- 선택한 원격 기준 브랜치와 선택 이유
- 확인한 원격 후보 브랜치 목록
- 직전 SHA와 현재 SHA
- 변경 파일 요약
- 영향 도메인/기능/서브모듈 분류
- 읽은 KB 문서 목록
- 업데이트한 KB 문서 목록
- 자동화 프롬프트 갱신 여부
- 갱신한 패키지/경로 분류 규칙
- 반영하지 못한 항목 또는 추가 확인 필요 항목

## 7. 변경 이력 기록

`00-change-index.md`에는 상세 설명을 길게 쌓지 않는다.
전체 KB의 변경사항이 생기면 반드시 한 줄 색인을 추가한다.

권장 표:

```markdown
| 날짜 | 기준 ref/SHA | 영향 영역 | KB 반영 |
|---|---|---|---|
| YYYY-MM-DD | `<ref>` / `<sha>` | `<domain>` 패키지 이동 | `00-agent-routing.md`, `<domain>/README.md` 반영 |
```

상세 내용은 해당 도메인/기능/서브모듈 문서에 적고, `00-change-index.md`에는 찾을 수 있을 정도만 남긴다.

패키지별 `change-log.md`에는 상세 변경 이력을 남긴다.

```markdown
## YYYY-MM-DD

- 기준:
- 변경 근거:
- 변경 파일:
- 갱신한 KB:
- 갱신하지 않은 KB와 이유:
- 자동화 프롬프트 갱신 여부:
- 다음 검증:
```

자동화가 KB 문서를 수정했으면 아래 둘 중 하나는 반드시 갱신되어야 한다.

- 루트 `00-change-index.md`
- 변경된 패키지의 `change-log.md`

도메인/기능/서브모듈 의미가 바뀐 변경이면 둘 다 갱신한다.

서브모듈 KB를 갱신할 때는 도메인 의존 정보를 넣지 않는다.
도메인별 특수 동작은 해당 도메인 `change-log.md`와 도메인 문서에 기록하고, 서브모듈에는 필요한 경우 링크만 남긴다.

## 8. 운영상 주의점

- 로컬 KB가 Git ignore 대상이면 `git status`에 보이지 않을 수 있다. `rg --files <kb-root>`로 존재를 확인한다.
- 원격 ref의 `docs/`는 기준 문서가 아니다. 로컬 KB 절대 경로가 기준이다.
- 자동화 memory는 중복 실행 방지용 상태값이다. 실행 규칙은 memory가 아니라 자동화 프롬프트에 들어가야 한다.
- memory에는 이번 실행에서 어떤 ref/SHA를 봤고 무엇을 갱신했는지만 남긴다.
- 라우팅 규칙이 바뀌면 KB 문서와 자동화 프롬프트를 함께 점검한다.
- `CLAUDE.md`는 얇은 인덱스로 유지하고, 자동화가 KB 상세를 직접 추가하지 않는다.
