---
title: "MoneyTalk 개발·실행 기준"
status: verified
last_checked: "2026-09-30"
source_ref: "95b419e8b98e374a991f3210a194a88959937ee8"
---

# MoneyTalk 개발·실행 기준

## 범위

프로젝트 root에서 사용하는 개발 명령과 이 checkout의 환경 한계를 설명한다. 인증정보 생성·등록, 배포, 실제 사용자 데이터 입력은 수행하지 않았다. 저장소 README의 오래된 SDK/API 키 예시보다 아래 코드 기준을 우선한다.

## 빌드와 설정 — 사실

| 근거 | 현재 코드 설정 |
|---|---|
| [앱 빌드](../../../../app/build.gradle.kts) · `android` | `compileSdk=36`, `targetSdk=36`, `minSdk=26`; Java/Kotlin bytecode target 17; 앱 ID `com.sanha.moneytalk`; versionCode 23, versionName 1.0.5 |
| [Gradle wrapper](../../../../gradle/wrapper/gradle-wrapper.properties) · `distributionUrl` | Gradle 8.11.1 |
| [version catalog](../../../../gradle/libs.versions.toml) · `agp`/`kotlin`/`ksp` | AGP 8.10.1, Kotlin 2.1.20, KSP 2.1.20-1.0.32; 의존성 버전 변경 시 plugin과 compiler 호환성 별도 확인 |
| [앱 빌드](../../../../app/build.gradle.kts) · Firebase plugin 조건 | `app/google-services.json`이 존재할 때만 Google Services/Crashlytics 플러그인 적용. 파일 부재와 일반 컴파일 실패를 같은 것으로 단정하지 않음 |
| [앱 빌드](../../../../app/build.gradle.kts) · `localProperties`/`signingConfigs` | `local.properties`의 release 서명 설정 이름: `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. 값·서명 파일은 KB에 포함하지 않음 |
| [BuildVariantPolicy.kt](../../../../app/src/main/java/com/sanha/moneytalk/core/util/BuildVariantPolicy.kt) | `moneytalk.monetizationTestOverride` Gradle property → `BuildConfig.MONETIZATION_TEST_OVERRIDE`; release 또는 override에서 monetization 허용, 실제 기능은 추가 gate 적용 |

SDK 위치는 개발 환경의 `ANDROID_HOME`/`ANDROID_SDK_ROOT` 또는 `local.properties`의 `sdk.dir`로 준비한다. `CLAUDE_API_KEY`는 기존 [local.properties.example](../../../../local.properties.example)에 남은 이름이다. 현재 Gemini 모델 생성은 Firebase AI Logic 경로이므로 이 이름을 입력했다고 AI 연결이 확인되는 것은 아니다.

### 개발 환경에서 실행할 명령

아래 명령은 프로젝트 root 기준이다. SDK 36과 쓰기 가능한 Gradle cache, 필요한 의존성 및 Android 개발 환경을 먼저 준비한다.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:installDebug
adb shell am start -n com.sanha.moneytalk/.feature.intro.ui.IntroActivity
```

`connectedDebugAndroidTest`, 설치·시작 명령에는 연결된 기기/에뮬레이터가 필요하다. SMS provider/방송/알림 보완은 실제 권한과 지원 채널에서 확인한다. 권한 거절 시 Intro가 Main으로 이동할 수 있다는 사실을 SMS 동기화 성공으로 해석하지 않는다.

### 변경별 대표 테스트 위치

파일 존재와 테스트 대상만 확인했다. 아래 Android/Kotlin 테스트는 이 환경에서 실행하지 않았다.

| 변경 | 대표 근거 |
|---|---|
| 로컬 채팅/크레딧 gate | [LocalChatQueryRouterTest.kt](../../../../app/src/test/java/com/sanha/moneytalk/core/util/LocalChatQueryRouterTest.kt), [ChatCreditPolicyTest.kt](../../../../app/src/test/java/com/sanha/moneytalk/core/util/ChatCreditPolicyTest.kt), [CreditFeaturePolicyTest.kt](../../../../app/src/test/java/com/sanha/moneytalk/core/ad/CreditFeaturePolicyTest.kt) |
| 분석·수입 맥락·메시지 관찰 | [ChatAnalyticsCalculatorTest.kt](../../../../app/src/test/java/com/sanha/moneytalk/feature/chat/data/ChatAnalyticsCalculatorTest.kt), [ChatIncomeContextPolicyTest.kt](../../../../app/src/test/java/com/sanha/moneytalk/feature/chat/data/ChatIncomeContextPolicyTest.kt), [ChatMessageObserverTest.kt](../../../../app/src/test/java/com/sanha/moneytalk/feature/chat/data/ChatMessageObserverTest.kt) |
| SMS 저장·삭제 추적 | [ExpenseIngestionInstrumentedTest.kt](../../../../app/src/androidTest/java/com/sanha/moneytalk/core/database/ExpenseIngestionInstrumentedTest.kt), [TransactionSourceDeletionInstrumentedTest.kt](../../../../app/src/androidTest/java/com/sanha/moneytalk/core/sms/TransactionSourceDeletionInstrumentedTest.kt); 배치·분류 추가 위치는 [데이터 흐름](data-flows.md) |
| App Functions | [MoneyTalkAppFunctionExecutionTest.kt](../../../../app/src/androidTest/java/com/sanha/moneytalk/core/appfunctions/MoneyTalkAppFunctionExecutionTest.kt) |

## 현재 환경 관측·미검증

- **사실:** 2026-09-30 파일 존재 검사에서 `app/google-services.json`, `app/src/debug/google-services.json`, `app/src/release/google-services.json`, `local.properties`가 없었다. 내용이나 인증값을 조회하지 않았다.
- **사실:** shell의 Java는 OpenJDK 21이었다. `gradle`, `kotlinc`, `adb`, `emulator` 실행 파일은 없고 `ANDROID_HOME`/`ANDROID_SDK_ROOT`는 설정된 유효 SDK를 가리키지 않았다. Gradle wrapper jar는 있으나 다운로드된 Gradle 배포본 cache는 없었다.
- **실행 실패:** `./gradlew --offline :app:assembleDebug :app:testDebugUnitTest`는 기본 Gradle cache의 lock parent 디렉터리를 만들지 못해 exit 1로 끝났다. wrapper 단계이며 컴파일·앱 테스트까지 도달하지 않았다. 문서만 변경했으며 SDK/인증 설치나 다운로드 재시도는 수행하지 않았다.
- **미검증:** RTDB 설정 수신, Firebase AI 응답, App Check, 광고 지급, 기기 SMS/MMS/RCS 수신, migration, Compose 화면 동작. 설정 파일이 없다는 관측만으로 모든 로컬 기능이 실패한다고 주장하지 않는다.

## 근거

위 빌드 파일과 설정 이름, Manifest의 launcher, `MoneyTalkApplication.initializeFirebase`를 실제 코드 기준으로 확인했다. [FirebaseAiModelFactory.kt](../../../../app/src/main/java/com/sanha/moneytalk/core/firebase/FirebaseAiModelFactory.kt)의 `create`가 모델 생성 경로다. [debug AppCheckInstaller.kt](../../../../app/src/debug/java/com/sanha/moneytalk/core/firebase/AppCheckInstaller.kt)와 [release AppCheckInstaller.kt](../../../../app/src/release/java/com/sanha/moneytalk/core/firebase/AppCheckInstaller.kt)는 서로 다른 provider를 설치한다. provider 코드 존재와 토큰 발급 성공은 구분한다.

프로젝트 및 workspace의 `.agents/skills`는 조사 시 없었다. 적용한 로컬 지침은 [AGENTS.md](../../../../AGENTS.md), 프로젝트 KB 스캐폴드 README/작성절차, 함께 checkout된 ClaudeGuide의 기준 작성 계약이다.

## 검증

명령·파일 경로와 코드 설정은 정적 대조 대상이다. 수행한 문서 검증과 실패/미실행 결과는 [검증 기록](validation.md)에 분리해서 남긴다. 추가 개발 검증은 위 명령을 SDK·기기·Firebase 설정이 준비된 환경에서 다시 실행한다.
