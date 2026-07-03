---
type: file-inventory
title: "<패키지명> 파일 인벤토리"
description: "<패키지명>의 핵심 파일, 역할, 참조 시점, 함께 볼 파일을 인덱싱한다."
tags: [file-inventory, kb, android, "<domain-or-module>"]
resource: "<target-source-root>"
timestamp: "<YYYY-MM-DDTHH:mm:ss+09:00>"
status: draft
---

# <패키지명> 파일 인벤토리

> 상태: draft
> 기준: `<YYYY-MM-DD 현재 코드 확인>`

이 문서는 도메인/서브모듈/하위 책임 패키지에서 전체 파일의 역할을 빠르게 찾기 위한 필수 인덱스다.
모든 파일을 길게 설명하지 않는다.
핵심 파일은 `00-structure-map.md`, `03-extension-points.md`, `04-files-checklist.md`에서 상세히 설명하고, 이 문서에는 한 줄 역할과 탐색 기준만 둔다.

## 분류 기준

| 분류 | 기준 |
|---|---|
| 핵심 | 작업 시작점이거나 수정 빈도가 높고 side effect가 큰 파일 |
| 핵심 후보 | 자주 수정되지는 않지만 문제 발생 시 반드시 확인해야 하는 파일 |
| 보조 | 이름과 위치만 알면 충분한 helper/model 파일 |

## 파일 목록

| 파일 | 역할 | 언제 보는가 | 분류 | 함께 볼 파일 |
|---|---|---|---|---|
| `<path/File.kt>` | `<한 줄 역할>` | `<작업 키워드>` | `핵심` | `<related.kt>` |
| `<path/Helper.kt>` | `<한 줄 역할>` | `<작업 키워드>` | `보조` | `<related.kt>` |

## 갱신 규칙

- 파일 추가/삭제/이동 시 이 문서를 갱신한다.
- 파일 책임이 바뀌면 해당 패키지 `change-log.md`에 이유를 남긴다.
- 핵심 파일로 승격된 파일은 `00-structure-map.md` 또는 `03-extension-points.md`에도 반영한다.
- 도메인 특화 설명은 이 문서에 넣지 않고 도메인 KB로 이동한다.
