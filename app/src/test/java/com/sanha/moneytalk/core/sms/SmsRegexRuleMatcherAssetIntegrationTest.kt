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
import com.sanha.moneytalk.core.firebase.GeminiApiKeyProvider
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
        val matcher = SmsRegexRuleMatcher(
            ruleRepository = SmsRegexRuleRepository(ruleDao),
            smsPatternDao = EmptySmsPatternDao(),
            templateEngine = SmsTemplateEngine(
                GeminiApiKeyProvider(uninitializedInstance(PremiumManager::class.java))
            ),
            originSampleCollector = SmsOriginSampleCollector(
                database = null,
                premiumManager = uninitializedInstance(PremiumManager::class.java)
            ),
            channelProbeCollector = SmsChannelProbeCollector(EmptySmsChannelProbeLogDao())
        )
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

    private fun coveredIssuerSamples(): List<Sample> {
        val dateTime = "2026-04-24 13:45"
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
                body = "삼성1234승인 홍길동\n12,300원 일시불\n04/24 13:45 스타벅스\n누적100,000원",
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
                issuer = "스마일카드",
                sender = "15220080",
                body = "스마일카드승인 홍길동 12,300원 일시불 04/24 13:45 스타벅스 누적100,000원",
                amount = 12_300,
                store = "스타벅스",
                card = "스마일카드",
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
