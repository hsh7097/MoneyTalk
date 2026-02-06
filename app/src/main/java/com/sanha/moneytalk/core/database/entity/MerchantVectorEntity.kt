package com.sanha.moneytalk.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 가맹점 벡터 DB 엔티티
 * 가맹점명과 카테고리, 해당 명칭의 벡터값을 저장하여
 * 텍스트가 100% 일치하지 않아도 의미적 유사도를 통해 카테고리 자동 할당
 */
@Entity(tableName = "merchant_vectors")
data class MerchantVectorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val merchantName: String,          // 가맹점 이름 (정규화된 메인 이름)

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val vector: FloatArray,            // 가맹점명의 임베딩 벡터

    val category: String,              // 매핑된 카테고리

    val aliases: String = "",          // 알려진 별칭들 (콤마 구분)
    val matchCount: Int = 1,           // 이 가맹점에 매칭된 횟수
    val lastMatchedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MerchantVectorEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
