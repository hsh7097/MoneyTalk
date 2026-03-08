package com.sanha.moneytalk.feature.home.data

import com.sanha.moneytalk.core.database.dao.StoreRuleDao
import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 거래처 규칙 Repository
 *
 * 거래처명 키워드 기반으로 카테고리/고정지출을 자동 적용하는 규칙을 관리합니다.
 * contains 매칭: storeName에 keyword가 포함되면 규칙 적용.
 */
@Singleton
class StoreRuleRepository @Inject constructor(
    private val storeRuleDao: StoreRuleDao
) {
    /** 모든 규칙 (Flow) */
    fun getAll(): Flow<List<StoreRuleEntity>> = storeRuleDao.getAll()

    /** 모든 규칙 (1회성) */
    suspend fun getAllOnce(): List<StoreRuleEntity> = storeRuleDao.getAllOnce()

    /** 규칙 추가/갱신 */
    suspend fun upsert(rule: StoreRuleEntity) = storeRuleDao.upsert(rule)

    /** 규칙 삭제 */
    suspend fun delete(rule: StoreRuleEntity) = storeRuleDao.delete(rule)

    /** ID로 규칙 삭제 */
    suspend fun deleteById(id: Long) = storeRuleDao.deleteById(id)

    /**
     * storeName에 매칭되는 첫 번째 규칙 반환 (contains 매칭).
     * 규칙이 없거나 매칭되지 않으면 null 반환.
     */
    suspend fun findMatchingRule(storeName: String): StoreRuleEntity? {
        val rules = getAllOnce()
        if (rules.isEmpty()) return null
        val lowerStore = storeName.lowercase()
        return rules.firstOrNull { lowerStore.contains(it.keyword.lowercase()) }
    }
}
