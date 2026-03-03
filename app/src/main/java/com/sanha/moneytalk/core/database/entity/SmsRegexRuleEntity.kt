package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 전화번호(sender) 기반 regex 룰 저장 엔티티
 *
 * 파싱 실행 시 sender로 ACTIVE 룰을 조회한 뒤 priority 순으로 매칭합니다.
 * 동일 룰 중복 저장을 막기 위해 (senderAddress, type, ruleKey)를 복합 PK로 사용합니다.
 */
@Entity(
    tableName = "sms_regex_rules",
    primaryKeys = ["senderAddress", "type", "ruleKey"],
    indices = [
        Index(value = ["senderAddress"]),
        Index(value = ["senderAddress", "type"]),
        Index(value = ["status"]),
        Index(value = ["updatedAt"])
    ]
)
data class SmsRegexRuleEntity(
    /** 정규화된 발신번호 (예: 15881688, 01012345678) */
    val senderAddress: String,

    /** 룰 타입 (expense, income, cancel, overseas 등) */
    val type: String,

    /** 결정적 키(rule fingerprint 해시) */
    val ruleKey: String,

    /** 본문 매칭 regex */
    val bodyRegex: String,

    /** 금액 추출 그룹명/인덱스 식별자 */
    val amountGroup: String = "",

    /** 가게명 추출 그룹명/인덱스 식별자 */
    val storeGroup: String = "",

    /** 카드명 추출 그룹명/인덱스 식별자 */
    val cardGroup: String = "",

    /** 날짜 추출 그룹명/인덱스 식별자 */
    val dateGroup: String = "",

    /** 실행 우선순위 (내림차순) */
    val priority: Int = 0,

    /** 룰 상태 (ACTIVE/INACTIVE) */
    val status: String = "ACTIVE",

    /** 룰 출처 (asset/rtdb/llm) */
    val source: String = "asset",

    /** 룰 포맷 버전 */
    val version: Int = 1,

    /** 룰 매칭 성공 횟수 */
    val matchCount: Int = 0,

    /** 룰 매칭 실패 횟수 */
    val failCount: Int = 0,

    /** 마지막 매칭 시간 */
    val lastMatchedAt: Long = 0L,

    /** 마지막 업데이트 시간 */
    val updatedAt: Long = System.currentTimeMillis(),

    /** 생성 시간 */
    val createdAt: Long = System.currentTimeMillis()
)
