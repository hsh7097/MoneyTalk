---
type: domain
title: Store Rule Settings 도메인
description: 거래처 규칙과 가게명 기반 분류 설정 화면을 설명한다.
tags: [moneytalk, store-rule, settings, domain]
resource: app/src/main/java/com/sanha/moneytalk/feature/storerulesettings/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Store Rule Settings 도메인

> 상태: draft
> 기준: 2026-07-08 현재 `feature/storerulesettings/ui/**`, `StoreRuleRepository` 확인

Store Rule Settings는 거래처명/키워드 기반 규칙을 관리해 카테고리 분류와 거래 일괄 적용에 영향을 주는 화면이다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| 이 문서 | 화면 entry, 핵심 파일, 분류/일괄 적용 영향 범위를 정리한다. | 거래처 규칙 설정 화면 작업 |
| [change-log.md](change-log.md) | KB 변경 로그다. | 문서 변경 이유 확인 |
| [../category-classification/README.md](../category-classification/README.md) | 카테고리 분류 기능 KB다. | 규칙이 자동 분류에 미치는 영향 |
| [../transaction-mutation/README.md](../transaction-mutation/README.md) | 거래 일괄 변경 기능 KB다. | 같은 거래처 일괄 적용 확인 |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `StoreRuleSettingsActivity.kt` | 거래처 규칙 설정 Activity entry |
| `StoreRuleSettingsScreen.kt` | 규칙 목록/추가/삭제 UI |
| `StoreRuleSettingsViewModel.kt` | 규칙 load/save/delete, refresh |
| `feature/home/data/StoreRuleRepository.kt` | 규칙 DB repository |
| `feature/home/data/StoreRuleSyncService.kt` | 규칙 동기화/적용 보조 |
| `feature/storerulesettings/ui/coachmark/StoreRuleCoachMark.kt` | Store Rule 화면 코치마크 |
