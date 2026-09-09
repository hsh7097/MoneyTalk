---
type: package-reference
title: Transaction Edit data/ViewModel
description: TransactionEditViewModel의 state, 저장/삭제, category, 고정지출/통계 제외 일괄 적용 흐름을 설명한다.
tags: [moneytalk, transaction-edit, viewmodel, data]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditViewModel.kt
timestamp: 2026-09-09T00:00:00+09:00
status: draft
---

# Transaction Edit data/ViewModel

## 상태

`TransactionEditUiState`는 거래 type, amount, store/source, category, date/time, memo, 고정 여부, 통계 제외, 일괄 적용 옵션, picker/dialog 상태를 가진다.

## 주요 action

| 메서드 | 역할 | 함께 볼 파일 |
|---|---|---|
| `loadExpense()` / `loadIncome()` | 기존 거래 로딩 | `ExpenseRepository`, `IncomeRepository` |
| `initNewExpense()` | 신규 지출 기본 state | `TransactionType` |
| `selectCategory()` / `addCategoryFromPicker()` | category 선택/추가 | `CustomCategoryRepository`, `CategoryProvider` |
| `save()` | state validation 후 지출/수입 저장 분기 | `saveAsExpense()`, `saveAsIncome()` |
| `saveAsExpense()` | 지출 저장, 카테고리/고정/통계 제외 일괄 적용 | `transaction-mutation/README.md` |
| `saveAsIncome()` | 수입 저장 | `IncomeRepository` |
| `delete()` | 거래 삭제 | `DataRefreshEvent` |

## 주의 경계

- category displayName과 custom category name을 혼동하지 않는다.
- 고정지출/통계 제외 일괄 적용은 현재 거래뿐 아니라 같은 store/rule 대상에 영향을 줄 수 있다.
- 저장 후 Home/History/CategoryDetail cache refresh를 고려한다.

## 책임 분리와 편집 변경 감지 (2026-09-08)

- `TransactionEditUiState.kt`: 로딩/저장 결과와 편집 입력 계약.
- `TransactionEditArgs.kt`: Activity와 ViewModel이 공유하는 진입 인자.
- `TransactionEditSnapshot.kt`: 저장에 영향을 주는 입력 비교. 규칙 키워드와 카테고리/고정 일괄 적용도 포함한다. picker 열기 같은 표시 상태는 포함하지 않는다.
- `TransactionEditViewModel`: 최초 로딩 시 snapshot을 보관하고 UI에 변경 여부를 제공한다. 화면 재생성 후에도 기존 ViewModel 기준을 유지하여 미저장 변경 확인을 건너뛰지 않는다.
- 없는 거래는 `loadErrorResId` 상태로 표시하고 `save()`를 차단한다. 저장/삭제/규칙 적용의 기존 repository 호출과 새로고침 이벤트는 유지한다.

회귀 검증: `TransactionEditSnapshotTest`, `TransactionNotificationInstrumentedTest`의 화면 재생성/기존 진입/없는 ID/목표 거래 저장.

## 편집 중 알림 액션과 저장 충돌 (2026-09-09)

- 저장 시 `AppDatabase.withTransaction` 안에서 최초 타입/ID의 최신 행을 다시 읽는다. 최초 입력 snapshot과 비교해 사용자가 바꾼 필드만 최신 행에 덮어쓴다. 알림의 단건 통계 제외나 정본 교체의 SMS ID/본문/발신번호, 다른 화면의 미편집 필드 변경을 보존한다.
- 날짜/시간을 편집하지 않은 경우 최신 `dateTime`의 초·밀리초도 유지한다. 날짜는 독립적으로 병합하며, 시 또는 분을 바꿨다면 사용자가 고른 시·분 쌍 전체를 유지한다. 시간 둘 다 미편집이면 최신 시·분 쌍을 사용한다.
- 원본 행이 이미 삭제됐으면 `transaction_edit_not_found` 상태로 저장을 차단한다. 성공 상태/일괄 규칙 적용을 발생시키지 않으며 수입↔지출/이체 전환으로 삭제된 거래를 재생성하지 않는다. 정상 유형 전환의 원본 확인, 새 행 삽입, 원본 삭제도 같은 DB transaction에 속한다.
- 최초 입력과 비교해 규칙 입력의 변경이 없으면 다시 소급 적용하지 않는다. 메모만 저장해도 기존 `isExcludedFromStats=false` 규칙이 알림의 단건 제외를 되돌리던 경로를 막는다. 사용자가 일괄 적용을 명시적으로 변경한 경우 규칙의 이전/새 값이 같아도 단건 예외까지 소급 적용한다. 외부에서 바뀐 단건 값을 자동으로 전체 거래처 규칙으로 확대하지 않는다.
- 편집 화면 삭제도 `TransactionQuickActionService.delete`를 호출한다. 최신 원본을 삭제한 뒤에만 재수집 방지 기록과 새로고침 이벤트를 남기고, 없는 행이면 저장과 같은 오류 상태를 표시한다. 아직 저장하지 않은 유형 전환은 삭제할 원본 테이블을 바꾸지 않는다.

회귀 검증: `TransactionEditNotificationRaceTest`는 실제 Activity/Hilt ViewModel과 알림 Broadcast를 연결해 단건 제외 후 메모 저장, 변경 없는 거래처 규칙, 삭제 후 저장/양방향 전환 차단, 최신 정본을 보존한 정상 전환, 수입 부분 수정, 명시적인 통계 포함 변경, 편집 삭제의 최신 SMS 및 Missing 처리를 검사한다. 시·분 선택의 원자적 병합과 실제 자동 정리 스위치 OFF/같은 거래처 체크 해제도 검사한다. DB 삭제 실패/취소 시 행과 삭제 기록의 보존은 공용 서비스의 `TransactionQuickActionServiceTest`에서 별도로 검사한다. 실행 결과는 통합 빌드/기기 검증 기록에서 확인한다.
