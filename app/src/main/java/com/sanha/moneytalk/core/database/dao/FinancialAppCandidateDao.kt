package com.sanha.moneytalk.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sanha.moneytalk.core.database.entity.FinancialAppCandidateEntity

@Dao
interface FinancialAppCandidateDao {

    @Query("SELECT * FROM financial_app_candidates WHERE packageName = :packageName LIMIT 1")
    suspend fun findByPackageName(packageName: String): FinancialAppCandidateEntity?

    @Query("SELECT packageName FROM financial_app_candidates WHERE status = :status")
    suspend fun getPackageNamesByStatus(status: String): List<String>

    @Query(
        """
        SELECT * FROM financial_app_candidates
        WHERE source = :source AND status = :status
        """
    )
    suspend fun getBySourceAndStatus(
        source: String,
        status: String
    ): List<FinancialAppCandidateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FinancialAppCandidateEntity)

    @Query(
        """
        UPDATE financial_app_candidates
        SET displayName = :displayName,
            lastSeenAt = :lastSeenAt,
            updatedAt = :lastSeenAt
        WHERE packageName = :packageName
        """
    )
    suspend fun touch(
        packageName: String,
        displayName: String,
        lastSeenAt: Long
    )
}
