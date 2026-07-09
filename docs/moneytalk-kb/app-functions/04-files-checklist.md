---
type: checklist
title: App Functions Files Checklist
description: App Functions 수정 전후에 확인할 파일과 검증 질문을 정리한다.
tags: [moneytalk, app-functions, checklist]
resource: app/src/main/java/com/sanha/moneytalk/core/appfunctions/
timestamp: 2026-07-09T02:45:00+09:00
status: draft
---

# 04 Files Checklist

## 빠른 파일 찾기

| 작업 | 확인 파일 |
|---|---|
| 함수 추가 | `MoneyTalkChatAppFunctions.kt`, `MoneyTalkChatAppFunctionReader.kt` |
| 월간 요약 변경 | `MoneyTalkFinanceAppFunctions.kt`, `MoneyTalkFinanceSummaryReader.kt` |
| 응답 모델 변경 | `MoneyTalkChatAppFunctionModels.kt`, `MoneyTalkFinanceSummaryModels.kt` |
| DB 조회 변경 | [finance-data](../finance-data/README.md), Repository/DAO |
| category 수정 함수 변경 | [category-classification](../category-classification/README.md) |
| 함수 노출 범위 확인 | [06-function-catalog.md](06-function-catalog.md) |
| DB 점검 절차 변경 | [07-operational-playbook.md](07-operational-playbook.md) |

## 수정 전 질문

- 조회 함수인가 수정 함수인가?
- 삭제성 함수라면 기본 비활성 정책을 유지했는가?
- App Function 본문에서 DB 작업이 IO dispatcher로 오프로드되는가?
- response model 변경이 generated metadata에 반영되는가?
- 함수 카탈로그와 운영 플레이북 갱신이 필요한가?
- 기존 루트 문서와 충돌하면 [../source-docs/01-consolidation-map.md](../source-docs/01-consolidation-map.md)에 따라 KB 우선으로 정리했는가?
