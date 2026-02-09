package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * SMS 제외 키워드 엔티티
 *
 * SMS 파싱 시 특정 키워드가 포함된 문자를 결제/수입 문자에서 제외합니다.
 * SmsParser의 excludeKeywords(기본값)에 더해 사용자/채팅에서 추가한 키워드를 관리합니다.
 *
 * source 구분:
 * - "default": 앱 기본 제외 키워드 (삭제 불가)
 * - "user": 설정 화면에서 사용자가 추가
 * - "chat": 채팅에서 사용자가 추가
 */
@Entity(tableName = "sms_exclusion_keywords")
data class SmsExclusionKeywordEntity(
    /** 키워드 (lowercase, PK) */
    @PrimaryKey
    val keyword: String,

    /** 키워드 소스 ("default" | "user" | "chat") */
    val source: String = "user",

    /** 생성 시간 */
    val createdAt: Long = System.currentTimeMillis()
)
