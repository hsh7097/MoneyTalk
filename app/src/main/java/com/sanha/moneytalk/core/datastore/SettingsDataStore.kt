package com.sanha.moneytalk.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sanha.moneytalk.BuildConfig
import com.sanha.moneytalk.core.firebase.ServiceTier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
        private val LAST_RCS_PROVIDER_SCAN_TIME = longPreferencesKey("last_rcs_provider_scan_time")
        private val MONTH_START_DAY = intPreferencesKey("month_start_day")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val SERVICE_TIER = stringPreferencesKey("service_tier")
        private val REWARD_CHAT_REMAINING = intPreferencesKey("reward_chat_remaining")
        private val FULL_SYNC_UNLOCKED = booleanPreferencesKey("full_sync_unlocked")
        private val SYNCED_MONTHS = stringSetPreferencesKey("synced_months")
        private val FREE_SYNC_USED_COUNT = intPreferencesKey("free_sync_used_count")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        private val PREMIUM_CONFIG_JSON = stringPreferencesKey("premium_config_json")

        // ===== 화면별 온보딩 (코치마크) =====
        private val SCREEN_ONBOARDING_KEYS = mapOf(
            "home" to booleanPreferencesKey("has_seen_home_onboarding_v1"),
            "history" to booleanPreferencesKey("has_seen_history_onboarding_v1"),
            "chat" to booleanPreferencesKey("has_seen_chat_onboarding_v1"),
            "settings" to booleanPreferencesKey("has_seen_settings_onboarding_v1"),
            "store_rule" to booleanPreferencesKey("has_seen_store_rule_onboarding_v1"),
            "history_filter" to booleanPreferencesKey("has_seen_history_filter_onboarding_v1"),
            "transaction_edit" to booleanPreferencesKey("has_seen_transaction_edit_onboarding_v1"),
            "rule_keyword_guide" to booleanPreferencesKey("has_seen_rule_keyword_guide_v1")
        )
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

    // 마지막 동기화 시간 Flow
    val lastSyncTimeFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_SYNC_TIME] ?: 0L
    }

    // RCS provider 마지막 성공 scan 시간 저장
    suspend fun saveLastRcsProviderScanTime(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_RCS_PROVIDER_SCAN_TIME] = timestamp
        }
    }

    // RCS provider 마지막 성공 scan 시간 가져오기
    suspend fun getLastRcsProviderScanTime(): Long {
        return context.dataStore.data.first()[LAST_RCS_PROVIDER_SCAN_TIME] ?: 0L
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

    // ===== 월별 동기화 완료 기록 관리 =====

    /**
     * 특정 월의 동기화 완료 기록 추가
     * @param yearMonth "YYYY-MM" 형식 (예: "2026-02")
     */
    suspend fun addSyncedMonth(yearMonth: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[SYNCED_MONTHS] ?: emptySet()
            preferences[SYNCED_MONTHS] = current + yearMonth
        }
    }

    /** 동기화 완료된 월 목록 Flow */
    val syncedMonthsFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[SYNCED_MONTHS] ?: emptySet()
    }

    /** 특정 월이 동기화 완료되었는지 확인 */
    suspend fun isMonthSynced(yearMonth: String): Boolean {
        val months = context.dataStore.data.first()[SYNCED_MONTHS] ?: emptySet()
        return yearMonth in months
    }

    /** 동기화 완료된 월이 하나라도 있는지 (= 월별 CTA를 한 번이라도 완료했는지) */
    suspend fun hasAnySyncedMonth(): Boolean {
        val prefs = context.dataStore.data.first()
        val months = prefs[SYNCED_MONTHS] ?: emptySet()
        if (months.isNotEmpty()) return true
        // 하위 호환: 기존 전역 boolean이 true인 경우도 인정
        return prefs[FULL_SYNC_UNLOCKED] ?: false
    }

    // ===== 하위 호환용 (기존 전역 boolean) =====

    @Deprecated("월별 동기화로 전환. addSyncedMonth/isMonthSynced 사용")
    suspend fun saveFullSyncUnlocked(unlocked: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FULL_SYNC_UNLOCKED] = unlocked
        }
    }

    @Deprecated("월별 동기화로 전환. syncedMonthsFlow 사용")
    val fullSyncUnlockedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[FULL_SYNC_UNLOCKED] ?: false
    }

    @Deprecated("월별 동기화로 전환. isMonthSynced 사용")
    suspend fun isFullSyncUnlocked(): Boolean {
        return context.dataStore.data.first()[FULL_SYNC_UNLOCKED] ?: false
    }

    // ===== 온보딩 완료 여부 =====

    /** 온보딩(권한 설명 화면) 완료 여부 Flow (기본값: false) */
    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED] ?: false
    }

    /** 온보딩 완료 상태 저장 */
    suspend fun setOnboardingCompleted(completed: Boolean = true) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = completed
        }
    }

    /** 월별 동기화 기록만 초기화 (레거시 전역 플래그는 유지) */
    suspend fun resetSyncedMonths() {
        context.dataStore.edit { preferences ->
            preferences.remove(SYNCED_MONTHS)
        }
    }

    /** 광고 시청으로 해제된 월별 동기화 + 레거시 전역 플래그 + 무료 동기화 카운터 초기화 */
    suspend fun clearSyncedMonths() {
        context.dataStore.edit { preferences ->
            preferences.remove(SYNCED_MONTHS)
            preferences.remove(FULL_SYNC_UNLOCKED)
            preferences.remove(FREE_SYNC_USED_COUNT)
        }
    }

    // ===== 무료 동기화 사용 횟수 =====

    /** 무료 동기화 사용 횟수 Flow */
    val freeSyncUsedCountFlow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[FREE_SYNC_USED_COUNT] ?: 0
    }

    // ===== 알림 설정 =====

    /** 거래 알림 활성화 여부 Flow (기본값: false = 알림 안받음) */
    val notificationEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[NOTIFICATION_ENABLED] ?: false
    }

    /** 거래 알림 활성화 여부 즉시 조회 */
    suspend fun isNotificationEnabled(): Boolean {
        return context.dataStore.data.first()[NOTIFICATION_ENABLED] ?: false
    }

    /** 거래 알림 활성화 여부 저장 */
    suspend fun saveNotificationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATION_ENABLED] = enabled
        }
    }

    // ===== 서버 설정 캐시 =====

    /** 마지막으로 정상 수신한 서버 설정 JSON 저장 */
    suspend fun savePremiumConfigJson(json: String) {
        context.dataStore.edit { preferences ->
            preferences[PREMIUM_CONFIG_JSON] = json
        }
    }

    /** 마지막으로 정상 수신한 서버 설정 JSON 즉시 조회 */
    suspend fun getPremiumConfigJson(): String {
        return context.dataStore.data.first()[PREMIUM_CONFIG_JSON].orEmpty()
    }

    /** 무료 동기화 사용 횟수 1 증가 */
    suspend fun incrementFreeSyncUsedCount() {
        context.dataStore.edit { preferences ->
            val current = preferences[FREE_SYNC_USED_COUNT] ?: 0
            preferences[FREE_SYNC_USED_COUNT] = current + 1
        }
    }

    // ===== 화면별 온보딩 (코치마크) =====

    /** 특정 화면의 온보딩 완료 여부 Flow */
    fun hasSeenScreenOnboardingFlow(screenId: String): Flow<Boolean> {
        val key = SCREEN_ONBOARDING_KEYS[screenId] ?: return flowOf(true)
        return context.dataStore.data.map { preferences ->
            preferences[key] ?: false
        }
    }

    /** 특정 화면의 온보딩 완료 마킹 */
    suspend fun setScreenOnboardingSeen(screenId: String) {
        val key = SCREEN_ONBOARDING_KEYS[screenId] ?: return
        context.dataStore.edit { it[key] = true }
    }

    /** 모든 화면 온보딩 리셋 (설정 > 가이드 초기화) */
    suspend fun resetAllScreenOnboardings() {
        context.dataStore.edit { prefs ->
            SCREEN_ONBOARDING_KEYS.values.forEach { prefs.remove(it) }
        }
    }
}
