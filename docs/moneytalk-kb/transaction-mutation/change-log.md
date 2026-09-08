---
type: changelog
title: Transaction Mutation KB 변경 로그
description: Transaction Mutation KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, transaction, changelog]
resource: docs/moneytalk-kb/transaction-mutation/
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# Transaction Mutation KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-09-08 | 간단 메뉴를 상용 앱 수준으로 정리해 달라는 사용자 피드백 | 가로 텍스트 버튼을 아이콘 포함 세로 작업 행으로 정리 | `01-feature-flow.md`, 화면 요구사항, UI 선언 맵 | 최대 320dp 중앙 메뉴, 낮은 강조의 헤더/구분선, 행 전체 터치, 삭제 색상 구분. 수정·삭제·닫기 계약은 유지. |
| 2026-09-08 | 중앙 모달 정보 과다에 대한 사용자 피드백 | 거래명·닫기·수정/삭제만 있는 작업 메뉴로 축소 | `01-feature-flow.md`, 영향 화면 rendering 문서 | 수정 입력/저장은 기존 편집 화면으로 연결. 모달 내부 카테고리/고정/통계 제외와 저장 UI를 제거하고 삭제 확인·추적은 유지. |
| 2026-09-08 | 롱클릭 편집을 중앙 모달로 바꿔 달라는 사용자 요청 | 공용 UI 진입점을 `TransactionQuickActionDialog`로 변경 | `01-feature-flow.md`, 영향 화면 rendering 문서 | 닫기 버튼·뒤로가기·바깥 터치의 미저장 취소와 저장 중 닫기/중복 동작 차단을 명시. 저장 서비스와 삭제 추적은 유지. |
| 2026-09-08 | `feature/transactionactions/**`와 4개 화면 호출부 확인 | 롱클릭 단건 변경의 UI/서비스/Room/refresh 흐름 추가 | `01-feature-flow.md` | 유형·ID, 최신 행의 바뀐 필드만 갱신, 수입 반복일, 카테고리 검증, 삭제 추적·취소 경계. 규칙/학습/일괄 적용과 구분. |
| 2026-07-08 | `TransactionEditViewModel`, `HistoryDialogs`, `ExpenseRepository`, `IncomeRepository`, `DataQueryParser` 확인 | Transaction Mutation 기능 KB 생성 | `README.md`, `01-feature-flow.md` | 아이템 추가/수정/삭제와 일괄 적용, 채팅 action 변경 경계를 기능 KB로 분리. |
