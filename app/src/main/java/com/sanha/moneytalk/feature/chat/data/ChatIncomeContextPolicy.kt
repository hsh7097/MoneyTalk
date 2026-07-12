package com.sanha.moneytalk.feature.chat.data

import com.sanha.moneytalk.core.util.DataQuery
import com.sanha.moneytalk.core.util.QueryType

internal object ChatIncomeContextPolicy {

    private val incomeKeywords = listOf(
        "수입",
        "소득",
        "월급",
        "급여",
        "입금",
        "income",
        "salary",
        "deposit"
    )

    private val incomeQueryTypes = setOf(
        QueryType.TOTAL_INCOME,
        QueryType.MONTHLY_INCOME,
        QueryType.CATEGORY_RATIO,
        QueryType.INCOME_LIST
    )

    fun requiresIncomeContext(userMessage: String): Boolean {
        val normalized = userMessage.lowercase()
        return incomeKeywords.any(normalized::contains)
    }

    fun filterQueries(userMessage: String, queries: List<DataQuery>): List<DataQuery> {
        if (requiresIncomeContext(userMessage)) return queries
        return queries.filterNot { it.type in incomeQueryTypes }
    }
}
