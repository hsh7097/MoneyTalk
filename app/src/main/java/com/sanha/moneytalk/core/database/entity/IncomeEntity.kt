package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 수입 내역 엔티티
 *
 * 입금 SMS에서 자동 파싱된 수입 또는 사용자가 수동 입력한 수입을 저장합니다.
 *
 * 수입 유형: 급여, 보너스, 이체, 송금, 환급, 정산, 입금 등
 *
 * 데이터 흐름:
 * - 자동: SMS 수신 → SmsParser.isIncomeSms() → 입금 정보 추출 → IncomeEntity 생성
 * - 수동: 사용자 입력 → IncomeEntity 생성
 *
 * @see com.sanha.moneytalk.core.database.dao.IncomeDao
 */
@Entity(
    tableName = "incomes",
    indices = [
        Index(value = ["smsId"]),
        Index(value = ["dateTime"])
    ]
)
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** SMS에서 파싱된 경우 고유 ID (중복 방지, 수동 입력이면 null) */
    val smsId: String? = null,

    /** 입금 금액 (원 단위) */
    val amount: Int,

    /** 수입 유형 (급여, 보너스, 이체, 송금, 환급, 정산, 입금) */
    val type: String,

    /** 송금인/출처 (SMS에서 파싱된 경우) */
    val source: String = "",

    /** 수입 설명 (예: "OOO에서 급여") */
    val description: String,

    /** 고정 수입 여부 (급여 등 매월 반복되는 수입) */
    val isRecurring: Boolean,

    /** 매월 입금일 (고정 수입인 경우, 1~31) */
    val recurringDay: Int? = null,

    /** 입금 일시 (Unix timestamp, 밀리초) */
    val dateTime: Long,

    /** 원본 SMS 메시지 (자동 파싱인 경우) */
    val originalSms: String? = null,

    /** 사용자 메모 (선택) */
    val memo: String? = null,

    /** 레코드 생성 시간 */
    val createdAt: Long = System.currentTimeMillis()
)
