---
type: structure-map
title: Chat 구조 지도
description: Chat 탭의 UI, ViewModel, Repository, Gemini, 로컬 조회 라우터 파일 역할을 정리한다.
tags: [moneytalk, chat, structure-map]
resource: app/src/main/java/com/sanha/moneytalk/feature/chat/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Chat 구조 지도

## 패키지 구조

```text
feature/chat/
├── ui/
│   ├── ChatScreen.kt
│   ├── ChatRoomView.kt
│   ├── ChatGuideQuestions.kt
│   ├── ChatMessageBubble.kt
│   ├── ChatTypingIndicator.kt
│   ├── ChatRetryButton.kt
│   ├── ChatRewardAdDialog.kt
│   ├── ChatServiceUnavailableDialog.kt
│   ├── ChatUiState.kt
│   ├── ChatRoomListView.kt
│   ├── ChatViewModel.kt
│   └── coachmark/ChatCoachMark.kt
└── data/
    ├── ChatQueryExecutor.kt
    ├── ChatActionExecutor.kt
    ├── ChatAnalyticsCalculator.kt
    ├── ChatMessageObserver.kt
    ├── ChatPrompts.kt
    ├── ChatRepository.kt
    ├── ChatRepositoryImpl.kt
    ├── GeminiRepository.kt
    └── GeminiRepositoryImpl.kt
core/util/
├── LocalChatQueryRouter.kt
├── DataQueryParser.kt
├── ChatCreditPolicy.kt
└── ChatContextBuilder.kt
```

## 핵심 파일

| 파일 | 분류 | 역할 | 함께 볼 파일 |
|---|---|---|---|
| `ChatScreen.kt` | entry/rendering/action | Chat 탭 entry, 채팅방 목록/방 내부 전환, 화면 dialog | `ChatViewModel.kt`, `ChatRoomView.kt` |
| `ChatRoomView.kt` | rendering/action | 메시지 목록과 입력창, 가이드/광고 기능 조합 | `ChatUiState.kt` |
| `ChatRoomListView.kt` | rendering/action | 채팅 세션 목록, 세션 선택/삭제/제목 UI | `ChatRepositoryImpl.kt` |
| `ChatGuideQuestions.kt`, `ChatMessageBubble.kt`, `ChatTypingIndicator.kt`, `ChatRetryButton.kt` | rendering | 가이드/말풍선/생성 중/재시도를 기능별 파일에서 렌더링 | `ChatRoomView.kt` |
| `ChatRewardAdDialog.kt`, `ChatServiceUnavailableDialog.kt` | rendering/action | 광고 안내와 서비스 상태 dialog | `ChatRoomView.kt`, `ChatScreen.kt` |
| `ChatViewModel.kt` | data/action | UI state, session/credit gate, local route, Gemini 3-step orchestration | `ChatUiState.kt`, `LocalChatQueryRouter.kt` |
| `ChatQueryExecutor.kt` | data | 로컬/AI 조회 공통 실행, 날짜·카드·통계 필터와 기본 결과 | `ChatAnalyticsCalculator.kt`, `DataQueryParser.kt` |
| `ChatActionExecutor.kt` | data/action | 거래/카테고리/키워드/예산 수정 | `DataQueryParser.kt` |
| `ChatAnalyticsCalculator.kt` | calculation | DB/UI 없이 거래를 필터·그룹·집계 | `ChatQueryExecutor.kt` |
| `ChatMessageObserver.kt` | data | 세션 ID 변경 시 이전 구독 취소 및 메시지 전환 | `ChatDao.kt`, `ChatViewModel.kt` |
| `ChatRepositoryImpl.kt` | data | 세션/메시지 저장, rolling summary, local exchange 저장 | `ChatDao.kt` |
| `GeminiRepositoryImpl.kt` | AI/API | Firebase AI Logic 기반 query analyzer, final answer, title/summary 호출 | `FirebaseAiModelFactory.kt`, `ChatPrompts.kt`, `PremiumConfig.kt` |
| `ChatPrompts.kt` | prompt | XML prompt resource 접근 | `string_prompt.xml` |
| `coachmark/ChatCoachMark.kt` | onboarding | Chat 화면 코치마크 step | `coachmark/README.md` |

## AI 참조 순서

| 작업 | 참조 순서 |
|---|---|
| 채팅 UI 변경 | `chat/README.md` -> `chat/package-reference/03-rendering-action.md` -> `ChatScreen.kt` |
| 채팅방 목록 변경 | `chat/00-structure-map.md` -> `ChatRoomListView.kt` -> `ChatRepositoryImpl.kt` |
| 단순 조회/토큰 비용 | `chat/README.md` -> `LocalChatQueryRouter.kt` -> `ChatViewModel.processLocalSimpleLookup()` |
| Gemini prompt/model | `chat/README.md` -> `GeminiRepositoryImpl.kt` -> `ChatPrompts.kt` -> `string_prompt.xml` |
| 크레딧/광고 | `chat/package-reference/02-data-viewmodel.md` -> `budget-credit-monetization/README.md` |
