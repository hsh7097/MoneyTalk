package com.sanha.moneytalk.core.database.dao

/** 자동 수집 저장 결과. 기존 거래 갱신과 중복 스킵에는 새 거래 알림을 보내지 않는다. */
sealed interface ExpenseIngestionResult {
    /** Room이 실제 저장한 ID. 저장 전 entity의 id(0)를 알림에 넘기지 않는다. */
    data class Inserted(val expenseId: Long) : ExpenseIngestionResult
    data class Updated(val expenseId: Long) : ExpenseIngestionResult
    data object Skipped : ExpenseIngestionResult
}
