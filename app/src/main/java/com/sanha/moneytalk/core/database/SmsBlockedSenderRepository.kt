package com.sanha.moneytalk.core.database

import com.sanha.moneytalk.core.database.dao.SmsBlockedSenderDao
import com.sanha.moneytalk.core.database.entity.SmsBlockedSenderEntity
import com.sanha.moneytalk.core.sms2.SmsFilter
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS 수신거부 발신번호 Repository
 *
 * 발신번호 정규화는 [SmsFilter.normalizeAddress]를 재사용하여
 * 저장 시와 비교 시 동일한 정규화 로직을 보장합니다.
 */
@Singleton
class SmsBlockedSenderRepository @Inject constructor(
    private val dao: SmsBlockedSenderDao
) {

    /** 파싱 필터링용 캐시 (정규화 주소) */
    @Volatile
    private var cachedBlockedAddresses: Set<String>? = null

    /** 설정 화면 목록 관찰 */
    fun observeBlockedSenders(): Flow<List<SmsBlockedSenderEntity>> = dao.observeAll()

    /** 파싱 필터링용 주소 조회 (백그라운드 스레드에서 호출) */
    suspend fun getBlockedAddressSet(): Set<String> {
        return cachedBlockedAddresses ?: dao.getAllAddresses()
            .filter { it.isNotBlank() }
            .toSet()
            .also { cachedBlockedAddresses = it }
    }

    /** 수신거부 번호 추가 */
    suspend fun addBlockedSender(rawAddress: String): Boolean {
        val trimmed = rawAddress.trim()
        val normalized = SmsFilter.normalizeAddress(trimmed)
        if (normalized.isBlank()) return false

        dao.insert(
            SmsBlockedSenderEntity(
                address = normalized,
                rawAddress = trimmed
            )
        )
        invalidateCache()
        return true
    }

    /** 수신거부 번호 삭제 */
    suspend fun removeBlockedSender(address: String): Boolean {
        val normalized = SmsFilter.normalizeAddress(address)
        if (normalized.isBlank()) return false
        val deleted = dao.deleteByAddress(normalized)
        if (deleted > 0) {
            invalidateCache()
        }
        return deleted > 0
    }

    private fun invalidateCache() {
        cachedBlockedAddresses = null
    }
}
