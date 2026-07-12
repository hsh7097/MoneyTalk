---
type: package-reference
title: Chat rendering/action
description: ChatScreen, ChatComponents, ChatRoomListView, RewardAdDialog UI와 사용자 액션 연결을 설명한다.
tags: [moneytalk, chat, compose, action]
resource: app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatScreen.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Chat rendering/action

## 주요 Composable

| Composable | 역할 |
|---|---|
| `ChatScreen` | Chat 탭 전체 entry, 목록/방 내부 전환, dialog 배치 |
| `ChatRoomView` | 메시지 목록, 입력창, 전송/retry 처리 |
| `ChatRoomListView` | 세션 목록, 세션 선택/삭제/제목 표시 |
| `GuideQuestionsOverlay` | 추천/가이드 질문 overlay |
| `ChatBubble` | 사용자/AI 메시지 bubble |
| `TypingIndicator` | 응답 생성 중 표시 |
| `RewardAdDialog` | AI 크레딧 부족 시 보상형 광고 안내 |
| AI 오류/Retry 상태 | App Check, service disabled, 모델 호출 실패를 메시지와 재시도로 안내 |

## 액션 연결

- 메시지 전송은 `ChatViewModel.processSendMessage()` 경로로 들어간다.
- retry는 마지막 실패 질문과 `RetryButton` action을 확인한다.
- 세션 삭제/선택은 `ChatRoomListView`와 `ChatRepository`를 같이 본다.
- reward ad dialog는 [budget-credit-monetization/README.md](../../budget-credit-monetization/README.md)를 같이 본다.
- 사용자 API key 입력 dialog는 제거됐다. AI 사용 가능 여부는 `GeminiConfigProvider`와 Firebase AI Logic 호출 결과가 결정한다.
