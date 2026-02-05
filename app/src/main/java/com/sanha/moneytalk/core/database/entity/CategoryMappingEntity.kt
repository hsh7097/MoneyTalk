package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 가게명 -> 카테고리 매핑 테이블
 * 가게명을 기준으로 카테고리를 저장하여 동일 가게 재방문 시 자동 분류
 */
@Entity(
    tableName = "category_mappings",
    indices = [Index(value = ["storeName"], unique = true)]
)
data class CategoryMappingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val storeName: String,          // 가게명 (정규화된 형태)
    val category: String,           // 카테고리 displayName (예: "식비", "카페")
    val source: String = "local",   // 분류 출처: "local"(로컬 키워드), "gemini"(AI), "user"(사용자 지정)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
