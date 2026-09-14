package com.sanha.moneytalk.core.database

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.sanha.moneytalk.core.database.dao.ExpenseIngestionResult
import com.sanha.moneytalk.core.database.dao.IncomeDao
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.model.SmsAnalysisResult
import com.sanha.moneytalk.core.model.CategoryProvider
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.firebase.PremiumManager
import com.sanha.moneytalk.core.notification.SmsNotificationManager
import com.sanha.moneytalk.core.sms.SmsChannelProbeCollector
import com.sanha.moneytalk.core.sms.DeletedSmsTracker
import com.sanha.moneytalk.core.sms.SmsEmbeddingService
import com.sanha.moneytalk.core.sms.SmsIncomeFilter
import com.sanha.moneytalk.core.sms.SmsInstantProcessor
import com.sanha.moneytalk.core.sms.SmsIngestionWriter
import com.sanha.moneytalk.core.sms.SmsInput
import com.sanha.moneytalk.core.sms.SmsOriginSampleCollector
import com.sanha.moneytalk.core.sms.SmsParseResult
import com.sanha.moneytalk.core.sms.SmsPreFilter
import com.sanha.moneytalk.core.sms.SmsRegexRuleMatcher
import com.sanha.moneytalk.core.sms.SmsTemplateEngine
import com.sanha.moneytalk.core.sms.SmsTransactionDateResolver
import com.sanha.moneytalk.core.ui.ClassificationState
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.feature.home.data.StoreRuleRepository
import com.sanha.moneytalk.feature.transactionactions.data.TransactionQuickActionService
import com.sanha.moneytalk.feature.transactionactions.model.TransactionTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpenseIngestionInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ExpenseRepository
    private lateinit var writer: SmsIngestionWriter
    private lateinit var classificationState: ClassificationState
    private val baseTime = 1_783_332_000_000L
    private val preferenceName = "ingestion_test_${UUID.randomUUID()}"
    private lateinit var trackerContext: Context

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        trackerContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences(preferenceName, mode)
        }
        DeletedSmsTracker.init(trackerContext)
        DeletedSmsTracker.clear()
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        repository = ExpenseRepository(database.expenseDao())
        classificationState = ClassificationState()
        writer = SmsIngestionWriter(
            repository,
            IncomeRepository(database.incomeDao()),
            LocalCategoryClassifier(),
            StoreRuleRepository(database.storeRuleDao()),
            classificationState,
            database
        )
    }

    @After
    fun tearDown() {
        database.close()
        DeletedSmsTracker.clear()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DeletedSmsTracker.init(context)
        context.deleteSharedPreferences(preferenceName)
    }

    @Test
    fun concurrentSmsAndAppNotificationKeepOneSmsRecord() = runBlocking(Dispatchers.IO) {
        repeat(30) { index ->
            val sms = expense("sms-$index", time = baseTime + index * 180_000L)
            val app = expense("app-$index", app = true, time = sms.dateTime + 20_000L)
            val results = concurrently(
                { repository.insertIngested(sms) },
                { repository.insertIngested(app) }
            )

            assertEquals(1, results.count { it is ExpenseIngestionResult.Inserted })
            val stored = repository.getExpensesByDateRangeOnce(sms.dateTime, app.dateTime)
            assertEquals(1, stored.size)
            assertEquals(sms.smsId, stored.single().smsId)
            assertEquals(stored.single().id, results.filterIsInstance<ExpenseIngestionResult.Inserted>().single().expenseId)
        }
        assertEquals(30, repository.getExpenseCount())
    }

    @Test
    fun concurrentBatchAndInstantNotificationUseSameDeduplicationBoundary() =
        runBlocking(Dispatchers.IO) {
            val sms = expense("batch-sms")
            val app = expense("instant-app", app = true, time = baseTime + 20_000L)
            concurrently(
                { repository.insertAllIngested(listOf(sms)) },
                { repository.insertIngested(app) }
            )

            assertEquals(1, repository.getExpenseCount())
            assertEquals(sms.smsId, repository.getAllExpensesOnce().single().smsId)
        }

    @Test
    fun smsReplacesAppNotificationWithoutChangingRowIdOrUserMetadata() =
        runBlocking(Dispatchers.IO) {
            val app = expense("app", app = true).copy(
                memo = "분할 정산",
                isFixed = true,
                isExcludedFromStats = true,
                createdAt = baseTime - 10_000L
            )
            repository.insertIngested(app)
            val existing = repository.getAllExpensesOnce().single()

            val result = repository.insertIngested(expense("sms"))

            val stored = repository.getAllExpensesOnce().single()
            assertEquals(ExpenseIngestionResult.Updated(existing.id), result)
            assertEquals(existing.id, stored.id)
            assertEquals("sms", stored.smsId)
            assertEquals(existing.memo, stored.memo)
            assertEquals(existing.createdAt, stored.createdAt)
            assertTrue(stored.isFixed)
            assertTrue(stored.isExcludedFromStats)
        }

    @Test
    fun repeatedDeliveryOfSameSmsDoesNotOverwriteEditedRecord() = runBlocking(Dispatchers.IO) {
        val sms = expense("sms")
        repository.insertIngested(sms)
        val edited = repository.getAllExpensesOnce().single().copy(memo = "사용자 메모")
        repository.update(edited)

        val result = repository.insertIngested(sms)

        assertEquals(ExpenseIngestionResult.Skipped, result)
        assertEquals(edited, repository.getAllExpensesOnce().single())
    }

    @Test
    fun concurrentSmsRedeliveryWithDriftedReceiveTimesKeepsOneRecord() = runBlocking(Dispatchers.IO) {
        repeat(30) { index ->
            val transactionAt = baseTime + index * 180_000L
            val first = smsExpense(receivedAt = transactionAt + 7_200_000L, transactionAt = transactionAt)
            val second = smsExpense(receivedAt = transactionAt + 7_202_000L, transactionAt = transactionAt)

            val results = concurrently(
                { repository.insertIngested(first) },
                { repository.insertIngested(second) }
            )

            assertEquals(1, results.count { it is ExpenseIngestionResult.Inserted })
            assertEquals(1, results.count { it == ExpenseIngestionResult.Skipped })
            assertEquals(1, repository.getExpensesByDateRangeOnce(transactionAt, transactionAt).size)
        }
        assertEquals(30, repository.getExpenseCount())
    }

    @Test
    fun concurrentBatchAndInstantSmsRedeliveryKeepOneRecord() = runBlocking(Dispatchers.IO) {
        val first = smsExpense(receivedAt = baseTime)
        val second = smsExpense(receivedAt = baseTime + 2_000L)

        val results = concurrently(
            { repository.insertAllIngested(listOf(first)).single() },
            { repository.insertIngested(second) }
        )

        assertEquals(1, results.count { it is ExpenseIngestionResult.Inserted })
        assertEquals(1, results.count { it == ExpenseIngestionResult.Skipped })
        assertEquals(1, repository.getExpenseCount())
    }

    @Test
    fun concurrentSameMinutePaymentsWithDifferentBalancesRemainSeparate() = runBlocking(Dispatchers.IO) {
        val first = smsExpense(receivedAt = baseTime + 10_000L)
        val second = smsExpense(
            receivedAt = baseTime + 50_000L,
            body = first.originalSms.replace("잔액100,000원", "잔액88,000원")
        )

        val results = concurrently(
            { repository.insertIngested(first) },
            { repository.insertIngested(second) }
        )

        assertEquals(2, results.count { it is ExpenseIngestionResult.Inserted })
        assertEquals(
            setOf(first.smsId, second.smsId),
            repository.getAllExpensesOnce().map { it.smsId }.toSet()
        )
    }

    @Test
    fun concurrentIdenticalMinutePaymentsWithoutBalanceRemainSeparate() = runBlocking(Dispatchers.IO) {
        val first = sameMinutePaymentWithoutBalance(baseTime)
        val second = sameMinutePaymentWithoutBalance(baseTime + 39_000L)
        assertEquals(first.dateTime, second.dateTime)
        assertEquals(first.originalSms, second.originalSms)

        val results = concurrently(
            { repository.insertIngested(first) },
            { repository.insertIngested(second) }
        )

        assertEquals(2, results.count { it is ExpenseIngestionResult.Inserted })
        assertEquals(2, repository.getExpenseCount())
    }

    @Test
    fun writerKeepsIdenticalMinutePaymentsWithoutBalanceInOneBatch() = runBlocking(Dispatchers.IO) {
        val first = sameMinutePaymentWithoutBalance(baseTime)
        val second = sameMinutePaymentWithoutBalance(baseTime + 20_000L)
        val parsed = listOf(parsedExpense(first, baseTime), parsedExpense(second, baseTime + 20_000L))
        val inputs = parsed.map { it.input }
        val snapshot = writer.buildExistingSmsSnapshot(inputs, baseTime to baseTime + 60_000L)

        assertEquals(inputs, writer.readAndFilterSms(inputs, emptyMap(), snapshot))
        val result = writer.write(parsed, emptyList(),
            requireNotNull(classificationState.captureRegistrationEpoch()))

        assertEquals(2, result.expenses.newCount)
        assertEquals(2, repository.getExpenseCount())
    }

    @Test
    fun writerKeepsRepeatedPaymentAndDoesNotCompleteAnotherPendingSms() = runBlocking(Dispatchers.IO) {
        val first = sameMinutePaymentWithoutBalance(baseTime)
        val second = sameMinutePaymentWithoutBalance(baseTime + 39_000L)
        repository.insertIngested(first)
        val firstInput = parsedExpense(first, baseTime).input
        val secondParsed = parsedExpense(second, baseTime + 39_000L)
        val snapshot = writer.buildExistingSmsSnapshot(
            listOf(secondParsed.input), baseTime to baseTime + 60_000L
        )
        val pending = writer.buildSmsIdCandidateIndex(setOf(first.smsId), snapshot)

        assertEquals(listOf(secondParsed.input), writer.readAndFilterSms(
            listOf(secondParsed.input), pending, snapshot
        ))
        assertTrue(writer.findMatchingSmsIds(secondParsed.input, pending).isEmpty())
        assertEquals(setOf(first.smsId), writer.findMatchingSmsIds(firstInput, pending))
        val result = writer.write(listOf(secondParsed), emptyList(),
            requireNotNull(classificationState.captureRegistrationEpoch()))

        assertEquals(1, result.expenses.newCount)
        assertEquals(2, repository.getExpenseCount())
    }

    @Test
    fun smsRedeliveryWindowUsesReceivedTimeInsteadOfTransactionMinute() = runBlocking(Dispatchers.IO) {
        repository.insertIngested(smsExpense(receivedAt = baseTime))

        val withinWindow = repository.insertIngested(smsExpense(receivedAt = baseTime + 60_000L))
        val outsideWindow = repository.insertIngested(smsExpense(receivedAt = baseTime + 60_001L))

        assertEquals(ExpenseIngestionResult.Skipped, withinWindow)
        assertTrue(outsideWindow is ExpenseIngestionResult.Inserted)
        assertEquals(2, repository.getExpenseCount())
    }

    @Test
    fun driftedSmsRedeliveryDoesNotOverwriteUserMetadata() = runBlocking(Dispatchers.IO) {
        repository.insertIngested(smsExpense(receivedAt = baseTime))
        val edited = repository.getAllExpensesOnce().single().copy(
            memo = "사용자 메모",
            category = "쇼핑",
            isFixed = true,
            isExcludedFromStats = true
        )
        repository.update(edited)

        val result = repository.insertIngested(smsExpense(receivedAt = baseTime + 2_000L))

        assertEquals(ExpenseIngestionResult.Skipped, result)
        assertEquals(edited, repository.getAllExpensesOnce().single())
    }

    @Test
    fun sameChannelRepeatedPaymentsWithSameAmountAndBodyRemainSeparate() =
        runBlocking(Dispatchers.IO) {
            repository.insertIngested(smsExpense(receivedAt = baseTime))
            repository.insertIngested(smsExpense(
                receivedAt = baseTime + 20_000L,
                transactionAt = baseTime + 20_000L
            ))
            repository.insertIngested(expense("app-1", app = true, time = baseTime + 180_000L))
            repository.insertIngested(expense("app-2", app = true, time = baseTime + 200_000L))

            assertEquals(4, repository.getExpenseCount())
        }

    @Test
    fun differentCardsAndDistinctTransactionsOutsideWindowRemainSeparate() =
        runBlocking(Dispatchers.IO) {
            repository.insertIngested(expense("sms"))
            repository.insertIngested(expense("other-card", app = true).copy(
                originalSms = "우리카드 5678 승인\n테스트상점\n12,000원"
            ))
            repository.insertIngested(expense("later-app", app = true, time = baseTime + 60_001L))

            assertEquals(3, repository.getExpenseCount())
        }

    @Test
    fun batchReconciliationUpdatesParsingAndKeepsLatestUserMetadata() =
        runBlocking(Dispatchers.IO) {
            val sms = expense("sms")
            repository.insertIngested(sms)
            val stored = repository.getAllExpensesOnce().single()
            repository.update(stored.copy(memo = "사용자 메모", isExcludedFromStats = true))

            repository.insertAllIngested(listOf(sms.copy(category = "식비")))

            val reconciled = repository.getAllExpensesOnce().single()
            assertEquals(stored.id, reconciled.id)
            assertEquals("식비", reconciled.category)
            assertEquals("사용자 메모", reconciled.memo)
            assertTrue(reconciled.isExcludedFromStats)
            assertFalse(reconciled.senderAddress.startsWith("app:"))
        }

    @Test
    fun writerReconcilesTimestampDriftWithoutLosingMemo() = runBlocking(Dispatchers.IO) {
        val sms = expense("unused").copy(originalSms = "우리카드 1234 승인\n테스트상점\n12,000원\n잔액100,000")
        val firstId = "${sms.senderAddress}_${baseTime}_${sms.originalSms.hashCode()}"
        repository.insertIngested(sms.copy(smsId = firstId, memo = "보존할 메모"))
        val existing = repository.getAllExpensesOnce().single()
        val providerId = "${sms.senderAddress}_${baseTime + 20_000L}_${sms.originalSms.hashCode()}"
        val parsed = parsedExpense(sms.copy(smsId = providerId), baseTime + 20_000L)
        val snapshot = writer.buildExistingSmsSnapshot(listOf(parsed.input), baseTime to baseTime + 60_000L)
        val pending = writer.buildSmsIdCandidateIndex(setOf(firstId), snapshot)

        assertEquals(setOf(firstId), writer.findMatchingSmsIds(parsed.input, pending))
        assertEquals(listOf(parsed.input), writer.readAndFilterSms(listOf(parsed.input), pending, snapshot))

        writer.write(
            expenses = listOf(parsed),
            incomes = emptyList(),
            registrationEpoch = requireNotNull(classificationState.captureRegistrationEpoch())
        )

        val reconciled = repository.getAllExpensesOnce().single()
        assertEquals(existing.id, reconciled.id)
        assertEquals(providerId, reconciled.smsId)
        assertEquals(existing.memo, reconciled.memo)
    }

    @Test
    fun filteredTimestampDriftStaysDeletedAfterTrackerReload() = runBlocking(Dispatchers.IO) {
        val original = smsExpense(baseTime)
        val provider = smsExpense(baseTime + 20_000L)
        val id = repository.insert(original)
        val input = parsedExpense(provider, baseTime + 20_000L).input
        val snapshot = writer.buildExistingSmsSnapshot(listOf(input), baseTime to baseTime + 60_000L)

        // Process restart loses pending reconciliation; the provider alias is filtered before the DAO.
        assertTrue(writer.readAndFilterSms(listOf(input), emptyMap(), snapshot).isEmpty())
        deleteTransaction(TransactionTarget.Expense(id))
        DeletedSmsTracker.init(trackerContext)

        val emptySnapshot = writer.buildExistingSmsSnapshot(listOf(input), baseTime to baseTime + 60_000L)
        assertTrue(writer.readAndFilterSms(listOf(input), emptyMap(), emptySnapshot).isEmpty())
        assertEquals(ExpenseIngestionResult.Skipped, repository.insertIngested(provider))
        assertTrue(DeletedSmsTracker.isDeleted(original.smsId))
        assertEquals(0, repository.getExpenseCount())
    }

    @Test
    fun currentBatchTimestampDriftStaysDeletedAfterTrackerReload() = runBlocking(Dispatchers.IO) {
        val original = smsExpense(baseTime)
        val provider = smsExpense(baseTime + 20_000L)
        val inputs = listOf(parsedExpense(original).input, parsedExpense(provider, baseTime + 20_000L).input)
        val filtered = writer.readAndFilterSms(inputs, emptyMap(), SmsIngestionWriter.ExistingSmsSnapshot())
        assertEquals(listOf(inputs.first()), filtered)
        writer.write(listOf(parsedExpense(original)), emptyList(),
            requireNotNull(classificationState.captureRegistrationEpoch()))
        deleteTransaction(TransactionTarget.Expense(repository.getAllExpensesOnce().single().id))
        DeletedSmsTracker.init(trackerContext)

        assertEquals(ExpenseIngestionResult.Skipped, repository.insertIngested(provider))
        assertEquals(0, repository.getExpenseCount())
    }

    @Test
    fun reconciledExpenseTimestampDriftStaysDeletedFromBothSources() = runBlocking(Dispatchers.IO) {
        val original = smsExpense(baseTime)
        val provider = smsExpense(baseTime + 20_000L)
        val id = repository.insert(original)
        writer.write(listOf(parsedExpense(provider, baseTime + 20_000L)), emptyList(),
            requireNotNull(classificationState.captureRegistrationEpoch()))
        assertEquals(provider.smsId, repository.getAllExpensesOnce().single().smsId)
        assertEquals(id, repository.getAllExpensesOnce().single().id)
        deleteTransaction(TransactionTarget.Expense(id))
        DeletedSmsTracker.init(trackerContext)

        assertEquals(ExpenseIngestionResult.Skipped, repository.insertIngested(original))
        assertEquals(ExpenseIngestionResult.Skipped, repository.insertIngested(provider))
        assertEquals(0, repository.getExpenseCount())
    }

    @Test
    fun reconciledIncomeTimestampDriftStaysDeletedFromBothSources() = runBlocking(Dispatchers.IO) {
        val body = "[Web발신]\n[우리은행]\n입금 12,000원\n테스트회사 → 입출금통장(1234)\n잔액 50,000원"
        val original = SmsInput("15889955_${baseTime}_${body.hashCode()}", body, "15889955", baseTime)
        val provider = original.copy(id = "15889955_${baseTime + 20_000L}_${body.hashCode()}", date = baseTime + 20_000L)
        val epoch = requireNotNull(classificationState.captureRegistrationEpoch())
        writer.write(emptyList(), listOf(original), epoch)
        val id = database.incomeDao().getAllIncomesOnce().single().id
        writer.write(emptyList(), listOf(provider), epoch)
        assertEquals(provider.id, database.incomeDao().getAllIncomesOnce().single().smsId)
        assertEquals(id, database.incomeDao().getAllIncomesOnce().single().id)
        deleteTransaction(TransactionTarget.Income(id))
        DeletedSmsTracker.init(trackerContext)

        writer.write(emptyList(), listOf(original, provider), epoch)
        assertTrue(DeletedSmsTracker.isDeleted(original.id))
        assertTrue(DeletedSmsTracker.isDeleted(provider.id))
        assertEquals(0, database.incomeDao().getIncomeCount())
    }

    @Test
    fun writerKeepsPaymentAndItsCancellationInSeparateTables() = runBlocking(Dispatchers.IO) {
        val payment = expense("payment")
        val cancellation = SmsInput(
            id = "cancel",
            body = "12,000원 결제 취소 우리카드 | 테스트상점(일시불)",
            address = "15889955",
            date = baseTime + 120_000L
        )

        writer.write(
            listOf(parsedExpense(payment)),
            listOf(cancellation),
            requireNotNull(classificationState.captureRegistrationEpoch())
        )

        assertEquals(1, repository.getExpenseCount())
        val income = database.incomeDao().getAllIncomesOnce().single()
        assertEquals("cancel", income.smsId)
        assertEquals(12_000, income.amount)
        assertEquals("환불", income.type)
    }

    @Test
    fun writerRejectsRequestCreatedBeforeDataDeletion() = runBlocking(Dispatchers.IO) {
        val previousEpoch = requireNotNull(classificationState.captureRegistrationEpoch())
        classificationState.withRegistrationsPaused { }
        var cancelled = false
        try {
            writer.write(listOf(parsedExpense(expense("old-job"))), emptyList(), previousEpoch)
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertEquals(0, repository.getExpenseCount())
        assertEquals(0, database.incomeDao().getIncomeCount())
    }

    @Test
    fun writerReportsSmsReplacingAppAsReconciliation() = runBlocking(Dispatchers.IO) {
        repository.insertIngested(expense("app", app = true))

        val result = writer.write(
            listOf(parsedExpense(expense("sms"))), emptyList(),
            requireNotNull(classificationState.captureRegistrationEpoch())
        )

        assertEquals(0, result.expenses.newCount)
        assertEquals(1, result.expenses.reconciledCount)
        assertEquals(1, repository.getExpenseCount())
    }

    @Test
    fun writerReportsSkippedDuplicateAsHandled() = runBlocking(Dispatchers.IO) {
        repository.insertIngested(expense("sms"))

        val result = writer.write(
            listOf(parsedExpense(expense("app", app = true))), emptyList(),
            requireNotNull(classificationState.captureRegistrationEpoch())
        )

        assertEquals(0, result.expenses.newCount)
        assertEquals(0, result.expenses.reconciledCount)
        assertEquals(setOf("app"), result.handledSmsIds)
        assertEquals(1, repository.getExpenseCount())
    }

    @Test
    fun writerLeavesUnparsedIncomeUnhandled() = runBlocking(Dispatchers.IO) {
        val input = SmsInput("unparsed", "입금 알림", "15889955", baseTime)

        val result = writer.write(emptyList(), listOf(input),
            requireNotNull(classificationState.captureRegistrationEpoch()))

        assertTrue(result.handledSmsIds.isEmpty())
        assertEquals(0, database.incomeDao().getIncomeCount())
    }

    @Test
    fun writerStoresOrdinaryDepositWithoutAnExpense() = runBlocking(Dispatchers.IO) {
        val input = SmsInput(
            "deposit", "입금 100,000원\n테스터 → 입출금통장(1234)\n잔액 250,000원",
            "15889955", baseTime
        )

        val result = writer.write(emptyList(), listOf(input),
            requireNotNull(classificationState.captureRegistrationEpoch()))

        val stored = database.incomeDao().getAllIncomesOnce().single()
        assertEquals(100_000, stored.amount)
        assertEquals("테스터", stored.source)
        assertEquals("입금", stored.type)
        assertEquals(1, result.incomes.newCount)
        assertEquals(setOf("deposit"), result.handledSmsIds)
        assertEquals(0, repository.getExpenseCount())
    }

    @Test
    fun writerRefundReplacementPreservesNotificationTargetAndMetadata() = runBlocking(Dispatchers.IO) {
        val original = refundNotice()
        val originalId = database.incomeDao().insert(original)

        val result = writer.write(emptyList(), listOf(refundDeposit()),
            requireNotNull(classificationState.captureRegistrationEpoch()))

        val stored = database.incomeDao().getAllIncomesOnce().single()
        assertEquals(originalId, stored.id)
        assertEquals(original.memo, stored.memo)
        assertEquals(original.createdAt, stored.createdAt)
        assertEquals(original.recurringDay, stored.recurringDay)
        assertEquals(refundDeposit().body, stored.originalSms)
        assertEquals("refund-deposit", stored.smsId)
        assertEquals(0, result.incomes.newCount)
        assertEquals(1, result.incomes.reconciledCount)
    }

    @Test
    fun writerRetainsRestoredIdAndDeletesOnlyDistinctRefundNotice() = runBlocking(Dispatchers.IO) {
        val noticeId = database.incomeDao().insert(refundNotice())
        val restored = restoredRefundDeposit()
        val restoredId = database.incomeDao().insert(restored)

        writer.write(emptyList(), listOf(refundDeposit()),
            requireNotNull(classificationState.captureRegistrationEpoch()))

        val stored = database.incomeDao().getAllIncomesOnce().single()
        assertEquals(restoredId, stored.id)
        assertEquals(restored.memo, stored.memo)
        assertEquals(restored.createdAt, stored.createdAt)
        assertEquals(null, database.incomeDao().getIncomeById(noticeId))
    }

    @Test
    fun writerRefundReplacementWithinBatchStillCreatesOnlyOneIncome() = runBlocking(Dispatchers.IO) {
        val notice = refundNotice()
        val noticeInput = SmsInput("refund-notice", requireNotNull(notice.originalSms), notice.senderAddress, baseTime)

        val result = writer.write(emptyList(), listOf(noticeInput, refundDeposit()),
            requireNotNull(classificationState.captureRegistrationEpoch()))

        val stored = database.incomeDao().getAllIncomesOnce().single()
        assertEquals("refund-deposit", stored.smsId)
        assertEquals(1, result.incomes.newCount)
    }

    @Test
    fun writerLaterNoticeCannotOverwriteRefundDepositAtRetainedId() = runBlocking(Dispatchers.IO) {
        val notice = refundNotice()
        val originalId = database.incomeDao().insert(notice)
        val noticeInput = SmsInput("refund-notice", requireNotNull(notice.originalSms), notice.senderAddress, baseTime)

        writer.write(emptyList(), listOf(refundDeposit(), noticeInput),
            requireNotNull(classificationState.captureRegistrationEpoch()))

        val stored = database.incomeDao().getAllIncomesOnce().single()
        assertEquals(originalId, stored.id)
        assertEquals("refund-deposit", stored.smsId)
        assertEquals(refundDeposit().body, stored.originalSms)
    }

    @Test
    fun instantRefundReplacementPreservesNotificationTargetAndMetadata() = runBlocking(Dispatchers.IO) {
        val original = refundNotice()
        val originalId = database.incomeDao().insert(original)
        val input = refundDeposit()

        val result = instantProcessor().processAndSave(input.address, input.body, input.date, showUserNotification = false)

        assertTrue(result is SmsInstantProcessor.Result.Income)
        val stored = database.incomeDao().getAllIncomesOnce().single()
        assertEquals(originalId, stored.id)
        assertEquals(originalId, (result as SmsInstantProcessor.Result.Income).entity.id)
        assertEquals(original.memo, stored.memo)
        assertEquals(original.createdAt, stored.createdAt)
        assertEquals(original.recurringDay, stored.recurringDay)
        assertEquals(input.body, stored.originalSms)
        SmsInstantProcessor.clearPendingReconciliationIds(listOf(requireNotNull(stored.smsId)))
    }

    @Test
    fun writerRefundReplacementDeletionBlocksNoticeAndDepositAfterReload() = runBlocking(Dispatchers.IO) {
        val notice = refundNotice()
        database.incomeDao().insert(notice)
        val noticeInput = SmsInput(requireNotNull(notice.smsId), requireNotNull(notice.originalSms), notice.senderAddress, baseTime)
        writer.write(emptyList(), listOf(refundDeposit()), requireNotNull(classificationState.captureRegistrationEpoch()))
        val stored = database.incomeDao().getAllIncomesOnce().single()
        database.incomeDao().deleteById(stored.id)
        DeletedSmsTracker.markDeleted(requireNotNull(stored.smsId))
        DeletedSmsTracker.init(trackerContext)
        writer.write(emptyList(), listOf(noticeInput, refundDeposit()), requireNotNull(classificationState.captureRegistrationEpoch()))
        assertEquals(0, database.incomeDao().getIncomeCount())
        assertTrue(DeletedSmsTracker.isDeleted(requireNotNull(notice.smsId)))
    }

    @Test
    fun writerSkippedRefundNoticeIsAlsoBlockedAfterDepositDeletion() = runBlocking(Dispatchers.IO) {
        writer.write(emptyList(), listOf(refundDeposit()), requireNotNull(classificationState.captureRegistrationEpoch()))
        val notice = refundNotice()
        val noticeInput = SmsInput(requireNotNull(notice.smsId), requireNotNull(notice.originalSms), notice.senderAddress, baseTime)
        writer.write(emptyList(), listOf(noticeInput), requireNotNull(classificationState.captureRegistrationEpoch()))
        val stored = database.incomeDao().getAllIncomesOnce().single()
        assertEquals(refundDeposit().id, stored.smsId)
        database.incomeDao().deleteById(stored.id)
        DeletedSmsTracker.markDeleted(requireNotNull(stored.smsId))
        writer.write(emptyList(), listOf(noticeInput), requireNotNull(classificationState.captureRegistrationEpoch()))
        assertEquals(0, database.incomeDao().getIncomeCount())
        assertTrue(DeletedSmsTracker.isDeleted(requireNotNull(notice.smsId)))
    }

    @Test
    fun reprocessingOneOfTwoExistingRefundsDoesNotLinkTheirDeletion() = runBlocking(Dispatchers.IO) {
        val first = refundNotice().copy(smsId = "first-refund")
        val second = refundNotice().copy(smsId = "second-refund", dateTime = baseTime + 1_000L)
        val firstId = database.incomeDao().insert(first)
        val secondId = database.incomeDao().insert(second)
        val input = SmsInput(requireNotNull(first.smsId), requireNotNull(first.originalSms), first.senderAddress, baseTime)
        writer.write(emptyList(), listOf(input), requireNotNull(classificationState.captureRegistrationEpoch()))
        assertEquals(2, database.incomeDao().getIncomeCount())
        database.incomeDao().deleteById(firstId)
        DeletedSmsTracker.markDeleted(requireNotNull(first.smsId))
        DeletedSmsTracker.init(trackerContext)
        assertFalse(DeletedSmsTracker.isDeleted(requireNotNull(second.smsId)))
        assertEquals(secondId, database.incomeDao().getAllIncomesOnce().single().id)
    }

    @Test
    fun instantRestoredRefundDoesNotLinkAnotherPreservedRefund() = runBlocking(Dispatchers.IO) {
        val restored = refundNotice().copy(smsId = null)
        val other = refundNotice().copy(smsId = "other-refund", dateTime = baseTime + 1_000L)
        val restoredId = database.incomeDao().insert(restored)
        val otherId = database.incomeDao().insert(other)
        val body = requireNotNull(restored.originalSms)
        val result = instantProcessor().processAndSave(restored.senderAddress, body, baseTime, showUserNotification = false)
        assertTrue(result is SmsInstantProcessor.Result.Income)
        assertEquals(2, database.incomeDao().getIncomeCount())
        val saved = requireNotNull(database.incomeDao().getIncomeById(restoredId))
        database.incomeDao().deleteById(restoredId)
        DeletedSmsTracker.markDeleted(requireNotNull(saved.smsId))
        assertFalse(DeletedSmsTracker.isDeleted(requireNotNull(other.smsId)))
        assertEquals(otherId, database.incomeDao().getAllIncomesOnce().single().id)
        SmsInstantProcessor.clearPendingReconciliationIds(listOf(requireNotNull(saved.smsId)))
    }

    @Test
    fun instantIncomePausedBeforeFinalWriteCannotRestoreDeletedRefund() = runBlocking(Dispatchers.IO) {
        val original = refundNotice()
        val originalId = database.incomeDao().insert(original)
        val atFinalWrite = CompletableDeferred<Unit>()
        val resumeWrite = CompletableDeferred<Unit>()
        val actualDao = database.incomeDao()
        val controlledDao = object : IncomeDao by actualDao {
            override suspend fun insertIngested(income: IncomeEntity): Long? {
                atFinalWrite.complete(Unit)
                resumeWrite.await()
                return actualDao.insertIngested(income)
            }
        }
        val processor = instantProcessor(controlledDao)
        val input = refundDeposit()
        val pending = async {
            processor.processAndSave(input.address, input.body, input.date, showUserNotification = false)
        }
        try {
            withTimeout(5_000) { atFinalWrite.await() }
            val categories = CustomCategoryRepository(database.customCategoryDao())
            val service = TransactionQuickActionService(database, repository, IncomeRepository(actualDao),
                CategoryProvider(categories), categories, DataRefreshEvent())
            assertTrue(service.delete(TransactionTarget.Income(originalId)))
            resumeWrite.complete(Unit)
            assertEquals(SmsInstantProcessor.Result.Skipped, withTimeout(5_000) { pending.await() })
            assertEquals(0, database.incomeDao().getIncomeCount())
        } finally {
            resumeWrite.complete(Unit)
            pending.cancel()
        }
    }

    @Test
    fun instantRefundDeletionBlocksBothProviderSources() = runBlocking(Dispatchers.IO) {
        val notice = refundNotice()
        val noticeBody = requireNotNull(notice.originalSms)
        val noticeId = "${notice.senderAddress}_${baseTime}_${noticeBody.hashCode()}"
        database.incomeDao().insert(notice.copy(smsId = noticeId))
        val processor = instantProcessor()
        val deposit = refundDeposit()
        processor.processAndSave(deposit.address, deposit.body, deposit.date, showUserNotification = false)
        val stored = database.incomeDao().getAllIncomesOnce().single()
        database.incomeDao().deleteById(stored.id)
        DeletedSmsTracker.markDeleted(requireNotNull(stored.smsId))
        DeletedSmsTracker.init(trackerContext)
        assertEquals(SmsInstantProcessor.Result.Skipped,
            processor.processAndSave(notice.senderAddress, noticeBody, baseTime, showUserNotification = false))
        assertEquals(SmsInstantProcessor.Result.Skipped,
            processor.processAndSave(deposit.address, deposit.body, deposit.date, showUserNotification = false))
        assertEquals(0, database.incomeDao().getIncomeCount())
        SmsInstantProcessor.clearPendingReconciliationIds(listOf(requireNotNull(stored.smsId)))
    }

    @Test
    fun instantRestoredRefundReplacementNeverDeletesRetainedRow() = runBlocking(Dispatchers.IO) {
        // 복원 행 자체가 semantic duplicate로도 선택되는 경우 기존 코드는 갱신 직후 같은 ID를 삭제했다.
        val restored = refundNotice().copy(smsId = null)
        val restoredId = database.incomeDao().insert(restored)
        val input = SmsInput("restored-notice", requireNotNull(restored.originalSms), restored.senderAddress, baseTime)

        val result = instantProcessor().processAndSave(input.address, input.body, input.date, showUserNotification = false)

        assertTrue(result is SmsInstantProcessor.Result.Income)
        val stored = database.incomeDao().getAllIncomesOnce().single()
        assertEquals(restoredId, stored.id)
        assertEquals(restored.memo, stored.memo)
        assertEquals(restored.createdAt, stored.createdAt)
        SmsInstantProcessor.clearPendingReconciliationIds(listOf(requireNotNull(stored.smsId)))
    }

    @Test
    fun instantRestoredDepositWinsOverDistinctRefundNotice() = runBlocking(Dispatchers.IO) {
        val noticeId = database.incomeDao().insert(refundNotice())
        val restored = restoredRefundDeposit()
        val restoredId = database.incomeDao().insert(restored)
        val input = refundDeposit()

        val result = instantProcessor().processAndSave(input.address, input.body, input.date, showUserNotification = false)

        assertTrue(result is SmsInstantProcessor.Result.Income)
        val stored = database.incomeDao().getAllIncomesOnce().single()
        assertEquals(restoredId, stored.id)
        assertEquals(restored.memo, stored.memo)
        assertEquals(null, database.incomeDao().getIncomeById(noticeId))
        SmsInstantProcessor.clearPendingReconciliationIds(listOf(requireNotNull(stored.smsId)))
    }

    @Test
    fun failedExpenseReplacementRestoresOriginalIncome() = runBlocking(Dispatchers.IO) {
        val sms = expense("reclassified")
        val original = IncomeEntity(
            smsId = sms.smsId, amount = sms.amount, type = "입금",
            description = "파싱 보정 대상", isRecurring = false,
            dateTime = sms.dateTime, originalSms = sms.originalSms, memo = "보존 메모"
        )
        database.incomeDao().insert(original)
        val stored = database.incomeDao().getAllIncomesOnce().single()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_expense BEFORE INSERT ON expenses " +
                "BEGIN SELECT RAISE(ABORT, 'test write failure'); END"
        )

        assertWriteFails {
            writer.write(listOf(parsedExpense(sms)), emptyList(),
                requireNotNull(classificationState.captureRegistrationEpoch()))
        }

        assertEquals(stored, database.incomeDao().getAllIncomesOnce().single())
        assertEquals(0, repository.getExpenseCount())
    }

    @Test
    fun failedIncomeReplacementRestoresOriginalExpense() = runBlocking(Dispatchers.IO) {
        val original = expense("reclassified").copy(memo = "보존 메모")
        repository.insertIngested(original)
        val stored = repository.getAllExpensesOnce().single()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_income BEFORE INSERT ON incomes " +
                "BEGIN SELECT RAISE(ABORT, 'test write failure'); END"
        )
        val cancellation = SmsInput(
            original.smsId, "12,000원 결제 취소 우리카드 | 테스트상점(일시불)",
            original.senderAddress, baseTime
        )

        assertWriteFails {
            writer.write(emptyList(), listOf(cancellation),
                requireNotNull(classificationState.captureRegistrationEpoch()))
        }

        assertEquals(stored, repository.getAllExpensesOnce().single())
        assertEquals(0, database.incomeDao().getIncomeCount())
    }

    private suspend fun assertWriteFails(operation: suspend () -> Unit) {
        var failed = false
        try {
            operation()
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            failed = true
        }
        assertTrue("Expected injected SQLite write failure", failed)
    }

    private suspend fun deleteTransaction(target: TransactionTarget) {
        val categories = CustomCategoryRepository(database.customCategoryDao())
        val service = TransactionQuickActionService(database, repository, IncomeRepository(database.incomeDao()),
            CategoryProvider(categories), categories, DataRefreshEvent())
        assertTrue(service.delete(target))
    }

    private fun refundDeposit() = SmsInput(
        "refund-deposit",
        "[Web발신]\n[우리은행]\n입금 12,000원\n테스트상점 → 입출금통장(1234)\n잔액 50,000원",
        "15889955", baseTime
    )

    private fun refundNotice() = IncomeEntity(
        smsId = "refund-notice", amount = 12_000, type = "환불", source = "테스트상점",
        description = "테스트상점 환불", isRecurring = false, recurringDay = 15,
        dateTime = baseTime, originalSms = "우리카드 12,000원 결제 취소 테스트상점",
        senderAddress = "15889955", memo = "환불 확인 메모", createdAt = baseTime - 100_000
    )

    private fun restoredRefundDeposit() = refundNotice().copy(
        smsId = null, type = "입금", description = "테스트상점에서 입금",
        originalSms = refundDeposit().body, memo = "복원한 사용자 메모", createdAt = baseTime - 200_000
    )

    private fun instantProcessor(incomeDao: IncomeDao = database.incomeDao()): SmsInstantProcessor {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = SettingsDataStore(context)
        val matcher = SmsRegexRuleMatcher(
            SmsRegexRuleRepository(database.smsRegexRuleDao()), database.smsPatternDao(),
            SmsTemplateEngine(SmsEmbeddingService()),
            SmsOriginSampleCollector(null, PremiumManager(null, settings, Gson())),
            SmsChannelProbeCollector(database.smsChannelProbeLogDao())
        )
        return SmsInstantProcessor(
            SmsPreFilter(), SmsIncomeFilter(), matcher, repository,
            IncomeRepository(incomeDao), StoreRuleRepository(database.storeRuleDao()),
            SmsExclusionRepository(database.smsExclusionKeywordDao()), OwnedCardRepository(database.ownedCardDao()),
            SmsNotificationManager(context), settings
        )
    }

    private suspend fun <T> concurrently(first: suspend () -> T, second: suspend () -> T): List<T> =
        coroutineScope {
            val start = CompletableDeferred<Unit>()
            val jobs = listOf(first, second).map { operation ->
                async(Dispatchers.IO) {
                    start.await()
                    operation()
                }
            }
            start.complete(Unit)
            jobs.awaitAll()
        }

    private fun smsExpense(
        receivedAt: Long,
        transactionAt: Long = baseTime,
        body: String = "우리카드 1234 승인\n테스트상점\n12,000원\n잔액100,000원"
    ): ExpenseEntity {
        val sms = expense("unused", time = transactionAt).copy(originalSms = body)
        return sms.copy(smsId = "${sms.senderAddress}_${receivedAt}_${body.hashCode()}")
    }

    private fun sameMinutePaymentWithoutBalance(receivedAt: Long): ExpenseEntity {
        val body = "우리카드 1234 승인\n테스트상점\n12,000원\n${DateUtils.formatDateTime(baseTime).substring(5)}"
        val transactionAt = DateUtils.parseDateTime(
            SmsTransactionDateResolver.extractDateTime(body, receivedAt)
        )
        return smsExpense(receivedAt, transactionAt, body)
    }

    private fun expense(
        smsId: String,
        app: Boolean = false,
        time: Long = baseTime
    ): ExpenseEntity = ExpenseEntity(
        amount = 12_000,
        storeName = "테스트상점",
        category = "기타",
        // SmsInstantProcessor가 DB에 저장하는 정규화된 카드사명과 동일하게 구성한다.
        cardName = "우리",
        dateTime = time,
        originalSms = "우리카드 1234 승인\n테스트상점\n12,000원",
        smsId = smsId,
        senderAddress = if (app) "app:com.wooricard.smartapp" else "15889955"
    )

    private fun parsedExpense(expense: ExpenseEntity, receivedAt: Long = expense.dateTime) =
        SmsParseResult(
            input = SmsInput(expense.smsId, expense.originalSms, expense.senderAddress, receivedAt),
            analysis = SmsAnalysisResult(
                expense.amount,
                expense.storeName,
                "식비",
                DateUtils.formatDateTime(expense.dateTime),
                expense.cardName
            ),
            tier = 2,
            confidence = 1f
        )

    private class LocalCategoryClassifier : CategoryClassifierService {
        override suspend fun initCategoryCache() = Unit
        override fun clearCategoryCache() = Unit
        override suspend fun flushPendingMappings() = Unit
        override suspend fun getCategory(storeName: String, originalSms: String) = "식비"
        override suspend fun classifyStoreNamesInMemory(
            storeNames: List<String>,
            onStepProgress: (suspend (String, Int, Int) -> Unit)?
        ) = emptyMap<String, String>()
        override suspend fun classifyUnclassifiedExpenses(
            onStepProgress: (suspend (String, Int, Int) -> Unit)?, maxStoreCount: Int?
        ) = 0
        override suspend fun updateExpenseCategory(expenseId: Long, storeName: String, newCategory: String) = Unit
        override suspend fun updateCategoryForAllSameStore(storeName: String, newCategory: String) = Unit
        override suspend fun hasGeminiApiKey() = false
        override suspend fun canAttemptGeminiClassification() = false
        override suspend fun reclassifyLowConfidenceItems(confidenceThreshold: Float) = 0
        override suspend fun getUnclassifiedCount() = 0
        override suspend fun getVectorCacheCount() = 0
        override suspend fun classifyUnclassifiedIncomes(onStepProgress: (suspend (String, Int, Int) -> Unit)?) = 0
        override suspend fun getUnclassifiedIncomeCount() = 0
        override suspend fun classifyAllUntilComplete(
            onProgress: suspend (Int, Int, Int) -> Unit,
            onStepProgress: (suspend (String, Int, Int) -> Unit)?,
            maxRounds: Int
        ) = 0
    }
}
