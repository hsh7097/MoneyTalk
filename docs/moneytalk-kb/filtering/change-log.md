---
type: changelog
title: Filtering KB 변경 로그
description: Filtering KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, filtering, changelog]
resource: docs/moneytalk-kb/filtering/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Filtering KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-07-08 | `HistoryFilter.kt`, `CardVisibilityFilter.kt`, `StatsExclusionClassifier.kt`, SMS 제외 repository 확인 | Filtering 기능 KB 생성 | `README.md`, `01-feature-flow.md` | 화면 필터, 카드 숨김, 통계 제외, SMS 제외 설정의 책임을 기능 KB로 분리. |
| 2026-07-09 | `HistoryViewModel`, `HistoryFilter`, `CardVisibilityFilter`, `StatsExclusionClassifier`, `DataBackupManager`, SMS 제외 repository 확인 | 필터 계층 상세화 | `README.md`, `02-filter-surfaces.md` | 내역 화면 필터, 카드 노출 필터, 통계 제외, SMS 입력 제외, export 필터의 적용 시점과 영향 범위를 분리. |
