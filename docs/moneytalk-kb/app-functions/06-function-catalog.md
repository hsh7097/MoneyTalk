---
type: reference
title: App Functions Function Catalog
description: MoneyTalk에 등록된 Android App Functions 50개를 그룹별로 정리한다.
tags: [moneytalk, app-functions, catalog, agent]
resource: app/src/main/java/com/sanha/moneytalk/core/appfunctions/
timestamp: 2026-07-09T02:45:00+09:00
status: draft
---

# 06 Function Catalog

> 기준: 2026-07-09 현재 `MoneyTalkChatAppFunctions.kt`, `MoneyTalkFinanceAppFunctions.kt`, KSP 생성 `app_functions.xml` 확인

이 문서는 agent가 호출할 수 있는 App Functions 범위를 빠르게 확인하기 위한 목록이다.
현재 등록 함수는 50개이고, 삭제성 4개 함수만 `isEnabled=false`로 기본 비활성화되어 있다.

## 등록 상태

| 항목 | 값 |
|---|---|
| SDK | `androidx.appfunctions:appfunctions-*:1.0.0-alpha08` |
| 등록 enclosing class | `MoneyTalkChatAppFunctions`, `MoneyTalkFinanceAppFunctions` |
| factory 등록 | `MoneyTalkApplication.appFunctionConfiguration` |
| DI entry point | `MoneyTalkAppFunctionEntryPoint` |
| metadata | `app/src/main/res/xml/app_functions_app_metadata.xml` |
| KSP 생성 파일 | `app/build/generated/ksp/debug/resources/assets/app_functions.xml` |
| 총 등록 함수 | 50 |
| 기본 활성 함수 | 46 |
| 기본 비활성 함수 | 4 |

`1.0.0-alpha09`는 `compileSdk 37+`, `AGP 9.1.0+` 요구 조건 때문에 현재 프로젝트 설정에서는 적용하지 않는다.

## 상태/요약 조회

| 함수 | 상태 | 책임 |
|---|---|---|
| `canExposeChatOperationAppFunctions` | enabled | 채팅 작업용 App Functions 노출 가능 여부를 확인한다. |
| `getDatabaseSnapshot` | enabled | 지출/수입/중복/카드/거래처 규칙/카테고리/동기화 설정 요약을 반환한다. |
| `getMonthlyFinanceSummary` | enabled | 월간 가계 요약, 예산, 전월 비교, 최근 거래를 반환한다. |

## 지출 조회

| 함수 | 상태 | 책임 |
|---|---|---|
| `getTotalExpense` | enabled | 기간/카테고리 조건의 총 지출을 반환한다. |
| `getExpenseCategoryTotals` | enabled | 기간 내 카테고리별 지출 합계를 반환한다. |
| `getExpenses` | enabled | 기간/카테고리 조건의 지출 목록을 반환한다. |
| `getExpensesByStore` | enabled | 거래처명 또는 별칭 기준 지출 목록을 반환한다. |
| `getExpensesByCard` | enabled | 카드명 기준 지출 목록을 반환한다. |
| `getDailyExpenseTotals` | enabled | 일별 지출 합계를 반환한다. |
| `getMonthlyExpenseTotals` | enabled | 월별 지출 합계를 반환한다. |
| `getUncategorizedExpenses` | enabled | 미분류 지출 목록을 반환한다. |
| `searchExpenses` | enabled | 거래처/카테고리/카드/메모 키워드로 지출을 검색한다. |
| `getDuplicateExpenses` | enabled | 중복 지출 후보를 반환한다. |
| `analyzeExpenses` | enabled | 필터, 그룹, metric 기반 지출 분석 결과를 반환한다. |

`analyzeExpenses`는 기간, 카테고리, 거래처, 카드, 키워드, 금액 범위, 고정 지출, 통계 제외 조건을 조합한다.
상위 카테고리 요청에서 하위 카테고리 포함 여부가 필요하면 reader의 `includeSubcategories` 경로를 함께 확인한다.

## 수입 조회

| 함수 | 상태 | 책임 |
|---|---|---|
| `getTotalIncome` | enabled | 기간 내 총 수입을 반환한다. |
| `getIncomes` | enabled | 기간별 수입 목록을 반환한다. |
| `getConfiguredMonthlyIncome` | enabled | 설정된 월 수입을 반환한다. |
| `getCategoryRatio` | enabled | 수입 대비 지출 비율을 반환한다. |

## 설정/메타 조회

| 함수 | 상태 | 책임 |
|---|---|---|
| `getUsedCards` | enabled | 거래 내역에서 사용된 카드명 목록을 반환한다. |
| `getOwnedCards` | enabled | 카드 표시/숨김 설정 목록을 반환한다. |
| `getStoreRules` | enabled | 거래처 규칙 목록을 반환한다. |
| `getCustomCategories` | enabled | 사용자 정의 카테고리 목록을 반환한다. |
| `getSmsExclusionKeywords` | enabled | SMS 제외 키워드 목록을 반환한다. |
| `getBudgetStatus` | enabled | 예산 설정과 사용 현황을 반환한다. |

## 거래 수정

| 함수 | 상태 | 책임 |
|---|---|---|
| `addExpense` | enabled | 수동 지출을 추가한다. |
| `updateExpenseCategory` | enabled | 지출 ID 기준 카테고리를 변경한다. |
| `updateExpenseCategoryByStore` | enabled | 거래처 별칭 기준 지출 카테고리를 일괄 변경한다. |
| `updateExpenseCategoryByKeyword` | enabled | 키워드 기준 지출 카테고리를 일괄 변경한다. |
| `updateExpenseMemo` | enabled | 지출 메모를 변경한다. |
| `updateExpenseStoreName` | enabled | 지출 거래처명을 변경한다. |
| `updateExpenseAmount` | enabled | 지출 금액을 변경한다. |
| `updateExpenseFixed` | enabled | 지출 고정 여부를 변경한다. |
| `updateExpenseStatsExcluded` | enabled | 지출 통계 제외 여부를 변경한다. |
| `addIncome` | enabled | 수동 수입을 추가한다. |
| `updateIncomeMemo` | enabled | 수입 메모를 변경한다. |
| `updateIncomeCategoryByKeyword` | enabled | 수입 출처/설명 키워드 기준 카테고리를 변경한다. |
| `updateIncomeRecurringByKeyword` | enabled | 수입 출처/설명 키워드 기준 고정 수입 여부를 변경한다. |

수정 함수는 `MoneyTalkChatAppFunctionReader`에서 validation과 repository 호출을 수행한다.
DB write 이후 화면 갱신이 필요하면 기존 `DataRefreshEvent` 발행 경로를 확인한다.

## 설정 수정

| 함수 | 상태 | 책임 |
|---|---|---|
| `addSmsExclusionKeyword` | enabled | SMS 제외 키워드를 추가한다. |
| `removeSmsExclusionKeyword` | enabled | SMS 제외 키워드를 제거한다. 기본 키워드는 제거되지 않는다. |
| `setCardOwnership` | enabled | 카드 표시/숨김 상태를 설정한다. 등록되지 않은 카드는 수동 카드로 추가한다. |
| `addManualCard` | enabled | 문자 설정에서 사용할 수동 카드를 추가한다. |
| `setBudget` | enabled | 카테고리 예산을 설정한다. |
| `setMonthlyIncome` | enabled | 월 수입 설정값을 변경한다. |
| `setMonthStartDay` | enabled | 월 시작일 설정값을 변경한다. |
| `upsertStoreRule` | enabled | 거래처 규칙을 추가하거나 갱신하고 기존 거래에 소급 적용한다. |
| `addCustomCategory` | enabled | 사용자 정의 카테고리를 추가한다. |

## 기본 비활성 삭제 함수

| 함수 | 상태 | 책임 |
|---|---|---|
| `deleteExpense` | disabled | 지출 ID 기준 지출을 삭제한다. |
| `deleteExpensesByKeyword` | disabled | 키워드가 포함된 지출을 일괄 삭제한다. |
| `deleteDuplicateExpenses` | disabled | 중복 지출 항목을 삭제한다. |
| `deleteStoreRule` | disabled | 거래처 규칙 ID 또는 키워드 기준으로 규칙을 삭제한다. |

삭제성 함수는 등록되어 있어도 기본 호출 가능 상태로 두지 않는다.
삭제가 필요하면 사용자 승인을 먼저 받고, 가능하면 앱 UI 또는 repository의 안전한 삭제 경로를 우선 확인한다.

## 변경 시 확인

1. `@AppFunction` KDoc이 agent가 이해할 수 있을 만큼 구체적인가?
2. 필수 parameter 누락, 잘못된 날짜, 음수 금액 같은 입력이 실패 응답으로 정리되는가?
3. DB write 함수가 필요한 refresh event를 발행하는가?
4. KSP 생성 `app_functions.xml`의 등록 수와 disabled 수가 기대와 맞는가?
5. 이 문서, [02-data-contract.md](02-data-contract.md), [07-operational-playbook.md](07-operational-playbook.md)가 함께 갱신됐는가?
