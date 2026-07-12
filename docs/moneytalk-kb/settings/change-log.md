---
type: changelog
title: Settings KB 변경 로그
description: Settings KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, settings, changelog]
resource: docs/moneytalk-kb/settings/
timestamp: 2026-07-12T00:00:00+09:00
status: draft
---

# Settings KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-07-12 | SM-F966N, `font_scale=2.0`, `ExportDialog` 실기기 QA | 내보내기 형식 선택지를 세로 전체 폭으로 변경, 즉시 실행 메뉴 계약 보강 | `package-reference/03-rendering-action.md`, `package-reference/05-menu-map.md`, `../ui-map/01-screen-composable-index.md` | CSV 세로 붕괴를 제거하고 중복 정리/가이드 초기화 QA 주의사항을 기록. |
| 2026-07-12 | Firebase AI Logic/App Check 전환과 release emulator 순회 | 사용자 API key 설정 계약 제거, AI 서비스 상태 계약 반영 | `README.md`, `00-structure-map.md`, `package-reference/02-data-viewmodel.md`, `03-rendering-action.md`, `05-menu-map.md`, `05-file-inventory.md` | Settings에는 API key row/dialog가 없고 RTDB service/model 설정과 Firebase AI Logic만 사용함을 기록. |
| 2026-07-08 | `feature/settings/ui/**`, `SettingsViewModel.kt`, `DataBackupManager.kt`, `OwnedCardRepository` 확인 | Settings 화면 KB 생성 | `README.md`, `00-structure-map.md`, `package-reference/**`, `05-file-inventory.md` | 설정 탭의 예산/API key/백업/Drive/카드 보유/코치마크 작업 시작점을 분리. |
| 2026-07-09 | `SettingsScreen`, `SettingsViewModel`, `SettingsDataDialogs`, `BudgetBottomSheet` 확인 | 설정 메뉴 상세 지도 추가 | `README.md`, `package-reference/README.md`, `package-reference/05-menu-map.md` | 테마, 예산, AI 크레딧, 카테고리, 알림, SMS 설정, 백업/복원, 앱 정보 메뉴를 하위 Activity/Dialog/저장소 기준으로 정리. |
