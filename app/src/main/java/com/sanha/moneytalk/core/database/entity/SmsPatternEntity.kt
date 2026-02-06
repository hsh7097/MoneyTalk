package com.sanha.moneytalk.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SMS 파싱 성공 패턴 저장 엔티티
 * Gemini가 파싱에 성공한 SMS의 벡터와 결과를 저장하여
 * 동일 패턴의 SMS를 AI 호출 없이 로컬에서 즉시 처리
 */
@Entity(tableName = "sms_patterns")
data class SmsPatternEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    val vector: FloatArray,            // SMS 원문의 임베딩 벡터

    val smsTemplate: String,           // SMS 원문 (학습 기록용)
    val cardName: String,              // 파싱된 카드사명
    val amountPattern: String,         // 금액 추출 패턴 (정규식 또는 위치 정보)
    val storeNamePattern: String,      // 가게명 추출 패턴
    val dateTimePattern: String,       // 날짜/시간 추출 패턴
    val parsingSource: String,         // 파싱 출처: "regex", "gemini"

    val successCount: Int = 1,         // 이 패턴으로 성공한 횟수
    val lastUsedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SmsPatternEntity
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
