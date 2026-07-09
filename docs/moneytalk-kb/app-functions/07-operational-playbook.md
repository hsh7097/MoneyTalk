---
type: playbook
title: App Functions Operational Playbook
description: DB 점검, 복원 검증, 중복/카드/거래처 규칙 확인 때 App Functions를 우선 사용하는 순서를 정리한다.
tags: [moneytalk, app-functions, operations, database]
resource: app/src/main/java/com/sanha/moneytalk/core/appfunctions/
timestamp: 2026-07-09T02:45:00+09:00
status: draft
---

# 07 Operational Playbook

> 기준: 2026-07-09 현재 `core/appfunctions`와 흡수된 App Functions 카탈로그 확인

DB 상태를 확인해야 할 때는 App Functions를 먼저 고려한다.
App Functions 런타임 호출이 불가능하거나 debug 설치 앱에서 직접 DB 확인이 필요한 경우에만 `adb run-as com.sanha.moneytalk` 기반 DB 복사를 보조 경로로 둔다.
Play Store 설치 앱은 `run-as`가 막힐 수 있으므로 App Functions 또는 앱 UI 확인을 우선한다.

## 기본 확인 순서

1. 전체 상태 요약: `getDatabaseSnapshot`
2. 월간 요약: `getMonthlyFinanceSummary`
3. 지출 목록/합계: `getExpenses`, `getTotalExpense`, `getExpenseCategoryTotals`, `getExpensesByStore`, `getExpensesByCard`
4. 수입 목록/합계: `getIncomes`, `getTotalIncome`
5. 중복 후보: `getDuplicateExpenses`
6. 카드 설정: `getOwnedCards`, `getUsedCards`
7. 거래처 규칙: `getStoreRules`
8. SMS 제외 키워드: `getSmsExclusionKeywords`
9. 커스텀 카테고리/예산: `getCustomCategories`, `getBudgetStatus`
10. 복합 분석: `analyzeExpenses`

## 중복 데이터 확인

| 단계 | 확인 |
|---|---|
| 1 | `getDatabaseSnapshot`으로 `duplicateExpenseCount`, 수입/지출 총 건수를 확인한다. |
| 2 | 지출 중복은 `getDuplicateExpenses`로 후보를 확인한다. |
| 3 | 수입/환불 중복은 별도 App Function이 없으므로 `getIncomes`로 기간을 좁히거나 debug 앱이면 DB를 직접 조회한다. |
| 4 | 정리는 앱 설정의 중복 삭제 액션 또는 repository의 `deleteDuplicates()` 경로를 확인한다. App Functions 삭제 함수는 기본 비활성이다. |

## 카드 숨김/제외 확인

| 단계 | 확인 |
|---|---|
| 1 | `getOwnedCards`로 `isOwned=false` 카드를 확인한다. |
| 2 | `getExpensesByCard`로 숨김 카드 거래가 실제 저장되어 있는지 확인한다. |
| 3 | 화면 노출 문제는 [../filtering/02-filter-surfaces.md](../filtering/02-filter-surfaces.md)의 카드 숨김 계층과 `DataRefreshEvent.RefreshType.OWNED_CARD_UPDATED` 구독을 함께 확인한다. |

## 거래처 규칙 확인

| 단계 | 확인 |
|---|---|
| 1 | `getStoreRules`로 규칙 목록을 확인한다. |
| 2 | `getExpensesByStore`로 대상 거래처 기존 거래를 확인한다. |
| 3 | `upsertStoreRule`은 기존 거래에 즉시 소급 적용한다. |
| 4 | 복원 후 규칙 미적용 문제는 `StoreRuleSyncService.reapplyAllRules()`와 백업/복원 경로를 함께 확인한다. |

## SMS 제외 키워드 확인

| 단계 | 확인 |
|---|---|
| 1 | `getSmsExclusionKeywords`로 전체 키워드를 확인한다. |
| 2 | `addSmsExclusionKeyword`, `removeSmsExclusionKeyword`로 사용자/채팅 키워드를 변경한다. |
| 3 | 기존 데이터가 삭제되는 것이 아니라 화면/집계 노출에서 필터링되는지 [../filtering/README.md](../filtering/README.md)와 [../sms-settings/README.md](../sms-settings/README.md)를 확인한다. |

## 예산/월 시작일 확인

| 단계 | 확인 |
|---|---|
| 1 | `getBudgetStatus`로 카테고리별 예산과 사용액을 확인한다. |
| 2 | `getConfiguredMonthlyIncome`으로 월 수입 설정값을 확인한다. |
| 3 | `setBudget`, `setMonthlyIncome`, `setMonthStartDay` 변경 후 홈/채팅/월간 요약 기간 계산이 동일한 월 시작일을 쓰는지 확인한다. |

## 카테고리/하위 카테고리 확인

| 단계 | 확인 |
|---|---|
| 1 | `getCustomCategories`로 사용자 정의 카테고리를 확인한다. |
| 2 | `getExpenseCategoryTotals`와 `analyzeExpenses`에서 상위/하위 카테고리 포함 규칙을 확인한다. |
| 3 | 카테고리 분류 변경은 [../category-classification/README.md](../category-classification/README.md)와 [../embedding/README.md](../embedding/README.md)를 함께 본다. |

## KSP metadata 확인

함수 추가/삭제/enable 정책 변경 후에는 생성 파일을 확인한다.

```powershell
[xml]$x = Get-Content -Encoding UTF8 app\build\generated\ksp\debug\resources\assets\app_functions.xml
$x.appfunctions.appfunction.Count
($x.appfunctions.appfunction | Where-Object { $_.enabled_by_default -eq 'false' }).Count
```

기대값이 바뀌지 않았다면 현재 기준은 등록 50개, disabled 4개다.

## 주의사항

- App Function 본문은 DB 작업을 main thread에서 직접 수행하지 않도록 reader/repository 경로를 확인한다.
- 응답 모델 변경은 DB schema 변경이 아니어도 KSP 생성 metadata와 agent 호출 contract에 영향을 준다.
- 삭제성 함수는 `isEnabled=false` 정책을 유지한다.
- 루트 App Functions 원문은 이 KB로 흡수 후 삭제됐다. 실제 작업 라우팅은 이 KB 패키지를 우선한다.
