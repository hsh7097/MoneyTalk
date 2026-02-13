package com.sanha.moneytalk.feature.chat.data

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.sanha.moneytalk.core.datastore.SettingsDataStore
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
 * 3개의 GenerativeModel을 내부적으로 관리하며, API 키 변경 시 자동으로 모델을 재생성합니다.
 * - queryAnalyzerModel (gemini-2.5-pro): 쿼리/액션 분석용
 * - financialAdvisorModel (gemini-2.5-pro): 재무 상담 답변용
 * - summaryModel (gemini-2.5-flash): 대화 요약용
 *
 * @see GeminiRepository 인터페이스
 */
@Singleton
class GeminiRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore
) : GeminiRepository {
    private var cachedApiKey: String? = null

    // 쿼리 분석용 모델 (System Instruction에 스키마 포함)
    private var queryAnalyzerModel: GenerativeModel? = null

    // 재무 상담용 모델 (System Instruction에 상담사 역할 포함)
    private var financialAdvisorModel: GenerativeModel? = null

    // 요약 전용 모델
    private var summaryModel: GenerativeModel? = null

    companion object {
        private const val TAG = "gemini"
    }

    // DataStore에서 API 키 가져오기 (캐싱)
    private suspend fun getApiKey(): String {
        if (cachedApiKey.isNullOrBlank()) {
            cachedApiKey = settingsDataStore.getGeminiApiKey()
        }
        return cachedApiKey ?: ""
    }

    // 쿼리 분석용 모델 가져오기 (System Instruction 포함)
    private suspend fun getQueryAnalyzerModel(): GenerativeModel? {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return null

        if (queryAnalyzerModel == null || cachedApiKey != apiKey) {
            queryAnalyzerModel = GenerativeModel(
                modelName = "gemini-2.5-pro",
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

        if (financialAdvisorModel == null || cachedApiKey != apiKey) {
            financialAdvisorModel = GenerativeModel(
                modelName = "gemini-2.5-pro",
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

        if (summaryModel == null || cachedApiKey != apiKey) {
            summaryModel = GenerativeModel(
                modelName = "gemini-2.5-flash",
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
                modelName = "gemini-2.5-flash-lite",
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
            Log.w(TAG, "인사이트 생성 실패: ${e.message}")
            null
        }
    }

    override suspend fun setApiKey(key: String) {
        cachedApiKey = key
        queryAnalyzerModel = null
        financialAdvisorModel = null
        summaryModel = null
        settingsDataStore.saveGeminiApiKey(key)
    }

    override suspend fun hasApiKey(): Boolean {
        return getApiKey().isNotBlank()
    }

    override suspend fun analyzeQueryNeeds(contextualMessage: String): Result<DataQueryRequest?> {
        return try {
            Log.d(TAG, "=== analyzeQueryNeeds 시작 ===")

            val model = getQueryAnalyzerModel()
            if (model == null) {
                Log.e(TAG, "API 키가 설정되지 않음")
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

            Log.d(TAG, "=== 쿼리 분석 시스템 인스트럭션 ===")
            Log.d(TAG, ChatPrompts.getQueryAnalyzerSystemInstruction(context))
            Log.d(
                TAG,
                "=== 시스템 인스트럭션 끝 (길이: ${ChatPrompts.getQueryAnalyzerSystemInstruction(context).length}) ==="
            )
            Log.d(TAG, "=== 쿼리 분석 프롬프트 ===")
            Log.d(TAG, prompt)
            Log.d(TAG, "=== 프롬프트 끝 (길이: ${prompt.length}) ===")

            val response = model.generateContent(prompt)
            val responseText = response.text ?: return Result.success(null)

            Log.d(TAG, "Gemini 쿼리 분석 응답: $responseText")

            val queryRequest = DataQueryParser.parseQueryRequest(responseText)
            Log.d(TAG, "파싱된 쿼리: $queryRequest")
            Result.success(queryRequest)
        } catch (e: Exception) {
            Log.e(TAG, "쿼리 분석 실패", e)
            Log.e(TAG, "에러 메시지: ${e.message}")
            Log.e(TAG, "에러 클래스: ${e.javaClass.simpleName}")
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
            Log.d(TAG, "=== generateFinalAnswer 시작 ===")
            Log.d(TAG, "사용자 메시지: $userMessage")
            Log.d(TAG, "쿼리 결과 수: ${queryResults.size}")
            Log.d(TAG, "액션 결과 수: ${actionResults.size}")

            val model = getFinancialAdvisorModel()
            if (model == null) {
                Log.e(TAG, "API 키가 설정되지 않음")
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

            Log.d(TAG, "=== 최종 답변 프롬프트 ===")
            Log.d(TAG, prompt)
            Log.d(TAG, "=== 프롬프트 끝 (길이: ${prompt.length}) ===")
            Log.d(TAG, "Gemini 호출 중...")

            val response = model.generateContent(prompt)
            val responseText = response.text ?: "응답을 받지 못했어요."

            Log.d(TAG, "Gemini 최종 응답: $responseText")

            Result.success(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "최종 답변 생성 실패", e)
            Log.e(TAG, "에러 메시지: ${e.message}")
            Log.e(TAG, "에러 클래스: ${e.javaClass.simpleName}")
            Result.failure(Exception("요청 실패: ${e.message}"))
        }
    }

    override suspend fun generateFinalAnswerWithContext(contextPrompt: String): Result<String> {
        return try {
            Log.d(TAG, "=== generateFinalAnswerWithContext 시작 ===")
            Log.d(TAG, "=== 최종 답변 시스템 인스트럭션 ===")
            Log.d(TAG, ChatPrompts.getFinancialAdvisorSystemInstruction(context))
            Log.d(
                TAG,
                "=== 시스템 인스트럭션 끝 (길이: ${ChatPrompts.getFinancialAdvisorSystemInstruction(context).length}) ==="
            )
            Log.d(TAG, "=== 컨텍스트 기반 최종 답변 프롬프트 ===")
            Log.d(TAG, contextPrompt)
            Log.d(TAG, "=== 프롬프트 끝 (길이: ${contextPrompt.length}) ===")

            val model = getFinancialAdvisorModel()
            if (model == null) {
                return Result.failure(Exception("API 키가 설정되지 않았습니다."))
            }

            val response = model.generateContent(contextPrompt)
            val responseText = response.text ?: "응답을 받지 못했어요."

            Log.d(TAG, "Gemini 최종 응답: ${responseText.take(200)}...")
            Result.success(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "컨텍스트 기반 답변 생성 실패", e)
            Result.failure(Exception("요청 실패: ${e.message}"))
        }
    }

    override suspend fun simpleChat(userMessage: String): Result<String> {
        return try {
            Log.d(TAG, "=== simpleChat 시작 ===")
            Log.d(TAG, "=== 심플 채팅 시스템 인스트럭션 ===")
            Log.d(TAG, ChatPrompts.getFinancialAdvisorSystemInstruction(context))
            Log.d(
                TAG,
                "=== 시스템 인스트럭션 끝 (길이: ${ChatPrompts.getFinancialAdvisorSystemInstruction(context).length}) ==="
            )
            Log.d(TAG, "=== 심플 채팅 프롬프트 ===")
            Log.d(TAG, userMessage)
            Log.d(TAG, "=== 프롬프트 끝 (길이: ${userMessage.length}) ===")

            val model = getFinancialAdvisorModel()
            if (model == null) {
                Log.e(TAG, "API 키가 설정되지 않음")
                return Result.failure(Exception("API 키가 설정되지 않았습니다. 설정에서 Gemini API 키를 입력해주세요."))
            }

            Log.d(TAG, "Gemini 호출 중...")
            val response = model.generateContent(userMessage)
            val responseText = response.text ?: "응답을 받지 못했어요."

            Log.d(TAG, "Gemini 응답: $responseText")

            Result.success(responseText)
        } catch (e: Exception) {
            Log.e(TAG, "simpleChat 실패", e)
            Log.e(TAG, "에러 메시지: ${e.message}")
            Log.e(TAG, "에러 클래스: ${e.javaClass.simpleName}")
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

            Log.d(TAG, "=== [타이틀 생성] 시스템 인스트럭션 ===")
            Log.d(TAG, ChatPrompts.getSummarySystemInstruction(context))
            Log.d(TAG, "=== 시스템 인스트럭션 끝 ===")
            Log.d(TAG, "=== [타이틀 생성] 요청 ===")
            Log.d(TAG, prompt)
            val response = model.generateContent(prompt)
            val title = response.text?.trim()?.take(20)
            Log.d(TAG, "=== [타이틀 생성] 응답: $title ===")
            if (title.isNullOrBlank()) null else title
        } catch (e: Exception) {
            Log.w(TAG, "채팅 타이틀 생성 실패: ${e.message}")
            null
        }
    }

    override suspend fun generateRollingSummary(
        existingSummary: String?,
        newMessages: String
    ): Result<String> {
        return try {
            Log.d(TAG, "=== Rolling Summary 생성 시작 ===")

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

            Log.d(TAG, "=== 요약 시스템 인스트럭션 ===")
            Log.d(TAG, ChatPrompts.getSummarySystemInstruction(context))
            Log.d(TAG, "=== 시스템 인스트럭션 끝 ===")
            Log.d(TAG, "=== 요약 프롬프트 ===")
            Log.d(TAG, prompt)
            Log.d(TAG, "=== 프롬프트 끝 (길이: ${prompt.length}) ===")
            val response = model.generateContent(prompt)
            val summaryText = response.text ?: return Result.failure(Exception("요약 응답 없음"))

            Log.d(TAG, "생성된 요약: $summaryText")
            Result.success(summaryText.trim())
        } catch (e: Exception) {
            Log.e(TAG, "Rolling Summary 생성 실패", e)
            Result.failure(Exception("요약 생성 실패: ${e.message}"))
        }
    }
}
