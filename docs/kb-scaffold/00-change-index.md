---
type: log
title: KB Scaffold Change Index
description: docs/kb-scaffold 가이드 변경 이력을 짧게 기록하는 루트 변경 색인이다.
tags: [kb-scaffold, changelog, log, android, moneytalk]
resource: docs/kb-scaffold/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# KB Scaffold Change Index

이 파일은 `docs/kb-scaffold` 가이드 변경 이력을 짧게 남기는 색인이다.
상세 설명은 각 가이드 문서 본문에 둔다.

| 날짜 | 기준 | 영향 영역 | 갱신 문서 | 요약 |
|---|---|---|---|---|
| 2026-07-03 | OKF frontmatter 피드백 | YAML frontmatter | `docs/kb-scaffold/**/*.md` | 스카폴드 전체 Markdown 문서에 `type/title/description/tags/resource/timestamp/status` YAML frontmatter를 추가하고, README/원칙/워크플로우에 frontmatter 작성 기준을 반영. |
| 2026-07-03 | 대화 기반 요구사항 반영 | 필수 구조, 로그 계약, 서브모듈 경계 | `README.md`, `01-principles.md`, `02-folder-structure.md`, `03-authoring-workflow.md`, `04-automation-package-routing.md`, `05-team-rollout.md`, `templates/*` | 도메인/서브모듈 필수 파일, `00-structure-map.md`, 파일별 역할, KB 수정 로그 위치, 서브모듈의 도메인 의존 금지 기준을 스카폴드에 추가. |
| 2026-07-03 | 서브모듈 docs 구조 피드백 | 서브모듈 하위 책임 패키지 | `02-folder-structure.md`, `03-authoring-workflow.md`, `templates/module-readme.md`, `templates/structure-map.md` | `sms-pipeline/reader`, `sms-pipeline/filter`, `sms-pipeline/fast-path`, `app-functions/finance`처럼 서브모듈 내부가 역할별로 분리된 경우 docs에도 동일한 하위 패키지를 만들도록 규칙 추가. |
| 2026-07-03 | 파일 정리 깊이 기준 피드백 | 파일 인벤토리 | `02-folder-structure.md`, `03-authoring-workflow.md`, `templates/module-readme.md`, `templates/structure-map.md`, `templates/file-inventory.md` | 모든 파일을 같은 깊이로 설명하지 않고 폴더 요약, 핵심 파일 상세, 전체 파일 인벤토리의 계층형 KB 작성 기준 추가. |
| 2026-07-03 | Claude Code 문서 구조 정리 | Claude Code 문서 구조 연동 | `README.md`, `01-principles.md`, `02-folder-structure.md`, `03-authoring-workflow.md`, `04-automation-package-routing.md`, `05-team-rollout.md`, `templates/*` | `CLAUDE.md` 얇은 인덱스, `.claude/docs` 운영 문서, `.claude/docs/kb` 코드 분석 KB, 문서 상태(`stub/draft/verified/stale`), 필요 시 읽기 정책을 스카폴드에 추가. |
| 2026-07-03 | README 설명 보강 피드백 | 파일 역할 설명, 특정 이슈 참조 제거 | `README.md`, `03-authoring-workflow.md`, `00-change-index.md` | README에 상위 문서와 템플릿별 역할을 추가하고, 특정 이슈 번호 기반 설명을 제거해 문서 구조 원칙만 남김. |
| 2026-07-03 | 서브모듈 README 사용성 피드백 | README 라우팅 표 | `templates/module-readme.md`, `templates/domain-readme.md` | `먼저 볼 파일`을 단순 링크 목록이 아니라 문서 역할과 참조 시점을 포함한 표로 작성하도록 템플릿 보강. |
| 2026-07-03 | KB 생성 규칙 보강 | README 파일 설명 계약 | `README.md`, `01-principles.md`, `02-folder-structure.md`, `03-authoring-workflow.md` | KB 생성 시 README에 각 생성 파일이 어떤 파일인지, 언제 참조해야 하는지 반드시 명시하도록 원칙과 작성 절차에 추가. |
| 2026-07-03 | 재귀 리뷰 반영 | 파일 인벤토리 필수화, README 표 계약, package-reference 템플릿 | `README.md`, `01-principles.md`, `02-folder-structure.md`, `03-authoring-workflow.md`, `04-automation-package-routing.md`, `05-team-rollout.md`, `templates/*` | `05-file-inventory.md`를 도메인/서브모듈 필수 인덱스로 승격하고, README 표에 모든 필수 파일을 포함하도록 템플릿을 보강. package-reference 단일 설명 템플릿은 실제 생성 구조와 같은 `templates/package-reference/` 디렉터리로 분리. |
| 2026-07-03 | 공유 전 재검토 반영 | 필수 파일 계약 정합성 | `README.md`, `04-automation-package-routing.md`, `05-team-rollout.md` | 작은 모듈 예외를 필수 인벤토리 계약과 맞추고, 자동화 필수 파일 표를 전체 필수 파일로 확장. README 결정 범위에서 이미 확정한 필수 문서 수 항목 제거. |
| 2026-07-03 | MoneyTalk 적용 전 정리 | 서비스별 예시 정합성 | `README.md`, `01-principles.md`, `02-folder-structure.md`, `03-authoring-workflow.md`, `04-automation-package-routing.md`, `05-team-rollout.md`, `templates/*` | 특정 서비스 전용 예시와 legacy UI 용어를 제거하고 MoneyTalk의 Compose, SMS pipeline, App Functions, finance data 기준 예시로 교체. |
| 2026-07-03 | 기능 단위 KB 요구 반영 | feature slice KB | `README.md`, `01-principles.md`, `02-folder-structure.md`, `03-authoring-workflow.md`, `04-automation-package-routing.md`, `05-team-rollout.md`, `templates/feature-readme.md`, `templates/feature-flow.md` | 문자 파싱, 카테고리 분류, App Functions처럼 여러 화면/모듈을 가로지르는 end-to-end 기능 KB 작성 기준과 템플릿 추가. |
| 2026-07-08 | Gmarket `.claude/docs/kb-scaffold` 최신 기준 반영 | 최소/확장 계약, 샘플 검증, 템플릿 인덱스 | `README.md`, `01-principles.md`, `03-authoring-workflow.md`, `04-team-share-summary.md`, `templates/README.md`, `templates/kb-readme.md`, `templates/change-index.md`, `templates/structure-map.md`, `templates/agent-routing.md` | MoneyTalk 기존 KB 명명은 유지하면서, `README.md + change-log.md`로 작게 시작하고 필요 시 구조 지도/파일 인벤토리/package-reference를 확장하는 최신 scaffold 기준을 반영. |
