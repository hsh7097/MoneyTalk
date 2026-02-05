package com.sanha.moneytalk.core.database.dao

import androidx.room.*
import com.sanha.moneytalk.core.database.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chat: ChatEntity): Long

    @Delete
    suspend fun delete(chat: ChatEntity)

    @Query("SELECT * FROM chat_history ORDER BY timestamp ASC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chat_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentChats(limit: Int): List<ChatEntity>

    @Query("DELETE FROM chat_history")
    suspend fun deleteAll()
}
