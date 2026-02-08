package com.sanha.moneytalk.core.database.dao

import androidx.room.*
import com.sanha.moneytalk.core.database.entity.OwnedCardEntity
import kotlinx.coroutines.flow.Flow

/**
 * 보유 카드 DAO
 *
 * 사용자의 보유 카드를 관리합니다.
 * SMS 동기화 시 자동으로 카드가 등록되고,
 * 사용자가 설정 화면에서 내 카드 여부를 변경할 수 있습니다.
 */
@Dao
interface OwnedCardDao {

    /** 모든 카드 조회 (실시간 UI 업데이트) */
    @Query("SELECT * FROM owned_cards ORDER BY seenCount DESC, lastSeenAt DESC")
    fun getAllCards(): Flow<List<OwnedCardEntity>>

    /** 모든 카드 조회 (일회성) */
    @Query("SELECT * FROM owned_cards ORDER BY seenCount DESC, lastSeenAt DESC")
    suspend fun getAllCardsOnce(): List<OwnedCardEntity>

    /** 내 카드(isOwned=true) 목록 조회 (일회성) */
    @Query("SELECT cardName FROM owned_cards WHERE isOwned = 1")
    suspend fun getOwnedCardNames(): List<String>

    /** 내 카드(isOwned=true) 목록 조회 (실시간 Flow) */
    @Query("SELECT cardName FROM owned_cards WHERE isOwned = 1")
    fun getOwnedCardNamesFlow(): Flow<List<String>>

    /** 특정 카드 조회 */
    @Query("SELECT * FROM owned_cards WHERE cardName = :cardName LIMIT 1")
    suspend fun getCard(cardName: String): OwnedCardEntity?

    /** 카드 존재 여부 확인 */
    @Query("SELECT EXISTS(SELECT 1 FROM owned_cards WHERE cardName = :cardName)")
    suspend fun exists(cardName: String): Boolean

    /** 카드 삽입 (이미 있으면 무시) */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNotExists(card: OwnedCardEntity)

    /** 카드 삽입/업데이트 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: OwnedCardEntity)

    /** 내 카드 여부 변경 */
    @Query("UPDATE owned_cards SET isOwned = :isOwned WHERE cardName = :cardName")
    suspend fun updateOwnership(cardName: String, isOwned: Boolean)

    /** 발견 횟수 및 마지막 발견 시간 업데이트 */
    @Query("UPDATE owned_cards SET seenCount = seenCount + :count, lastSeenAt = :lastSeenAt WHERE cardName = :cardName")
    suspend fun updateSeenInfo(cardName: String, count: Int, lastSeenAt: Long)

    /** 카드 개수 조회 */
    @Query("SELECT COUNT(*) FROM owned_cards")
    suspend fun getCardCount(): Int

    /** 내 카드 개수 조회 */
    @Query("SELECT COUNT(*) FROM owned_cards WHERE isOwned = 1")
    suspend fun getOwnedCardCount(): Int

    /** 필터링 활성화 여부 (내 카드가 1개 이상 있으면 필터링 가능) */
    @Query("SELECT COUNT(*) > 0 FROM owned_cards WHERE isOwned = 1")
    suspend fun hasOwnedCards(): Boolean

    /** 모든 카드 삭제 */
    @Query("DELETE FROM owned_cards")
    suspend fun deleteAll()
}
