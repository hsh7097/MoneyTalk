---
type: structure-map
title: App Functions 구조 지도
description: App Functions 기능에 참여하는 함수, reader, model, metadata 파일을 정리한다.
tags: [moneytalk, app-functions, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/core/appfunctions/
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# App Functions 구조 지도

| group | 파일 | 책임 |
|---|---|---|
| entry point | `MoneyTalkAppFunctionEntryPoint.kt` | App Functions 객체 생성/의존성 연결 |
| finance summary functions | `MoneyTalkFinanceAppFunctions.kt` | 월간 요약 단일 함수 노출 |
| finance summary reader | `MoneyTalkFinanceSummaryReader.kt` | 월간 요약 데이터 조합 |
| chat operation functions | `MoneyTalkChatAppFunctions.kt` | 조회/수정 함수 다수 노출 |
| chat operation reader | `MoneyTalkChatAppFunctionReader.kt` | Repository/DAO/DataStore 접근과 business validation |
| response models | `MoneyTalkFinanceSummaryModels.kt`, `MoneyTalkChatAppFunctionModels.kt` | `@AppFunctionSerializable` 응답 contract |
| metadata | `app/src/main/res/xml/app_functions_app_metadata.xml` | 앱 함수 metadata 등록 |

## 관련 KB

| KB | 이유 |
|---|---|
| [finance-data](../finance-data/README.md) | Repository/DAO 데이터 접근 |
| [category-classification](../category-classification/README.md) | category update/custom category 함수 영향 |
| [history](../history/README.md) | 거래 추가/수정 결과 화면 영향 |
