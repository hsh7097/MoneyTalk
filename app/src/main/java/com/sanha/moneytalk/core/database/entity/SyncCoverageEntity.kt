package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 실제 SMS 동기화가 성공적으로 수행된 시간 구간.
 *
 * 화면의 "데이터 가져오기 필요" 여부는 월 라벨이 아니라
 * 이 구간들의 합집합이 현재 보고 있는 기간을 얼마나 덮는지로 판단한다.
 */
@Entity(
    tableName = "sync_coverage",
    indices = [
        Index(value = ["startMillis"]),
        Index(value = ["endMillis"]),
        Index(value = ["syncedAt"]),
        Index(value = ["trigger"])
    ]
)
data class SyncCoverageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val startMillis: Long,
    val endMillis: Long,
    val trigger: String,
    val expenseCount: Int,
    val incomeCount: Int,
    val reconciledExpenseCount: Int = 0,
    val reconciledIncomeCount: Int = 0,
    val syncedAt: Long = System.currentTimeMillis()
)
