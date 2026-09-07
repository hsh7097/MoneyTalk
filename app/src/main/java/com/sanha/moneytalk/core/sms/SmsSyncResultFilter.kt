package com.sanha.moneytalk.core.sms

import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.MoneyTalkLogger

/** 파싱된 거래시각을 기준으로 요청한 월의 저장 대상을 고른다. */
object SmsSyncResultFilter {
    fun filterByTransactionRange(
        syncResult: SyncResult,
        transactionRange: Pair<Long, Long>?
    ): SyncResult {
        if (transactionRange == null) return syncResult

        val filteredExpenses = syncResult.expenses.filter { parsed ->
            DateUtils.parseDateTime(parsed.analysis.dateTime) in transactionRange.first..transactionRange.second
        }
        val filteredIncomes = syncResult.incomes.filter { income ->
            val dateTime = SmsIncomeParser.extractDateTime(income.body, income.date)
            DateUtils.parseDateTime(dateTime) in transactionRange.first..transactionRange.second
        }

        val droppedByRange = syncResult.expenses.size + syncResult.incomes.size -
            filteredExpenses.size - filteredIncomes.size
        if (droppedByRange > 0) {
            MoneyTalkLogger.i("월 동기화 저장 범위 밖 SMS ${droppedByRange}건 제외")
        }

        return syncResult.copy(
            expenses = filteredExpenses,
            incomes = filteredIncomes
        )
    }

}
