---
type: feature-flow
title: Backup Restore 기능 흐름
description: 로컬 백업 export/import와 Google Drive 백업/복원 흐름을 설명한다.
tags: [moneytalk, backup, restore, feature-flow]
resource: app/src/main/java/com/sanha/moneytalk/core/util/DataBackupManager.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Backup Restore 기능 흐름

## Local export

```text
SettingsScreen
-> ExportDialog
-> SettingsViewModel.prepareBackup()/exportBackup()
-> DataBackupManager.createBackup()
-> SAF uri write
```

## Local import

```text
SAF uri
-> SettingsViewModel.importBackup()
-> DataBackupManager.parse/convert
-> SettingsViewModel.restoreData()
-> DAO insert/update
-> DataRefreshEvent.ALL_DATA_DELETED or relevant refresh
```

## Google Drive

```text
Google sign-in
-> SettingsViewModel.exportToGoogleDrive()
-> GoogleDriveHelper upload/list/download/delete
-> SettingsDataDialogs GoogleDriveDialog
```

## 검증 질문

1. 새 entity가 추가되면 `BackupData`, export, import conversion을 모두 갱신했는가?
2. restore가 기존 데이터와 충돌할 때 정책이 명확한가?
3. Drive sign-in 실패와 네트워크 실패 메시지가 사용자에게 전달되는가?
4. restore 후 화면 cache/설정 값이 최신화되는가?
