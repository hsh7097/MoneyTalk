---
type: template
template_for: log
title: "<KB명> Change Index"
description: "<KB명> 루트 변경 이력 색인 템플릿이다."
tags: [kb, changelog, index, android, "<kb-name>"]
resource: "<kb-root>/00-change-index.md"
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# <KB명> Change Index

상세 설명은 각 패키지 `change-log.md`에 둔다.
이 파일은 전체 KB에서 어떤 영역이 바뀌었는지 찾기 위한 짧은 색인이다.

| 날짜 | 기준 | 영향 영역 | 갱신 문서 | 요약 |
|---|---|---|---|---|
| `<YYYY-MM-DD>` | `<확인한 코드/diff/문서/ref>` | `<영향 영역>` | `<갱신 문서>` | `<한 줄 요약>` |

## 기록 기준

- 루트 라우팅, 구조, 패키지 경계가 바뀌면 기록한다.
- 특정 패키지 상세 변경은 해당 패키지 `change-log.md`에 먼저 기록하고, 루트에는 한 줄 색인만 남긴다.
- 단순 오타/링크 수정은 의미가 바뀌지 않으면 생략할 수 있다.
- 기준에는 확인한 코드 경로, diff, 문서, ref/SHA 중 실제로 본 근거를 적는다.
