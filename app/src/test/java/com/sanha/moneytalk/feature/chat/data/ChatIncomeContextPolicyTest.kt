package com.sanha.moneytalk.feature.chat.data

import com.sanha.moneytalk.core.util.DataQuery
import com.sanha.moneytalk.core.util.QueryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatIncomeContextPolicyTest {

    @Test
    fun generalSavingQuestionDoesNotRequireIncomeContext() {
        assertFalse(
            ChatIncomeContextPolicy.requiresIncomeContext(
                "이번 달 절약할 수 있는 부분이 있을까?"
            )
        )
    }

    @Test
    fun incomeRatioQuestionRequiresIncomeContext() {
        assertTrue(
            ChatIncomeContextPolicy.requiresIncomeContext(
                "식비가 수입 대비 적절해?"
            )
        )
    }

    @Test
    fun salaryQuestionRequiresIncomeContext() {
        assertTrue(ChatIncomeContextPolicy.requiresIncomeContext("월급 대비 지출 비율 알려줘"))
    }

    @Test
    fun generalSavingQuestionDropsIncomeQueries() {
        val queries = listOf(
            DataQuery(QueryType.TOTAL_EXPENSE),
            DataQuery(QueryType.EXPENSE_BY_CATEGORY),
            DataQuery(QueryType.MONTHLY_INCOME),
            DataQuery(QueryType.CATEGORY_RATIO)
        )

        assertEquals(
            listOf(QueryType.TOTAL_EXPENSE, QueryType.EXPENSE_BY_CATEGORY),
            ChatIncomeContextPolicy.filterQueries(
                userMessage = "이번 달 절약할 수 있는 부분이 있을까?",
                queries = queries
            ).map { it.type }
        )
    }

    @Test
    fun incomeQuestionKeepsIncomeQueries() {
        val queries = listOf(
            DataQuery(QueryType.EXPENSE_BY_CATEGORY),
            DataQuery(QueryType.MONTHLY_INCOME),
            DataQuery(QueryType.CATEGORY_RATIO)
        )

        assertEquals(
            queries,
            ChatIncomeContextPolicy.filterQueries(
                userMessage = "식비가 수입 대비 적절해?",
                queries = queries
            )
        )
    }
}
