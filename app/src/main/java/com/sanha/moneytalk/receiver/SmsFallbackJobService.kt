package com.sanha.moneytalk.receiver

import android.app.job.JobParameters
import android.app.job.JobService
import com.sanha.moneytalk.core.sms.SmsFallbackProcessor
import com.sanha.moneytalk.core.sms.SmsFallbackScheduler
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SmsFallbackJobService : JobService() {
    @Inject lateinit var processor: SmsFallbackProcessor
    @Inject lateinit var scheduler: SmsFallbackScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var activeParameters: JobParameters? = null
    private var runGeneration = 0L

    override fun onStartJob(params: JobParameters): Boolean {
        val generation = ++runGeneration
        activeParameters = params
        scheduler.onJobStarted()
        activeJob = scope.launch {
            try {
                processor.processPending()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MoneyTalkLogger.w("[SmsFallback] 작업 실패: type=${e.javaClass.simpleName}")
            } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    if (runGeneration == generation && activeParameters != null) {
                        scheduler.finishJob { retry -> jobFinished(params, retry) }
                        activeParameters = null
                        activeJob = null
                    }
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        // Binder may deliver a different JobParameters instance for stop.
        if (activeParameters?.jobId != params.jobId) return false
        runGeneration++
        activeParameters = null
        activeJob?.cancel()
        activeJob = null
        return scheduler.onJobStopped()
    }

    override fun onDestroy() {
        runGeneration++
        activeParameters = null
        scope.cancel()
        scheduler.onJobStopped()
        super.onDestroy()
    }
}
