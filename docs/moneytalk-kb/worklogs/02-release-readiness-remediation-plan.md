---
type: worklog
title: Release Readiness Remediation Plan
description: 2026-07-11 release readiness review에서 발견된 배포 보류 이슈를 처리하기 위한 작업 계획과 검증 기록.
tags: [moneytalk, release, lint, privacy, gemini, verification]
resource: app/
timestamp: 2026-07-12T23:14:00+09:00
status: draft
---

# Release Readiness Remediation Plan

> 기준: 2026-07-11 배포 품질 재검토 결과
> 목표: 배포 보류 블로커를 하나씩 닫고, 릴리즈 게이트를 다시 통과 가능한 상태로 만든다.

## 범위

| 항목 | 상태 | 처리 기준 | 검증 |
|---|---|---|---|
| Release lint 번역 누락 | 수정 완료 | `values-en/strings.xml`에 누락된 chat 문자열 4개 추가 | `:app:lintRelease` |
| 권한 고지/개인정보 처리방침 | 기존 유지 | 사용자 결정에 따라 앱 한/영 고지와 `docs/privacy-policy.html`을 HEAD 기존 문구로 복원 | 정책 파일 `git diff --exit-code`, AVD 고지 화면 |
| release SMS 표본/로그 노출 | 코드 수정 완료 | Gemini/RTDB 표본 식별정보 최소화와 release 로그 원문 제거. 표본 수집 설정 동작은 기존 구현을 유지 | unit test, release APK/AAB 정적 검색 |
| Gemini client API key 제거 | 수정 완료 | Google Generative AI client와 RTDB key 공급 경로를 제거하고 Firebase AI Logic SDK로 전환 | source grep, dependency graph, APK/AAB binary search |
| 기타 local API key 잔존 제거 | 수정 완료 | `CLAUDE_API_KEY` BuildConfig와 사용자 key 입력/조회 경로를 제거하고 기존 DataStore key는 앱 시작 시 삭제 | generated BuildConfig, source grep, unit test |
| Google Sign-In 회귀 | 조건부 통과 | AVD에서 Google 계정 선택 화면 진입과 앱 프로세스 생존 확인. 실제 Drive 계정 선택 이후 백업/복원은 실기기 후속 검증 | release AVD account chooser, crash log |
| 계측 테스트 skipped | 보류 | AVD에서 skipped된 real-device monthly SMS/order 테스트는 실기기 조건이 준비된 별도 검증으로 분리 | 추후 실기기 connected test |
| Gemini key 전달 경로 | 수정 완료 | RTDB key 필드를 더 이상 파싱·캐시하지 않고 Firebase AI Logic을 사용한다. release는 Play Integrity App Check provider를 설치한다 | source grep, release build, Firebase Console |
| Firebase AI Logic App Check 강제 적용 | **배포 보류** | Play Integrity provider는 등록됐지만 AI Logic 앱 상태가 `등록됨(적용되지 않음)`이다. 현재 로그인 계정에는 적용 관리 권한이 없다 | 프로젝트 소유자가 AI Logic App Check 적용 후 Play 배포본 호출 확인 |
| release 후보 Git 상태 | 진행 중 | 기능 브랜치에서 검증·커밋·푸시하고, App Check 배포 게이트가 닫히기 전에는 `develop` 병합하지 않는다 | staged diff 감사, feature push |

## 처리 순서

1. `strings.xml` / `values-en/strings.xml` release lint 오류를 닫는다.
2. 권한 고지와 `docs/privacy-policy.html`은 사용자 결정에 따라 기존 문구를 유지하고 diff가 없는지 확인한다.
3. Google Generative AI client, RTDB key provider, BuildConfig/DataStore key 경로를 제거하고 Firebase AI Logic/App Check로 전환한다.
4. Gemini 원격 embedding을 결정적 local 768차원 vector로 교체하고 기존 Room vector를 최초 사용 시 재생성한다.
5. `lintRelease`, `testDebugUnitTest`, `assembleRelease`, `bundleRelease`를 재실행한다.
6. release APK를 AVD에 재설치해 SMS 수신, 파싱, 거래 알림, 핵심 화면과 crash 여부를 확인한다.
7. 변경 diff를 다시 읽고 과잉 수정, stale 문구, API key 잔존 여부를 확인한다.

## 결정 사항

- Gemini/Claude API key는 release APK/AAB에 포함하지 않는다.
- Gemini 요청은 `FirebaseAiModelFactory`의 Firebase AI Logic 경로만 사용한다. RTDB는 service flag와 model name 같은 비밀이 아닌 운영 설정만 제공한다.
- 과거 `gemini_api_key`, `claude_api_key` DataStore preference와 cached config의 key 필드는 앱 시작 시 제거·재저장한다.
- release App Check provider는 Play Integrity, debug provider는 Debug App Check를 사용한다.
- SMS/거래처 embedding은 네트워크나 API key가 없는 결정적 local vector다. 기존 Room vector는 현재 계약으로 재생성한다.
- Gemini SMS 파싱 요청은 사용자명과 계좌/카드 식별정보를 최소화하되 금액, 거래처, 날짜처럼 파싱에 필요한 필드는 유지한다.
- `local.properties`에 남아 있는 개발자 로컬 키는 빌드 스크립트가 읽지 않도록 제거 대상에서 제외한다. 이 파일은 로컬 환경 파일이며 소스 변경 범위가 아니다.
- Google Sign-In은 `play-services-auth 21.5.0` 기준이며 AVD 계정 선택 화면까지 통과했다. 실제 Drive 읽기/쓰기는 실기기 Google 계정으로 별도 확인한다.
- 기존 권한 고지와 개인정보 처리방침 문구는 유지했다. 한/영 문자열 파일의 다른 UI 문구 변경과 무관하게 권한 고지 entry가 유지되는지 별도로 확인한다.

## 검증 기록

| 검증 | 결과 | 메모 |
|---|---|---|
| `:app:lintRelease` | 통과 | 0 errors, 199 warnings, 4 hints |
| `:app:testDebugUnitTest` | 통과 | 245 tests, 0 failed/errors/skipped |
| `:app:testReleaseUnitTest` | 통과 | 245 tests, 0 failed/errors/skipped |
| SMS origin audit | 통과 | 192 rows 중 186 처리, 96.9%; 관측치 667/676, 98.7%; 정책 오탐/조치 가능한 미매칭 0 |
| `:app:assembleRelease` | 통과 | versionCode 19, versionName 1.0.3, targetSdk 35 release APK 생성 |
| `:app:bundleRelease` | 통과 | 서명된 release AAB 생성 |
| `apksigner verify app-release.apk` | 통과 | v2 signing verified |
| `zipalign -c -P 16 4 app-release.apk` | 통과 | APK 16KB page alignment 검증 |
| `jarsigner -verify app-release.aab` | 통과 | `jar verified`, release 서명 확인 |
| APK/AAB local key binary search | 통과 | `local.properties`의 Gemini/Claude 후보 값 2개 미검출. 포함된 `AIza...` 1개는 `google-services.json`의 Firebase Android 구성 키와 일치 |
| release APK install/launch smoke | 통과 | Codex_Fold_API_36 Android 16 AVD에서 `1.0.3(19)` 설치, IntroActivity에서 MainActivity 진입, 프로세스 유지, crash buffer 비어 있음 |
| `:app:connectedDebugAndroidTest` | 조건부 통과 | AVD 3개 중 1개 통과, 실기기 SMS 조건 2개 skipped, 실패 0 |
| 기존 고지/개인정보 문구 감사 | 통과 | 한/영 권한 고지 entry와 `docs/privacy-policy.html`의 기존 정책 문구 유지. 문자열 파일의 AI/UI 문구 변경은 별도 범위 |
| `git diff --check` | 통과 | `app/build.gradle.kts` 혼합 line ending을 원래 배열로 복원하고 버전 두 줄만 변경 |
| release override 광고 ID 감사 | 통과 | `MONETIZATION_TEST_OVERRIDE=true`; APK DEX에 Google rewarded/banner 테스트 ID 존재, 운영 배너 ID 미검출 |
| normal release 광고 ID 감사 | 통과 | `MONETIZATION_TEST_OVERRIDE=false`; APK/AAB DEX에 운영 reward/banner ID 4개 존재, Google 테스트 ID 2개 미검출 |
| AVD 보상형 광고 | 통과 | 테스트 광고 표시/완료 후 AI 크레딧 `4 -> 6`, 원장 `보상형 광고 +2`, fatal crash 없음 |
| SM-F966N 보상형 광고 | 통과 | override APK에서 `테스트 광고`, `리워드 지급됨`, 잔액 `5 -> 7`, 원장 `광고 충전 / 보상형 광고 / +2`, crash 0건 확인 |
| AVD 합성 SMS/자체 알림 | 통과 | 네트워크 off에서 `QA_STORE 7,890원`, `QA_MARKET 8,910원` 저장. 거래 알림 on 시 `sms_transaction_v2`에 `QA_MARKET / 8,910원` 표시 |
| AVD 화면 QA | 통과 | 홈, 가계부 목록/달력/검색/필터, 거래 추가·상세, Category Detail/Settings, AI Credit/Chat과 하위 설정 화면 진입·스크롤 확인 |
| 최대 글자/좁은 화면 QA | 통과 | Codex_Fold_API_36 CLOSED 1080x2424, `font_scale=2.0`; 공용 차트 양끝 `...` 보정 후 Home/Category Detail 재확인 |
| 테스트 설정 원복 | 통과 | 거래 알림 off, AI Credit 진입점 미노출, `font_scale=1.0`, device state OPENED(2), 앱 locale 자동, Wi-Fi/데이터 on 복원 |
| 실기기 정상 릴리즈 원복 | 통과 | SM-F966N에 override 없는 `1.0.3(19)` 재설치, 테스트 ID 미포함, AI Credit 진입점 미노출, 앱 화면 종료 확인 |
| Notification Access 진입 | 통과 | 설정 항목에서 Android `NotificationAccessSettingsActivity`로 이동하고 앱으로 정상 복귀. 권한은 실기기 후속 검증을 위해 변경하지 않음 |
| AVD runtime permission | 통과 | 최종 normal release에서 `READ_SMS`, `RECEIVE_SMS`, `POST_NOTIFICATIONS` 모두 `granted=true` 유지 |
| 실기기 UI 회귀 | 통과 | SM-F966N Android 16 닫힘 화면 1080x2520, `font_scale=1.0`에서 Home 차트 양끝 `...` 발견 후 양끝 라벨 비움으로 수정·재검증 |
| 실기기 최대 글자 전체 화면 QA | 통과 | SM-F966N Android 16 닫힘 화면 1080x2520, `font_scale=2.0`; Home, History 목록/달력/필터/검색, 거래 상세/날짜 picker/카테고리 picker, Category Detail, Chat, Settings와 안전한 하위 화면 순회 |
| 최대 글자 UI 결함 재검증 | 통과 | Export JSON/CSV 전체 폭, 공통 카테고리 picker 3열·전체 이름, 거래 날짜·시간 두 줄 전체 값 확인 |
| 최대 글자 수정 빌드 | 통과 | `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:assembleRelease`; APK SHA-256 `2B3B8BD51C4ADB6FCB2950C4199795DFAFF2A41CF41E80D6444A2BA3B1EB6B9F` |
| 수정 release 실기기 설치 | 통과 | `adb install -r` 성공, `1.0.3(19)` 유지, 사용자 데이터 보존 상태로 MainActivity 재진입 |
| 글자 크기 원복/일반 글자 회귀 | 통과 | `font_scale=1.0`; Export 형식 세로 배치, 거래 날짜 한 줄, 카테고리 picker 4열 확인 후 앱 force-stop 및 삼성 런처 복귀, crash buffer 비어 있음 |
| 최신 release AVD 재설치 | 통과 | `1.0.3(19)` APK 덮어 설치, IntroActivity에서 MainActivity 진입, 앱 데이터 유지, 프로세스 생존 |
| 최신 release 실시간 SMS/알림 | 통과 | 스마일카드 3,300원 주입 후 월 지출 `33,600 -> 36,900`, 거래처 `테스트카페`, `sms_transaction_v2` 알림 `테스트카페 / 3,300원`, fatal crash 0 |
| 최신 release 화면 회귀 | 통과 | Home, History, Settings 진입과 합계/신규 거래 표시 확인, `font_scale=1.0` 복원 |
| 최신 APK/AAB fingerprint | 통과 | APK 8,831,165 bytes, SHA-256 `365CD41B9324F74E81C2562C2EEA7D269B54C988D766381ED6E981610C78FD65`; AAB 15,022,495 bytes, SHA-256 `9484D128F7059180722CCAF79DFAE8FBB8AF4E06469C0147AF9B6825FBDDC542` |
| Firebase 모델명 확인 | 통과 | Firebase AI Logic 안정 모델 `gemini-3.1-flash-lite`, `gemini-3.5-flash` 사용 확인 |
| Firebase App Check 앱 등록 | 통과 | Firebase Console에서 `com.sanha.moneytalk` Play Integrity `등록됨` 확인 |
| Firebase AI Logic App Check 적용 | **실패/배포 차단** | AI Logic 앱 상태 `등록됨(적용되지 않음)`. 현재 로그인된 두 계정 모두 적용 관리 권한 없음 |

## 2026-07-12 최대 글자 실기기 QA 메모

- 앱 데이터 초기화 없이 기존 사용자 상태에서 검사했다. Intro/Permission과 feature gate가 닫힌 AI Credit은 이전 AVD `font_scale=2.0` 증거를 재사용했고, 실기기에서는 노출 가능한 화면을 모두 순회했다.
- 시스템 화면은 Notification Access, Google 계정 선택, 로컬 복원 파일 picker까지 진입한 뒤 값을 변경하지 않고 복귀했다.
- `중복 데이터 삭제` row는 확인 dialog 없이 즉시 실행되는 동작이었다. 순회 중 동일 금액·시간 중복 2건이 정리됐으며, 각 중복 그룹의 원본 1건은 유지되는 DAO 계약이다. `전체 삭제`와 `가이드 초기화`는 실행하지 않았다.

## 2026-07-12 실기기 초기화 재동기화와 App Check 실패 격리

### 데이터 재생성 결과

- SM-F966N의 앱 데이터를 삭제하고 `1.0.3(19)` release를 다시 설치한 뒤 SMS/알림 권한과 온보딩을 처음부터 진행했다.
- 초기 범위 계약인 최근 2개월 원본 메시지 492건에서 지출 299건, 수입 16건을 생성했다.
- 초기화 전·후 2026년 7월 홈 결과가 지출 `1,385,433원`, 수입 `451,165원`, 전월 대비 `798,376원(37%) 적게`, TOP 4 `식비 229,460 / 온라인쇼핑 148,770 / 카페·간식 101,730 / 의료·건강 83,200`으로 정확히 일치했다.
- JSON export 기준 지출 `smsId` 중복 0, 금액·시간·가게 중복 그룹 0, 0원 이하 지출 0, 빈 거래처 0, 통계 제외 3건이었다. 7월 통계 포함 지출 82건 합계도 홈과 같은 `1,385,433원`이었다.
- 미분류 109건은 release sideload의 `Firebase App Check token is invalid`로 Gemini batch가 실패한 결과다. 로컬 파싱 합계와 이미 학습된 카테고리 결과는 기준선과 같으므로 SMS regex·local embedding은 원복하지 않는다.

### 화면과 시스템 경로

- 홈 요약/추이/TOP 4/오늘 내역, 가계부 목록·달력·고정 거래 필터 적용 및 해제, 거래 상세·원본 문자·카테고리 picker, 카테고리 상세 가격순, 카테고리 설정 지출·수입·이체, 거래처 규칙 폼을 순회했다.
- 설정의 문자 제외 문구/번호/카드, 거래 알림 토글, Google 계정 선택, 로컬 복원 picker, 전체 삭제 확인 후 취소, 개인정보 처리방침, 테마·월 시작일·예산 설정을 데이터 변경 없이 확인했다.
- 알림 접근을 허용한 뒤 시스템 `enabled_notification_listeners`와 `dumpsys notification`에서 `NotificationTransactionService`가 실제 연결된 상태를 확인했다.
- 인사이트 local fast path는 `이번 달 총 지출`에 `1,385,433원`, `미분류 항목`에 실제 ID/일시/가게/금액을 반환했다. AI 의존 `식비가 수입 대비 적절해?`는 App Check 오류 bubble과 `다시 요청`을 표시했고 프로세스는 유지됐다.

### 후속 실패 격리 패치와 최신 산출물

- 실기기 clean sync는 APK SHA-256 `365CD41B9324F74E81C2562C2EEA7D269B54C988D766381ED6E981610C78FD65` 후보에서 수행했다. 이후 패치는 SMS parser/rule/embedding이 아니라 카테고리 AI 실패 격리와 Job 상호배제만 변경했다.
- App Check 실패를 첫 카테고리 요청 뒤 15분간 차단하고 대기 batch, 2차 라운드, 수입 분류, resume 재시도를 네트워크 호출 없이 종료한다. cooldown 중에도 StoreRule/vector/local keyword 단계와 `source=local` 저장은 유지한다.
- SMS 동기화 전체 처리, 자동 분류, 홈/설정 수동 분류는 한 Job만 소유한다. sync coverage와 카드 자동 등록도 소유 Job 안에서 끝내며, 잔여 분류는 소유권 해제 뒤 시작한다.
- 전체 삭제 gate는 중복 호출을 직렬화하고 취소 cleanup 동안 신규 분류·동기화를 거부한다. 취소된 sync의 진행 UI와 pending silent 재실행도 함께 제거한다.
- gate epoch로 삭제 전에 생성된 증분·월별 sync와 resume 분류 검사를 무효화한다. 월별 sync가 무효화·취소·실패되면 실제 차감된 1크레딧만 `month_sync_refund`로 복구한다.
- 카테고리/수입 LLM batch는 공유 단일 permit으로 실행해 첫 App Check 실패 뒤 다음 batch가 네트워크를 시작하는 경쟁을 차단한다.
- SMS 권한을 먼저 확인하고 광고를 표시한다. 권한 거부 시 광고와 sync를 시작하지 않고 다이얼로그를 유지한다. 광고 표시 직전에는 월별 sync 다이얼로그 UI만 숨기고 pending gate epoch는 보존하며, 사용자가 명시적으로 취소하거나 광고 표시가 실패할 때만 pending 요청을 제거하고 보상 성공 콜백은 보존한 epoch로 크레딧 지급과 월별 sync를 계속한다.
- 인프로세스 취소·시작 거부·sync 실패는 실제 차감분만 환불한다. 차감 직후 프로세스가 강제 종료되면 영속 request ID/정산 상태가 없어 1크레딧을 자동 복구하지 못하는 잔여 리스크가 있으며, 현재는 가상 크레딧·RTDB gate 단계라 비차단으로 분류한다. 유료 크레딧 도입 전에는 영속 정산을 추가해야 한다.
- 최종 release 에뮬레이터 재설치 결과 cold start category batch 오류 1회, cooldown 활성화 1회, 동일 PID 재진입 후 추가 batch 오류 0회, 설정 수동 분류 후 추가 batch 오류 0회, cooldown local-only skip 2회, `2 unclassified` 유지, fatal/ANR 0건이었다.
- 최종 Debug/Release 단위 테스트는 각각 257개, 실패·오류·skip 0이다. `lintRelease`는 0 errors, 199 warnings, 4 hints다.
- 최신 APK는 8,847,549 bytes, SHA-256 `35129257F5C345914A4E7C476DEE9288D79604A10FCC2674B0891297502CA25C`; AAB는 15,037,075 bytes, SHA-256 `85FCE0517B8D61217AEEB3AA82D9D9242F24DAAEF4E19B2DF31DE2FEB18028F3`이다. APK v2 서명·16KB zipalign과 AAB `jar verified`를 통과했다.
- 최신 패치 실기기 덮어 설치 직전에 기기 ADB 연결이 해제됐다. 최신 APK의 App Check 실패 격리는 동일 Android 16 release 에뮬레이터에서 확인했고, 실기기에는 재연결 후 `adb install -r` 및 1회 로그 확인만 남았다.

## 2026-07-12 배포 판정

소스의 RTDB API key 전달 구조는 제거됐고 APK/AAB 빌드, 서명, lint, 257개 단위 테스트, parser audit, release AVD SMS/알림/화면 smoke를 통과했다. 로컬 산출물 기준 배포 품질은 충족하지만, Firebase AI Logic의 App Check 강제 적용이 꺼져 있으므로 현재 판정은 **배포 및 develop 병합 보류**다.

남은 게이트는 다음 두 단계다.

1. Firebase 프로젝트 소유자가 AI Logic의 `com.sanha.moneytalk` App Check 상태를 `등록됨(적용되지 않음)`에서 적용 상태로 전환한다.
2. Play Console에서 설치한 release로 AI 채팅 또는 홈 인사이트 1회를 호출해 App Check와 원하는 응답 형식을 확인한다. sideload AVD의 `Firebase App Check token is invalid`는 Play Integrity 특성상 이 검증을 대체할 수 없다.

기능 브랜치는 검증 결과 보존을 위해 커밋·푸시할 수 있지만, 위 두 게이트를 통과하기 전에는 `develop`으로 병합하지 않는다.
