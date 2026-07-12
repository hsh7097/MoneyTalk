---
type: reference
title: MoneyTalk System Overview
description: MoneyTalk 앱의 현재 코드 구조와 핵심 기능 책임을 설명한다.
tags: [moneytalk, kb, architecture, overview]
resource: app/src/main/java/com/sanha/moneytalk/
timestamp: 2026-07-09T05:10:00+09:00
status: draft
---

# MoneyTalk System Overview

> 기준: 흡수된 프로젝트 컨텍스트/아키텍처 원문 내용을 KB용으로 재작성

MoneyTalk는 SMS/MMS/RCS 문자와 메시지 앱 알림에서 지출/수입 거래를 읽어 Room DB에 저장하고, Gemini 기반 AI 상담과 Android App Functions로 조회/수정 기능을 제공하는 Android 앱이다.

## 현재 앱 기준

| 항목 | 값 |
|---|---|
| Package | `com.sanha.moneytalk` |
| 언어/UI | Kotlin, Jetpack Compose, Material 3 |
| 구조 | MVVM, Hilt DI, Room DB |
| AI | Gemini 2.5 Flash Lite/Flash 중심 |
| Min SDK | 26 |
| DB | `moneytalk.db`, 현재 v8 |

## 코드 소유 경계

| 영역 | 책임 |
|---|---|
| `core/database` | Room entity, DAO, repository 일부, AI 크레딧/카드/SMS 제외 저장소 |
| `core/sms` | SMS/MMS/RCS 읽기 이후 파싱 파이프라인, 실시간 1건 처리, regex/remote rule/embedding/LLM 추출 |
| `core/sync` | 월별/증분 동기화 범위, coverage 기록, 월별 CTA 판단 |
| `core/similarity` | 유사도 임계값 판단 SSOT. `VectorSearchEngine`은 순수 연산만 담당 |
| `core/appfunctions` | Assistant/agent가 호출할 수 있는 앱 내부 조회/수정 함수 |
| `feature/home` | 홈 탭, 월간 현황, SMS 동기화 트리거, 카테고리/임베딩 repository |
| `feature/history` | 내역 탭, 달력/목록/필터 |
| `feature/chat` | AI 상담, 로컬 단순 조회, Gemini 3-step, Rolling Summary |
| `feature/settings` | 설정, 백업/복원, 카드/SMS/예산/AI 크레딧 메뉴 진입 |
| `receiver` | SMS broadcast, MMS/RCS observer, 메시지 앱 알림 기반 보완 수집 |

Feature 전용 `Activity`, `ViewModel`, 화면 모델은 `feature/<name>/ui` 또는 `ui/model`에 둔다. 여러 화면이 공유하는 저장소/서비스만 `data` 또는 `core`에 둔다.

## 핵심 시스템

| 시스템 | 현재 책임 | 상세 KB |
|---|---|---|
| SMS 파싱 | Regex Fast Path -> Vector -> Gemini LLM 배치 파싱 | [sms-pipeline](../sms-pipeline/README.md) |
| SMS 저장 흐름 | 동기화/실시간 수신 -> 중복 제거 -> 지출/수입 저장 -> 화면 갱신 | [sms-parsing](../sms-parsing/README.md) |
| 앱 알림 보완 | 메시지 앱 알림에서 금융 알림 후보를 추출하고 provider 원문으로 보강 | [notification-ingestion](../notification-ingestion/README.md), [notification-display](../notification-display/README.md) |
| 카테고리 분류 | StoreRule -> Room -> Vector -> Keyword -> Gemini Batch | [category-classification](../category-classification/README.md) |
| 필터링 | 화면 필터, SMS 제외 키워드, 카드 숨김, 통계 제외 플래그 | [filtering](../filtering/README.md) |
| AI 채팅 | Local Fast Path -> query/action 분석 -> DB 실행 -> 최종 답변 | [chat](../chat/README.md) |
| App Functions | 월간 요약, DB 조회/수정 함수 노출. 삭제성 함수는 기본 비활성 | [app-functions](../app-functions/README.md) |
| 예산/크레딧/광고 | AI 크레딧 차감/충전, 보상형 광고, release/RTDB gate | [budget-credit-monetization](../budget-credit-monetization/README.md) |
| 백업/복원 | 거래, 카테고리, 규칙, 예산, 카드, SMS 제외 키워드 병합 복원 | [backup-restore](../backup-restore/README.md) |

## DB 스키마 메모

현재 DB v8에는 지출/수입/예산/채팅/세션/카테고리 매핑/SMS 패턴/가게 임베딩/카드/SMS 제외/regex 룰/custom category/store rule/sync coverage/금융앱 후보/AI 크레딧 잔액/원장 계열 entity가 포함된다. 스키마 변경은 migration 추가가 필수이고, 문서만 보고 entity를 변경하지 않는다.

최근 migration 축:

| 버전 | 주요 변경 |
|---|---|
| v5 | `expenses.is_excluded_from_stats` |
| v6 | `store_rules.is_excluded_from_stats` |
| v7 | `financial_app_candidates` |
| v8 | `ai_credit_balance`, `ai_credit_ledger` |

## AI 운영 경계

- 프롬프트 템플릿은 `app/src/main/res/values/string_prompt.xml`에서 관리한다.
- 프롬프트 보조 문자열과 상태/라벨 문자열은 `app/src/main/res/values/strings.xml`의 `ai_*` 키가 담당한다.
- 운영 기본 모델은 `PremiumConfig.GeminiModelConfig`의 역할별 기본값(`gemini-3.1-flash-lite`, summary/regex의 `gemini-3.5-flash`)을 사용한다.
- Pro/preview 계열 검증은 Firebase RTDB `/config/models`에서 역할별 모델명을 override해 내부 테스트 범위로 제한한다.
- 모든 Gemini 호출은 `FirebaseAiModelFactory`의 Firebase AI Logic 경로를 사용하며 release는 Play Integrity App Check를 요구한다.
- 최종 답변 모델은 앱이 계산한 조회 결과와 ANALYTICS 결과만 인용해야 하며, 원본 거래 리스트를 직접 합산/평균/비율 계산하지 않는다.

## Golden Flow 요약

| 흐름 | 요약 |
|---|---|
| 배치 SMS 저장 | `MainViewModel.syncSmsV2` -> `SmsSyncRangeCalculator` -> `SmsSyncMessageReader` -> 중복/발신자 필터 -> `SmsSyncCoordinator` -> 지출/수입 저장 -> coverage 기록 |
| 실시간 SMS 저장 | `SmsReceiver`/observer/notification 보완 -> `SmsInstantProcessor` -> 중복 방지 -> 거래 저장 |
| 카테고리 확정 | StoreRule 우선 -> Room exact mapping -> vector auto/group -> local keyword -> Gemini batch -> mapping/embedding 저장 |
| 채팅 조회 | `ChatViewModel.sendMessage` -> Local Fast Path 또는 Gemini query analyzer -> `executeQuery`/`executeAction`/`executeAnalytics` -> final answer 저장 |
| 크레딧/광고 | release 빌드 + RTDB gate가 켜진 FREE 사용자만 크레딧 차감/충전 UI와 보상형 광고 흐름을 탄다 |
