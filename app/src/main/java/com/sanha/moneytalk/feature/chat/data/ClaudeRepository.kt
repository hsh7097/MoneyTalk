package com.sanha.moneytalk.feature.chat.data

import com.google.gson.Gson
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.util.PromptTemplates
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClaudeRepository @Inject constructor(
    private val claudeApi: ClaudeApi,
    private val gson: Gson,
    private val settingsDataStore: SettingsDataStore
) {
    private var cachedApiKey: String? = null

    // DataStore에서 API 키 가져오기 (캐싱)
    private suspend fun getApiKey(): String {
        if (cachedApiKey.isNullOrBlank()) {
            cachedApiKey = settingsDataStore.getApiKey()
        }
        return cachedApiKey ?: ""
    }

    // 수동으로 API 키 설정 (설정 화면에서 사용)
    suspend fun setApiKey(key: String) {
        cachedApiKey = key
        settingsDataStore.saveApiKey(key)
    }

    // API 키 존재 여부 확인
    suspend fun hasApiKey(): Boolean {
        return getApiKey().isNotBlank()
    }

    suspend fun analyzeSms(smsText: String): Result<SmsAnalysisResult> {
        return try {
            val apiKey = getApiKey()
            if (apiKey.isBlank()) {
                return Result.failure(Exception("API 키가 설정되지 않았습니다"))
            }

            val request = ClaudeRequest(
                messages = listOf(
                    ClaudeMessage(
                        role = "user",
                        content = PromptTemplates.analyzeSms(smsText)
                    )
                )
            )

            val response = claudeApi.sendMessage(apiKey = apiKey, request = request)
            val responseText = response.content.firstOrNull()?.text ?: ""

            // JSON 파싱
            val result = gson.fromJson(responseText, SmsAnalysisResult::class.java)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun chat(
        userMessage: String,
        monthlyIncome: Int,
        totalExpense: Int,
        categoryBreakdown: String,
        recentExpenses: String
    ): Result<String> {
        return try {
            val apiKey = getApiKey()
            if (apiKey.isBlank()) {
                return Result.failure(Exception("API 키가 설정되지 않았습니다"))
            }

            val prompt = PromptTemplates.financialAdvice(
                monthlyIncome = monthlyIncome,
                totalExpense = totalExpense,
                categoryBreakdown = categoryBreakdown,
                recentExpenses = recentExpenses,
                userQuestion = userMessage
            )

            val request = ClaudeRequest(
                messages = listOf(
                    ClaudeMessage(role = "user", content = prompt)
                )
            )

            val response = claudeApi.sendMessage(apiKey = apiKey, request = request)
            val responseText = response.content.firstOrNull()?.text ?: "응답을 받지 못했어요."

            Result.success(responseText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun simpleChat(userMessage: String): Result<String> {
        return try {
            val apiKey = getApiKey()
            if (apiKey.isBlank()) {
                return Result.failure(Exception("API 키가 설정되지 않았습니다"))
            }

            val request = ClaudeRequest(
                messages = listOf(
                    ClaudeMessage(role = "user", content = userMessage)
                )
            )

            val response = claudeApi.sendMessage(apiKey = apiKey, request = request)
            val responseText = response.content.firstOrNull()?.text ?: "응답을 받지 못했어요."

            Result.success(responseText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
