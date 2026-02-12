package com.sanha.moneytalk.core.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱 전역 데이터 새로고침 이벤트 관리자
 *
 * 설정 화면에서 전체 데이터 삭제 등의 이벤트가 발생했을 때,
 * 다른 ViewModel(HomeViewModel 등)이 데이터를 새로고침할 수 있도록
 * 공유 이벤트를 발행합니다.
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
        OWNED_CARD_UPDATED
    }
}
