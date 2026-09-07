package com.sanha.moneytalk.core.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsRegexRemoteRuleLoaderTest {

    private val loader = SmsRegexRemoteRuleLoader(database = null)

    @Test
    fun `one malformed remote rule does not remove valid neighboring rules`() {
        val rules = loader.parseRules(mapOf(
            "1588-1234" to mapOf("expense" to linkedMapOf(
                "valid-before" to validRule(),
                "invalid-priority" to validRule() + ("priority" to "high"),
                "invalid-body" to validRule() + ("bodyRegex" to 123L),
                "invalid-group" to validRule() + ("amountGroup" to listOf("amount")),
                "invalid-regex" to validRule() + ("bodyRegex" to "["),
                "valid-after" to validRule()
            ))
        ))

        assertEquals(listOf("valid-before", "valid-after"), rules.map { it.ruleKey })
        assertTrue(rules.all { it.senderAddress == "15881234" })
        assertTrue(rules.all { it.amountGroup == "amount" && it.storeGroup == "store" })
    }

    @Test
    fun `missing optional remote metadata keeps documented defaults`() {
        val rules = loader.parseRules(mapOf(
            "15881234" to mapOf("expense" to mapOf("valid" to validRule()))
        ))
        val rule = rules.single()

        assertEquals("ACTIVE", rule.status)
        assertEquals("rtdb", rule.source)
        assertEquals(1, rule.version)
        assertEquals(900, rule.priority)
        assertEquals("", rule.cardGroup)
        assertEquals("", rule.dateGroup)
    }

    @Test
    fun `explicit inactive overlay is retained instead of reviving its asset rule`() {
        val rules = loader.parseRules(mapOf(
            "15881234" to mapOf("expense" to mapOf(
                "disabled" to validRule() + ("status" to "INACTIVE")
            ))
        ))

        assertEquals("INACTIVE", rules.single().status)
    }

    @Test
    fun `fractional and overflowing rule metadata are rejected independently`() {
        val rules = loader.parseRules(mapOf(
            "15881234" to mapOf("expense" to linkedMapOf(
                "fractional-priority" to validRule() + ("priority" to 900.5),
                "overflow-version" to validRule() + ("version" to 4294967297L),
                "fractional-timestamp" to validRule() + ("updatedAt" to 123.5),
                "valid" to validRule()
            ))
        ))

        assertEquals(listOf("valid"), rules.map { it.ruleKey })
    }

    @Test
    fun `malformed tree branches do not abort unrelated senders`() {
        val rules = loader.parseRules(mapOf(
            "invalid-sender-node" to "invalid",
            "15880000" to mapOf("expense" to "invalid"),
            "15880001" to mapOf("expense" to mapOf("invalid" to "invalid")),
            "15881234" to mapOf("expense" to mapOf("valid" to validRule()))
        ))

        assertEquals(listOf("valid"), rules.map { it.ruleKey })
    }

    private fun validRule(): Map<String, Any> = mapOf(
        "bodyRegex" to "(?<store>[^\\n]+) (?<amount>[\\d,]+)원",
        "amountGroup" to "amount",
        "storeGroup" to "store",
        "priority" to 900L
    )
}
