---
type: domain
title: SMS Settings 도메인
description: SMS 제외 키워드와 차단 발신자 설정 화면을 설명한다.
tags: [moneytalk, sms-settings, domain, compose]
resource: app/src/main/java/com/sanha/moneytalk/feature/smssettings/
timestamp: 2026-07-08T00:00:00+09:00
status: draft
---

# SMS Settings 도메인

> 상태: draft
> 기준: 2026-07-08 현재 `feature/smssettings/ui/**`, SMS 제외/차단 repository 확인

SMS Settings는 SMS 파싱 전에 제외할 키워드와 발신자 차단 설정을 관리하는 화면이다.

## 먼저 볼 파일

| 문서 | 역할 | 언제 보는가 |
|---|---|---|
| 이 문서 | 화면 entry, 핵심 파일, SMS pipeline 영향 범위를 정리한다. | SMS 설정 화면 작업 |
| [change-log.md](change-log.md) | KB 변경 로그다. | 문서 변경 이유 확인 |
| [../sms-parsing/README.md](../sms-parsing/README.md) | 문자 파싱 기능 KB다. | 제외 설정이 파싱 결과에 미치는 영향 확인 |
| [../sms-pipeline/README.md](../sms-pipeline/README.md) | SMS pipeline 구현 KB다. | 필터/reader 단계 변경 |

## 핵심 파일

| 파일 | 역할 |
|---|---|
| `SmsSettingsActivity.kt` | SMS 설정 Activity entry |
| `SmsSettingsScreen.kt` | 제외 키워드/차단 발신자 UI |
| `SmsSettingsViewModel.kt` | 설정 load/add/delete, repository 호출 |
| `SmsExclusionRepository.kt` | 제외 키워드 저장/조회 |
| `SmsBlockedSenderRepository.kt` | 차단 발신자 저장/조회 |

## 검증 질문

1. 제외 키워드/발신자 변경 후 기존 Home/History 표시 필터와 신규 SMS sync 필터가 모두 영향을 받는가?
2. SMS pipeline의 pre-filter 단계와 UI 설정 값이 같은 repository를 보는가?

## 화면별 책임 (2026-09-08)

| 파일 | 책임 |
|---|---|
| `SmsSettingsScreen.kt` | 내부 route, toolbar title/back, ViewModel 상태와 callback 연결 |
| `SmsSettingsMainContent.kt` | 동기화 상태/요청, 세 관리 화면 진입 메뉴 |
| `BlockedPhraseManageScreen.kt` | 기본/사용자 제외 문구 목록과 사용자 문구 추가/삭제 |
| `BlockedSenderManageScreen.kt` | 차단 발신자 목록과 추가/삭제 |
| `ExcludedCardManageScreen.kt` | 제외 카드 수동 등록과 소유/제외 toggle |

각 관리 화면은 기존 상태와 callback 시그니처를 유지한다. 저장소 호출은 `SmsSettingsViewModel`에 있으며 입력값은 해당 관리 화면의 Compose-local state다. route 이름/뒤로가기/필터 저장 정책은 바꾸지 않는다.
