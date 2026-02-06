package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.sanha.moneytalk.core.database.converter.FloatListConverter

/**
 * 가게명 임베딩 벡터 캐시 엔티티
 *
 * 가게명의 임베딩 벡터와 분류된 카테고리를 캐시하여
 * 유사한 가게명이 들어왔을 때 Gemini API 없이 즉시 카테고리를 반환합니다.
 *
 * 카테고리 분류 4-tier 시스템의 Tier 1.5 (벡터 유사도 매칭) 데이터 저장소.
 *
 * 용도:
 * - "스타벅스강남점" 학습 후 "스타벅스역삼점"이 벡터 유사도로 자동 매칭
 * - 사용자 수정 피드백이 유사 가게에 자동 전파
 * - Gemini 배치 분류 시 시맨틱 그룹핑의 기반 데이터
 *
 * @see com.sanha.moneytalk.core.database.entity.SmsPatternEntity SMS 본문 벡터 (용도 다름)
 * @see com.sanha.moneytalk.core.database.entity.CategoryMappingEntity 정확 매핑 (경량, 벡터 없음)
 */
@Entity(
    tableName = "store_embeddings",
    indices = [Index(value = ["storeName"], unique = true)]
)
@TypeConverters(FloatListConverter::class)
data class StoreEmbeddingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 가게명 (unique) */
    val storeName: String,

    /** 분류된 카테고리 (식비, 카페, 교통 등) */
    val category: String,

    /** 가게명의 임베딩 벡터 (Gemini Embedding API, 768차원) */
    val embedding: List<Float>,

    /** 분류 출처: "gemini" / "user" / "local" / "propagated" */
    val source: String = "gemini",

    /** 분류 신뢰도 (0.0 ~ 1.0) */
    val confidence: Float = 0.8f,

    /** 이 임베딩이 매칭에 사용된 횟수 (사용 빈도 추적) */
    val matchCount: Int = 0,

    /** 생성 시간 */
    val createdAt: Long = System.currentTimeMillis(),

    /** 마지막 업데이트 시간 (카테고리 변경 시 갱신) */
    val updatedAt: Long = System.currentTimeMillis()
)
