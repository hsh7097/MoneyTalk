---
type: guide
title: 템플릿 인덱스
description: templates/ 아래 복사용 템플릿의 목록, 복사 대상, 역할, 사용 시점을 안내한다.
tags: [kb-scaffold, templates, index, android, moneytalk]
resource: docs/kb-scaffold/templates/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# 템플릿 인덱스

> 역할:
> - `templates/` 폴더의 인덱스로 각 템플릿의 복사 대상과 사용 시점을 안내한다.
> - 새 템플릿을 추가/삭제/이동할 때 가장 먼저 갱신한다.
> - 이 파일 자체는 복사하지 않고, 실제 KB 파일은 나머지 템플릿을 복사해 만든다.

이 폴더의 문서는 이 README를 제외하면 전부 실제 KB가 아니라 복사용 템플릿이다.
새 KB의 루트 `README.md`가 될 복사용 템플릿은 [kb-readme.md](kb-readme.md)다.

## 복사 공통 규칙

- 꺾쇠 `<placeholder>`를 실제 값으로 채운다.
- frontmatter의 `type`을 실제 문서 타입으로 바꾸고 템플릿 전용 필드는 제거한다.
- 채울 수 없는 행이나 섹션은 각 템플릿 안의 안내에 따라 제거한다.
- 템플릿 본문의 상대 링크는 복사된 KB 기준으로 작성한다.

## 템플릿 라우팅

| 템플릿 | 복사 대상 | 역할 | 언제 쓰는가 |
|---|---|---|---|
| [kb-readme.md](kb-readme.md) | `<kb-root>/README.md` | 새 KB의 루트 진입 인덱스 | 새 KB 루트를 만들 때 |
| [agent-routing.md](agent-routing.md) | `<kb-root>/00-agent-routing.md` | 변경 파일 경로/키워드를 읽을 KB 문서로 연결 | 루트 생성 또는 라우팅 변경 시 |
| [change-index.md](change-index.md) | `<kb-root>/00-change-index.md` | KB 전체 변경의 한 줄 색인 형식 | 루트 생성 또는 구조/경계 변경 시 |
| [structure-map.md](structure-map.md) | `<kb-root>/01-structure-map.md` 또는 `<package>/00-structure-map.md` | source root, 패키지 책임, 핵심 파일, 참조 순서 지도 | 루트 생성 또는 복잡한 패키지 확장 시 |
| [domain-readme.md](domain-readme.md) | `<domain>/README.md` | 도메인 패키지 진입 인덱스 | 도메인 패키지를 만들 때 |
| [feature-readme.md](feature-readme.md) | `<feature>/README.md` | end-to-end 기능 패키지 진입 인덱스 | 여러 화면/모듈을 가로지르는 기능 KB를 만들 때 |
| [feature-flow.md](feature-flow.md) | `<feature>/01-feature-flow.md` | 기능 trigger부터 결과 반영까지 흐름 | 기능 흐름이 README만으로 길어질 때 |
| [module-readme.md](module-readme.md) | `<module>/README.md` | 서브모듈 패키지 진입 인덱스 | 서브모듈 또는 하위 책임 패키지를 만들 때 |
| [change-log.md](change-log.md) | `<package>/change-log.md` | 패키지 상세 변경 로그 | 모든 패키지 생성 시 |
| [file-inventory.md](file-inventory.md) | `<package>/05-file-inventory.md` | 전체 파일의 한 줄 역할 인덱스 | 파일 이름만으로 수정 후보를 좁히기 어려울 때 |
| [package-reference/](package-reference/) | `<domain>/package-reference/` | 화면 진입/데이터/렌더링·액션/체크리스트 세부 문서 | 도메인 단계에서 세부 수정 위치 안내가 반복될 때 |

## 파일명 규칙

- README가 되는 템플릿은 `*-readme.md`로 구분하고, 복사할 때 `README.md`로 이름을 바꾼다.
- MoneyTalk 기존 KB는 루트 색인을 `00-change-index.md`, 구조 지도를 `01-structure-map.md`로 둔다.
- 다른 KB 루트에서 `01-change-index.md`, `02-structure-map.md`를 채택하더라도 한 KB 안에서 명명을 섞지 않는다.
