package com.sanha.moneytalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "incomes")
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Int,              // 금액
    val type: String,             // 월급, 부수입, 용돈 등
    val description: String,      // 설명
    val isRecurring: Boolean,     // 고정 수입 여부
    val recurringDay: Int? = null,// 매월 입금일
    val dateTime: Long,           // 등록 시간
    val createdAt: Long = System.currentTimeMillis()
)
