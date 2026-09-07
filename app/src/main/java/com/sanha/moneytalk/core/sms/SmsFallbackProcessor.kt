package com.sanha.moneytalk.core.sms

import com.sanha.moneytalk.core.database.OwnedCardRepository
import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.ui.ClassificationState
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import com.sanha.moneytalk.feature.home.data.CategoryClassifierService
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

/** One bounded pass over financial SMS missed by the local receiver parser. */
class SmsFallbackProcessor @Inject constructor(
    private val queue: SmsFallbackQueue,
    private val coordinator: SmsSyncCoordinator,
    private val writer: SmsIngestionWriter,
    private val classificationState: ClassificationState,
    private val smsExclusionRepository: SmsExclusionRepository,
    private val categoryClassifierService: CategoryClassifierService,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val ownedCardRepository: OwnedCardRepository,
    private val dataRefreshEvent: DataRefreshEvent
) {
    suspend fun processPending() {
        val epoch = classificationState.captureRegistrationEpoch() ?: return
        val job = currentCoroutineContext()[Job] ?: return
        if (!classificationState.tryRegisterJob(job, epoch)) return

        try {
            val keywords = smsExclusionRepository.getUserKeywords()
            coordinator.setUserExcludeKeywords(keywords)
            SmsIncomeParser.setUserExcludeKeywords(keywords)
            categoryClassifierService.initCategoryCache()

            // Each candidate gets at most one attempt per OS job run.
            for (pending in queue.pending()) {
                currentCoroutineContext().ensureActive()
                if (!classificationState.isRegistrationEpochCurrent(epoch)) return
                if (DeletedSmsTracker.isDeleted(pending.id) || alreadyStored(pending.id)) {
                    queue.complete(pending.id, pending.generation)
                    continue
                }
                val entry = queue.beginAttempt(pending.id, pending.generation) ?: continue
                try {
                    val result = coordinator.process(listOf(entry.toInput()))
                    currentCoroutineContext().ensureActive()
                    if (DeletedSmsTracker.isDeleted(entry.id)) {
                        queue.complete(entry.id, entry.generation)
                        continue
                    }
                    if (result.stats.skipped == 1 && result.expenses.isEmpty() && result.incomes.isEmpty()) {
                        queue.complete(entry.id, entry.generation)
                        continue
                    }
                    val saved = writer.write(result.expenses, result.incomes, epoch)
                    currentCoroutineContext().ensureActive()
                    if (!classificationState.isRegistrationEpochCurrent(epoch)) return
                    val savedCount = saved.expenses.newCount + saved.expenses.reconciledCount +
                        saved.incomes.newCount + saved.incomes.reconciledCount
                    if (entry.id in saved.handledSmsIds || alreadyStored(entry.id)) {
                        currentCoroutineContext().ensureActive()
                        if (!classificationState.isRegistrationEpochCurrent(epoch)) return
                        if (savedCount > 0) {
                            ownedCardRepository.registerCardsFromSync(result.expenses.map { it.analysis.cardName })
                        }
                        currentCoroutineContext().ensureActive()
                        if (!classificationState.isRegistrationEpochCurrent(epoch)) return
                        queue.complete(entry.id, entry.generation)
                        dataRefreshEvent.emitSuspend(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
                        MoneyTalkLogger.i(
                            "[SmsFallback] 처리 완료: attempt=${entry.attempts}, " +
                                "pendingMs=${System.currentTimeMillis() - entry.queuedAt}"
                        )
                    } else {
                        MoneyTalkLogger.w("[SmsFallback] 미확정 금융 문자: attempt=${entry.attempts}/${SmsFallbackQueue.MAX_ATTEMPTS}")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    MoneyTalkLogger.w("[SmsFallback] 처리 실패: attempt=${entry.attempts}, type=${e.javaClass.simpleName}")
                }
            }
            categoryClassifierService.flushPendingMappings()
        } finally {
            categoryClassifierService.clearCategoryCache()
        }
    }

    private suspend fun alreadyStored(smsId: String): Boolean =
        expenseRepository.existsBySmsId(smsId) || incomeRepository.existsBySmsId(smsId)
}
