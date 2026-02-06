package com.sanha.moneytalk.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 지출 내역 엔티티
 *
 * SMS 파싱으로 자동 추출된 카드 결제 내역을 저장합니다.
 * 하나의 결제 SMS가 하나의 ExpenseEntity로 변환됩니다.
 *
 * 데이터 흐름:
 * SMS 수신 → SmsParser/HybridSmsClassifier 파싱 → ExpenseEntity 생성 → expenses 테이블 저장
 *
 * 중복 방지:
 * smsId (발신번호 + 시간 + 본문해시)로 동일 SMS의 중복 저장을 방지합니다.
 *
 * @see com.sanha.moneytalk.core.database.dao.ExpenseDao
 */
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 결제 금액 (원 단위) */
    val amount: Int,

    /** 가게명/상호명 (SMS에서 추출) */
    val storeName: String,

    /** 지출 카테고리 (식비, 카페, 교통, 쇼핑 등) */
    val category: String,

    /** 카드사명 (KB국민, 신한, 삼성 등) */
    val cardName: String,

    /** 결제 일시 (Unix timestamp, 밀리초) */
    val dateTime: Long,

    /** 원본 SMS 본문 (디버깅/확인용) */
    val originalSms: String,

    /** SMS 고유 ID (발신번호_시간_본문해시) - 중복 저장 방지 */
    val smsId: String,

    /** 사용자 메모 (선택) */
    val memo: String? = null,

    /** 레코드 생성 시간 */
    val createdAt: Long = System.currentTimeMillis()
)
