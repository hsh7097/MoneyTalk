package com.sanha.moneytalk.core.sms

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.regex.Pattern

class SmsRegexRuleAssetTest {

    private val allowedFastPathTypes = setOf("expense", "cancel", "overseas", "payment", "debit")

    @Test
    fun `asset rules use only supported Fast Path types`() {
        val rules = loadRules()
        val unsupported = rules.filter { it.type !in allowedFastPathTypes }

        assertTrue("unsupported rule types: ${unsupported.map { it.type }.distinct()}", unsupported.isEmpty())
        assertFalse("income rules must stay on SmsIncomeParser path", rules.any { it.type == "income" })
    }

    @Test
    fun `active asset rules are structurally valid`() {
        val activeRules = loadRules().filter { it.status == "ACTIVE" }

        assertTrue("active rules must not be empty", activeRules.isNotEmpty())
        activeRules.forEach { rule ->
            assertTrue("blank ruleKey: $rule", rule.ruleKey.isNotBlank())
            assertTrue("blank bodyRegex: ${rule.ruleKey}", rule.bodyRegex.isNotBlank())
            assertTrue("blank amountGroup: ${rule.ruleKey}", rule.amountGroup.isNotBlank())
            assertTrue("blank storeGroup: ${rule.ruleKey}", rule.storeGroup.isNotBlank())
            assertTrue("priority must be positive: ${rule.ruleKey}", rule.priority > 0)
            assertTrue("blank status: ${rule.ruleKey}", rule.status.isNotBlank())
            assertNotNull("regex compile failed: ${rule.ruleKey}", runCatching { Pattern.compile(rule.bodyRegex) }.getOrNull())
        }
    }

    @Test
    fun `asset rule keys follow deterministic ruleKey formula`() {
        loadRules().forEach { rule ->
            val expected = sha256(
                listOf(
                    rule.sender,
                    rule.type,
                    rule.bodyRegex,
                    rule.amountGroup,
                    rule.storeGroup,
                    rule.cardGroup,
                    rule.dateGroup,
                    rule.version.toString()
                ).joinToString("|")
            ).take(24)

            assertEquals("ruleKey mismatch for ${rule.sender}/${rule.type}", expected, rule.ruleKey)
        }
    }

    @Test
    fun `covered issuers match synthetic payment samples`() {
        val rules = loadRules()
        val samples = listOf(
            Sample(
                issuer = "KB국민",
                sender = "16449999",
                type = "expense",
                body = "[KB]04/24 13:45\n1234*5678\n스타벅스출금\n12,300\n잔액100,000",
                amount = "12,300",
                store = "스타벅스",
                card = "KB",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "신한",
                sender = "15447200",
                type = "expense",
                body = "신한카드(1234)승인 홍길동 12,300원(일시불)04/24 13:45 스타벅스 누적100,000원",
                amount = "12,300",
                store = "스타벅스",
                card = "신한카드",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "신한 카드번호입력",
                sender = "15447200",
                type = "expense",
                body = listOf(
                    "[Web발신]",
                    "RE:신한 카드번호입력승인 홍길동님(1234) 04/24 13:45 18,700원 SK 세븐모바일"
                ).joinToString("\n"),
                amount = "18,700",
                store = "SK 세븐모바일",
                card = "신한",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "현대",
                sender = "15776200",
                type = "expense",
                body = "현대카드 홍길동 승인\n홍길동\n12,300원 일시불\n04/24 13:45\n스타벅스\n누적100,000원",
                amount = "12,300",
                store = "스타벅스",
                card = "현대카드",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "삼성",
                sender = "15888900",
                type = "expense",
                body = "삼성1234승인 홍길동\n12,300원 일시불\n04/24 13:45 스타벅스\n누적100,000원",
                amount = "12,300",
                store = "스타벅스",
                card = "삼성",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "롯데",
                sender = "15888100",
                type = "expense",
                body = "스타벅스\n12,300원 승인\n홍길동 롯데1234*5678*\n일시불 04/24 13:45\n누적100,000원",
                amount = "12,300",
                store = "스타벅스",
                card = "롯데",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "우리",
                sender = "15889955",
                type = "expense",
                body = "안내 ● 우리카드 이용안내\n우리(1234)승인\n홍길동님\n12,300원 일시불\n04/24 13:45\n스타벅스\n누적100,000원",
                amount = "12,300",
                store = "스타벅스",
                card = "우리카드",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "NH농협",
                sender = "15881600",
                type = "expense",
                body = "NH카드1*2*승인\n홍길동\n12,300원 체크\n04/24 13:45\n스타벅스\n잔액100,000원",
                amount = "12,300",
                store = "스타벅스",
                card = "NH카드",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "NH농협 지역화폐",
                sender = "15881600",
                type = "expense",
                body = listOf(
                    "[Web발신]",
                    "NH카드5*6*승인",
                    "홍길동 8,000원 체크",
                    "(지역화폐 8,000원 사용)",
                    "04/24 13:45",
                    "더벤티 영천시"
                ).joinToString("\n"),
                amount = "8,000",
                store = "더벤티 영천시",
                card = "NH카드",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "NH농협 자동출금",
                sender = "15882100",
                type = "expense",
                body = listOf(
                    "[Web발신]",
                    "농협04/24 13:45 352-****-6488-03 자동출금33,980원(신한카드) 잔액11,024,387원"
                ).joinToString("\n"),
                amount = "33,980",
                store = "신한카드",
                card = "농협",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "롯데 무누적 승인",
                sender = "15888100",
                type = "expense",
                body = listOf(
                    "[Web발신]",
                    "(주)마이리얼트립",
                    "250,158원 승인",
                    "홍길동 롯데8*2*",
                    "일시불 04/24 13:45"
                ).joinToString("\n"),
                amount = "250,158",
                store = "(주)마이리얼트립",
                card = "롯데",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "스마일카드",
                sender = "15220080",
                type = "expense",
                body = "스마일카드승인 홍길동 12,300원 일시불 04/24 13:45 스타벅스 누적100,000원",
                amount = "12,300",
                store = "스타벅스",
                card = "스마일카드",
                date = "04/24 13:45"
            )
        )

        samples.forEach { sample ->
            val match = findFirstMatch(rules, sample)
            assertNotNull("${sample.issuer} sample did not match", match)
            checkNotNull(match)

            assertEquals("${sample.issuer} amount", sample.amount, match.group(match.rule.amountGroup))
            assertEquals("${sample.issuer} store", sample.store, match.group(match.rule.storeGroup).trim())
            assertEquals("${sample.issuer} card", sample.card, match.group(match.rule.cardGroup).trim())
            assertEquals("${sample.issuer} date", sample.date, match.group(match.rule.dateGroup).trim())
        }
    }

    private fun findFirstMatch(rules: List<AssetRule>, sample: Sample): RuleMatch? {
        return rules
            .filter { it.sender == sample.sender && it.type == sample.type && it.status == "ACTIVE" }
            .sortedByDescending { it.priority }
            .firstNotNullOfOrNull { rule ->
                val matcher = Pattern.compile(rule.bodyRegex).matcher(sample.body)
                if (matcher.find()) RuleMatch(rule, matcher) else null
            }
    }

    private fun loadRules(): List<AssetRule> {
        val root = JsonParser.parseString(assetFile().readText()).asJsonObject
        val smsRules = root.getAsJsonObject("sms_rules")
        return smsRules.entrySet().flatMap { (sender, senderNode) ->
            senderNode.asJsonObject.entrySet().flatMap { (type, typeNode) ->
                typeNode.asJsonObject.entrySet().map { (ruleKey, ruleNode) ->
                    val ruleObject = ruleNode.asJsonObject
                    AssetRule(
                        sender = sender,
                        type = type,
                        ruleKey = ruleKey,
                        bodyRegex = ruleObject.string("bodyRegex"),
                        amountGroup = ruleObject.string("amountGroup"),
                        storeGroup = ruleObject.string("storeGroup"),
                        cardGroup = ruleObject.string("cardGroup"),
                        dateGroup = ruleObject.string("dateGroup"),
                        priority = ruleObject.int("priority"),
                        status = ruleObject.string("status"),
                        version = ruleObject.int("version")
                    )
                }
            }
        }
    }

    private fun assetFile(): File {
        return listOf(
            File("src/main/assets/sms_rules_v1.json"),
            File("app/src/main/assets/sms_rules_v1.json")
        ).first { it.isFile }
    }

    private fun JsonObject.string(key: String): String {
        return get(key)?.asString.orEmpty()
    }

    private fun JsonObject.int(key: String): Int {
        return get(key)?.asInt ?: 0
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private data class AssetRule(
        val sender: String,
        val type: String,
        val ruleKey: String,
        val bodyRegex: String,
        val amountGroup: String,
        val storeGroup: String,
        val cardGroup: String,
        val dateGroup: String,
        val priority: Int,
        val status: String,
        val version: Int
    )

    private data class Sample(
        val issuer: String,
        val sender: String,
        val type: String,
        val body: String,
        val amount: String,
        val store: String,
        val card: String,
        val date: String
    )

    private data class RuleMatch(
        val rule: AssetRule,
        private val matcher: java.util.regex.Matcher
    ) {
        fun group(name: String): String {
            return matcher.group(name).orEmpty()
        }
    }
}
