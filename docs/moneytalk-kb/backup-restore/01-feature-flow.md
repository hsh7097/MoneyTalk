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
-> DataBackupManager.createBackupJson()
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

## JSON 거래 메타데이터 보존

- `ExpenseBackup`은 기존 메모·분류·고정·통계 제외·거래유형·방향과 함께 원래 `createdAt`을 저장하고 복원한다.
- `IncomeBackup`은 기존 수입 필드에 `category`, `source`, `memo`, `createdAt`을 포함한다. 사용자 수정 분류·출처·메모와 원등록시각이 JSON 내보내기/복원 사이에 보존된다.
- 새 필드는 선택 필드이며 기존 JSON version 2를 유지한다. 필드가 없는 이전 백업은 수입 카테고리 `미분류`, 출처 빈 문자열, 메모 null로 복원하고, 지출·수입의 `createdAt`은 기존 동작처럼 복원 시각을 사용한다. 예전 백업에 기록되지 않은 원래 값은 복구할 수 없다.
- 거래 `dateTime`과 원등록시각 `createdAt`, 최상위 백업 생성 시각은 서로 다른 값이다. Room 행 ID는 기존처럼 복원 시 재생성한다.
- `DataBackupManagerTest`는 자동/수동 수입과 지출의 합성 데이터 JSON 왕복, 전체 엔티티 값 보존(행 ID 제외), 구형 JSON의 기본값·복원 시각 대체를 확인한다. Room 스키마는 변경하지 않는다.

## Google Drive

```text
Google sign-in
-> SettingsViewModel.exportToGoogleDrive()
-> GoogleDriveHelper upload/list/download/delete
-> SettingsDataDialogs GoogleDriveDialog
```

## 검증 질문

전체 데이터 삭제는 `ClassificationState.withRegistrationsPaused`로 실행 중인 분류 종료를 기다리고, `SmsFallbackScheduler.clearPending()`으로 대기 후보와 예약 Job을 지운 뒤 거래 테이블을 비운다. 이전 큐 세대로 수신한 요청은 삭제 후 다시 등록할 수 없다. 수동 백업에는 이 임시 수집 큐를 포함하지 않는다.

1. 새 entity가 추가되면 `BackupData`, export, import conversion을 모두 갱신했는가?
2. restore가 기존 데이터와 충돌할 때 정책이 명확한가?
3. Drive sign-in 실패와 네트워크 실패 메시지가 사용자에게 전달되는가?
4. restore 후 화면 cache/설정 값이 최신화되는가?
