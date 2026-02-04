package com.sanha.moneytalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Int,              // 금액
    val storeName: String,        // 가게명
    val category: String,         // 카테고리
    val cardName: String,         // 카드사
    val dateTime: Long,           // 결제 시간 (timestamp)
    val originalSms: String,      // 원본 문자
    val smsId: String,            // 문자 고유 ID (중복 방지)
    val memo: String? = null,     // 메모
    val createdAt: Long = System.currentTimeMillis()
)
