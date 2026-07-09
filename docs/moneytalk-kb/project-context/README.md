---
type: package
title: Project Context KB
description: MoneyTalk 전체 구조, 핵심 시스템, 임계값, AI/프롬프트 운영 경계를 요약한다.
tags: [moneytalk, kb, project-context, architecture, threshold]
resource: docs/moneytalk-kb/project-context/
timestamp: 2026-07-09T05:10:00+09:00
status: draft
---

# Project Context KB

> 상태: draft
> 기준: 2026-07-09 현재 흡수된 프로젝트 컨텍스트/아키텍처 원문 내용을 KB 기준으로 재구성

이 패키지는 원문 문서를 그대로 보관하지 않는다. 루트 문서에 있던 프로젝트 전체 맥락을 AI 작업자가 빠르게 참조할 수 있도록 현재 코드 기준의 구조, 시스템 책임, 임계값만 추려서 관리한다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [01-system-overview.md](01-system-overview.md) | 앱 정의, 패키지 책임, 핵심 시스템, DB/AI 운영 경계 | 작업 시작 시 전체 맥락을 복원할 때 |
| [02-threshold-registry.md](02-threshold-registry.md) | SMS/가게명/카테고리 전파 유사도와 주요 파이프라인 상수 | 임계값 변경, 파싱/분류 정확도 조정 |
| [change-log.md](change-log.md) | 이 패키지 변경 로그 | 왜 구조/임계값 문서가 바뀌었는지 확인 |

## 소유 경계

- 전체 구조와 공통 임계값은 이 패키지가 설명한다.
- 화면별 진입점과 UI 요구사항은 `home`, `settings`, `history`, `transaction-*`, `category-*`, `chat` 등 각 화면 KB가 설명한다.
- SMS 내부 파이프라인은 [../sms-pipeline/README.md](../sms-pipeline/README.md), end-to-end 저장 흐름은 [../sms-parsing/README.md](../sms-parsing/README.md)를 본다.
- AI 채팅 실행 계약은 [../chat/README.md](../chat/README.md), App Functions는 [../app-functions/README.md](../app-functions/README.md)를 본다.
