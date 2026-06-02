package com.sanha.moneytalk.feature.chat.data

import com.sanha.moneytalk.core.util.MoneyTalkLogger

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.firebase.GeminiApiKeyProvider
import com.sanha.moneytalk.core.firebase.GeminiModelConfig
import com.sanha.moneytalk.core.util.ActionResult
import com.sanha.moneytalk.core.util.DataQueryParser
import com.sanha.moneytalk.core.util.DataQueryRequest
import com.sanha.moneytalk.core.util.QueryResult
import kotlinx.coroutines.delay
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini AI Repository 구현체
 *
 * 3개의 GenerativeModel을 내부적으로 관리하며, API 키 또는 모델 설정 변경 시 자동으로 재생성합니다.
 * - queryAnalyzerModel: 쿼리/액션 분석용
 * - financialAdvisorModel: 재무 상담 답변용
 * - summaryModel: 대화 요약용
 *
 * 모델명은 Firebase RTDB에서 원격 관리됩니다 (GeminiModelConfig).
 *
 * @see GeminiRepository 인터페이스
 */
@Singleton
class GeminiRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiKeyProvider: GeminiApiKeyProvider
) : GeminiRepository {
    companion object {
        /** Home 인사이트 생성 재시도 횟수 (최초 포함) */
        private const val HOME_INSIGHT_MAX_ATTEMPTS = 3
        /** Home 인사이트 재시도 기본 지연 (선형 백오프) */
        private const val HOME_INSIGHT_RETRY_BASE_DELAY_MS = 1200L
    }

    private var cachedApiKey: String? = null
    private var cachedModelConfig: GeminiModelConfig? = null

    // 쿼리 분석용 모델
    private var queryAnalyzerModel: GenerativeModel? = null

    // 재무 상담용 모델
    private var financialAdvisorModel: GenerativeModel? = null

    // 요약 전용 모델
    private var summaryModel: GenerativeModel? = null

    // API 키 또는 모델 설정 변경 감지 → 모델 재생성
    private suspend fun getApiKey(): String {
        val key = apiKeyProvider.getApiKey()
        val currentModelConfig = apiKeyProvider.modelConfig
        if (key != cachedApiKey || currentModelConfig != cachedModelConfig) {
            cachedApiKey = key
            cachedModelConfig = currentModelConfig
            // 키 또는 모델 설정 변경 시 모델 재생성
            queryAnalyzerModel = null
            financialAdvisorModel = null
            summaryModel = null
        }
        return cachedApiKey ?: ""
    }

    // 쿼리 분석용 모델 가져오기 (System Instruction 포함)
    private suspend fun getQueryAnalyzerModel(): GenerativeModel? {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return null

        if (queryAnalyzerModel == null) {
            queryAnalyzerModel = GenerativeModel(
                modelName = apiKeyProvider.modelConfig.queryAnalyzer,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.3f  // 쿼리 분석은 정확도가 중요
                    topK = 20
                    topP = 0.9f
                    maxOutputTokens = 10000
                },
                systemInstruction = content {
                    text(
                        ChatPrompts.getQueryAnalyzerSystemInstruction(
                            context
                        )
                    )
                }
            )
        }
        return queryAnalyzerModel
    }

    // 재무 상담용 모델 가져오기 (System Instruction 포함)
    private suspend fun getFinancialAdvisorModel(): GenerativeModel? {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return null

        if (financialAdvisorModel == null) {
            financialAdvisorModel = GenerativeModel(
                modelName = apiKeyProvider.modelConfig.financialAdvisor,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.7f
                    topK = 40
                    topP = 0.95f
                    maxOutputTokens = 10000
                },
                systemInstruction = content {
                    text(
                        ChatPrompts.getFinancialAdvisorSystemInstruction(
                            context
                        )
                    )
                }
            )
        }
        return financialAdvisorModel
    }

    // 요약 전용 모델 가져오기 (System Instruction 포함)
    private suspend fun getSummaryModel(): GenerativeModel? {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return null

        if (summaryModel == null) {
            summaryModel = GenerativeModel(
                modelName = apiKeyProvider.modelConfig.summary,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.3f  // 요약은 정확도가 중요
                    topK = 20
                    topP = 0.9f
                    maxOutputTokens = 10000
                },
                systemInstruction = content { text(ChatPrompts.getSummarySystemInstruction(context)) }
            )
        }
        return summaryModel
    }

    override suspend fun generateHomeInsight(
        monthlyExpense: Int,
        lastMonthExpense: Int,
        todayExpense: Int,
        topCategories: List<Pair<String, Int>>,
        lastMonthTopCategories: List<Pair<String, Int>>,
        monthlyBudget: Int?
    ): String? {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return null

        val model = GenerativeModel(
            modelName = apiKeyProvider.modelConfig.homeInsight,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.7f
                maxOutputTokens = 100
            },
            systemInstruction = content {
                text(ChatPrompts.getHomeInsightSystemInstruction(context))
            }
        )
        val topCatText = topCategories
            .joinToString(", ") {
                context.getString(
                    R.string.ai_home_insight_top_category_item,
                    it.first,
                    it.second.formatWon()
                )
            }
            .ifBlank { context.getString(R.string.ai_home_insight_no_data) }
        val lastMonthCategoryMap = lastMonthTopCategories.toMap()
        val categoryComparisonText = if (topCategories.isNotEmpty()) {
            val comparison = topCategories.joinToString(", ") { (category, amount) ->
                val lastAmount = lastMonthCategoryMap[category] ?: 0
                if (lastAmount > 0) {
                    val difference = amount - lastAmount
                    val absDifference = kotlin.math.abs(difference)
                    val percent = absDifference.toLong() * 100 / lastAmount
                    val direction = when {
                        difference > 0 -> context.getString(
                            R.string.ai_home_insight_direction_increase
                        )
                        difference < 0 -> context.getString(
                            R.string.ai_home_insight_direction_decrease
                        )
                        else -> context.getString(R.string.ai_home_insight_direction_same)
                    }
                    context.getString(
                        R.string.ai_home_insight_category_comparison_item,
                        category,
                        absDifference.formatWon(),
                        direction,
                        percent
                    )
                } else {
                    context.getString(
                        R.string.ai_home_insight_category_comparison_insufficient_item,
                        category
                    )
                }
            }
            context.getString(R.string.ai_home_insight_category_comparison, comparison)
        } else {
            context.getString(R.string.ai_home_insight_category_comparison_insufficient)
        }
        val budgetText = monthlyBudget
            ?.takeIf { it > 0 }
            ?.let {
                val usagePercent = monthlyExpense.toLong() * 100 / it
                context.getString(
                    R.string.ai_home_insight_budget,
                    it.formatWon(),
                    usagePercent
                )
            }
            .orEmpty()
        val monthComparisonText = when {
            lastMonthExpense > 0 -> {
                val difference = monthlyExpense - lastMonthExpense
                val absDifference = kotlin.math.abs(difference)
                val percent = absDifference.toLong() * 100 / lastMonthExpense
                val direction = when {
                    difference > 0 -> context.getString(
                        R.string.ai_home_insight_direction_increase
                    )
                    difference < 0 -> context.getString(
                        R.string.ai_home_insight_direction_decrease
                    )
                    else -> context.getString(R.string.ai_home_insight_direction_same)
                }
                context.getString(
                    R.string.ai_home_insight_month_comparison,
                    absDifference.formatWon(),
                    direction,
                    percent
                )
            }

            monthlyExpense > 0 -> context.getString(
                R.string.ai_home_insight_month_comparison_no_previous
            )
            else -> context.getString(R.string.ai_home_insight_month_comparison_insufficient)
        }
        val noExpenseHint = if (monthlyExpense == 0) {
            context.getString(R.string.ai_home_insight_no_expense_hint)
        } else {
            ""
        }
        val prompt = context.getString(
            R.string.prompt_home_insight_user,
            monthlyExpense.formatWon(),
            lastMonthExpense.formatWon(),
            todayExpense.formatWon(),
            topCatText,
            categoryComparisonText,
            budgetText,
            monthComparisonText,
            noExpenseHint
        )

        for (attempt in 1..HOME_INSIGHT_MAX_ATTEMPTS) {
            try {
                val response = model.generateContent(prompt)
                val insight = sanitizeHomeInsight(response.text)
                if (!insight.isNullOrBlank()) return insight
                throw IllegalStateException("인사이트 응답이 비어있습니다")
            } catch (e: Exception) {
                val message = e.message.orEmpty()
                val retryable = message.contains("503") ||
                    message.contains("UNAVAILABLE", ignoreCase = true) ||
                    message.contains("high demand", ignoreCase = true)
                val hasNext = attempt < HOME_INSIGHT_MAX_ATTEMPTS

                if (retryable && hasNext) {
                    val delayMs = HOME_INSIGHT_RETRY_BASE_DELAY_MS * attempt
                    MoneyTalkLogger.w(
                        "인사이트 생성 일시 실패(재시도 ${attempt}/${HOME_INSIGHT_MAX_ATTEMPTS - 1}, ${delayMs}ms 대기): ${e.message}"
                    )
                    delay(delayMs)
                    continue
                }

                MoneyTalkLogger.w("인사이트 생성 실패: ${e.message}")
                return null
            }
        }

        return null
    }

    private fun sanitizeHomeInsight(raw: String?): String? {
        val line = raw
            ?.lineSequence()
            ?.map { it.trim().trim('"', '\'', '“', '”') }
            ?.firstOrNull { it.isNotBlank() }
            ?: return null

        val cleaned = line
            .removePrefix("-")
            .removePrefix("*")
            .trim()
            .replace(Regex("\\s+"), " ")

        return cleaned.takeIf { it.isNotBlank() }?.let {
            if (it.length <= 36) it else it.take(35).trimEnd() + "…"
        }
    }

    @Deprecated("API 키는 Firebase RTDB에서 관리됩니다")
    override suspend fun setApiKey(key: String) {
        // RTDB 기반 키 관리로 전환 — 로컬 키 저장 제거
    }

    override suspend fun hasApiKey(): Boolean {
        return apiKeyProvider.hasValidApiKey()
    }

    override suspend fun analyzeQueryNeeds(contextualMessage: String): Result<DataQueryRequest?> {
        return try {

            val model = getQueryAnalyzerModel()
            if (model == null) {
                MoneyTalkLogger.e("API 키가 설정되지 않음")
                return Result.failure(Exception("API 키가 설정되지 않았습니다."))
            }

            // 오늘 날짜 정보 추가 (스키마는 System Instruction에 있음)
            val calendar = Calendar.getInstance()
            val today = "${calendar.get(Calendar.YEAR)}년 ${calendar.get(Calendar.MONTH) + 1}월 ${
                calendar.get(Calendar.DAY_OF_MONTH)
            }일"

            val prompt = context.getString(
                R.string.prompt_query_analyzer_user,
                today,
                contextualMessage
            )


            val response = model.generateContent(prompt)
            val responseText = response.text ?: return Result.success(null)


            val queryRequest = DataQueryParser.parseQueryRequest(responseText)
            Result.success(queryRequest)
        } catch (e: Exception) {
            MoneyTalkLogger.e("쿼리 분석 실패", e)
            MoneyTalkLogger.e("에러 메시지: ${e.message}")
            MoneyTalkLogger.e("에러 클래스: ${e.javaClass.simpleName}")
            Result.failure(Exception("쿼리 분석 실패: ${e.message}"))
        }
    }

    override suspend fun generateFinalAnswer(
        userMessage: String,
        queryResults: List<QueryResult>,
        monthlyIncome: Int,
        actionResults: List<ActionResult>
    ): Result<String> {
        return try {

            val model = getFinancialAdvisorModel()
            if (model == null) {
                MoneyTalkLogger.e("API 키가 설정되지 않음")
                return Result.failure(Exception("API 키가 설정되지 않았습니다."))
            }

            // 데이터만 전송 (역할/규칙은 System Instruction에 있음)
            val dataContext = queryResults.joinToString("\n\n") { result ->
                "[${result.queryType.name}]\n${result.data}"
            }
            val safeDataContext = dataContext.ifBlank {
                context.getString(R.string.prompt_final_answer_empty_data_context)
            }

            val actionContext = if (actionResults.isNotEmpty()) {
                "\n\n${context.getString(R.string.ai_chat_section_action_results)}\n" +
                    actionResults.joinToString("\n") { result ->
                    "- ${result.message}"
                }
            } else ""
            val incomeContext = if (monthlyIncome > 0) {
                context.getString(
                    R.string.ai_chat_section_monthly_income,
                    String.format(Locale.KOREA, "%,d", monthlyIncome)
                ) + "\n\n"
            } else {
                context.getString(R.string.ai_chat_section_monthly_income_unset) + "\n\n"
            }

            val prompt = context.getString(
                R.string.prompt_final_answer_user,
                incomeContext,
                safeDataContext,
                actionContext,
                userMessage
            )


            val response = model.generateContent(prompt)
            val responseText = response.text ?: "응답을 받지 못했어요."


            Result.success(responseText)
        } catch (e: Exception) {
            MoneyTalkLogger.e("최종 답변 생성 실패", e)
            MoneyTalkLogger.e("에러 메시지: ${e.message}")
            MoneyTalkLogger.e("에러 클래스: ${e.javaClass.simpleName}")
            Result.failure(Exception("요청 실패: ${e.message}"))
        }
    }

    override suspend fun generateFinalAnswerWithContext(contextPrompt: String): Result<String> {
        return try {

            val model = getFinancialAdvisorModel()
            if (model == null) {
                return Result.failure(Exception("API 키가 설정되지 않았습니다."))
            }

            val response = model.generateContent(contextPrompt)
            val responseText = response.text ?: "응답을 받지 못했어요."

            Result.success(responseText)
        } catch (e: Exception) {
            MoneyTalkLogger.e("컨텍스트 기반 답변 생성 실패", e)
            Result.failure(Exception("요청 실패: ${e.message}"))
        }
    }

    override suspend fun simpleChat(userMessage: String): Result<String> {
        return try {

            val model = getFinancialAdvisorModel()
            if (model == null) {
                MoneyTalkLogger.e("API 키가 설정되지 않음")
                return Result.failure(Exception("API 키가 설정되지 않았습니다. 설정에서 Gemini API 키를 입력해주세요."))
            }

            val response = model.generateContent(userMessage)
            val responseText = response.text ?: "응답을 받지 못했어요."


            Result.success(responseText)
        } catch (e: Exception) {
            MoneyTalkLogger.e("simpleChat 실패", e)
            MoneyTalkLogger.e("에러 메시지: ${e.message}")
            MoneyTalkLogger.e("에러 클래스: ${e.javaClass.simpleName}")
            Result.failure(Exception("요청 실패: ${e.message}"))
        }
    }

    override suspend fun generateChatTitle(recentMessages: String): String? {
        return try {
            val model = getSummaryModel() ?: return null

            val prompt = context.getString(R.string.prompt_chat_title_user, recentMessages)

            val response = model.generateContent(prompt)
            val title = response.text?.trim()?.take(20)
            if (title.isNullOrBlank()) null else title
        } catch (e: Exception) {
            MoneyTalkLogger.w("채팅 타이틀 생성 실패: ${e.message}")
            null
        }
    }

    override suspend fun generateRollingSummary(
        existingSummary: String?,
        newMessages: String
    ): Result<String> {
        return try {

            val model = getSummaryModel()
            if (model == null) {
                return Result.failure(Exception("API 키가 설정되지 않았습니다."))
            }

            val prompt = if (existingSummary.isNullOrBlank()) {
                // 첫 요약: 새 메시지만으로 요약 생성
                context.getString(R.string.prompt_rolling_summary_initial_user, newMessages)
            } else {
                // 누적 요약: 기존 요약 + 새 메시지를 통합
                context.getString(
                    R.string.prompt_rolling_summary_update_user,
                    existingSummary,
                    newMessages
                )
            }

            val response = model.generateContent(prompt)
            val summaryText = response.text ?: return Result.failure(Exception("요약 응답 없음"))

            Result.success(summaryText.trim())
        } catch (e: Exception) {
            MoneyTalkLogger.e("Rolling Summary 생성 실패", e)
            Result.failure(Exception("요약 생성 실패: ${e.message}"))
        }
    }

    private fun Int.formatWon(): String = String.format(Locale.KOREA, "%,d", this)
}
