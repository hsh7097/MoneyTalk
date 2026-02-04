package com.sanha.moneytalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_history")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val message: String,          // 메시지 내용
    val isUser: Boolean,          // 사용자/AI 구분
    val timestamp: Long = System.currentTimeMillis()
)
