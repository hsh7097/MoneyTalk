package com.sanha.moneytalk.core.ui

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

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }
}
