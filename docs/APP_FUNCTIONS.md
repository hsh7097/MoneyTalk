# Android App Functions

MoneyTalk는 Android App Functions를 통해 assistant/agent가 앱 내부 데이터를 조회하거나 일부 설정을 수정할 수 있게 노출한다.

이 문서는 이후 작업자가 DB 확인, 복원 검증, 중복 데이터 확인, 카드/거래처 규칙 확인을 할 때 App Functions를 우선 고려할 수 있도록 남기는 작업 메모다.

## 현재 상태

- SDK: `androidx.appfunctions:appfunctions-*:1.0.0-alpha08`
- 최신 검토 기준: `1.0.0-alpha09`는 `compileSdk 37+`, `AGP 9.1.0+`가 필요해 현재 프로젝트에서는 미적용
- 등록 파일: `app/src/main/res/xml/app_functions_app_metadata.xml`
- 생성 확인 파일: `app/build/generated/ksp/debug/resources/assets/app_functions.xml`
- 구현 위치:
  - `app/src/main/java/com/sanha/moneytalk/core/appfunctions/MoneyTalkFinanceAppFunctions.kt`
  - `app/src/main/java/com/sanha/moneytalk/core/appfunctions/MoneyTalkChatAppFunctions.kt`
  - `app/src/main/java/com/sanha/moneytalk/core/appfunctions/MoneyTalkChatAppFunctionReader.kt`
  - `app/src/main/java/com/sanha/moneytalk/MoneyTalkApplication.kt`
- 총 등록 함수: 50개
- 기본 활성 함수: 46개
- 기본 비활성 함수: 4개 삭제성 함수

## 작업 시 사용 기준

DB 상태를 확인해야 하면 우선 아래 순서로 본다.

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

App Functions 런타임으로 호출할 수 없는 환경이거나 debug 설치 앱이면 `adb run-as com.sanha.moneytalk`로 DB를 복사해 확인한다. Play Store 설치 앱은 `run-as`가 안 될 수 있으므로 App Functions 또는 앱 UI를 우선 고려한다.

## 조회 함수

### 상태/요약

- `canExposeChatOperationAppFunctions`: 노출된 채팅 작업 함수 사용 가능 여부 확인
- `getDatabaseSnapshot`: 지출/수입/중복/카드/거래처 규칙/카테고리/동기화 설정 요약
- `getMonthlyFinanceSummary`: 월간 가계 요약, 예산, 전월 비교, 최근 거래

### 지출

- `getTotalExpense`: 기간/카테고리별 총 지출
- `getExpenseCategoryTotals`: 기간 내 카테고리별 지출 합계
- `getExpenses`: 기간/카테고리별 지출 목록
- `getExpensesByStore`: 거래처명 또는 별칭 기준 지출 목록
- `getExpensesByCard`: 카드명 기준 지출 목록
- `getDailyExpenseTotals`: 일별 지출 합계
- `getMonthlyExpenseTotals`: 월별 지출 합계
- `getUncategorizedExpenses`: 미분류 지출
- `searchExpenses`: 거래처/카테고리/카드/메모 검색
- `getDuplicateExpenses`: 중복 지출 후보
- `analyzeExpenses`: 필터/그룹/metric 기반 지출 분석

### 수입

- `getTotalIncome`: 기간 내 총 수입
- `getIncomes`: 기간별 수입 목록
- `getConfiguredMonthlyIncome`: 설정된 월 수입
- `getCategoryRatio`: 수입 대비 지출 비율

### 설정/메타데이터

- `getUsedCards`: 거래 내역에서 사용된 카드명 목록
- `getOwnedCards`: 카드 표시/숨김 설정 목록
- `getStoreRules`: 거래처 규칙 목록
- `getCustomCategories`: 커스텀 카테고리 목록
- `getSmsExclusionKeywords`: SMS 제외 키워드 목록
- `getBudgetStatus`: 예산 설정과 사용 현황

## 수정 함수

수정 함수는 기존 Repository/Service를 통해 DB를 변경하고 `DataRefreshEvent`를 발행한다. 화면 갱신이 필요하면 기존 앱 이벤트 흐름을 따른다.

### 지출/수입 수정

- `addExpense`: 수동 지출 추가
- `updateExpenseCategory`: 지출 ID 기준 카테고리 변경
- `updateExpenseCategoryByStore`: 거래처 별칭 기준 지출 카테고리 일괄 변경
- `updateExpenseCategoryByKeyword`: 키워드 기준 지출 카테고리 일괄 변경
- `updateExpenseMemo`: 지출 메모 변경
- `updateExpenseStoreName`: 지출 거래처명 변경
- `updateExpenseAmount`: 지출 금액 변경
- `updateExpenseFixed`: 지출 고정 여부 변경
- `updateExpenseStatsExcluded`: 지출 통계 제외 여부 변경
- `addIncome`: 수동 수입 추가
- `updateIncomeMemo`: 수입 메모 변경
- `updateIncomeCategoryByKeyword`: 수입 출처/설명 키워드 기준 카테고리 변경
- `updateIncomeRecurringByKeyword`: 수입 출처/설명 키워드 기준 고정 수입 여부 변경

### 설정 수정

- `setCardOwnership`: 카드 표시/숨김 상태 설정. 등록되지 않은 카드는 수동 카드로 추가
- `addManualCard`: 문자 설정에서 사용할 수동 카드 추가
- `addSmsExclusionKeyword`: SMS 제외 키워드 추가
- `removeSmsExclusionKeyword`: SMS 제외 키워드 삭제. 기본 키워드는 삭제되지 않음
- `setBudget`: 예산 설정
- `setMonthlyIncome`: 월 수입 설정
- `setMonthStartDay`: 월 시작일 설정
- `upsertStoreRule`: 거래처 규칙 추가/갱신. 기존 거래에 즉시 소급 적용
- `addCustomCategory`: 커스텀 카테고리 추가

## 기본 비활성 함수

아래 삭제성 함수는 등록은 되어 있으나 `isEnabled=false`로 기본 비활성화되어 있다.

- `deleteExpense`
- `deleteExpensesByKeyword`
- `deleteDuplicateExpenses`
- `deleteStoreRule`

삭제가 필요하면 먼저 사용자 승인을 받고, 가능하면 앱 UI의 안전한 액션이나 Repository 로직을 이용한다. App Functions 삭제 함수는 기본 호출 가능 상태로 두지 않는다.

## DB 확인 플레이북

### 중복 데이터 확인

1. `getDatabaseSnapshot`으로 `duplicateExpenseCount`, 수입/지출 총 건수 확인
2. 지출 중복은 `getDuplicateExpenses`로 후보 확인
3. 수입/환불 중복은 현재 App Function 조회 목록에 별도 함수가 없으므로 `getIncomes`로 기간을 좁히거나 debug 앱이면 DB 직접 조회
4. 정리는 앱 설정의 중복 삭제 액션 또는 Repository의 `deleteDuplicates()` 경로를 사용

### 카드 숨김/제외 확인

1. `getOwnedCards`로 `isOwned=false` 카드 확인
2. `getExpensesByCard`로 숨김 카드 거래가 실제 저장되어 있는지 확인
3. 화면 노출 문제는 History/Home 필터 로직과 `DataRefreshEvent.RefreshType.OWNED_CARD_UPDATED` 구독을 같이 확인

### 거래처 규칙 확인

1. `getStoreRules`로 규칙 목록 확인
2. `getExpensesByStore`로 대상 거래처 기존 거래 확인
3. `upsertStoreRule`은 기존 거래에 즉시 소급 적용한다
4. 복원 후 규칙 미적용 문제는 `StoreRuleSyncService.reapplyAllRules()`와 설정 복원 경로를 함께 확인

### SMS 제외 키워드 확인

1. `getSmsExclusionKeywords`로 전체 키워드 확인
2. `addSmsExclusionKeyword`, `removeSmsExclusionKeyword`로 사용자/채팅 키워드 변경
3. 기존 데이터는 삭제되지 않고 화면/집계 노출에서 필터링되는지 확인

## 주의사항

- App Function 본문은 `withContext(Dispatchers.IO)`로 DB 작업을 오프로드해야 한다.
- DB 스키마 변경 없이 App Function 응답 모델만 바꾸는 경우에도 KSP 생성 결과를 확인한다.
- 삭제성 기능은 기본 비활성 정책을 유지한다.
- 새 App Function을 추가하면 `docs/APP_FUNCTIONS.md`, `docs/AI_CONTEXT.md`, `docs/CHANGELOG.md`를 같이 갱신한다.
- 생성 함수 수 확인:

```bash
python3 - <<'PY'
import xml.etree.ElementTree as ET
root = ET.parse('app/build/generated/ksp/debug/resources/assets/app_functions.xml').getroot()
items = root.findall('appfunction')
print('count', len(items))
print('disabled', sum(1 for node in items if node.findtext('enabled_by_default') == 'false'))
PY
```
