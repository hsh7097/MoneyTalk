package com.sanha.moneytalk.core.sms

import androidx.room.withTransaction
import com.sanha.moneytalk.core.database.AppDatabase
import com.sanha.moneytalk.core.database.dao.ExpenseIngestionResult
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.IncomeCategoryMapper
import com.sanha.moneytalk.core.model.TransferDirection
import com.sanha.moneytalk.core.ui.ClassificationState
import com.sanha.moneytalk.core.util.CardNameNormalizer
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import com.sanha.moneytalk.core.util.StatsExclusionClassifier
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.feature.home.data.StoreRuleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** 화면 동기화와 백그라운드 재처리가 공유하는 SMS 저장/중복 보정 정책. */
@Singleton
class SmsIngestionWriter @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryClassifierService: CategoryClassifierService,
    private val storeRuleRepository: StoreRuleRepository,
    private val classificationState: ClassificationState,
    private val database: AppDatabase
) {
    companion object {
        private const val DB_BATCH_INSERT_SIZE = 100
        private const val SMS_ID_LOOKUP_CHUNK_SIZE = 500
        private const val FUZZY_TIME_MARGIN_MS = 60_000L
        private const val FUZZY_CANDIDATE_PADDING_MS = 3L * 24 * 60 * 60 * 1000
    }

    data class SaveProgress(val message: String, val current: Int? = null, val total: Int? = null)

    data class WriteResult(val expenses: SaveResult, val incomes: SaveResult) {
        /** 저장 또는 명확한 중복 판정까지 끝난 입력. 미파싱과 구분해 재시도를 막는다. */
        val handledSmsIds: Set<String>
            get() = expenses.handledSmsIds + incomes.handledSmsIds
    }

    /** 호출자는 동일 epoch로 ClassificationState에 Job을 등록한 동안 실행한다. */
    suspend fun write(
        expenses: List<SmsParseResult>,
        incomes: List<SmsInput>,
        registrationEpoch: Long,
        onProgress: (SaveProgress) -> Unit = {}
    ): WriteResult {
        ensureWritable(registrationEpoch)
        val inputs = expenses.map { it.input } + incomes
        val dates = inputs.map { it.date } +
            expenses.map { DateUtils.parseDateTime(it.analysis.dateTime) } +
            incomes.map { DateUtils.parseDateTime(SmsIncomeParser.extractDateTime(it.body, it.date)) }
        val range = (dates.minOrNull() ?: 0L) to (dates.maxOrNull() ?: 0L)
        // LLM 처리 도중 즉시 저장된 내역도 포함하도록 저장 직전에 다시 읽는다.
        val snapshot = buildExistingSmsSnapshot(inputs, range)
        val expenseResult = saveExpenses(expenses, snapshot, registrationEpoch, onProgress)
        val incomeResult = saveIncomes(incomes, snapshot, registrationEpoch, onProgress)
        ensureWritable(registrationEpoch)
        return WriteResult(expenseResult, incomeResult)
    }

    private suspend fun ensureWritable(registrationEpoch: Long) {
        currentCoroutineContext().ensureActive()
        if (!classificationState.isRegistrationEpochCurrent(registrationEpoch)) {
            throw CancellationException("데이터 초기화 전에 생성된 SMS 저장 요청입니다")
        }
    }

    data class SaveResult(
        val newCount: Int = 0,
        val reconciledCount: Int = 0,
        val handledSmsIds: Set<String> = emptySet()
    )

    private data class ParsedSmsId(
        val address: String,
        val timestamp: Long,
        val bodyHash: String
    ) {
        val contentKey: String = "${address}_${bodyHash}"
    }

    data class SmsMatchCandidate(
        val smsId: String,
        val timestamp: Long,
        val originalSms: String? = null
    )

    private data class RefundDuplicateCandidate(
        val income: IncomeEntity,
        val batchIndex: Int? = null
    )

    data class ExistingSmsSnapshot(
        val exactSmsIds: Set<String> = emptySet(),
        val expensesBySmsId: Map<String, ExpenseEntity> = emptyMap(),
        val incomesBySmsId: Map<String, IncomeEntity> = emptyMap(),
        val incomes: List<IncomeEntity> = emptyList(),
        val restoredIncomesByContentKey: Map<String, IncomeEntity> = emptyMap(),
        val contentIndex: Map<String, List<SmsMatchCandidate>> = emptyMap()
    )

    /**
     * 읽은 SMS 목록을 기존 내역 및 pending 상태와 비교해 신규 처리 대상만 남긴다.
     */
    fun readAndFilterSms(
        allSmsList: List<SmsInput>,
        pendingContentIndex: Map<String, List<SmsMatchCandidate>>,
        existingSnapshot: ExistingSmsSnapshot,
        reprocessExisting: Boolean = false
    ): List<SmsInput> {
        val acceptedContentIndex = mutableMapOf<String, MutableList<SmsMatchCandidate>>()
        val newSmsList = mutableListOf<SmsInput>()

        for (sms in allSmsList) {
            // 사용자가 명시적으로 삭제한 SMS는 재처리하지 않음
            if (DeletedSmsTracker.isDeleted(sms.id)) continue

            val contentKey = buildContentKey(sms.address, sms.body)
            val currentBatchMatch = findClosestCandidate(
                smsId = sms.id,
                originalSms = sms.body,
                contentKey = contentKey,
                timestamp = sms.date,
                candidateIndex = acceptedContentIndex
            )
            val existsInDbExact = sms.id in existingSnapshot.exactSmsIds
            val dbMatch = findClosestCandidate(
                smsId = sms.id,
                originalSms = sms.body,
                contentKey = contentKey,
                timestamp = sms.date,
                candidateIndex = existingSnapshot.contentIndex
            )
            val existsInDb = existsInDbExact || dbMatch != null

            val pendingMatch = findClosestCandidate(
                smsId = sms.id,
                originalSms = sms.body,
                contentKey = contentKey,
                timestamp = sms.date,
                candidateIndex = pendingContentIndex
            )

            // DAO까지 도달하지 않는 중복도 출처를 연결해야 삭제 후 다른 수신시각 ID로 복원되지 않는다.
            listOfNotNull(currentBatchMatch, dbMatch, pendingMatch).forEach { match ->
                DeletedSmsTracker.linkSameTransaction(sms.id, match.smsId)
            }
            if (DeletedSmsTracker.isDeleted(sms.id)) continue

            val shouldProcess = when {
                currentBatchMatch != null -> false
                reprocessExisting -> true
                existsInDb && pendingMatch != null -> true
                existsInDb -> false
                else -> true
            }

            if (shouldProcess) {
                newSmsList += sms
                acceptedContentIndex.getOrPut(contentKey) { mutableListOf() } += SmsMatchCandidate(
                    smsId = sms.id,
                    timestamp = sms.date,
                    originalSms = sms.body
                )
            }
        }
        MoneyTalkLogger.i("syncSmsV2 중복 제거: ${allSmsList.size}건 → ${newSmsList.size}건")

        return newSmsList
    }

    /**
     * 현재 읽은 SMS 기준으로 기존 DB 스냅샷을 구성한다.
     *
     * exact dedupe는 chunk 조회로 유지하고,
     * fuzzy dedupe 후보는 대상 기간 주변의 기존 거래만 읽어 메모리 사용을 제한한다.
     */
    suspend fun buildExistingSmsSnapshot(
        allSmsList: List<SmsInput>,
        targetMonthRange: Pair<Long, Long>
    ): ExistingSmsSnapshot {
        if (allSmsList.isEmpty()) return ExistingSmsSnapshot()

        val smsIdChunks = allSmsList
            .map { it.id }
            .distinct()
            .chunked(SMS_ID_LOOKUP_CHUNK_SIZE)

        val exactSmsIds = coroutineScope {
            val expenseExistingDeferred = async {
                val ids = HashSet<String>()
                for (chunk in smsIdChunks) {
                    ids.addAll(expenseRepository.getExistingSmsIds(chunk))
                }
                ids
            }
            val incomeExistingDeferred = async {
                val ids = HashSet<String>()
                for (chunk in smsIdChunks) {
                    ids.addAll(incomeRepository.getExistingSmsIds(chunk))
                }
                ids
            }
            expenseExistingDeferred.await() + incomeExistingDeferred.await()
        }

        val minSmsTimestamp = allSmsList.minOf { it.date }
        val maxSmsTimestamp = allSmsList.maxOf { it.date }
        val candidateStart = maxOf(
            0L,
            minOf(targetMonthRange.first, minSmsTimestamp) - FUZZY_CANDIDATE_PADDING_MS
        )
        val candidateEnd = maxOf(targetMonthRange.second, maxSmsTimestamp) + FUZZY_CANDIDATE_PADDING_MS
        val existingData = coroutineScope {
            val expensesDeferred = async {
                expenseRepository.getExpensesByDateRangeOnce(candidateStart, candidateEnd)
            }
            val incomesDeferred = async {
                incomeRepository.getIncomesByDateRangeOnce(candidateStart, candidateEnd)
            }
            expensesDeferred.await() to incomesDeferred.await()
        }

        val existingExpenses = existingData.first
        val existingIncomes = existingData.second

        return ExistingSmsSnapshot(
            exactSmsIds = exactSmsIds,
            expensesBySmsId = existingExpenses.associateBy { it.smsId },
            incomesBySmsId = existingIncomes
                .mapNotNull { income -> income.smsId?.let { it to income } }
                .toMap(),
            incomes = existingIncomes,
            restoredIncomesByContentKey = buildRestoredIncomeContentIndex(existingIncomes),
            contentIndex = buildExistingContentIndex(existingExpenses, existingIncomes)
        )
    }

    /**
     * smsId 목록을 fuzzy dedupe용 content index로 변환한다.
     */
    fun buildSmsIdCandidateIndex(
        smsIds: Collection<String>,
        existingSnapshot: ExistingSmsSnapshot
    ): Map<String, List<SmsMatchCandidate>> {
        return smsIds.mapNotNull { smsId ->
            parseSmsId(smsId)?.let { parsed ->
                parsed.contentKey to SmsMatchCandidate(
                    smsId = smsId,
                    timestamp = parsed.timestamp,
                    originalSms = existingSnapshot.expensesBySmsId[smsId]?.originalSms
                        ?: existingSnapshot.incomesBySmsId[smsId]?.originalSms
                )
            }
        }.groupBy({ it.first }, { it.second })
    }

    private fun buildExistingContentIndex(
        expenses: List<ExpenseEntity>,
        incomes: List<IncomeEntity>
    ): Map<String, List<SmsMatchCandidate>> {
        val entries = mutableListOf<Pair<String, SmsMatchCandidate>>()

        expenses.forEach { expense ->
            parseSmsId(expense.smsId)?.let { parsed ->
                entries += parsed.contentKey to SmsMatchCandidate(
                    smsId = expense.smsId,
                    timestamp = parsed.timestamp,
                    originalSms = expense.originalSms
                )
            }
        }

        incomes.forEach { income ->
            val smsId = income.smsId ?: return@forEach
            parseSmsId(smsId)?.let { parsed ->
                entries += parsed.contentKey to SmsMatchCandidate(
                    smsId = smsId,
                    timestamp = parsed.timestamp,
                    originalSms = income.originalSms
                )
            }
        }

        return entries.groupBy({ it.first }, { it.second })
    }

    private fun buildRestoredIncomeContentIndex(
        incomes: List<IncomeEntity>
    ): Map<String, IncomeEntity> {
        return incomes
            .filter { it.smsId.isNullOrBlank() }
            .mapNotNull { income ->
                buildIncomeContentKey(income)?.let { key -> key to income }
            }
            .toMap()
    }

    private fun parseSmsId(smsId: String): ParsedSmsId? {
        val lastSeparator = smsId.lastIndexOf('_')
        if (lastSeparator <= 0 || lastSeparator == smsId.lastIndex) return null

        val secondLastSeparator = smsId.lastIndexOf('_', startIndex = lastSeparator - 1)
        if (secondLastSeparator <= 0 || secondLastSeparator == lastSeparator - 1) return null

        val address = smsId.substring(0, secondLastSeparator)
        val timestamp = smsId.substring(secondLastSeparator + 1, lastSeparator).toLongOrNull()
            ?: return null
        val bodyHash = smsId.substring(lastSeparator + 1)
        if (bodyHash.isBlank()) return null

        return ParsedSmsId(
            address = address,
            timestamp = timestamp,
            bodyHash = bodyHash
        )
    }

    private fun buildContentKey(address: String, body: String): String =
        "${SmsFilter.normalizeAddress(address)}_${body.hashCode()}"

    private fun buildIncomeContentKey(income: IncomeEntity): String? {
        val originalSms = income.originalSms?.takeIf { it.isNotBlank() } ?: return null
        val senderAddress = SmsFilter.normalizeAddress(income.senderAddress)
            .takeIf { it.isNotBlank() } ?: return null
        return "${buildContentKey(senderAddress, originalSms)}_${income.dateTime}_${income.amount}"
    }

    private fun findClosestCandidate(
        smsId: String,
        originalSms: String,
        contentKey: String,
        timestamp: Long,
        candidateIndex: Map<String, List<SmsMatchCandidate>>
    ): SmsMatchCandidate? {
        val candidates = candidateIndex[contentKey] ?: return null
        var closestCandidate: SmsMatchCandidate? = null
        var closestDiff = Long.MAX_VALUE

        for (candidate in candidates) {
            if (!hasMatchingSmsEvidence(smsId, originalSms, candidate)) continue
            val diff = abs(candidate.timestamp - timestamp)
            if (diff <= FUZZY_TIME_MARGIN_MS && diff < closestDiff) {
                closestCandidate = candidate
                closestDiff = diff
            }
        }

        return closestCandidate
    }

    fun findMatchingSmsIds(
        sms: SmsInput,
        candidateIndex: Map<String, List<SmsMatchCandidate>>
    ): Set<String> {
        val parsed = parseSmsId(sms.id) ?: return emptySet()
        val candidates = candidateIndex[parsed.contentKey] ?: return emptySet()
        val matches = mutableSetOf<String>()

        for (candidate in candidates) {
            if (!hasMatchingSmsEvidence(sms.id, sms.body, candidate)) continue
            if (abs(candidate.timestamp - parsed.timestamp) <= FUZZY_TIME_MARGIN_MS) {
                matches += candidate.smsId
            }
        }

        return matches
    }

    private fun hasMatchingSmsEvidence(
        smsId: String,
        originalSms: String,
        candidate: SmsMatchCandidate
    ): Boolean {
        return candidate.smsId == smsId || TransactionSemanticDedupe.hasSameSmsRedeliveryEvidence(
            originalSms, candidate.originalSms.orEmpty()
        )
    }

    private fun supportsFixedExpense(expense: ExpenseEntity): Boolean {
        return expense.transactionType == "EXPENSE" ||
            expense.transactionType == "TRANSFER"
    }

    /**
     * 지출 파싱 결과를 ExpenseEntity로 변환하여 DB에 배치 저장
     *
     * 카테고리 분류를 DB INSERT 전에 완료하여 UI 깜빡임을 방지합니다.
     * Phase 1: 로컬 분류 (캐시 + 키워드)
     * Phase 2: Gemini 사전 분류 ("미분류" 항목을 API로 분류)
     * Phase 3: 분류 완료된 엔티티를 DB에 배치 저장
     */
    private suspend fun saveExpenses(
        expenses: List<com.sanha.moneytalk.core.sms.SmsParseResult>,
        existingSnapshot: ExistingSmsSnapshot,
        registrationEpoch: Long,
        onProgress: (SaveProgress) -> Unit
    ): SaveResult {
        if (expenses.isEmpty()) return SaveResult()

        onProgress(SaveProgress("지출 저장 중..."))

        // Phase 1: 엔티티 빌드 + 로컬 분류
        val entities = ArrayList<ExpenseEntity>(expenses.size)
        for (parsed in expenses) {
            val localCategory = if (parsed.analysis.category.isNotBlank() &&
                parsed.analysis.category != "미분류" &&
                parsed.analysis.category != "기타"
            ) {
                parsed.analysis.category
            } else {
                categoryClassifierService.getCategory(
                    storeName = parsed.analysis.storeName,
                    originalSms = parsed.input.body
                )
            }

            entities.add(
                ExpenseEntity(
                    amount = parsed.analysis.amount,
                    storeName = parsed.analysis.storeName,
                    category = localCategory,
                    cardName = CardNameNormalizer.normalizeWithFallback(parsed.analysis.cardName, parsed.input.body),
                    dateTime = DateUtils.parseDateTime(parsed.analysis.dateTime),
                    originalSms = parsed.input.body,
                    smsId = parsed.input.id,
                    senderAddress = SmsFilter.normalizeAddress(parsed.input.address)
                )
            )
        }

        // Phase 1.5: StoreRule 적용 (최우선 = Tier 0)
        val allRules = storeRuleRepository.getAllOnce()
        val ruleCandidates = StoreRuleRepository.buildMatchCandidates(allRules)
        fun findMatchingStoreRule(storeName: String): StoreRuleEntity? {
            return StoreRuleRepository.findBestMatchingRuleFromCandidates(ruleCandidates, storeName)
        }

        fun resolveStatsExclusion(entity: ExpenseEntity): Boolean {
            return findMatchingStoreRule(entity.storeName)?.isExcludedFromStats
                ?: StatsExclusionClassifier.shouldExcludeExpense(entity)
        }

        if (allRules.isNotEmpty()) {
            for (i in entities.indices) {
                val entity = entities[i]
                val matchedRule = findMatchingStoreRule(entity.storeName)
                if (matchedRule != null) {
                    entities[i] = entity.copy(
                        category = matchedRule.category ?: entity.category,
                        isFixed = if (supportsFixedExpense(entity)) {
                            matchedRule.isFixed ?: entity.isFixed
                        } else {
                            entity.isFixed
                        },
                        isExcludedFromStats = matchedRule.isExcludedFromStats ?: entity.isExcludedFromStats
                    )
                }
            }
        }

        // Phase 2: "미분류" 가게명을 사전 분류 (로컬 규칙은 AI 서비스와 무관하게 수행)
        val unclassifiedStores = entities
            .filter { it.category == "미분류" }
            .map { it.storeName }
            .distinct()

        if (unclassifiedStores.isNotEmpty()) {
            val isAiServiceAvailable = categoryClassifierService.canAttemptGeminiClassification()
            if (isAiServiceAvailable) {
                onProgress(SaveProgress("AI가 카테고리 분류 중..."))
            }
            try {
                val classificationResults = categoryClassifierService.classifyStoreNamesInMemory(
                    storeNames = unclassifiedStores,
                    onStepProgress = if (isAiServiceAvailable) {
                        { step, current, total ->
                            onProgress(SaveProgress("AI가 카테고리 분류 중...\n$step", current, total))
                        }
                    } else {
                        null
                    }
                )

                if (classificationResults.isNotEmpty()) {
                    for (i in entities.indices) {
                        val entity = entities[i]
                        if (entity.category == "미분류") {
                            val newCategory = classificationResults[entity.storeName]
                            if (newCategory != null) {
                                val isTransfer = newCategory == Category.TRANSFER_GENERAL.displayName
                                val updated = entity.copy(
                                    category = newCategory,
                                    transactionType = if (isTransfer) "TRANSFER" else entity.transactionType,
                                    transferDirection = if (isTransfer) TransferDirection.WITHDRAWAL.dbValue else entity.transferDirection
                                )
                                entities[i] = updated.copy(
                                    isExcludedFromStats = resolveStatsExclusion(updated)
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MoneyTalkLogger.w("사전 카테고리 분류 실패 (무시): ${e.message}")
            }
        }

        val smsIds = entities.map { it.smsId }.distinct()
        val existingExpensesBySmsId = expenseRepository.getExpensesBySmsIds(smsIds)
            .associateBy { it.smsId }
        val existingIncomesBySmsId = incomeRepository.getIncomesBySmsIds(smsIds)
            .mapNotNull { income -> income.smsId?.let { it to income } }
            .toMap()
        val crossTypeIncomeIdsBySmsId = mutableMapOf<String, Long>()
        val handledSmsIds = mutableSetOf<String>()
        var newCount = 0
        var reconciledCount = 0

        for (i in entities.indices) {
            val entity = entities[i]

            // 1. 정확한 ID로 먼저 확인
            var existingExpense = existingExpensesBySmsId[entity.smsId]
            var existingIncome = existingIncomesBySmsId[entity.smsId]

            // 2. 정확한 ID가 없으면 Fuzzy 매칭 시도
            if (existingExpense == null && existingIncome == null) {
                val parsedSmsId = parseSmsId(entity.smsId)
                if (parsedSmsId != null) {
                    val fuzzyMatch = findClosestCandidate(
                        smsId = entity.smsId,
                        originalSms = entity.originalSms,
                        contentKey = buildContentKey(entity.senderAddress, entity.originalSms),
                        timestamp = parsedSmsId.timestamp,
                        candidateIndex = existingSnapshot.contentIndex
                    )
                    val fuzzyId = fuzzyMatch?.smsId
                    if (fuzzyId != null) {
                        existingExpense = existingSnapshot.expensesBySmsId[fuzzyId]
                        if (existingExpense == null) {
                            existingIncome = existingSnapshot.incomesBySmsId[fuzzyId]
                        }
                        if (existingExpense != null || existingIncome != null) {
                            DeletedSmsTracker.linkSameTransaction(entity.smsId, fuzzyId)
                        }
                    }
                }
            }

            if (existingIncome != null) {
                crossTypeIncomeIdsBySmsId[entity.smsId] = existingIncome.id
            }

            if (existingExpense != null) {
                entities[i] = entity.copy(
                    id = existingExpense.id,
                    memo = existingExpense.memo,
                    isExcludedFromStats = existingExpense.isExcludedFromStats,
                    createdAt = existingExpense.createdAt
                )
            } else {
                entities[i] = entity.copy(
                    isExcludedFromStats = resolveStatsExclusion(entity)
                )
            }
        }

        // Phase 3: 사용자가 삭제한 항목 제외 후 DB에 배치 저장
        val filteredEntities = entities.filterNot { DeletedSmsTracker.isDeleted(it.smsId) }
        onProgress(SaveProgress("지출 저장 중..."))
        for (chunk in filteredEntities.chunked(DB_BATCH_INSERT_SIZE)) {
            database.withTransaction {
                ensureWritable(registrationEpoch)
                chunk.mapNotNull { crossTypeIncomeIdsBySmsId[it.smsId] }
                    .distinct().forEach { incomeRepository.deleteById(it) }
                val outcomes = expenseRepository.insertAllIngested(chunk)
                outcomes.forEachIndexed { index, outcome ->
                    when (outcome) {
                        is ExpenseIngestionResult.Inserted -> {
                            if (chunk[index].smsId in crossTypeIncomeIdsBySmsId) reconciledCount++
                            else newCount++
                        }
                        is ExpenseIngestionResult.Updated -> reconciledCount++
                        ExpenseIngestionResult.Skipped -> Unit
                    }
                }
                ensureWritable(registrationEpoch)
            }
            handledSmsIds += chunk.map { it.smsId }
        }

        return SaveResult(
            newCount = newCount,
            reconciledCount = reconciledCount,
            handledSmsIds = handledSmsIds
        )
    }

    /**
     * 수입 SMS를 SmsIncomeParser로 파싱하여 DB에 배치 저장
     */
    private suspend fun saveIncomes(
        incomes: List<SmsInput>,
        existingSnapshot: ExistingSmsSnapshot,
        registrationEpoch: Long,
        onProgress: (SaveProgress) -> Unit
    ): SaveResult {
        if (incomes.isEmpty()) return SaveResult()

        onProgress(SaveProgress("수입 처리 중..."))
        val batch = mutableListOf<IncomeEntity>()
        val batchSmsIds = mutableSetOf<String>()
        var newCount = 0
        var reconciledCount = 0

        for (income in incomes) {
            try {
                val amount = SmsIncomeParser.extractIncomeAmount(income.body)
                val incomeType = SmsIncomeParser.extractIncomeType(income.body)
                val source = SmsIncomeParser.extractIncomeSource(income.body)
                val dateTime = SmsIncomeParser.extractDateTime(income.body, income.date)

                if (amount > 0) {
                    batchSmsIds += income.id
                    batch.add(
                        IncomeEntity(
                            smsId = income.id,
                            amount = amount,
                            type = incomeType,
                            source = source,
                            description = if (source.isNotBlank()) "${source}에서 $incomeType" else incomeType,
                            isRecurring = incomeType == "급여",
                            dateTime = DateUtils.parseDateTime(dateTime),
                            originalSms = income.body,
                            senderAddress = SmsFilter.normalizeAddress(income.address),
                            category = IncomeCategoryMapper.categoryForType(incomeType)
                        )
                    )
                }
            } catch (e: Exception) {
                MoneyTalkLogger.e("수입 처리 실패: ${income.id} - ${e.message}")
            }
        }

        val existingIncomesBySmsId = incomeRepository.getIncomesBySmsIds(batchSmsIds.toList())
            .mapNotNull { income -> income.smsId?.let { it to income } }
            .toMap()
        val existingExpensesBySmsId = expenseRepository.getExpensesBySmsIds(batchSmsIds.toList())
            .associateBy { it.smsId }
        val crossTypeExpenseIdsBySmsId = mutableMapOf<String, Long>()
        val duplicateIncomeIdsBySmsId = mutableMapOf<String, Long>()
        val isNewFlags = BooleanArray(batch.size)
        val skipInsertFlags = BooleanArray(batch.size)
        val refundDuplicateCandidates = existingSnapshot.incomes
            .map { RefundDuplicateCandidate(income = it) }
            .toMutableList()

        for (i in batch.indices) {
            val entity = batch[i]
            val smsId = entity.smsId ?: continue

            var existingIncome = existingIncomesBySmsId[smsId]
            var existingExpense = existingExpensesBySmsId[smsId]

            // Fuzzy 매칭
            if (existingIncome == null && existingExpense == null) {
                val parsedSmsId = parseSmsId(smsId)
                if (parsedSmsId != null) {
                    val fuzzyMatch = findClosestCandidate(
                        smsId = smsId,
                        originalSms = entity.originalSms.orEmpty(),
                        contentKey = buildContentKey(entity.senderAddress, entity.originalSms.orEmpty()),
                        timestamp = parsedSmsId.timestamp,
                        candidateIndex = existingSnapshot.contentIndex
                    )
                    val fuzzyId = fuzzyMatch?.smsId
                    if (fuzzyId != null) {
                        existingIncome = existingSnapshot.incomesBySmsId[fuzzyId]
                        if (existingIncome == null) {
                            existingExpense = existingSnapshot.expensesBySmsId[fuzzyId]
                        }
                        if (existingIncome != null || existingExpense != null) {
                            DeletedSmsTracker.linkSameTransaction(smsId, fuzzyId)
                        }
                    }
                }
            }

            if (existingIncome == null && existingExpense == null) {
                existingIncome = findRestoredIncomeDuplicate(entity, existingSnapshot)
            }

            val semanticDuplicateIncome = if (existingExpense == null) {
                findSemanticDuplicateRefundIncome(entity, refundDuplicateCandidates)
            } else {
                null
            }
            if (
                semanticDuplicateIncome != null &&
                shouldPreferCurrentRefundIncome(entity, semanticDuplicateIncome.income)
            ) {
                DeletedSmsTracker.linkSameTransaction(smsId, semanticDuplicateIncome.income.smsId)
                semanticDuplicateIncome.batchIndex?.let { duplicateIndex ->
                    skipInsertFlags[duplicateIndex] = true
                    if (isNewFlags[duplicateIndex]) {
                        isNewFlags[duplicateIndex] = false
                        newCount--
                    }
                }
                refundDuplicateCandidates.remove(semanticDuplicateIncome)
                // DB에 저장된 환불 행을 정본으로 보정할 때 기존 알림의 상세 대상 ID도 유지한다.
                if (existingIncome == null && semanticDuplicateIncome.income.id > 0L) {
                    existingIncome = semanticDuplicateIncome.income
                }
                if (semanticDuplicateIncome.batchIndex == null &&
                    semanticDuplicateIncome.income.id > 0L &&
                    semanticDuplicateIncome.income.id != existingIncome?.id
                ) {
                    duplicateIncomeIdsBySmsId[smsId] = semanticDuplicateIncome.income.id
                }
                if (existingIncome != null) {
                    reconciledCount++
                } else {
                    isNewFlags[i] = true
                    newCount++
                }
            } else if (semanticDuplicateIncome != null) {
                if (existingIncome != null) {
                    reconciledCount++
                    // 같은 배치에서 이미 정본으로 보정한 ID를 뒤늦은 안내 원문으로 되돌리지 않는다.
                    if (semanticDuplicateIncome.batchIndex != null &&
                        existingIncome.id == semanticDuplicateIncome.income.id
                    ) {
                        DeletedSmsTracker.linkSameTransaction(smsId, semanticDuplicateIncome.income.smsId)
                        skipInsertFlags[i] = true
                    }
                } else {
                    DeletedSmsTracker.linkSameTransaction(smsId, semanticDuplicateIncome.income.smsId)
                    reconciledCount++
                    skipInsertFlags[i] = true
                    MoneyTalkLogger.i(
                        "수입 중복 알림 스킵: amount=${entity.amount}, existingId=${semanticDuplicateIncome.income.id}"
                    )
                }
            } else if (existingIncome != null || existingExpense != null) {
                reconciledCount++
            } else {
                isNewFlags[i] = true
                newCount++
            }

            if (existingExpense != null) {
                crossTypeExpenseIdsBySmsId[smsId] = existingExpense.id
            }

            if (existingIncome != null) {
                batch[i] = entity.copy(
                    id = existingIncome.id,
                    memo = existingIncome.memo,
                    recurringDay = existingIncome.recurringDay,
                    createdAt = existingIncome.createdAt
                )
            }

            val isDeletedIncome = batch[i].smsId?.let { DeletedSmsTracker.isDeleted(it) } == true
            if (!skipInsertFlags[i] && !isDeletedIncome) {
                refundDuplicateCandidates += RefundDuplicateCandidate(
                    income = batch[i],
                    batchIndex = i
                )
            }
        }

        // 사용자가 삭제한 항목 제외 후 저장
        val filteredBatch = batch.filterIndexed { index, entity ->
            !skipInsertFlags[index] &&
                entity.smsId?.let { DeletedSmsTracker.isDeleted(it) } != true
        }
        if (filteredBatch.isNotEmpty()) {
            database.withTransaction {
                ensureWritable(registrationEpoch)
                val writableBatch = filteredBatch.filterNot {
                    it.smsId?.let(DeletedSmsTracker::isDeleted) == true
                }
                writableBatch.mapNotNull { crossTypeExpenseIdsBySmsId[it.smsId] }
                    .distinct().forEach { expenseRepository.deleteById(it) }
                writableBatch.mapNotNull { duplicateIncomeIdsBySmsId[it.smsId] }
                    .distinct()
                    .filterNot { duplicateId -> writableBatch.any { it.id == duplicateId } }
                    .forEach { incomeRepository.deleteById(it) }
                for (chunk in writableBatch.chunked(DB_BATCH_INSERT_SIZE)) {
                    ensureWritable(registrationEpoch)
                    incomeRepository.insertAll(chunk)
                }
                ensureWritable(registrationEpoch)
            }
        }

        return SaveResult(
            newCount = newCount,
            reconciledCount = reconciledCount,
            handledSmsIds = batch.mapNotNull { entity ->
                entity.smsId?.takeUnless { DeletedSmsTracker.isDeleted(it) }
            }.toSet()
        )
    }

    private fun findSemanticDuplicateRefundIncome(
        entity: IncomeEntity,
        candidates: List<RefundDuplicateCandidate>
    ): RefundDuplicateCandidate? {
        return candidates.firstOrNull { candidate ->
            val existing = candidate.income
            RefundIncomeSemanticDedupe.isPotentialDuplicate(entity, existing)
        }
    }

    private fun findRestoredIncomeDuplicate(
        entity: IncomeEntity,
        existingSnapshot: ExistingSmsSnapshot
    ): IncomeEntity? {
        val contentKey = buildIncomeContentKey(entity) ?: return null
        return existingSnapshot.restoredIncomesByContentKey[contentKey]
    }

    private fun shouldPreferCurrentRefundIncome(
        entity: IncomeEntity,
        duplicate: IncomeEntity
    ): Boolean {
        return RefundIncomeSemanticDedupe.shouldPreferCandidate(entity, duplicate)
    }

    private fun isRefundLikeIncome(entity: IncomeEntity): Boolean {
        return RefundIncomeSemanticDedupe.isRefundLike(entity)
    }

    private fun isRefundNoticeIncome(entity: IncomeEntity): Boolean {
        return RefundIncomeSemanticDedupe.isRefundNotice(entity)
    }

}
