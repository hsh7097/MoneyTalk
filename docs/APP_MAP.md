# MoneyTalk 앱 엔트리 포인트 맵

> 코드 기준 앱 시작 지점과 주요 화면 진입 흐름 정리
> 마지막 확인: 2026-04-03

## 1. 엔트리 포인트

### Application
- 클래스: `MoneyTalkApplication`
- 경로: [`MoneyTalkApplication.kt`](../app/src/main/java/com/sanha/moneytalk/MoneyTalkApplication.kt)
- 역할: `@HiltAndroidApp`로 DI 루트 초기화

### Launcher Activity
- 클래스: `IntroActivity`
- 경로: [`IntroActivity.kt`](../app/src/main/java/com/sanha/moneytalk/feature/intro/ui/IntroActivity.kt)
- Manifest 등록: `android.intent.action.MAIN` + `android.intent.category.LAUNCHER`
- Manifest 경로: [`AndroidManifest.xml`](../app/src/main/AndroidManifest.xml)

### Broadcast Receiver (백그라운드 진입점)
- 클래스: `SmsReceiver`
- 경로: [`SmsReceiver.kt`](../app/src/main/java/com/sanha/moneytalk/receiver/SmsReceiver.kt)
- Intent: `android.provider.Telephony.SMS_RECEIVED`
- 역할: SMS 수신 시 실시간 파싱 파이프라인 시작

### Notification Listener Service (백그라운드 진입점)
- 클래스: `NotificationTransactionService`
- 경로: [`NotificationTransactionService.kt`](../app/src/main/java/com/sanha/moneytalk/receiver/NotificationTransactionService.kt)
- Service action: `android.service.notification.NotificationListenerService`
- 역할: 메시지 앱 알림을 트리거로 최근 SMS/MMS/RCS provider 원본을 찾아 실시간 파싱 보완

## 2. 앱 시작 플로우

1. 앱 실행 시 `IntroActivity.onCreate()` 호출
2. 인트로에서 권한/RTDB 설정/강제 업데이트 조건을 확인
3. 초기 조건이 충족되면 `MainActivity`를 시작
4. `MainActivity`가 `setContent { MoneyTalkApp(...) }`로 Compose 루트를 구성
5. `NavGraph`의 기본 진입은 `Screen.Home` (`home`)

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
| Home (카테고리 롱클릭) | CategoryDetailActivity | `category={카테고리명}` | 카테고리별 월별 지출 추이 상세 화면 |

## 6. NavGraph 외부 Activity

NavGraph에 포함되지 않고 `Intent`로 직접 시작하는 Activity:

| Activity | 경로 | 진입 방식 | 설명 |
|----------|------|----------|------|
| `CategoryDetailActivity` | [`CategoryDetailActivity.kt`](../app/src/main/java/com/sanha/moneytalk/feature/categorydetail/ui/CategoryDetailActivity.kt) | `HomeScreen` 카테고리 롱클릭 → `Intent(EXTRA_CATEGORY)` | 카테고리별 월별 지출 추이 차트 + 월별 리스트 |
| `TransactionEditActivity` | [`TransactionEditActivity.kt`](../app/src/main/java/com/sanha/moneytalk/feature/transactionedit/ui/TransactionEditActivity.kt) | Home/History/CategoryDetail/TransactionList → `Intent` | 지출/수입/이체 편집 및 신규 추가 |
| `TransactionDetailListActivity` | [`TransactionDetailListActivity.kt`](../app/src/main/java/com/sanha/moneytalk/feature/transactionlist/ui/TransactionDetailListActivity.kt) | `HistoryCalendar` 날짜 클릭 → `Intent(EXTRA_DATE)` | 특정 날짜의 거래 목록 |
| `SmsSettingsActivity` | [`SmsSettingsActivity.kt`](../app/src/main/java/com/sanha/moneytalk/feature/smssettings/ui/SmsSettingsActivity.kt) | `SettingsScreen` "문자 설정" 클릭 → `Intent` | 문자분석 업데이트 + 수신거부 문구/번호 관리 |
| `CategorySettingsActivity` | [`CategorySettingsActivity.kt`](../app/src/main/java/com/sanha/moneytalk/feature/categorysettings/ui/CategorySettingsActivity.kt) | `SettingsScreen` "카테고리 설정" 클릭 → `Intent` | 커스텀 카테고리 추가/삭제 |
| `StoreRuleSettingsActivity` | [`StoreRuleSettingsActivity.kt`](../app/src/main/java/com/sanha/moneytalk/feature/storerulesettings/ui/StoreRuleSettingsActivity.kt) | `SettingsScreen` "거래처 규칙" 클릭 → `Intent` | 거래처 규칙 추가/편집/삭제 |
