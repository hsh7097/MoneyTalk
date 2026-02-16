# MoneyTalk 앱 엔트리 포인트 맵

> 코드 기준 앱 시작 지점과 주요 화면 진입 흐름 정리
> 마지막 확인: 2026-02-15

## 1. 엔트리 포인트

### Application
- 클래스: `MoneyTalkApplication`
- 경로: [`MoneyTalkApplication.kt`](../app/src/main/java/com/sanha/moneytalk/MoneyTalkApplication.kt)
- 역할: `@HiltAndroidApp`로 DI 루트 초기화

### Launcher Activity
- 클래스: `MainActivity`
- 경로: [`MainActivity.kt`](../app/src/main/java/com/sanha/moneytalk/MainActivity.kt)
- Manifest 등록: `android.intent.action.MAIN` + `android.intent.category.LAUNCHER`
- Manifest 경로: [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml)

### Broadcast Receiver (백그라운드 진입점)
- 클래스: `SmsReceiver`
- 경로: [`SmsReceiver.kt`](../app/src/main/java/com/sanha/moneytalk/receiver/SmsReceiver.kt)
- Intent: `android.provider.Telephony.SMS_RECEIVED`
- 역할: SMS 수신 시 실시간 파싱 파이프라인 시작

## 2. 앱 시작 플로우

1. 앱 실행 시 `MainActivity.onCreate()` 호출
2. `checkInitialPermissions()`에서 SMS 권한 확인/요청
3. `setContent { MoneyTalkApp(...) }`로 Compose 루트 구성
4. `NavGraph` 시작 목적지:
   - 기본: `Screen.Splash` (`splash`)
   - 스플래시 종료 후 `Screen.Home` (`home`)로 이동

## 3. 주요 화면 라우트 맵

| Route | Screen 객체 | 진입 위치 | 화면 파일 |
|---|---|---|---|
| `splash` | `Screen.Splash` | 앱 시작 직후 | [`SplashScreen.kt`](../app/src/main/java/com/sanha/moneytalk/feature/splash/ui/SplashScreen.kt) |
| `home` | `Screen.Home` | 스플래시 완료 후 기본 진입 | [`HomeScreen.kt`](../app/src/main/java/com/sanha/moneytalk/feature/home/ui/HomeScreen.kt) |
| `history?category={category}` | `Screen.History` | 하단 탭 / 홈 카테고리 클릭 | [`HistoryScreen.kt`](../app/src/main/java/com/sanha/moneytalk/feature/history/ui/HistoryScreen.kt) |
| `chat` | `Screen.Chat` | 하단 탭 | [`ChatScreen.kt`](../app/src/main/java/com/sanha/moneytalk/feature/chat/ui/ChatScreen.kt) |
| `settings` | `Screen.Settings` | 하단 탭 | [`SettingsScreen.kt`](../app/src/main/java/com/sanha/moneytalk/feature/settings/ui/SettingsScreen.kt) |

라우트 정의 파일:
- [`Screen.kt`](../app/src/main/java/com/sanha/moneytalk/navigation/Screen.kt)
- [`NavGraph.kt`](../app/src/main/java/com/sanha/moneytalk/navigation/NavGraph.kt)

## 4. 하단 네비게이션 구성

`MainActivity`의 `MoneyTalkApp()`에서 `bottomNavItems`를 렌더링하며 아래 탭을 노출:
- 홈 (`home`)
- 내역 (`history`)
- 상담 (`chat`)
- 설정 (`settings`)

탭 정의 파일:
- [`BottomNavItem.kt`](../app/src/main/java/com/sanha/moneytalk/navigation/BottomNavItem.kt)

## 5. 화면 간 딥링크

| 출발 | 도착 | 파라미터 | 설명 |
|------|------|---------|------|
| Home (카테고리 클릭) | History | `category={카테고리명}` | 해당 카테고리 필터가 적용된 내역 화면으로 이동 |
