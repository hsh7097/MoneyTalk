---
type: domain
title: Settings 도메인
description: MoneyTalk 설정 탭의 예산, API 키, 테마, 백업/복원, 카드 보유, Google Drive, 코치마크 흐름을 설명한다.
tags: [moneytalk, settings, domain, compose]
resource: app/src/main/java/com/sanha/moneytalk/feature/settings/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Settings 도메인

> 상태: draft
> 기준: 2026-07-08 현재 `feature/settings/ui/**`, `SettingsViewModel.kt`, `DataBackupManager.kt`, `OwnedCardRepository` 확인

Settings는 네 번째 하단 탭이며 앱 환경, 예산, AI/API, 데이터 백업/복원, Google Drive, 카드 보유 여부, SMS/거래처/카테고리 설정 진입을 담당한다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-structure-map.md](00-structure-map.md) | Settings UI/data/dialog 파일 구조를 정리한다. | 변경 파일이 Settings 내부 어느 책임인지 판단할 때 |
| [package-reference/README.md](package-reference/README.md) | entry/data/rendering/checklist 문서 인덱스다. | 실제 수정 위치를 더 좁힐 때 |
| [package-reference/01-entry-screen.md](package-reference/01-entry-screen.md) | Settings 탭 진입과 보조 Activity 진입을 설명한다. | 설정 화면 routing/Activity 이동 |
| [package-reference/02-data-viewmodel.md](package-reference/02-data-viewmodel.md) | SettingsViewModel intent, repository, backup/drive/credit 흐름을 설명한다. | 설정 값 저장, 백업, 예산, 카드 보유 |
| [package-reference/03-rendering-action.md](package-reference/03-rendering-action.md) | SettingsScreen, dialog, bottom sheet, action 연결을 설명한다. | Compose UI, dialog, 버튼 액션 |
| [package-reference/04-files-checklist.md](package-reference/04-files-checklist.md) | 수정 전후 확인 파일과 검증 질문이다. | 리뷰 전 누락 점검 |
| [package-reference/05-menu-map.md](package-reference/05-menu-map.md) | 설정 섹션별 메뉴, 하위 Activity/Dialog, 저장소 영향 범위를 설명한다. | 설정 row 추가/정렬, 하위 메뉴 영향 점검 |
| [05-file-inventory.md](05-file-inventory.md) | Settings 전체 파일 역할 인덱스다. | 파일 후보가 애매할 때 |
| [change-log.md](change-log.md) | Settings KB 변경 로그다. | 문서 변경 이유 확인 |

## 기준 패키지

- `app/src/main/java/com/sanha/moneytalk/feature/settings/ui/`

## 핵심 흐름

```text
NavGraph -> SettingsScreen
-> SettingsViewModel.uiState / onIntent()
-> SettingsDataStore, BudgetDao, OwnedCardRepository, AiCreditRepository
-> DataBackupManager / GoogleDriveHelper
-> Settings*Dialogs / BudgetBottomSheet
```

## 작업 판단

- 설정 row 추가/정렬/UI는 `package-reference/03-rendering-action.md`를 본다.
- 각 설정 메뉴와 하위 화면/저장소 영향은 `package-reference/05-menu-map.md`를 본다.
- API key, 테마, 월 시작일, 예산, 카드 보유는 `package-reference/02-data-viewmodel.md`를 본다.
- 백업/복원/Google Drive는 [backup-restore/README.md](../backup-restore/README.md)를 같이 본다.
- AI 크레딧/광고 정책은 [budget-credit-monetization/README.md](../budget-credit-monetization/README.md)를 같이 본다.
- SMS/거래처/카테고리 설정 Activity 이동은 해당 화면 KB도 같이 본다.
