---
type: package-reference
title: Settings rendering/action
description: SettingsScreen, dialog, bottom sheet, 설정 row action 연결을 설명한다.
tags: [moneytalk, settings, compose, action]
resource: app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsScreen.kt
timestamp: 2026-07-12T00:00:00+09:00
status: verified
---

# Settings rendering/action

## 주요 UI 파일

| 파일 | 역할 |
|---|---|
| `SettingsScreen.kt` | 설정 탭 전체 UI, row group, action dispatch |
| `SettingsPreferenceDialogs.kt` | 테마/월 시작일/월 예산 dialog |
| `BudgetBottomSheet.kt` | 카테고리별 예산 입력 bottom sheet |
| `SettingsDataDialogs.kt` | 백업 export/import, Google Drive 파일 목록 dialog |
| `SettingsInfoDialogs.kt` | 앱 정보/개인정보 dialog |

## 액션 연결 원칙

- 화면 row click은 가능하면 `SettingsIntent`로 ViewModel에 전달한다.
- dialog 표시 여부는 `SettingsDialog`와 `SettingsUiState`가 관리한다.
- 파일 picker/Google sign-in처럼 Activity 결과가 필요한 액션은 UI에서 launcher를 들고 ViewModel 메서드로 결과를 전달한다.
- 예산/백업/Drive/카드 보유처럼 여러 저장소를 만지는 액션은 관련 기능 KB를 같이 확인한다.
- AI 서비스 불가 상태는 사용자 key 입력 dialog로 우회하지 않고 현재 작업 실패/서비스 상태 메시지로 안내한다.

## 최대 글자 배율 계약

- `ExportDialog`의 JSON/CSV 형식 선택지는 세로 전체 폭 `FilterChip`으로 배치한다. 가로 `Row`로 되돌리면 선택 아이콘이 있는 JSON 항목이 폭을 먼저 차지해 `CSV (엑셀)`이 한 글자씩 세로로 붕괴한다.
- 카드와 카테고리 필터는 항목 수가 가변적이므로 기존 가로 스크롤을 유지한다.
- SM-F966N 닫힘 화면 1080x2520, Android 16, `font_scale=2.0`에서 제목, 형식 선택, 데이터 유형, 필터, 로컬 저장/Google 버튼까지 스크롤 가능 상태를 확인한다.
