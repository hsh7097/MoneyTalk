---
type: domain
title: MoneyTalk Chat KB
description: AI 채팅의 Gemini 호출, DataQuery 실행, 토큰 비용 경계, 로컬 정형 조회 우회 구현을 정리한다.
tags: [moneytalk, kb, chat, gemini, token-cost]
resource: app/src/main/java/com/sanha/moneytalk/feature/chat/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Chat KB

> 상태: draft
> 기준: 2026-07-09 현재 `feature/chat/**`, `core/util/LocalChatQueryRouter.kt`, `core/util/DataQueryParser.kt`, `core/util/ChatCreditPolicy.kt`, `core/util/ChatContextBuilder.kt`, 루트 채팅/AI 문서 내용을 KB 기준으로 재작성

이 문서는 MoneyTalk AI 채팅 작업에서 Gemini 호출 경계와 앱 내부 계산 경계를 빠르게 찾기 위한 KB다.
로컬 정형 조회 우회는 1차 구현됐지만, 기간 해석 범위는 보수적으로 제한되어 있다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| 이 문서 | AI 채팅의 현재 3-step 구조, 비용 경계, 로컬 정형 조회 우회 구현 | `feature/chat/**`, `LocalChatQueryRouter`, `ChatCreditPolicy`, `DataQueryParser`, 토큰 비용 절감 작업 |
| [00-structure-map.md](00-structure-map.md) | Chat 탭 UI/data/prompt/router 파일 구조를 정리한다. | 변경 파일이 Chat 내부 어느 책임인지 판단할 때 |
| [05-system-contract.md](05-system-contract.md) | Local Fast Path, Gemini 3-step, Query/Action/ANALYTICS, 모델/프롬프트 계약을 설명한다. | 채팅 실행 흐름, 비용/수치 안전 경계, query/action 타입 확인 |
| [package-reference/README.md](package-reference/README.md) | Chat 화면 세부 개발 문서 인덱스다. | entry/data/rendering/checklist 중 어떤 문서로 내려갈지 고를 때 |
| [change-log.md](change-log.md) | chat KB 변경 상세 로그 | chat KB가 왜 바뀌었는지 확인할 때 |
| [../budget-credit-monetization/02-policy-and-plans.md](../budget-credit-monetization/02-policy-and-plans.md) | AI 크레딧/광고/심층 분석 후속 계획 | 채팅 비용, 크레딧, 결제/광고 후속 작업 |
| ../finance-data/README.md | Room DB/DAO/Repository 라우팅 | 채팅 쿼리가 실제 금융 데이터를 읽는 위치 확인 |

## 현재 구현 요약

현재 AI 채팅은 `ChatViewModel.sendMessage()`에서 시작해 아래 흐름으로 동작한다.

```text
사용자 질문
-> ChatCreditPolicy.estimate()
-> LocalChatQueryRouter.tryRoute()
   -> 매칭 성공: executeQuery() -> Local template answer -> saveLocalExchange()
   -> 매칭 실패: 기존 Gemini 3-step
-> ChatRepository.sendMessageAndBuildContext()
-> GeminiRepository.analyzeQueryNeeds()
-> DataQueryParser.parseQueryRequest()
-> ChatViewModel.executeQuery() / executeAction() / executeAnalytics()
-> ChatContextBuilder.buildFinalAnswerPrompt()
-> GeminiRepository.generateFinalAnswerWithContext()
-> ChatRepository.saveAiResponseAndUpdateSummary()
```

현재 구조의 핵심은 Gemini가 원본 DB를 직접 계산하지 않고, `DataQueryRequest` JSON으로 필요한 쿼리/액션만 요청한다는 점이다.
실제 합계, 평균, 건수, 비율, 필터링은 `ChatViewModel.executeQuery()`와 `executeAnalytics()`에서 앱이 수행한다.
1차 로컬 우회에서는 `LocalChatQueryRouter`가 안전한 조회 질문을 `DataQuery`로 직접 만들고, 같은 `executeQuery()`를 실행한 뒤 Gemini 없이 `strings.xml` 템플릿 응답을 저장한다.

## 현재 토큰 비용 경계

로컬 라우터가 매칭하지 못한 조회는 기존 Gemini 경로로 들어가며 아래 단계에서 토큰 비용이 발생할 수 있다.

| 단계 | 현재 입력 | 비용/정확도 영향 |
|---|---|---|
| Local route | 현재 질문 | 매칭 성공 시 Gemini 호출 없이 `executeQuery()`와 템플릿 응답만 사용한다. |
| Step 1 query analyzer | 요약, 최근 대화, 현재 질문, 날짜 기준 | 로컬 라우터 미매칭 조회 또는 상담/분석 질문에서 호출된다. |
| Step 3 final answer | 월수입, 조회 결과 문자열, 액션 결과, 최근 대화, 현재 질문 | 로컬 라우터 미매칭 조회 또는 상담/분석 질문에서 호출된다. |
| Rolling Summary | 윈도우 밖 대화 메시지 | 로컬 조회는 `saveLocalExchange()`로 사용자/응답을 저장해 summary 갱신을 건너뛴다. 기존 Gemini 경로는 사용자 메시지 저장 시 summary 갱신 경로를 탄다. |

`ChatCreditPolicy`의 `CHAT_MESSAGE`는 사용자 채팅 전송 1회가 1크레딧이라는 뜻이다.
로컬 단순 조회가 Gemini API 호출을 생략하더라도 사용자 크레딧 정책은 채팅 1회 기준으로 동일하게 적용된다.

## 이미 구현된 비용 절감 장치

| 장치 | 상태 | 설명 |
|---|---|---|
| `DataQueryRequest` | 구현됨 | Gemini 응답을 쿼리/액션 JSON으로 제한한다. |
| `ANALYTICS` 쿼리 | 구현됨 | 복합 필터, 그룹핑, 집계는 앱에서 결정론적으로 계산한다. |
| 수치 직접 계산 금지 프롬프트 | 구현됨 | 최종 답변 모델은 앱 계산 결과만 인용해야 한다. |
| 채팅 1회 1크레딧 | 구현됨 | 질문 유형과 무관하게 사용자 채팅 전송을 1크레딧으로 본다. |
| 운영 기본 모델 Flash-Lite | 구현됨 | RTDB와 앱 fallback에서 Pro/preview 계열 비용을 피한다. |
| `LocalChatQueryRouter` | 구현됨 | 안전한 단순 조회를 Gemini analyze/final/summary 밖에서 처리한다. |

## 구현됨: 로컬 정형 조회 우회

목표는 단순 조회를 Gemini 경로 밖으로 완전히 빼서 토큰 비용을 0에 가깝게 만드는 것이다.
1차 구현은 보수적 라우팅으로 시작한다.

```text
사용자 질문
-> LocalChatQueryRouter.tryRoute(message)
   -> 매칭 성공: DataQuery 생성
      -> ChatViewModel.executeQuery() 실행
      -> ChatViewModel.buildLocalLookupResponse()로 템플릿 응답 생성
      -> Gemini analyze/final answer 호출 생략
      -> Rolling Summary 갱신 생략 또는 로컬 저장만 수행
   -> 매칭 실패: 기존 Gemini 3-step 경로 사용
```

현재 코드는 별도 `LocalChatAnswerFormatter` 클래스 대신 `ChatViewModel.buildLocalLookupResponse()`에서 `strings.xml` 템플릿을 사용한다.
로컬 응답 저장은 `ChatRepository.saveLocalExchange()`가 담당하며, 이 경로는 사용자 메시지와 로컬 응답만 저장하고 summary 갱신을 호출하지 않는다.

### LocalChatQueryRouter 처리 범위

| 질문 유형 | 로컬 생성 쿼리 | 비고 |
|---|---|---|
| `이번 달 총 지출 얼마야` | `QueryType.TOTAL_EXPENSE` | 앱의 커스텀 월 시작일을 사용한다. |
| `식비 얼마야`, `배달 얼마야` | `QueryType.TOTAL_EXPENSE` + category | 상위 카테고리는 기존 `executeQuery()`의 하위 포함 규칙을 재사용한다. |
| `식비 내역 보여줘` | `QueryType.EXPENSE_LIST` + category + limit | 현재 앱 기준 이번 달 범위에서 조회한다. |
| `카테고리별 지출 보여줘` | `QueryType.EXPENSE_BY_CATEGORY` | leaf 카테고리 분해를 유지한다. |
| `최근 지출 10개` | `QueryType.EXPENSE_LIST` + 1970-01-01~오늘 + limit | 결과 리스트를 Gemini에 다시 보내지 않는다. |
| `예산 현황` | `QueryType.BUDGET_STATUS` | 현재 `executeBudgetStatusQuery()`를 재사용한다. |
| `미분류 몇 건`, `미분류 보여줘` | `QueryType.UNCATEGORIZED_LIST` | 카테고리 정리 CTA는 템플릿으로 처리한다. |
| `올해 월별 지출 보여줘` | `QueryType.MONTHLY_TOTALS` + 올해 1월 1일~오늘 | 월별 합계를 Gemini 없이 조회한다. |
| `일별 지출 보여줘` | `QueryType.DAILY_TOTALS` | 기간을 명시하지 않으면 앱 기준 이번 달만 처리한다. |
| `사용 카드 목록 보여줘` | `QueryType.CARD_LIST` | 제외 카드 필터를 기존 쿼리에서 재사용한다. |
| `중복 지출 내역 있어?` | `QueryType.DUPLICATE_LIST` | 중복 조회만 처리하고 삭제는 기존 Gemini/action 경로로 넘긴다. |
| `수입 얼마야` | `QueryType.TOTAL_INCOME` | 기간을 명시하지 않으면 앱 기준 이번 달만 처리한다. |

### 로컬 라우팅 제외 범위

아래 표현이 있으면 기존 Gemini 경로로 보낸다.

| 제외 범위 | 이유 |
|---|---|
| `분석`, `비교`, `추세`, `패턴`, `왜`, `원인` | 해석/분석 문장 생성이 필요하다. |
| `줄여`, `절약`, `추천`, `조언`, `많은 편`, `적절`, `평가` | 단순 조회가 아니라 상담/판단 요청이다. |
| `설정`, `변경`, `수정`, `삭제`, `추가` | DB 수정 액션이므로 기존 action 안전 경로를 탄다. |
| `지난달`, `오늘`, `어제`, `지난 3개월` 등 현재 미지원 기간 표현 | 로컬 라우터가 잘못된 기본 기간으로 계산하지 않도록 보수적으로 제외한다. |
| `쇼핑 얼마야` | `온라인쇼핑`과 `패션/쇼핑`이 모호하므로 Gemini clarification 여지를 둔다. |

### 실행 원칙

1. LLM이 임의 SQL이나 임의 수식을 반환하게 하지 않는다.
2. 로컬 라우터 또는 LLM이 만들 수 있는 구조는 `DataQueryRequest`/`DataQuery` 화이트리스트로 제한한다.
3. 실행 전 앱이 `QueryType`, 날짜, 카테고리, 필터 필드, 연산자, metric을 검증한다.
4. 단순 조회 응답은 `ChatViewModel.buildLocalLookupResponse()`가 템플릿으로 만든다.
5. 상담, 해석, 비교 원인 분석처럼 문장 생성 가치가 있는 질문만 Gemini final answer를 호출한다.
6. 원본 거래 전체를 Gemini에 보내지 않고, 필요 시 앱이 만든 집계 JSON만 보낸다.

## 수정 시 확인할 파일

| 파일 | 역할 | 언제 보는가 |
|---|---|---|
| `feature/chat/ui/ChatViewModel.kt` | 채팅 orchestration, 쿼리/액션/분석 실행 | 로컬 우회 분기, executeQuery 재사용, 응답 저장 방식 변경 |
| `core/util/LocalChatQueryRouter.kt` | 단순 조회 문구를 `DataQuery`로 변환 | 로컬 처리 범위, 제외 키워드, 기간 제한 변경 |
| `feature/chat/data/GeminiRepositoryImpl.kt` | Gemini 모델 호출 | analyze/final answer 호출 조건 변경 |
| `feature/chat/data/ChatRepositoryImpl.kt` | 사용자/AI 메시지 저장, Rolling Summary | 로컬 응답에서 summary 호출을 피할 때 |
| `core/util/DataQueryParser.kt` | `DataQueryRequest`, `DataQuery`, `QueryType`, `ActionType` | 로컬 라우터가 만들 수 있는 쿼리 계약 변경 |
| `core/util/ChatCreditPolicy.kt` | 채팅 1회 1크레딧 산정 | 크레딧 단가 변경 |
| `core/util/ChatContextBuilder.kt` | Step 1/Step 3 프롬프트 컨텍스트 구성 | Gemini 입력 축소 또는 구조화 JSON 전환 |

## 화면 UI 작업

채팅 탭 화면 자체를 수정할 때는 아래 순서로 본다.

1. [00-structure-map.md](00-structure-map.md)
2. [package-reference/01-entry-screen.md](package-reference/01-entry-screen.md)
3. [package-reference/03-rendering-action.md](package-reference/03-rendering-action.md)
4. [package-reference/02-data-viewmodel.md](package-reference/02-data-viewmodel.md)

## 검증 질문

로컬 정형 조회 우회를 수정할 때는 아래를 확인한다.

1. 단순 조회 매칭 성공 시 `analyzeQueryNeeds()`가 호출되지 않는가?
2. 단순 조회 매칭 성공 시 `generateFinalAnswerWithContext()`가 호출되지 않는가?
3. 로컬 응답 저장이 Rolling Summary Gemini 호출을 유발하지 않는가?
4. 기존 Gemini 경로가 필요한 상담/분석 질문은 그대로 동작하는가?
5. 커스텀 월 시작일, 제외 카드, `isExcludedFromStats`, 하위 카테고리 포함 규칙이 기존 `executeQuery()`와 동일하게 적용되는가?
6. 사용자가 보는 문장은 템플릿 응답이어도 어색하지 않은가?
7. 미지원 기간 표현은 잘못된 기본 기간으로 로컬 계산하지 않고 기존 Gemini 경로로 넘어가는가?
