---
type: package-reference
title: Chat data/ViewModel
description: ChatViewModel의 credit gate, local route, Gemini 3-step, repository 저장 흐름을 설명한다.
tags: [moneytalk, chat, viewmodel, data]
resource: app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatViewModel.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Chat data/ViewModel

## 처리 흐름

```text
sendMessage()
-> ChatCreditPolicy.estimate()
-> LocalChatQueryRouter.tryRoute()
   -> match: ChatQueryExecutor.execute() -> template response -> saveLocalExchange()
   -> no match: sendMessageAndBuildContext()
-> GeminiRepository.analyzeQueryNeeds()
-> ChatQueryExecutor.execute()/ChatActionExecutor.execute()/ChatAnalyticsCalculator.calculate()
-> GeminiRepository.generateFinalAnswerWithContext()
-> saveAiResponseAndUpdateSummary()
```

## 주요 책임

| 파일 | 책임 |
|---|---|
| `ChatViewModel.kt` | 세션/전송 orchestration, credit gate, UI state |
| `ChatQueryExecutor.kt` | 18종 조회와 기본 조회 fallback, 카드/통계 제외/기간 적용 |
| `ChatActionExecutor.kt` | 13종 수정 액션, 카테고리 학습 무효화 및 기존 갱신 이벤트 |
| `ChatAnalyticsCalculator.kt` | 전달받은 거래의 순수 필터/그룹/집계/문자열 결과 계산 |
| `ChatMessageObserver.kt` | 선택된 세션의 메시지 Flow 하나만 구독 |
| `ChatUiState.kt` | 화면 상태와 표시용 메시지/세션 모델 |
| `LocalChatQueryRouter.kt` | 단순 조회를 Gemini 없이 `DataQuery`로 변환 |
| `ChatRepositoryImpl.kt` | 메시지/세션 저장, rolling summary, local exchange 저장 |
| `GeminiRepositoryImpl.kt` | Gemini query analyzer/final answer/title/summary 호출 |
| `DataQueryParser.kt` | query/action JSON contract |
| `ChatCreditPolicy.kt` | 채팅 전송 1회 1크레딧 비용 산정 |

## 함께 볼 문서

- [../README.md](../README.md): 토큰 비용 경계와 로컬 조회 범위
- [../../budget-credit-monetization/README.md](../../budget-credit-monetization/README.md): 크레딧/광고 정책
- [../../finance-data/README.md](../../finance-data/README.md): 실제 DB 조회 위치

## 세션 메시지 관찰

`ChatViewModel`은 초기화 시 `ChatMessageObserver.observe(currentSessionId Flow)`를 한 번 수집한다. 세션 ID에 `distinctUntilChanged`와 `flatMapLatest`를 적용해 목록 갱신마다 새 구독을 만들지 않으며, 다른 방으로 이동하거나 선택이 없어지면 이전 구독을 취소한다. 방 전환 시 빈 목록을 먼저 내보내고, ViewModel도 결과의 세션 ID가 현재 선택과 같을 때만 반영한다.

기존 구현은 `loadMessagesForSession()`마다 별도 coroutine을 시작해 과거 방의 DB 변경이 현재 방 메시지를 덮어쓸 수 있었다. `ChatMessageObserverTest`는 전환 후 이전 방 갱신, 같은 ID 반복, 선택 삭제 및 초기 미선택을 확인한다.

## 계산과 변경 경계

`ChatQueryExecutor`가 기존 카드/통계 제외 및 월 시작일 정책을 적용한 거래를 조회해 `ChatAnalyticsCalculator`에 전달한다. 계산기는 DB나 화면 상태를 직접 읽지 않는다. 분석 경로의 coroutine 취소는 일반 오류 문자열로 바꾸지 않고 전송 취소·환불 경로에 전달한다.

`ChatActionExecutor`는 기존 13종 액션을 그대로 실행한다. ViewModel은 성공한 액션의 데이터 갱신 이벤트와 최종 답변 생성을 조정한다. DB 스키마, 크레딧 정책, API 호출 수와 프롬프트는 변경하지 않는다.
