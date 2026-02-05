package com.sanha.moneytalk.feature.chat.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.util.PromptTemplates
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    private var cachedApiKey: String? = null
    private var generativeModel: GenerativeModel? = null

    companion object {
        private const val FINANCIAL_ASSISTANT_SYSTEM_PROMPT = """당신은 친절한 한국어 재무 상담 AI 비서입니다.
사용자의 지출 내역을 분석하고 재무 조언을 제공합니다.
답변은 간결하고 실용적이며, 한국 문화와 경제 상황에 맞게 작성해주세요.
이모지를 적절히 사용하여 친근한 느낌을 주세요."""
    }

    // DataStore에서 API 키 가져오기 (캐싱)
    private suspend fun getApiKey(): String {
        if (cachedApiKey.isNullOrBlank()) {
            cachedApiKey = settingsDataStore.getGeminiApiKey()
        }
        return cachedApiKey ?: ""
    }

    // GenerativeModel 가져오기 (캐싱)
    private suspend fun getModel(): GenerativeModel? {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return null

        if (generativeModel == null || cachedApiKey != apiKey) {
            generativeModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.7f
                    topK = 40
                    topP = 0.95f
                    maxOutputTokens = 1024
                }
            )
        }
        return generativeModel
    }

    // 수동으로 API 키 설정 (설정 화면에서 사용)
    suspend fun setApiKey(key: String) {
        cachedApiKey = key
        generativeModel = null // 모델 재생성 필요
        settingsDataStore.saveGeminiApiKey(key)
    }

    // API 키 존재 여부 확인
    suspend fun hasApiKey(): Boolean {
        return getApiKey().isNotBlank()
    }

    suspend fun chat(
        userMessage: String,
        monthlyIncome: Int,
        totalExpense: Int,
        categoryBreakdown: String,
        recentExpenses: String
    ): Result<String> {
        return try {
            val model = getModel()
            if (model == null) {
                return Result.failure(Exception("API 키가 설정되지 않았습니다. 설정에서 Gemini API 키를 입력해주세요."))
            }

            val prompt = buildString {
                appendLine(FINANCIAL_ASSISTANT_SYSTEM_PROMPT)
                appendLine()
                appendLine(PromptTemplates.financialAdvice(
                    monthlyIncome = monthlyIncome,
                    totalExpense = totalExpense,
                    categoryBreakdown = categoryBreakdown,
                    recentExpenses = recentExpenses,
                    userQuestion = userMessage
                ))
            }

            val response = model.generateContent(prompt)
            val responseText = response.text ?: "응답을 받지 못했어요."

            Result.success(responseText)
        } catch (e: Exception) {
            Result.failure(Exception("요청 실패: ${e.message}"))
        }
    }

    suspend fun simpleChat(userMessage: String): Result<String> {
        return try {
            val model = getModel()
            if (model == null) {
                return Result.failure(Exception("API 키가 설정되지 않았습니다. 설정에서 Gemini API 키를 입력해주세요."))
            }

            val prompt = buildString {
                appendLine(FINANCIAL_ASSISTANT_SYSTEM_PROMPT)
                appendLine()
                appendLine(userMessage)
            }

            val response = model.generateContent(prompt)
            val responseText = response.text ?: "응답을 받지 못했어요."

            Result.success(responseText)
        } catch (e: Exception) {
            Result.failure(Exception("요청 실패: ${e.message}"))
        }
    }
}
