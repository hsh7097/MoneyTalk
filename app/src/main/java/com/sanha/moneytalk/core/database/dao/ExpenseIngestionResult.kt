package com.sanha.moneytalk.core.database.dao

/** 자동 수집 저장 결과. 기존 거래 갱신과 중복 스킵에는 새 거래 알림을 보내지 않는다. */
enum class ExpenseIngestionResult {
    INSERTED,
    UPDATED,
    SKIPPED
}
