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
   -> 성공: ChatQueryExecutor.execute() -> buildLocalLookupResponse() -> ChatRepository.saveLocalExchange()
   -> 실패: Gemini 3-step
-> ChatRepository.sendMessageAndBuildContext()
-> GeminiRepository.analyzeQueryNeeds()
-> DataQueryParser.parseQueryRequest()
-> ChatQueryExecutor.execute() / ChatActionExecutor.execute() / ChatAnalyticsCalculator.calculate()
-> ChatContextBuilder.buildFinalAnswerPrompt()
-> GeminiRepository.generateFinalAnswerWithContext()
-> ChatRepository.saveAiResponseAndUpdateSummary()
```

## Local Fast Path

`LocalChatQueryRouter`가 안전하게 해석할 수 있는 단순 조회는 Gemini analyzer, final answer, Rolling Summary 요약 모델을 호출하지 않는다. 라우터가 만든 `DataQuery`는 기존 `ChatQueryExecutor.execute()`를 재사용하고, 응답은 템플릿으로 저장한다.

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

- 합계/평균/건수/최대/최소는 `ChatAnalyticsCalculator.calculate()` 또는 Room query가 계산한다.
- Gemini final answer는 `[조회된 데이터]`, `[ANALYTICS 계산 결과]`만 인용한다.
- 서로 다른 query 결과를 Gemini가 임의 합산/비교해 새 수치를 만들지 않는다.
- 데이터가 없거나 표본이 1~2건이면 패턴/습관/추세 판단을 보류한다.

## 모델과 프롬프트

| 역할 | 기본 모델 | temperature | 프롬프트 |
|---|---|---:|---|
| Query analyzer | `GeminiModelConfig.queryAnalyzer` (기본 `gemini-3.1-flash-lite`) | 0.3 | `prompt_query_analyzer_system`, `prompt_query_analyzer_user` |
| Financial advisor final answer | `GeminiModelConfig.financialAdvisor` (기본 `gemini-3.1-flash-lite`) | 0.7 | `prompt_financial_advisor_system`, `prompt_final_answer_*` |
| Rolling Summary / title | `GeminiModelConfig.summary` (기본 `gemini-3.5-flash`) | 0.3 | `prompt_summary_system`, `prompt_rolling_summary_*`, `prompt_chat_title_user` |

프롬프트 본문은 `app/src/main/res/values/string_prompt.xml`에서 관리한다. 보조 라벨/상태 문자열은 `app/src/main/res/values/strings.xml`의 `ai_*` 또는 chat 관련 string을 확인한다.
모델 객체는 `FirebaseAiModelFactory`에서 생성하며 release 요청은 Play Integrity App Check 검증을 통과해야 한다.

### 인증 실패 처리

- query analyzer가 App Check 인증 오류로 실패하면 같은 질문의 기본 조회 보완과 final answer 호출을 중단한다. 일반 파싱·모델 오류의 기존 fallback은 유지한다.
- 분류 기준은 카테고리 AI가 사용하는 `FirebaseAiRateLimitPolicy`를 재사용하며, 감싸진 예외의 cause도 확인한다. `Too many attempts`만으로는 인증 실패로 단정하지 않고 App Check 문맥이 함께 있어야 한다.
- analyzer와 final answer 예외는 원인 cause를 보존하고 coroutine 취소는 다시 전파한다. 인증 실패 시 이미 차감한 크레딧은 기존 `CreditRefundGuard` 경로로 한 번만 반환한다.
- 사용자에게는 `chat_app_verification_failed`의 한국어 인증 안내를 표시한다. 영어 내부 오류를 그대로 보여주거나 설치 방식이 원인이라고 단정하지 않는다.
- 이 변경은 App Check 설정·토큰 갱신·카테고리의 기존 15분 차단 정책을 바꾸지 않는다. SDK의 `Too many attempts` 상태나 실제 Play Integrity 거절 원인은 별도 운영 진단 대상이다.

`FirebaseAiRateLimitPolicyTest`는 감싸진 인증 실패, 일반 quota/JSON 파싱 실패 유지, App Check 문맥이 있는/없는 시도 제한 오류를 구분한다. 실제 채팅의 final 호출 생략과 안내·환불 표시는 통합 검증 대상이다.

### AI 연결 운영 진단 (2026-09-09)

- 에뮬레이터의 `8890134` 빌드에서 `식비가 수입 대비 적절해?`를 실행하면 Debug App Check의 `403 App attestation failed` 뒤 `Firebase App Check token is invalid`로 실패했다. 같은 빌드의 `이번 달 총 지출 얼마야?`는 Local Fast Path로 정상 응답했다. 로컬 조회 성공을 Gemini 연결 성공으로 판단하지 않는다. 이후 알림/수집/편집 보완에서 AI 연결 코드는 변경하지 않았다.
- Firebase CLI의 기존 로그인으로 SDK 설정, App Check와 RTDB 설정을 읽기 전용 확인했다. Firebase SDK 설정의 API 키는 로컬 `google-services.json`과 일치했고, RTDB의 서비스/무료 사용 플래그와 모델 설정도 활성 상태였다. `local.properties`의 별도 Gemini/Claude 키는 현재 Firebase AI Logic 채팅 경로가 사용하지 않는다. 이번 장애의 첫 실패 지점은 앱 인증이며 API 키 교체 근거는 확인되지 않았다.
- App Check에는 기존 개인 실기기용 debug 등록만 있고 현재 에뮬레이터 등록은 없었다. AI 서비스의 App Check enforcement는 켜져 있었다. 에뮬레이터는 해당 기기의 debug secret을 별도로 등록한 뒤 실제 Gemini 응답까지 재검증해야 한다. 토큰과 인증값은 문서·커밋·진단 출력에 남기지 않는다.
- 마지막 개인용 실기기 설치본은 Release의 Play Integrity 제공자를 유지한다. 보존된 9월 8일 실기기 로그에도 같은 인증 거절이 있어 이번 알림 액션 변경 전부터 발생했다. 현재 로컬 Release 서명과 Firebase 등록 SHA-256이 다르며 `allowUnrecognizedVersion`도 허용하지 않은 상태다. 따라서 개인용 APK 설치 방식과 인증 설정을 함께 맞춰야 한다. 실기기는 이번 점검에 연결되지 않아 수정 후 실기기 인증 성공은 검증하지 못했다.
- 운영 enforcement 해제, Play Integrity 정책 완화, API 키 갱신은 수행하지 않았다. 에뮬레이터 단독 debug 등록은 별도 승인 대기 상태이며, 인증 통과 뒤 모델·할당량·최종 답변까지 확인해야 복구 완료로 판단할 수 있다.

개선 방향: 배포 점검에 실제 설치 경로의 AI 질문 1건을 포함한다. RTDB 활성 플래그나 로컬 조회 성공만으로 연결 상태를 판정하지 않는다. 개인용 배포도 등록된 서명과 설치 경로에 맞는 App Check 검증을 유지한다.

공식 근거: [Android debug provider](https://firebase.google.com/docs/app-check/android/debug-provider), [Play Integrity 설정](https://firebase.google.com/docs/reference/appcheck/rest/v1/projects.apps.playIntegrityConfig). 당시 비밀값을 제거한 점검 결과와 화면은 로컬 `artifacts/ai-notification-audit-20260909/`에 보관한다.

## Rolling Summary

- 최근 3턴, 6개 메시지를 window로 유지한다.
- window 밖 메시지는 summary model로 200자 이내 한국어 요약으로 압축한다.
- 요약은 `ChatSessionEntity.currentSummary`에 세션별로 저장한다.
- Local Fast Path 응답은 `saveLocalExchange()` 경로로 저장해 summary 갱신 모델 호출을 생략한다.
