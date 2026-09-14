---
type: reference
title: Release 1.0.5 Readiness
description: 1.0.5의 기능별 검토·검증, 브랜치 통합과 Play 제출 결과 및 남은 심사 조건을 기록한다.
tags: [moneytalk, release, 1.0.5, verification, play-console]
resource: app/src/
timestamp: 2026-09-14T22:56:00+09:00
status: draft
---

# 1.0.5 배포 준비 기록

## 문서의 범위와 현재 상태

2026-09-14 `1.0.5 (23)`의 검토·수정·빌드와 보호 브랜치 통합·원격 푸시를 완료했다. 내부 테스트는 22:53에 23번으로 교체했으며, 프로덕션과 Alpha의 전체 출시 요청을 Google에 전송했다. 게시 개요에는 두 항목이 `검토 중인 변경사항`으로 표시되고 자동 빠른 검사가 진행 중이다. API 정책 페이지도 `업데이트 검토 중`이다. 운영에 새 버전이 공개됐거나 정책 경고가 최종 해소됐다는 의미는 아니다. 승인 후 자동 공개되는 기존 설정을 유지했다.

| 기준 | 확인한 사실 | 해석 범위 |
|---|---|---|
| 실제 Play 프로덕션 | `22 (1.0.4)`, 2026-07-24 업로드, 07-27 전체 출시, 대상 API 36, 16KB 메모리 페이지 지원 | 09-14 Play Console 실사 기준의 배포본 |
| 기존 로컬 변경 검토 | `origin/develop`의 `9b4b4bf`부터 `f9f708f`까지의 변경 | 검토용 Git 범위이며 정확한 Play 22 빌드 소스를 증명하는 범위는 아님 |
| 09-14 후속 변경 | `f0b4955`, `296d569`, `c27a3b1`, `e16565a`, `173a984`, `8c94ab5` | 기능별 수정과 누락된 영문 리소스 4개 보완 |
| 사전 검증 빌드 | `8c94ab5`, `versionName=1.0.4`, `versionCode=22`, `targetSdk=36` | 9월 로컬 검증용 산출물이며 7월 Play APK와 다름. 운영 업로드 금지 |
| 배포 후보 버전 설정 | `4a69b6b`, `versionName=1.0.5`, `versionCode=23`, `targetSdk=36` | 표준 AAB/APK 빌드·산출물 검증 완료 |
| 23번 AAB 해시 / Play 결과 | 아래 최종 산출물 절에 SHA-256 기록 / 내부 테스트 게시, 프로덕션·Alpha 심사 요청 접수 | Google 자동 검사·심사 진행 중. 운영 공개 완료로 해석하지 않음 |

9월 로컬 기능이나 개인 설치본의 동작을 7월 배포본의 성과로 평가하지 않는다. 사용자·광고·AI 비용을 비교할 때도 실제 배포 시점과 관찰 기간을 맞춘다. 기존 API 변경 기록은 [1.0.4 대상 API 업데이트](03-release-1.0.4-target-sdk-update.md)를 함께 본다.

## 포함할 대표 변경

아래는 현재 소스에 반영된 변경 요약이다. 각 기능의 상세 계약과 이전 검증 기록은 연결된 KB를 기준으로 한다.

| 기능 | 변경된 동작 | 관련 근거 |
|---|---|---|
| 문자 수집과 파싱 | SMS·MMS·RCS의 본문과 출처 처리를 보완하고, 미완성 금융 문자는 지연 재처리한다. JSON 필수값·금액·배치 식별자를 검증해 잘못된 응답을 정상 거래로 저장하지 않도록 한다. 잘못된 원격 규칙 하나가 다른 규칙 로딩을 막지 않게 한다. | [수집 계약](../sms-parsing/06-ingestion-contract.md), [RTDB 표본 검토](../sms-pipeline/07-rtdb-sms-origin-import-log.md) |
| 중복과 삭제 상태 | 문자와 앱 알림의 같은 거래를 중복 저장하지 않도록 보완하고, 정본 교체·중복 생략 시에도 출처 관계를 보존한다. 삭제한 거래가 다른 출처로 다시 수집되는 경로를 막는다. | [수집 계약](../sms-parsing/06-ingestion-contract.md), [알림 후속 검증](../project-context/08-home-ledger-ux-validation-20260909.md#알림-액션-후속-검증과-ai-연결-진단) |
| 홈 | 기존 그라데이션·흰색 큰 금액과 복원된 팔레트를 유지한다. 이번 달 지출 바로 아래에 누적 추이를 두고, 다가올 고정 지출은 홈 최하단에 배치한다. | [홈·가계부 검증](../project-context/08-home-ledger-ux-validation-20260909.md), `HomePageContent.kt` |
| 주간 소비 비교 | 테두리 카드의 `내 소비 한눈에`에서 `최근 7일 소비`와 `이전 7일 소비`를 강조한다. 예산을 빼고 `고정 지출 제외 · 기록 기준`을 안내한다. | [홈·가계부 검증](../project-context/08-home-ledger-ux-validation-20260909.md) |
| 누적 추이 조작 | 탭·길게 누르기·드래그로 날짜를 선택하고, 현재 표시한 월별 선과 선택한 비교 항목의 해당 날짜 누적 금액을 함께 보여준다. 범례는 연도를 생략해 간결하게 표시한다. | [홈·가계부 검증](../project-context/08-home-ledger-ux-validation-20260909.md) |
| 가계부와 거래 편집 | 스크롤 시 큰 요약 영역을 접고 목록·달력·필터를 한 줄에 둔다. 필터 근거 내역과 편집 진입을 연결하고, 거래를 길게 누르면 간결한 수정·삭제 모달을 연다. | [홈·가계부 검증](../project-context/08-home-ledger-ux-validation-20260909.md) |
| 거래 알림 | 알림에 삭제·통계 제외 액션을 제공한다. 알림 본문은 편집으로 연결한다. 편집 중 발생한 알림 변경을 오래된 입력으로 덮어쓰거나 삭제 거래를 재생성하는 경로를 방지한다. | [알림 후속 검증](../project-context/08-home-ledger-ux-validation-20260909.md#알림-액션-후속-검증과-ai-연결-진단) |
| AI 채팅 | 인증 실패의 원인을 보존하고 후속 AI 호출을 중단하며, 기존 경로에서 차감 크레딧을 한 번만 반환한다. 취소를 정상 전파하고 선택한 대화의 메시지만 구독한다. 조회·액션·분석 실행 책임을 분리했다. | [채팅 계약](../chat/05-system-contract.md), `ChatMessageObserver.kt` |

고정 지출 제외는 주간 소비 비교와 그 근거 내역의 기준이다. 월 전체 지출·누적 추이·예산에서 고정 지출을 일괄 제외하는 변경이 아니다. 고정 지출 예상 역시 실제 거래를 자동 생성하는 기능이 아니다.

RTDB에서 가져온 실패 중심 표본의 재생 결과를 전체 사용자의 문자 정확도나 실서비스 비용 절감률로 일반화하지 않는다. 원문과 개인 거래 정보는 이 문서 및 배포 노트에 포함하지 않는다.

## 09-14 리뷰에서 추가한 수정

| 커밋/항목 | 수정 내용 | 확인 범위 |
|---|---|---|
| `296d569` | `SmsIngestionWriter`가 수신시각 차이로 서로 다른 출처 ID를 가진 동일 문자를 생략·조정할 때 출처 연결을 보존한다. | 동일 본문·숫자 잔액·60초 범위 등 동등성 근거가 있는 경우에 한정하며, 정상 반복 거래를 무조건 합치지 않는다. |
| `c27a3b1` | 앱을 다음 날 다시 열면 달력의 오늘 표시와 일별 금액이 현재 날짜를 기준으로 갱신되도록 한다. | 날짜 변경 복귀 테스트 1개와 금액 테스트 2개 통과 |
| `e16565a` | 알림 테스트가 게시·취소 완료를 기다리도록 보완한다. | 테스트의 비동기 대기 수정이며 앱 동작 변경은 아니다. 해당 클래스 8개 재검증 통과 |
| `173a984` | 현재 시계를 직접 읽는 Pager callback을 제거하고 홈·가계부·카테고리 상세에서 구성 시 확정된 페이지 수를 사용한다. | Play의 `MutableIntervalList:183`, `extraPagesAfter:346/340`, `createPagesAfter:496` 스택을 수정 전 재현. 수정 후 월 증가·시계 후퇴·시작일 변경 회귀 3개 통과 |
| `8c94ab5` | 인증·예산 입력·거래 알림·편집 안내에서 빠진 영어 번역 4개를 추가한다. | 표준 Debug/Release 빌드, Release AAB 생성 및 Release Lint 통과 |
| `4a69b6b` | 배포 버전을 `1.0.5 (23)`으로 지정한다. | 버전 변경 후 Debug/Release·AAB·Lint 및 최종 산출물 검사 통과 |
| `f0b4955` | 개인 설치본의 AI 인증 복구와 일반 운영 배포의 인증 경계를 문서화했다. | 아래 운영 경계와 [채팅 계약](../chat/05-system-contract.md) 참조 |

## 검증 현황

단위·계측·화면 검사는 버전 변경 전 `8c94ab5`까지의 사전 검증 결과다. 마지막 영문 번역 보완은 빌드와 Lint로 검증했으며, 계측 실행 이후 앱 동작 코드는 바뀌지 않았다. 버전 23의 빌드와 산출물 검사는 별도 행과 아래 절에 기록한다. 범위가 겹치는 재검증 수를 합산해 총 테스트 수로 표시하지 않는다.

| 검증 | 작성 시점 결과 | 근거/제약 |
|---|---|---|
| 표준 빌드 | `assembleDebug`, `assembleRelease`, `bundleRelease`, `lintRelease` 성공 | `artifacts/release-1.0.5-20260914/build-release-verified.log`; 3분 12초. 이 실행의 APK/AAB는 사전 검증용 버전 22 |
| Release Lint | 오류 0개, 경고 237개, 힌트 4개 | `app/build/reports/lint-results-release.txt`; 경고가 없는 빌드라는 의미는 아님 |
| JVM 단위 테스트 | Debug 424개, Release 424개 모두 통과 | `app/build/test-results/testDebugUnitTest/`, `testReleaseUnitTest/`; 같은 424개 검사를 두 빌드 설정에서 확인한 결과이며 서로 다른 848개 테스트가 아님 |
| Android 최초 통합 실행 | 162개 실행 대상 중 실패 1개, 수동 fixture 2개 조건부 제외 | `artifacts/release-1.0.5-20260914/instrumentation-main.txt`; 알림 비동기 대기 수정 전 결과 |
| Android 최종 통합 실행 | 165개 대상, 163개 통과, 수동 fixture 2개 조건부 제외, 실패 0개 | `instrumentation-final.txt`, 320.155초; 월 Pager와 알림 테스트 보완 포함. 이후 영문 번역 4개 보완은 리소스 빌드/Lint로 검증 |
| 알림 실패 영향 범위 재검증 | 8개 통과, 13.329초 | `notification-after.txt`; `e16565a`의 대기 보완 후 해당 클래스 재실행 |
| 문자 수집 및 출처별 삭제 보존 | 문자 41개 + 출처별 삭제 8개 통과 | 관련 Room 계측 결과; 주요 실행과 중복되는 범위 |
| 날짜 변경과 달력 금액 | 날짜 복귀 1개 + 금액 2개 통과 | `CalendarDateRolloverTest` 등 영향 범위 재검증 |
| 월 경계 Pager | 수정 전 오류 재현, 수정 후 회귀 3개 통과 | `pager-before.txt`, `instrumentation-final.txt`; 위 최종 통합 실행에 포함 |
| 표준 Release 화면 확인 | 홈·12,340원 수동 거래 저장·길게 누르기 모달·달력·이전 월 이동·강제 종료 후 데이터 유지 확인, 크래시 0건 | read-only 모드로 실행한 별도 `emulator-5556`, `preflight-version22.apk`; 아래 화면 근거 참조 |
| 버전 23 빌드 | Debug/Release APK, Release AAB, Release Lint 성공 | `build-version23.log`, 5분 54초 |
| 버전 23 최종 패키징 | 버전 커밋 후 VCS 메타데이터를 맞추고 표준 APK/AAB 재생성 성공 | `build-vcs23.log`, `build-package23.log`; 최종 패키징 3분 39초 |
| 배포용 23번 산출물 | 패키지·버전·서명·운영 설정·16KB 정렬 검사 통과 | 아래 해시로 식별한 `MoneyTalk-1.0.5-release23.aab` 및 APK. Play 내부 초안의 23번 등록 확인, 미게시 |

조건부 제외한 두 항목은 `TransactionNotificationActionInstrumentedTest.manualFixtureLeavesExpenseAndIncomeActionsForShadeReview`와 `TransactionQuickActionUiTest.seedManualOvernightFixture`다. 명시적으로 요청할 때 화면 확인용 합성 데이터를 남기는 도구이며, 일반 회귀 검사의 성공으로 세지 않는다.

최초 Release Lint는 영문 번역 누락 4개로 실패했다. `chat_app_verification_failed`, `budget_input_invalid`, `notification_transaction_amount`, `transaction_edit_not_found`를 `8c94ab5`에서 보완한 뒤 오류 0개로 재검증을 통과했다. 남은 237개 경고와 4개 힌트는 별도로 선별 검토했다. 기존 HTTP 라이브러리의 trust-all 유틸 경고는 앱에서 호출하지 않으며 기본 HTTPS 검증을 사용한다. 신규 JobScheduler ID 범위 경고는 현재 고정 작업 ID와 WorkManager의 실제 충돌 근거가 없어 이번 수정에서 설정을 변경하지 않았다. 나머지 경고에서 미지원 API·권한 누락·DB 스키마 오류와 같은 배포 차단 결함은 확인되지 않았다.

표준 Release 화면 확인 자료는 같은 산출물 폴더의 `release-start.png`, `release-menu.png`, `release-calendar.png`, `release-home-final.png` 및 대응 XML에 보존했다. `preflight-version22.apk`와 `preflight-version22.aab`는 버전 변경 전 검증용으로 이름을 분리했다. 이 APK의 합성 거래 유지 확인은 실제 사용자 DB 전체 무결성이나 Play 설치본 AI 인증 성공을 뜻하지 않으며, 두 22번 파일은 새 출시 업로드에 사용하지 않는다.

이전 09-08~10 화면·빌드·개인 기기 확인은 각 KB에 보존한다. 과거 실기기 확인이나 개인 설치본 AI 응답 성공을 이번 일반 배포 후보의 실기기·Play 인증 검증으로 대신하지 않는다. 이번 계측은 에뮬레이터와 합성 데이터를 사용하며 일상 사용 중인 실기기를 조작하지 않는다.

## 최종 23번 산출물

앱 소스와 산출물의 VCS 기준 커밋은 `4a69b6bee1a3a04c3c4e8c891dafae80f82c2d5e`다. 아래 파일은 `artifacts/release-1.0.5-20260914/`에 보관했다. 이후 문서 커밋과 배포 브랜치 통합이 추가되어도 이 산출물의 앱 소스 기준은 해당 버전 커밋으로 구분한다.

| 산출물 | SHA-256 |
|---|---|
| `MoneyTalk-1.0.5-release23.aab` | `0a8ebe8da67d2b78c1fbecec803939c5813dd29e389c224bec82ddacdb3387ed` |
| `MoneyTalk-1.0.5-release23.apk` | `fb00db7a6970743120478681ffca0745cf3a5bdb73ac6ab2376ad3866e458691` |

- APK 패키지는 `com.sanha.moneytalk`, 버전은 `23 / 1.0.5`, 최소 API는 26, 대상 API는 36이다. manifest의 debuggable은 활성화되지 않았다.
- 두 파일의 서명 인증서 SHA-256은 `9661f4e625c6a3fd56c779fa6f914eab72a152fea3fcb1832d59f66c4f8c479e`로 Play Console의 업로드 인증서와 일치한다. Google Play가 배포 APK에 사용하는 앱 서명과는 별개다. AAB의 `jarsigner -verify` 결과는 종료 코드 0, `jar verified.`이며 `aab-signature23.txt`에 기록했다.
- `BuildConfig.DEBUG=false`, `MONETIZATION_TEST_OVERRIDE=false`를 확인했다. 최종 mapping에는 Play Integrity 제공자가 있고 Debug App Check 제공자는 없다. 개인 광고 제외·Debug App Check 빌드가 섞이지 않았음을 확인했다.
- AAB/APK에 각각 포함된 네이티브 라이브러리 8개를 검사했고, 64비트 라이브러리 중 16KB 비호환 항목은 0개다. APK의 16KB zipalign 검증도 성공했다. 근거는 `native-alignment23.json`, `zipalign23.txt`다.
- 별도 read-only 에뮬레이터 `5556`을 종료하고 기존 `5554`의 준비 상태를 복원했다. 이 정리는 일반 Play 설치본의 AI 응답 검증을 대신하지 않는다.

## 운영과 Play 정책 경계

- 일반 Release의 [AppCheckInstaller](../../../app/src/release/java/com/sanha/moneytalk/core/firebase/AppCheckInstaller.kt)는 Play Integrity를 사용한다. 개인용 광고 제외·Debug App Check APK는 운영 업로드 대상이 아니다. 일회성 개인 빌드 변경은 복원된 상태이며 Firebase AI enforcement 완화나 API 키 교체를 배포 해결책으로 적용하지 않는다.
- 09-10 개인 설치본에서 실제 Gemini 응답을 확인한 결과는 해당 인증 경로에 한정한다. 일반 배포본은 실제 Play 설치 후 Gemini 응답과 App Check 오류 여부를 별도로 확인해야 한다.
- 검토 범위에서 광고 보상·크레딧·Premium 정책의 운영 경로는 유지했다. 앱 함수의 삭제 기능 기본 비활성 상태, 알림 액션 receiver의 비공개 설정, 서비스 권한 보호, debug 한정 진단 수집 및 원문 포함 플래그의 경계를 확인했다. 정적 리뷰만으로 실제 광고 노출·수익·모든 원격 기능의 동작을 보증하지 않는다.
- 09-14 Console 실사에서 `com.sanha.moneytalk`의 Android 개발자 앱 등록 완료와 Play 서명 일치를 확인했다. 개발자 등록 안내를 새 패키지 생성이나 등록 미완료로 해석하지 않는다.
- 최초 정책 점검에서 프로덕션 22는 API 36이었고 Alpha `15 (1.0.0)`와 내부 테스트 `16`이 API 35였다. 두 테스트 트랙의 새 출시에는 23번만 포함하고 구형 번들을 제외했다. 제출 후 [정책 상세](https://play.google.com/console/u/0/developers/8359650362945506885/app/4972939095841401371/policy-center/issues/4985765371414586223/details)는 `업데이트 검토 중`으로 변경됐다. 최종 해소 여부는 Google 검토 이후 확인해야 한다. [대상 API 요구사항](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en) 참조.
- 파일 선택 도구의 `Not allowed` 오류 이후 별도 권한 변경 없이 실제 Console의 23번 등록을 확인했다. 업로드된 번들은 API 36, 16KB 지원, ReTrace 매핑 포함으로 표시됐다. 도구 오류만으로 업로드 미완료를 단정하지 않는다. 이후에는 동일 번들을 승급해 사용했으며 22번 검증 파일을 업로드하지 않았다.
- [내부 테스트 출시 4](https://play.google.com/console/u/0/developers/8359650362945506885/app/4972939095841401371/tracks/4701584506886264546/releases/4/details)는 `1.0.5 (23)`, `내부 테스터에게 제공됨`, 게시일 09-14 22:53으로 확인했다. 기존 테스터 미지정 상태는 유지했다.
- [프로덕션 출시 7](https://play.google.com/console/u/0/developers/8359650362945506885/app/4972939095841401371/tracks/4697421205430103547/releases/7/review)과 [Alpha 출시 12](https://play.google.com/console/u/0/developers/8359650362945506885/app/4972939095841401371/tracks/4698232216425212686/releases/12/review)를 각각 23번·전체 출시 100%로 저장한 뒤 두 변경사항을 함께 전송했다. 기존 대상 국가와 테스터 구성을 변경하지 않았다. [게시 개요](https://play.google.com/console/u/0/developers/8359650362945506885/app/4972939095841401371/publishing)는 `검토 중인 변경사항` 및 자동 빠른 검사 진행 중으로 표시한다. 검사가 성공하면 심사로 넘어가며 관리형 게시가 꺼져 있어 승인 후 자동 공개된다.
- Play 출시 검토에서 차단 오류는 없었다. 운영에는 네이티브 디버그 기호 미첨부 권고 1개, 두 테스트 트랙에는 같은 권고와 테스터 미지정 경고가 있었다. ReTrace 매핑은 첨부돼 있으나 네이티브 충돌의 기호 분석에는 한계가 남는다. 이를 없애기 위해 서명·난독화·라이브러리 설정을 변경하지 않았다.

## 브랜치와 보존 기록

배포 준비는 `codex/release-1.0.5`에서 수행했다. 기능별 커밋을 유지한 채 `develop`과 `master`를 `54dfc0f`까지 fast-forward 통합하고 원격에 atomic push했다. `git ls-remote`로 두 원격 브랜치의 동일 커밋을 확인했다. 배포 코드 태그 `1.0.5`는 최종 AAB의 소스 커밋 `4a69b6b`를 가리키며 원격 태그의 실제 대상도 확인했다. 이후 제출 결과는 문서 커밋으로 기록하며 배포 코드 태그를 이동하지 않는다. 이미 통합된 작업 브랜치 4개는 삭제했다.

`codex/refactor-sms-processing-scope`의 단독 커밋 `70c5fe1`은 현재 구현과 동등하게 통합된 변경이 아니다. 오래된 스코프 분리 실험을 이번 배포에 섞지 않고, 로컬 annotated tag `archive/sms-processing-scope-20260601`과 `artifacts/release-1.0.5-20260914/sms-scope-unmerged-backup.bundle`에 보존·검증한 뒤 작업 브랜치를 삭제했다. 원격 브랜치를 정리했다는 뜻은 아니다.

## 외부 처리와 후속 확인

1. Google 자동 검사·심사 완료와 프로덕션·Alpha 공개 상태를 확인한다. 이 문서 작성 시점에는 심사 요청이 접수된 상태다.
2. 심사 이후 API 정책 경고의 최종 해소 여부를 확인한다.
3. 실제 Play 설치 경로에서 Gemini 응답과 App Check 오류 여부를 확인한다. 개인 설치본 또는 에뮬레이터 검사 결과로 대신하지 않는다.

스토어 안내는 로컬 `artifacts/release-1.0.5-20260914/release-notes-ko.txt`와 동일한 한국어 문구로 세 트랙에 반영했다. 최종 공개 여부는 파일 존재가 아닌 실제 Console 처리 상태를 기준으로 판단한다.
