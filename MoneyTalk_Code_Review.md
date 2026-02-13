# MoneyTalk 코드 리뷰 리포트

> 리뷰 일시: 2026-02-14
> 범위: 전체 프로젝트 (74개 .kt 파일) — Compose Stability, Recomposition, ViewModel, 코드 품질

---

## 종합 평가: 7/10

바이브 코딩 치고는 아키텍처가 잘 잡혀 있습니다. MVVM + Hilt + Room + StateFlow 패턴이 일관되고, 유사도 정책 분리(Vector → Policy → Service)도 깔끔합니다. 다만 Compose 최적화와 에러 핸들링 부분에서 실무 수준의 보완이 필요합니다.

---

## 1. Compose Stability 이슈 (CRITICAL)

### 문제: 모든 UiState에 `@Stable` / `@Immutable` 미적용 + 불안정 컬렉션 사용

Compose 컴파일러는 `List`, `Map` 등 kotlin.collections 타입을 **unstable**로 판정합니다. 현재 모든 UiState가 이 패턴입니다:

```kotlin
// 현재 코드 — 모든 List/Map이 unstable
data class HomeUiState(
    val categoryExpenses: List<CategorySum> = emptyList(),
    val recentExpenses: List<ExpenseEntity> = emptyList(),
    val todayExpenses: List<ExpenseEntity> = emptyList(),
    // ...
)
```

**영향받는 클래스:**

| UiState | 불안정 필드 수 | 영향 |
|---------|-------------|------|
| HomeUiState | 4개 (List) | 홈 화면 전체 리컴포지션 |
| HistoryUiState | 4개 (List + Map) | 내역 화면 전체 리컴포지션 |
| ChatUiState | 2개 (List) | 채팅 메시지/세션 리컴포지션 |
| SettingsUiState | 5개 (List) | 설정 화면 전체 리컴포지션 |

### 수정 방안 (2가지 중 택 1)

**방안 A: `@Stable` 어노테이션 (간단, 권장)**
```kotlin
@Stable
data class HomeUiState(
    val categoryExpenses: List<CategorySum> = emptyList(),
    // ...
)
```
- StateFlow + data class copy() 패턴에서는 참조가 바뀔 때만 emit되므로 `@Stable`이 적합
- Compose 컴파일러에게 "이 클래스의 equals()를 신뢰해라"라고 알려줌

**방안 B: `kotlinx-collections-immutable` 도입 (확실, 비용 있음)**
```kotlin
// build.gradle
implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")

// 사용
data class HomeUiState(
    val categoryExpenses: ImmutableList<CategorySum> = persistentListOf(),
    // ...
)
```
- 근본적 해결이지만 `.toImmutableList()` 변환 비용 + 마이그레이션 범위가 큼

**추천**: 방안 A를 먼저 적용하고, 프로파일링 후 핫스팟에만 B 적용

---

## 2. Recomposition 최적화 이슈

### 2-1. `collectAsState()` → `collectAsStateWithLifecycle()` (CRITICAL)

모든 Screen에서 lifecycle-unaware 수집을 사용 중:

```kotlin
// 현재 — 백그라운드에서도 계속 수집
val uiState by viewModel.uiState.collectAsState()

// 수정 — STOPPED 상태에서 수집 중단
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

| 파일 | 라인 |
|------|------|
| HomeScreen.kt | ~103 |
| HistoryScreen.kt | ~60 |
| ChatScreen.kt | ~56 |
| SettingsScreen.kt | ~81 |

**의존성 추가 필요:**
```gradle
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
```

### 2-2. ChatScreen LazyColumn에 `key` 미지정 (CRITICAL)

```kotlin
// 현재 — key 없음, 메시지 추가/삭제 시 전체 리컴포지션
items(uiState.messages) { message -> ChatBubble(...) }

// 수정
items(uiState.messages, key = { it.id }) { message -> ChatBubble(...) }
```

### 2-3. Callback 람다 remember 누락 (HIGH)

HomeScreen에서 여러 콜백이 매 리컴포지션마다 새 인스턴스 생성:

```kotlin
// 현재
onPreviousMonth = { viewModel.previousMonth() },
onNextMonth = { viewModel.nextMonth() },

// 수정 — remember로 래핑하거나 메서드 참조 사용
onPreviousMonth = remember { { viewModel.previousMonth() } },
// 또는
onPreviousMonth = viewModel::previousMonth,
```

### 2-4. NumberFormat 매 리컴포지션마다 생성 (MEDIUM)

```kotlin
// TransactionCardCompose.kt, TransactionGroupHeaderCompose.kt
val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA) // 비싼 객체

// 수정
val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
```

---

## 3. ViewModel 및 코루틴 이슈

### 3-1. Mutex 데드락 위험 (CRITICAL)

```kotlin
// ChatViewModel.kt
private val sendMutex = Mutex()

// 현재 — 타임아웃 없음, 내부에서 크래시 나면 영구 잠김
sendMutex.withLock { ... }

// 수정
withTimeoutOrNull(30_000L) {
    sendMutex.withLock { ... }
} ?: run {
    _uiState.update { it.copy(isLoading = false) }
    snackbarBus.show("요청 시간 초과")
}
```

### 3-2. Non-null assertion (!!) 사용 (CRITICAL)

CLAUDE.md에서 `!!` 금지 규칙이 있지만 실제 코드에 존재:

```kotlin
// HomeViewModel.kt ~line 619, 993
val analysis = result.analysisResult!!

// 수정
val analysis = result.analysisResult ?: return@launch
```

### 3-3. 비원자적 상태 갱신 (MODERATE)

```kotlin
// HomeViewModel.kt
_uiState.update { it.copy(selectedYear = newYear, selectedMonth = newMonth) }
lastInsightInputHash = 0  // _uiState와 별개로 갱신 → race condition

// 수정: UiState에 통합하거나 AtomicInteger 사용
```

### 3-4. 에러 로그에 스택트레이스 누락 (MODERATE)

```kotlin
// 현재
Log.e(TAG, "백그라운드 재분류 실패: ${e.message}")

// 수정 — 스택트레이스 포함
Log.e(TAG, "백그라운드 재분류 실패", e)
```

---

## 4. DI / 인프라 이슈

### 4-1. `fallbackToDestructiveMigration()` 프로덕션 빌드에서 위험 (CRITICAL)

```kotlin
// DatabaseModule.kt
Room.databaseBuilder(...)
    .fallbackToDestructiveMigration()  // 스키마 불일치 시 데이터 전부 삭제!

// 수정
.apply {
    if (BuildConfig.DEBUG) {
        fallbackToDestructiveMigration()
    }
}
```

사용자의 지출 데이터가 날아갈 수 있는 치명적 이슈입니다.

### 4-2. HistoryViewModel에 Context 저장 (MODERATE)

```kotlin
// 현재
class HistoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context  // ViewModel에 Context 보관
)

// 개선: ApplicationContext는 leak은 아니지만, 문자열 리소스는
// StringResourceProvider 인터페이스로 분리하는 것이 테스터블
```

---

## 5. 프로덕션 로깅 (LOW but 중요)

```kotlin
// 곳곳에 디버그용 E-level 로그
android.util.Log.e("sanha", "=== syncSmsMessages 시작 ===")
```

릴리스 빌드에서 `Log.e`가 logcat에 노출됩니다. Timber + release tree 또는 `BuildConfig.DEBUG` 가드를 추가하세요.

---

## 6. 기타 코드 스멜

| 이슈 | 위치 | 심각도 |
|------|------|--------|
| ChatViewModel.kt ~1670줄 (God Object) | feature/chat/ui/ | MODERATE |
| Result/Exception 핸들링 패턴 불일치 | 프로젝트 전반 | MODERATE |
| 매직 넘버 (500, 200, 100, 50 등) | HomeViewModel 진행률 계산 | LOW |
| fire-and-forget 코루틴 (자동 타이틀 생성 등) | ChatViewModel | LOW |

---

## 우선순위별 수정 권장

### P0 — 즉시 수정 (데이터 손실/크래시 위험)
1. `fallbackToDestructiveMigration()` 제거 (릴리스)
2. `!!` non-null assertion 제거
3. Mutex 타임아웃 추가

### P1 — 성능 영향 (리컴포지션 폭발)
4. 모든 UiState에 `@Stable` 추가
5. `collectAsState()` → `collectAsStateWithLifecycle()`
6. ChatScreen LazyColumn `key` 추가
7. NumberFormat `remember` 처리

### P2 — 품질 개선
8. 콜백 람다 remember/메서드 참조
9. 비원자적 상태 갱신 수정
10. 에러 로그 스택트레이스 추가
11. 프로덕션 로깅 정리

### P3 — 리팩토링
12. ChatViewModel 분리 (query/action 로직)
13. StringResourceProvider 도입
14. Result 패턴 통일
