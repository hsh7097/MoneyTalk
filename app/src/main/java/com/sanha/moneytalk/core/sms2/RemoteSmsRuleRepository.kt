package com.sanha.moneytalk.core.sms2

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ===== 원격 SMS 정규식 룰 리포지토리 =====
 *
 * RTDB `/sms_regex_rules/v1/` 경로에서 정제된 regex 룰을 로드.
 * 메모리 캐시 + TTL(10분)로 동기화 중 반복 네트워크 호출 방지.
 *
 * 사용처: SmsPatternMatcher.matchPatterns() — 로컬 패턴 미매칭 시 2순위 매칭
 *
 * 안정성:
 * - RTDB 실패 시 빈 리스트 반환 (예외 전파 금지)
 * - enabled=true 룰만 로드
 * - main thread 블로킹 없음 (suspend + Dispatchers.IO)
 */
@Singleton
class RemoteSmsRuleRepository @Inject constructor(
    private val database: FirebaseDatabase?
) {
    companion object {
        private const val TAG = "RemoteSmsRuleRepo"

        /** RTDB 규칙 경로 */
        private const val RULES_PATH = "sms_regex_rules/v1"

        /** 캐시 TTL (10분) */
        private const val CACHE_TTL_MS = 10L * 60 * 1000
    }

    /** 메모리 캐시: sender별 룰 리스트 */
    private var cachedRules: Map<String, List<RemoteSmsRule>> = emptyMap()
    private var cacheTimestamp: Long = 0L

    /**
     * 모든 원격 룰 로드 (캐시 우선, TTL 내 재사용)
     *
     * @return sender별 룰 맵 (빈 맵 = 룰 없음 또는 실패)
     */
    suspend fun loadRules(): Map<String, List<RemoteSmsRule>> {
        // 캐시 TTL 내면 재사용
        val now = System.currentTimeMillis()
        if (cachedRules.isNotEmpty() && (now - cacheTimestamp) < CACHE_TTL_MS) {
            return cachedRules
        }

        val db = database ?: return emptyMap()

        return try {
            withContext(Dispatchers.IO) {
                val snapshot = db.getReference(RULES_PATH).get().await()
                val rules = parseRules(snapshot)

                // sender별로 그룹핑
                val grouped = rules.groupBy { it.normalizedSenderAddress }
                cachedRules = grouped
                cacheTimestamp = now

                val totalRules = rules.size
                val senderCount = grouped.size
                Log.d(TAG, "원격 룰 로드 완료: ${totalRules}건 (${senderCount}개 발신번호)")
                for ((sender, senderRules) in grouped) {
                    Log.d(TAG, "  [$sender] ${senderRules.size}건")
                }

                grouped
            }
        } catch (e: Exception) {
            Log.w(TAG, "원격 룰 로드 실패 (빈 룰 반환): ${e.message}")
            emptyMap()
        }
    }

    /**
     * 특정 발신번호의 원격 룰 조회 (캐시에서)
     *
     * loadRules() 호출 후 사용.
     * 캐시가 없으면 빈 리스트 반환.
     */
    fun getRulesForSender(normalizedSender: String): List<RemoteSmsRule> {
        return cachedRules[normalizedSender].orEmpty()
    }

    /**
     * 캐시 무효화 (테스트/디버그용)
     */
    fun invalidateCache() {
        cachedRules = emptyMap()
        cacheTimestamp = 0L
    }

    /**
     * RTDB DataSnapshot을 RemoteSmsRule 리스트로 파싱
     *
     * 경로 구조: /sms_regex_rules/v1/{normalizedSender}/{ruleId}
     * enabled=false 룰은 필터링
     */
    private fun parseRules(snapshot: DataSnapshot): List<RemoteSmsRule> {
        val rules = mutableListOf<RemoteSmsRule>()

        for (senderSnapshot in snapshot.children) {
            val sender = senderSnapshot.key ?: continue

            for (ruleSnapshot in senderSnapshot.children) {
                try {
                    val rule = parseRule(ruleSnapshot, sender)
                    if (rule != null && rule.enabled) {
                        rules.add(rule)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "룰 파싱 실패: $sender/${ruleSnapshot.key} - ${e.message}")
                }
            }
        }

        return rules
    }

    /**
     * 단일 룰 DataSnapshot → RemoteSmsRule 변환
     *
     * 필수 필드: embedding, amountRegex, storeRegex
     * 하나라도 없으면 null 반환 (불완전 룰 무시)
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseRule(snapshot: DataSnapshot, sender: String): RemoteSmsRule? {
        val ruleId = snapshot.key ?: return null
        val data = snapshot.value as? Map<*, *> ?: return null

        // embedding 파싱 (List<Double/Long> → List<Float>)
        val rawEmbedding = data["embedding"] as? List<*> ?: return null
        val embedding = rawEmbedding.mapNotNull { value ->
            when (value) {
                is Double -> value.toFloat()
                is Long -> value.toFloat()
                is Float -> value
                is Number -> value.toFloat()
                else -> null
            }
        }
        if (embedding.isEmpty()) return null

        val amountRegex = data["amountRegex"] as? String ?: return null
        val storeRegex = data["storeRegex"] as? String ?: return null
        if (amountRegex.isBlank() || storeRegex.isBlank()) return null

        val cardRegex = data["cardRegex"] as? String ?: ""
        val minSimilarity = (data["minSimilarity"] as? Number)?.toFloat()
            ?: RemoteSmsRule.DEFAULT_MIN_SIMILARITY
        val enabled = data["enabled"] as? Boolean ?: true
        val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L

        return RemoteSmsRule(
            ruleId = ruleId,
            normalizedSenderAddress = sender,
            embedding = embedding,
            amountRegex = amountRegex,
            storeRegex = storeRegex,
            cardRegex = cardRegex,
            minSimilarity = minSimilarity,
            enabled = enabled,
            updatedAt = updatedAt
        )
    }
}
