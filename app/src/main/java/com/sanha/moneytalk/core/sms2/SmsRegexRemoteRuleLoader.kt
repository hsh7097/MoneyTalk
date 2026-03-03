package com.sanha.moneytalk.core.sms2

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.sanha.moneytalk.core.database.entity.SmsRegexRuleEntity
import com.sanha.moneytalk.core.util.MoneyTalkLogger
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
                parseSnapshot(snapshot)
            }
            cachedRules = loaded
            cacheTimestamp = now
            MoneyTalkLogger.i("RTDB 룰 로드 완료: ${loaded.size}건")
            loaded
        } catch (e: Exception) {
            MoneyTalkLogger.w("RTDB 룰 로드 실패: ${e.message}")
            emptyList()
        }
    }

    fun invalidateCache() {
        cachedRules = null
        cacheTimestamp = 0L
    }

    private fun parseSnapshot(snapshot: DataSnapshot): List<SmsRegexRuleEntity> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<SmsRegexRuleEntity>()

        for (senderSnapshot in snapshot.children) {
            val senderRaw = senderSnapshot.key ?: continue
            val sender = SmsFilter.normalizeAddress(senderRaw)

            for (typeSnapshot in senderSnapshot.children) {
                val type = typeSnapshot.key ?: continue

                for (ruleSnapshot in typeSnapshot.children) {
                    val ruleKey = ruleSnapshot.key ?: continue
                    val bodyRegex = ruleSnapshot.getString("bodyRegex")
                    if (bodyRegex.isNullOrBlank()) continue

                    result.add(
                        SmsRegexRuleEntity(
                            senderAddress = sender,
                            type = type,
                            ruleKey = ruleKey,
                            bodyRegex = bodyRegex,
                            amountGroup = ruleSnapshot.getString("amountGroup"),
                            storeGroup = ruleSnapshot.getString("storeGroup"),
                            cardGroup = ruleSnapshot.getString("cardGroup"),
                            dateGroup = ruleSnapshot.getString("dateGroup"),
                            priority = ruleSnapshot.getInt("priority", 0),
                            status = ruleSnapshot.getString("status", "ACTIVE"),
                            source = ruleSnapshot.getString("source", "rtdb"),
                            version = ruleSnapshot.getInt("version", 1),
                            matchCount = ruleSnapshot.getInt("matchCount", 0),
                            failCount = ruleSnapshot.getInt("failCount", 0),
                            lastMatchedAt = ruleSnapshot.getLong("lastMatchedAt", 0L),
                            updatedAt = ruleSnapshot.getLong("updatedAt", now),
                            createdAt = ruleSnapshot.getLong("createdAt", now)
                        )
                    )
                }
            }
        }
        return result
    }
}

private fun DataSnapshot.getString(key: String, defaultValue: String = ""): String {
    return child(key).getValue(String::class.java) ?: defaultValue
}

private fun DataSnapshot.getInt(key: String, defaultValue: Int = 0): Int {
    return child(key).getValue(Int::class.java)
        ?: child(key).getValue(Long::class.java)?.toInt()
        ?: defaultValue
}

private fun DataSnapshot.getLong(key: String, defaultValue: Long = 0L): Long {
    return child(key).getValue(Long::class.java)
        ?: child(key).getValue(Int::class.java)?.toLong()
        ?: defaultValue
}
