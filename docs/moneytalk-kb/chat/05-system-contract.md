---
type: reference
title: Chat System Contract
description: AI 채팅의 Local Fast Path, Gemini 3-step, 쿼리/액션/ANALYTICS, 모델/프롬프트 경계를 설명한다.
tags: [moneytalk, chat, gemini, data-query, analytics]
resource: app/src/main/java/com/sanha/moneytalk/feature/chat/
timestamp: 2026-07-09T05:30:00+09:00
status: draft
---

# Chat System Contract

> 기준: 흡수된 채팅 시스템 원문과 project-context 채팅 파트를 KB용으로 재작성

채팅 시스템은 자연어 질문을 앱 내부 `DataQuery`/`Action`/`ANALYTICS` 계약으로 바꾼 뒤, 실제 계산과 DB 수정은 앱이 수행하고 Gemini는 분석 요청 생성과 최종 문장화를 담당한다.

## 실행 흐름

```text
ChatViewModel.sendMessage(message)
-> ChatCreditPolicy.estimate()
-> LocalChatQueryRouter.tryRoute()
   -> 성공: executeQuery() -> buildLocalLookupResponse() -> ChatRepository.saveLocalExchange()
   -> 실패: Gemini 3-step
-> ChatRepository.sendMessageAndBuildContext()
-> GeminiRepository.analyzeQueryNeeds()
-> DataQueryParser.parseQueryRequest()
-> executeQuery() / executeAction() / executeAnalytics()
-> ChatContextBuilder.buildFinalAnswerPrompt()
-> GeminiRepository.generateFinalAnswerWithContext()
-> ChatRepository.saveAiResponseAndUpdateSummary()
```

## Local Fast Path

`LocalChatQueryRouter`가 안전하게 해석할 수 있는 단순 조회는 Gemini analyzer, final answer, Rolling Summary 요약 모델을 호출하지 않는다. 라우터가 만든 `DataQuery`는 기존 `ChatViewModel.executeQuery()`를 재사용하고, 응답은 템플릿으로 저장한다.

| 로컬 처리 | 비고 |
|---|---|
| 이번 달 총 지출 | 사용자 설정 월 시작일을 반영 |
| 특정 카테고리 지출/내역 | 명시적 식비 조회는 하위 카테고리 포함 |
| 카테고리별 지출 | leaf 카테고리 분해 유지 |
| 최근 지출 N건 | 결과 리스트를 Gemini에 다시 보내지 않음 |
| 예산 현황 | 기존 예산 query 재사용 |
| 미분류 항목 | 카테고리 정리 CTA는 템플릿 처리 |
| 올해 월별 지출, 이번 달 일별 지출 | 안전한 기간만 로컬 처리 |
| 카드 목록, 중복 지출 조회, 이번 달 수입 합계 | 조회만 처리 |

아래 질문은 Gemini 경로로 넘긴다.

| 제외 범위 | 이유 |
|---|---|
| 분석, 비교, 추세, 패턴, 원인 | 해석/상담 문장이 필요 |
| 절약, 추천, 조언, 평가 | 단순 조회가 아니라 판단 요청 |
| 설정, 변경, 수정, 삭제, 추가 | DB 수정 action 안전 경로 필요 |
| 지난달, 오늘, 어제, 지난 3개월 등 미지원 기간 표현 | 잘못된 기본 기간 계산 방지 |
| 쇼핑처럼 모호한 카테고리 | clarification 여지 유지 |

## Query 타입

| 타입 | 설명 |
|---|---|
| `total_expense` | 기간 내 총 지출 |
| `total_income` | 기간 내 총 수입 |
| `expense_by_category` | 카테고리별 지출 합계 |
| `expense_list` | 지출 내역 리스트 |
| `expense_by_store` | 특정 가게 지출 |
| `expense_by_card` | 특정 카드 지출 |
| `daily_totals` | 일별 지출 합계 |
| `monthly_totals` | 월별 지출 합계 |
| `monthly_income` | 설정된 월 수입 |
| `uncategorized_list` | 미분류 항목 |
| `category_ratio` | 수입 대비 비율 분석 |
| `search_expense` | 가게/카테고리/카드 검색 |
| `card_list` | 사용 카드 목록 |
| `income_list` | 수입 내역 |
| `duplicate_list` | 중복 지출 항목 |
| `sms_exclusion_list` | SMS 제외 키워드 목록 |
| `analytics` | 필터/그룹핑/집계 복합 분석 |
| `budget_status` | 예산 현황 |

## Action 타입

| 타입 | 설명 |
|---|---|
| `update_category` | 특정 지출 카테고리 변경 |
| `update_category_by_store` | 가게명 기준 일괄 변경 |
| `update_category_by_keyword` | 키워드 기준 일괄 변경 |
| `delete_expense` | 특정 지출 삭제 |
| `delete_by_keyword` | 키워드 기준 일괄 삭제 |
| `delete_duplicates` | 중복 항목 전체 삭제 |
| `add_expense` | 수동 지출 추가 |
| `update_memo` | 메모 수정 |
| `update_store_name` | 가게명 수정 |
| `update_amount` | 금액 수정 |
| `add_sms_exclusion` | SMS 제외 키워드 추가 |
| `remove_sms_exclusion` | SMS 제외 키워드 삭제 |
| `set_budget` | 카테고리별 월 예산 설정/변경 |

## ANALYTICS 계약

| 구성 | 지원 값 |
|---|---|
| 필터 연산자 | `==`, `!=`, `>`, `>=`, `<`, `<=`, `contains`, `not_contains`, `in`, `not_in` |
| 필터 필드 | `category`, `storeName`, `cardName`, `amount`, `memo`, `dayOfWeek` |
| 그룹핑 | `category`, `storeName`, `cardName`, `date`, `month`, `dayOfWeek` |
| 집계 metric | `sum`, `avg`, `count`, `max`, `min` |
| 정렬 | `asc`, `desc` |

수치 안전 원칙:

- 합계/평균/건수/최대/최소는 `executeAnalytics()` 또는 Room query가 계산한다.
- Gemini final answer는 `[조회된 데이터]`, `[ANALYTICS 계산 결과]`만 인용한다.
- 서로 다른 query 결과를 Gemini가 임의 합산/비교해 새 수치를 만들지 않는다.
- 데이터가 없거나 표본이 1~2건이면 패턴/습관/추세 판단을 보류한다.

## 모델과 프롬프트

| 역할 | 기본 모델 | temperature | 프롬프트 |
|---|---|---:|---|
| Query analyzer | `gemini-2.5-flash-lite` | 0.3 | `prompt_query_analyzer_system`, `prompt_query_analyzer_user` |
| Financial advisor final answer | `gemini-2.5-flash-lite` | 0.7 | `prompt_financial_advisor_system`, `prompt_final_answer_*` |
| Rolling Summary / title | `gemini-2.5-flash` | 0.3 | `prompt_summary_system`, `prompt_rolling_summary_*`, `prompt_chat_title_user` |

프롬프트 본문은 `app/src/main/res/values/string_prompt.xml`에서 관리한다. 보조 라벨/상태 문자열은 `app/src/main/res/values/strings.xml`의 `ai_*` 또는 chat 관련 string을 확인한다.

## Rolling Summary

- 최근 3턴, 6개 메시지를 window로 유지한다.
- window 밖 메시지는 summary model로 200자 이내 한국어 요약으로 압축한다.
- 요약은 `ChatSessionEntity.currentSummary`에 세션별로 저장한다.
- Local Fast Path 응답은 `saveLocalExchange()` 경로로 저장해 summary 갱신 모델 호출을 생략한다.
