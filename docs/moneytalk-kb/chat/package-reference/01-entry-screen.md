---
type: package-reference
title: Chat entry/screen
description: Chat 탭 진입, 채팅방 목록과 채팅방 내부 전환 흐름을 설명한다.
tags: [moneytalk, chat, entry]
resource: app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatScreen.kt
timestamp: 2026-09-07T00:00:00+09:00
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
- 채팅방 내부 메시지/입력/가이드 질문은 `ChatRoomView.kt`와 기능별 `Chat*.kt`가 담당한다.
- 채팅방 내부의 시스템 뒤로가기는 `ChatScreen`의 `BackHandler`에서 상단 뒤로가기와 같은 `ChatViewModel.exitChatRoom()`을 호출해 채팅방 목록으로 돌아간다. 목록에서는 이 handler가 비활성화되어 앱의 기존 홈 이동 동작을 따른다.

## 코치마크

- Chat 화면 step은 `ChatCoachMark.kt`에 있다.
- 공통 overlay와 screen seen 저장은 [coachmark/README.md](../../coachmark/README.md)를 본다.
