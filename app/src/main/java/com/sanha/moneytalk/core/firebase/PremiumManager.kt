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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 프리미엄 서비스 상태 관리자
 *
 * Firebase Realtime Database에서 서버 설정(PremiumConfig)을 실시간으로 감시하고,
 * 로컬 DataStore의 서비스 티어와 결합하여 현재 사용자의 서비스 상태를 관리합니다.
 *
 * ## 서비스 흐름
 * 1. **무료(FREE)**: 사용자가 직접 입력한 Gemini API 키 사용
 * 2. **프리미엄(PREMIUM)**: Firebase에서 제공하는 서버 API 키 사용
 * 3. **무료 차단**: 서버에서 free_tier_enabled=false 설정 시 무료 사용자 차단
 *
 * ## Firebase Realtime Database 구조
 * ```
 * /config
 *   /gemini_api_key: "서버 관리 API 키"
 *   /free_tier_enabled: true|false
 *   /service_enabled: true|false
 *   /maintenance_message: "점검 메시지"
 * ```
 */
@Singleton
class PremiumManager @Inject constructor(
    private val database: FirebaseDatabase,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "PremiumManager"
        private const val CONFIG_PATH = "config"
    }

    private val _premiumConfig = MutableStateFlow(PremiumConfig())
    val premiumConfig: StateFlow<PremiumConfig> = _premiumConfig.asStateFlow()

    private var configListener: ValueEventListener? = null

    /**
     * Firebase Realtime Database의 /config 경로를 실시간 감시 시작
     */
    fun startObservingConfig() {
        val configRef = database.getReference(CONFIG_PATH)

        configListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val config = PremiumConfig(
                    geminiApiKey = snapshot.child("gemini_api_key").getValue(String::class.java) ?: "",
                    freeTierEnabled = snapshot.child("free_tier_enabled").getValue(Boolean::class.java) ?: true,
                    serviceEnabled = snapshot.child("service_enabled").getValue(Boolean::class.java) ?: true,
                    maintenanceMessage = snapshot.child("maintenance_message").getValue(String::class.java) ?: ""
                )
                _premiumConfig.value = config
                Log.d(TAG, "서버 설정 갱신: freeTierEnabled=${config.freeTierEnabled}, serviceEnabled=${config.serviceEnabled}")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "서버 설정 감시 실패: ${error.message}")
            }
        }

        configRef.addValueEventListener(configListener!!)
        Log.d(TAG, "서버 설정 실시간 감시 시작")
    }

    /**
     * 실시간 감시 중지
     */
    fun stopObservingConfig() {
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
            tier == ServiceTier.PREMIUM && config.geminiApiKey.isBlank() -> ServiceStatus.PremiumKeyUnavailable
            else -> ServiceStatus.Active(tier)
        }
    }

    /**
     * 현재 서비스 티어에 맞는 Gemini API 키 반환
     */
    suspend fun getEffectiveApiKey(): String {
        val tier = settingsDataStore.getServiceTier()
        val config = _premiumConfig.value

        return when {
            !config.serviceEnabled -> ""
            tier == ServiceTier.PREMIUM && config.geminiApiKey.isNotBlank() -> config.geminiApiKey
            tier == ServiceTier.FREE && config.freeTierEnabled -> settingsDataStore.getGeminiApiKey()
            tier == ServiceTier.FREE && !config.freeTierEnabled -> ""
            else -> settingsDataStore.getGeminiApiKey()
        }
    }

    /**
     * 서버 설정을 한 번만 가져오기 (Flow 대신 단발성)
     */
    fun fetchConfigOnce(onResult: (PremiumConfig) -> Unit) {
        database.getReference(CONFIG_PATH).get()
            .addOnSuccessListener { snapshot ->
                val config = PremiumConfig(
                    geminiApiKey = snapshot.child("gemini_api_key").getValue(String::class.java) ?: "",
                    freeTierEnabled = snapshot.child("free_tier_enabled").getValue(Boolean::class.java) ?: true,
                    serviceEnabled = snapshot.child("service_enabled").getValue(Boolean::class.java) ?: true,
                    maintenanceMessage = snapshot.child("maintenance_message").getValue(String::class.java) ?: ""
                )
                _premiumConfig.value = config
                onResult(config)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "서버 설정 1회 조회 실패: ${e.message}")
                onResult(PremiumConfig())
            }
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
    /** 프리미엄 키가 서버에 아직 설정되지 않음 */
    data object PremiumKeyUnavailable : ServiceStatus()
}
