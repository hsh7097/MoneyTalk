package com.sanha.moneytalk.core.ui

import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱 전역 카테고리 분류 상태 관리
 *
 * SMS 동기화/카드 정규화, 백그라운드 자동 분류, 홈·설정 수동 분류 중 하나만
 * 실행되도록 조정하고 데이터 삭제 전에는 진행 중인 작업의 실제 종료까지 기다립니다.
 *
 * Hilt @Singleton으로 제공되어 모든 ViewModel에서 동일 인스턴스를 공유합니다.
 */
@Singleton
class ClassificationState @Inject constructor() {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    private val lock = Any()
    private val pauseMutex = Mutex()
    private var registrationsPaused = false
    private var registrationEpoch = 0L

    /** 현재 실행 중인 분류 Job 집합 (외부에서 취소 가능) */
    private val activeJobs = mutableSetOf<Job>()

    /** gate 시작 전 요청만 식별할 수 있는 epoch. gate 안에서는 새 요청을 만들지 않는다. */
    fun captureRegistrationEpoch(): Long? = synchronized(lock) {
        if (registrationsPaused) null else registrationEpoch
    }

    fun isRegistrationEpochCurrent(epoch: Long): Boolean = synchronized(lock) {
        !registrationsPaused && registrationEpoch == epoch
    }

    /** 다른 분류 작업이 없을 때만 Job을 등록한다. */
    fun tryRegisterJob(job: Job, expectedEpoch: Long? = null): Boolean {
        val registered = synchronized(lock) {
            activeJobs.removeAll { it.isCompleted }
            _isRunning.value = activeJobs.isNotEmpty()
            if (registrationsPaused ||
                (expectedEpoch != null && registrationEpoch != expectedEpoch) ||
                job.isCompleted ||
                activeJobs.isNotEmpty()
            ) {
                false
            } else {
                activeJobs.add(job)
                _isRunning.value = true
                true
            }
        }
        if (registered) observeCompletion(job)
        return registered
    }

    private fun observeCompletion(job: Job) {
        job.invokeOnCompletion { completeJob(job) }
    }

    /** 지정한 Job을 활성 집합에서 제거하고 상태를 갱신 */
    private fun completeJob(job: Job) {
        synchronized(lock) {
            activeJobs.remove(job)
            activeJobs.removeAll { it.isCompleted }
            _isRunning.value = activeJobs.isNotEmpty()
        }
    }

    /** 진행 중인 분류 작업을 취소하고 실제 종료까지 기다린다. */
    suspend fun cancelIfRunning(expectedEpoch: Long? = null): Boolean {
        val jobsToCancel = synchronized(lock) {
            if (expectedEpoch != null &&
                (registrationsPaused || registrationEpoch != expectedEpoch)
            ) {
                null
            } else {
                activeJobs.removeAll { it.isCompleted }
                _isRunning.value = activeJobs.isNotEmpty()
                activeJobs.toList()
            }
        } ?: return false
        jobsToCancel.forEach { job -> job.cancel() }
        jobsToCancel.joinAll()
        synchronized(lock) {
            activeJobs.removeAll { it.isCompleted }
            _isRunning.value = activeJobs.isNotEmpty()
        }
        return true
    }

    /** 기존 분류를 완전히 종료한 뒤 지정한 작업이 전역 소유권을 얻도록 한다. */
    suspend fun replaceWith(job: Job, expectedEpoch: Long? = null): Boolean {
        val alreadyRegistered = synchronized(lock) {
            if (registrationsPaused ||
                (expectedEpoch != null && registrationEpoch != expectedEpoch)
            ) {
                false
            } else {
                job in activeJobs
            }
        }
        if (alreadyRegistered) return true
        while (job.isActive) {
            if (!cancelIfRunning(expectedEpoch)) return false
            if (tryRegisterJob(job, expectedEpoch)) return true
            if (synchronized(lock) {
                    registrationsPaused ||
                        (expectedEpoch != null && registrationEpoch != expectedEpoch)
                }
            ) return false
        }
        return false
    }

    /** 데이터 초기화처럼 새 작업까지 막아야 하는 구간을 원자적으로 보호한다. */
    suspend fun <T> withRegistrationsPaused(block: suspend () -> T): T {
        pauseMutex.lock()
        return try {
            val jobsToCancel = synchronized(lock) {
                registrationsPaused = true
                registrationEpoch++
                activeJobs.removeAll { it.isCompleted }
                _isRunning.value = activeJobs.isNotEmpty()
                activeJobs.toList()
            }
            try {
                jobsToCancel.forEach { it.cancel() }
                withContext(NonCancellable) { jobsToCancel.joinAll() }
                currentCoroutineContext().ensureActive()
                block()
            } finally {
                synchronized(lock) {
                    activeJobs.removeAll { it.isCompleted }
                    registrationsPaused = false
                    _isRunning.value = activeJobs.isNotEmpty()
                }
            }
        } finally {
            pauseMutex.unlock()
        }
    }
}
