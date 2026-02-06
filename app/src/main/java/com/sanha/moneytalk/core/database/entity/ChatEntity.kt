package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 채팅 메시지 엔티티
 *
 * AI 재무 상담 채팅의 개별 메시지를 저장합니다.
 * ChatSessionEntity와 1:N 관계 (세션 삭제 시 메시지도 CASCADE 삭제)
 *
 * Rolling Summary 전략에서의 역할:
 * - 최근 N개 메시지만 전체 텍스트로 AI에 전달
 * - 오래된 메시지는 요약본(ChatSessionEntity.currentSummary)으로 대체
 *
 * @see ChatSessionEntity
 * @see com.sanha.moneytalk.core.database.dao.ChatDao
 */
@Entity(
    tableName = "chat_history",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class ChatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 소속 세션 ID (외래 키 → chat_sessions.id) */
    val sessionId: Long,

    /** 메시지 내용 (사용자 질문 또는 AI 응답) */
    val message: String,

    /** 발신자 구분 (true: 사용자, false: AI) */
    val isUser: Boolean,

    /** 메시지 전송 시간 */
    val timestamp: Long = System.currentTimeMillis()
)
