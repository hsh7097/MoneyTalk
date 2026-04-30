package com.sanha.moneytalk.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 거래처 규칙 엔티티
 *
 * 거래처명에 대한 카테고리/고정지출/통계 제외 규칙을 저장합니다.
 * SMS 수신 시 거래처명에 keyword가 포함(contains)되면 해당 규칙이 자동 적용됩니다.
 *
 * 매칭 방식: storeName.lowercase().contains(keyword.lowercase())
 *
 * 적용 우선순위: StoreRule > Room 매핑 > Vector > Keyword > Gemini (Tier 0)
 *
 * @see com.sanha.moneytalk.core.database.dao.StoreRuleDao
 */
@Entity(
    tableName = "store_rules",
    indices = [Index(value = ["keyword"], unique = true)]
)
data class StoreRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 매칭 키워드 (거래처명에 포함 여부로 판단) */
    val keyword: String,

    /** 카테고리 (null이면 카테고리 규칙 없음) */
    val category: String? = null,

    /** 고정지출 여부 (null이면 고정지출 규칙 없음) */
    @ColumnInfo(name = "is_fixed")
    val isFixed: Boolean? = null,

    /** 통계 제외 여부 (null이면 통계 제외 규칙 없음) */
    @ColumnInfo(name = "is_excluded_from_stats")
    val isExcludedFromStats: Boolean? = null,

    /** 레코드 생성 시간 */
    val createdAt: Long = System.currentTimeMillis()
)
