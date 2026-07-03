---
type: checklist
title: "<도메인명> Files Checklist"
description: "<도메인명> 수정 전후에 확인할 파일과 검증 질문을 정리한다."
tags: [checklist, files, validation, android, "<domain-name>"]
resource: "<domain-source-root>"
timestamp: "<YYYY-MM-DDTHH:mm:ss+09:00>"
status: draft
---

# 04 Files Checklist

> 상태: draft
> 기준: `<YYYY-MM-DD 현재 코드 확인>`

이 문서는 `<도메인명>` 수정 전후에 확인할 파일과 검증 질문을 정리한다.

## 빠른 파일 찾기

| 작업 | 확인 파일 |
|---|---|
| 화면 진입 변경 | `01-entry-screen.md`, `<EntryFile.kt>` |
| 데이터 변경 | `02-data-viewmodel.md`, `<ViewModel.kt>`, `<Repository.kt>` |
| 렌더링 변경 | `03-rendering-action.md`, `<Screen.kt>`, component files |
| action/analytics 변경 | `03-rendering-action.md`, `<Screen.kt>`, `<Analytics.kt>` |
| 파일 후보가 애매함 | 상위 `05-file-inventory.md` |

## 수정 전 질문

- 현재 변경이 entry, data, rendering, action, analytics 중 어디에 속하는가?
- 함께 확인할 ViewModel/Repository/Composable/component가 있는가?
- 도메인 예외인지 서브모듈 공통 계약인지 분리했는가?
- 접근성, analytics, navigation side effect가 있는가?

## 검증

- 관련 unit test 또는 build task
- 샘플 작업으로 문서 충분성 확인
- 변경한 문서와 `change-log.md` 갱신 여부 확인
