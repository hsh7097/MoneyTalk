---
type: log
title: App Functions KB Change Log
description: App Functions 기능 KB 변경 상세 이력을 기록한다.
tags: [moneytalk, app-functions, changelog]
resource: docs/moneytalk-kb/app-functions/
timestamp: 2026-07-13T23:23:18+09:00
status: draft
---

# App Functions Change Log

## 2026-07-13

- 기준: Play Console의 Android App Functions metadata XML 파싱 오류와 release AAB 내부 파일 확인
- 원인: 앱 수준 metadata 루트가 공식 `AppFunctionAppMetadata`가 아닌 `app-function-app-metadata`로 선언됨
- 변경:
  - 루트 태그, App Functions library namespace, `description` 선언을 공식 형식으로 정렬
  - KSP 생성 함수 metadata와 앱 수준 metadata의 검증 경계를 KB에 명시
  - `bundletool validate`와 Play Console 업로드를 분리된 release gate로 기록

## 2026-07-09

- 기준: `MoneyTalkChatAppFunctions.kt`, `MoneyTalkFinanceAppFunctions.kt`, `MoneyTalkApplication.kt`, `docs/APP_FUNCTIONS.md`, KSP 생성 `app_functions.xml` 확인
- 변경 근거: App Functions 50개 등록 함수와 DB 점검 절차를 KB에서 바로 찾을 수 있어야 한다는 요구 반영
- 갱신한 KB:
  - `README.md`
  - `00-structure-map.md`
  - `02-data-contract.md`
  - `03-extension-points.md`
  - `04-files-checklist.md`
  - `05-file-inventory.md`
  - `06-function-catalog.md`
  - `07-operational-playbook.md`
- 확인 결과:
  - KSP 생성 metadata 기준 등록 함수 50개, disabled 4개
  - 삭제성 함수 `deleteExpense`, `deleteExpensesByKeyword`, `deleteDuplicateExpenses`, `deleteStoreRule`은 기본 비활성 유지

## 2026-07-03

- 기준: `core/appfunctions`, `docs/APP_FUNCTIONS.md` 확인
- 변경 근거: 기능 단위 KB 요구 반영
- 갱신한 KB: `app-functions/**`
- 다음 검증: 새 조회 함수 추가 작업에서 Functions/Reader/Model/docs/KSP metadata를 정확히 찾는지 확인
