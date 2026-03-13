package com.sanha.moneytalk.core.sms2

import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.notification.SmsNotificationManager
import com.sanha.moneytalk.core.util.CardNameNormalizer
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.feature.home.data.StoreRuleRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS 수신 즉시 파싱/저장/알림을 담당하는 프로세서.
 *
 * [SmsReceiver]에서 goAsync() 범위 내에서 호출되며, 로컬 파싱만 사용하여
 * ~100ms 이내에 완료된다 (Gemini 호출 없음).
 *
 * 처리 흐름:
 * 1. 발신번호 필터 (010/070 차단)
 * 2. 사전 필터 (비결제 키워드 + 구조 검증)
 * 3. 수입/지출 분류
 * 4. Regex 룰 매칭 (지출) 또는 IncomeParser (수입)
 * 5. StoreRule 적용 (Tier 0)
 * 6. DB 저장 + 알림 표시
 *
 * smsId 형식은 [SmsReaderV2]와 동일하여 후속 전체 동기화에서 dedup 처리됨.
 */
@Singleton
class SmsInstantProcessor @Inject constructor(
    private val preFilter: SmsPreFilter,
    private val incomeFilter: SmsIncomeFilter,
    private val regexRuleMatcher: SmsRegexRuleMatcher,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val storeRuleRepository: StoreRuleRepository,
    private val smsExclusionRepository: SmsExclusionRepository,
    private val notificationManager: SmsNotificationManager
) {

    companion object {
        /** 마지막 즉시 저장 시각 (앱 콜드스타트 시 silent sync 판단용) */
        @Volatile
        var lastInstantSaveTime: Long = 0L
            private set

        /** 즉시 저장 후 배치 파이프라인으로 재검증할 smsId 목록 */
        private val pendingReconciliationIds = ConcurrentHashMap.newKeySet<String>()

        fun snapshotPendingReconciliationIds(): Set<String> = pendingReconciliationIds.toSet()

        fun clearPendingReconciliationIds(smsIds: Collection<String>) {
            smsIds.forEach { pendingReconciliationIds.remove(it) }
        }

        private fun markPendingReconciliation(smsId: String) {
            lastInstantSaveTime = System.currentTimeMillis()
            pendingReconciliationIds.add(smsId)
        }
    }

    /** 즉시 처리 결과 */
    sealed interface Result {
        data class Expense(val entity: ExpenseEntity) : Result
        data class Income(val entity: IncomeEntity) : Result
        data object Skipped : Result
        data class Error(val message: String) : Result
    }

    /**
     * 단일 SMS를 즉시 파싱 → DB 저장 → 알림 표시.
     *
     * BroadcastReceiver의 goAsync() 범위 내에서 호출.
     * 전체 처리 시간 ~50-100ms.
     *
     * @param address 발신번호 (원본)
     * @param body SMS 본문
     * @param timestampMillis SMS 수신 시각 (밀리초)
     */
    suspend fun processAndSave(
        address: String,
        body: String,
        timestampMillis: Long
    ): Result {
        // 1. 발신번호 필터 (010/070 개인번호 차단)
        if (SmsFilter.shouldSkipBySender(address, body)) {
            return Result.Skipped
        }

        // 2. 사전 필터 (비결제 키워드)
        if (preFilter.isObviouslyNonPayment(body)) {
            return Result.Skipped
        }

        // 3. 구조 필터 (길이, 금액 패턴 등)
        if (preFilter.lacksPaymentRequirements(body)) {
            return Result.Skipped
        }

        // 4. 사용자 제외 키워드 체크
        val exclusionKeywords = smsExclusionRepository.getAllKeywordStrings()
        if (exclusionKeywords.isNotEmpty()) {
            val bodyLower = body.lowercase()
            if (exclusionKeywords.any { bodyLower.contains(it) }) {
                return Result.Skipped
            }
        }

        // 5. 수입/지출 분류
        val (smsType, _) = incomeFilter.classify(body)
        val smsId = generateSmsId(address, body, timestampMillis)

        return when (smsType) {
            SmsType.PAYMENT -> processExpense(address, body, timestampMillis, smsId)
            SmsType.INCOME -> processIncome(address, body, timestampMillis, smsId)
            SmsType.SKIP -> Result.Skipped
        }
    }

    private suspend fun processExpense(
        address: String,
        body: String,
        timestamp: Long,
        smsId: String
    ): Result {
        // Dedup 체크
        if (expenseRepository.existsBySmsId(smsId)) {
            return Result.Skipped
        }

        // Regex 룰 매칭 (Fast Path)
        val smsInput = SmsInput(id = smsId, body = body, address = address, date = timestamp)
        val matchResult = regexRuleMatcher.matchPaymentCandidates(listOf(smsInput))
        val parsed = matchResult.matched.firstOrNull()

        if (parsed == null) {
            // Regex 미매칭 → 전체 동기화에서 벡터/LLM 파이프라인으로 처리
            MoneyTalkLogger.i("[InstantSMS] regex 미매칭, 전체 동기화 대기: ${smsId.take(30)}")
            return Result.Skipped
        }

        // 로컬 카테고리 분류
        val category = if (parsed.analysis.category.isNotBlank() &&
            parsed.analysis.category != "미분류" &&
            parsed.analysis.category != "기타"
        ) {
            parsed.analysis.category
        } else {
            SmsParser.inferCategory(parsed.analysis.storeName, body)
        }

        val normalizedCard = CardNameNormalizer.normalizeWithFallback(
            parsed.analysis.cardName, body
        )

        var entity = ExpenseEntity(
            amount = parsed.analysis.amount,
            storeName = parsed.analysis.storeName,
            category = category,
            cardName = normalizedCard,
            dateTime = DateUtils.parseDateTime(parsed.analysis.dateTime),
            originalSms = body,
            smsId = smsId,
            senderAddress = SmsFilter.normalizeAddress(address)
        )

        // StoreRule 적용 (Tier 0)
        entity = applyStoreRules(entity)

        expenseRepository.insert(entity)
        markPendingReconciliation(smsId)
        MoneyTalkLogger.i("[InstantSMS] 지출 저장: ${entity.storeName} ${entity.amount}원 [${entity.category}]")

        // 알림
        notificationManager.showExpenseNotification(
            amount = entity.amount,
            storeName = entity.storeName,
            cardName = entity.cardName
        )

        return Result.Expense(entity)
    }

    private suspend fun processIncome(
        address: String,
        body: String,
        timestamp: Long,
        smsId: String
    ): Result {
        // Dedup 체크
        if (incomeRepository.existsBySmsId(smsId)) {
            return Result.Skipped
        }

        val amount = SmsIncomeParser.extractIncomeAmount(body)
        if (amount <= 0) return Result.Skipped

        val incomeType = SmsIncomeParser.extractIncomeType(body)
        val source = SmsIncomeParser.extractIncomeSource(body)
        val dateTimeStr = SmsIncomeParser.extractDateTime(body, timestamp)
        val category = mapIncomeTypeToCategory(incomeType)

        val entity = IncomeEntity(
            smsId = smsId,
            amount = amount,
            type = incomeType,
            source = source,
            description = if (source.isNotBlank()) "${source}에서 $incomeType" else incomeType,
            isRecurring = incomeType == "급여",
            dateTime = DateUtils.parseDateTime(dateTimeStr),
            originalSms = body,
            senderAddress = SmsFilter.normalizeAddress(address),
            category = category
        )

        incomeRepository.insert(entity)
        markPendingReconciliation(smsId)
        MoneyTalkLogger.i("[InstantSMS] 수입 저장: ${entity.source} ${entity.amount}원 [$category]")

        // 알림
        notificationManager.showIncomeNotification(
            amount = entity.amount,
            source = entity.source,
            incomeType = entity.type
        )

        return Result.Income(entity)
    }

    /** StoreRule 적용 (카테고리 + 고정지출) */
    private suspend fun applyStoreRules(entity: ExpenseEntity): ExpenseEntity {
        val matchedRule = storeRuleRepository.findMatchingRule(entity.storeName)
            ?: return entity
        return entity.copy(
            category = matchedRule.category ?: entity.category,
            isFixed = matchedRule.isFixed ?: entity.isFixed
        )
    }

    /** 수입 type → category 초기 매핑 (MainViewModel.mapIncomeTypeToCategory와 동일) */
    private fun mapIncomeTypeToCategory(type: String): String {
        return when (type) {
            "급여" -> "급여"
            "보너스" -> "상여금"
            "정산" -> "더치페이"
            else -> "미분류"
        }
    }

    /** SmsReaderV2.generateSmsId()와 동일한 형식으로 smsId 생성 (address 정규화 필수) */
    private fun generateSmsId(address: String, body: String, date: Long): String {
        return "${SmsFilter.normalizeAddress(address)}_${date}_${body.hashCode()}"
    }
}
