---
type: guide
title: KB 작성 원칙
description: AI 작업용 KB의 목적, 금지사항, 필수 파일, 로그, 서브모듈 경계를 정의한다.
tags: [kb-scaffold, principles, authoring, android, moneytalk]
resource: docs/kb-scaffold/01-principles.md
timestamp: 2026-07-03T15:51:26+09:00
status: draft
---

# KB 작성 원칙

## 1. KB의 목적

KB는 AI가 코드를 수정하거나 리뷰할 때 필요한 현재 구현 지식을 빠르게 찾도록 돕는 문서다.

좋은 KB는 아래 질문에 답한다.

- 이 작업은 어느 도메인 또는 서브모듈에 속하는가?
- 먼저 읽어야 할 문서는 무엇인가?
- 현재 구현은 어떤 Activity, NavGraph, ViewModel, Repository, Room DAO, Composable로 이어지는가?
- 신규 작업 시 어떤 확장 지점을 건드려야 하는가?
- 이 영역에서 반복되는 실수나 금지사항은 무엇인가?

## 2. KB가 아닌 것

KB에 아래 내용을 섞지 않는다.

| 구분 | 저장 위치 | 이유 |
|---|---|---|
| PRD | 이슈/Jira/별도 요구사항 문서 | 요구사항은 구현 상태가 아니다. |
| 개발 스펙 | 작업별 spec 문서 | 구현 계획은 작업마다 달라진다. |
| 미래 마이그레이션 목표 | roadmap/spec 문서 | KB에 넣으면 AI가 현재 코드에 이미 적용된 것으로 오해할 수 있다. |
| 개인 실험 메모 | 별도 로그 또는 실험 문서 | 검증되지 않은 내용을 공통 규칙처럼 읽게 만들 수 있다. |
| 저장소 운영법 | `.claude/docs/` | 빌드, 브랜치, CI, Jira 같은 운영 지식은 코드 분석 KB가 아니다. |

KB는 “현재 코드는 이렇게 되어 있다”를 설명한다.
“앞으로 이렇게 바꿀 것이다”는 KB가 아니라 스펙 또는 계획 문서에 둔다.

## 3. 최상위 문서의 역할

최상위 문서는 상세 구현 설명을 길게 담지 않는다.
AI가 필요한 하위 문서를 찾는 인덱스 역할을 한다.

최상위 문서에 들어갈 내용:

- KB 전체 폴더 구조
- 도메인/서브모듈별 README 링크
- 먼저 읽을 문서의 우선순위
- 팀 공통 금지사항
- 변경 로그 위치

최상위 문서에 넣지 않을 내용:

- 특정 Composable의 상세 구현
- 특정 API 응답 필드 전체 설명
- 특정 도메인에만 적용되는 예외
- 오래된 회의 맥락

## 4. 사실 기반 작성

KB에는 확인된 사실만 쓴다.

작성 근거로 인정하는 것:

- 현재 코드 파일 경로
- 실제 diff
- 빌드/테스트/실행 결과
- 기존 문서와 코드가 일치하는지 확인한 결과
- 담당자 리뷰로 확정된 팀 규칙

피해야 할 것:

- “아마 이렇게 동작할 것이다”
- “다른 앱에서도 비슷할 것이다”
- “이 방향으로 바뀔 예정이니 현재도 그렇게 쓴다”
- 코드 확인 없이 회의 발언만 근거로 구현 사실을 단정

## 5. 목표 수준

초기 KB의 현실적인 목표는 AI가 아래 수준까지 안정적으로 작업하도록 만드는 것이다.

1. 도메인/서브모듈 위치를 찾는다.
2. 기존 확장 지점을 찾는다.
3. Compose 화면, state holder, dialog, navigation 연결 위치를 찾는다.
4. Repository/ViewModel/receiver/analytics 연결 위치를 찾는다.
5. 세부 UI 구현, 디자인 해석, 세밀한 테스트는 사람 검토 또는 별도 스펙으로 보완한다.

초기부터 PRD 해석, 디자인 분석, 완성 UI 구현, 테스트 자동화까지 모두 KB로 해결하려고 하지 않는다.
그 범위는 별도 검증 체계가 준비된 뒤 확장한다.

## 6. 좋은 KB의 기준

좋은 KB는 작고 찾기 쉽다.

- 루트는 얇고 명확하다.
- 도메인 문서는 해당 도메인 작업에 필요한 내용만 가진다.
- 서브모듈 문서는 여러 앱/도메인에서 공통으로 쓰는 규칙을 가진다.
- 도메인별 예외는 도메인 문서에 둔다.
- 공통 구현체의 의도와 사용법은 서브모듈 문서에 둔다.
- 변경 이력은 상세 설명보다 “왜 바뀌었는지”와 “어느 문서가 바뀌었는지”를 남긴다.

## 7. 필수 파일 계약

KB는 AI가 매번 같은 방식으로 진입할 수 있어야 한다.
따라서 도메인과 서브모듈은 최소 필수 파일을 가진다.

각 KB 패키지의 `README.md`는 단순 파일 링크 목록이 아니라 파일 사용 설명을 포함해야 한다.
최소한 아래 항목을 표로 명시한다.

| 항목 | 설명 |
|---|---|
| 문서 | README 기준 상대 경로 또는 링크 |
| 역할 | 해당 문서가 설명하는 정보 |
| 언제 보는가 | AI가 어떤 작업/변경 키워드에서 이 문서를 읽어야 하는지 |

이 표는 AI가 첫 진입에서 읽을 문서를 고르는 라우팅 장치다.
문서 이름만 나열하면 AI가 각 파일을 다시 열어 역할을 추론해야 하므로, README 단계에서 문서의 정체와 참조 시점을 명시한다.

도메인 KB 필수 파일:

- `README.md`: 도메인 인덱스와 작업 시작점
- `00-structure-map.md`: 폴더, 패키지, 핵심 파일, 파일별 역할, AI 참조 순서
- `change-log.md`: 도메인 KB 변경 상세 로그
- `package-reference/README.md`: 세부 개발 문서 인덱스
- `package-reference/01-entry-screen.md`
- `package-reference/02-data-viewmodel.md`
- `package-reference/03-rendering-action.md`
- `package-reference/04-files-checklist.md`
- `05-file-inventory.md`: 도메인 전체 파일 역할 인덱스

서브모듈 KB 필수 파일:

- `README.md`: 서브모듈 인덱스와 작업 시작점
- `00-structure-map.md`: 모듈 구조, 패키지, 핵심 파일, 파일별 역할, AI 참조 순서
- `01-purpose-architecture.md`
- `02-how-to-use.md`
- `03-extension-points.md`
- `04-files-checklist.md`
- `05-file-inventory.md`: 서브모듈 전체 파일 역할 인덱스
- `change-log.md`

작은 패키지도 `README.md`, `00-structure-map.md`, `05-file-inventory.md`, `change-log.md`는 최소로 가진다.
세부 목적/사용법/확장 지점 문서는 한 파일 안에 합쳐 시작할 수 있지만, 자동화 라우팅 대상이 되거나 두 개 이상 도메인에서 사용되면 위 필수 파일 구조로 분리한다.

## 8. 로그 작성 계약

KB를 수정하면 로그를 남긴다.
로그 위치는 임의로 정하지 않는다.

| 변경 종류 | 반드시 갱신할 로그 |
|---|---|
| KB 전체 라우팅, 구조, 패키지 경계 변경 | 루트 `00-change-index.md` |
| 특정 도메인 KB 변경 | 해당 도메인 `change-log.md` |
| 특정 서브모듈 KB 변경 | 해당 서브모듈 `change-log.md` |
| 도메인/서브모듈 의미가 바뀌는 변경 | 루트 `00-change-index.md`와 패키지 `change-log.md` 둘 다 |
| 단순 오타/링크 수정 | 의미가 바뀌지 않으면 로그 생략 가능 |

`00-change-index.md`는 전체 색인이고, `change-log.md`는 패키지 상세 로그다.
둘 중 하나에만 모든 내용을 몰아넣지 않는다.

## 9. 서브모듈 KB 경계

서브모듈 KB에는 상위 도메인에 의존하는 정보를 넣지 않는다.

서브모듈 KB에 넣을 수 있는 것:

- 모듈 자체 목적
- 공통 API와 extension point
- 공통 사용 규칙
- 모듈 내부 폴더/패키지/파일 역할
- consumer가 지켜야 하는 일반 계약

서브모듈 KB에 넣지 않는 것:

- Home, History, Chat 같은 특정 도메인의 비즈니스 흐름
- 특정 화면만의 analytics payload 예외
- 특정 도메인 API response field mapping
- 특정 도메인 Compose 상태 조합 순서

서브모듈에서 도메인 예외를 안내해야 한다면 내용을 복사하지 말고 “도메인 예외 위치” 링크만 둔다.
실제 예외 설명은 상위 도메인 KB에 둔다.

## 10. 운영 문서와 코드 KB 경계

Claude Code 하네스에서는 운영 문서와 코드 분석 KB를 분리한다.

| 문서 종류 | 위치 | 질문 |
|---|---|---|
| 진입점 | `CLAUDE.md` | 이 저장소에서 어디를 읽어야 하는가? |
| 행동 가이드 | `.claude/guidelines.md` | 작업할 때 항상 지킬 원칙은 무엇인가? |
| 운영 문서 | `.claude/docs/` | 빌드, 브랜치, CI, Jira, Confluence를 어떻게 다루는가? |
| 코드 분석 KB | `.claude/docs/kb/` 또는 로컬 KB | 현재 코드는 어떻게 연결되어 있고 어디를 수정해야 하는가? |

KB에는 “저장소를 다루는 법”이 아니라 “코드가 무엇을 하는가”를 적는다.
빌드 명령, 브랜치 전략, Jira API 사용법처럼 코드 흐름이 아닌 정보는 운영 문서로 분리한다.

## 11. 로딩 정책

토큰 절약을 위해 항상 로드할 문서와 필요할 때 읽을 문서를 구분한다.

항상 로드해도 되는 문서:

- `CLAUDE.md`
- 짧은 행동 가이드
- 금지사항과 안전 규칙

필요할 때만 읽는 문서:

- 도메인/서브모듈 KB
- `00-structure-map.md`
- `05-file-inventory.md`
- package-reference
- 운영 세부 문서

전체 KB를 `@import`하지 않는다.
AI는 먼저 얇은 인덱스를 읽고, 작업 경로와 키워드로 필요한 문서만 선택한다.

## 12. 문서 상태 표기

초기 KB에는 스텁과 초안이 섞일 수 있다.
상태를 명시해 AI가 검증 수준을 오해하지 않게 한다.

| 상태 | 의미 | 사용 기준 |
|---|---|---|
| `stub` | 자리만 있음 | 구현 사실로 사용 금지 |
| `draft` | 일부 코드 확인 | 작업 전 재검증 필요 |
| `verified` | 코드 경로와 파일 역할 확인 | 일반 작업 근거로 사용 가능 |
| `stale` | 코드 변경으로 재검증 필요 | 현재 코드 확인 후 갱신 |

상태는 문서 상단에 적는다.
`stub` 또는 `draft` 문서를 근거로 코드 수정을 단정하지 않는다.

## 13. YAML frontmatter

각 Markdown 문서 상단에는 YAML frontmatter를 둔다.
frontmatter는 본문 내용이 아니라 AI, 검색 도구, 자동화가 문서를 분류하기 위한 메타데이터다.

기본 필드:

| 필드 | 의미 |
|---|---|
| `type` | 문서 종류. 예: `guide`, `routing`, `domain`, `module`, `template`, `file-inventory`, `log` |
| `title` | 문서 제목 |
| `description` | 문서가 다루는 범위 한 줄 설명 |
| `tags` | 검색/분류용 키워드 |
| `resource` | 근거 코드 경로, 문서 경로, GHE 링크, PR 링크, 위키 링크 |
| `timestamp` | 문서 생성 또는 마지막 구조 갱신 기준 시각 |
| `status` | `stub`, `draft`, `verified`, `stale` |

예시:

```yaml
---
type: domain
title: 거래 내역 화면
description: 거래 내역 진입, 데이터, 렌더링, 필터, 상세 흐름을 설명한다.
tags: [transaction, history, android, moneytalk]
resource: <app-module>/src/main/java/<package>/transaction/TransactionActivity.kt
timestamp: 2026-07-03T15:51:26+09:00
status: draft
---
```

`resource`는 확인 가능한 근거를 적는다.
근거가 여러 개이면 대표 root path를 쓰고, 상세 근거는 본문 표에 둔다.
