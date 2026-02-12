package com.sanha.moneytalk.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sanha.moneytalk.core.database.entity.SmsPatternEntity
import kotlinx.coroutines.flow.Flow

/**
 * SMS 패턴 DAO (Data Access Object)
 *
 * 벡터 유사도 기반 SMS 분류 시스템의 데이터 접근 계층입니다.
 * SmsPatternEntity에 대한 CRUD 및 검색 쿼리를 제공합니다.
 *
 * 주요 사용처:
 * - HybridSmsClassifier: 벡터 검색을 위한 패턴 조회/등록
 * - SmsBatchProcessor: 대량 패턴 매칭 및 등록
 * - VectorSearchEngine: 유사도 검색 시 패턴 목록 제공
 *
 * @see SmsPatternEntity
 * @see com.sanha.moneytalk.core.util.HybridSmsClassifier
 */
@Dao
interface SmsPatternDao {

    /** 단일 패턴 삽입 (충돌 시 교체) - 새 패턴 학습 시 사용 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pattern: SmsPatternEntity): Long

    /** 다수 패턴 일괄 삽입 (충돌 시 교체) - 배치 처리 시 사용 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(patterns: List<SmsPatternEntity>)

    /** 패턴 정보 업데이트 (파싱 결과 수정 등) */
    @Update
    suspend fun update(pattern: SmsPatternEntity)

    /** 특정 패턴 삭제 */
    @Delete
    suspend fun delete(pattern: SmsPatternEntity)

    /**
     * 모든 결제 패턴 조회 (벡터 검색용)
     * VectorSearchEngine에서 코사인 유사도 검색 시 후보 패턴으로 사용
     */
    @Query("SELECT * FROM sms_patterns WHERE isPayment = 1")
    suspend fun getAllPaymentPatterns(): List<SmsPatternEntity>

    /**
     * 비결제 패턴 조회 (비결제 SMS 빠른 필터링용)
     * LLM이 비결제로 판정한 SMS의 벡터를 캐시하여
     * 다음에 유사한 SMS가 오면 Tier 2에서 바로 비결제로 판정
     */
    @Query("SELECT * FROM sms_patterns WHERE isPayment = 0")
    suspend fun getAllNonPaymentPatterns(): List<SmsPatternEntity>

    /** 모든 패턴 조회 (최근 매칭순 정렬) */
    @Query("SELECT * FROM sms_patterns ORDER BY lastMatchedAt DESC")
    suspend fun getAllPatterns(): List<SmsPatternEntity>

    /** 발신번호로 패턴 조회 (같은 카드사/은행 패턴 그룹핑) */
    @Query("SELECT * FROM sms_patterns WHERE senderAddress = :address")
    suspend fun getPatternsBySender(address: String): List<SmsPatternEntity>

    /** 전체 패턴 수 조회 */
    @Query("SELECT COUNT(*) FROM sms_patterns")
    suspend fun getPatternCount(): Int

    /** 결제 패턴 수 조회 - 부트스트랩 모드 판정에 사용 (< 10이면 부트스트랩) */
    @Query("SELECT COUNT(*) FROM sms_patterns WHERE isPayment = 1")
    suspend fun getPaymentPatternCount(): Int

    /**
     * 매칭 횟수 증가 + 마지막 매칭 시간 갱신
     * 동일 패턴이 반복 매칭될 때 호출하여 패턴 사용 빈도를 추적
     */
    @Query("UPDATE sms_patterns SET matchCount = matchCount + 1, lastMatchedAt = :timestamp WHERE id = :patternId")
    suspend fun incrementMatchCount(patternId: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * 오래된 미사용 패턴 정리
     * 조건: 마지막 매칭이 threshold 이전 AND 매칭 횟수 1회 이하
     * → 1회만 매칭되고 오래 사용 안 된 패턴은 노이즈로 간주하여 삭제
     */
    @Query("DELETE FROM sms_patterns WHERE lastMatchedAt < :threshold AND matchCount <= 1")
    suspend fun deleteStalePatterns(threshold: Long)

    /** 전체 패턴 삭제 (DB 초기화 시 사용) */
    @Query("DELETE FROM sms_patterns")
    suspend fun deleteAll()

    /** 모든 패턴 실시간 관찰 (매칭 횟수 내림차순) - UI 모니터링용 */
    @Query("SELECT * FROM sms_patterns ORDER BY matchCount DESC")
    fun observeAllPatterns(): Flow<List<SmsPatternEntity>>
}
