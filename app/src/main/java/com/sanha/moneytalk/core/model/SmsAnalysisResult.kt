package com.sanha.moneytalk.core.model

/**
 * SMS 분석 결과 데이터 모델
 *
 * 카드 결제 SMS에서 파싱된 결과를 담는 공통 데이터 클래스.
 * Regex 파서, Vector 검색, LLM 분석 등 모든 파싱 경로에서 공통으로 사용.
 *
 * @see com.sanha.moneytalk.core.sms2.SmsParser
 * @see com.sanha.moneytalk.core.sms2.SmsGroupClassifier
 */
data class SmsAnalysisResult(
    val amount: Int,
    val storeName: String,
    val category: String,
    val dateTime: String,
    val cardName: String
)
