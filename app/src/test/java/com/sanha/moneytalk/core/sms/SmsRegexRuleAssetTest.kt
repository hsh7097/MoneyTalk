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

    private val knownAssetTypes = setOf("expense", "cancel", "overseas", "payment", "debit")
    private val runtimeFastPathTypes = setOf("expense", "overseas", "payment", "debit")

    @Test
    fun `asset rules use only known types while cancellation stays outside Fast Path`() {
        val rules = loadRules()
        val unsupported = rules.filter { it.type !in knownAssetTypes }

        assertTrue("unsupported rule types: ${unsupported.map { it.type }.distinct()}", unsupported.isEmpty())
        assertFalse("income rules must stay on SmsIncomeParser path", rules.any { it.type == "income" })
        assertFalse("cancellation must stay on the income path", "cancel" in runtimeFastPathTypes)
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
                body = "삼성1234승인 홍길동\n12,300원 3개월\n04/24 13:45 스타벅스\n누적100,000원",
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
                    "농협04/24 13:45 352-****-6488-03 자동출금33,980원(신한카드) 잔액-11,024,387원"
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
            ),
            Sample(
                issuer = "하나카드 멀티라인",
                sender = "18001111",
                type = "expense",
                body = listOf(
                    "[Web발신]",
                    "금액",
                    "12,300원",
                    "카드",
                    "하나카드*",
                    "손님명",
                    "홍길동",
                    "거래종류",
                    "승인",
                    "거래구분",
                    "일시불",
                    "사용처",
                    "스타벅스",
                    "거래시간",
                    "04/24 13:45"
                ).joinToString("\n"),
                amount = "12,300",
                store = "스타벅스",
                card = "하나카드*",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "롯데법인",
                sender = "15998800",
                type = "expense",
                body = listOf(
                    "[Web발신]",
                    "스타벅스",
                    "12,300원 승인",
                    "홍길동 롯데법인카드 1234",
                    "일시불 04/24 13:45",
                    "누적100,000원"
                ).joinToString("\n"),
                amount = "12,300",
                store = "스타벅스",
                card = "롯데법인카드",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "롯데법인 이용금액 출금",
                sender = "15998800",
                type = "expense",
                body = listOf(
                    "[Web발신]",
                    "이용금액이 기업은행에서 출금됐어요.",
                    "",
                    "- 출금액: 100,000원",
                    "- 출금일: 04/24",
                    "- 롯데법인(1234)"
                ).joinToString("\n"),
                amount = "100,000",
                store = "롯데법인",
                card = "롯데법인",
                date = "04/24"
            ),
            Sample(
                issuer = "롯데법인 원화 해외승인",
                sender = "15998800",
                type = "overseas",
                body = listOf(
                    "ADOBE",
                    "KRW 39,050 해외승인",
                    "홍길동 롯데법인1234",
                    "일시불 04/24 13:45",
                    "누적100,000원"
                ).joinToString("\n"),
                amount = "39,050",
                store = "ADOBE",
                card = "롯데법인",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "카카오뱅크 카드결제",
                sender = "15993333",
                type = "expense",
                body = listOf(
                    "[Web발신]",
                    "[카카오뱅크] 카드결제",
                    "홍길동(1234)",
                    "04/24 13:45",
                    "12,300원",
                    "스타벅스",
                    "잔액 100,000원"
                ).joinToString("\n"),
                amount = "12,300",
                store = "스타벅스",
                card = "카카오뱅크",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "부산BC",
                sender = "15884000",
                type = "expense",
                body = listOf(
                    "부산BC(1234)",
                    "12,300원 사용",
                    "개인",
                    "홍길동님",
                    "일시불 04/24 13:45",
                    "총누적",
                    "100,000원",
                    "스타벅스"
                ).joinToString("\n"),
                amount = "12,300",
                store = "스타벅스",
                card = "부산BC",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "계좌 메모 출금",
                sender = "15446200",
                type = "expense",
                body = listOf(
                    "출금",
                    "04/24 13:45",
                    "12,300",
                    "계좌",
                    "1234****5678",
                    "내용",
                    "메모",
                    "스타벅스",
                    "잔액",
                    "100,000"
                ).joinToString("\n"),
                amount = "12,300",
                store = "스타벅스",
                card = "1234****5678",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "농협 출금",
                sender = "15882100",
                type = "expense",
                body = listOf(
                    "[Web발신]",
                    "농협 출금12,300원",
                    "04/24 13:45 352-****-6488-03 스타벅스 잔액100,000원"
                ).joinToString("\n"),
                amount = "12,300",
                store = "스타벅스",
                card = "농협",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "KB국민카드 앱",
                sender = "15881688",
                type = "expense",
                body = listOf(
                    "[Web발신]",
                    "KB국민카드1234",
                    "승인",
                    "12,300원 (일시불)",
                    "스타벅스",
                    "고객명",
                    "홍길동님",
                    "승인시각",
                    "04/24 13:45",
                    "누적",
                    "100,000원"
                ).joinToString("\n"),
                amount = "12,300",
                store = "스타벅스",
                card = "KB국민카드",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "롯데카드 결제대금 출금",
                sender = "15888100",
                type = "expense",
                body = "[Web발신]\n[롯데카드] 홍길동님, 4월 결제대금 120,000원 중 100,000원 04/24 출금되었습니다.",
                amount = "100,000",
                store = "롯데",
                card = "롯데",
                date = "04/24"
            ),
            Sample(
                issuer = "신한카드 결제대금 인출",
                sender = "15447000",
                type = "expense",
                body = listOf(
                    "[Web발신]",
                    "[신한카드] 결제대금 인출 안내",
                    "- 홍길동님 결제대금(1234)",
                    "- 120,000원 중 100,000원",
                    "  04/24일 인출 되었습니다."
                ).joinToString("\n"),
                amount = "100,000",
                store = "신한",
                card = "신한",
                date = "04/24"
            ),
            Sample(
                issuer = "씨티카드",
                sender = "15661000",
                type = "expense",
                body = listOf(
                    "[Web발신]",
                    "씨티카드(1234*56*)",
                    "홍길동님",
                    "04/24 13:45",
                    "일시불 12,300원",
                    "스타벅스"
                ).joinToString("\n"),
                amount = "12,300",
                store = "스타벅스",
                card = "씨티카드",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "NH농협 신용승인",
                sender = "15881600",
                type = "expense",
                body = listOf(
                    "[Web발신]",
                    "NH카드1*2*신용승인",
                    "홍길동",
                    "12,300원",
                    "04/24 13:45",
                    "스타벅스",
                    "총누적100,000원"
                ).joinToString("\n"),
                amount = "12,300",
                store = "스타벅스",
                card = "NH카드",
                date = "04/24 13:45"
            ),
            Sample(
                issuer = "새마을금고 수수료",
                sender = "15999000",
                type = "expense",
                body = "[Web발신]\n[새마을금고] 1234*5678 입출금알림수수료 500원 출금 1234*5678 04/24 13:45 잔액100,000원",
                amount = "500",
                store = "입출금알림수수료",
                card = "1234*5678",
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
