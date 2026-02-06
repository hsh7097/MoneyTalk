package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 예산 엔티티
 *
 * 카테고리별 월간 예산 한도를 관리합니다.
 * 동일 카테고리라도 월별로 다른 예산을 설정할 수 있습니다.
 *
 * PK: category (카테고리명이 곧 식별자)
 *
 * @see com.sanha.moneytalk.core.database.dao.BudgetDao
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    /** 카테고리명 (PK, 예: "식비", "교통") */
    @PrimaryKey
    val category: String,

    /** 월 지출 한도 (원 단위) */
    val monthlyLimit: Int,

    /** 적용 연월 ("YYYY-MM" 형식, 예: "2024-02") */
    val yearMonth: String
)
