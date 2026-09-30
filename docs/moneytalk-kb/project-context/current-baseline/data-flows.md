---
title: MoneyTalk 수집·분류·거래 변경 흐름
status: verified
last_checked: 2026-09-30
source_ref: 95b419e8b98e374a991f3210a194a88959937ee8
---

# 수집·분류·거래 변경 흐름

## 범위

SMS 배치/실시간 입력이 Room 거래와 화면 갱신으로 이어지는 경계를 설명한다. 기준은 위 commit이며 애플리케이션 작업트리 변경은 포함하지 않았다.
**사실**은 확인한 구현, **추론**은 그 구현에서 예상하는 영향, **미검증**은 실행으로 확인하지 않은 범위다. 광고·크레딧·과금의 미래 정책은 여기서 현재 수집 동작으로 확정하지 않는다.
기존 상세 KB는 유지한다. 아래 기존 문서 링크는 후속 탐색 안내이며, 기존 문서 전체를 이번 기준으로 재검증했다는 뜻은 아니다.

## 탐색

| 개발 질문 | 먼저 볼 실제 코드 | 기존 상세 KB |
|---|---|---|
| 월별/증분 동기화에서 문자가 빠지는가? | [MainViewModel](../../../../app/src/main/java/com/sanha/moneytalk/MainViewModel.kt)의 `syncSmsV2Internal` | [수집 계약](../../sms-parsing/06-ingestion-contract.md) |
| 실시간 문자가 즉시 저장되지 않는가? | [SmsReceiver](../../../../app/src/main/java/com/sanha/moneytalk/receiver/SmsReceiver.kt)의 `onReceive` | [SMS Pipeline](../../sms-pipeline/README.md) |
| 카테고리 선택이 다르게 나오는가? | [CategoryClassifierServiceImpl](../../../../app/src/main/java/com/sanha/moneytalk/feature/home/data/CategoryClassifierServiceImpl.kt)의 `getCategory`, `classifyStoreNamesInMemory` | [분류 티어](../../category-classification/06-classification-tiers.md) |
| 편집/삭제 결과가 다른 화면에 반영되지 않는가? | [TransactionEditViewModel](../../../../app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditViewModel.kt)의 `save`, `delete` | [거래 변경 흐름](../../transaction-mutation/01-feature-flow.md) |

## 배치·증분 수집

**사실:** `MainViewModel.launchSync`와 `syncSmsV2`는 같은 `syncSmsV2Internal`을 호출한다. 두 진입 경로는 중복 실행을 막고 `ClassificationState` 소유권을 확보하며, 성공 처리와 카드 등록 뒤 잔여 분류를 재개한다.

1. `prepareSyncRequest`가 읽기 계획을 정한다. [SmsSyncMessageReader](../../../../app/src/main/java/com/sanha/moneytalk/core/sms/SmsSyncMessageReader.kt)의 `read`는 [SmsReaderV2](../../../../app/src/main/java/com/sanha/moneytalk/core/sms/SmsReaderV2.kt)의 SMS/MMS/RCS 읽기를 호출한다.
2. [SmsIngestionWriter](../../../../app/src/main/java/com/sanha/moneytalk/core/sms/SmsIngestionWriter.kt)의 `buildExistingSmsSnapshot`과 `readAndFilterSms`가 기존 거래·삭제 기록·즉시 저장 후 보정 대상을 확인한다.
3. [SmsSyncCoordinator](../../../../app/src/main/java/com/sanha/moneytalk/core/sms/SmsSyncCoordinator.kt)의 `process`: 룰 동기화 실패는 폴백 → 사전 필터 → 수입/결제/스킵 분리 → sender regex Fast Path → 미매칭 결제만 파이프라인.
4. [SmsPipeline](../../../../app/src/main/java/com/sanha/moneytalk/core/sms/SmsPipeline.kt)의 `process`: 템플릿 임베딩 → 기존 패턴/regex 매칭 → regex 실패 배치 추출 → 미매칭 그룹 분류와 regex 실패 폴백. 결과를 합쳐 반환한다.
5. [SmsSyncResultFilter](../../../../app/src/main/java/com/sanha/moneytalk/core/sms/SmsSyncResultFilter.kt)의 `filterByTransactionRange`가 수신 범위와 별개로 거래 시각 기준 결과를 제한한다.
6. `SmsIngestionWriter.write`는 저장 직전 snapshot을 다시 읽고 지출·수입을 각각 저장한다. 파싱 중 실시간 경로가 먼저 저장한 거래도 이 snapshot과 DAO 재확인 대상이다.
7. `postSyncCleanup`이 분류 매핑 캐시를 flush/해제하고 watermark를 저장한다. 성공 coverage·카드 등록 뒤 `launchSync`의 최초 동기화(`isInitialSync`)는 신규 거래가 없어도 `notifyDataChanged`를 호출한다. 그 밖의 `handleSyncResult` 경로는 신규/교체/수입 출처 보정 등 실제 데이터 변경이 있을 때만 `TRANSACTION_ADDED`를 발행한다.

SMS provider 읽기 실패는 `SmsSyncMessageReader.read`에서 예외로 종료하여 후속 watermark 저장에 도달하지 않는다. RCS 읽기 성공 여부는 별도 RCS scan watermark 갱신 조건이다.
`SmsSyncCoordinator`는 저장·watermark·카드 등록을 담당하지 않는다. 일부 주석의 “실시간도 유일한 진입점” 표현보다 아래 즉시 저장 구현을 기준으로 탐색한다.

## 실시간 수집과 보완

**사실:** `SmsReceiver.onReceive`는 multipart 본문을 합치고 `goAsync` 범위에서 [SmsInstantProcessor](../../../../app/src/main/java/com/sanha/moneytalk/core/sms/SmsInstantProcessor.kt)의 `processAndSave`를 호출한다.

- 발신자·비거래 구조·사용자 제외 키워드·삭제 기록·in-flight 중복을 확인한 뒤 수입/지출을 나눈다.
- 지출은 sender regex가 맞으면 로컬 카테고리와 거래처 규칙을 적용하고 `ExpenseRepository.insertIngested`로 저장한다. regex 미매칭은 `Deferred`다.
- 수입은 `SmsIncomeParser`로 추출하고 [IncomeRepository](../../../../app/src/main/java/com/sanha/moneytalk/feature/home/data/IncomeRepository.kt)의 `insertIngested` → [IncomeDao](../../../../app/src/main/java/com/sanha/moneytalk/core/database/dao/IncomeDao.kt)의 동명 transaction으로 저장한다. 환불 정본 보정은 기존 행 ID와 사용자 메타데이터 보존 분기를 갖는다.
- 저장 성공은 `TRANSACTION_ADDED`만 발행한다. 다음 앱 resume의 증분 동기화가 즉시 저장 결과를 보정한다. 성공하지 않은 결과는 `SMS_RECEIVED`로 활성 화면의 증분 동기화를 유도한다.
- `Deferred`만 [SmsFallbackScheduler](../../../../app/src/main/java/com/sanha/moneytalk/core/sms/SmsFallbackScheduler.kt)의 `enqueue`로 보존한다. `Skipped`는 재처리 후보가 아니다.

[SmsFallbackQueue](../../../../app/src/main/java/com/sanha/moneytalk/core/sms/SmsFallbackQueue.kt)는 no-backup 파일에 후보를 보존하고 `MAX_ATTEMPTS`로 시도를 제한한다. 반복 이벤트는 횟수를 초기화하지 않는다.
[SmsFallbackJobService](../../../../app/src/main/java/com/sanha/moneytalk/receiver/SmsFallbackJobService.kt) → [SmsFallbackProcessor](../../../../app/src/main/java/com/sanha/moneytalk/core/sms/SmsFallbackProcessor.kt)의 `processPending` → coordinator → writer가 화면 밖에서 보완한다. JobScheduler는 네트워크 조건을 요구한다.
**미검증:** 기기별 provider 전달, OS 작업 실행 지연, 앱 종료 상태 수신과 실제 알림은 이번 정적 조사로 보장하지 않는다.

## 카테고리 분류의 서로 다른 모드

**사실:** 아래 모드는 [CategoryClassifierServiceImpl](../../../../app/src/main/java/com/sanha/moneytalk/feature/home/data/CategoryClassifierServiceImpl.kt)에 함께 있으며, 모든 호출이 같은 티어를 거치는 것은 아니다.

| 모드/심볼 | 실제 순서와 결과 |
|---|---|
| 일반 `getCategory` | 사용자 `StoreRule` → Room 정확 매핑 → 벡터 최고/그룹 매칭 → `SmsParser.inferCategory` → 미분류 반환. 벡터 성공은 Room 매핑으로 promotion한다. |
| 캐시 활성 `getCategory` | 인메모리 정확/정규화 부분 매핑 → 로컬 키워드. 벡터/원격 AI를 호출하지 않으며 로컬 매핑은 flush를 기다린다. |
| writer 저장 전 | 유효한 추출 카테고리 또는 `getCategory` → 사용자 거래처 규칙으로 category/fixed/stats 재적용 → 남은 미분류 상호만 `classifyStoreNamesInMemory`. |
| `classifyStoreNamesInMemory` | 이체 패턴 → 추가 로컬 규칙 → AI 가능 여부 확인 → 상호 그룹 대표의 Gemini 분류 → 멤버 전파 → 로컬/Gemini 출처별 Room 매핑 저장. 대량 그룹에서 생성한 벡터만 추가 저장한다. |
| `classifyAllUntilComplete` | 잔여 미분류를 라운드별 처리한다. 진행 없음/남은 수 동일/최대 라운드에서 종료한다. |

[SmsEmbeddingService](../../../../app/src/main/java/com/sanha/moneytalk/core/sms/SmsEmbeddingService.kt)의 `createEmbedding`은 문자 n-gram으로 결정적 로컬 벡터를 만든다. 현재 벡터 생성을 “embedding API 호출 비용”으로 설명하지 않는다.
벡터/전파 임계값은 [기존 레지스트리](../02-threshold-registry.md)와 [StoreNameSimilarityPolicy](../../../../app/src/main/java/com/sanha/moneytalk/core/similarity/StoreNameSimilarityPolicy.kt), [CategoryPropagationPolicy](../../../../app/src/main/java/com/sanha/moneytalk/core/similarity/CategoryPropagationPolicy.kt)를 함께 확인한다.
`updateExpenseCategory`/`updateCategoryForAllSameStore` 서비스 API는 `source=user` Room 매핑과 거래를 수정하고 프롬프트 참조 캐시를 무효화한다. [StoreEmbeddingRepositoryImpl](../../../../app/src/main/java/com/sanha/moneytalk/feature/home/data/StoreEmbeddingRepositoryImpl.kt)의 전파는 사용자 확정 항목을 제외한 유사 **벡터 행**을 갱신한다.
**미검증:** 실제 Gemini 응답, 로컬 벡터의 분류 정확도와 학습 전파 품질. AI 사용 가능 여부가 false여도 위 저장 전 로컬 규칙 결과는 유지되는 구현을 확인했다.

## 거래 편집·삭제와 화면 갱신

**사실:** `TransactionEditViewModel.save`는 수입 또는 지출/이체 저장으로 분기한다.

1. 입력을 확인하고 Room transaction 안의 `resolveSaveState`에서 최신 행을 다시 읽는다. 사용자가 바꾼 필드만 덮어써 다른 화면/알림 변경을 보존하며, 이미 삭제한 행은 되살리지 않는다.
2. 유형 전환은 새 테이블에 insert한 뒤 기존 테이블에서 delete하여 한 transaction으로 처리한다. 날짜/시간을 편집하지 않았으면 `resolveSaveDateTime`이 최신 정본의 초·밀리초를 유지한다.
3. 지출 편집의 동일 거래처 옵션은 [StoreRuleSyncService](../../../../app/src/main/java/com/sanha/moneytalk/feature/home/data/StoreRuleSyncService.kt)의 `applyRuleChange`로 규칙과 기존 거래를 갱신한다. 이 후처리는 단건 저장 transaction 뒤이며 실패 시 저장 성공/규칙 실패 상태를 알린다.
4. 수입의 일괄 옵션은 keyword별 카테고리/반복 값을 갱신한다. 저장과 수정 모두 `TRANSACTION_ADDED`를 발행한다. 이 이벤트 이름을 신규 insert 전용으로 해석하지 않는다.
5. `delete`는 유형+ID의 `TransactionTarget`을 [TransactionQuickActionService](../../../../app/src/main/java/com/sanha/moneytalk/feature/transactionactions/data/TransactionQuickActionService.kt)의 `delete`에 전달한다. 최신 행 삭제 성공 뒤 `DeletedSmsTracker.markDeleted`와 refresh를 실행한다.

[DataRefreshEvent](../../../../app/src/main/java/com/sanha/moneytalk/core/util/DataRefreshEvent.kt)는 `MutableSharedFlow` 기반 통지다. `emit`은 `tryEmit`, `emitSuspend`는 suspend 발행을 사용한다.
소비자는 [HomeViewModel](../../../../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeViewModel.kt), [HistoryViewModel](../../../../app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryViewModel.kt), [CategoryDetailViewModel](../../../../app/src/main/java/com/sanha/moneytalk/feature/categorydetail/ui/CategoryDetailViewModel.kt)의 `observeDataRefreshEvents`다. 각 화면은 현재/인접 페이지 또는 전체 캐시를 자신의 정책으로 다시 읽는다.
`SMS_RECEIVED`의 증분 동기화는 `MainViewModel.observeDataRefreshEvents`가 담당한다. refresh는 저장소 자체가 아니며 비활성 소비자에 영속 작업을 예약하지 않는다.

## 변경 영향과 위험

| 변경 지점 | 사실로 확인한 경계 / 추론한 위험 |
|---|---|
| regex·필터·날짜 해석 | **추론:** 배치와 즉시 경로의 제외/추출 결과가 어긋날 수 있다. 완료 카드대금 출금의 통계 제외와 취소/환불의 수입 분기도 함께 확인한다. |
| 자동 저장/중복 기준 | [ExpenseDao](../../../../app/src/main/java/com/sanha/moneytalk/core/database/dao/ExpenseDao.kt)의 `insertIngested`가 저장 transaction 안에서 ID/재전달/교차 출처를 재확인한다. 수동 입력/복원용 `insert`와 구분한다. |
| 비슷한 SMS를 합치는 조건 | [TransactionSemanticDedupe](../../../../app/src/main/java/com/sanha/moneytalk/core/sms/TransactionSemanticDedupe.kt)의 `isSameSmsRedelivery`는 동일 발신자·원문·숫자 잔액 근거·거래 필드·수신시각 창을 요구한다. **추론:** 상호/금액/분 단위 시각만으로 합치면 정상 반복 결제를 잃는다. |
| 정본 교체·삭제 | [DeletedSmsTracker](../../../../app/src/main/java/com/sanha/moneytalk/core/sms/DeletedSmsTracker.kt)의 `linkSameTransaction`은 확인한 출처 연결을 보존한다. Room commit과 SharedPreferences 기록은 단일 원자 transaction이 아니므로 프로세스 중단 경계를 별도로 검증한다. |
| 전체 삭제/분류 동시성 | [ClassificationState](../../../../app/src/main/java/com/sanha/moneytalk/core/ui/ClassificationState.kt)의 소유권/registration epoch와 writer의 `ensureWritable`을 유지한다. **추론:** gate를 우회하면 삭제 전 요청이 데이터를 다시 저장할 수 있다. |
| 일괄 규칙/refresh | 단건 저장과 규칙 후처리의 실패 범위, 사용자 확정 벡터 보호, 페이지 캐시 무효화를 함께 확인한다. **추론:** 일부만 바꾸면 다른 화면과 다음 자동 분류가 다른 값을 사용할 수 있다. |

## 근거

MoneyTalk `git rev-parse HEAD`로 `95b419e8b98e374a991f3210a194a88959937ee8`을 확인했다. 작성 계약은 ClaudeGuide `2d2aa90c82a141e7440d3ee60b692911eb2ee5f1` 기준이다.
위 링크의 파일과 표기한 심볼을 실제 읽고 대조했다. 특히 `syncSmsV2Internal`, `processAndSave`, `write`, `getCategory`, `classifyStoreNamesInMemory`, `resolveSaveState`, `insertIngested`, `observeDataRefreshEvents`의 호출 순서/책임을 확인했다.
원문 문자·거래·발신자·설정 값은 문서에 전사하지 않았다. 현재 구현 설명에 기존 KB의 운영 표본/실기기 검증 주장을 인용하지 않았다.

## 검증

| 구분 | 수행 또는 다음 확인 | 결과 |
|---|---|---|
| 정적 대조 | 위 실제 코드 경로와 함수 정의·호출 순서를 읽었다. | 수행; 실행 성능/정확성 판정은 제외 |
| 탐색 | “regex 미매칭 문자는 화면 밖에서 어디로 가는가?”, “편집 중 알림 변경을 누가 보존하는가?”, “배치 분류가 벡터를 항상 호출하는가?”를 위 진입점으로 추적했다. | 해당 파일/심볼 확인; 독립 평가 결과는 baseline 검증 문서에서 기록 |
| 자동 문서 검사 | 공용 검사기의 링크·메타데이터·placeholder 확인 | 5 Markdown 오류 0; 전체 결과는 검증 기록 참조 |
| 빌드/자동 테스트/런타임 | 애플리케이션 실행 검증 | 컴파일 전 wrapper 실패로 관련 앱 테스트·런타임 미실행; 실행 성공 주장 없음 |

코드 변경 후 선택할 실제 테스트 위치는 아래와 같다. 존재와 대표 assertion을 읽은 결과이며 실행 결과가 아니다.

| 회귀 범위 | 실제 테스트 파일 |
|---|---|
| asset regex 실제 matcher 연결 | [SmsRegexRuleMatcherAssetIntegrationTest](../../../../app/src/test/java/com/sanha/moneytalk/core/sms/SmsRegexRuleMatcherAssetIntegrationTest.kt) |
| 큐 복원·횟수 제한·삭제 세대 | [SmsFallbackQueueTest](../../../../app/src/test/java/com/sanha/moneytalk/core/sms/SmsFallbackQueueTest.kt) |
| 월별 순서·수신월/거래월 차이 | [MonthlySmsSyncOrderRegressionTest](../../../../app/src/test/java/com/sanha/moneytalk/core/sync/MonthlySmsSyncOrderRegressionTest.kt) — 합성 동기화 시뮬레이션 |
| 동시 수집·정본/메모 보존·환불 | [ExpenseIngestionInstrumentedTest](../../../../app/src/androidTest/java/com/sanha/moneytalk/core/database/ExpenseIngestionInstrumentedTest.kt) |
| 연결된 출처 삭제 후 재수집 | [TransactionSourceDeletionInstrumentedTest](../../../../app/src/androidTest/java/com/sanha/moneytalk/core/sms/TransactionSourceDeletionInstrumentedTest.kt) |
| 편집과 알림의 경합 | [TransactionEditNotificationRaceTest](../../../../app/src/androidTest/java/com/sanha/moneytalk/feature/transactionedit/TransactionEditNotificationRaceTest.kt) — disposable emulator만 허용하는 setup |
| 최신 행 삭제·타입/ID·실패 rollback | [TransactionQuickActionServiceTest](../../../../app/src/androidTest/java/com/sanha/moneytalk/feature/transactionactions/TransactionQuickActionServiceTest.kt) |
