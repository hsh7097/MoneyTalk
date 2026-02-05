package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incomes")
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val smsId: String? = null,    // SMS에서 파싱된 경우 고유 ID (중복 방지)
    val amount: Int,              // 금액
    val type: String,             // 월급, 부수입, 용돈 등
    val source: String = "",      // 송금인/출처 (SMS에서 파싱된 경우)
    val description: String,      // 설명
    val isRecurring: Boolean,     // 고정 수입 여부
    val recurringDay: Int? = null,// 매월 입금일
    val dateTime: Long,           // 등록 시간
    val originalSms: String? = null, // 원본 SMS 메시지
    val createdAt: Long = System.currentTimeMillis()
)
