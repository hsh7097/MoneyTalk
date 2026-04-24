package com.sanha.moneytalk.feature.chat.data

import com.sanha.moneytalk.core.util.MoneyTalkLogger

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.sanha.moneytalk.core.firebase.GeminiApiKeyProvider
import com.sanha.moneytalk.core.firebase.GeminiModelConfig
import com.sanha.moneytalk.core.util.ActionResult
import com.sanha.moneytalk.core.util.DataQueryParser
import com.sanha.moneytalk.core.util.DataQueryRequest
import com.sanha.moneytalk.core.util.QueryResult
import kotlinx.coroutines.delay
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
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
            }
        )
        val topCatText = topCategories.joinToString(", ") { "${it.first} ${it.second}원" }
        val lastMonthCategoryMap = lastMonthTopCategories.toMap()
        val categoryComparisonText = if (topCategories.isNotEmpty()) {
            "\n카테고리별 전월 대비 계산 결과: " + topCategories.joinToString(", ") { (category, amount) ->
                val lastAmount = lastMonthCategoryMap[category] ?: 0
                if (lastAmount > 0) {
                    val difference = amount - lastAmount
                    val absDifference = kotlin.math.abs(difference)
                    val percent = absDifference.toLong() * 100 / lastAmount
                    val direction = when {
                        difference > 0 -> "증가"
                        difference < 0 -> "감소"
                        else -> "동일"
                    }
                    "$category ${absDifference}원 $direction (${percent}% $direction)"
                } else {
                    "$category 전월 데이터 부족으로 비율 판단 불가"
                }
            }
        } else {
            "\n카테고리별 전월 대비 계산 결과: 비교 데이터 부족"
        }
        val budgetText = monthlyBudget
            ?.takeIf { it > 0 }
            ?.let {
                val usagePercent = monthlyExpense.toLong() * 100 / it
                "\n월 예산: ${it}원\n예산 사용률: ${usagePercent}%"
            }
            .orEmpty()
        val monthComparisonText = when {
            lastMonthExpense > 0 -> {
                val difference = monthlyExpense - lastMonthExpense
                val absDifference = kotlin.math.abs(difference)
                val percent = absDifference.toLong() * 100 / lastMonthExpense
                val direction = when {
                    difference > 0 -> "증가"
                    difference < 0 -> "감소"
                    else -> "동일"
                }
                "\n전월 대비 계산 결과: ${absDifference}원 $direction (${percent}% $direction)"
            }

            monthlyExpense > 0 -> "\n전월 대비 계산 결과: 전월 지출 0원/데이터 부족으로 비율 판단 불가"
            else -> "\n전월 대비 계산 결과: 비교 데이터 부족"
        }
        val noExpenseHint = if (monthlyExpense == 0) "\n※ 이번 달 지출이 아직 없습니다. 격려/기대감 톤으로 작성." else ""
        val prompt = """
                재무 어드바이저로서 한국어로 한줄 인사이트를 작성해.
                이번 달 지출: ${monthlyExpense}원
                지난 달 지출: ${lastMonthExpense}원
                오늘 지출: ${todayExpense}원
                이번 달 주요 카테고리: $topCatText$categoryComparisonText$budgetText$monthComparisonText$noExpenseHint

                규칙: 이모지 1개 + 한줄(30자 이내). 격려/경고/팁 중 적절한 톤 선택.
                숫자/비율/증감률은 위에 제공된 값만 사용하고 직접 계산하지 마. 근거 없는 절약률, 원인, 소비 패턴은 추정하지 마.
                "비율 판단 불가" 또는 "비교 데이터 부족"이면 절약률/증가율/패턴을 단정하지 말고 데이터 부족을 짧게 안내.
                예산 사용률이 있으면 예산 초과/근접 여부를 우선 고려하고, 그 외에는 계산 결과에 있는 카테고리별 전월 대비 증감을 참고하여 인사이트 생성.
                예시: "💪 이번 달 지출 좋아요" 또는 "☕ 카페 지출을 확인해봐요"
            """.trimIndent()

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

            val prompt = """오늘: $today

$contextualMessage

위 질문에 필요한 데이터 쿼리를 JSON으로 반환해줘:"""


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
                """
                DB 조회 결과 없음
                - 재무 수치가 필요한 질문이라면 데이터가 부족한 상태로 보고 추정하지 마세요.
                - 금융 판단에 필요한 데이터가 없으면 필요한 기간/대상/월 수입/예산 정보를 요청하거나 현재 데이터만으로는 판단할 수 없다고 답하세요.
                """.trimIndent()
            }

            val actionContext = if (actionResults.isNotEmpty()) {
                "\n\n[실행된 액션 결과]\n" + actionResults.joinToString("\n") { result ->
                    "- ${result.message}"
                }
            } else ""
            val incomeContext = if (monthlyIncome > 0) {
                "[월 수입] ${String.format("%,d", monthlyIncome)}원\n\n"
            } else {
                "[월 수입] 미설정\n\n"
            }

            val prompt = """$incomeContext[조회된 데이터]
$safeDataContext$actionContext

[사용자 질문]
$userMessage"""


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

            val prompt = """다음 대화 내용을 보고, 이 대화를 가장 잘 나타내는 짧은 제목을 한국어로 만들어줘.

규칙:
- 반드시 15자 이내
- 이모지 금지
- 따옴표 금지
- 핵심 주제만 담기
- 예시: "이번 달 식비 분석", "카페 지출 줄이기", "저축 계획 상담"

대화 내용:
$recentMessages

제목:"""

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
                """다음 대화 내용을 요약해주세요:

$newMessages"""
            } else {
                // 누적 요약: 기존 요약 + 새 메시지를 통합
                """다음 기존 요약본과 새로운 대화 내용을 통합하여 하나의 누적 요약본을 생성해주세요.

[기존 요약본]
$existingSummary

[새로운 대화 내용]
$newMessages"""
            }

            val response = model.generateContent(prompt)
            val summaryText = response.text ?: return Result.failure(Exception("요약 응답 없음"))

            Result.success(summaryText.trim())
        } catch (e: Exception) {
            MoneyTalkLogger.e("Rolling Summary 생성 실패", e)
            Result.failure(Exception("요약 생성 실패: ${e.message}"))
        }
    }
}
