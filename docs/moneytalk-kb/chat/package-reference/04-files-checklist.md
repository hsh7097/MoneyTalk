---
type: package-reference
title: Chat files checklist
description: Chat 탭 수정 전후 확인할 파일과 검증 질문을 정리한다.
tags: [moneytalk, chat, checklist]
resource: app/src/main/java/com/sanha/moneytalk/feature/chat/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Chat files checklist

## 수정 전 확인

| 작업 | 먼저 볼 파일 |
|---|---|
| 채팅 화면 UI | `ChatScreen.kt`, `ChatComponents.kt`, `ChatRoomListView.kt` |
| 단순 조회 비용 절감 | `LocalChatQueryRouter.kt`, `ChatViewModel.kt`, `LocalChatQueryRouterTest.kt` |
| Gemini prompt/model | `GeminiRepositoryImpl.kt`, `ChatPrompts.kt`, `string_prompt.xml`, `PremiumConfig.kt` |
| 크레딧/광고 | `ChatCreditPolicy.kt`, `AiCreditRepository.kt`, `RewardAdManager.kt` |
| DB query/action | `DataQueryParser.kt`, `ChatViewModel.executeQuery()`, `ChatViewModel.executeAction()` |

## 검증 질문

1. 단순 조회는 Gemini analyze/final/summary 호출 없이 저장되는가?
2. 상담/분석/수정 요청은 기존 Gemini/action 경로로 남는가?
3. 크레딧 예약 차감/환불/확정 경계가 유지되는가?
4. 채팅방 목록과 내부 화면 전환이 세션 state를 잃지 않는가?
5. 사용자 문구는 `strings.xml` 또는 `string_prompt.xml`에 있는가?
