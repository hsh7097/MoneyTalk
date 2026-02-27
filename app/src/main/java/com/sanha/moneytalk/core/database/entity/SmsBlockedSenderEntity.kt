package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SMS 수신거부 발신번호 엔티티
 *
 * address는 정규화된 번호(비교용)이며 PK로 중복을 방지합니다.
 * rawAddress는 사용자가 입력한 원본 값(표시용)입니다.
 */
@Entity(tableName = "sms_blocked_senders")
data class SmsBlockedSenderEntity(
    @PrimaryKey
    val address: String,
    val rawAddress: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
