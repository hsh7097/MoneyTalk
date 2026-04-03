package com.sanha.moneytalk.core.sms

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sanha.moneytalk.core.database.entity.SmsRegexRuleEntity
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱 내 assets JSON에서 sender 기반 regex 룰을 로드합니다.
 *
 * 목표 구조:
 * sms_rules/{sender}/{type}/{ruleKey}
 */
@Singleton
class SmsRegexRuleAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        /** 기본 룰 파일명 (app/src/main/assets) */
        const val DEFAULT_ASSET_FILE = "sms_rules_v1.json"
    }

    suspend fun loadRules(fileName: String = DEFAULT_ASSET_FILE): List<SmsRegexRuleEntity> =
        withContext(Dispatchers.IO) {
            try {
                val jsonText = context.assets.open(fileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
                parseRules(jsonText)
            } catch (e: Exception) {
                MoneyTalkLogger.w("Asset 룰 로드 실패: ${e.message}")
                emptyList()
            }
        }

    private fun parseRules(jsonText: String): List<SmsRegexRuleEntity> {
        if (jsonText.isBlank()) return emptyList()
        val root = JsonParser.parseString(jsonText).asJsonObject
        val smsRules = root.getAsJsonObjectOrNull("sms_rules") ?: return emptyList()
        val now = System.currentTimeMillis()
        val result = mutableListOf<SmsRegexRuleEntity>()

        for ((senderAddress, senderNode) in smsRules.entrySet()) {
            val senderObject = senderNode.asJsonObjectOrNull() ?: continue
            val normalizedSender = SmsFilter.normalizeAddress(senderAddress)

            for ((type, typeNode) in senderObject.entrySet()) {
                val rulesObject = typeNode.asJsonObjectOrNull() ?: continue

                for ((ruleKey, ruleNode) in rulesObject.entrySet()) {
                    val ruleObject = ruleNode.asJsonObjectOrNull() ?: continue
                    val bodyRegex = ruleObject.getAsStringOrEmpty("bodyRegex")
                    if (bodyRegex.isBlank()) continue

                    result.add(
                        SmsRegexRuleEntity(
                            senderAddress = normalizedSender,
                            type = type,
                            ruleKey = ruleKey,
                            bodyRegex = bodyRegex,
                            amountGroup = ruleObject.getAsStringOrEmpty("amountGroup"),
                            storeGroup = ruleObject.getAsStringOrEmpty("storeGroup"),
                            cardGroup = ruleObject.getAsStringOrEmpty("cardGroup"),
                            dateGroup = ruleObject.getAsStringOrEmpty("dateGroup"),
                            priority = ruleObject.getAsIntOrDefault("priority", 0),
                            status = ruleObject.getAsStringOrDefault("status", "ACTIVE"),
                            source = ruleObject.getAsStringOrDefault("source", "asset"),
                            version = ruleObject.getAsIntOrDefault("version", 1),
                            matchCount = ruleObject.getAsIntOrDefault("matchCount", 0),
                            failCount = ruleObject.getAsIntOrDefault("failCount", 0),
                            lastMatchedAt = ruleObject.getAsLongOrDefault("lastMatchedAt", 0L),
                            updatedAt = ruleObject.getAsLongOrDefault("updatedAt", now),
                            createdAt = ruleObject.getAsLongOrDefault("createdAt", now)
                        )
                    )
                }
            }
        }
        MoneyTalkLogger.i("Asset 룰 로드 완료: ${result.size}건")
        return result
    }
}

private fun JsonObject.getAsJsonObjectOrNull(key: String): JsonObject? {
    val element = get(key) ?: return null
    return if (element.isJsonObject) element.asJsonObject else null
}

private fun com.google.gson.JsonElement.asJsonObjectOrNull(): JsonObject? {
    return if (isJsonObject) asJsonObject else null
}

private fun JsonObject.getAsStringOrEmpty(key: String): String {
    val value = get(key) ?: return ""
    return runCatching { value.asString }.getOrDefault("")
}

private fun JsonObject.getAsStringOrDefault(key: String, defaultValue: String): String {
    val value = get(key) ?: return defaultValue
    return runCatching { value.asString }.getOrDefault(defaultValue)
}

private fun JsonObject.getAsIntOrDefault(key: String, defaultValue: Int): Int {
    val value = get(key) ?: return defaultValue
    return runCatching { value.asInt }.getOrDefault(defaultValue)
}

private fun JsonObject.getAsLongOrDefault(key: String, defaultValue: Long): Long {
    val value = get(key) ?: return defaultValue
    return runCatching { value.asLong }.getOrDefault(defaultValue)
}
