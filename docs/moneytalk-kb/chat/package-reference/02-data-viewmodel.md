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
   -> match: executeQuery() -> template response -> saveLocalExchange()
   -> no match: sendMessageAndBuildContext()
-> GeminiRepository.analyzeQueryNeeds()
-> executeQuery()/executeAction()/executeAnalytics()
-> GeminiRepository.generateFinalAnswerWithContext()
-> saveAiResponseAndUpdateSummary()
```

## 주요 책임

| 파일 | 책임 |
|---|---|
| `ChatViewModel.kt` | orchestration, query/action/analytics 실행, UI state |
| `LocalChatQueryRouter.kt` | 단순 조회를 Gemini 없이 `DataQuery`로 변환 |
| `ChatRepositoryImpl.kt` | 메시지/세션 저장, rolling summary, local exchange 저장 |
| `GeminiRepositoryImpl.kt` | Gemini query analyzer/final answer/title/summary 호출 |
| `DataQueryParser.kt` | query/action JSON contract |
| `ChatCreditPolicy.kt` | 사용자 질문별 크레딧 비용 산정 |

## 함께 볼 문서

- [../README.md](../README.md): 토큰 비용 경계와 로컬 조회 범위
- [../../budget-credit-monetization/README.md](../../budget-credit-monetization/README.md): 크레딧/광고 정책
- [../../finance-data/README.md](../../finance-data/README.md): 실제 DB 조회 위치
