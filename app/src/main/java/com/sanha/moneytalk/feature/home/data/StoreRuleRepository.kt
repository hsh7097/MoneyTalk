package com.sanha.moneytalk.feature.home.data

import com.sanha.moneytalk.core.database.dao.StoreRuleDao
import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 거래처 규칙 Repository
 *
 * 거래처명 키워드 기반으로 카테고리/고정지출/통계 제외를 자동 적용하는 규칙을 관리합니다.
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

    /** keyword 기준 정확 일치 규칙 조회 */
    suspend fun getByKeyword(keyword: String): StoreRuleEntity? = storeRuleDao.getByKeyword(keyword)

    /** 규칙 추가/갱신 */
    suspend fun upsert(rule: StoreRuleEntity) = storeRuleDao.upsert(rule)

    /** 규칙 삭제 */
    suspend fun delete(rule: StoreRuleEntity) = storeRuleDao.delete(rule)

    /** ID로 규칙 삭제 */
    suspend fun deleteById(id: Long) = storeRuleDao.deleteById(id)

    /**
     * storeName에 매칭되는 가장 구체적인 규칙 반환 (contains 매칭).
     * keyword 길이가 긴 규칙을 우선하고, 길이가 같으면 최신 규칙을 선택한다.
     */
    suspend fun findMatchingRule(storeName: String): StoreRuleEntity? {
        val rules = getAllOnce()
        if (rules.isEmpty()) return null
        val lowerStore = storeName.lowercase()
        return rules
            .filter { lowerStore.contains(it.keyword.lowercase()) }
            .maxWithOrNull(
                compareBy<StoreRuleEntity>({ it.keyword.length }, { it.createdAt })
            )
    }
}
