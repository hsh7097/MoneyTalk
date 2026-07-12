---
type: package-reference
title: Transaction Edit rendering/action
description: TransactionEditScreen과 DetailContent의 UI 카드, picker, bottom action 연결을 설명한다.
tags: [moneytalk, transaction-edit, compose, action]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditDetailContent.kt
timestamp: 2026-07-12T00:00:00+09:00
status: draft
---

# Transaction Edit rendering/action

## 주요 Composable

| Composable | 역할 |
|---|---|
| `TransactionEditScreen` | state 수집, picker/dialog, save/delete result 처리 |
| `TransactionEditDetailContent` | 실제 거래 입력 화면 조합 |
| `TransactionEditTopBar` | 상단 닫기/제목 영역 |
| `TransactionHeroCard` | 금액/가게명 또는 수입원 핵심 입력 |
| `TransactionBasicInfoCard` | category/date/time/memo/type 기본 정보 |
| `TransactionAutomationCard` | 고정지출/통계 제외/일괄 적용 옵션 |
| `TransactionSameStoreRuleCard` | 같은 거래처 규칙 옵션 |
| `TransactionEditBottomActions` | 저장/삭제 하단 액션 |

## Dialog와 picker

- DatePicker/TimePicker는 `TransactionEditScreen.kt`에 있다.
- Category picker와 custom category 추가 dialog는 `TransactionEditViewModel` state와 연결된다.
- 공통 category UI는 `core/ui/component/CategoryAddDialog.kt`, `EmojiPickerCompose.kt`, `CategoryIcon.kt`를 함께 확인한다.

## 최대 글자 배율 계약

- 공통 `CategorySelectDialog`는 `fontScale < 1.5`에서 4열/한 줄 라벨, `fontScale >= 1.5`에서 3열/최대 두 줄 라벨을 사용한다. 이 picker는 Transaction Edit뿐 아니라 History filter와 Store Rule Settings에서도 공유한다.
- `DetailRowFrame`의 값은 최대 두 줄을 허용한다. 날짜와 시간이 최대 글자에서 말줄임표로 숨지 않고 `7월 12일 (일)`과 `11:28`처럼 줄바꿈되어 모두 보여야 한다.
- SM-F966N 닫힘 화면 1080x2520, Android 16, `font_scale=2.0`에서 거래 기본정보와 카테고리 picker를 재검증한다.
