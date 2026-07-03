---
type: package-reference
title: "<도메인명> package-reference"
description: "<도메인명> 작업에서 실제 수정 위치를 찾기 위한 세부 개발 문서 인덱스다."
tags: [package-reference, domain, android, "<domain-name>"]
resource: "<domain-source-root>"
timestamp: "<YYYY-MM-DDTHH:mm:ss+09:00>"
status: draft
---

# <도메인명> package-reference

> 상태: draft
> 기준: `<YYYY-MM-DD 현재 코드 확인>`

이 디렉터리는 `<도메인명>` 작업에서 실제 수정 위치를 찾기 위한 세부 개발 문서 묶음이다.
도메인 README에서 진입한 뒤 작업 유형에 맞는 문서만 읽는다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| `01-entry-screen.md` | Activity, NavGraph, permission 같은 화면 진입 흐름을 설명한다. | 화면 진입, routing, lifecycle, navigation 문제를 볼 때 본다. |
| `02-data-viewmodel.md` | ViewModel, Repository, DataSource, API, state 생성 흐름을 설명한다. | 데이터 로딩, state, paging, API field mapping 문제를 볼 때 본다. |
| `03-rendering-action.md` | Composable, UI state, action, navigation, analytics 연결을 설명한다. | 렌더링, 클릭/액션, analytics, 접근성 문제를 볼 때 본다. |
| `04-files-checklist.md` | 도메인 수정 전후 확인할 파일과 검증 관점을 정리한다. | 리뷰 전 누락된 주변 파일이나 side effect를 점검할 때 본다. |

## 사용 규칙

- 이 디렉터리는 도메인 내부 개발 흐름만 설명한다.
- 도메인 전체 파일 인덱스는 상위 `05-file-inventory.md`에 둔다.
- 서브모듈 공통 구현 설명은 해당 서브모듈 KB로 연결하고 이곳에 복사하지 않는다.
- package-reference 문서를 수정하면 도메인 `change-log.md`에 이유를 남긴다.
