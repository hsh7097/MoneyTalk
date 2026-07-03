---
type: domain
title: History 도메인
description: MoneyTalk 내역 화면 작업의 진입점, 필수 문서, 기준 패키지를 안내한다.
tags: [moneytalk, history, domain, compose]
resource: app/src/main/java/com/sanha/moneytalk/feature/history/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# History 도메인

> 상태: draft
> 기준: 2026-07-03 현재 `feature/history` 소스 확인

History는 내역 탭의 목록/달력/필터/상세 다이얼로그와 거래 수정 진입을 담당한다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| [00-structure-map.md](00-structure-map.md) | History 폴더, 핵심 파일, AI 참조 순서를 정리한다. | 변경 파일이 History 내부 어느 책임인지 판단할 때 본다. |
| [package-reference/README.md](package-reference/README.md) | 세부 개발 문서 인덱스다. | entry/data/rendering/checklist 중 어떤 문서로 내려갈지 고를 때 본다. |
| [package-reference/01-entry-screen.md](package-reference/01-entry-screen.md) | NavGraph, HistoryScreen 진입, 외부 filterCategory 흐름을 설명한다. | 화면 진입, route argument, 권한/동기화 상태 표시를 볼 때 본다. |
| [package-reference/02-data-viewmodel.md](package-reference/02-data-viewmodel.md) | HistoryViewModel, Repository, page cache, 필터 state를 설명한다. | 데이터 로딩, 필터, 정렬, 월별 cache 문제를 볼 때 본다. |
| [package-reference/03-rendering-action.md](package-reference/03-rendering-action.md) | HistoryScreen, Calendar, Filter, Dialog, 거래 카드 action을 설명한다. | 렌더링, 클릭/액션, navigation 문제를 볼 때 본다. |
| [package-reference/04-files-checklist.md](package-reference/04-files-checklist.md) | 수정 전후 확인할 파일과 검증 질문이다. | 리뷰 전 누락된 주변 파일을 점검할 때 본다. |
| [05-file-inventory.md](05-file-inventory.md) | History 전체 파일 역할 인덱스다. | 파일 후보가 애매할 때 본다. |
| [change-log.md](change-log.md) | History KB 변경 로그다. | 문서가 왜 바뀌었는지 확인할 때 본다. |

## 기준 패키지

- `app/src/main/java/com/sanha/moneytalk/feature/history/ui/`

## 핵심 흐름

1. `navigation/NavGraph.kt`의 `Screen.History.route`
2. `HistoryScreen`
3. `HistoryViewModel`
4. `ExpenseRepository`, `IncomeRepository`, `OwnedCardRepository`, `SettingsDataStore`
5. `HistoryPageData`, `TransactionListItem`
6. `HistoryCalendar`, `HistoryFilter`, `HistoryDialogs`, `HistoryHeader`
7. `TransactionEditActivity` 또는 거래 상세 다이얼로그

## 작업 판단

- 화면 진입/route argument 문제면 `package-reference/01-entry-screen.md`를 본다.
- 필터, 정렬, page cache, 데이터 로딩 문제면 `package-reference/02-data-viewmodel.md`를 본다.
- 목록/달력/다이얼로그/거래 카드 액션 문제면 `package-reference/03-rendering-action.md`를 본다.
- 수정 전 빠른 파일 점검은 `package-reference/04-files-checklist.md`와 `05-file-inventory.md`를 본다.
