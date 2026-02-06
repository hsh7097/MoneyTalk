package com.sanha.moneytalk.core.database.dao

import androidx.room.*
import com.sanha.moneytalk.core.database.entity.ChatEntity
import com.sanha.moneytalk.core.database.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 채팅 DAO
 *
 * AI 재무 상담 채팅의 세션 및 메시지에 대한 CRUD를 제공합니다.
 *
 * 구조: ChatSessionEntity (1) → (N) ChatEntity
 * - 세션: 독립적인 대화방 (제목, Rolling Summary 포함)
 * - 메시지: 사용자/AI 개별 메시지 (세션에 종속)
 *
 * Rolling Summary 관련 쿼리:
 * - updateSessionSummary(): 누적 요약본 갱신
 * - getSessionSummary(): 기존 요약본 조회
 * - getRecentChatsBySessionAsc(): 최근 N개 메시지 조회 (시간순)
 *
 * @see ChatEntity
 * @see ChatSessionEntity
 */
@Dao
interface ChatDao {

    // ===== 세션 관련 쿼리 =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity): Long

    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Delete
    suspend fun deleteSession(session: ChatSessionEntity)

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): ChatSessionEntity?

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE chat_sessions SET updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionTimestamp(sessionId: Long, updatedAt: Long = System.currentTimeMillis())

    // ===== 채팅 메시지 관련 쿼리 =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chat: ChatEntity): Long

    @Delete
    suspend fun delete(chat: ChatEntity)

    @Query("SELECT * FROM chat_history WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getChatsBySession(sessionId: Long): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chat_history WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentChatsBySession(sessionId: Long, limit: Int): List<ChatEntity>

    @Query("SELECT COUNT(*) FROM chat_history WHERE sessionId = :sessionId")
    suspend fun getMessageCountBySession(sessionId: Long): Int

    @Query("DELETE FROM chat_history WHERE sessionId = :sessionId")
    suspend fun deleteChatsBySession(sessionId: Long)

    @Query("UPDATE chat_sessions SET currentSummary = :summary, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionSummary(sessionId: Long, summary: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT currentSummary FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSessionSummary(sessionId: Long): String?

    @Query("SELECT * FROM (SELECT * FROM chat_history WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit) sub ORDER BY timestamp ASC")
    suspend fun getRecentChatsBySessionAsc(sessionId: Long, limit: Int): List<ChatEntity>

    // 레거시 호환 (전체 조회)
    @Query("SELECT * FROM chat_history ORDER BY timestamp ASC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chat_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentChats(limit: Int): List<ChatEntity>

    @Query("DELETE FROM chat_history")
    suspend fun deleteAll()
}
