package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.sanha.moneytalk.core.database.converter.FloatListConverter

/**
 * SMS 패턴 임베딩 저장 엔티티
 *
 * 결제 문자의 임베딩 벡터와 파싱 결과를 캐시하여
 * 유사한 SMS가 들어왔을 때 빠르게 분류/파싱할 수 있도록 합니다.
 *
 * 벡터 유사도 기반 SMS 분류 시스템의 핵심 저장소.
 */
@Entity(tableName = "sms_patterns")
@TypeConverters(FloatListConverter::class)
data class SmsPatternEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 원본 SMS 본문 (템플릿화된 형태) */
    val smsTemplate: String,

    /** 발신 번호 (패턴 그룹핑용) */
    val senderAddress: String = "",

    /** 임베딩 벡터 (Gemini Embedding API로 생성) */
    val embedding: List<Float>,

    /** 결제 문자 여부 (true: 결제, false: 비결제) */
    val isPayment: Boolean = true,

    /** 파싱된 결제 금액 (캐시) */
    val parsedAmount: Int = 0,

    /** 파싱된 가게명 (캐시) */
    val parsedStoreName: String = "",

    /** 파싱된 카드사명 (캐시) */
    val parsedCardName: String = "",

    /** 파싱된 카테고리 (캐시) */
    val parsedCategory: String = "",

    /** 파싱 소스 (regex, llm, manual) */
    val parseSource: String = "regex",

    /** 신뢰도 점수 (0.0 ~ 1.0) */
    val confidence: Float = 1.0f,

    /** 이 패턴이 매칭된 횟수 */
    val matchCount: Int = 1,

    /** 생성 시간 */
    val createdAt: Long = System.currentTimeMillis(),

    /** 마지막 매칭 시간 */
    val lastMatchedAt: Long = System.currentTimeMillis()
)
