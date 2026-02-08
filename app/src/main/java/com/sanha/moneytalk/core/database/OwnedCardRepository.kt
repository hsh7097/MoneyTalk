package com.sanha.moneytalk.core.database

import android.util.Log
import com.sanha.moneytalk.core.database.dao.OwnedCardDao
import com.sanha.moneytalk.core.database.entity.OwnedCardEntity
import com.sanha.moneytalk.core.util.CardNameNormalizer
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 보유 카드 Repository
 *
 * OwnedCard의 비즈니스 로직을 담당합니다.
 * - SMS 동기화 시 카드 자동 등록 (정규화된 이름으로)
 * - 사용자의 카드 소유 설정 관리
 * - 내 카드 기반 지출 필터링 지원
 */
@Singleton
class OwnedCardRepository @Inject constructor(
    private val ownedCardDao: OwnedCardDao
) {
    companion object {
        private const val TAG = "OwnedCard"
    }

    /** 모든 카드 목록 (Flow) */
    fun getAllCards(): Flow<List<OwnedCardEntity>> = ownedCardDao.getAllCards()

    /** 내 카드명 목록 (Flow) */
    fun getOwnedCardNamesFlow(): Flow<List<String>> = ownedCardDao.getOwnedCardNamesFlow()

    /** 내 카드명 목록 (일회성) */
    suspend fun getOwnedCardNames(): List<String> = ownedCardDao.getOwnedCardNames()

    /** 내 카드 여부 변경 */
    suspend fun updateOwnership(cardName: String, isOwned: Boolean) {
        ownedCardDao.updateOwnership(cardName, isOwned)
        Log.d(TAG, "카드 소유 변경: $cardName → isOwned=$isOwned")
    }

    /** 내 카드 필터링 활성화 여부 */
    suspend fun hasOwnedCards(): Boolean = ownedCardDao.hasOwnedCards()

    /**
     * SMS 동기화 시 발견된 카드명들을 일괄 등록/업데이트
     *
     * @param rawCardNames 정규화 전 카드명 목록 (SMS에서 추출된 원본)
     */
    suspend fun registerCardsFromSync(rawCardNames: List<String>) {
        if (rawCardNames.isEmpty()) return

        // 카드명 정규화 후 중복 제거하여 등록 수 집계
        val normalizedCounts = rawCardNames
            .map { CardNameNormalizer.normalize(it) }
            .filter { it.isNotBlank() && it != "기타" }
            .groupingBy { it }
            .eachCount()

        val now = System.currentTimeMillis()

        for ((normalizedName, count) in normalizedCounts) {
            val existing = ownedCardDao.getCard(normalizedName)
            if (existing != null) {
                // 이미 등록된 카드 → 발견 횟수와 마지막 시간만 업데이트
                ownedCardDao.updateSeenInfo(normalizedName, count, now)
            } else {
                // 신규 카드 → 자동 등록 (기본 isOwned=true)
                ownedCardDao.insertIfNotExists(
                    OwnedCardEntity(
                        cardName = normalizedName,
                        isOwned = true,
                        firstSeenAt = now,
                        lastSeenAt = now,
                        seenCount = count,
                        source = "sms_sync"
                    )
                )
                Log.d(TAG, "새 카드 자동 등록: $normalizedName (발견 ${count}건)")
            }
        }
    }

    /** 전체 초기화 시 카드 삭제 */
    suspend fun deleteAll() {
        ownedCardDao.deleteAll()
    }
}
