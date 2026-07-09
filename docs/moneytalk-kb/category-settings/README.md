---
type: domain
title: Category Settings 도메인
description: 사용자 카테고리 추가/수정/삭제 설정 화면을 설명한다.
tags: [moneytalk, category-settings, domain, compose]
resource: app/src/main/java/com/sanha/moneytalk/feature/categorysettings/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Category Settings 도메인

> 상태: draft
> 기준: 2026-07-08 현재 `feature/categorysettings/ui/**`, `CustomCategoryRepository` 확인

Category Settings는 사용자가 지출/수입 custom category를 관리하는 설정 화면이다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| 이 문서 | 화면 entry, 핵심 파일, 수정 시 주의점을 정리한다. | 카테고리 설정 화면 작업 |
| [package-reference/README.md](package-reference/README.md) | entry/data/rendering/checklist로 나눈 세부 개발 문서 인덱스다. | Category Settings를 실제 수정하기 전 |
| [change-log.md](change-log.md) | KB 변경 로그다. | 문서 변경 이유 확인 |
| [../category-classification/README.md](../category-classification/README.md) | 카테고리 분류 기능 KB다. | custom category가 자동 분류/수동 수정에 미치는 영향 확인 |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `CategorySettingsActivity.kt` | 설정 Activity entry |
| `CategorySettingsScreen.kt` | category type tab, category list item, add/edit/delete UI |
| `CategorySettingsViewModel.kt` | custom category load/save/delete, validation |
| `core/database/CustomCategoryRepository.kt` | custom category DB repository |

## 검증 질문

1. custom category 추가/삭제 후 `CategoryProvider` 또는 category entry list가 갱신되는가?
2. 기존 거래의 category displayName과 삭제/변경 정책이 충돌하지 않는가?
3. 지출/수입 category type이 올바르게 분리되는가?
