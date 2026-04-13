package com.sanha.moneytalk.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanha.moneytalk.core.database.entity.SyncCoverageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 동기화 성공 구간 DAO.
 */
@Dao
interface SyncCoverageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SyncCoverageEntity)

    @Query("SELECT * FROM sync_coverage ORDER BY startMillis ASC, endMillis ASC, syncedAt ASC")
    fun observeAll(): Flow<List<SyncCoverageEntity>>

    @Query("DELETE FROM sync_coverage")
    suspend fun deleteAll()
}
