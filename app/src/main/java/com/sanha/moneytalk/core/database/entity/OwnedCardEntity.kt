package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 보유 카드 엔티티
 *
 * SMS에서 자동 감지된 카드사 목록을 관리합니다.
 * 사용자가 체크한 카드만 "내 카드"로 인정하여
 * 홈 화면에서 해당 카드의 지출만 표시합니다.
 *
 * 데이터 흐름:
 * SMS 동기화 → 카드사명 추출 → 정규화 → OwnedCard 자동 등록
 * 설정 화면 → 사용자가 내 카드 체크/해제 → isOwned 업데이트
 * 홈 화면 → isOwned=true 카드의 지출만 필터링하여 표시
 */
@Entity(tableName = "owned_cards")
data class OwnedCardEntity(
    /** 정규화된 카드사명 (PK) - 예: "KB국민", "신한", "삼성" */
    @PrimaryKey
    val cardName: String,

    /** 내 카드 여부 (사용자 설정) */
    val isOwned: Boolean = true,

    /** 최초 발견 시간 */
    val firstSeenAt: Long = System.currentTimeMillis(),

    /** 마지막 발견 시간 */
    val lastSeenAt: Long = System.currentTimeMillis(),

    /** 발견 횟수 */
    val seenCount: Int = 1,

    /** 감지 소스 ("sms_sync" | "manual") */
    val source: String = "sms_sync"
)
