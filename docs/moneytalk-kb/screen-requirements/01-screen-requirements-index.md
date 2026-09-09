---
type: reference
title: Screen Requirements Index
description: 화면별 핵심 요구사항과 담당 KB를 연결한다.
tags: [moneytalk, screen, requirements, ui]
resource: app/src/main/java/com/sanha/moneytalk/feature/
timestamp: 2026-09-09T00:00:00+09:00
status: draft
---

# Screen Requirements Index

> 기준: 흡수된 화면 요구사항 원문을 KB용으로 재구성

## 인트로/권한

| 화면 | 핵심 요구 | 담당 KB |
|---|---|---|
| Splash | 빠른 진입, 강제 업데이트/설정 cache 처리 | [onboarding](../onboarding/README.md) |
| Onboarding | 앱 가치 설명, SMS/알림 접근 안내 | [onboarding](../onboarding/README.md) |
| Permission | SMS 권한, 알림 접근 권한, 거부/재시도 처리 | [onboarding](../onboarding/README.md), [notification-ingestion](../notification-ingestion/README.md) |

## 하단 탭 4개

| 화면 | 핵심 요구 | 담당 KB |
|---|---|---|
| Home | 기존 중앙 월 이동·그라데이션 월 지출/수입, 바로 아래 누적 차트·날짜별 금액 조회와 수집 안내, 예산 표시를 뺀 최근 소비 비교, 카테고리·오늘 전체 내역/합계/지출 건수·최하단 고정 예상, SMS/과거 월 CTA와 FAB | [home](../home/README.md), [home surface map](../home/package-reference/05-surface-map.md) |
| History | 목록/달력 view mode, 지출·수입·이체 필터, 헤더, 재편집 가능한 필터/정렬, 지출·수입 전체 기간 검색, 거래 롱클릭 단건 수정/삭제, 수동 추가, 외부 카테고리 필터 | [history](../history/README.md), [filtering](../filtering/README.md) |
| Chat | 세션 목록, 채팅방, 가이드 질문, 입력, Local Fast Path, Gemini 3-step, query/action/ANALYTICS, Rolling Summary, 크레딧/광고 | [chat](../chat/README.md), [chat contract](../chat/05-system-contract.md) |
| Settings | 화면 설정, 월 시작일/예산, AI 크레딧, 데이터 관리, 카테고리/거래처/SMS 설정, 백업/복원, 앱 정보 | [settings](../settings/README.md), [settings menu map](../settings/package-reference/05-menu-map.md) |

## 상세/편집 화면

| 화면 | 핵심 요구 | 담당 KB |
|---|---|---|
| Category Detail | 홈 카테고리 행에서 진입, 월간 추이, 해당 카테고리 목록, 클릭 상세 편집·롱클릭 단건 수정/삭제 | [category-detail](../category-detail/README.md) |
| Transaction Edit | 신규/기존 지출/수입 편집, 금액/가게/카테고리/메모/고정/통계 제외, 동일 거래처 적용. 기존 false 규칙도 보존·해제 가능하며 전체 적용 상태의 스위치 OFF는 false 값 적용 | [transaction-edit](../transaction-edit/README.md), [transaction-mutation](../transaction-mutation/README.md) |
| Transaction Detail List | 날짜별 거래 목록, 그룹 헤더, 지출/수입 카드, 상세 편집·롱클릭 단건 수정/삭제 | [transaction-list](../transaction-list/README.md) |
| SMS Settings | 제외 키워드, 차단 발신자, 신규 파싱 입력 제외 | [sms-settings](../sms-settings/README.md), [filtering](../filtering/README.md) |
| AI Credit | 잔액, 최근 원장, 광고 충전 진입, feature gate | [ai-credit-screen](../ai-credit-screen/README.md), [budget-credit-monetization](../budget-credit-monetization/README.md) |
| Weekly Evidence | 브리핑과 같은 두 7일·기준 시각·시간대·고정 제외, 전체/category 범위, 초기 기간 탭, 날짜별 거래·합계와 기존 편집·롱클릭 | [home](../home/package-reference/03-rendering-action.md) |
| Category Review | 설정에서 전체 기간 미분류 직접 확인, 같은 노출 기준의 건수, 기존 거래 편집과 동일 거래처 적용, 로딩/실패/빈 상태와 복귀 갱신. 자동 분류는 별도 실행 | [settings](../settings/package-reference/03-rendering-action.md), [transaction-edit](../transaction-edit/README.md) |
| Category Settings | custom category 추가/수정/삭제/재정렬 | [category-settings](../category-settings/README.md), [category-classification](../category-classification/README.md) |
| Store Rule Settings | 거래처 규칙 추가/편집/삭제, 카테고리/고정/통계 제외 소급 적용 | [store-rule-settings](../store-rule-settings/README.md) |

## 글로벌 요구

| 영역 | 핵심 요구 | 담당 KB |
|---|---|---|
| 디자인 시스템 | 기존 팔레트의 Material 3 배경/표면, 의미 색상과 금액 위계, 공통 카드/탭/설정 row, 큰 글자 대응 | [ui-map](../ui-map/README.md), [금융 UI 기준](../project-context/05-finance-ui-design-system-20260908.md), [사용성 감사](../project-context/06-finance-ux-plan-20260908.md), [통합 검증](../project-context/07-finance-ui-validation-20260908.md) |
| 데이터 새로고침 | 거래/카테고리/설정 변경 후 화면 refresh event | [data-refresh](../data-refresh/README.md) |
| 전역 스낵바 | 장기 작업/실패/성공 피드백 | [app-shell](../app-shell/README.md) |
| SMS 즉시 저장 + 앱 노티 표시 | 실시간 저장 후 거래 알림 표시/숨김, 본문 클릭 편집 진입, 지출은 통계 제외/삭제·수입은 삭제 액션으로 단건 처리 | [notification-display](../notification-display/README.md), [filtering](../filtering/README.md) |
| SMS 파싱 파이프라인 | batch/instant SMS, regex/vector/LLM, coverage | [sms-parsing](../sms-parsing/README.md), [sms-pipeline](../sms-pipeline/README.md) |
| 카테고리 분류 | 4-tier 자동 분류, 사용자 수정 학습 | [category-classification](../category-classification/README.md) |
| Room DB | schema/migration, finance data repository | [finance-data](../finance-data/README.md) |

## 로컬 기능의 표시·저장 계약

- Home은 사용자 요청에 따라 600996a의 기존 UI를 복원한다. `HomeTheme`/`HomeColors`와 홈 전용 거래 카드·CTA의 기존 구성을 유지한다. 최신 색상 요청에 따라 공통 테마와 홈 색상은 `9b4b4bf` 기준으로 복원하고 Typography와 다른 화면의 배치는 유지한다. 중앙 월 이동 아래 초록·노랑 그라데이션에 월 전체 지출과 수입 배지를 표시한다. 금액은 원래 32sp 흰색 중앙 정렬이고 통화 단위도 같은 크기다. 긴 금액은 1px 여유 폭에서 비례 축소 후 재측정하며 필요하면 0.5sp씩 더 낮춰 한 줄 표시한다.
- 누적 차트는 최신 요청에 따라 월 지출 바로 아래에 둔다. `SpendingTrendSection(showCard = false)`로 카드 없는 기존 배치와 큰 누적 금액, 영역 채움, 전월·3/6개월 평균·예산 토글을 유지한다. 홈은 `scaleToVisibleLines = true`로 오늘까지 주 곡선과 켜진 비교선에 Y축을 맞추고 다른 화면은 기본 false의 전체 곡선 기준을 유지한다. 다른 화면은 기본 `showCard = true`를 사용한다. 별도 두 곡선 비교 카드·분석 접기는 제거한다.
- 차트 비교는 당월 오늘까지 같은 경과일, 과거 월 각 월 전체 기준이다. 미래 날짜 거래는 당월 차트 금액·주 곡선에서 제외하지만 Hero·예산·카테고리는 월 전체 기록을 사용하므로 값이 다를 수 있다. 현재/전월 수집 미완료나 배열 부재는 많음/적음 평가 대신 안내하고 미완료 전월 선은 숨긴다. 실제 전월 0원은 금액 차이로 비교하며 앱의 수집 완료를 실제 금융 기록의 완전성으로 단정하지 않는다.
- 차트 날짜 선택은 실제 `inspectionPeriodStart`를 전달하는 활성 Home 페이지에서만 사용한다. index 1은 실제 회계 시작일이며 `M월 d일 · 기간 N일차 누적`을 표시한다. 시작 0원인 index 0은 날짜로 선택하지 않는다. CategoryDetail의 기본 null과 기존 Canvas 오버로드는 유지한다.
- 선택 금액은 켜진 전월·3/6개월 평균·예산선과 주 곡선의 원본 `Long`이다. 꺼진 선·애니메이션용 값은 생략하고 실제 0원은 보존한다. 당월 오늘 이후 금액은 생략하고 오늘까지만 표시한다는 안내를 두되, 같은 경과일의 전월·평균·예산 지점이 있으면 조회한다. 짧은 전월은 실제 마지막 지점 뒤로 연장하지 않는다. 이 날짜별 조회는 기존 요약 비교의 짧은 전월 마지막 값 제한과 구분한다.
- 차트를 탭하거나 길게 누른 뒤 좌우로 움직여 선택선·점을 갱신한다. 롱프레스 전에는 부모 세로 스크롤과 가로 월 pager를 보존한다. 금액 패널은 차트 아래·범례 위에 표시해 차트 위치를 유지하고, 지출 금액 빨강과 곡선별 점 색상을 구분한다. 손을 떼어도 선택을 유지하며 닫기·월/회계 시작일 변경·페이지 비활성화로 초기화한다. 조회만으로 DB 저장·새 AI 호출을 만들지 않는다.
- ‘내 소비 한눈에’는 차트 다음에 최근 소비 비교가 있을 때만 표시하며 예산·잔여 일수·하루 참고액은 표시하지 않는다. 기록 기준과 부분 수집/권한 안내, 고정 지출을 뺀 최근/이전 7일 금액을 테두리 카드 안에서 최근 금액은 지출색, 이전 금액은 보조색으로 표시한다. 실제 날짜와 과거 월의 마지막 날 기준 안내를 함께 표시한다. 금액은 해당 기간 전체 근거, 증가 카테고리는 같은 두 기간의 category 근거로 이동한다. 주간 비교가 없어 카드가 숨겨져도 CTA·차트의 수집 안내는 독립적으로 유지한다. 예산 계산·설정·차트의 양수 예산 토글은 별도 기능으로 남는다.
- 카테고리 다음에는 현재 회계월의 오늘 지출/수입 전체를 최신순으로 표시한다. 오늘 지출 합계·지출 건수 헤더와 클릭/롱클릭을 복원하며 이 건수는 수입을 포함한 목록 총 건수가 아니다. 3건 제한·전체/접기 상태는 사용하지 않는다. 카테고리 행은 원래 퍼센트 표시를 사용하고 분모는 예산이 있으면 카테고리 예산, 없으면 전체 표시 지출이다. 별도 ‘예산 %’/‘비중 %’ 접두어는 없다.
- 홈 목록은 마지막까지 스크롤했을 때 마지막 거래의 금액과 터치 영역이 ‘맨 위로’ FAB에 가려지지 않도록 아래 80dp content padding을 확보한다. 위·좌우 여백과 거래 데이터는 바꾸지 않는다.
- 고정 지출 예상은 실제 일정이나 결제 완료가 아니다. 후보가 없으면 카드를 숨기고, 미래 거래 삽입/예산 차감/새 알림을 만들지 않는다. 근거 버튼은 실제 거래로 이동한다.
- 거래 롱클릭은 화면 중앙의 작은 작업 메뉴로 표시한다. 거래명·닫기 X 아래 구분선과 아이콘을 곁들인 세로형 `수정`·`삭제` 행을 두고, 행 전체를 누를 수 있게 한다. 삭제만 위험 색상으로 구분하며 수정은 유형과 ID를 보존해 기존 편집 화면을 연다. 삭제는 별도 확인에서 거래명·금액을 보여준다. 메뉴 닫기의 데이터 불변, 수입·지출 ID 구분, 삭제 중 닫기와 중복 동작 차단, 삭제 원문 재수집 방지를 확인한다.
- 새 브리핑/예상 계산은 AI 요청·광고·크레딧 차감을 만들지 않는다. 사용성 효과와 운영 비용/광고 지급의 검증 범위는 [제품 개선 검토](../project-context/04-product-improvements-20260908.md)를 본다.
- 내역의 필터 진입은 활성 필터가 있어도 유지한다. 상태 문구를 눌러 초기화하지 않으며 별도 X만 초기화한다. 시트에서 닫기는 임시 변경을 버리고 적용은 기존 조건과 선택 결과를 전달한다.
- 공용 거래 카드는 거래명·금액을 우선 표시하고 일반 지출은 빨강, 일반 수입은 라이트 파랑·다크 초록과 부호를 사용한다. 통계 제외 금액은 보조색으로 표시한다. 큰 글자·좁은 화면·긴 금액은 세로로 배치하고 고정/통계 제외는 별도 상태줄로 보존한다. 홈 전용 카드는 원래 지출 색상·카테고리 칩과 메타데이터 줄바꿈을 사용하면서 금액 세로 배치와 수정/삭제 계약을 유지한다. 달력은 글자에 맞게 셀 높이를 늘리고 세로 스크롤을 허용한다.
- 하단 탭은 표준 아이콘과 label로 구성하며 기존 route/재클릭/키보드 표시 정책을 보존한다. 카테고리 관리의 유형 선택은 공통 세그먼트 탭을 사용한다. 채팅의 질문·서비스 상태·답변 한계, 설정의 권한 안내와 삭제 확인을 UI 정돈 과정에서 숨기지 않는다.

## 변경 시 체크

1. 화면 요구사항을 수정하면 담당 화면 KB의 `package-reference/**`도 같이 업데이트했는가?
2. 화면 진입점, intent extra, Activity open 계약이 실제 코드와 맞는가?
3. 필터/통계 제외/카드 숨김 정책이 화면별 합계와 노티 표시까지 일관되게 적용되는가?
4. Composable 이름이나 계층이 바뀌면 [../ui-map/01-screen-composable-index.md](../ui-map/01-screen-composable-index.md)를 같이 갱신했는가?

## 개발 가능성 감사

- 전체 화면 KB가 실제 수정 진입에 충분한지 확인하려면 [02-screen-development-audit-plan.md](02-screen-development-audit-plan.md)를 먼저 본다.
- 현재 화면별 판정은 [03-screen-development-readiness-audit.md](03-screen-development-readiness-audit.md)에 둔다.

- 다가올 고정 지출은 현재 회계월의 오늘 거래 다음, 홈 최하단에 표시한다. 실제 소비 확인을 먼저 두며 후보 없음 숨김·근거 거래 진입·80dp 하단 여백은 유지한다.

- 최신 색상은 `9b4b4bf`의 팔레트를 따른다. 공통 수입은 라이트 `#137FEC`·다크 `#3AC977`, 지출은 공통 `#EF4444`다. 홈 그라데이션의 큰 금액은 흰색, 수입은 흰색 80%, 점은 흰색 50%다. 통계 제외 금액은 보조색과 태그로 구분하고 고정 태그는 Coral을 사용한다. 편집은 지출 `FriendlyMoneyColors.Coral`, 수입 `FriendlyMoneyColors.Mint`, 이체 `FriendlyMoneyColors.Sky`를 사용한다. 기능·배치·차트 비교선은 유지한다. 이번 색상 복원의 실행 결과는 [후속 통합 검증 기록](../project-context/08-home-ledger-ux-validation-20260909.md)의 색상 복원 절을 따른다.

- 가계부의 제목·월 요약은 내역 스크롤에 따라 접히고 목록·달력·필터는 한 줄에 남는다. 좁은 화면/큰 글자는 도구 행을 가로로 스크롤할 수 있으며 터치 영역을 줄이지 않는다. 활성 필터의 짧은 조건명과 전체 접근성 설명, 별도 초기화 X를 유지한다. 월 변경·검색 전환·탭 재클릭·맨 위로 이동하면 요약도 복원한다. 검색 중에는 목록을 표시하고 종료하면 기존 보기 모드로 돌아간다.
