package com.sanha.moneytalk.core.database

import com.sanha.moneytalk.core.database.dao.SmsBlockedSenderDao
import com.sanha.moneytalk.core.database.entity.SmsBlockedSenderEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS 수신거부 발신번호 Repository
 */
@Singleton
class SmsBlockedSenderRepository @Inject constructor(
    private val dao: SmsBlockedSenderDao
) {

    private val addressCleanupRegex = Regex("""[-\s()\u00A0]""")

    /** 파싱 필터링용 캐시 (정규화 주소) */
    @Volatile
    private var cachedBlockedAddresses: Set<String>? = null

    /** 설정 화면 목록 관찰 */
    fun observeBlockedSenders(): Flow<List<SmsBlockedSenderEntity>> = dao.observeAll()

    /** 파싱 필터링용 주소 조회 (백그라운드 스레드에서 호출) */
    fun getBlockedAddressSet(): Set<String> {
        return cachedBlockedAddresses ?: dao.getAllAddresses()
            .map { normalizeAddress(it) }
            .filter { it.isNotBlank() }
            .toSet()
            .also { cachedBlockedAddresses = it }
    }

    /** 수신거부 번호 추가 */
    suspend fun addBlockedSender(rawAddress: String): Boolean {
        val trimmed = rawAddress.trim()
        val normalized = normalizeAddress(trimmed)
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
        val normalized = normalizeAddress(address)
        if (normalized.isBlank()) return false
        val deleted = dao.deleteByAddress(normalized)
        if (deleted > 0) {
            invalidateCache()
        }
        return deleted > 0
    }

    /** 발신번호 정규화 (+82, 공백/하이픈 제거) */
    fun normalizeAddress(rawAddress: String): String {
        var normalized = rawAddress.trim()
        normalized = addressCleanupRegex.replace(normalized, "")
        if (normalized.startsWith("+82")) {
            normalized = "0" + normalized.substring(3)
        } else if (normalized.startsWith("82") && normalized.length >= 11) {
            normalized = "0" + normalized.substring(2)
        }
        return normalized
    }

    private fun invalidateCache() {
        cachedBlockedAddresses = null
    }
}
