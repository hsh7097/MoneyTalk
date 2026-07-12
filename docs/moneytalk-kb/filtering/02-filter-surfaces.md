---
type: feature-detail
title: Filtering surfaces
description: MoneyTalk의 필터 계층을 화면 필터, 노출 필터, 통계 제외, SMS 입력 제외, 백업 필터로 나눠 정리한다.
tags: [moneytalk, filtering, surface, history, sms]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-11T00:00:00+09:00
status: draft
---

# Filtering surfaces

Filtering은 하나의 공통 필터가 아니라 데이터 생명주기 단계별 필터 묶음이다. 변경할 때는 "거래를 삭제하는가", "화면에서만 숨기는가", "집계에서만 제외하는가", "신규 파싱 입력을 막는가"를 먼저 구분한다.

## 필터 계층

| 계층 | 기준 파일 | 저장 위치 | 적용 시점 | 영향 범위 |
|---|---|---|---|---|
| 내역 화면 임시 필터 | `HistoryFilter.kt`, `HistoryViewModel.kt` | `HistoryUiState` | History 화면 렌더링/월별 cache reload | 내역 목록/월 합계/검색 결과 |
| 카드 노출 필터 | `CardVisibilityFilter.kt`, `OwnedCardRepository` | `OwnedCardEntity.isOwned=false` 계열 | 화면/집계 데이터 로드 후 | Home, History, CategoryDetail, TransactionList, Chat/App Functions, 노티 표시 |
| 통계 제외 필터 | `ExpenseEntity.isExcludedFromStats`, `StatsExclusionClassifier` | `expenses.isExcludedFromStats` | 저장/수정 후 집계 계산 시 | 월 지출, 오늘 지출, 차트, 카테고리 합계, 채팅 분석 |
| SMS 제외 키워드 | `SmsExclusionRepository`, `SmsPreFilter`, `SmsInstantProcessor` | `SmsExclusionKeywordEntity` | 신규 SMS/앱 알림 처리 전, 일부 화면 로드 후 | 신규 파싱 스킵, 기존 거래 화면 노출 제외 |
| SMS 차단 발신자 | `SmsBlockedSenderRepository`, `SmsFilter` | `SmsBlockedSenderEntity` | SMS/MMS/RCS 읽기 또는 즉시 처리 초반 | 해당 발신자 신규 파싱 스킵 |
| 백업 export 필터 | `ExportFilter`, `DataBackupManager` | Dialog local state | 내보내기 파일 생성 시 | export 결과만 제한 |

## History 필터

| 옵션 | 값 | 구현 기준 |
|---|---|---|
| 정렬 | `DATE_DESC`, `AMOUNT_DESC`, `STORE_FREQ` | `SortOrder` |
| 거래 유형 | 지출, 수입, 이체 | `showExpenses`, `showIncomes`, `showTransfers` |
| 카테고리 | 지출/수입/이체 카테고리별 multi select | `selectedExpenseCategories`, `selectedIncomeCategories`, `selectedTransferCategories` |
| 카드 | 카드명 multi select | `selectedCardNames`, `CardFilterListBottomSheet` |
| 고정 거래 | 전체, 고정 거래만, 고정 거래 제외 | `FixedExpenseFilter` |
| 검색 | store/category/memo 등 text search | `searchQuery`, `isSearchMode` |

History 필터는 사용자 화면 상태다. 원본 거래를 삭제하거나 SMS 파싱 규칙을 바꾸지 않는다.

## 카드 노출 필터

`CardVisibilityFilter`는 카드명 원문과 `CardNameNormalizer.normalize()` 결과를 모두 비교한다.

```text
OwnedCardRepository.getExcludedCardNames()
-> CardVisibilityFilter.filterVisibleExpenses()
-> 화면 목록/집계에서 제외
```

노티 표시도 같은 정책을 쓴다.

```text
SmsInstantProcessor.shouldShowExpenseNotification()
-> settings notification enabled 확인
-> OwnedCardRepository.getExcludedCardNames()
-> CardVisibilityFilter.shouldShowExpenseNotification()
```

따라서 제외 카드 정책을 바꾸면 화면뿐 아니라 신규 거래 알림 표시 여부도 함께 검증한다.

## 통계 제외

통계 제외는 거래를 숨기지 않는다. `ExpenseEntity.isExcludedFromStats`가 true인 거래는 목록에는 남고, 합계/차트/카테고리 집계에서 빠진다.

| 생성 경로 | 기준 |
|---|---|
| 신규 SMS 저장 | `StatsExclusionClassifier.shouldExcludeExpense()` 또는 `StoreRule.isExcludedFromStats` |
| 거래 편집 | `TransactionEditViewModel`의 통계 제외 토글 |
| 거래처 규칙 | store rule이 이후 저장/수정 거래에 적용 |
| 채팅/App Functions | `updateExpenseStatsExcluded`, `updateStoreRule` 계열 action |

카드대금 납부/결제대금/이용대금/이용금액/자동이체성 문구는 기본적으로 통계 제외 후보로 본다. 카드 승인으로 이미 소비가 잡힌 뒤 상환 거래가 다시 소비로 중복 집계되는 것을 막기 위한 정책이다.

카드대금 문구를 입력 단계에서 모두 버리지는 않는다. 실제 출금/인출 완료와 원화 금액이 확인되면 거래를 저장하고 통계에서만 제외한다. 반대로 예정/명세서/청구서/결제일 안내는 입력 단계에서 저장하지 않는다.

| SMS 유형 | 입력 처리 | 저장 후 처리 |
|---|---|---|
| 완료된 카드대금 출금 | PAYMENT로 보존 | `isExcludedFromStats=true` |
| 카드대금 예정/명세서 | SKIP | 저장 없음 |
| 교통/하이패스 N건 요약 | SKIP | 저장 없음 |
| KSNET/매출접수 N건 집계 | SKIP | 저장 없음 |
| 승인/결제/사용 금액 0원 | SKIP | 저장 없음 |
| 쇼핑 금액과 입금계좌만 있는 미완료 안내 | SKIP | 저장 없음 |
| 취소/환불 | INCOME | 수입 파서로 저장 |

## SMS 입력 제외

SMS 제외 키워드와 발신자 차단은 신규 입력 단계에서 먼저 작동한다.

```text
SmsSettingsScreen
-> SmsExclusionRepository / SmsBlockedSenderRepository
-> SmsReceiver / MmsContentObserver / RcsContentObserver / SmsReaderV2
-> SmsInstantProcessor / SmsPreFilter
```

주의할 점은 기존 거래 처리다. 신규 파싱은 스킵하지만, Home/History/CategoryDetail 일부 로더는 `originalSms`에 제외 키워드가 포함된 기존 거래도 화면/집계에서 한 번 더 제외한다. 이 정책을 바꾸면 "과거 데이터도 다시 보이게 할지"를 명확히 결정해야 한다.

## Backup export 필터

`ExportFilter`는 backup 파일 생성에만 쓰인다.

| 필드 | 적용 대상 |
|---|---|
| `startDate`, `endDate` | 지출/수입 모두 |
| `cardNames` | 지출만 |
| `categories` | 지출만 |
| `includeExpenses`, `includeIncomes` | export 포함 여부 |

복원은 export 필터와 독립이다. 필터된 파일을 복원하면 파일 안에 들어 있는 데이터만 복원된다.

## 변경 시 체크

1. 필터 변경이 신규 파싱, 기존 화면 노출, 통계 집계 중 어디에 영향을 주는지 먼저 적었는가?
2. 카드 제외 변경 후 Home, History, CategoryDetail, TransactionList, Chat/App Functions, 노티 표시를 같이 확인했는가?
3. 통계 제외 거래가 목록에는 남고 합계/차트에서만 빠지는지 확인했는가?
4. SMS 제외 키워드가 신규 입력과 기존 데이터 노출에 각각 어떻게 적용되는지 문서화했는가?
5. 카드대금 완료 출금과 예정 안내를 구분하고 저장/통계 제외를 각각 검증했는가?
6. 집계형 SMS와 취소 SMS가 지출 Fast Path에 들어가지 않는지 확인했는가?
