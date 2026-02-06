package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 채팅 세션 엔티티
 *
 * AI 재무 상담 채팅의 세션(대화방) 단위를 관리합니다.
 * 각 세션은 독립적인 대화 맥락을 유지합니다.
 *
 * Rolling Summary 전략:
 * - currentSummary: 과거 대화 내용의 누적 요약본
 * - 대화가 길어질수록 오래된 메시지를 요약으로 압축
 * - AI에게 [요약 + 최근 N개 메시지]를 전달하여 컨텍스트 유지
 *
 * @see ChatEntity
 * @see com.sanha.moneytalk.core.database.dao.ChatDao
 */
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 세션 제목 (자동 생성 또는 사용자 지정) */
    val title: String = "새 대화",

    /**
     * Rolling Summary (과거 대화 누적 요약본)
     *
     * 대화 윈도우 밖으로 밀려난 메시지들을 Gemini가 요약하여 저장.
     * 새 메시지가 추가될 때마다 기존 요약 + 밀려난 메시지를 통합 요약.
     * null이면 아직 요약이 생성되지 않은 새 세션.
     */
    val currentSummary: String? = null,

    /** 세션 생성 시간 */
    val createdAt: Long = System.currentTimeMillis(),

    /** 세션 최종 업데이트 시간 (정렬 기준) */
    val updatedAt: Long = System.currentTimeMillis()
)
