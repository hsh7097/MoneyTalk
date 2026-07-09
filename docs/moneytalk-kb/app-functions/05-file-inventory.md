---
type: file-inventory
title: App Functions File Inventory
description: App Functions 관련 파일 역할과 참조 시점을 인덱싱한다.
tags: [moneytalk, app-functions, file-inventory]
resource: app/src/main/java/com/sanha/moneytalk/core/appfunctions/
timestamp: 2026-07-09T02:45:00+09:00
status: draft
---

# App Functions File Inventory

| 파일 | 역할 | 언제 보는가 | 분류 |
|---|---|---|---|
| `MoneyTalkAppFunctionEntryPoint.kt` | App Functions 객체와 의존성 entry point | DI/생성 문제 | 핵심 후보 |
| `MoneyTalkFinanceAppFunctions.kt` | 월간 요약 App Function 노출 | 월간 요약 함수 변경 | 핵심 |
| `MoneyTalkFinanceSummaryReader.kt` | 월간 요약 데이터 조합 | summary 계산/validation | 핵심 |
| `MoneyTalkFinanceSummaryModels.kt` | 월간 요약 응답 모델 | response contract 변경 | 핵심 후보 |
| `MoneyTalkChatAppFunctions.kt` | 조회/수정 App Function 선언 | 함수 추가/삭제/enable 변경 | 핵심 |
| `MoneyTalkChatAppFunctionReader.kt` | 실제 DB/DataStore/Repository 작업 | business logic 변경 | 핵심 |
| `MoneyTalkChatAppFunctionModels.kt` | chat operation response model | 응답 모델 변경 | 핵심 후보 |
| `app_functions_app_metadata.xml` | App Functions metadata 등록 | generated metadata 문제 | 핵심 후보 |
| `app/build/generated/ksp/debug/resources/assets/app_functions.xml` | KSP가 생성한 실제 등록 함수 metadata | 함수 수/disabled 수 확인 | 생성 산출물 |

## 문서

| 파일 | 용도 |
|---|---|
| `06-function-catalog.md` | 등록 함수 50개, 활성/비활성 정책, 그룹별 호출 범위 |
| `07-operational-playbook.md` | DB 점검/복원 검증에서 App Functions를 쓰는 순서 |
| `../source-docs/01-consolidation-map.md` | 삭제된 레거시 루트 문서와 현재 KB 목적지 매핑 |
