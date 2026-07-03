---
type: domain
title: "<도메인명> 도메인"
description: "<도메인명> 작업의 진입점, 필수 문서, 기준 패키지를 안내한다."
tags: [domain, android, "<domain-name>"]
resource: "<domain-source-root>"
timestamp: "<YYYY-MM-DDTHH:mm:ss+09:00>"
status: draft
---

# <도메인명> 도메인

> 상태: draft
> 기준: `<YYYY-MM-DD 현재 코드 확인>`

이 문서는 `<도메인명>` 작업을 시작할 때 보는 인덱스 문서다.
도메인 KB는 `README.md`, `00-structure-map.md`, `05-file-inventory.md`, `change-log.md`, `package-reference/` 문서를 필수로 가진다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| `00-structure-map.md` | 도메인의 folder tree, package group, 핵심 파일, AI 참조 순서를 정리한다. | 변경 파일이 어느 화면/패키지 책임에 속하는지 먼저 판단할 때 본다. |
| `package-reference/README.md` | 도메인 세부 개발 문서의 인덱스와 읽는 순서를 정리한다. | entry/data/rendering/checklist 중 어떤 문서로 내려갈지 고를 때 본다. |
| `package-reference/01-entry-screen.md` | Activity, NavGraph, permission 같은 화면 진입 흐름을 설명한다. | 화면 진입, routing, lifecycle, navigation 문제를 볼 때 본다. |
| `package-reference/02-data-viewmodel.md` | ViewModel, Repository, DataSource, API, state 생성 흐름을 설명한다. | 데이터 로딩, state, paging, API field mapping 문제를 볼 때 본다. |
| `package-reference/03-rendering-action.md` | Composable, UI state, dialog, action, navigation, analytics 연결을 설명한다. | 렌더링, 클릭/액션, analytics, 접근성 문제를 볼 때 본다. |
| `package-reference/04-files-checklist.md` | 도메인 수정 전후 확인할 파일과 검증 관점을 정리한다. | 리뷰 전 누락된 주변 파일이나 side effect를 점검할 때 본다. |
| `05-file-inventory.md` | 도메인 전체 파일의 한 줄 역할과 함께 볼 파일을 인덱싱한다. | 파일 이름만으로 수정 후보가 애매하거나 영향 파일을 좁힐 때 본다. |
| `change-log.md` | 도메인 KB 변경 근거와 갱신 이력을 기록한다. | 문서가 왜 바뀌었는지, 어떤 문서를 함께 봐야 하는지 확인할 때 본다. |

## 핵심 요약

`<도메인명>`은 아래 흐름으로 동작한다.

1. `<EntryActivity 또는 NavGraph route>`
2. `<Screen container>`
3. `<ViewModel>`
4. `<Repository/DataSource/API>`
5. `<List manager 또는 parser>`
6. `<Adapter>`
7. `<Composable 또는 dialog/card component>`

## 기준 패키지

- `<package.path>`
- `<package.path.sub>`

## 구조 지도

- `00-structure-map.md`

`00-structure-map.md`에는 이 도메인의 folder tree, package groups, 핵심 파일, 작업 유형별 AI 참조 순서를 둔다.
핵심 파일에는 파일별 역할과 수정 시 함께 확인할 파일을 함께 적는다.

## 작업의 핵심 판단

- `<진입 문제>`면 `<파일/클래스>`를 본다.
- `<데이터 문제>`면 `<파일/클래스>`를 본다.
- `<렌더링 문제>`면 `<파일/클래스>`를 본다.
- `<액션/트래킹 문제>`면 `<파일/클래스>`를 같이 본다.

## 변경 로그

- [change-log.md](change-log.md)

도메인 KB를 수정하면 이 파일에 상세 로그를 남긴다.
도메인 라우팅 또는 도메인 경계가 바뀌면 루트 `00-change-index.md`에도 색인을 추가한다.
