---
type: package-reference
title: Settings rendering/action
description: SettingsScreen, dialog, bottom sheet, 설정 row action 연결을 설명한다.
tags: [moneytalk, settings, compose, action]
resource: app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsScreen.kt
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# Settings rendering/action

## 주요 UI 파일

| 파일 | 역할 |
|---|---|
| `SettingsScreen.kt` | 설정 탭 전체 UI, row group, action dispatch |
| `SettingsPreferenceDialogs.kt` | 테마/API key/월 시작일/월 예산 dialog |
| `BudgetBottomSheet.kt` | 카테고리별 예산 입력 bottom sheet |
| `SettingsDataDialogs.kt` | 백업 export/import, Google Drive 파일 목록 dialog |
| `SettingsInfoDialogs.kt` | 앱 정보/개인정보 dialog |

## 액션 연결 원칙

- 화면 row click은 가능하면 `SettingsIntent`로 ViewModel에 전달한다.
- dialog 표시 여부는 `SettingsDialog`와 `SettingsUiState`가 관리한다.
- 파일 picker/Google sign-in처럼 Activity 결과가 필요한 액션은 UI에서 launcher를 들고 ViewModel 메서드로 결과를 전달한다.
- 예산/백업/Drive/카드 보유처럼 여러 저장소를 만지는 액션은 관련 기능 KB를 같이 확인한다.
