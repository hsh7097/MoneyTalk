---
type: feature-flow
title: "<기능명> Feature Flow"
description: "<기능명>의 trigger, orchestration, data contract, 결과 반영 흐름을 설명한다."
tags: [feature-flow, android, "<feature-name>"]
resource: "<feature-related-source-roots>"
timestamp: "<YYYY-MM-DDTHH:mm:ss+09:00>"
status: draft
---

# 01 Feature Flow

> 상태: draft
> 기준: `<YYYY-MM-DD 현재 코드 확인>`

이 문서는 `<기능명>`이 trigger부터 결과 반영까지 어떻게 흐르는지 설명한다.
서브모듈 내부 구현 전체를 복사하지 않고, 필요한 서브모듈 KB로 연결한다.

## Trigger

| trigger | 진입 파일 | 비고 |
|---|---|---|
| `<사용자 액션 또는 시스템 이벤트>` | `<EntryFile.kt>` | `<조건>` |

## End-to-End Flow

```text
<trigger>
→ <orchestrator>
→ <service/repository>
→ <database/api/app function>
→ <result mapping>
→ <ui refresh 또는 external result>
```

## Data Contract

| 단계 | input | output | 관련 파일 |
|---|---|---|---|
| `<stage>` | `<input>` | `<output>` | `<file>` |

## Side Effects

- `<DB write>`
- `<DataRefreshEvent 또는 UI refresh>`
- `<analytics>`
- `<notification 또는 external response>`

## 실패/권한/비용 정책

- `<permission>`
- `<network/api cost>`
- `<fallback>`
- `<error handling>`

## 관련 KB

| 문서 | 이유 |
|---|---|
| `<domain>/README.md` | 화면/상태 상세 |
| `<module>/README.md` | 공통 구현체 상세 |
