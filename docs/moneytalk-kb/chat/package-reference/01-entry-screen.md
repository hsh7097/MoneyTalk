---
type: package-reference
title: Chat entry/screen
description: Chat 탭 진입, 채팅방 목록과 채팅방 내부 전환 흐름을 설명한다.
tags: [moneytalk, chat, entry]
resource: app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatScreen.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Chat entry/screen

## 진입

```text
MainActivity.MoneyTalkApp
-> NavGraph(Screen.Chat.route)
-> ChatScreen()
```

## 화면 상태

- 채팅 탭은 채팅방 목록과 특정 채팅방 내부 화면을 같은 `ChatScreen` 계층에서 전환한다.
- 채팅방 목록 UI는 `ChatRoomListView.kt`가 담당한다.
- 채팅방 내부 메시지/입력/가이드 질문은 `ChatScreen.kt`와 `ChatComponents.kt`가 담당한다.

## 코치마크

- Chat 화면 step은 `ChatCoachMark.kt`에 있다.
- 공통 overlay와 screen seen 저장은 [coachmark/README.md](../../coachmark/README.md)를 본다.
