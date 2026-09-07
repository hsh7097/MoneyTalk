---
type: changelog
title: Backup Restore KB 변경 로그
description: Backup Restore KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, backup, changelog]
resource: docs/moneytalk-kb/backup-restore/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Backup Restore KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-09-07 | JSON 백업 모델·복원 변환과 엔티티 비교 | 수입 분류/출처/메모 및 지출·수입 원등록시각을 저장·복원 양쪽에 반영 | `01-feature-flow.md` | 선택 필드로 추가해 구형 JSON 기본값을 유지. 자동/수동 수입·지출 JSON 왕복 회귀 2개와 기존 하위호환 테스트를 보강. |
| 2026-09-07 | `SettingsViewModel.deleteAllData`, `SmsFallbackScheduler` | 전체 삭제 전 큐 세대 증가와 OS 예약 취소 계약 추가 | `01-feature-flow.md` | 진행 작업 종료를 기다린 뒤 대기 후보를 제거해 이전 요청이 다시 적재되지 않게 함. |
| 2026-07-08 | `DataBackupManager`, `SettingsDataDialogs`, `SettingsViewModel`, `GoogleDriveHelper` 확인 | Backup Restore 기능 KB 생성 | `README.md`, `01-feature-flow.md` | 로컬/Drive 백업 복원과 DB entity contract 변경 위치를 기능 KB로 분리. |
