---
type: log
title: Chat KB Change Log
description: MoneyTalk AI 채팅 KB의 상세 변경 이력을 기록한다.
tags: [moneytalk, kb, chat, changelog]
resource: docs/moneytalk-kb/chat/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Chat KB Change Log

## 2026-07-09 - 채팅 시스템 계약 KB화

- 기준: `docs/CHAT_SYSTEM.md`, `docs/AI_CONTEXT.md` 채팅 파트 재검토
- 변경 근거: 루트 문서를 그대로 이동하지 않고 Local Fast Path, 18 query, 13 action, ANALYTICS, 모델/프롬프트, Rolling Summary 경계를 KB 내부 문서로 흡수하기 위해 `05-system-contract.md`를 추가했다.
- 갱신한 문서: `README.md`, `05-system-contract.md`

## 2026-07-08 - 채팅 크레딧 정책 단순화

- 기준: `core/util/ChatCreditPolicy.kt`, `feature/chat/ui/ChatViewModel.kt`, `RewardAdManager`, `docs/moneytalk-kb/budget-credit-monetization/02-policy-and-plans.md`
- 변경 근거: 사용자가 이해하기 쉬운 수익화 정책을 위해 채팅 질문 유형별 차등 비용을 제거하고, 채팅 전송 1회 1크레딧으로 통일했다.
- 갱신한 문서: `README.md`, `05-system-contract.md`, `docs/moneytalk-kb/project-context/01-system-overview.md`, `docs/moneytalk-kb/project-context/02-threshold-registry.md`, `docs/moneytalk-kb/screen-requirements/01-screen-requirements-index.md`

## 2026-07-08 - Chat 화면 KB 보강

- 기준: `ChatScreen.kt`, `ChatComponents.kt`, `ChatRoomListView.kt`, `ChatViewModel.kt`, `ChatRepositoryImpl.kt`, `GeminiRepositoryImpl.kt`, `LocalChatQueryRouter.kt` 확인
- 변경 근거: 하단 탭 4개를 모두 화면별 KB로 탐색할 수 있게 Chat 탭도 UI/data/rendering/checklist 문서로 분리했다.
- 갱신한 문서: `00-structure-map.md`, `package-reference/**`, `README.md`, 루트 `../00-agent-routing.md`, `../01-structure-map.md`, `../00-change-index.md`

## 2026-07-08

- 기준: `core/util/LocalChatQueryRouter.kt`, `feature/chat/ui/ChatViewModel.kt`, `feature/chat/data/ChatRepository.kt`, `feature/chat/data/ChatRepositoryImpl.kt`, `app/src/test/java/com/sanha/moneytalk/core/util/LocalChatQueryRouterTest.kt`
- 변경 근거: 단순 조회의 Gemini query analyzer/final answer/Rolling Summary 비용을 줄이기 위해 안전한 정형 조회를 로컬 `DataQuery`로 직접 실행하는 1차 구현을 반영했다.
- 갱신한 문서: `README.md`, 루트 `../00-change-index.md`, `05-system-contract.md`, `docs/moneytalk-kb/project-context/01-system-overview.md`, `docs/moneytalk-kb/project-context/02-threshold-registry.md`, `docs/moneytalk-kb/budget-credit-monetization/02-policy-and-plans.md`, `docs/moneytalk-kb/release-history/01-release-timeline.md`
- 다음 검증: 실제 채팅에서 `이번 달 총 지출 얼마야`, `이번 달 식비 얼마야`, `최근 지출 5개 보여줘`, `이번 달 예산 현황 보여줘`가 Gemini 사용량 없이 응답되는지 usage dashboard 또는 fake repository 계측으로 확인한다.

- 기준: `feature/chat/ui/ChatViewModel.kt`, `feature/chat/data/GeminiRepositoryImpl.kt`, `feature/chat/data/ChatRepositoryImpl.kt`, `core/util/DataQueryParser.kt`, `core/util/ChatCreditPolicy.kt`, `core/util/ChatContextBuilder.kt`, `05-system-contract.md`, `docs/moneytalk-kb/budget-credit-monetization/02-policy-and-plans.md`
- 변경 근거: 단순 조회도 Gemini query analyzer와 final answer 단계에서 토큰 비용이 발생한다는 구조를 재확인했고, 로컬 정형 조회 우회 설계를 KB에 남겨 후속 개발의 기준점으로 삼는다.
- 갱신한 문서: `README.md`, 루트 `../README.md`, `../00-agent-routing.md`, `../01-structure-map.md`, `../00-change-index.md`
- 다음 검증: `LocalChatQueryRouter` 구현 시 단순 조회에서 `analyzeQueryNeeds()`와 `generateFinalAnswerWithContext()`가 호출되지 않는지 단위 테스트 또는 fake repository로 확인한다.
