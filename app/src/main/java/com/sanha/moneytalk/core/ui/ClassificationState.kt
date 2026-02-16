package com.sanha.moneytalk.core.ui

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱 전역 카테고리 분류 상태 관리
 *
 * HomeViewModel의 백그라운드 분류가 진행 중일 때
 * SettingsViewModel에서 분류 버튼을 비활성화하기 위해 사용합니다.
 *
 * Hilt @Singleton으로 제공되어 모든 ViewModel에서 동일 인스턴스를 공유합니다.
 */
@Singleton
class ClassificationState @Inject constructor() {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    private val lock = Any()

    /** 현재 실행 중인 분류 Job 집합 (외부에서 취소 가능) */
    private val activeJobs = mutableSetOf<Job>()

    fun setRunning(running: Boolean) {
        synchronized(lock) {
            if (!running) {
                activeJobs.clear()
            }
            _isRunning.value = running
        }
    }

    /** 분류 Job을 등록하여 외부에서 취소 가능하게 함 */
    fun registerJob(job: Job) {
        synchronized(lock) {
            activeJobs.removeAll { it.isCompleted }
            if (!job.isCompleted) {
                activeJobs.add(job)
            }
            _isRunning.value = activeJobs.isNotEmpty()
        }
    }

    /** 지정한 Job을 활성 집합에서 제거하고 상태를 갱신 */
    fun completeJob(job: Job) {
        synchronized(lock) {
            activeJobs.remove(job)
            activeJobs.removeAll { it.isCompleted }
            _isRunning.value = activeJobs.isNotEmpty()
        }
    }

    /** 진행 중인 분류 작업을 취소하고 상태를 초기화 */
    fun cancelIfRunning() {
        val jobsToCancel = synchronized(lock) {
            val snapshot = activeJobs.toList()
            activeJobs.clear()
            _isRunning.value = false
            snapshot
        }
        jobsToCancel.forEach { job -> job.cancel() }
    }
}
