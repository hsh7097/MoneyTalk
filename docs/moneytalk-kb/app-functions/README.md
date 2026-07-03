---
type: feature
title: App Functions 기능
description: agent가 MoneyTalk 앱 데이터를 읽거나 일부 설정/거래를 수정하는 App Functions 기능 KB다.
tags: [moneytalk, app-functions, feature, agent]
resource: app/src/main/java/com/sanha/moneytalk/core/appfunctions/
timestamp: 2026-07-03T17:10:00+09:00
status: draft
---

# App Functions 기능

> 상태: draft
> 기준: 2026-07-03 현재 `core/appfunctions`와 `docs/APP_FUNCTIONS.md` 확인

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
| [change-log.md](change-log.md) | 기능 KB 변경 로그다. | 변경 이유 확인 |

## 기능 요약

```text
assistant/agent
→ @AppFunction method
→ Reader
→ Repository/DAO/DataStore
→ Serializable response model
```
