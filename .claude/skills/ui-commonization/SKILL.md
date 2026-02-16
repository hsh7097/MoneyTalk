---
name: 공통화
description: UI-도메인 공통화 작업을 수행한다. Contract/Factory/Mapper/Renderer/Intent 패턴 기반 Compose 공통 컴포넌트 설계 및 구현.
argument-hint: "[컴포넌트명 또는 작업 내용]"
---

# UI-도메인 공통화 스킬

공통화 작업 요청 시 아래 가이드를 따른다.
상세 가이드: `~/.claude/guides/ui-domain-commonization.md`

---

## 1. 핵심 아키텍처

```text
[Domain Raw Model]
      |
      v
[Factory/Mapper.from(...)]   // 도메인 모듈
      |
      v
[UI Contract Model]          // 공통 UI 모듈
      |
      v
[Renderer(View/Compose)]     // 공통 UI 모듈
      |
      v
[Intent -> Upper Layer Side Effect]
```

---

## 2. 역할 정의

| 구성 | 역할 | 위치 | 금지 |
|------|------|------|------|
| **Contract** (Info/Data) | UI 렌더링 최소 속성 + 정책 API | 공통 UI | 도메인 모델 참조, 비즈니스 규칙 계산 |
| **Factory/Mapper** | 도메인→Contract 변환, 비즈니스 규칙을 값으로 확정 | 도메인 | UI 위젯 제어, Compose API, State 보관 |
| **Renderer** (Composable) | Contract→화면 표현 | 공통 UI | 도메인 필드 직접 해석, side-effect 실행 |
| **Intent** | 사용자 액션 → 순수 이벤트 전달 | 공통 UI | 자체적 상태 확정, 비즈니스 처리 |
| **State** | UI 변화값 보관 | ViewHolder/remember | 불변 입력값 보관, 도메인 원본 보관 |
| **Params** | 불변 입력값 전달 | 도메인 | 내부 값 변경 (그건 State) |

---

## 3. 두 가지 패턴

### 3.1 조합형 (Composite Contract)
- **적용**: 한 UI 블록이 여러 하위 블록을 **동시에** 합성할 때
- 하위 컴포넌트가 **공존** (모두 렌더링)
- State/Intent 없음 (stateless)

```kotlin
interface CompositeData {
    val sectionA: SectionAData?
    val sectionB: SectionBData?
    fun isNullOrEmpty(): Boolean
    fun createDescriptionText(): String
}
```

### 3.2 타입분기형 (Polymorphic Contract)
- **적용**: 같은 자리에서 타입별로 **하나만** 렌더링할 때
- State + Intent 있음
- 타입 분기 지점: **Factory + Router 2곳으로 제한**
- Router만 public, 하위 Composable은 private

```kotlin
sealed interface ActionData {
    data class Landing(val url: String?) : ActionData
    data class Refresh(val totalPageCount: Int) : ActionData
}

@Composable
fun ActionRouter(data: ActionData, state: ActionState, onAction: (ActionIntent) -> Unit) {
    when (data) {
        is ActionData.Landing -> LandingCompose(data, onAction)
        is ActionData.Refresh -> RefreshCompose(data, state, onAction)
    }
}
```

### 패턴 선택 기준

| 조건 | 패턴 |
|------|------|
| 하위 컴포넌트 동시 표시 | 조합형 |
| 하위 컴포넌트 중 하나만 선택 | 타입분기형 |
| 사용자 인터랙션 없음 | 조합형 (stateless) |
| 사용자 인터랙션 있음 | 타입분기형 (State + Intent) |

---

## 4. data class vs interface 판단

```text
Q: Contract 프로퍼티가 모든 도메인에서 동일한가?
|
+-- YES --> data class (Factory에서 값만 주입)
|
+-- NO  --> Q: 도메인별 레이아웃 정책이나 표현 다형성이 필요한가?
            |
            +-- YES --> interface (도메인이 override, 구현체는 data class)
            +-- NO  --> data class (Factory에서 변환 후 주입)
```

---

## 5. 경계 위반 금지

```kotlin
// ❌ Contract에서 비즈니스 규칙 계산
interface PriceData {
    val finalPrice: Long
        get() = rawPrice * (100 - discountRate) / 100
}

// ✅ Factory에서 계산 후 값만 전달
data class PriceUiModel(
    val discountRateText: String?,
    val finalPriceText: String,
)
```

```kotlin
// ❌ Renderer에서 도메인 의미 해석
if (data.infoLabels?.any { it.type == "SOLD_OUT" } == true) { ... }

// ✅ Renderer는 Contract가 제공한 값만 사용
data.infoLabels?.forEach { InfoLabelCompose(it) }
```

```kotlin
// ❌ Factory에서 UI 간격 결정
fun from(...) = Data(topPadding = 4.dp)

// ✅ Factory는 데이터 정책만 결정
fun from(...) = Data(maxLines = if (scale == LARGE) 1 else 2)
```

---

## 6. State 생명주기

```text
ViewHolder 생성 → remember { State() }
    ↓
bind(item) → 아이템 key 변경 시 state.resetForContextChange()
    ↓
사용자 인터랙션 → state.advancePage() / state.updateExpanded()
    ↓
ViewHolder 재활용 → bind(다른 item) → resetForContextChange()
```

- State는 ViewHolder(또는 `remember`)에서 생성 — Factory에서 생성하지 않음
- 아이템 key 변경 시 반드시 `resetForContextChange()` 호출
- Params(불변 입력)과 State(변화값)를 절대 섞지 않기

---

## 7. 팀 공통 룰

1. 공통 UI 모듈에서 도메인 모델 import 금지
2. 도메인 → Contract 변환은 `from(...)`/`Factory`로 단일화
3. Renderer에는 비즈니스 분기/데이터 가공 금지
4. Params(입력)와 State(변화값) 분리
5. 클릭 처리 side-effect는 상위 레이어에서만 실행
6. 접근성 설명문 생성 책임은 Contract에 두기
7. 타입 분기는 Factory + Router 2단으로 제한
8. Router 하위 Composable은 private

---

## 8. 코드 리뷰 체크리스트

- [ ] UI 모듈이 도메인 모델을 직접 참조하지 않는가?
- [ ] 변환 로직이 Factory/Mapper 한곳으로 수렴되는가?
- [ ] Renderer가 Contract만 다루는가?
- [ ] Params/State/Intent 역할이 분리되어 있는가?
- [ ] 타입 분기 지점이 Factory/Router로 제한되어 있는가?
- [ ] A11y 문장이 Contract 기반으로 일관 생성되는가?
- [ ] Contract 수정 시 기본값(default)을 제공하여 하위 호환을 유지하는가?
- [ ] State는 ViewHolder에서 관리되고, bind 시 reset되는가?

---

## 9. 구현 형태 요약

| 조건 | UI 위치 | Mapper 위치 | Mapper 방식 |
|------|---------|-------------|-------------|
| 단일 도메인 + 상태 있음 | 도메인 내부 | 도메인 내부 | Factory 함수 또는 sealed class |
| 다중 도메인 + 상태 없음 | 공통 UI | 각 도메인 | data class Factory + Interface |
| 다중 도메인 + 상태 있음 | 공통 UI | 각 도메인 | data class Factory + Interface + 도메인별 State |
| 복합 컴포넌트 | 공통 UI | 각 도메인 | data class (from 팩토리) + 하위 sealed class |

---

## 10. 네이밍 규칙

| 구성 요소 | 패턴 | 예시 |
|----------|------|------|
| Interface | `{기능}Info`, `{기능}Data` | `PriceWithCouponInfo` |
| Composable | `{기능}Compose` | `PriceWithCouponCompose` |
| State | `{기능}State` | `HomeActionState` |
| Intent | `{기능}Intent` | `HomeActionIntent` |
| Mapper | `{도메인}{기능}InfoSealed` | `HomePriceWithCouponInfoSealedV2` |
| Factory | `{도메인}{기능}Factory` | `HomePriceWithCouponInfoFactory` |

---

## 11. Preview 규칙

- Preview 파일은 **debug 소스셋**에 동일 패키지 경로로 배치
- 테스트 데이터 + PreviewParameterProvider + Preview 함수를 한 파일에
- Preview용 테스트 데이터는 private으로 선언

---

## 12. 안티패턴 → 교정

| 안티패턴 | 교정 |
|----------|------|
| Renderer에서 도메인 필드 직접 참조 | Contract로 변환 후 전달 |
| 클릭 핸들러에서 바로 네트워크/화면이동 | Intent만 전달, 상위에서 side-effect |
| 호출부마다 임시 매핑 반복 | Factory/Mapper 단일 진입점 |
| totalCount + selectedPage + expanded 혼합 | Params/State 분리 |
| 프로퍼티 동일한데 interface 사용 | data class + Factory로 전환 |
| Renderer 내부에서 when(data)로 재분기 | Router에서만 분기, 하위는 private |
| Contract에서 가격 할인율 계산 | Factory에서 계산, Contract는 결과값만 |
| Factory에서 UI 간격(dp) 결정 | 간격은 Renderer 책임, Factory는 데이터 정책만 |
