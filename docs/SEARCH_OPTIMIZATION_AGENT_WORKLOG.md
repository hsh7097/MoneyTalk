# Search Optimization Agent Worklog

> MoneyTalk 검색/분류 최적화 작업을 단계별로 관리하는 문서.
> 한 번에 전체 변경을 적용하지 않고, 각 단계마다 구현 -> 검증 -> 셀프 리뷰를 통과한 뒤 다음 단계로 이동한다.

## 작업 기준 재검토

### 적절한 방향

- 기존 앱은 이미 `Room 정확 매핑 -> 벡터 유사도 -> 로컬 키워드 -> Gemini Batch` 구조를 갖고 있다.
- HNSW, Redis, Python 기반 인덱스는 현재 Android 앱 구조와 맞지 않으며, 수치상 병목이 확인되기 전에는 도입하지 않는다.
- 우선순위는 새 인프라가 아니라 기존 분류 파이프라인의 비용, 지연, 오분류 리스크를 낮추는 것이다.
- DB 스키마, Gradle, ProGuard, signing config는 필요성이 검증되기 전까지 변경하지 않는다.

### 단계별 우선순위

1. API 키가 없어도 로컬 사전 분류 규칙이 실행되게 한다.
2. 로컬 고확도 규칙과 벡터 호출 순서를 검토해 불필요한 임베딩 API 호출을 줄인다.
3. tier별 소요시간/호출 여부/결과를 계측할 수 있는 최소 로그를 추가한다.
4. 벡터/그룹핑/캐시 우선순위 회귀 테스트를 추가한다.
5. 수치상 병목이 확인된 뒤에만 벡터 표현 최적화나 ANN 후보 생성을 검토한다.

## 진행 방식

- 각 단계는 작은 변경으로 제한한다.
- 단계 완료 조건:
  - 관련 단위 테스트 또는 최소 빌드 성공
  - 변경 diff 셀프 리뷰 통과
  - 이 문서의 상태 갱신
- 실패하면 다음 단계로 넘어가지 않고 원인 수정 후 재검증한다.

## 작업 현황

| 단계 | 상태 | 내용 | 검증 |
|---|---|---|---|
| 0 | 완료 | 기준 재검토 및 작업관리 문서 생성 | diff 리뷰 |
| 1 | 완료 | API 키가 없어도 로컬 규칙 기반 분류 수행 | `testDebugUnitTest`, `assembleDebug` |
| 2 | 보류 | 단건 분류의 로컬 고확도 규칙 우선 적용 검토 | 넓은 키워드 오분류 리스크로 보류 |
| 3 | 완료 | 검색/분류 계측 로그 최소 추가 | `testDebugUnitTest`, `assembleDebug`, diff 리뷰 |
| 4 | 대기 | 벡터/그룹핑 회귀 테스트 보강 | `testDebugUnitTest` |

## 단계별 메모

### 2026-05-15

- 기존 계획서의 HNSW/Redis/Python 중심 설계는 현재 Android 앱에 바로 적용하기 부적절하다고 판단했다.
- 첫 구현 단계는 DB/Gradle 변경 없이 가능한 `classifyStoreNamesInMemory` 호출 조건 개선으로 제한한다.
- 1단계 완료:
  - `MainViewModel.saveExpenses()`에서 API 키 여부와 무관하게 미분류 가게명 사전 분류를 호출하도록 변경했다.
  - `CategoryClassifierServiceImpl.classifyStoreNamesInMemory()`에서 API 키가 없으면 로컬 규칙 결과만 저장/반환하고 Gemini/임베딩 그룹핑 단계로 내려가지 않도록 했다.
  - 로컬 규칙만 저장되는 경우 `CategoryMappingEntity.source`를 `local`로 남기도록 했다.
  - 검증: `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` 성공.
- 2단계 검토 결과:
  - 기존 `PRE_CLASSIFY_RULES`에는 `자동납부`, `카드결제`, `푸드`, `식당`처럼 넓은 키워드가 포함되어 있어 벡터 매칭보다 앞당기면 오분류를 만들 수 있다.
  - 로컬 고확도 규칙 우선 적용은 golden dataset 또는 최소 회귀 테스트를 만든 뒤 다시 진행한다.
- 3단계 완료:
  - `classifyStoreNamesInMemory()`에 count-only 계측 로그를 추가했다.
  - 로그는 input/transfer/rule/geminiCandidates/groups/geminiResults/apiKeySkipped/result 숫자와 boolean만 포함하며 가게명, SMS 원문, 금액은 기록하지 않는다.
  - `MoneyTalkLogger.i()`를 사용하므로 debug build에서만 출력된다.
  - 검증: `git diff --check`, `./gradlew testDebugUnitTest`, `./gradlew assembleDebug` 성공.
- 재귀 검증 완료:
  - 라운드 1: 변경 파일 전체를 다시 읽고 API 키 없음/있음/로컬-only 경로를 추적했다. 수정 대상 없음.
  - 라운드 2: `classifyStoreNamesInMemory()` 호출자와 `hasApiKey()` 구현을 교차 확인하고 `./gradlew testDebugUnitTest`를 재실행했다. 수정 대상 없음.
  - 라운드 3: 최종 diff와 문서 기록을 확인하고 `./gradlew assembleDebug`를 재실행했다. 수정 대상 없음.
  - 최종 판정: 현재 변경은 DB/Gradle 변경 없이 기존 파이프라인의 빈틈을 보완하는 범위라 적절하다. 단, 4단계 회귀 테스트 보강 전에는 로컬 규칙 우선순위 변경을 진행하지 않는다.
