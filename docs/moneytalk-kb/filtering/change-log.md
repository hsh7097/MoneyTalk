---
type: changelog
title: Filtering KB 변경 로그
description: Filtering KB 변경 이유와 영향 문서를 기록한다.
tags: [moneytalk, filtering, changelog]
resource: docs/moneytalk-kb/filtering/
timestamp: 2026-07-11T00:00:00+09:00
status: draft
---

# Filtering KB 변경 로그

| 날짜 | 근거 | 변경 | 영향 문서 | 메모 |
|---|---|---|---|---|
| 2026-07-08 | `HistoryFilter.kt`, `CardVisibilityFilter.kt`, `StatsExclusionClassifier.kt`, SMS 제외 repository 확인 | Filtering 기능 KB 생성 | `README.md`, `01-feature-flow.md` | 화면 필터, 카드 숨김, 통계 제외, SMS 제외 설정의 책임을 기능 KB로 분리. |
| 2026-07-09 | `HistoryViewModel`, `HistoryFilter`, `CardVisibilityFilter`, `StatsExclusionClassifier`, `DataBackupManager`, SMS 제외 repository 확인 | 필터 계층 상세화 | `README.md`, `02-filter-surfaces.md` | 내역 화면 필터, 카드 노출 필터, 통계 제외, SMS 입력 제외, export 필터의 적용 시점과 영향 범위를 분리. |
| 2026-07-10 | RTDB `sms_origin` export, `SmsNonTransactionNoticeFilter`, `SmsIncomeFilter`, `StatsExclusionClassifier` 검증 | 정산/요약/취소 입력 정책 명시 | `02-filter-surfaces.md` | 완료 카드대금은 저장 후 통계 제외, 예정/집계는 SKIP, 취소는 INCOME으로 분리. |
| 2026-07-11 | 롯데법인 이용금액 실제 출금 표본과 전체 RTDB 재감사 | `이용금액` 완료 출금을 카드대금 통계 제외 키워드에 포함 | `02-filter-surfaces.md` | 거래는 보존하되 카드 승인 소비와 중복 합산하지 않음. |
| 2026-07-11 | RTDB 192개 row 반복 감사 | 0원 승인과 미완료 쇼핑 입금계좌 안내를 고신뢰 SKIP에 추가 | `02-filter-surfaces.md` | 지출 오염과 불필요한 LLM 호출을 방지. |
