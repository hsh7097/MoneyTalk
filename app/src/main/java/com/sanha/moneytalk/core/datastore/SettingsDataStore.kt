package com.sanha.moneytalk.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
        private val CUSTOM_ALIASES = stringPreferencesKey("custom_aliases")
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

    // 사용자 정의 별칭 저장 (직렬화: "mainName:alias1,alias2|mainName2:alias3")
    suspend fun saveCustomAliases(aliases: Map<String, Set<String>>) {
        val serialized = aliases.entries.joinToString("|") { (mainName, aliasList) ->
            "$mainName:${aliasList.joinToString(",")}"
        }
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_ALIASES] = serialized
        }
    }

    // 사용자 정의 별칭 가져오기
    suspend fun getCustomAliases(): Map<String, Set<String>> {
        val serialized = context.dataStore.data.first()[CUSTOM_ALIASES] ?: return emptyMap()
        return deserializeAliases(serialized)
    }

    val customAliasesFlow: Flow<Map<String, Set<String>>> = context.dataStore.data.map { preferences ->
        val serialized = preferences[CUSTOM_ALIASES] ?: return@map emptyMap()
        deserializeAliases(serialized)
    }

    private fun deserializeAliases(serialized: String): Map<String, Set<String>> {
        if (serialized.isBlank()) return emptyMap()
        return serialized.split("|").associate { entry ->
            val parts = entry.split(":", limit = 2)
            val mainName = parts[0]
            val aliases = if (parts.size > 1 && parts[1].isNotBlank()) {
                parts[1].split(",").toSet()
            } else emptySet()
            mainName to aliases
        }
    }
}
