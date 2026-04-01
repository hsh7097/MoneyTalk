package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SMS/MMS/RCS/실시간 수신 경로 진단 로그.
 *
 * 특정 메시지가 어느 채널에서 관측되었는지 이벤트 단위로 저장한다.
 */
@Entity(
    tableName = "sms_channel_probe_logs",
    indices = [
        Index(value = ["normalizedSenderAddress"]),
        Index(value = ["createdAt"]),
        Index(value = ["channel", "stage"])
    ]
)
data class SmsChannelProbeLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val normalizedSenderAddress: String,
    val channel: String,
    val stage: String,
    val originBody: String,
    val maskedBody: String,
    val normalizedBody: String,
    val messageTimestamp: Long,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
