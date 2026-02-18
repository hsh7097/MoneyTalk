---
name: docs
description: 작업 전 docs/ 문서를 확인하여 컨텍스트를 파악하고, 작업 후 변경 사항에 맞춰 전체 docs/를 갱신한다.
argument-hint: "[before|after] (선택, 생략 시 both)"
---

# Docs Sync 스킬

> 작업 전후로 `docs/` 문서를 확인·갱신하는 통합 스킬

---

## 사용법

| 호출 | 동작 |
|------|------|
| `/docs` | before + after 순차 실행 |
| `/docs before` | 작업 전 문서 확인만 |
| `/docs after` | 작업 후 문서 갱신만 |

---

## Phase 1: Before (작업 전 문서 확인)

작업 시작 전 아래 문서를 **Read**하여 현재 상태를 파악한다.

### 필수 확인 문서

| 우선순위 | 문서 | 확인 목적 |
|---------|------|----------|
| 1 | `docs/AI_HANDOFF.md` | 이전 세션의 작업 상태, 미완료 작업, 주의사항 |
| 2 | `docs/AI_TASKS.md` | 전체 태스크 목록, 현재 Phase, 미완료 항목 |
| 3 | `docs/AI_CONTEXT.md` | 아키텍처, 임계값 레지스트리, Golden Flows |

### 상황별 추가 확인

| 작업 유형 | 추가 확인 문서 |
|----------|--------------|
| UI/Compose 작업 | `docs/COMPOSABLE_MAP.md` |
| SMS 파싱 관련 | `docs/SMS_PARSING.md` |
| 카테고리 분류 관련 | `docs/CATEGORY_CLASSIFICATION.md` |
| 채팅 시스템 관련 | `docs/CHAT_SYSTEM.md` |
| 네비게이션/라우팅 관련 | `docs/APP_MAP.md` |
| 패키지 구조 변경 | `docs/ARCHITECTURE.md` |

### Before 완료 시 출력

```
📋 문서 확인 완료
- 현재 상태: {AI_HANDOFF 요약}
- 진행 중 태스크: {AI_TASKS에서 미완료 항목}
- 주의사항: {있으면 표시}
```

---

## Phase 2: After (작업 후 문서 갱신)

작업 완료 후 아래 기준에 따라 **변경 내용에 해당하는 문서만** 갱신한다.

### 갱신 판단 기준

```bash
git diff --stat   # 변경된 파일 목록 확인
git log --oneline -5   # 최근 커밋 확인
```

### 문서별 갱신 규칙

| 문서 | 갱신 조건 | 갱신 내용 |
|------|----------|----------|
| **AI_HANDOFF.md** | **항상** | 현재 브랜치, 마지막 커밋, 진행 중 작업, 주의사항 |
| **DEVELOPMENT_LOG.md** | **항상** | 오늘 날짜 섹션에 작업 내역 추가 (이미 있으면 append) |
| **AI_TASKS.md** | 태스크 완료/추가 시 | 체크박스 업데이트, 완료 날짜 기입, 새 태스크 추가 |
| **CHANGELOG.md** | 기능 추가/버그 수정 시 | Unreleased 섹션에 Added/Changed/Fixed 항목 추가 |
| **AI_CONTEXT.md** | 아키텍처/임계값 변경 시 | 변경된 구조, 새 패턴, 임계값 수치 갱신 |
| **ARCHITECTURE.md** | 패키지/파일 구조 변경 시 | 파일 트리, 모듈 설명 갱신 |
| **COMPOSABLE_MAP.md** | Composable 추가/삭제/변경 시 | 함수 트리 + 테이블 갱신 (CLAUDE.md 규칙 #7 필수) |
| **APP_MAP.md** | 네비게이션/진입점 변경 시 | 라우팅 구조 갱신 |
| **SMS_PARSING.md** | SMS 파싱 로직 변경 시 | 파싱 규칙, 분류 기준 갱신 |
| **CATEGORY_CLASSIFICATION.md** | 카테고리 분류 변경 시 | 분류 로직, 매핑 갱신 |
| **CHAT_SYSTEM.md** | 채팅 시스템 변경 시 | 쿼리/액션, 프롬프트 구조 갱신 |
| **GIT_CONVENTION.md** | Git 규칙 변경 시 | 컨벤션 갱신 |
| **QUICK_START.md** | 환경 설정 변경 시 | 설정 가이드 갱신 |
| **PROJECT_CONTEXT.md** | 프로젝트 개요 변경 시 | 전체 개요 갱신 |

### 갱신 원칙

1. **변경 없는 문서는 건드리지 않는다** — 불필요한 날짜만 업데이트하는 등의 noise 금지
2. **기존 형식을 유지한다** — 각 문서의 기존 헤더/구조/스타일을 따름
3. **사실만 기록한다** — 추측이나 계획이 아닌 실제 변경 사항만 반영
4. **마지막 업데이트 날짜**를 갱신한다 (문서 하단에 있는 경우)

### After 완료 시 출력

```
📝 문서 갱신 완료
- 갱신: {갱신된 문서 목록}
- 스킵: {변경 없어서 스킵한 문서 사유}
```

---

## 전체 흐름 (/docs)

```
1. [Before] 핵심 문서 3개 Read → 컨텍스트 파악 → 요약 출력
2. 사용자에게 "확인 완료, 작업을 진행하세요" 안내
3. ... (사용자가 작업 수행) ...
4. 사용자가 다시 /docs after 또는 /docs 호출
5. [After] git diff 기반으로 변경 범위 파악 → 해당 문서만 갱신 → 결과 출력
```

> **주의**: After 실행 시 갱신된 문서 파일은 자동 커밋하지 않는다.
> 커밋이 필요하면 `/커밋` 스킬을 별도로 호출한다.
