---
type: package
title: Project Context KB
description: MoneyTalk 전체 구조, 핵심 시스템, 임계값, AI/프롬프트 운영 경계를 요약한다.
tags: [moneytalk, kb, project-context, architecture, threshold]
resource: docs/moneytalk-kb/project-context/
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# Project Context KB

> 상태: draft
> 기준: 흡수된 프로젝트 컨텍스트와 2026-09-08 구조·제품·금융 UI 감사 문서

이 패키지는 프로젝트 전체 맥락을 빠르게 참조하도록 현재 코드의 구조·시스템 책임·임계값과 제품/UI 변경 판단을 관리한다. 구현 계약, 변경안, 실행 검증 범위를 문서별로 구분한다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [01-system-overview.md](01-system-overview.md) | 앱 정의, 패키지 책임, 핵심 시스템, DB/AI 운영 경계 | 작업 시작 시 전체 맥락을 복원할 때 |
| [02-threshold-registry.md](02-threshold-registry.md) | SMS/가게명/카테고리 전파 유사도와 주요 파이프라인 상수 | 임계값 변경, 파싱/분류 정확도 조정 |
| [03-screen-function-architecture-audit.md](03-screen-function-architecture-audit.md) | 13개 화면 도메인과 공통 기능의 책임 분리/유지 판단 및 검증 지점 | 화면별 클래스, MVVM/MVI, 가독성·패턴 정리 판단 |
| [04-product-improvements-20260908.md](04-product-improvements-20260908.md) | 공식 제품 자료에 근거한 로컬 소비 브리핑·고정 지출 예상·단건 정리의 현재 구현과 한계 | 다시 열 이유, 정리 동선, 비용 없는 기본 확인의 선택 근거와 검증 범위 |
| [05-finance-ui-design-system-20260908.md](05-finance-ui-design-system-20260908.md) | 금융 UI의 중립 표면·의미 색상·타이포·공통 행·큰 글자 기준 | 공통 테마, 거래/설정 행, CTA/차트/탭의 시각 규칙 확인 |
| [06-finance-ux-plan-20260908.md](06-finance-ux-plan-20260908.md) | 공식 벤치마크 자료와 기획·디자인 교차 검토, 화면 위계·필터 동선 감사 | 변경 이유와 보존할 금융 의미, 관찰한 화면과 미검증 범위 구분 |
| [07-finance-ui-validation-20260908.md](07-finance-ui-validation-20260908.md) | 금융 UI의 빌드·자동 검사·합성 데이터 화면 검증 결과 | 실제 통과 결과, 중간 실패와 후속 검사, 최신 캡처의 적용 범위 확인 |
| [08-home-ledger-ux-validation-20260909.md](08-home-ledger-ux-validation-20260909.md) | 홈·가계부 후속 기능의 최종 검증과 기능별 커밋 | 주간 근거·고정 제외·의미 색상·차트 날짜 선택·접히는 요약·미정리 편집 연결 확인 |
| [change-log.md](change-log.md) | 이 패키지 변경 로그 | 왜 구조/임계값 문서가 바뀌었는지 확인 |

## 소유 경계

- 전체 구조와 공통 임계값은 이 패키지가 설명한다.
- 화면별 진입점과 UI 요구사항은 `home`, `settings`, `history`, `transaction-*`, `category-*`, `chat` 등 각 화면 KB가 설명한다.
- SMS 내부 파이프라인은 [../sms-pipeline/README.md](../sms-pipeline/README.md), end-to-end 저장 흐름은 [../sms-parsing/README.md](../sms-parsing/README.md)를 본다.
- AI 채팅 실행 계약은 [../chat/README.md](../chat/README.md), App Functions는 [../app-functions/README.md](../app-functions/README.md)를 본다.
