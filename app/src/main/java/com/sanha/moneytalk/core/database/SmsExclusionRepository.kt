package com.sanha.moneytalk.core.database

import android.util.Log
import com.sanha.moneytalk.core.database.dao.SmsExclusionKeywordDao
import com.sanha.moneytalk.core.database.entity.SmsExclusionKeywordEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS 제외 키워드 Repository
 *
 * 제외 키워드의 CRUD와 인메모리 캐시를 관리합니다.
 * SmsParser에 사용자 제외 키워드를 제공하고,
 * 설정 화면 및 채팅에서 키워드 추가/삭제를 지원합니다.
 */
@Singleton
class SmsExclusionRepository @Inject constructor(
    private val dao: SmsExclusionKeywordDao
) {
    companion object {
        private const val TAG = "SmsExclusion"
    }

    /** 인메모리 캐시 (사용자/채팅 키워드만, lowercase) */
    private var cachedUserKeywords: Set<String>? = null

    /**
     * 사용자/채팅 키워드 조회 (캐시 사용)
     * SmsParser.setUserExcludeKeywords()에 전달할 키워드 Set
     */
    suspend fun getUserKeywords(): Set<String> {
        return cachedUserKeywords ?: dao.getUserKeywords().toSet().also {
            cachedUserKeywords = it
        }
    }

    /**
     * 전체 키워드 엔티티 조회 (설정 화면 표시용)
     */
    suspend fun getAllKeywords(): List<SmsExclusionKeywordEntity> {
        return dao.getAll()
    }

    /**
     * 키워드 추가
     * @param keyword 추가할 키워드 (자동 lowercase 변환)
     * @param source "user" 또는 "chat"
     * @return 추가 성공 여부
     */
    suspend fun addKeyword(keyword: String, source: String = "user"): Boolean {
        val normalized = keyword.trim().lowercase()
        if (normalized.isBlank()) return false

        dao.insert(SmsExclusionKeywordEntity(
            keyword = normalized,
            source = source
        ))
        invalidateCache()
        Log.d(TAG, "키워드 추가: $normalized (source=$source)")
        return true
    }

    /**
     * 키워드 삭제 (default 소스는 삭제 불가)
     * @return 삭제된 행 수
     */
    suspend fun removeKeyword(keyword: String): Int {
        val normalized = keyword.trim().lowercase()
        val deleted = dao.deleteByKeyword(normalized)
        if (deleted > 0) {
            invalidateCache()
            Log.d(TAG, "키워드 삭제: $normalized")
        }
        return deleted
    }

    /** 소스별 개수 */
    suspend fun getDefaultCount(): Int = dao.getCountBySource("default")
    suspend fun getUserCount(): Int = dao.getCount() - dao.getCountBySource("default")

    /** 캐시 무효화 */
    private fun invalidateCache() {
        cachedUserKeywords = null
    }
}
