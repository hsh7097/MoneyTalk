---
type: routing
title: "<KB명> 에이전트 작업 라우팅"
description: "변경 파일 경로와 작업 키워드를 기준으로 AI가 읽을 KB 문서를 결정한다."
tags: [routing, kb, android, "<domain-or-module>"]
resource: "<repository-root-or-routing-source>"
timestamp: "<YYYY-MM-DDTHH:mm:ss+09:00>"
status: draft
---

# 에이전트 작업 라우팅

이 문서는 AI 또는 자동화가 변경 파일 경로를 보고 어떤 KB 문서를 읽을지 결정하는 진입점이다.
전체 KB를 매번 읽지 않는다.

## 1. 기본 원칙

1. 변경 파일 경로를 먼저 확인한다.
2. 이 문서에서 영향 도메인 또는 서브모듈을 고른다.
3. 해당 패키지의 `README.md`와 필요한 `package-reference/`만 읽는다.
4. 실제 파일 위치 확인이 필요하면 해당 패키지의 `00-structure-map.md`를 읽는다.
5. 공통 영향이 있으면 공통 문서를 추가로 읽는다.
6. 판단 기준은 항상 현재 코드와 실제 diff다.
7. 문서와 코드가 다르면 코드 확인 결과를 기준으로 문서를 갱신한다.

## 2. 항상 먼저 보는 문서

- `README.md`
- `00-change-index.md`
- `01-structure-map.md` 또는 변경 패키지의 `00-structure-map.md`

## 3. 경로별 문서 라우팅

| 변경 파일 경로 또는 키워드 | 우선 참조 문서 | 비고 |
|---|---|---|
| `<domain-path>/**` | `<domain>/README.md`, `<domain>/00-structure-map.md`, `<domain>/package-reference/README.md` | 도메인 화면/업무 흐름 |
| `<module-path>/**` | `<module>/README.md`, `<module>/00-structure-map.md` | 공통 서브모듈 |
| `<analytics-path>/**`, analytics keyword | `<analytics-module>/README.md`, 영향 도메인의 `package-reference/03-rendering-action.md` | 공통 구현과 도메인 예외를 분리 |
| `<data-path>/**`, Repository, DataSource, Service, DAO | `<data-module>/README.md`, 영향 도메인의 `package-reference/02-data-viewmodel.md` | 데이터 흐름 |
| `<ui-path>/**`, Composable, dialog, card | 영향 도메인의 `package-reference/03-rendering-action.md` | 렌더링/action |

## 4. 자동화 갱신 라우팅

자동화는 원격 diff 파일 목록을 먼저 만든 뒤 아래 순서로만 KB를 읽는다.

1. `00-agent-routing.md`
2. `00-change-index.md`
3. 변경 파일 경로가 매칭된 도메인/서브모듈 문서
4. 변경 위치 확인이 필요한 경우 해당 `00-structure-map.md`
5. 공통 영향 문서

변경 파일이 여러 도메인에 걸쳐도 전체 KB를 읽지 않는다.
각 영향 영역별로 읽을 문서를 좁힌다.

## 5. 패키지 이동 시 갱신 규칙

패키지 이동, 신규 패키지, 클래스 이동이 확인되면 아래를 함께 갱신한다.

- 이 문서의 경로별 라우팅 표
- 루트 또는 패키지 `00-structure-map.md`
- 해당 도메인/서브모듈 `README.md`
- 해당 `package-reference/04-files-checklist.md`
- `00-change-index.md`
- 예약 자동화 프롬프트의 `갱신 대상 분류` 규칙
