package com.sanha.moneytalk.core.database.dao

import androidx.room.*
import com.sanha.moneytalk.core.database.entity.ChatEntity
import com.sanha.moneytalk.core.database.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

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

    // 레거시 호환 (전체 조회)
    @Query("SELECT * FROM chat_history ORDER BY timestamp ASC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chat_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentChats(limit: Int): List<ChatEntity>

    @Query("DELETE FROM chat_history")
    suspend fun deleteAll()
}
