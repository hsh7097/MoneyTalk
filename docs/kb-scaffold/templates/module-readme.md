---
type: module
title: "<서브모듈명> 서브모듈"
description: "<서브모듈명>의 목적, 필수 문서, 하위 책임 패키지, 사용 기준을 안내한다."
tags: [module, android, "<module-name>"]
resource: "<module-source-root>"
timestamp: "<YYYY-MM-DDTHH:mm:ss+09:00>"
status: draft
---

# <서브모듈명> 서브모듈

> 상태: draft
> 기준: `<YYYY-MM-DD 현재 코드 확인>`

이 문서는 `<서브모듈명>`을 사용하는 도메인 작업 또는 모듈 내부 수정 시 먼저 보는 인덱스 문서다.
서브모듈 KB는 `README.md`, `00-structure-map.md`, `01-purpose-architecture.md`, `02-how-to-use.md`, `03-extension-points.md`, `04-files-checklist.md`, `05-file-inventory.md`, `change-log.md`를 필수로 가진다.

## 모듈 목적

- `<이 모듈이 해결하는 문제>`
- `<여러 도메인/앱에서 공통으로 쓰는 이유>`

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| `00-structure-map.md` | `<서브모듈명>`의 folder tree, package group, 핵심 파일, AI 참조 순서를 정리한다. | 변경 파일이 어느 하위 책임 패키지에 속하는지 먼저 판단할 때 본다. |
| `01-purpose-architecture.md` | 모듈 목적, 책임 경계, 주요 흐름을 설명한다. | 모듈 내부 수정이 현재 architecture와 맞는지 확인할 때 본다. |
| `02-how-to-use.md` | 작업 유형별 문서 참조 순서와 수정 기준을 안내한다. | 실제 작업을 시작하기 전 어떤 문서를 먼저 열지 정할 때 본다. |
| `03-extension-points.md` | 모듈 확장 지점과 확장 시 주의할 책임 경계를 정리한다. | 새 기능, helper, adapter, receiver 등을 추가할 때 본다. |
| `04-files-checklist.md` | 수정 전후 확인할 주변 파일과 검증 관점을 정리한다. | 리뷰 전 누락된 파일이나 side effect를 점검할 때 본다. |
| `05-file-inventory.md` | 파일별 역할, 분류, 함께 볼 파일을 인덱싱한다. | 파일 이름만으로 책임이 애매하거나 후보 파일을 좁힐 때 본다. |
| `change-log.md` | 서브모듈 KB 변경 근거와 갱신 이력을 기록한다. | 문서가 왜 바뀌었는지, 어떤 문서를 함께 봐야 하는지 확인할 때 본다. |

## 주요 패키지

- `<module/package/path>`

## 파일 인벤토리

- `05-file-inventory.md`

모든 서브모듈은 전체 파일을 한 줄 역할로 인덱싱한다.
핵심 파일은 상세 문서에서 다루고, 보조 파일은 인벤토리에서 위치와 역할만 빠르게 찾는다.

## 하위 책임 패키지

서브모듈 내부가 책임별로 나뉘면 docs에도 같은 단위의 하위 패키지를 둔다.
상위 서브모듈 README에는 요약과 라우팅만 두고, 상세 파일 역할은 하위 패키지 문서에 둔다.

| 하위 패키지 | 기준 코드 경로 | 역할 | 먼저 볼 문서 |
|---|---|---|---|
| `<sub-package-a>` | `<module>/<sub-package-a>` | `<역할>` | `<sub-package-a>/README.md` |
| `<sub-package-b>` | `<module>/<sub-package-b>` | `<역할>` | `<sub-package-b>/README.md` |

## 구조 지도

- `00-structure-map.md`

`00-structure-map.md`에는 모듈의 folder tree, Gradle module, package groups, 핵심 파일, 작업 유형별 AI 참조 순서를 둔다.
핵심 파일에는 파일별 역할과 수정 시 함께 확인할 파일을 함께 적는다.

## 의존성 방향

```text
<consumer module>
→ <this module>
→ <lower dependency>
```

## 도메인별 예외 위치

이 문서는 공통 구현과 사용 규칙만 설명한다.
도메인별 특수 동작은 각 도메인 KB에 둔다.
서브모듈 KB에 도메인별 예외 내용을 복사하지 않고, 위치 링크만 남긴다.

| 도메인 | 예외 문서 |
|---|---|
| `<domain-a>` | `<path>` |
| `<domain-b>` | `<path>` |

## 변경 로그

- [change-log.md](change-log.md)

서브모듈 KB를 수정하면 이 파일에 상세 로그를 남긴다.
서브모듈 라우팅 또는 모듈 경계가 바뀌면 루트 `00-change-index.md`에도 색인을 추가한다.
하위 책임 패키지의 상세 변경은 해당 하위 패키지 `change-log.md`에 남긴다.
