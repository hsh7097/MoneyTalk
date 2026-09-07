package com.sanha.moneytalk.core.sms

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import com.sanha.moneytalk.core.ui.ClassificationState
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import com.sanha.moneytalk.receiver.SmsFallbackJobService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules only persisted financial candidates; never polls the inbox periodically. */
@Singleton
class SmsFallbackScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: SmsFallbackQueue,
    private val classificationState: ClassificationState
) {
    data class RequestToken(val registrationEpoch: Long, val queueGeneration: Long)

    companion object {
        const val JOB_ID = 0x4D54534D
    }

    private var running = false
    private val jobs: JobScheduler
        get() = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    fun captureRequest(): RequestToken? {
        val epoch = classificationState.captureRegistrationEpoch() ?: return null
        return RequestToken(epoch, queue.generation())
    }

    fun enqueue(address: String, body: String, timestamp: Long, token: RequestToken?) {
        enqueue(
            SmsInput("${SmsFilter.normalizeAddress(address)}_${timestamp}_${body.hashCode()}", body, address, timestamp),
            token
        )
    }

    @Synchronized
    fun enqueue(input: SmsInput, token: RequestToken?) {
        if (token == null || !classificationState.isRegistrationEpochCurrent(token.registrationEpoch)) return
        if (DeletedSmsTracker.isDeleted(input.id)) return
        val added = queue.enqueue(input, token.queueGeneration, System.currentTimeMillis())
        if (!running && (added || jobs.getPendingJob(JOB_ID) == null) && queue.pending().isNotEmpty()) {
            schedule()
        }
    }

    @Synchronized
    fun restorePending() {
        if (!running && queue.pending().isNotEmpty() && jobs.getPendingJob(JOB_ID) == null) schedule()
    }

    @Synchronized
    fun onJobStarted() {
        running = true
    }

    @Synchronized
    fun onJobStopped(): Boolean {
        running = false
        return queue.pending().isNotEmpty()
    }

    @Synchronized
    fun finishJob(finish: (Boolean) -> Unit) {
        finish(queue.pending().isNotEmpty())
        running = false
    }

    /** Call inside ClassificationState.withRegistrationsPaused before deleting transactions. */
    @Synchronized
    fun clearPending() {
        queue.clear()
        jobs.cancel(JOB_ID)
    }

    private fun schedule() {
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, SmsFallbackJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        if (jobs.schedule(job) != JobScheduler.RESULT_SUCCESS) {
            MoneyTalkLogger.w("[SmsFallback] OS 예약 실패, 미처리 문자는 기기에 보존")
        }
    }
}
