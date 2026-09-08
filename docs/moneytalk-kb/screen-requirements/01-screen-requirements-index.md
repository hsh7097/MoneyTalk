---
type: reference
title: Screen Requirements Index
description: 화면별 핵심 요구사항과 담당 KB를 연결한다.
tags: [moneytalk, screen, requirements, ui]
resource: app/src/main/java/com/sanha/moneytalk/feature/
timestamp: 2026-09-08T00:00:00+09:00
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
| Home | 월 네비게이션/현황, SMS/과거 월 CTA, 로컬 예산·주간 브리핑, 근거 있는 고정 지출 예상, 누적 차트, 카테고리 지출, 오늘 거래 클릭·롱클릭, FAB | [home](../home/README.md), [home surface map](../home/package-reference/05-surface-map.md) |
| History | 목록/달력/수입 view mode, 헤더, 필터/정렬, 지출·수입 전체 기간 검색, 거래 롱클릭 단건 수정/삭제, 수동 추가, 외부 카테고리 필터 | [history](../history/README.md), [filtering](../filtering/README.md) |
| Chat | 세션 목록, 채팅방, 가이드 질문, 입력, Local Fast Path, Gemini 3-step, query/action/ANALYTICS, Rolling Summary, 크레딧/광고 | [chat](../chat/README.md), [chat contract](../chat/05-system-contract.md) |
| Settings | 화면 설정, 월 시작일/예산, AI 크레딧, 데이터 관리, 카테고리/거래처/SMS 설정, 백업/복원, 앱 정보 | [settings](../settings/README.md), [settings menu map](../settings/package-reference/05-menu-map.md) |

## 상세/편집 화면

| 화면 | 핵심 요구 | 담당 KB |
|---|---|---|
| Category Detail | 홈/브리핑에서 진입, 월간 추이, 해당 카테고리 목록, 클릭 상세 편집·롱클릭 단건 수정/삭제 | [category-detail](../category-detail/README.md) |
| Transaction Edit | 신규/기존 지출/수입 편집, 금액/가게/카테고리/메모/고정/통계 제외, 동일 거래처 적용 | [transaction-edit](../transaction-edit/README.md), [transaction-mutation](../transaction-mutation/README.md) |
| Transaction Detail List | 날짜별 거래 목록, 그룹 헤더, 지출/수입 카드, 상세 편집·롱클릭 단건 수정/삭제 | [transaction-list](../transaction-list/README.md) |
| SMS Settings | 제외 키워드, 차단 발신자, 신규 파싱 입력 제외 | [sms-settings](../sms-settings/README.md), [filtering](../filtering/README.md) |
| AI Credit | 잔액, 최근 원장, 광고 충전 진입, feature gate | [ai-credit-screen](../ai-credit-screen/README.md), [budget-credit-monetization](../budget-credit-monetization/README.md) |
| Category Settings | custom category 추가/수정/삭제/재정렬 | [category-settings](../category-settings/README.md), [category-classification](../category-classification/README.md) |
| Store Rule Settings | 거래처 규칙 추가/편집/삭제, 카테고리/고정/통계 제외 소급 적용 | [store-rule-settings](../store-rule-settings/README.md) |

## 글로벌 요구

| 영역 | 핵심 요구 | 담당 KB |
|---|---|---|
| 디자인 시스템 | Material 3, 앱 색상/타이포, 공통 카드/탭/설정 row | [ui-map](../ui-map/README.md) |
| 데이터 새로고침 | 거래/카테고리/설정 변경 후 화면 refresh event | [data-refresh](../data-refresh/README.md) |
| 전역 스낵바 | 장기 작업/실패/성공 피드백 | [app-shell](../app-shell/README.md) |
| SMS 즉시 저장 + 앱 노티 표시 | 실시간 저장 후 MoneyTalk 거래 알림 표시/숨김 정책 | [notification-display](../notification-display/README.md), [filtering](../filtering/README.md) |
| SMS 파싱 파이프라인 | batch/instant SMS, regex/vector/LLM, coverage | [sms-parsing](../sms-parsing/README.md), [sms-pipeline](../sms-pipeline/README.md) |
| 카테고리 분류 | 4-tier 자동 분류, 사용자 수정 학습 | [category-classification](../category-classification/README.md) |
| Room DB | schema/migration, finance data repository | [finance-data](../finance-data/README.md) |

## 로컬 기능의 표시·저장 계약

- 브리핑은 기록 기준임을 항상 알리고 부분 수집/권한 없음 안내를 보존한다. 과거 월에는 미래 하루 참고액을 표시하지 않고 예산 초과는 음수 참고액 대신 초과 금액으로 표시한다.
- 고정 지출 예상은 실제 일정이나 결제 완료가 아니다. 후보가 없으면 카드를 숨기고, 미래 거래 삽입/예산 차감/새 알림을 만들지 않는다. 근거 버튼은 실제 거래로 이동한다.
- 거래 롱클릭은 화면 중앙의 작은 작업 메뉴로 표시한다. 거래명·닫기 X 아래 구분선과 아이콘을 곁들인 세로형 `수정`·`삭제` 행을 두고, 행 전체를 누를 수 있게 한다. 삭제만 위험 색상으로 구분하며 수정은 유형과 ID를 보존해 기존 편집 화면을 연다. 삭제는 별도 확인에서 거래명·금액을 보여준다. 메뉴 닫기의 데이터 불변, 수입·지출 ID 구분, 삭제 중 닫기와 중복 동작 차단, 삭제 원문 재수집 방지를 확인한다.
- 새 브리핑/예상 계산은 AI 요청·광고·크레딧 차감을 만들지 않는다. 사용성 효과와 운영 비용/광고 지급의 검증 범위는 [제품 개선 검토](../project-context/04-product-improvements-20260908.md)를 본다.

## 변경 시 체크

1. 화면 요구사항을 수정하면 담당 화면 KB의 `package-reference/**`도 같이 업데이트했는가?
2. 화면 진입점, intent extra, Activity open 계약이 실제 코드와 맞는가?
3. 필터/통계 제외/카드 숨김 정책이 화면별 합계와 노티 표시까지 일관되게 적용되는가?
4. Composable 이름이나 계층이 바뀌면 [../ui-map/01-screen-composable-index.md](../ui-map/01-screen-composable-index.md)를 같이 갱신했는가?

## 개발 가능성 감사

- 전체 화면 KB가 실제 수정 진입에 충분한지 확인하려면 [02-screen-development-audit-plan.md](02-screen-development-audit-plan.md)를 먼저 본다.
- 현재 화면별 판정은 [03-screen-development-readiness-audit.md](03-screen-development-readiness-audit.md)에 둔다.
