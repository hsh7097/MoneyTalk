package com.sanha.moneytalk.core.database.converter

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Room TypeConverter: List<Float> ↔ String (JSON)
 *
 * SmsPatternEntity의 임베딩 벡터(768차원 Float 배열)를
 * Room SQLite에 JSON 문자열로 직렬화/역직렬화합니다.
 *
 * 저장 형식 예시: "[0.123, -0.456, 0.789, ...]"
 *
 * 사용처:
 * - SmsPatternEntity.embedding 필드
 * - AppDatabase의 @TypeConverters에 등록
 *
 * @see SmsPatternEntity
 */
class FloatListConverter {

    private val gson = Gson()

    /**
     * List<Float> → JSON String 변환 (DB 저장 시)
     * @param value 임베딩 벡터
     * @return JSON 문자열 (null이면 null)
     */
    @TypeConverter
    fun fromFloatList(value: List<Float>?): String? {
        return value?.let { gson.toJson(it) }
    }

    /**
     * JSON String → List<Float> 변환 (DB 읽기 시)
     * @param value JSON 문자열
     * @return 임베딩 벡터 (null이면 null)
     */
    @TypeConverter
    fun toFloatList(value: String?): List<Float>? {
        if (value == null) return null
        val listType = object : TypeToken<List<Float>>() {}.type
        return gson.fromJson(value, listType)
    }
}
