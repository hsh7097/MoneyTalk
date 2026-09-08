---
type: package-reference
title: Transaction Edit rendering/action
description: TransactionEditScreen과 DetailContent의 UI 카드, picker, bottom action 연결을 설명한다.
tags: [moneytalk, transaction-edit, compose, action]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditDetailContent.kt
timestamp: 2026-09-09T00:00:00+09:00
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

## 기능별 렌더링 파일 (2026-09-08)

1,215줄에 모여 있던 상세 UI는 기존 함수 시그니처와 본문을 유지한 채 다음 기능 파일로 나눈다. 외부 파일에서 호출되는 함수만 internal로 공개하고 카드 내부 helper는 private으로 유지한다.

| 파일 | 담당 함수/기능 |
|---|---|
| `TransactionEditDetailContent.kt` | 화면 카드 배치, callback 연결, 같은 거래처 규칙 표시 animation |
| `TransactionEditChrome.kt` | 상단 닫기/저장, 하단 저장/삭제, 시스템 바 색상 |
| `TransactionHeroCard.kt` | 금액/거래처/타입 핵심 입력, signed amount 변환 및 cursor mapping |
| `TransactionBasicInfoCard.kt` | 카테고리/날짜·시간/메모/이체 방향과 정보 row |
| `TransactionAutomationCard.kt` | 고정/통계 제외 옵션, 같은 거래처 적용 checkbox, 타입별 안내 |
| `TransactionSameStoreRuleCard.kt` | 거래처 키워드 입력과 가이드 |
| `TransactionOriginalSmsCard.kt` | 문자 원문 표시 |
| `TransactionEditComponents.kt` | 여러 카드가 사용하는 section frame/divider/색상 |

UI 상태, 데이터 저장, category/date picker, 알림 진입 처리에는 관여하지 않는다. 입력 remember, callback 순서, 자동화 옵션 LaunchedEffect, 최대 글자 배율의 2줄 값 정책과 시스템 바 처리는 그대로 보존한다.

## 금융 앱 화면 정리 (2026-09-08)

- 편집 카드의 현재 24dp 모서리와 20dp 안쪽 여백은 유지한다. 2026-09-09 최신 색상 복원 요청에 따라 표면·저장/커서/스위치는 원래 편집 팔레트를 따르고 거래 유형은 지출 `FriendlyMoneyColors.Coral`, 수입 `FriendlyMoneyColors.Mint`, 이체 `FriendlyMoneyColors.Sky`로 구분한다. 금액 폭 측정·입력·저장 동작은 유지하며 이번 색상 복원의 실행 결과는 [후속 통합 검증 기록](../../project-context/08-home-ledger-ux-validation-20260909.md)의 색상 복원 절을 따른다.
- 거래 유형은 중립 배경의 탭으로 표시하며 선택 상태와 `Role.Tab` 의미를 제공한다. 금액/거래처 입력, 지우기, 같은 거래처 적용 행은 48dp 이상 터치 영역을 확보한다.
- 저장 버튼의 너비를 삭제보다 크게 두고 삭제 글자만 위험 색상으로 구분한다. 기존 상단 저장, 하단 저장/삭제, 삭제 확인, 동일 거래처 적용, 원문, 금액의 부호·쉼표 cursor mapping은 유지한다.
- 화면 바깥 여백 20dp, 카드 사이 16dp로 맞춘다. 새 저장 정책이나 추가 필드는 도입하지 않는다.
- 큰 글자 QA에서 거래처 단일 행과 좁은 금액 칸의 가로 잘림을 확인해 거래처를 최대 3줄로 허용하고 금액을 아이콘 아래 전체 카드 너비로 분리했다. 금액은 현재 글자 배율에서 부호·쉼표·단위의 실제 폭을 재고, 너비를 넘는 경우에만 글자 크기를 줄인다. 편집 중 지우기 아이콘 공간은 평소에도 확보해 포커스 시 첫 자리가 밀리지 않게 한다. `TransactionHeroLayoutTest`는 320dp·글자 1.5배에서 포커스 전후와 10자리 입력의 실제 폭/원래 값 보존을 검사한다.
