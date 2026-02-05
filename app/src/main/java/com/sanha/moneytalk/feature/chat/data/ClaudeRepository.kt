package com.sanha.moneytalk.feature.chat.data

import com.google.gson.Gson
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.util.PromptTemplates
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClaudeRepository @Inject constructor(
    private val claudeApi: ClaudeApi,
    private val gson: Gson,
    private val settingsDataStore: SettingsDataStore
) {
    private var cachedApiKey: String? = null

    companion object {
        private const val FINANCIAL_ASSISTANT_SYSTEM_PROMPT = """당신은 친절한 한국어 재무 상담 AI 비서입니다.
사용자의 지출 내역을 분석하고 재무 조언을 제공합니다.
답변은 간결하고 실용적이며, 한국 문화와 경제 상황에 맞게 작성해주세요.
이모지를 적절히 사용하여 친근한 느낌을 주세요."""
    }

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

    // HTTP 에러에서 상세 메시지 추출
    private fun parseHttpError(e: HttpException): String {
        return try {
            val errorBody = e.response()?.errorBody()?.string()
            if (errorBody != null) {
                val errorResponse = gson.fromJson(errorBody, ClaudeErrorResponse::class.java)
                errorResponse.error?.message ?: "알 수 없는 오류 (${e.code()})"
            } else {
                "HTTP 오류: ${e.code()} ${e.message()}"
            }
        } catch (parseError: Exception) {
            "HTTP 오류: ${e.code()} ${e.message()}"
        }
    }

    suspend fun analyzeSms(smsText: String): Result<SmsAnalysisResult> {
        return try {
            val apiKey = getApiKey()
            if (apiKey.isBlank()) {
                return Result.failure(Exception("API 키가 설정되지 않았습니다. 설정에서 Claude API 키를 입력해주세요."))
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
        } catch (e: HttpException) {
            Result.failure(Exception(parseHttpError(e)))
        } catch (e: Exception) {
            Result.failure(Exception("요청 실패: ${e.message}"))
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
                return Result.failure(Exception("API 키가 설정되지 않았습니다. 설정에서 Claude API 키를 입력해주세요."))
            }

            val prompt = PromptTemplates.financialAdvice(
                monthlyIncome = monthlyIncome,
                totalExpense = totalExpense,
                categoryBreakdown = categoryBreakdown,
                recentExpenses = recentExpenses,
                userQuestion = userMessage
            )

            val request = ClaudeRequest(
                system = FINANCIAL_ASSISTANT_SYSTEM_PROMPT,
                messages = listOf(
                    ClaudeMessage(role = "user", content = prompt)
                )
            )

            val response = claudeApi.sendMessage(apiKey = apiKey, request = request)
            val responseText = response.content.firstOrNull()?.text ?: "응답을 받지 못했어요."

            Result.success(responseText)
        } catch (e: HttpException) {
            Result.failure(Exception(parseHttpError(e)))
        } catch (e: Exception) {
            Result.failure(Exception("요청 실패: ${e.message}"))
        }
    }

    suspend fun simpleChat(userMessage: String): Result<String> {
        return try {
            val apiKey = getApiKey()
            if (apiKey.isBlank()) {
                return Result.failure(Exception("API 키가 설정되지 않았습니다. 설정에서 Claude API 키를 입력해주세요."))
            }

            val request = ClaudeRequest(
                system = FINANCIAL_ASSISTANT_SYSTEM_PROMPT,
                messages = listOf(
                    ClaudeMessage(role = "user", content = userMessage)
                )
            )

            val response = claudeApi.sendMessage(apiKey = apiKey, request = request)
            val responseText = response.content.firstOrNull()?.text ?: "응답을 받지 못했어요."

            Result.success(responseText)
        } catch (e: HttpException) {
            Result.failure(Exception(parseHttpError(e)))
        } catch (e: Exception) {
            Result.failure(Exception("요청 실패: ${e.message}"))
        }
    }
}
