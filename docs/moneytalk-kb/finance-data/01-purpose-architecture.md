---
type: architecture
title: Finance Data Purpose Architecture
description: Finance Data의 목적, 책임, 의존성 방향을 설명한다.
tags: [moneytalk, finance-data, architecture]
resource: app/src/main/java/com/sanha/moneytalk/core/database/
timestamp: 2026-07-03T16:30:00+09:00
status: draft
---

# 01 Purpose Architecture

Finance Data는 로컬 Room DB와 이를 감싸는 Repository 계층이다.
화면 ViewModel은 DAO를 직접 다루기보다 Repository 또는 service를 통해 데이터를 읽고 쓴다.

## 의존성 방향

```text
Feature ViewModel
→ Repository / Service
→ DAO
→ Entity
→ AppDatabase
```

## 책임

- `AppDatabase`: 모든 entity/DAO 등록과 migration 정의
- `dao`: DB query와 transaction 단위
- `entity`: 저장 모델과 DB column 정의
- `feature/home/data`: 앱 기능에서 사용하는 repository와 분류 service
- `core/database/*Repository`: 카드, SMS 제외, 크레딧처럼 여러 도메인에서 쓰는 DB-backed repository

DB schema가 바뀌면 migration과 영향 repository를 같이 본다.
