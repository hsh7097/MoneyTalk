---
type: file-inventory
title: Transaction Edit 파일 인벤토리
description: 거래 추가/수정 도메인 파일의 역할, 확인 시점, 함께 볼 파일을 정리한다.
tags: [moneytalk, transaction-edit, file-inventory]
resource: app/src/main/java/com/sanha/moneytalk/feature/transactionedit/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Transaction Edit 파일 인벤토리

| 파일 | 역할 | 언제 보는가 | 분류 | 함께 볼 파일 |
|---|---|---|---|---|
| `TransactionEditActivity.kt` | 거래 수정/추가 Activity entry | intent/진입 문제 | 핵심 | `TransactionEditScreen.kt` |
| `TransactionEditScreen.kt` | state 수집과 picker/dialog 연결 | 화면 흐름/action | 핵심 | `TransactionEditViewModel.kt` |
| `TransactionEditDetailContent.kt` | 상세 입력 UI 대부분 | UI/폼/하단 버튼 | 핵심 | `TransactionEditUiState` |
| `TransactionEditDateTimeMapper.kt` | 날짜/시간 변환 | date/time 버그 | 보조 | `DateUtils.kt` |
| `TransactionEditViewModel.kt` | 거래 로딩/저장/삭제/일괄 적용 | 아이템 변경 로직 | 핵심 | `ExpenseRepository.kt`, `IncomeRepository.kt` |
| `coachmark/TransactionEditCoachMark.kt` | 거래 수정 화면 코치마크 | 온보딩 target 변경 | 핵심 후보 | `coachmark/README.md` |
| `model/TransactionType.kt` | 거래 타입 model | 지출/수입 분기 | 핵심 후보 | `TransactionEditViewModel.kt` |
