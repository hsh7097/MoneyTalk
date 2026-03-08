package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 사용자 커스텀 카테고리 Room Entity.
 *
 * 기본 카테고리(Category enum)에 추가로 사용자가 직접 생성한 카테고리를 저장한다.
 * displayName + categoryType 조합으로 유니크 제약.
 */
@Entity(
    tableName = "custom_categories",
    indices = [
        Index(value = ["displayName", "categoryType"], unique = true)
    ]
)
data class CustomCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val displayName: String,
    val emoji: String,
    val categoryType: String,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
