package com.sanha.moneytalk.feature.home.data

import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoreRuleRepositoryTest {

    @Test
    fun findBestMatchingRule_ignoresWhitespace() {
        val rule = StoreRuleEntity(
            keyword = "가나다라",
            category = "식비",
            createdAt = 1L
        )

        val result = StoreRuleRepository.findBestMatchingRule(
            rules = listOf(rule),
            storeName = "가나 다라"
        )

        assertEquals(rule, result)
    }

    @Test
    fun findBestMatchingRule_prefersLongerNormalizedKeyword() {
        val shortRule = StoreRuleEntity(
            keyword = "가나",
            category = "기타",
            createdAt = 2L
        )
        val longRule = StoreRuleEntity(
            keyword = "가나 다라",
            category = "식비",
            createdAt = 1L
        )

        val result = StoreRuleRepository.findBestMatchingRule(
            rules = listOf(shortRule, longRule),
            storeName = "가나다라"
        )

        assertEquals(longRule, result)
    }

    @Test
    fun findBestMatchingRule_matchesTruncatedPrefixStoreName() {
        val rule = StoreRuleEntity(
            keyword = "유튜브프리미엄",
            category = "구독",
            isFixed = true,
            createdAt = 1L
        )

        val result = StoreRuleRepository.findBestMatchingRule(
            rules = listOf(rule),
            storeName = "유튜브프리미"
        )

        assertEquals(rule, result)
    }

    @Test
    fun findBestMatchingRule_returnsNullForBlankStoreName() {
        val rule = StoreRuleEntity(keyword = "가나다라")

        assertNull(StoreRuleRepository.findBestMatchingRule(listOf(rule), " "))
    }
}
