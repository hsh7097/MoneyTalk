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
    suspend fun saveGeminiApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[GEMINI_API_KEY] = apiKey
        }
    }

    // Gemini API 키 가져오기 (DataStore에 없으면 BuildConfig에서 가져옴)
    val geminiApiKeyFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[GEMINI_API_KEY] ?: BuildConfig.GEMINI_API_KEY
    }

    // Gemini API 키 즉시 가져오기
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
}
