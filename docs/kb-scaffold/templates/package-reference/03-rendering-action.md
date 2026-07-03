---
type: rendering-action
title: "<도메인명> Rendering Action"
description: "<도메인명>의 렌더링, 사용자 액션, navigation, analytics side effect 연결을 설명한다."
tags: [rendering, action, analytics, android, "<domain-name>"]
resource: "<rendering-source-files>"
timestamp: "<YYYY-MM-DDTHH:mm:ss+09:00>"
status: draft
---

# 03 Rendering Action

> 상태: draft
> 기준: `<YYYY-MM-DD 현재 코드 확인>`

이 문서는 `<도메인명>`의 렌더링, 사용자 액션, navigation, analytics side effect 연결을 설명한다.

## 포함할 내용

- Composable 생성 위치
- UI state와 callback mapping
- 화면/카드/dialog component 책임
- user action과 ViewModel event 흐름
- navigation/action side effect
- analytics side effect
- 접근성 처리 위치

## 빠른 참조

| 작업 | 먼저 볼 파일 | 함께 볼 파일 |
|---|---|---|
| 신규 Composable 추가 | `<Screen.kt>` | `<ViewModel.kt>`, component files |
| 클릭 액션 변경 | `<Screen.kt>` | `<ViewModel.kt>`, `<Navigator.kt>` |
| analytics 변경 | `<Analytics.kt>` | `<Screen.kt>`, 관련 analytics KB |

## 갱신 규칙

- 렌더링/action 책임이 바뀌면 이 문서와 상위 `05-file-inventory.md`를 함께 갱신한다.
- 공통 서브모듈 사용법이 바뀐 경우 서브모듈 KB에는 일반 계약만 기록하고 도메인 예외는 이 문서에 둔다.
