---
type: feature-flow
title: Transaction Mutation 기능 흐름
description: 거래 추가/수정/삭제와 일괄 적용 후 refresh event 발행 흐름을 설명한다.
tags: [moneytalk, transaction, mutation, feature-flow]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditViewModel.kt
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# Transaction Mutation 기능 흐름

## UI 기반 변경

```text
TransactionEditScreen / AddExpenseDialog
-> ViewModel validation
-> ExpenseRepository or IncomeRepository
-> optional same-store/category/fixed/stats-excluded propagation
-> DataRefreshEvent emit
-> Home/History/CategoryDetail refresh
```

## 거래 롱클릭 작업 메뉴

```text
Home 오늘 내역 / History 목록 / CategoryDetail / TransactionDetailList
-> TransactionCardCompose.onLongClick(TransactionTarget.Expense/Income(id))
-> TransactionQuickActionViewModel.open() 최신 행 로드
-> TransactionQuickActionDialog 중앙 모달: 거래명 / 닫기 / 수정 / 삭제
-> 수정: 유형과 ID를 TransactionEditActivity에 전달
-> 삭제: 확인 dialog -> TransactionQuickActionService.delete()
-> Room transaction + Repository
-> DataRefreshEvent.TRANSACTION_ADDED
-> 각 화면 월 캐시/검색 결과 다시 읽기
```

| 경계 | 현재 구현 |
|---|---|
| 거래 식별 | `TransactionTarget`이 지출/수입 유형과 `Long` ID를 함께 보존한다. 0 이하/없는 ID는 작업 대상으로 열지 않는다. |
| 메뉴 내용 | 거래명과 닫기 X, `수정`·`삭제`만 표시한다. 금액·유형·안내 문구와 카테고리/고정/통계 제외 입력·저장 버튼은 메뉴에 두지 않는다. |
| 메뉴 표현 | 최대 너비 320dp·모서리 20dp의 테마 surface를 사용한다. 낮은 강조의 거래명/닫기 영역과 작업 영역을 얇은 구분선으로 나누고, 윤곽 아이콘을 곁들인 세로 작업 행 전체를 누를 수 있게 한다. 행은 최소 56dp이며 큰 글자에서는 높이가 늘어난다. 삭제만 error 색상, 아이콘은 장식이며 행에 버튼 의미를 부여한다. |
| 수정 | `수정`을 누르면 모달을 닫고 유형에 맞는 `expenseId` 또는 `incomeId`로 기존 `TransactionEditActivity`를 연다. 카테고리·고정·통계 제외·금액·메모·원문과 일괄 적용의 입력/저장 계약은 기존 상세 편집을 따른다. |
| 삭제 | 별도 확인 dialog에서 거래명·금액을 확인한 뒤 최신 행을 삭제한다. 삭제 성공 시 원본 SMS ID를 `DeletedSmsTracker`에 기록해 재수집 복원을 막는다. DB 실패 시 삭제 추적을 만들지 않는다. 시작된 삭제와 추적/refresh는 짧은 `NonCancellable` 구간에서 마친다. |
| 닫기와 경합 | 닫기 버튼·뒤로가기·바깥 영역 터치는 데이터를 바꾸지 않고 메뉴를 닫는다. 연속 다른 행 열기는 이전 로드를 취소하고 세대로 구분한다. 삭제 중 재진입/닫기/중복 동작을 막는다. |

검증 대상은 `TransactionQuickActionServiceTest`, `TransactionQuickActionViewModelTest`, `TransactionQuickActionUiTest`다. 실제 화면에서는 클릭/롱클릭 분리, 간단한 메뉴 내용, 중앙 모달 닫기·뒤로가기·바깥 터치 취소, 지출/수입 수정 대상 전달, 삭제 확인·취소·추적, 삭제 후 필터·합계 갱신을 확인한다. 실행 결과는 해당 변경의 검증 로그를 기준으로 기록한다.

## Chat action 기반 변경

```text
ChatViewModel
-> Gemini/DataQueryParser ActionType
-> ChatActionExecutor
-> Repository update/delete/add
-> ActionResult
-> DataRefreshEvent
```

## 검증 질문

1. 단일 거래 변경과 일괄 적용 범위가 구분되는가?
2. 수입/지출 저장 경로가 각각 올바른 entity와 DAO를 쓰는가?
3. 카테고리 일괄 변경이 사용자 학습/embedding/source 정책과 충돌하지 않는가?
4. 변경 후 관련 화면 cache가 stale하지 않은가?
5. 채팅 action은 필요한 필드를 검증한 뒤 실행하는가?
6. 롱클릭의 수정은 정확한 유형과 ID로 기존 편집 화면을 열고, 삭제는 확인한 한 건만 처리하는가?
7. 삭제 후 다시 동기화해도 원본 거래가 재적재되지 않고 지출/수입의 같은 ID가 섞이지 않는가?

삭제 추적은 이후 수집 시 원본을 제외하지만, 이미 삭제 필터를 통과한 수집 배치와의 동시 저장은 기존 수집 경로의 별도 검증 대상이다. 삭제·수집의 모든 동시 실행이 원자적으로 차단된다고 단정하지 않는다.
