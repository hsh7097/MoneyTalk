---
type: entry-screen
title: "<도메인명> Entry Screen"
description: "<도메인명>의 화면 진입, routing, lifecycle 흐름을 설명한다."
tags: [entry, screen, lifecycle, android, "<domain-name>"]
resource: "<entry-source-files>"
timestamp: "<YYYY-MM-DDTHH:mm:ss+09:00>"
status: draft
---

# 01 Entry Screen

> 상태: draft
> 기준: `<YYYY-MM-DD 현재 코드 확인>`

이 문서는 `<도메인명>`의 화면 진입, routing, lifecycle 흐름을 설명한다.

## 포함할 내용

- 진입 Activity, NavGraph route, permission launcher
- navigation, deep link, app link 처리
- container layout 또는 host 화면
- 화면 depth와 하위 화면/layer 관계
- lifecycle 주의점
- 진입 흐름 변경 시 함께 확인할 파일

## 빠른 참조

| 작업 | 먼저 볼 파일 | 함께 볼 파일 |
|---|---|---|
| 화면 진입 변경 | `<EntryActivity.kt>` | `<NavGraph.kt>`, `<Navigator.kt>` |
| deep link 변경 | `<UrlExecutor.kt>` | `<Router.kt>`, `00-structure-map.md` |

## 갱신 규칙

- 화면 진입 파일이 이동하면 이 문서와 상위 `00-structure-map.md`, `05-file-inventory.md`를 함께 갱신한다.
- 진입 흐름 변경이 라우팅 기준에 영향을 주면 루트 `00-agent-routing.md`도 갱신한다.
