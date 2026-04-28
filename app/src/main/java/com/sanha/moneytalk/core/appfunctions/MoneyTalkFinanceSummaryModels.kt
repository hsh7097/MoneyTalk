package com.sanha.moneytalk.core.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/** 월간 지출 카테고리 요약. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkCategorySummary(
    /** 카테고리 표시명. */
    val category: String,
    /** 해당 기간 지출 금액 합계. */
    val amount: Int,
    /** 월 지출 전체 대비 비율. */
    val percentage: Int,
    /** 카테고리 예산 설정 여부. */
    val budgetConfigured: Boolean,
    /** 카테고리 예산. 설정되지 않은 경우 0. */
    val budget: Int,
    /** 예산 대비 남은 금액. 초과 시 음수. */
    val remainingBudget: Int
)

/** 최근 거래 요약. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkRecentTransaction(
    /** 앱 내부 거래 ID. */
    val id: Long,
    /** 거래 유형. expense, income, transfer_withdrawal, transfer_deposit 중 하나. */
    val transactionType: String,
    /** 거래처, 출처 또는 설명. */
    val title: String,
    /** 카테고리 표시명. */
    val category: String,
    /** 거래 금액. */
    val amount: Int,
    /** 거래 시각 timestamp. */
    val dateMillis: Long,
    /** 거래 시각 표시 문자열. */
    val dateText: String,
    /** 카드명 또는 출처. */
    val paymentMethod: String
)

/** 척척 가계부 월간 요약. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class MoneyTalkMonthlyFinanceSummary(
    /** 요약 기준 연도. */
    val summaryYear: Int,
    /** 요약 기준 월. */
    val summaryMonth: Int,
    /** 사용자가 설정한 월 시작일. */
    val monthStartDay: Int,
    /** 월 시작일이 1일이 아닌 커스텀 월 기준 여부. */
    val usesCustomMonth: Boolean,
    /** 조회 기간 표시명. */
    val periodLabel: String,
    /** 조회 기간 시작 timestamp. */
    val periodStart: Long,
    /** 조회 기간 종료 timestamp. */
    val periodEnd: Long,
    /** 기간 내 수입 합계. */
    val monthlyIncome: Int,
    /** 기간 내 지출 합계. */
    val monthlyExpense: Int,
    /** 수입에서 지출을 뺀 금액. */
    val balance: Int,
    /** 월 예산 설정 여부. */
    val budgetConfigured: Boolean,
    /** 월 예산. 설정되지 않은 경우 0. */
    val monthlyBudget: Int,
    /** 예산 대비 남은 금액. 초과 시 음수. */
    val remainingBudget: Int,
    /** 기간 내 지출 거래 수. */
    val expenseCount: Int,
    /** 기간 내 수입 거래 수. */
    val incomeCount: Int,
    /** 전월 동기간 지출 합계. */
    val previousPeriodExpense: Int,
    /** 전월 비교 기간 표시명. */
    val comparisonPeriodLabel: String,
    /** 이번 기간 지출과 전월 동기간 지출의 차이. */
    val comparisonExpenseDelta: Int,
    /** 지출 상위 카테고리 목록. */
    val topExpenseCategories: List<MoneyTalkCategorySummary>,
    /** 최근 거래 목록. */
    val recentTransactions: List<MoneyTalkRecentTransaction>
)
