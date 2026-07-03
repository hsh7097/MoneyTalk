---
type: feature
title: "<기능명> 기능"
description: "<기능명>의 end-to-end 흐름, 필수 문서, 기준 파일을 안내한다."
tags: [feature, android, "<feature-name>"]
resource: "<feature-related-source-roots>"
timestamp: "<YYYY-MM-DDTHH:mm:ss+09:00>"
status: draft
---

# <기능명> 기능

> 상태: draft
> 기준: `<YYYY-MM-DD 현재 코드 확인>`

이 문서는 `<기능명>` 작업을 시작할 때 보는 기능 단위 인덱스다.
기능 KB는 특정 화면이나 서브모듈 하나가 아니라 trigger부터 결과 반영까지 여러 패키지를 가로지르는 흐름을 설명한다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| `00-structure-map.md` | 기능에 참여하는 화면, ViewModel, Repository, 서브모듈, DB/App Function 파일을 정리한다. | 변경 파일이 기능 흐름의 어느 책임인지 판단할 때 본다. |
| `01-feature-flow.md` | 기능 trigger부터 결과 저장/표시까지 end-to-end 흐름을 설명한다. | 기능 동작 전체를 따라가야 할 때 본다. |
| `02-data-contract.md` | 기능의 input/output model, DB/API/App Function contract를 설명한다. | 데이터 필드, 응답 모델, 저장 모델, 노출 함수가 바뀔 때 본다. |
| `03-extension-points.md` | 기능 확장 지점과 책임 경계를 정리한다. | 새 rule, parser, function, policy를 추가할 때 본다. |
| `04-files-checklist.md` | 기능 수정 전후 확인할 파일과 검증 질문을 정리한다. | 리뷰 전 누락된 side effect를 점검할 때 본다. |
| `05-file-inventory.md` | 기능 관련 파일의 한 줄 역할과 참조 시점을 인덱싱한다. | 파일 후보가 애매할 때 본다. |
| `change-log.md` | 기능 KB 변경 근거와 갱신 이력을 기록한다. | 문서가 왜 바뀌었는지 확인할 때 본다. |

## 기능 요약

`<기능명>`은 아래 흐름으로 동작한다.

1. `<trigger>`
2. `<orchestrator>`
3. `<domain/viewmodel>`
4. `<repository/service>`
5. `<database/api/app function>`
6. `<ui refresh 또는 external result>`

## 관련 도메인과 서브모듈

| 구분 | 문서 | 역할 |
|---|---|---|
| 도메인 | `<domain>/README.md` | 화면/상태/UI 상세 |
| 서브모듈 | `<module>/README.md` | 공통 구현체 내부 구조 |

## 기준 코드 경로

- `<path>`
- `<path>`

## 변경 로그

- [change-log.md](change-log.md)
