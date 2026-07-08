---
type: feature
title: Backup Restore 기능
description: 로컬 export/import, Google Drive 백업/복원, 전체 데이터 삭제 흐름을 설명한다.
tags: [moneytalk, backup, restore, google-drive, feature]
resource: app/src/main/java/com/sanha/moneytalk/core/util/DataBackupManager.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Backup Restore 기능

> 상태: draft
> 기준: 2026-07-08 현재 `DataBackupManager`, `SettingsDataDialogs`, `SettingsViewModel`, `GoogleDriveHelper` 확인

Backup Restore 기능은 앱 데이터를 JSON/CSV로 내보내거나 가져오고, Google Drive에 백업 파일을 저장/복원/삭제하는 흐름이다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [01-feature-flow.md](01-feature-flow.md) | export/import/Drive 흐름을 설명한다. | 백업, 복원, Drive 작업 |
| [change-log.md](change-log.md) | Backup Restore KB 변경 로그다. | 문서 변경 이유 확인 |
| [../settings/README.md](../settings/README.md) | 설정 화면 KB다. | UI dialog와 action 연결 확인 |
| [../finance-data/README.md](../finance-data/README.md) | DB/Entity KB다. | 백업 대상 entity 변경 |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `core/util/DataBackupManager.kt` | backup data model, export/import 변환 |
| `core/util/GoogleDriveHelper.kt` | Drive 파일 업로드/다운로드/삭제 |
| `feature/settings/ui/SettingsDataDialogs.kt` | export/import/Drive dialog UI |
| `feature/settings/ui/SettingsViewModel.kt` | backup orchestration, restoreData, deleteAllData |
| `core/database/AppDatabase.kt` | 백업 대상 DAO/entity 확인 |
