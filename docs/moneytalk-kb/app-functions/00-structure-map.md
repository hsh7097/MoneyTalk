---
type: structure-map
title: App Functions 구조 지도
description: App Functions 기능에 참여하는 함수, reader, model, metadata 파일을 정리한다.
tags: [moneytalk, app-functions, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/core/appfunctions/
timestamp: 2026-07-09T03:25:00+09:00
status: draft
---

# App Functions 구조 지도

| group | 파일 | 책임 |
|---|---|---|
| entry point | `MoneyTalkAppFunctionEntryPoint.kt` | App Functions 객체 생성/의존성 연결 |
| finance summary functions | `MoneyTalkFinanceAppFunctions.kt` | 월간 요약 단일 함수 노출 |
| finance summary reader | `MoneyTalkFinanceSummaryReader.kt` | 월간 요약 데이터 조합 |
| chat operation functions | `MoneyTalkChatAppFunctions.kt` | 조회/수정 함수 다수 노출 |
| chat operation reader | `MoneyTalkChatAppFunctionReader.kt` | 저장 데이터 조회, 기간 검증과 typed 응답 조합 |
| chat operation actions | `MoneyTalkChatAppFunctionActionExecutor.kt` | 거래/설정 수정 입력 검증, Repository/DAO/DataStore 쓰기, 변경 알림 |
| analytics calculator | `MoneyTalkAppFunctionAnalyticsCalculator.kt` | 전달받은 지출의 필터, 그룹, 메트릭, 정렬 계산 |
| shared input rules | `MoneyTalkAppFunctionInputRules.kt` | 조회·수정 날짜 해석, 카테고리 범위, 결과 개수 제한 |
| response models | `MoneyTalkFinanceSummaryModels.kt`, `MoneyTalkChatAppFunctionModels.kt` | `@AppFunctionSerializable` 응답 contract |
| metadata | `app/src/main/res/xml/app_functions_app_metadata.xml` | 앱 함수 metadata 등록 |
| generated metadata | `app/build/generated/ksp/debug/resources/assets/app_functions.xml` | 실제 등록 함수 수와 기본 enabled 상태 확인 |

`MoneyTalkApplication`의 생성 factory는 EntryPoint에서 Reader와 ActionExecutor를 각각 받아 `MoneyTalkChatAppFunctions`에 연결한다. 노출 클래스가 기능별 실행자를 직접 선택하므로 Reader에 수정 함수 위임 껍데기를 남기지 않는다. 등록 함수의 이름, 인자, 응답 모델과 기본 enabled 설정은 이 내부 분리로 변경하지 않는다.

## 현재 등록 상태

| 항목 | 값 |
|---|---|
| 총 등록 함수 | 50 |
| 기본 활성 함수 | 46 |
| 기본 비활성 함수 | 4 |
| 상세 목록 | [06-function-catalog.md](06-function-catalog.md) |
| 운영 점검 순서 | [07-operational-playbook.md](07-operational-playbook.md) |

## 관련 KB

| KB | 이유 |
|---|---|
| [finance-data](../finance-data/README.md) | Repository/DAO 데이터 접근 |
| [category-classification](../category-classification/README.md) | category update/custom category 함수 영향 |
| [history](../history/README.md) | 거래 추가/수정 결과 화면 영향 |
