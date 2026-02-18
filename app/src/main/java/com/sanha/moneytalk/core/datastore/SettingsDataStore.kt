package com.sanha.moneytalk.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sanha.moneytalk.BuildConfig
import com.sanha.moneytalk.core.firebase.ServiceTier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val CLAUDE_API_KEY = stringPreferencesKey("claude_api_key")
        private val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        private val MONTHLY_INCOME = intPreferencesKey("monthly_income")
        private val LAST_SYNC_TIME = longPreferencesKey("last_sync_time")
        private val MONTH_START_DAY = intPreferencesKey("month_start_day")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val CHAT_VOICE_HINT_SEEN = booleanPreferencesKey("chat_voice_hint_seen")
        private val SERVICE_TIER = stringPreferencesKey("service_tier")
        private val REWARD_CHAT_REMAINING = intPreferencesKey("reward_chat_remaining")
        private val FULL_SYNC_UNLOCKED = booleanPreferencesKey("full_sync_unlocked")
    }

    // API 키 저장
    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[CLAUDE_API_KEY] = apiKey
        }
    }

    // API 키 가져오기 (DataStore에 없으면 BuildConfig에서 가져옴)
    val apiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[CLAUDE_API_KEY] ?: BuildConfig.CLAUDE_API_KEY
    }

    // API 키 즉시 가져오기
    suspend fun getApiKey(): String {
        val storedKey = context.dataStore.data.first()[CLAUDE_API_KEY]
        return if (storedKey.isNullOrBlank()) {
            BuildConfig.CLAUDE_API_KEY
        } else {
            storedKey
        }
    }

    // Gemini API 키 저장
    @Deprecated("API 키는 Firebase RTDB에서 관리됩니다")
    suspend fun saveGeminiApiKey(apiKey: String) {
        // RTDB 기반 키 관리로 전환 — 로컬 키 저장 제거
    }

    // Gemini API 키 가져오기 (DataStore에 없으면 BuildConfig에서 가져옴)
    @Deprecated("API 키는 Firebase RTDB에서 관리됩니다")
    val geminiApiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GEMINI_API_KEY] ?: BuildConfig.GEMINI_API_KEY
    }

    // Gemini API 키 즉시 가져오기
    @Deprecated("API 키는 Firebase RTDB에서 관리됩니다")
    suspend fun getGeminiApiKey(): String {
        val storedKey = context.dataStore.data.first()[GEMINI_API_KEY]
        return if (storedKey.isNullOrBlank()) {
            BuildConfig.GEMINI_API_KEY
        } else {
            storedKey
        }
    }

    // 월 수입 저장
    suspend fun saveMonthlyIncome(income: Int) {
        context.dataStore.edit { preferences ->
            preferences[MONTHLY_INCOME] = income
        }
    }

    // 월 수입 가져오기
    val monthlyIncomeFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MONTHLY_INCOME] ?: 0
    }

    suspend fun getMonthlyIncome(): Int {
        return context.dataStore.data.first()[MONTHLY_INCOME] ?: 0
    }

    // 마지막 동기화 시간 저장
    suspend fun saveLastSyncTime(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SYNC_TIME] = timestamp
        }
    }

    // 마지막 동기화 시간 가져오기
    suspend fun getLastSyncTime(): Long {
        return context.dataStore.data.first()[LAST_SYNC_TIME] ?: 0L
    }

    // 월 시작일 저장 (1-31)
    suspend fun saveMonthStartDay(day: Int) {
        context.dataStore.edit { preferences ->
            preferences[MONTH_START_DAY] = day.coerceIn(1, 31)
        }
    }

    // 월 시작일 가져오기 (기본값: 1일)
    val monthStartDayFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MONTH_START_DAY] ?: 1
    }

    suspend fun getMonthStartDay(): Int {
        return context.dataStore.data.first()[MONTH_START_DAY] ?: 1
    }

    // 테마 모드 저장 (SYSTEM, LIGHT, DARK)
    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }

    // 테마 모드 가져오기 (기본값: SYSTEM)
    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "SYSTEM"
    }

    // 채팅 음성 힌트 표시 여부 저장 (true=이미 표시됨)
    suspend fun setChatVoiceHintSeen(seen: Boolean = true) {
        context.dataStore.edit { preferences ->
            preferences[CHAT_VOICE_HINT_SEEN] = seen
        }
    }

    // 채팅 음성 힌트 표시 여부 가져오기 (기본값: false)
    val chatVoiceHintSeenFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[CHAT_VOICE_HINT_SEEN] ?: false
    }

    // 서비스 티어 저장 (FREE / PREMIUM)
    suspend fun saveServiceTier(tier: String) {
        context.dataStore.edit { preferences ->
            preferences[SERVICE_TIER] = tier
        }
    }

    // 서비스 티어 가져오기 (기본값: FREE)
    val serviceTierFlow: Flow<ServiceTier> = context.dataStore.data.map { preferences ->
        ServiceTier.fromString(preferences[SERVICE_TIER] ?: "FREE")
    }

    suspend fun getServiceTier(): ServiceTier {
        val stored = context.dataStore.data.first()[SERVICE_TIER] ?: "FREE"
        return ServiceTier.fromString(stored)
    }

    // 리워드 광고 채팅 잔여 횟수 저장
    suspend fun saveRewardChatRemaining(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[REWARD_CHAT_REMAINING] = count.coerceAtLeast(0)
        }
    }

    // 리워드 광고 채팅 잔여 횟수 가져오기 (기본값: 0)
    val rewardChatRemainingFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[REWARD_CHAT_REMAINING] ?: 0
    }

    suspend fun getRewardChatRemaining(): Int {
        return context.dataStore.data.first()[REWARD_CHAT_REMAINING] ?: 0
    }

    // 전체 동기화 해제 여부 저장 (광고 시청 후 true)
    suspend fun saveFullSyncUnlocked(unlocked: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FULL_SYNC_UNLOCKED] = unlocked
        }
    }

    // 전체 동기화 해제 여부 가져오기 (기본값: false → 3개월만 동기화)
    val fullSyncUnlockedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FULL_SYNC_UNLOCKED] ?: false
    }

    suspend fun isFullSyncUnlocked(): Boolean {
        return context.dataStore.data.first()[FULL_SYNC_UNLOCKED] ?: false
    }
}
