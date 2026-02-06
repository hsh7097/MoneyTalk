package com.sanha.moneytalk.core.database.converter

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Room TypeConverter for FloatArray <-> ByteArray
 * 벡터 데이터를 Room DB에 BLOB으로 저장하기 위한 변환기
 */
class VectorConverters {

    @TypeConverter
    fun fromFloatArray(vector: FloatArray?): ByteArray? {
        if (vector == null) return null
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(vector)
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(bytes: ByteArray?): FloatArray? {
        if (bytes == null) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floatArray = FloatArray(bytes.size / 4)
        buffer.asFloatBuffer().get(floatArray)
        return floatArray
    }
}
