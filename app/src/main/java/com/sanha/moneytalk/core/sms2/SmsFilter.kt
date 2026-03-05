package com.sanha.moneytalk.core.sms2

/**
 * SMS 발신번호 기반 사전 필터링
 *
 * 010/070 개인번호 SMS를 무조건 제외하여 파싱/분류 대상을 줄입니다.
 * 금융기관 SMS는 1588/1544/02 등 대표번호로 발송되므로 개인번호 차단에 문제 없음.
 *
 * 사용 위치: SmsReader의 각 채널(SMS/MMS/RCS) 읽기 루프에서 리스트 add 이전에 호출
 */
object SmsFilter {

    /** 주소 정규화용 Regex: 하이픈, 공백, 괄호, NBSP 제거 */
    private val ADDRESS_CLEAN_PATTERN = Regex("""[-\s()\u00A0]""")

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
     * 발신번호 기반 SMS 건너뛰기 판단
     *
     * 010/070 개인번호는 무조건 건너뛴다 (본문 내용 무관).
     * 금융기관은 대표번호(1588, 1544, 02 등)로 SMS를 발송하므로 개인번호 차단 안전.
     *
     * @param address 원본 발신번호
     * @param body SMS 본문 (미사용, 호출부 시그니처 유지)
     * @return true면 건너뛰기 (개인 문자), false면 처리 대상
     */
    fun shouldSkipBySender(address: String, @Suppress("UNUSED_PARAMETER") body: String): Boolean {
        val normalized = normalizeAddress(address)
        return normalized.startsWith("010") || normalized.startsWith("070")
    }
}
