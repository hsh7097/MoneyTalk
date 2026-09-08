---
type: change-log
title: Project Context KB Change Log
description: Project Context KB 변경 이력
tags: [moneytalk, kb, project-context, changelog]
resource: docs/moneytalk-kb/project-context/
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# Change Log

## 2026-09-08

- 실제 코드와 공식 경쟁 제품 자료를 근거로 `04-product-improvements-20260908.md`를 추가하고 README/routing에 연결했다.
- 로컬 소비 브리핑·고정 지출 예상·롱클릭 단건 변경·수입 검색의 현재 구현, 비용/데이터 한계, 테스트 대상을 기록했다. 사용성 효과/운영 수익은 미측정이며 합계 정의·개인정보·광고 지급은 별도 후속으로 구분한다.

- 13개 feature 화면 도메인과 앱 전역/SMS/알림/백업/AI 실행의 책임 감사 문서를 추가했다.
- 기존 MVVM 유지 이유, 실제 분리한 state/mapper/service/Composable와 이미 적절해 유지한 경계를 구분했다.
- 테스트 항목과 검증 한계를 기록했다. 통합 빌드/기기 통과 여부는 실제 로그 확인 전에는 주장하지 않는다.

## 2026-07-09

- 루트 `AI_CONTEXT.md`, `ARCHITECTURE.md`를 그대로 옮기는 방식 대신, 프로젝트 전체 구조와 임계값을 KB용 참조 문서로 재작성했다.
- `01-system-overview.md`를 추가해 코드 소유 경계, 핵심 시스템, DB/AI 운영 경계, Golden Flow를 정리했다.
- `02-threshold-registry.md`를 추가해 SimilarityPolicy 계층과 SMS/카테고리 임계값을 한곳에서 찾을 수 있게 했다.
