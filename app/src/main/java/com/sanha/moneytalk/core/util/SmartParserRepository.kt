package com.sanha.moneytalk.core.util

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.sanha.moneytalk.core.database.dao.MerchantVectorDao
import com.sanha.moneytalk.core.database.dao.SmsPatternDao
import com.sanha.moneytalk.core.database.entity.MerchantVectorEntity
import com.sanha.moneytalk.core.database.entity.SmsPatternEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.feature.chat.data.SmsAnalysisResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini SMS 파싱 결과 JSON 모델
 */
data class GeminiParseResult(
    @SerializedName("amount") val amount: Int = 0,
    @SerializedName("merchant") val merchant: String = "",
    @SerializedName("dateTime") val dateTime: String = "",
    @SerializedName("cardName") val cardName: String = "",
    @SerializedName("category") val category: String = "기타"
)

/**
 * 파싱 결과와 출처 정보
 */
data class SmartParseResult(
    val result: SmsAnalysisResult,
    val source: ParseSource,
    val confidence: Float = 1.0f
)

enum class ParseSource {
    VECTOR_MATCH,    // 벡터 유사도 매칭 (AI 호출 없음)
    REGEX_LOCAL,     // 기존 로컬 정규식 파싱
    GEMINI_LLM       // Gemini API 호출 (폴백)
}

/**
 * 벡터 우선(Vector-First) 지능형 SMS 파싱 파이프라인
 *
 * 처리 순서:
 * 1. SMS 원문 → 벡터화(Embedding)
 * 2. 로컬 DB의 성공 패턴 벡터와 유사도 비교
 *    - 유사도 ≥ 0.98: 기존 패턴으로 즉시 파싱 (Zero-cost)
 * 3. 유사도 낮으면: 기존 정규식 파서(SmsParser) 시도
 * 4. 정규식도 실패 시: Gemini Flash-Lite에 원문 전송하여 파싱 (Fallback)
 * 5. 성공 시 벡터 + 결과를 DB에 저장 (자가 학습)
 */
@Singleton
class SmartParserRepository @Inject constructor(
    private val smsPatternDao: SmsPatternDao,
    private val merchantVectorDao: MerchantVectorDao,
    private val embeddingRepository: EmbeddingRepository,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "SmartParser"
        private const val SMS_SIMILARITY_THRESHOLD = 0.98f
        private const val MERCHANT_SIMILARITY_THRESHOLD = 0.90f
        private const val GEMINI_PARSE_MODEL = "gemini-1.5-flash"

        private const val GEMINI_PARSE_PROMPT = """한국 카드 결제 SMS를 분석하여 JSON으로 반환해줘.

[규칙]
- amount: 결제 금액 (정수, 콤마 제거)
- merchant: 가맹점명 (가게 이름만)
- dateTime: "YYYY-MM-DD HH:mm" 형식
- cardName: 카드사명 (KB국민, 신한, 삼성, 현대 등)
- category: 카테고리 (식비/카페/교통/쇼핑/구독/의료·건강/문화·여가/교육/생활/기타 중 택1)

[응답 형식]
JSON만 반환 (다른 텍스트 없이):
{"amount": 15000, "merchant": "스타벅스", "dateTime": "2026-01-15 14:30", "cardName": "신한", "category": "카페"}

[SMS 원문]
"""
    }

    private var geminiParseModel: GenerativeModel? = null
    private var cachedApiKey: String? = null
    private val gson = Gson()

    private suspend fun getGeminiModel(): GenerativeModel? {
        val apiKey = settingsDataStore.getGeminiApiKey()
        if (apiKey.isBlank()) return null

        if (geminiParseModel == null || cachedApiKey != apiKey) {
            cachedApiKey = apiKey
            geminiParseModel = GenerativeModel(
                modelName = GEMINI_PARSE_MODEL,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.1f
                    topK = 10
                    topP = 0.9f
                    maxOutputTokens = 256
                }
            )
        }
        return geminiParseModel
    }

    /**
     * 메인 파싱 파이프라인
     * Vector-First → Regex → Gemini Fallback → 자가 학습
     */
    suspend fun smartParse(smsBody: String, smsTimestamp: Long): SmartParseResult {
        Log.d(TAG, "=== Smart Parse Start ===")
        Log.d(TAG, "SMS: ${smsBody.take(50)}...")

        // Step 1: SMS 벡터화
        val smsVector = embeddingRepository.embed(smsBody)

        // Step 2: 벡터 유사도 매칭 시도
        if (smsVector != null) {
            val vectorMatch = tryVectorMatch(smsVector, smsBody, smsTimestamp)
            if (vectorMatch != null) {
                Log.d(TAG, "Vector match found! (${vectorMatch.source})")
                return vectorMatch
            }
        }

        // Step 3: 기존 정규식 파서 시도
        val regexResult = tryRegexParse(smsBody, smsTimestamp)
        if (regexResult != null) {
            Log.d(TAG, "Regex parse success!")
            // 정규식 성공 시에도 벡터 학습 (벡터가 있는 경우)
            if (smsVector != null) {
                learnPattern(smsVector, smsBody, regexResult.result, "regex")
            }
            // 가맹점 벡터 학습
            if (smsVector != null) {
                learnMerchant(regexResult.result.storeName, regexResult.result.category)
            }
            return regexResult
        }

        // Step 4: Gemini LLM Fallback
        Log.d(TAG, "Falling back to Gemini LLM...")
        val geminiResult = tryGeminiParse(smsBody, smsTimestamp)
        if (geminiResult != null) {
            Log.d(TAG, "Gemini parse success!")
            // Gemini 성공 시 벡터 학습
            if (smsVector != null) {
                learnPattern(smsVector, smsBody, geminiResult.result, "gemini")
            }
            // 가맹점 벡터 학습
            learnMerchant(geminiResult.result.storeName, geminiResult.result.category)
            return geminiResult
        }

        // 최종 폴백: 기본 정규식 결과 반환 (파싱 불완전하더라도)
        Log.d(TAG, "All methods failed, returning basic regex result")
        val basicResult = SmsParser.parseSms(smsBody, smsTimestamp)
        return SmartParseResult(
            result = basicResult,
            source = ParseSource.REGEX_LOCAL,
            confidence = 0.3f
        )
    }

    /**
     * Step 2: 벡터 유사도 매칭
     */
    private suspend fun tryVectorMatch(
        smsVector: FloatArray,
        smsBody: String,
        smsTimestamp: Long
    ): SmartParseResult? {
        return try {
            val patterns = smsPatternDao.getAllPatterns()
            if (patterns.isEmpty()) return null

            val vectors = patterns.map { it.vector }
            val match = VectorUtils.findMostSimilar(
                query = smsVector,
                candidates = vectors,
                threshold = SMS_SIMILARITY_THRESHOLD
            ) ?: return null

            val (index, similarity) = match
            val matchedPattern = patterns[index]

            // 매칭된 패턴으로 파싱
            val result = applyPatternParsing(smsBody, smsTimestamp, matchedPattern)

            // 성공 횟수 증가
            smsPatternDao.incrementSuccessCount(matchedPattern.id)

            Log.d(TAG, "Vector match: similarity=$similarity, pattern=${matchedPattern.smsTemplate.take(30)}")

            SmartParseResult(
                result = result,
                source = ParseSource.VECTOR_MATCH,
                confidence = similarity
            )
        } catch (e: Exception) {
            Log.e(TAG, "Vector match failed", e)
            null
        }
    }

    /**
     * 매칭된 패턴을 사용하여 SMS 파싱
     * 패턴의 카드사 정보와 기존 정규식을 결합
     */
    private fun applyPatternParsing(
        smsBody: String,
        smsTimestamp: Long,
        pattern: SmsPatternEntity
    ): SmsAnalysisResult {
        // 기본 정규식 파싱으로 가변 데이터(금액, 시간) 추출
        val amount = SmsParser.extractAmount(smsBody) ?: 0
        val dateTime = SmsParser.extractDateTime(smsBody, smsTimestamp)
        val storeName = SmsParser.extractStoreName(smsBody)

        // 패턴에서 카드사 정보 활용
        val cardName = if (SmsParser.extractCardName(smsBody) != "기타") {
            SmsParser.extractCardName(smsBody)
        } else {
            pattern.cardName
        }

        // 가게명에 대한 카테고리는 가맹점 벡터 DB에서 찾기
        val category = SmsParser.inferCategory(storeName, smsBody)

        return SmsAnalysisResult(
            amount = amount,
            storeName = storeName,
            category = category,
            dateTime = dateTime,
            cardName = cardName
        )
    }

    /**
     * Step 3: 기존 정규식 파서
     * 금액과 가게명이 모두 정상 추출된 경우에만 성공으로 간주
     */
    private fun tryRegexParse(smsBody: String, smsTimestamp: Long): SmartParseResult? {
        val result = SmsParser.parseSms(smsBody, smsTimestamp)

        // 최소 품질 검증: 금액이 0보다 크고 가게명이 "결제"가 아닌 경우
        if (result.amount > 0 && result.storeName != "결제" && result.storeName.length >= 2) {
            return SmartParseResult(
                result = result,
                source = ParseSource.REGEX_LOCAL,
                confidence = 0.85f
            )
        }
        return null
    }

    /**
     * Step 4: Gemini LLM Fallback
     */
    private suspend fun tryGeminiParse(smsBody: String, smsTimestamp: Long): SmartParseResult? {
        return try {
            val model = getGeminiModel() ?: return null

            val prompt = GEMINI_PARSE_PROMPT + smsBody
            val response = model.generateContent(prompt)
            val responseText = response.text ?: return null

            // JSON 파싱
            val jsonStart = responseText.indexOf("{")
            val jsonEnd = responseText.lastIndexOf("}") + 1
            if (jsonStart == -1 || jsonEnd == 0) return null

            val jsonStr = responseText.substring(jsonStart, jsonEnd)
            val parseResult = gson.fromJson(jsonStr, GeminiParseResult::class.java) ?: return null

            // 유효성 검증
            if (parseResult.amount <= 0) return null

            val result = SmsAnalysisResult(
                amount = parseResult.amount,
                storeName = parseResult.merchant.ifBlank { "결제" },
                category = parseResult.category.ifBlank { "기타" },
                dateTime = parseResult.dateTime.ifBlank {
                    SmsParser.extractDateTime(smsBody, smsTimestamp)
                },
                cardName = parseResult.cardName.ifBlank {
                    SmsParser.extractCardName(smsBody)
                }
            )

            SmartParseResult(
                result = result,
                source = ParseSource.GEMINI_LLM,
                confidence = 0.95f
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gemini parse failed", e)
            null
        }
    }

    /**
     * 자가 학습: SMS 패턴 저장
     */
    private suspend fun learnPattern(
        smsVector: FloatArray,
        smsBody: String,
        result: SmsAnalysisResult,
        source: String
    ) {
        try {
            // 이미 유사한 패턴이 있는지 확인
            val existingPatterns = smsPatternDao.getAllPatterns()
            val existingVectors = existingPatterns.map { it.vector }
            val existing = VectorUtils.findMostSimilar(
                query = smsVector,
                candidates = existingVectors,
                threshold = SMS_SIMILARITY_THRESHOLD
            )

            if (existing != null) {
                // 기존 패턴의 성공 횟수만 증가
                val pattern = existingPatterns[existing.first]
                smsPatternDao.incrementSuccessCount(pattern.id)
                Log.d(TAG, "Pattern already learned, incrementing count: ${pattern.id}")
            } else {
                // 새 패턴 저장
                val pattern = SmsPatternEntity(
                    vector = smsVector,
                    smsTemplate = smsBody,
                    cardName = result.cardName,
                    amountPattern = "regex",
                    storeNamePattern = result.storeName,
                    dateTimePattern = "regex",
                    parsingSource = source
                )
                smsPatternDao.insert(pattern)
                Log.d(TAG, "New pattern learned from $source: ${smsBody.take(30)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pattern learning failed", e)
        }
    }

    /**
     * 가맹점 벡터 학습
     * 새로운 가맹점이면 벡터 저장, 기존 가맹점이면 매칭 횟수 증가
     */
    private suspend fun learnMerchant(merchantName: String, category: String) {
        if (merchantName.isBlank() || merchantName == "결제") return

        try {
            val existing = merchantVectorDao.getMerchantByName(merchantName)
            if (existing != null) {
                merchantVectorDao.incrementMatchCount(existing.id)
                return
            }

            // 새 가맹점 벡터 생성
            val merchantVector = embeddingRepository.embed(merchantName) ?: return

            val merchant = MerchantVectorEntity(
                merchantName = merchantName,
                vector = merchantVector,
                category = category
            )
            merchantVectorDao.insert(merchant)
            Log.d(TAG, "New merchant learned: $merchantName -> $category")
        } catch (e: Exception) {
            Log.e(TAG, "Merchant learning failed", e)
        }
    }

    /**
     * 의미 기반 가맹점 카테고리 매핑
     * 텍스트가 100% 일치하지 않아도 벡터 유사도로 카테고리 자동 할당
     *
     * @return 매칭된 카테고리, 없으면 null
     */
    suspend fun findCategoryByMerchant(merchantName: String): String? {
        if (merchantName.isBlank()) return null

        try {
            // 1. 정확한 이름 매칭 시도
            val exactMatch = merchantVectorDao.getMerchantByName(merchantName)
            if (exactMatch != null) {
                merchantVectorDao.incrementMatchCount(exactMatch.id)
                return exactMatch.category
            }

            // 2. 기존 StoreAliasManager 별칭 매칭 시도
            val normalizedName = StoreAliasManager.normalizeStoreName(merchantName)
            if (normalizedName != null) {
                val aliasMatch = merchantVectorDao.getMerchantByName(normalizedName)
                if (aliasMatch != null) {
                    merchantVectorDao.incrementMatchCount(aliasMatch.id)
                    return aliasMatch.category
                }
            }

            // 3. 벡터 유사도 매칭
            val merchantVector = embeddingRepository.embed(merchantName) ?: return null
            val allMerchants = merchantVectorDao.getAllMerchants()
            if (allMerchants.isEmpty()) return null

            val vectors = allMerchants.map { it.vector }
            val match = VectorUtils.findMostSimilar(
                query = merchantVector,
                candidates = vectors,
                threshold = MERCHANT_SIMILARITY_THRESHOLD
            ) ?: return null

            val (index, similarity) = match
            val matchedMerchant = allMerchants[index]

            Log.d(TAG, "Merchant vector match: '$merchantName' ≈ '${matchedMerchant.merchantName}' (sim=$similarity)")

            merchantVectorDao.incrementMatchCount(matchedMerchant.id)
            return matchedMerchant.category
        } catch (e: Exception) {
            Log.e(TAG, "Category lookup failed for: $merchantName", e)
            return null
        }
    }

    /**
     * 카테고리 매핑 적용된 완전한 파싱
     * smartParse 후 가맹점 벡터 DB로 카테고리 보정
     */
    suspend fun smartParseWithCategoryMapping(smsBody: String, smsTimestamp: Long): SmartParseResult {
        val parseResult = smartParse(smsBody, smsTimestamp)

        // 카테고리가 "기타"인 경우 벡터 매핑 시도
        if (parseResult.result.category == "기타" && parseResult.result.storeName != "결제") {
            val mappedCategory = findCategoryByMerchant(parseResult.result.storeName)
            if (mappedCategory != null && mappedCategory != "기타") {
                val updatedResult = SmsAnalysisResult(
                    amount = parseResult.result.amount,
                    storeName = parseResult.result.storeName,
                    category = mappedCategory,
                    dateTime = parseResult.result.dateTime,
                    cardName = parseResult.result.cardName
                )
                return parseResult.copy(result = updatedResult)
            }
        }

        return parseResult
    }

    /**
     * 학습 통계 조회
     */
    suspend fun getLearningStats(): LearningStats {
        return LearningStats(
            patternCount = smsPatternDao.getPatternCount(),
            merchantCount = merchantVectorDao.getMerchantCount(),
            cacheSize = embeddingRepository.getCacheSize()
        )
    }
}

data class LearningStats(
    val patternCount: Int,
    val merchantCount: Int,
    val cacheSize: Int
)
