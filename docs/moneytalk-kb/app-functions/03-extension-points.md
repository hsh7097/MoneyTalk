---
type: extension-points
title: App Functions Extension Points
description: App Functions 추가/변경 지점과 안전 정책을 정리한다.
tags: [moneytalk, app-functions, extension]
resource: app/src/main/java/com/sanha/moneytalk/core/appfunctions/
timestamp: 2026-07-09T02:45:00+09:00
status: draft
---

# 03 Extension Points

| 확장 지점 | 파일 | 주의 |
|---|---|---|
| 새 조회 함수 | `MoneyTalkChatAppFunctions.kt`, `MoneyTalkChatAppFunctionReader.kt` | response model과 docs 동시 갱신 |
| 새 요약 함수 | `MoneyTalkFinanceAppFunctions.kt`, `MoneyTalkFinanceSummaryReader.kt` | 날짜/monthStartDay validation 확인 |
| 새 응답 모델 | `MoneyTalkChatAppFunctionModels.kt` 또는 `MoneyTalkFinanceSummaryModels.kt` | `@AppFunctionSerializable` contract 유지 |
| 새 수정 함수 | `MoneyTalkChatAppFunctions.kt`, Reader | validation, DataRefreshEvent, 삭제성 정책 확인 |
| metadata | `app_functions_app_metadata.xml`, generated `app_functions.xml` | KSP 생성 결과 확인 |

삭제성 함수는 기본 비활성 정책을 유지한다.
함수 추가/삭제 시 [06-function-catalog.md](06-function-catalog.md)의 등록 함수 수, 활성/비활성 표, 그룹 설명을 함께 갱신한다.
DB 상태 확인 절차에 영향이 있으면 [07-operational-playbook.md](07-operational-playbook.md)의 플레이북도 함께 갱신한다.
