---
type: data-viewmodel
title: "<도메인명> Data ViewModel"
description: "<도메인명>의 ViewModel, Repository, DataSource, API, state 생성 흐름을 설명한다."
tags: [data, viewmodel, api, android, "<domain-name>"]
resource: "<data-source-files>"
timestamp: "<YYYY-MM-DDTHH:mm:ss+09:00>"
status: draft
---

# 02 Data ViewModel

> 상태: draft
> 기준: `<YYYY-MM-DD 현재 코드 확인>`

이 문서는 `<도메인명>`의 ViewModel, Repository, DataSource, API, state 생성 흐름을 설명한다.

## 포함할 내용

- ViewModel 책임과 state/effect 흐름
- Repository, RemoteDataSource, LocalDataSource 역할
- API Service와 request/response 주요 모델
- cache, paging, load more 정책
- model mapping 위치
- 데이터 변경 시 함께 확인할 rendering/action 파일

## 빠른 참조

| 작업 | 먼저 볼 파일 | 함께 볼 파일 |
|---|---|---|
| API 필드 추가 | `<Service.kt>` | `<Response.kt>`, `<Repository.kt>`, `<ViewModel.kt>` |
| state 변경 | `<ViewModel.kt>` | `<Screen.kt>`, `03-rendering-action.md` |

## 갱신 규칙

- API, model, state 생성 흐름이 바뀌면 이 문서와 상위 `05-file-inventory.md`를 함께 갱신한다.
- Repository/DataSource 책임이 이동하면 `00-structure-map.md`와 `change-log.md`에도 기록한다.
