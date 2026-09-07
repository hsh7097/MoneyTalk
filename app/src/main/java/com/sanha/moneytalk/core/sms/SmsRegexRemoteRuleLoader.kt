package com.sanha.moneytalk.core.sms

import com.google.firebase.database.FirebaseDatabase
import com.sanha.moneytalk.core.database.entity.SmsRegexRuleEntity
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RTDB sender 기반 regex 룰 로더
 *
 * 경로: /sms_rules/{sender}/{type}/{ruleKey}
 */
@Singleton
class SmsRegexRemoteRuleLoader @Inject constructor(
    private val database: FirebaseDatabase?
) {
    companion object {
        private const val RULES_PATH = "sms_rules"
        private const val CACHE_TTL_MS = 10L * 60L * 1000L
    }

    @Volatile
    private var cachedRules: List<SmsRegexRuleEntity>? = null

    @Volatile
    private var cacheTimestamp: Long = 0L

    suspend fun loadRules(forceRefresh: Boolean = false): List<SmsRegexRuleEntity> {
        val now = System.currentTimeMillis()
        val cached = cachedRules
        if (!forceRefresh && cached != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
            return cached
        }

        val db = database ?: return emptyList()
        return try {
            val loaded = withContext(Dispatchers.IO) {
                val snapshot = db.getReference(RULES_PATH).get().await()
                parseRules(snapshot.value)
            }
            cachedRules = loaded
            cacheTimestamp = now
            MoneyTalkLogger.i("RTDB 룰 로드 완료: ${loaded.size}건")
            loaded
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MoneyTalkLogger.w("RTDB 룰 로드 실패: ${e.message}")
            emptyList()
        }
    }

    fun invalidateCache() {
        cachedRules = null
        cacheTimestamp = 0L
    }

    internal fun parseRules(value: Any?): List<SmsRegexRuleEntity> {
        val senderNodes = value as? Map<*, *> ?: return emptyList()
        val now = System.currentTimeMillis()
        val result = mutableListOf<SmsRegexRuleEntity>()

        for ((senderKey, senderNode) in senderNodes) {
            val senderRaw = senderKey as? String ?: continue
            val sender = SmsFilter.normalizeAddress(senderRaw)
            val typeNodes = senderNode as? Map<*, *> ?: continue

            for ((typeKey, typeNode) in typeNodes) {
                val type = typeKey as? String ?: continue
                val ruleNodes = typeNode as? Map<*, *> ?: continue

                for ((ruleNodeKey, ruleNode) in ruleNodes) {
                    val ruleKey = ruleNodeKey as? String ?: continue
                    val rule = ruleNode as? Map<*, *> ?: continue
                    try {
                        val rawBodyRegex = rule.getString("bodyRegex")
                        if (rawBodyRegex.isBlank()) continue
                        val bodyRegex = normalizeBodyRegex(rawBodyRegex)
                        if (bodyRegex.isBlank()) {
                            MoneyTalkLogger.w(
                                "RTDB 룰 스킵: invalid bodyRegex sender=$sender type=$type ruleKey=$ruleKey"
                            )
                            continue
                        }

                        result.add(
                            SmsRegexRuleEntity(
                                senderAddress = sender,
                                type = type,
                                ruleKey = ruleKey,
                                bodyRegex = bodyRegex,
                                amountGroup = rule.getString("amountGroup"),
                                storeGroup = rule.getString("storeGroup"),
                                cardGroup = rule.getString("cardGroup"),
                                dateGroup = rule.getString("dateGroup"),
                                priority = rule.getInt("priority", 0),
                                status = rule.getString("status", "ACTIVE"),
                                source = rule.getString("source", "rtdb"),
                                version = rule.getInt("version", 1),
                                matchCount = rule.getInt("matchCount", 0),
                                failCount = rule.getInt("failCount", 0),
                                lastMatchedAt = rule.getLong("lastMatchedAt", 0L),
                                updatedAt = rule.getLong("updatedAt", now),
                                createdAt = rule.getLong("createdAt", now)
                            )
                        )
                    } catch (_: IllegalArgumentException) {
                        MoneyTalkLogger.w("RTDB 룰 스킵: invalid field sender=$sender type=$type ruleKey=$ruleKey")
                    } catch (_: ArithmeticException) {
                        MoneyTalkLogger.w("RTDB 룰 스킵: invalid number sender=$sender type=$type ruleKey=$ruleKey")
                    }
                }
            }
        }
        return result
    }

    private fun normalizeBodyRegex(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        if (isCompilableRegex(trimmed)) return trimmed

        val deEscaped = decodeOverEscapedRegex(trimmed)
        if (isCompilableRegex(deEscaped)) return deEscaped
        return ""
    }

    private fun isCompilableRegex(pattern: String): Boolean {
        return runCatching { Regex(pattern) }.isSuccess
    }

    private fun decodeOverEscapedRegex(pattern: String): String {
        var normalized = pattern
        val replacements = listOf(
            """\\d""" to """\d""",
            """\\D""" to """\D""",
            """\\s""" to """\s""",
            """\\S""" to """\S""",
            """\\w""" to """\w""",
            """\\W""" to """\W""",
            """\\n""" to """\n""",
            """\\t""" to """\t""",
            """\\r""" to """\r""",
            """\\(""" to """\(""",
            """\\)""" to """\)""",
            """\\[""" to """\[""",
            """\\]""" to """\]""",
            """\\{""" to """\{""",
            """\\}""" to """\}""",
            """\\*""" to """\*""",
            """\\+""" to """\+""",
            """\\?""" to """\?""",
            """\\|""" to """\|"""
        )
        replacements.forEach { (from, to) ->
            normalized = normalized.replace(from, to)
        }
        return normalized
    }
}

private fun Map<*, *>.getString(key: String, defaultValue: String = ""): String {
    val value = get(key) ?: return defaultValue
    require(value is String) { "Invalid string field: $key" }
    return value
}

private fun Map<*, *>.getInt(key: String, defaultValue: Int = 0): Int {
    val value = get(key) ?: return defaultValue
    require(value is Number) { "Invalid numeric field: $key" }
    return value.toString().toBigDecimal().intValueExact()
}

private fun Map<*, *>.getLong(key: String, defaultValue: Long = 0L): Long {
    val value = get(key) ?: return defaultValue
    require(value is Number) { "Invalid numeric field: $key" }
    return value.toString().toBigDecimal().longValueExact()
}
