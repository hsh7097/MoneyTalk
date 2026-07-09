---
type: reference
title: Git Workflow
description: MoneyTalk 브랜치, 커밋, 푸시, PR 운영 기준을 정리한다.
tags: [moneytalk, git, workflow, commit]
resource: .
timestamp: 2026-07-09T06:10:00+09:00
status: draft
---

# Git Workflow

> 기준: 흡수된 Git 운영 원문 내용을 KB용으로 재구성

## 브랜치

| 브랜치 | 역할 | 직접 커밋 |
|---|---|---|
| `master` | 릴리스 | 금지 |
| `develop` | 개발 통합 | 금지. 사용자가 명시한 경우만 예외 |
| 기능 브랜치 | 작업 단위 | 허용 |

`develop` 또는 `master`에서 작업 중이면 새 브랜치를 만든 뒤 작업한다. 브랜치 타입은 `feature/`, `fix/`, `refactor/`, `docs/`, `chore/`를 쓴다.

## 커밋 메시지

커밋 메시지는 한글을 기본으로 하고 코드 식별자는 영어를 유지한다.

```text
문서: MoneyTalk KB 화면 라우팅 보강

- 화면별 요구사항을 KB 인덱스로 정리
- 채팅/SMS/카테고리 시스템 계약 문서 추가

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>
```

복잡한 버그 수정은 `문제/원인/조치/검증` 구조를 권장한다.

## 커밋 분리

| 구분 | 원칙 |
|---|---|
| 코드 변경 vs 문서 변경 | 별도 커밋 |
| 기능 A vs 기능 B | 별도 커밋 |
| 버그 수정 vs 리팩토링 | 별도 커밋 |
| Lint 수정 vs 기능 변경 | 별도 커밋 |
| 같은 기능의 코드 + 관련 문서 | 함께 가능 |

## 푸시와 PR

- 사용자가 푸시를 요청한 경우에만 push한다.
- 원격 추적이 없으면 `git push -u origin <branch>`를 사용한다.
- `master`, `develop`에 force push하지 않는다.
- "PR 작성"은 텍스트만 작성하고, "PR 생성"은 실제 `gh pr create`를 수행한다.

## 커밋 전 체크

1. `develop`/`master`에서 직접 커밋하지 않는지 확인한다.
2. 작업 목적별로 staging이 분리되어 있는지 확인한다.
3. 민감 파일과 credentials가 포함되지 않았는지 확인한다.
4. 가능하면 `git add -A` 대신 파일을 명시한다.
5. Composable 추가/삭제/이름 변경이 있으면 [../ui-map/01-screen-composable-index.md](../ui-map/01-screen-composable-index.md)를 갱신한다.
6. 코드 변경이면 관련 테스트/빌드와 `git diff --check`를 확인한다.
7. 문서 변경이면 markdown link와 삭제 예정 원문 참조를 확인한다.
