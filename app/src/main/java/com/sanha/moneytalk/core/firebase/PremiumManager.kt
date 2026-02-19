package com.sanha.moneytalk.core.firebase

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 프리미엄 서비스 상태 관리자
 *
 * Firebase Realtime Database에서 서버 설정(PremiumConfig)을 실시간으로 감시하고,
 * API 키 풀링(라운드로빈) 및 모델명 원격 관리를 제공합니다.
 *
 * ## API 키 결정 로직
 * 1. 서비스 비활성화 → 빈 문자열
 * 2. gemini_api_keys 배열이 있으면 → 라운드로빈으로 키 선택
 * 3. gemini_api_key 단일 키가 있으면 → 해당 키 사용
 * 4. 키가 없으면 → 빈 문자열 (서비스 불가)
 *
 * ## Firebase Realtime Database 구조
 * ```
 * /config
 *   /gemini_api_key: "단일 키 (하위호환)"
 *   /gemini_api_keys: ["key1", "key2", ...]
 *   /models/query_analyzer: "gemini-2.5-pro"
 *   /models/financial_advisor: "gemini-2.5-pro"
 *   /models/summary: "gemini-2.5-flash"
 *   /models/... (기타 모델)
 *   /free_tier_enabled: true|false
 *   /service_enabled: true|false
 *   /maintenance_message: "점검 메시지"
 * ```
 */
@Singleton
class PremiumManager @Inject constructor(
    private val database: FirebaseDatabase?,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "PremiumManager"
        private const val CONFIG_PATH = "config"
    }

    private val _premiumConfig = MutableStateFlow(PremiumConfig())
    val premiumConfig: StateFlow<PremiumConfig> = _premiumConfig.asStateFlow()

    private var configListener: ValueEventListener? = null

    /** 라운드로빈 키 인덱스 (thread-safe) */
    private val keyIndex = AtomicInteger(0)

    /**
     * Firebase Realtime Database의 /config 경로를 실시간 감시 시작
     */
    fun startObservingConfig() {
        if (database == null) {
            Log.w(TAG, "Firebase 미설정 — 서버 설정 감시 스킵")
            return
        }
        val configRef = database.getReference(CONFIG_PATH)

        configListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val config = parseConfig(snapshot)
                _premiumConfig.value = config
                Log.d(
                    TAG,
                    "서버 설정 갱신: serviceEnabled=${config.serviceEnabled}, " +
                        "apiKeys=${config.geminiApiKeys.size}개, " +
                        "models=${config.modelConfig}"
                )
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "서버 설정 감시 실패: ${error.message}")
            }
        }

        configListener?.let { configRef.addValueEventListener(it) }
        Log.d(TAG, "서버 설정 실시간 감시 시작")
    }

    /**
     * 실시간 감시 중지
     */
    fun stopObservingConfig() {
        if (database == null) return
        configListener?.let {
            database.getReference(CONFIG_PATH).removeEventListener(it)
            configListener = null
            Log.d(TAG, "서버 설정 실시간 감시 중지")
        }
    }

    /**
     * 현재 서비스 티어 Flow (DataStore 기반)
     */
    val serviceTierFlow: Flow<ServiceTier> = settingsDataStore.serviceTierFlow

    /**
     * 서비스 티어 변경 (무료 → 프리미엄 또는 반대)
     */
    suspend fun setServiceTier(tier: ServiceTier) {
        settingsDataStore.saveServiceTier(tier.name)
        Log.d(TAG, "서비스 티어 변경: $tier")
    }

    /**
     * 현재 서비스 상태를 종합한 Flow
     * - 서비스 티어 + 서버 설정을 결합하여 실제 사용 가능 상태 판단
     */
    val serviceStatusFlow: Flow<ServiceStatus> = combine(
        serviceTierFlow,
        _premiumConfig
    ) { tier, config ->
        when {
            !config.serviceEnabled -> ServiceStatus.Maintenance(config.maintenanceMessage)
            tier == ServiceTier.FREE && !config.freeTierEnabled -> ServiceStatus.FreeTierBlocked
            !hasAvailableKeys(config) -> ServiceStatus.PremiumKeyUnavailable
            else -> ServiceStatus.Active(tier)
        }
    }

    /**
     * RTDB 키 풀에서 라운드로빈으로 API 키 반환
     *
     * 우선순위:
     * 1. gemini_api_keys 배열 (라운드로빈)
     * 2. gemini_api_key 단일 키 (하위호환)
     * 3. 빈 문자열 (키 없음)
     */
    suspend fun getEffectiveApiKey(): String {
        val config = _premiumConfig.value
        if (!config.serviceEnabled) return ""

        val keys = getAvailableKeys(config)
        return if (keys.isNotEmpty()) {
            keys[keyIndex.getAndIncrement().mod(keys.size)]
        } else {
            ""
        }
    }

    /**
     * 서버 설정을 한 번만 가져오기 (Flow 대신 단발성)
     */
    fun fetchConfigOnce(onResult: (PremiumConfig) -> Unit) {
        if (database == null) {
            Log.w(TAG, "Firebase 미설정 — 기본 설정 반환")
            onResult(PremiumConfig())
            return
        }
        database.getReference(CONFIG_PATH).get()
            .addOnSuccessListener { snapshot ->
                val config = parseConfig(snapshot)
                _premiumConfig.value = config
                onResult(config)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "서버 설정 1회 조회 실패: ${e.message}")
                onResult(PremiumConfig())
            }
    }

    /**
     * DataSnapshot에서 PremiumConfig 파싱 (중복 제거용 헬퍼)
     */
    private fun parseConfig(snapshot: DataSnapshot): PremiumConfig {
        // API 키 풀 파싱
        val apiKeys = snapshot.child("gemini_api_keys").children
            .mapNotNull { it.getValue(String::class.java) }
            .filter { it.isNotBlank() }

        // 모델 설정 파싱
        val modelsSnapshot = snapshot.child("models")
        val modelConfig = GeminiModelConfig(
            queryAnalyzer = modelsSnapshot.child("query_analyzer")
                .getValue(String::class.java) ?: GeminiModelConfig.DEFAULT_QUERY_ANALYZER,
            financialAdvisor = modelsSnapshot.child("financial_advisor")
                .getValue(String::class.java) ?: GeminiModelConfig.DEFAULT_FINANCIAL_ADVISOR,
            summary = modelsSnapshot.child("summary")
                .getValue(String::class.java) ?: GeminiModelConfig.DEFAULT_SUMMARY,
            homeInsight = modelsSnapshot.child("home_insight")
                .getValue(String::class.java) ?: GeminiModelConfig.DEFAULT_HOME_INSIGHT,
            categoryClassifier = modelsSnapshot.child("category_classifier")
                .getValue(String::class.java) ?: GeminiModelConfig.DEFAULT_CATEGORY_CLASSIFIER,
            smsExtractor = modelsSnapshot.child("sms_extractor")
                .getValue(String::class.java) ?: GeminiModelConfig.DEFAULT_SMS_EXTRACTOR,
            smsBatchExtractor = modelsSnapshot.child("sms_batch_extractor")
                .getValue(String::class.java) ?: GeminiModelConfig.DEFAULT_SMS_BATCH_EXTRACTOR,
            embedding = modelsSnapshot.child("embedding")
                .getValue(String::class.java) ?: GeminiModelConfig.DEFAULT_EMBEDDING
        )

        return PremiumConfig(
            geminiApiKey = snapshot.child("gemini_api_key").getValue(String::class.java) ?: "",
            geminiApiKeys = apiKeys,
            freeTierEnabled = snapshot.child("free_tier_enabled").getValue(Boolean::class.java) ?: true,
            serviceEnabled = snapshot.child("service_enabled").getValue(Boolean::class.java) ?: true,
            maintenanceMessage = snapshot.child("maintenance_message").getValue(String::class.java) ?: "",
            rewardAdEnabled = snapshot.child("reward_ad_enabled").getValue(Boolean::class.java) ?: false,
            rewardAdChatCount = snapshot.child("reward_ad_chat_count").getValue(Int::class.java) ?: 5,
            minVersionCode = snapshot.child("min_version_code").getValue(Int::class.java) ?: 1,
            minVersionName = snapshot.child("min_version_name").getValue(String::class.java) ?: "1.0.0",
            forceUpdateMessage = snapshot.child("force_update_message").getValue(String::class.java) ?: "",
            modelConfig = modelConfig
        )
    }

    /** 사용 가능한 키 목록 반환 (풀 우선, 단일 키 fallback) */
    private fun getAvailableKeys(config: PremiumConfig): List<String> {
        return config.geminiApiKeys.ifEmpty {
            if (config.geminiApiKey.isNotBlank()) listOf(config.geminiApiKey) else emptyList()
        }
    }

    /** 사용 가능한 키가 있는지 확인 */
    private fun hasAvailableKeys(config: PremiumConfig): Boolean {
        return getAvailableKeys(config).isNotEmpty()
    }
}

/**
 * 서비스 상태 sealed class
 */
sealed class ServiceStatus {
    /** 서비스 정상 사용 가능 */
    data class Active(val tier: ServiceTier) : ServiceStatus()
    /** 서버 점검 중 */
    data class Maintenance(val message: String) : ServiceStatus()
    /** 무료 티어 차단됨 (프리미엄 전환 필요) */
    data object FreeTierBlocked : ServiceStatus()
    /** API 키가 서버에 아직 설정되지 않음 */
    data object PremiumKeyUnavailable : ServiceStatus()
}
