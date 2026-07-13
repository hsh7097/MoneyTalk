---
type: feature
title: App Functions 기능
description: agent가 MoneyTalk 앱 데이터를 읽거나 일부 설정/거래를 수정하는 App Functions 기능 KB다.
tags: [moneytalk, app-functions, feature, agent]
resource: app/src/main/java/com/sanha/moneytalk/core/appfunctions/
timestamp: 2026-07-13T23:23:18+09:00
status: draft
---

# App Functions 기능

> 상태: draft
> 기준: 2026-07-13 현재 `core/appfunctions`, 앱 수준 metadata, KSP 생성 `app_functions.xml`, 흡수된 App Functions 카탈로그 확인

App Functions 기능은 assistant/agent가 앱 내부 데이터를 조회하거나 일부 설정/거래를 수정할 수 있게 노출하는 기능이다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-structure-map.md](00-structure-map.md) | App Functions 파일과 책임을 정리한다. | 변경 파일이 노출 함수, reader, model 중 어디인지 판단할 때 본다. |
| [01-feature-flow.md](01-feature-flow.md) | agent 호출부터 Repository/DAO 접근, 응답 반환까지 흐름을 설명한다. | 기능 읽어오기/수정 흐름을 따라갈 때 본다. |
| [02-data-contract.md](02-data-contract.md) | App Function 함수, request parameter, response model contract를 설명한다. | 함수 추가/응답 모델 변경 시 본다. |
| [03-extension-points.md](03-extension-points.md) | 새 App Function 추가와 삭제성 함수 정책을 정리한다. | agent 기능을 늘릴 때 본다. |
| [04-files-checklist.md](04-files-checklist.md) | 수정 전후 확인 파일과 검증 질문이다. | 리뷰 전 누락 점검 |
| [05-file-inventory.md](05-file-inventory.md) | 관련 파일 역할 인덱스다. | 수정 후보가 애매할 때 본다. |
| [06-function-catalog.md](06-function-catalog.md) | 등록된 50개 App Function을 그룹별로 정리한다. | agent 호출 가능 범위, 비활성 삭제 함수, 누락 함수 여부를 확인할 때 본다. |
| [07-operational-playbook.md](07-operational-playbook.md) | DB 상태 점검과 복원 검증에서 App Functions를 쓰는 순서를 정리한다. | 중복/카드/거래처 규칙/SMS 제외/예산 상태를 확인할 때 본다. |
| [change-log.md](change-log.md) | 기능 KB 변경 로그다. | 변경 이유 확인 |

## 기능 요약

```text
assistant/agent
→ @AppFunction method
→ Reader
→ Repository/DAO/DataStore
→ Serializable response model
```

## 현재 노출 범위

| 항목 | 값 |
|---|---|
| SDK | `androidx.appfunctions:appfunctions-*:1.0.0-alpha08` |
| 등록 함수 | 50개 |
| 기본 활성 함수 | 46개 |
| 기본 비활성 함수 | 4개 삭제성 함수 |
| 등록 metadata | `app/src/main/res/xml/app_functions_app_metadata.xml` |
| KSP 생성 확인 | `app/build/generated/ksp/debug/resources/assets/app_functions.xml` |

`1.0.0-alpha09`는 `compileSdk 37+`, `AGP 9.1.0+` 요구 조건 때문에 현재 프로젝트 설정에서는 올리지 않는다.

## 앱 수준 metadata 계약

`AndroidManifest.xml`의 `android.app.appfunctions.app_metadata` property는 `res/xml/app_functions_app_metadata.xml`을 가리킨다. 이 XML은 Play Console이 AAB 업로드 단계에서 별도로 파싱하므로 다음 형식을 유지한다.

```xml
<AppFunctionAppMetadata
    xmlns:appfunctions="http://schemas.android.com/apk/androidx.appfunctions"
    appfunctions:description="앱 함수 사용 범위를 설명하는 문장"
    appfunctions:displayDescription="@string/app_functions_app_display_description" />
```

- 루트 태그 `AppFunctionAppMetadata`는 대소문자를 포함해 그대로 사용한다.
- `description`은 metadata XML에 문자열로 선언한다.
- 사용자에게 보이는 `displayDescription`은 지역화 string resource를 참조한다.
- KSP 생성 `assets/app_functions.xml`, `assets/app_functions_v2.xml`과 앱 수준 metadata XML은 서로 다른 산출물이다. 한쪽의 XML 파싱 성공만으로 다른 쪽의 Play 호환성을 판단하지 않는다.
- `bundletool validate`는 AAB 구조 검증이다. metadata 의미 검증의 최종 release gate는 Play Console 업로드 성공이다.
