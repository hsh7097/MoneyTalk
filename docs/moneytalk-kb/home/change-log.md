---
type: changelog
title: Home KB 변경 로그
description: Home KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, home, changelog]
resource: docs/moneytalk-kb/home/
timestamp: 2026-07-11T00:00:00+09:00
status: draft
---

# Home KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-07-11 | Codex_Fold_API_36 닫힘 화면 `font_scale=2.0`, SM-F966N 닫힘 화면 `font_scale=1.0`, `VicoCumulativeChart` 확인 | 공용 누적 차트 X축 양끝 생략 표시 보정 | `VicoCumulativeChart.kt`, `../ui-map/01-screen-composable-index.md` | Vico alpha 버전은 큰 글자 AVD뿐 아니라 Samsung Fold 정상 배율에서도 첫날/말일을 `...`로 그릴 수 있다. 기간 헤더가 시작일/종료일을 제공하므로 모든 배율에서 양끝 라벨을 비우고 중간 날짜만 표시한다. Home 실기기와 Home/Category Detail AVD를 재검증했다. |
| 2026-07-08 | `feature/home/ui/**`, `feature/home/data/**`, `core/ui/coachmark/**` 확인 | Home 화면 KB 생성 | `README.md`, `00-structure-map.md`, `package-reference/**`, `05-file-inventory.md` | 홈 탭의 월별 현황, 카테고리 지출, AI 인사이트, 미분류 분류 CTA, 코치마크 작업 시작점을 분리. |
| 2026-07-09 | `HomeScreen.HomePageContent`, `HomeViewModel.loadPageData`, `CategoryDetailActivity`, `TransactionEditActivity` 확인 | 홈 화면 블록 상세화 | `README.md`, `package-reference/README.md`, `package-reference/05-surface-map.md` | CTA, 월간 현황, 추이, AI 인사이트, 카테고리, 오늘 내역의 표시 조건과 보조 화면 연결을 정리. |
