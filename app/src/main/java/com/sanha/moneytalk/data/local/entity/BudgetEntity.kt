package com.sanha.moneytalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    val category: String,         // 카테고리
    val monthlyLimit: Int,        // 월 한도
    val yearMonth: String         // "2024-02" 형식으로 월별 관리
)
