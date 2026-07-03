---
type: module
title: Finance Data 서브모듈
description: MoneyTalk 금융 데이터, Room DB, DAO, Repository 작업의 진입점이다.
tags: [moneytalk, finance-data, room, repository]
resource: app/src/main/java/com/sanha/moneytalk/core/database/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# Finance Data 서브모듈

> 상태: draft
> 기준: 2026-07-03 현재 `core/database`와 `feature/home/data` 소스 확인

Finance Data는 지출/수입/예산/카테고리/거래처/크레딧 등 앱의 주요 금융 데이터를 저장하고 조회하는 영역이다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-structure-map.md](00-structure-map.md) | DB, DAO, Repository 파일 구조를 정리한다. | 데이터 변경 파일을 분류할 때 본다. |
| [01-purpose-architecture.md](01-purpose-architecture.md) | Room DB와 repository 책임을 설명한다. | schema 또는 repository 책임 변경 전 본다. |
| [02-how-to-use.md](02-how-to-use.md) | 작업 유형별 참조 순서를 안내한다. | DB/DAO/Repository 수정 전 본다. |
| [03-extension-points.md](03-extension-points.md) | entity/DAO/repository 확장 지점을 정리한다. | 신규 데이터 필드나 저장소 추가 시 본다. |
| [04-files-checklist.md](04-files-checklist.md) | 수정 전후 체크리스트다. | migration, DI, refresh 누락 점검 시 본다. |
| [05-file-inventory.md](05-file-inventory.md) | 전체 파일 역할 인덱스다. | 파일 후보가 애매할 때 본다. |
| [change-log.md](change-log.md) | 서브모듈 KB 변경 로그다. | 문서 변경 이유를 확인할 때 본다. |

## 기준 패키지

- `app/src/main/java/com/sanha/moneytalk/core/database/`
- `app/src/main/java/com/sanha/moneytalk/feature/home/data/`
- `app/src/main/java/com/sanha/moneytalk/core/di/DatabaseModule.kt`
- `app/src/main/java/com/sanha/moneytalk/core/di/RepositoryModule.kt`
