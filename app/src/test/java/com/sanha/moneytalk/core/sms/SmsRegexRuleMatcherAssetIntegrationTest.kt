package com.sanha.moneytalk.core.sms

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sanha.moneytalk.core.database.SmsRegexRuleRepository
import com.sanha.moneytalk.core.database.dao.SmsChannelProbeLogDao
import com.sanha.moneytalk.core.database.dao.SmsPatternDao
import com.sanha.moneytalk.core.database.dao.SmsRegexRuleDao
import com.sanha.moneytalk.core.database.entity.SmsChannelProbeLogEntity
import com.sanha.moneytalk.core.database.entity.SmsPatternEntity
import com.sanha.moneytalk.core.database.entity.SmsRegexRuleEntity
import com.sanha.moneytalk.core.firebase.PremiumManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class SmsRegexRuleMatcherAssetIntegrationTest {

    @Test
    fun `asset rules parse covered issuer samples through SmsRegexRuleMatcher`() {
        val ruleDao = FakeSmsRegexRuleDao(loadAssetRules())
        val matcher = createMatcher(ruleDao)
        val samples = coveredIssuerSamples()

        val result = runSuspend {
            matcher.matchPaymentCandidates(
                samples.mapIndexed { index, sample ->
                    SmsInput(
                        id = "matcher-sample-$index",
                        body = sample.body,
                        address = sample.sender,
                        date = sampleTimestamp()
                    )
                }
            )
        }

        assertEquals("all synthetic samples must match in runtime matcher", samples.size, result.matched.size)
        assertTrue("no sample should fall back to pipeline", result.unmatched.isEmpty())
        assertEquals("matcher should update rule match counts", samples.size, ruleDao.matchCountUpdates)

        result.matched.zip(samples).forEach { (parsed, sample) ->
            assertEquals("${sample.issuer} amount", sample.amount, parsed.analysis.amount)
            assertEquals("${sample.issuer} store", sample.store, parsed.analysis.storeName)
            assertEquals("${sample.issuer} card", sample.card, parsed.analysis.cardName)
            assertEquals("${sample.issuer} date", sample.dateTime, parsed.analysis.dateTime)
        }
    }

    @Test
    fun `cancellation rules are excluded from runtime Fast Path`() {
        val cancelRule = SmsRegexRuleEntity(
            senderAddress = "15776000",
            type = "cancel",
            ruleKey = "synthetic-cancel-rule",
            bodyRegex = "(?<store>현대카드) (?<amount>취소) 알림",
            amountGroup = "amount",
            storeGroup = "store",
            priority = 900,
            status = "ACTIVE",
            source = "test",
            version = 1
        )
        val ruleDao = FakeSmsRegexRuleDao(listOf(cancelRule))
        val matcher = createMatcher(ruleDao)
        val input = SmsInput(
            id = "legacy-cancel-rule",
            body = "현대카드 취소 알림",
            address = "15776000",
            date = sampleTimestamp()
        )

        val result = runSuspend { matcher.matchPaymentCandidates(listOf(input)) }

        assertTrue("legacy cancellation rule must not produce an expense", result.matched.isEmpty())
        assertEquals(listOf(input), result.unmatched)
        assertEquals(1, result.failureReasonCounts["fast_path_rule_lookup:no_active_rule"])
        assertEquals("ignored cancellation rules must not update match counts", 0, ruleDao.matchCountUpdates)
    }

    private fun createMatcher(ruleDao: SmsRegexRuleDao): SmsRegexRuleMatcher {
        return SmsRegexRuleMatcher(
            ruleRepository = SmsRegexRuleRepository(ruleDao),
            smsPatternDao = EmptySmsPatternDao(),
            templateEngine = SmsTemplateEngine(SmsEmbeddingService()),
            originSampleCollector = SmsOriginSampleCollector(
                database = null,
                premiumManager = uninitializedInstance(PremiumManager::class.java)
            ),
            channelProbeCollector = SmsChannelProbeCollector(EmptySmsChannelProbeLogDao())
        )
    }

    private fun coveredIssuerSamples(): List<Sample> {
        val dateTime = "2026-04-24 13:45"
        val dateOnly = "2026-04-24 00:00"
        return listOf(
            Sample(
                issuer = "KB국민",
                sender = "16449999",
                body = "[KB]04/24 13:45\n1234*5678\n스타벅스출금\n12,300\n잔액100,000",
                amount = 12_300,
                store = "스타벅스",
                card = "KB",
                dateTime = dateTime
            ),
            Sample(
                issuer = "신한",
                sender = "15447200",
                body = listOf(
                    "신한카드(1234)승인 홍길동",
                    "12,300원(일시불)04/24 13:45 스타벅스 누적100,000원"
                ).joinToString(" "),
                amount = 12_300,
                store = "스타벅스",
                card = "신한카드",
                dateTime = dateTime
            ),
            Sample(
                issuer = "신한 카드번호입력",
                sender = "15447200",
                body = listOf(
                    "[Web발신]",
                    "RE:신한 카드번호입력승인 홍길동님(1234) 04/24 13:45 18,700원 SK 세븐모바일"
                ).joinToString("\n"),
                amount = 18_700,
                store = "SK 세븐모바일",
                card = "신한",
                dateTime = dateTime
            ),
            Sample(
                issuer = "현대",
                sender = "15776200",
                body = listOf(
                    "현대카드 홍길동 승인",
                    "홍길동",
                    "12,300원 일시불",
                    "04/24 13:45",
                    "스타벅스",
                    "누적100,000원"
                ).joinToString("\n"),
                amount = 12_300,
                store = "스타벅스",
                card = "현대카드",
                dateTime = dateTime
            ),
            Sample(
                issuer = "삼성",
                sender = "15888900",
                body = "삼성1234승인 홍길동\n12,300원 3개월\n04/24 13:45 스타벅스\n누적100,000원",
                amount = 12_300,
                store = "스타벅스",
                card = "삼성",
                dateTime = dateTime
            ),
            Sample(
                issuer = "롯데",
                sender = "15888100",
                body = listOf(
                    "스타벅스",
                    "12,300원 승인",
                    "홍길동 롯데1234*5678*",
                    "일시불 04/24 13:45",
                    "누적100,000원"
                ).joinToString("\n"),
                amount = 12_300,
                store = "스타벅스",
                card = "롯데",
                dateTime = dateTime
            ),
            Sample(
                issuer = "우리",
                sender = "15889955",
                body = listOf(
                    "안내 ● 우리카드 이용안내",
                    "우리(1234)승인",
                    "홍길동님",
                    "12,300원 일시불",
                    "04/24 13:45",
                    "스타벅스",
                    "누적100,000원"
                ).joinToString("\n"),
                amount = 12_300,
                store = "스타벅스",
                card = "우리카드",
                dateTime = dateTime
            ),
            Sample(
                issuer = "NH농협",
                sender = "15881600",
                body = "NH카드1*2*승인\n홍길동\n12,300원 체크\n04/24 13:45\n스타벅스\n잔액100,000원",
                amount = 12_300,
                store = "스타벅스",
                card = "NH카드",
                dateTime = dateTime
            ),
            Sample(
                issuer = "NH농협 지역화폐",
                sender = "15881600",
                body = listOf(
                    "[Web발신]",
                    "NH카드5*6*승인",
                    "홍길동 8,000원 체크",
                    "(지역화폐 8,000원 사용)",
                    "04/24 13:45",
                    "더벤티 영천시"
                ).joinToString("\n"),
                amount = 8_000,
                store = "더벤티 영천시",
                card = "NH카드",
                dateTime = dateTime
            ),
            Sample(
                issuer = "NH농협 자동출금",
                sender = "15882100",
                body = listOf(
                    "[Web발신]",
                    "농협04/24 13:45 352-****-6488-03 자동출금33,980원(신한카드) 잔액-11,024,387원"
                ).joinToString("\n"),
                amount = 33_980,
                store = "신한카드",
                card = "농협",
                dateTime = dateTime
            ),
            Sample(
                issuer = "롯데 무누적 승인",
                sender = "15888100",
                body = listOf(
                    "[Web발신]",
                    "(주)마이리얼트립",
                    "250,158원 승인",
                    "홍길동 롯데8*2*",
                    "일시불 04/24 13:45"
                ).joinToString("\n"),
                amount = 250_158,
                store = "(주)마이리얼트립",
                card = "롯데",
                dateTime = dateTime
            ),
            Sample(
                issuer = "스마일카드",
                sender = "15220080",
                body = "스마일카드승인 홍길동 12,300원 일시불 04/24 13:45 스타벅스 누적100,000원",
                amount = 12_300,
                store = "스타벅스",
                card = "스마일카드",
                dateTime = dateTime
            ),
            Sample(
                issuer = "하나카드 멀티라인",
                sender = "18001111",
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
                amount = 12_300,
                store = "스타벅스",
                card = "하나카드*",
                dateTime = dateTime
            ),
            Sample(
                issuer = "롯데법인",
                sender = "15998800",
                body = listOf(
                    "[Web발신]",
                    "스타벅스",
                    "12,300원 승인",
                    "홍길동 롯데법인카드 1234",
                    "일시불 04/24 13:45",
                    "누적100,000원"
                ).joinToString("\n"),
                amount = 12_300,
                store = "스타벅스",
                card = "롯데법인카드",
                dateTime = dateTime
            ),
            Sample(
                issuer = "롯데법인 이용금액 출금",
                sender = "15998800",
                body = listOf(
                    "[Web발신]",
                    "이용금액이 기업은행에서 출금됐어요.",
                    "",
                    "- 출금액: 100,000원",
                    "- 출금일: 04/24",
                    "- 롯데법인(1234)"
                ).joinToString("\n"),
                amount = 100_000,
                store = "롯데법인",
                card = "롯데법인",
                dateTime = dateOnly
            ),
            Sample(
                issuer = "롯데법인 원화 해외승인",
                sender = "15998800",
                body = listOf(
                    "ADOBE",
                    "KRW 39,050 해외승인",
                    "홍길동 롯데법인1234",
                    "일시불 04/24 13:45",
                    "누적100,000원"
                ).joinToString("\n"),
                amount = 39_050,
                store = "ADOBE",
                card = "롯데법인",
                dateTime = dateTime
            ),
            Sample(
                issuer = "카카오뱅크 카드결제",
                sender = "15993333",
                body = listOf(
                    "[Web발신]",
                    "[카카오뱅크] 카드결제",
                    "홍길동(1234)",
                    "04/24 13:45",
                    "12,300원",
                    "스타벅스",
                    "잔액 100,000원"
                ).joinToString("\n"),
                amount = 12_300,
                store = "스타벅스",
                card = "카카오뱅크",
                dateTime = dateTime
            ),
            Sample(
                issuer = "부산BC",
                sender = "15884000",
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
                amount = 12_300,
                store = "스타벅스",
                card = "부산BC",
                dateTime = dateTime
            ),
            Sample(
                issuer = "계좌 메모 출금",
                sender = "15446200",
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
                amount = 12_300,
                store = "스타벅스",
                card = "1234****5678",
                dateTime = dateTime
            ),
            Sample(
                issuer = "농협 출금",
                sender = "15882100",
                body = listOf(
                    "[Web발신]",
                    "농협 출금12,300원",
                    "04/24 13:45 352-****-6488-03 스타벅스 잔액100,000원"
                ).joinToString("\n"),
                amount = 12_300,
                store = "스타벅스",
                card = "농협",
                dateTime = dateTime
            ),
            Sample(
                issuer = "KB국민카드 앱",
                sender = "15881688",
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
                amount = 12_300,
                store = "스타벅스",
                card = "KB국민카드",
                dateTime = dateTime
            ),
            Sample(
                issuer = "롯데카드 결제대금 출금",
                sender = "15888100",
                body = "[Web발신]\n[롯데카드] 홍길동님, 4월 결제대금 120,000원 중 100,000원 04/24 출금되었습니다.",
                amount = 100_000,
                store = "롯데",
                card = "롯데",
                dateTime = dateOnly
            ),
            Sample(
                issuer = "신한카드 결제대금 인출",
                sender = "15447000",
                body = listOf(
                    "[Web발신]",
                    "[신한카드] 결제대금 인출 안내",
                    "- 홍길동님 결제대금(1234)",
                    "- 120,000원 중 100,000원",
                    "  04/24일 인출 되었습니다."
                ).joinToString("\n"),
                amount = 100_000,
                store = "신한",
                card = "신한",
                dateTime = dateOnly
            ),
            Sample(
                issuer = "씨티카드",
                sender = "15661000",
                body = listOf(
                    "[Web발신]",
                    "씨티카드(1234*56*)",
                    "홍길동님",
                    "04/24 13:45",
                    "일시불 12,300원",
                    "스타벅스"
                ).joinToString("\n"),
                amount = 12_300,
                store = "스타벅스",
                card = "씨티카드",
                dateTime = dateTime
            ),
            Sample(
                issuer = "NH농협 신용승인",
                sender = "15881600",
                body = listOf(
                    "[Web발신]",
                    "NH카드1*2*신용승인",
                    "홍길동",
                    "12,300원",
                    "04/24 13:45",
                    "스타벅스",
                    "총누적100,000원"
                ).joinToString("\n"),
                amount = 12_300,
                store = "스타벅스",
                card = "NH카드",
                dateTime = dateTime
            ),
            Sample(
                issuer = "새마을금고 수수료",
                sender = "15999000",
                body = "[Web발신]\n[새마을금고] 1234*5678 입출금알림수수료 500원 출금 1234*5678 04/24 13:45 잔액100,000원",
                amount = 500,
                store = "입출금알림수수료",
                card = "1234*5678",
                dateTime = dateTime
            )
        )
    }

    private fun loadAssetRules(): List<SmsRegexRuleEntity> {
        val root = JsonParser.parseString(assetFile().readText()).asJsonObject
        val smsRules = root.getAsJsonObject("sms_rules")
        return smsRules.entrySet().flatMap { (sender, senderNode) ->
            senderNode.asJsonObject.entrySet().flatMap { (type, typeNode) ->
                typeNode.asJsonObject.entrySet().map { (ruleKey, ruleNode) ->
                    val ruleObject = ruleNode.asJsonObject
                    SmsRegexRuleEntity(
                        senderAddress = sender,
                        type = type,
                        ruleKey = ruleKey,
                        bodyRegex = ruleObject.string("bodyRegex"),
                        amountGroup = ruleObject.string("amountGroup"),
                        storeGroup = ruleObject.string("storeGroup"),
                        cardGroup = ruleObject.string("cardGroup"),
                        dateGroup = ruleObject.string("dateGroup"),
                        priority = ruleObject.int("priority"),
                        status = ruleObject.string("status"),
                        source = ruleObject.string("source").ifBlank { "asset" },
                        version = ruleObject.int("version").takeIf { it > 0 } ?: 1
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

    private fun sampleTimestamp(): Long {
        return Calendar.getInstance(Locale.KOREA).apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.APRIL)
            set(Calendar.DAY_OF_MONTH, 24)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun JsonObject.string(key: String): String {
        return get(key)?.asString.orEmpty()
    }

    private fun JsonObject.int(key: String): Int {
        return get(key)?.asInt ?: 0
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        val latch = CountDownLatch(1)
        var outcome: Result<T>? = null
        block.startCoroutine(
            object : Continuation<T> {
                override val context = EmptyCoroutineContext

                override fun resumeWith(result: Result<T>) {
                    outcome = result
                    latch.countDown()
                }
            }
        )

        assertTrue("suspend block timed out", latch.await(5, TimeUnit.SECONDS))
        return outcome?.getOrThrow() ?: error("suspend block did not produce a result")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> uninitializedInstance(type: Class<T>): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return requireNotNull(type.cast(allocateInstance.invoke(field.get(null), type)))
    }

    private data class Sample(
        val issuer: String,
        val sender: String,
        val body: String,
        val amount: Int,
        val store: String,
        val card: String,
        val dateTime: String
    )

    private class FakeSmsRegexRuleDao(initialRules: List<SmsRegexRuleEntity>) : SmsRegexRuleDao {
        private val rules = initialRules.associateBy { it.key() }.toMutableMap()
        var matchCountUpdates: Int = 0
            private set

        override suspend fun insert(rule: SmsRegexRuleEntity) {
            rules[rule.key()] = rule
        }

        override suspend fun insertAll(rules: List<SmsRegexRuleEntity>) {
            rules.forEach { insert(it) }
        }

        override suspend fun getActiveRulesBySender(senderAddress: String): List<SmsRegexRuleEntity> {
            return rules.values
                .filter { it.senderAddress == senderAddress && it.status == "ACTIVE" }
                .sortedWith(ruleSort())
        }

        override suspend fun getActiveRulesBySenderAndType(
            senderAddress: String,
            type: String
        ): List<SmsRegexRuleEntity> {
            return rules.values
                .filter {
                    it.senderAddress == senderAddress &&
                        it.type == type &&
                        it.status == "ACTIVE"
                }
                .sortedWith(ruleSort())
        }

        override suspend fun getRule(
            senderAddress: String,
            type: String,
            ruleKey: String
        ): SmsRegexRuleEntity? {
            return rules[RuleKey(senderAddress, type, ruleKey)]
        }

        override suspend fun incrementMatchCount(
            senderAddress: String,
            type: String,
            ruleKey: String,
            timestamp: Long
        ): Int {
            val key = RuleKey(senderAddress, type, ruleKey)
            val rule = rules[key] ?: return 0
            rules[key] = rule.copy(
                matchCount = rule.matchCount + 1,
                lastMatchedAt = timestamp,
                updatedAt = timestamp,
                priority = (rule.priority + 10).coerceAtMost(1000),
                status = "ACTIVE"
            )
            matchCountUpdates += 1
            return 1
        }

        override suspend fun incrementFailCount(
            senderAddress: String,
            type: String,
            ruleKey: String,
            inactiveThreshold: Int,
            timestamp: Long
        ): Int {
            val key = RuleKey(senderAddress, type, ruleKey)
            val rule = rules[key] ?: return 0
            val failCount = rule.failCount + 1
            rules[key] = rule.copy(
                failCount = failCount,
                updatedAt = timestamp,
                priority = (rule.priority - 15).coerceAtLeast(0),
                status = if (rule.matchCount == 0 && failCount >= inactiveThreshold) {
                    "INACTIVE"
                } else {
                    rule.status
                }
            )
            return 1
        }

        override suspend fun updatePriority(
            senderAddress: String,
            type: String,
            ruleKey: String,
            priority: Int,
            timestamp: Long
        ): Int {
            val key = RuleKey(senderAddress, type, ruleKey)
            val rule = rules[key] ?: return 0
            rules[key] = rule.copy(priority = priority, updatedAt = timestamp)
            return 1
        }

        override suspend fun updateStatus(
            senderAddress: String,
            type: String,
            ruleKey: String,
            status: String,
            timestamp: Long
        ): Int {
            val key = RuleKey(senderAddress, type, ruleKey)
            val rule = rules[key] ?: return 0
            rules[key] = rule.copy(status = status, updatedAt = timestamp)
            return 1
        }

        override suspend fun deleteBySender(senderAddress: String): Int {
            val keys = rules.keys.filter { it.senderAddress == senderAddress }
            keys.forEach { rules.remove(it) }
            return keys.size
        }

        override suspend fun deleteAll() {
            rules.clear()
        }

        private fun SmsRegexRuleEntity.key(): RuleKey {
            return RuleKey(senderAddress, type, ruleKey)
        }

        private fun ruleSort(): Comparator<SmsRegexRuleEntity> {
            return compareByDescending<SmsRegexRuleEntity> { it.priority }
                .thenByDescending { it.lastMatchedAt }
                .thenByDescending { it.updatedAt }
        }

        private data class RuleKey(
            val senderAddress: String,
            val type: String,
            val ruleKey: String
        )
    }

    private class EmptySmsPatternDao : SmsPatternDao {
        override suspend fun insert(pattern: SmsPatternEntity): Long = 0L
        override suspend fun insertAll(patterns: List<SmsPatternEntity>) = Unit
        override suspend fun update(pattern: SmsPatternEntity) = Unit
        override suspend fun delete(pattern: SmsPatternEntity) = Unit
        override suspend fun getAllPaymentPatterns(): List<SmsPatternEntity> = emptyList()
        override suspend fun getAllNonPaymentPatterns(): List<SmsPatternEntity> = emptyList()
        override suspend fun getAllPatterns(): List<SmsPatternEntity> = emptyList()
        override suspend fun getPatternsBySender(address: String): List<SmsPatternEntity> = emptyList()
        override suspend fun getMainPatternBySender(address: String): SmsPatternEntity? = null
        override suspend fun getMainPatternsBySenders(addresses: List<String>): List<SmsPatternEntity> = emptyList()
        override suspend fun getPatternCount(): Int = 0
        override suspend fun getPaymentPatternCount(): Int = 0
        override suspend fun incrementMatchCount(patternId: Long, timestamp: Long) = Unit
        override suspend fun incrementMatchCountBy(patternId: Long, count: Int, timestamp: Long) = Unit
        override suspend fun deleteStalePatterns(threshold: Long) = Unit
        override suspend fun deleteAll() = Unit
        override fun observeAllPatterns(): Flow<List<SmsPatternEntity>> = flowOf(emptyList())
    }

    private class EmptySmsChannelProbeLogDao : SmsChannelProbeLogDao {
        override suspend fun insert(log: SmsChannelProbeLogEntity) = Unit
        override suspend fun deleteOlderThan(minCreatedAt: Long): Int = 0
    }
}
