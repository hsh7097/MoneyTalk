package com.sanha.moneytalk.core.sms2

import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.database.entity.supportsFixedExpense
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
import kotlin.math.abs

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
    private val notificationManager: SmsNotificationManager,
    private val settingsDataStore: SettingsDataStore
) {

    companion object {
        /** 알림-origin row와 실제 SMS/MMS row를 같은 건으로 볼 시간 오차 범위 */
        private const val NOTIFICATION_BRIDGE_WINDOW_MS = 60_000L

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

        /**
         * 비-SMS 소스(앱 알림 등)에서 regex 미매칭된 항목의 대기 큐.
         *
         * 알림은 디바이스 SMS inbox에 존재하지 않아 배치 동기화의 ContentProvider 읽기에서
         * 누락된다. 이 큐에 담아두면 다음 배치 동기화 시 SMS 목록에 합류하여
         * 벡터/LLM 파이프라인을 탈 수 있다.
         */
        private val pendingNotificationInputs = ConcurrentHashMap<String, SmsInput>()

        /** 미매칭 알림을 배치 처리 대기 큐에 추가 */
        fun addPendingNotification(input: SmsInput) {
            pendingNotificationInputs[input.id] = input
        }

        /** 대기 중인 알림 목록을 꺼내고 해당 항목만 제거한다 */
        fun drainPendingNotifications(): List<SmsInput> {
            val result = pendingNotificationInputs.values.toList()
            // clear() 대신 꺼낸 항목만 제거 — drain 중 새로 추가된 항목 유실 방지
            result.forEach { pendingNotificationInputs.remove(it.id) }
            return result
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

        // 5. 사용자 삭제 블랙리스트 체크
        val smsId = generateSmsId(address, body, timestampMillis)
        if (DeletedSmsTracker.isDeleted(smsId)) {
            return Result.Skipped
        }

        // 6. 수입/지출 분류
        val (smsType, _) = incomeFilter.classify(body)

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
            // 비-SMS 소스(알림 등)는 디바이스 inbox에 없어 배치 동기화에서 누락됨.
            // 대기 큐에 보관하여 다음 배치에서 벡터/LLM 파이프라인 합류.
            if (address.startsWith("NOTI_")) {
                addPendingNotification(smsInput)
            }
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

        val currentIsNotification = address.startsWith("NOTI_")
        val existingBridgeExpense = findBridgeExpense(entity, currentIsNotification)
        if (existingBridgeExpense != null) {
            if (currentIsNotification) {
                MoneyTalkLogger.i("[InstantSMS] 기존 실문자/알림 row 존재 → 알림 중복 저장 스킵: ${smsId.take(40)}")
                return Result.Skipped
            }

            entity = entity.copy(
                id = existingBridgeExpense.id,
                memo = existingBridgeExpense.memo,
                createdAt = existingBridgeExpense.createdAt
            )
            clearPendingReconciliationIds(listOf(existingBridgeExpense.smsId))
        }

        expenseRepository.insert(entity)
        markPendingReconciliation(smsId)
        MoneyTalkLogger.i("[InstantSMS] 지출 저장: ${entity.storeName} ${entity.amount}원 [${entity.category}]")

        // 알림 (설정에서 활성화된 경우만)
        if (settingsDataStore.isNotificationEnabled() && existingBridgeExpense == null) {
            notificationManager.showExpenseNotification(
                amount = entity.amount,
                storeName = entity.storeName,
                cardName = entity.cardName
            )
        }

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

        val currentIsNotification = address.startsWith("NOTI_")
        val existingBridgeIncome = findBridgeIncome(entity, currentIsNotification)
        if (existingBridgeIncome != null) {
            if (currentIsNotification) {
                MoneyTalkLogger.i("[InstantSMS] 기존 실문자/알림 row 존재 → 수입 알림 중복 저장 스킵: ${smsId.take(40)}")
                return Result.Skipped
            }

            val existingSmsId = existingBridgeIncome.smsId
            entity.copy(
                id = existingBridgeIncome.id,
                memo = existingBridgeIncome.memo,
                recurringDay = existingBridgeIncome.recurringDay,
                createdAt = existingBridgeIncome.createdAt
            ).also {
                incomeRepository.insert(it)
                if (existingSmsId != null) {
                    clearPendingReconciliationIds(listOf(existingSmsId))
                }
                markPendingReconciliation(smsId)
                MoneyTalkLogger.i("[InstantSMS] 수입 row 교체 저장: ${it.source} ${it.amount}원 [$category]")
                if (settingsDataStore.isNotificationEnabled()) {
                    // 알림-origin row를 실제 SMS row로 교체하는 경우 중복 알림 방지
                    if (!existingBridgeIncome.senderAddress.startsWith("NOTI_")) {
                        notificationManager.showIncomeNotification(
                            amount = it.amount,
                            source = it.source,
                            incomeType = it.type
                        )
                    }
                }
                return Result.Income(it)
            }
        }

        incomeRepository.insert(entity)
        markPendingReconciliation(smsId)
        MoneyTalkLogger.i("[InstantSMS] 수입 저장: ${entity.source} ${entity.amount}원 [$category]")

        // 알림 (설정에서 활성화된 경우만)
        if (settingsDataStore.isNotificationEnabled()) {
            notificationManager.showIncomeNotification(
                amount = entity.amount,
                source = entity.source,
                incomeType = entity.type
            )
        }

        return Result.Income(entity)
    }

    private suspend fun findBridgeExpense(
        entity: ExpenseEntity,
        currentIsNotification: Boolean
    ): ExpenseEntity? {
        val startTime = maxOf(0L, entity.dateTime - NOTIFICATION_BRIDGE_WINDOW_MS)
        val endTime = entity.dateTime + NOTIFICATION_BRIDGE_WINDOW_MS

        return expenseRepository.getExpensesByDateRangeOnce(startTime, endTime)
            .firstOrNull { existing ->
                existing.smsId != entity.smsId &&
                    abs(existing.dateTime - entity.dateTime) <= NOTIFICATION_BRIDGE_WINDOW_MS &&
                    existing.amount == entity.amount &&
                    existing.storeName == entity.storeName &&
                    existing.originalSms == entity.originalSms &&
                    (currentIsNotification || existing.senderAddress.startsWith("NOTI_"))
            }
    }

    private suspend fun findBridgeIncome(
        entity: IncomeEntity,
        currentIsNotification: Boolean
    ): IncomeEntity? {
        val startTime = maxOf(0L, entity.dateTime - NOTIFICATION_BRIDGE_WINDOW_MS)
        val endTime = entity.dateTime + NOTIFICATION_BRIDGE_WINDOW_MS

        return incomeRepository.getIncomesByDateRangeOnce(startTime, endTime)
            .firstOrNull { existing ->
                existing.smsId != entity.smsId &&
                    abs(existing.dateTime - entity.dateTime) <= NOTIFICATION_BRIDGE_WINDOW_MS &&
                    existing.amount == entity.amount &&
                    existing.type == entity.type &&
                    existing.source == entity.source &&
                    existing.originalSms == entity.originalSms &&
                    (currentIsNotification || existing.senderAddress.startsWith("NOTI_"))
            }
    }

    /** StoreRule 적용 (카테고리 + 고정지출) */
    private suspend fun applyStoreRules(entity: ExpenseEntity): ExpenseEntity {
        val matchedRule = storeRuleRepository.findMatchingRule(entity.storeName)
            ?: return entity
        return entity.copy(
            category = matchedRule.category ?: entity.category,
            isFixed = if (entity.supportsFixedExpense()) {
                matchedRule.isFixed ?: entity.isFixed
            } else {
                entity.isFixed
            }
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
