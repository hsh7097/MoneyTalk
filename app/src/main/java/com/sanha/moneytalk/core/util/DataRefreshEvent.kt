package com.sanha.moneytalk.core.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱 전역 데이터 새로고침 이벤트 관리자
 *
 * ViewModel 간 데이터 변경 통지(1:N)를 담당합니다.
 * 동기화 엔진/진행 상태는 MainViewModel이 직접 관리합니다.
 *
 * Hilt @Singleton으로 제공되어 모든 ViewModel에서 동일 인스턴스를 공유합니다.
 */
@Singleton
class DataRefreshEvent @Inject constructor() {

    private val _refreshEvent = MutableSharedFlow<RefreshType>(extraBufferCapacity = 1)
    val refreshEvent = _refreshEvent.asSharedFlow()

    /**
     * 데이터 새로고침 이벤트 발행
     */
    fun emit(type: RefreshType = RefreshType.ALL_DATA_DELETED) {
        _refreshEvent.tryEmit(type)
    }

    enum class RefreshType {
        /** 전체 데이터 삭제 (설정 초기화) */
        ALL_DATA_DELETED,

        /** 카테고리 재분류 완료 (API 키 설정 후 백그라운드 재분류) */
        CATEGORY_UPDATED,

        /** 내 카드 설정 변경 (홈/내역 필터 갱신용) */
        OWNED_CARD_UPDATED,

        /** 실시간 SMS 수신으로 지출/수입 추가 */
        TRANSACTION_ADDED,

        /** SMS 수신 감지 → 증분 동기화 트리거 */
        SMS_RECEIVED,

        /** DEBUG 전용: 기간 제한 없이 전체 메시지 동기화 트리거 */
        DEBUG_FULL_SYNC_ALL_MESSAGES
    }
}
