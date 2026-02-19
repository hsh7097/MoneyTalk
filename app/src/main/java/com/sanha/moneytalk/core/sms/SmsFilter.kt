package com.sanha.moneytalk.core.sms

/**
 * SMS 발신번호 기반 사전 필터링
 *
 * 010/070 개인번호 SMS를 조기 제외하여 파싱/분류 대상을 줄입니다.
 * 단, 금융 힌트(금액 패턴 + 카드/은행 키워드)가 있는 경우는 제외하지 않습니다.
 *
 * 사용 위치: SmsReader의 각 채널(SMS/MMS/RCS) 읽기 루프에서 리스트 add 이전에 호출
 */
object SmsFilter {

    /** 주소 정규화용 Regex: 하이픈, 공백, 괄호, NBSP 제거 */
    private val ADDRESS_CLEAN_PATTERN = Regex("""[-\s()\u00A0]""")

    /** 금액 패턴: 3자리 이상 숫자(콤마 포함 가능) + "원" */
    private val FINANCIAL_AMOUNT_PATTERN = Regex("""[\d,]{3,}원""")

    /** 3자리 이상 순수 숫자 (금액 힌트) */
    private val FINANCIAL_NUMBER_PATTERN = Regex("""\d{3,}""")

    /** 금융 힌트 키워드 (결제/입금 등 금융 거래 관련) */
    private val FINANCIAL_HINT_KEYWORDS = listOf(
        "결제", "승인", "사용", "출금", "이용",
        "입금", "이체", "송금",
        "카드", "은행", "체크",
        "kb", "국민", "신한", "삼성", "현대", "롯데",
        "하나", "우리", "nh", "농협", "bc", "비씨",
        "카카오", "토스", "케이뱅크"
    )

    /**
     * 발신번호 정규화 — +82, 하이픈, 공백 등을 제거하여 순수 숫자열로 변환
     *
     * 예시:
     * - "+82-10-1234-5678" → "01012345678"
     * - "010 1234 5678" → "01012345678"
     * - "1588-1234" → "15881234"
     * - "+8270..." → "070..."
     */
    fun normalizeAddress(rawAddress: String): String {
        var normalized = rawAddress.trim()
        // 하이픈, 공백, 괄호 제거
        normalized = ADDRESS_CLEAN_PATTERN.replace(normalized, "")
        // +82 → 0 변환 (한국 국가코드)
        if (normalized.startsWith("+82")) {
            normalized = "0" + normalized.substring(3)
        } else if (normalized.startsWith("82") && normalized.length >= 11) {
            normalized = "0" + normalized.substring(2)
        }
        return normalized
    }

    /**
     * SMS 본문에 금융 거래 힌트가 있는지 확인
     *
     * 금액 패턴(3자리+원) 또는 (3자리 숫자 + 금융 키워드 조합)이면 true
     */
    fun hasFinancialHints(body: String): Boolean {
        // 금액 패턴 체크 ("15,000원" 등)
        if (FINANCIAL_AMOUNT_PATTERN.containsMatchIn(body)) return true
        // 3자리 이상 숫자 + 금융 키워드 조합
        val bodyLower = body.lowercase()
        val hasNumber = FINANCIAL_NUMBER_PATTERN.containsMatchIn(body)
        val hasKeyword = FINANCIAL_HINT_KEYWORDS.any { bodyLower.contains(it) }
        return hasNumber && hasKeyword
    }

    /**
     * 발신번호 기반 SMS 건너뛰기 판단
     *
     * 정규화된 주소가 010/070으로 시작하고 금융 힌트가 없으면 true (건너뛰기)
     * 그 외에는 false (처리 대상)
     *
     * @param address 원본 발신번호
     * @param body SMS 본문
     * @return true면 건너뛰기 (개인 문자), false면 처리 대상
     */
    fun shouldSkipBySender(address: String, body: String): Boolean {
        val normalized = normalizeAddress(address)
        if (!normalized.startsWith("010") && !normalized.startsWith("070")) {
            return false // 010/070이 아니면 항상 처리
        }
        // 010/070이라도 금융 힌트가 있으면 건너뛰지 않음
        return !hasFinancialHints(body)
    }
}
