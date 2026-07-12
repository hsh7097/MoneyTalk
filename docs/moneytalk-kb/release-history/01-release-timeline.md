---
type: reference
title: Release Timeline
description: MoneyTalk 주요 릴리스/변경 축을 기능 KB 관점으로 요약한다.
tags: [moneytalk, release, changelog, history]
resource: docs/moneytalk-kb/release-history/
timestamp: 2026-07-13T01:11:00+09:00
status: draft
---

# Release Timeline

> 기준: 흡수된 릴리스 변경 이력 원문에서 향후 작업 판단에 필요한 변화만 추린 KB용 요약

## 2026-07

| 변화 | 의미 | 관련 KB |
|---|---|---|
| 1.0.3 배포 후보 통합 | `1.0.2` 이후 28개 커밋의 AI 크레딧, Firebase AI Logic, SMS 룰, local embedding, sync 안정화, UI·검증 결과를 버전 단위로 고정 | [1.0.3 개발 요약](02-release-1.0.3-development-summary.md) |
| 채팅 단순 조회 로컬 처리 | 안전한 단순 조회는 Gemini analyzer/final/summary 없이 앱 내부 `DataQuery`와 템플릿 응답으로 처리 | [chat](../chat/README.md) |
| AI 크레딧 정책 단순화 | 채팅 1회 1크레딧, 과거 월 문자 가져오기 월 1개당 1크레딧, 보상형 광고 2크레딧 | [budget-credit-monetization](../budget-credit-monetization/README.md) |
| Gemini 기본 모델 비용 방어 | 기본 운영 모델을 Flash Lite 중심으로 고정 | [project-context](../project-context/README.md) |
| 광고 실패 우회 차단 | 보상형 광고 실패 시 과거 월 동기화를 실행하지 않음 | [budget-credit-monetization](../budget-credit-monetization/README.md) |

## 2026-06

| 변화 | 의미 | 관련 KB |
|---|---|---|
| `배달` leaf 카테고리 분리 | 화면/필터는 leaf 기준, 채팅의 명시적 식비 조회만 하위 포함 | [category-classification](../category-classification/README.md), [chat](../chat/README.md) |
| 알림 리스너 재연결/몰림 방지 | 과거 알림 재검사 시 거래 데이터는 유지하되 사용자 노티 재표시는 억제 | [notification-ingestion](../notification-ingestion/README.md), [notification-display](../notification-display/README.md) |
| AI 크레딧 RTDB gate | `credit_ad_enable=false` 또는 값 없음이면 크레딧 UI/차감/마이그레이션 숨김 | [budget-credit-monetization](../budget-credit-monetization/README.md) |
| App Functions 확장 | 조회/수정 함수 확장, 삭제성 함수는 기본 비활성 | [app-functions](../app-functions/README.md) |

## 2026-05

| 변화 | 의미 | 관련 KB |
|---|---|---|
| AI 서비스 없이 가능한 카테고리 사전 분류 | Firebase AI Logic 호출 전 로컬 사전 분류 규칙과 exact mapping을 저장 | [category-classification](../category-classification/README.md) |
| 개인정보/릴리스 metadata 보정 | Play 등록 앱명/패키지/개발자명과 웹 정책 정합성 보정 | [onboarding](../onboarding/README.md) |
| 앱 알림/SMS 교차 중복 보정 | 금융 앱 알림과 SMS가 같은 거래일 때 중복 저장을 줄임 | [notification-ingestion](../notification-ingestion/README.md) |

## 2026-04

| 변화 | 의미 | 관련 KB |
|---|---|---|
| 백업/복원 확장 | 카테고리/규칙/예산/카드/SMS 제외 키워드까지 포함 | [backup-restore](../backup-restore/README.md) |
| 월별 SMS 동기화 회귀 테스트 | 월별 읽기 순서 독립성과 coverage/CTA 판정 검증 | [sms-parsing](../sms-parsing/README.md) |
| 통계 제외 플래그 | 카드대금/상환성 거래를 목록에는 남기고 집계에서는 제외 | [filtering](../filtering/README.md) |
| SMS 동기화 책임 분리 | 원본 읽기, 기간 계산, coverage, 날짜 해석을 전용 클래스로 분리 | [sms-parsing](../sms-parsing/README.md) |

## 2026-03

| 변화 | 의미 | 관련 KB |
|---|---|---|
| Coachmark 온보딩 | 화면별 최초 사용 가이드를 DataStore로 관리 | [coachmark](../coachmark/README.md) |
| 커스텀 카테고리/StoreRule | 사용자 카테고리와 거래처 규칙이 카테고리 분류의 핵심 축이 됨 | [category-settings](../category-settings/README.md), [store-rule-settings](../store-rule-settings/README.md) |
| TransactionEdit 화면 | 거래 수정/추가와 동일 거래처 적용 UX 도입 | [transaction-edit](../transaction-edit/README.md) |
| SMS Regex Fast Path | sender 기반 regex 룰, asset seed, RTDB overlay, 룰 자동 품질 관리 도입 | [sms-pipeline](../sms-pipeline/README.md) |

## 원칙

이 타임라인은 전체 changelog 대체 문서가 아니다. 구현 세부는 현재 코드와 각 기능 KB를 우선하고, 과거 변경 배경 확인이 필요할 때만 이 문서를 본다.
