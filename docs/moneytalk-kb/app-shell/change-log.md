---
type: changelog
title: App Shell KB 변경 로그
description: App Shell KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, app-shell, changelog]
resource: docs/moneytalk-kb/app-shell/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# App Shell KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-07-08 | `MainActivity.kt`, `MainViewModel.kt`, `RewardAdManager` 확인 | 이전 월 문자 가져오기 크레딧 다이얼로그 라우팅 보강 | `README.md`, `00-structure-map.md` | 월별 전체 동기화 광고 표현을 채팅/월별 가져오기 1크레딧 정책과 일치하도록 수정. |
| 2026-07-08 | `MainActivity.kt`, `MainViewModel.kt`, `navigation/**`, `MainUiState.kt` 확인 | App Shell KB 생성 | `README.md`, `00-structure-map.md`, `05-file-inventory.md` | 하단 탭 4개와 Activity 전역 sync/ad/dialog 상태를 화면 KB와 분리해 라우팅하도록 추가. |
