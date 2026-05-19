package com.sanha.moneytalk.core.notification

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.RequestOptions
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.firebase.GeminiApiKeyProvider
import com.sanha.moneytalk.core.firebase.GeminiModelConfig
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinancialAppLlmAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiKeyProvider: GeminiApiKeyProvider
) : FinancialAppCandidateAnalyzer {
    companion object {
        private const val REQUEST_TIMEOUT_SECONDS = 30L
        private val CODE_FENCE_JSON = Regex("```json\\s*", RegexOption.IGNORE_CASE)
        private val CODE_FENCE = Regex("```\\s*")
    }

    private var model: GenerativeModel? = null
    private var cachedApiKey: String? = null
    private var cachedModelConfig: GeminiModelConfig? = null

    override suspend fun analyze(
        packageName: String,
        displayName: String
    ): FinancialAppAnalysis? = withContext(Dispatchers.IO) {
        try {
            val currentModel = getModel() ?: return@withContext null
            val prompt = context.getString(
                R.string.prompt_financial_app_classifier_user,
                packageName,
                displayName
            )
            val responseText = currentModel.generateContent(prompt).text
                ?: return@withContext null
            parseAnalysis(packageName, displayName, responseText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MoneyTalkLogger.w("[FinancialAppLlm] 앱 분류 실패: $packageName, ${e.message}")
            null
        }
    }

    private suspend fun getModel(): GenerativeModel? {
        val apiKey = apiKeyProvider.getApiKey()
        if (apiKey.isBlank()) return null

        val currentModelConfig = apiKeyProvider.modelConfig
        if (model == null || apiKey != cachedApiKey || currentModelConfig != cachedModelConfig) {
            cachedApiKey = apiKey
            cachedModelConfig = currentModelConfig
            model = GenerativeModel(
                modelName = currentModelConfig.queryAnalyzer,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.0f
                    responseMimeType = "application/json"
                    maxOutputTokens = 512
                },
                requestOptions = RequestOptions(timeout = REQUEST_TIMEOUT_SECONDS * 1000),
                systemInstruction = content {
                    text(context.getString(R.string.prompt_financial_app_classifier_system))
                }
            )
        }
        return model
    }

    private fun parseAnalysis(
        packageName: String,
        displayName: String,
        responseText: String
    ): FinancialAppAnalysis? {
        val jsonText = extractFirstJsonObject(responseText) ?: return null
        val json = runCatching { JsonParser.parseString(jsonText).asJsonObject }
            .getOrNull()
            ?: return null

        val classification = json.getStringOrDefault(
            "classification",
            FinancialAppAnalysis.CLASSIFICATION_UNKNOWN
        )
        val confidence = json.getFloatOrDefault("confidence", 0f).coerceIn(0f, 1f)

        return FinancialAppAnalysis(
            packageName = packageName,
            displayName = displayName,
            classification = classification,
            appType = json.getStringOrDefault("appType", FinancialAppAnalysis.DEFAULT_TYPE),
            parserProfile = json.getStringOrDefault(
                "parserProfile",
                FinancialAppAnalysis.DEFAULT_PARSER_PROFILE
            ),
            confidence = confidence,
            reason = json.getStringOrDefault("reason", "")
        )
    }

    private fun extractFirstJsonObject(response: String): String? {
        val cleaned = response
            .replace(CODE_FENCE_JSON, "")
            .replace(CODE_FENCE, "")
            .trim()

        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return cleaned.substring(start, end + 1)
    }

    private fun JsonObject.getStringOrDefault(key: String, defaultValue: String): String {
        val element = get(key) ?: return defaultValue
        if (!element.isJsonPrimitive) return defaultValue
        return element.asString.takeIf { it.isNotBlank() } ?: defaultValue
    }

    private fun JsonObject.getFloatOrDefault(key: String, defaultValue: Float): Float {
        val element = get(key) ?: return defaultValue
        if (!element.isJsonPrimitive) return defaultValue
        return runCatching { element.asFloat }.getOrDefault(defaultValue)
    }
}
