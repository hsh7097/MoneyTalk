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
        lastMonthTopCategories: List<Pair<String, Int>>
    ): String? {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return null

        return try {
            val model = GenerativeModel(
                modelName = apiKeyProvider.modelConfig.homeInsight,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.7f
                    maxOutputTokens = 100
                }
            )
            val topCatText = topCategories.joinToString(", ") { "${it.first} ${it.second}원" }
            val lastMonthCatText = if (lastMonthTopCategories.isNotEmpty()) {
                "\n전월 동일 카테고리: " + lastMonthTopCategories.joinToString(", ") { "${it.first} ${it.second}원" }
            } else ""
            val noExpenseHint = if (monthlyExpense == 0) "\n※ 이번 달 지출이 아직 없습니다. 격려/기대감 톤으로 작성." else ""
            val prompt = """
                재무 어드바이저로서 한국어로 한줄 인사이트를 작성해.
                이번 달 지출: ${monthlyExpense}원
                지난 달 지출: ${lastMonthExpense}원
                오늘 지출: ${todayExpense}원
                이번 달 주요 카테고리: $topCatText$lastMonthCatText$noExpenseHint

                규칙: 이모지 1개 + 한줄(30자 이내). 격려/경고/팁 중 적절한 톤 선택.
                카테고리별 전월 대비 증감을 참고하여 인사이트 생성.
                예시: "💪 지난달보다 15% 절약 중이에요!" 또는 "☕ 카페 지출이 늘고 있어요"
            """.trimIndent()

            val response = model.generateContent(prompt)
            response.text?.trim()
        } catch (e: Exception) {
            MoneyTalkLogger.w("인사이트 생성 실패: ${e.message}")
            null
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

            val actionContext = if (actionResults.isNotEmpty()) {
                "\n\n[실행된 액션 결과]\n" + actionResults.joinToString("\n") { result ->
                    "- ${result.message}"
                }
            } else ""

            val prompt = """[월 수입] ${String.format("%,d", monthlyIncome)}원

[조회된 데이터]
$dataContext$actionContext

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
