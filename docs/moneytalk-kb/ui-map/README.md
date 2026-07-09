---
type: package
title: UI Map KB
description: 화면별 Composable 계층과 공통 UI/Contract 위치를 KB 내부에서 안내한다.
tags: [moneytalk, kb, ui, compose, screen-map]
resource: docs/moneytalk-kb/ui-map/
timestamp: 2026-07-09T06:00:00+09:00
status: draft
---

# UI Map KB

> 상태: draft
> 기준: 2026-07-09 현재 흡수된 Composable map 원문을 KB용 화면 인덱스로 재작성

이 패키지는 Composable 원문 지도를 그대로 보관하지 않는다. 화면/공통 컴포넌트를 수정할 때 어느 화면 KB와 package-reference를 먼저 읽어야 하는지 연결한다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [01-screen-composable-index.md](01-screen-composable-index.md) | 화면별 주요 Composable, Activity, ViewModel, 하위 KB 링크 | UI/Composable 추가·삭제·이름 변경 |
| [change-log.md](change-log.md) | 이 패키지 변경 로그 | UI map 변경 이유 확인 |

## 운영 규칙

- Composable을 추가/삭제/이름 변경하면 해당 화면 KB와 이 인덱스를 같이 갱신한다.
- 화면별 상세 데이터/렌더링/액션 계약은 각 화면의 `package-reference/**`가 우선이다.
- 공통 컴포넌트 contract를 바꾸면 사용하는 화면 KB를 함께 확인한다.
