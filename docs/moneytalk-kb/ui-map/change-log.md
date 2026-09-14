---
type: change-log
title: UI Map KB Change Log
description: UI Map KB 변경 이력
tags: [moneytalk, kb, ui, changelog]
resource: docs/moneytalk-kb/ui-map/
timestamp: 2026-09-08T00:00:00+09:00
status: draft
---

# Change Log

## 2026-09-14

- 홈·내역·카테고리 상세의 공용 월 Pager snapshot helper `rememberMonthPagerPageCount`를 선언 맵에 반영했다.
- 달력의 다음 날 복귀 처리를 담당하는 `rememberCalendarToday` 선언과 오늘 날짜 캐시 갱신을 History 항목에 반영했다.

## 2026-09-08

- 중앙 작업 메뉴를 최대 320dp·모서리 20dp·무톤 surface와 세로 아이콘 행으로 정리했다. `QuickTransactionMenuItem`을 선언 맵에 추가하고 행 전체 터치/버튼 의미와 삭제 색상 구분을 반영했다. 메뉴 정보와 수정·삭제 연결은 유지한다.

- 중앙 모달을 거래명·닫기·수정/삭제 메뉴로 축소하고 `QuickTransactionToggle`을 현재 선언 맵에서 제거했다. 수정은 기존 상세 편집을 열며 삭제 확인은 유지한다.

- 거래 롱클릭 진입점과 파일명을 `TransactionQuickActionDialog`로 갱신했다. 4개 거래 목록은 중앙 모달을 공유하며 다른 필터/예상 내역 시트는 유지한다.

- 로컬 소비 브리핑 3개, 고정 지출 예상/표시 helper 5개, 간단 수정 시트 3개의 Composable 선언을 실제 `rg` 결과로 추가했다. Home/History/CategoryDetail/TransactionList의 롱클릭 연결과 기존 `AiInsightCard`의 현재 미호출 상태를 반영했다.
- 제품 기능 구현의 의미와 검증 범위는 `project-context/04-product-improvements-20260908.md`와 영향 화면 KB를 따른다.

- MainActivity의 App/동기화 dialog, Home 페이지/섹션, History 필터, Chat 기능 UI, Settings 메뉴/화면, SMS 관리, 거래처 규칙 편집, 거래 편집 카드 분리를 현재 소스 경로로 반영했다.
- 45개 기능 파일의 82개 Composable 선언 위치와 관련 KB를 명시했다. 실제 파일/함수와 상대 링크를 대조했다.
- 화면/기능 전체의 상태·서비스·mapper 책임 감사 문서로 연결했다.

## 2026-07-12

- `MoneyTalkApp`의 월별 sync 보상형 광고/전역 다이얼로그 책임과 app-shell·monetization KB 라우팅을 앱 진입 항목에 추가했다.

## 2026-07-09

- 루트 `COMPOSABLE_MAP.md`를 그대로 옮기지 않고 `01-screen-composable-index.md`로 화면별 Composable 라우팅을 재작성했다.
- 하단 탭 4개, 보조 화면, 공통 컴포넌트, 코치마크 확인 경계를 KB 링크 중심으로 정리했다.
