package com.sanha.moneytalk.core.sms2

import javax.inject.Inject
import javax.inject.Singleton

/**
 * ===== SMS 사전 필터링 (Step 2) =====
 *
 * 역할: 명백한 비결제 SMS를 임베딩 API 호출 전에 제거하여 비용 절약.
 *
 * 필터링 기준 2가지:
 * 1. 키워드 필터 [isObviouslyNonPayment]: 비결제 키워드 포함 시 제외
 * 2. 구조 필터 [lacksPaymentRequirements]: 길이/숫자/금액 패턴 조건 미충족 시 제외
 *
 * 키워드 목록: 기존 비결제 키워드 합집합을
 * 여기서 단일 관리 (중복 제거).
 *
 * 호출 순서: SmsPipeline.process() → [여기] → SmsPipeline.batchEmbed()
 */
@Singleton
class SmsPreFilter @Inject constructor() {

    companion object {
        /**
         * 비결제 SMS 키워드 (통합)
         *
         * 이 키워드 중 하나라도 SMS 본문에 포함되면 결제 SMS가 아닌 것으로 판단.
         *
         * 카테고리:
         * - 인증/보안: 인증번호, OTP, 비밀번호 등
         * - 해외발신: 국외발신, 국제발신 등
         * - 광고/마케팅: 광고, 수신거부, 이벤트, 프로모션 등
         * - 청구/안내: 결제내역, 명세서, 청구서, 결제예정 등 (결제 예고 ≠ 실제 결제)
         * - 배송: 배송, 택배, 운송장
         * - 금융광고: 대출, 투자, 분양 등
         */
        private val NON_PAYMENT_KEYWORDS = listOf(
            // 인증/보안
            "인증번호", "인증코드", "authentication", "verification", "code",
            "OTP", "본인확인", "비밀번호",
            // 해외 발신
            "국외발신", "국제발신", "해외발신",
            // 광고/마케팅
            "광고", "[광고]", "(광고)", "무료수신거부", "수신거부", "080",
            "특가", "이벤트", "증정", "당첨", "축하", "최저가", "마감직전",
            "포인트 적립", "혜택안내", "프로모션", "할인쿠폰", "무료체험",
            // 안내
            "안내문", "점검", "정기점검", "공지사항",
            "회원님", "고객님", "불편을 드려",
            // 청구/안내 (결제 예고 ≠ 실제 결제)
            "결제내역", "명세서", "청구서", "이용대금", "결제예정", "결제일",
            "결제금액", "카드대금", "결제대금", "청구금액",
            "출금예정", "출금 예정", "자동이체", "납부안내", "납입일",
            // 배송
            "배송", "택배", "운송장",
            // 기타
            "설문", "survey", "투표",
            "예약은", "방문때", "접수 완료",
            "보험금", "해외원화결제시", "수수료 발생", "차단신청",
            // 금융광고
            "금리", "대출", "투자", "수익", "분양", "모델하우스"
        )

        /**
         * NON_PAYMENT_KEYWORDS를 미리 lowercase로 캐시.
         * filter() 호출 시 매번 keyword.lowercase() 생성 방지.
         */
        private val NON_PAYMENT_KEYWORDS_LOWER = NON_PAYMENT_KEYWORDS.map { it.lowercase() }

        /** HTTP 링크 패턴 — 광고/안내 링크 포함 SMS 제외용 */
        private val HTTP_PATTERN = Regex("https?://", RegexOption.IGNORE_CASE)

        /** 금액 패턴: 2자리 이상 연속 숫자 (결제 금액 존재 여부 판별) */
        private val AMOUNT_PATTERN = Regex("\\d{2,}")

        /** 금액+원 패턴: "15,000원" 형태 */
        private val AMOUNT_WITH_WON_PATTERN = Regex("[\\d,]+원")

        /**
         * 결제 힌트 키워드
         *
         * 구조 필터(lacksPaymentRequirements)에서 사용.
         * 이 중 하나라도 있거나 "숫자+원" 패턴이 있어야 결제 가능성 인정.
         */
        private val PAYMENT_HINT_KEYWORDS = listOf(
            "승인", "결제", "출금", "이체",
            "원", "USD", "JPY", "EUR",
            "카드", "체크", "CMS"
        )

        /** SMS 최대 길이 (100자 초과 = 결제 SMS 가능성 낮음) */
        private const val MAX_SMS_LENGTH = 100

        /** SMS 최소 길이 (20자 미만 = 결제 정보 담기 어려움) */
        private const val MIN_SMS_LENGTH = 20
    }

    /**
     * 비결제 SMS 필터링
     *
     * 키워드 필터 + 구조 필터를 순서대로 적용.
     * 두 조건 모두 통과한 SMS만 반환.
     *
     * @param smsList 전체 SMS 입력
     * @return 결제 가능성이 있는 SMS만 (비결제 제거됨)
     */
    fun filter(smsList: List<SmsInput>): List<SmsInput> {
        return smsList.filter { sms ->
            !isObviouslyNonPayment(sms.body) && !lacksPaymentRequirements(sms.body)
        }
    }

    /**
     * 키워드 기반 비결제 판별
     *
     * SMS 본문을 lowercase로 변환 후
     * NON_PAYMENT_KEYWORDS_LOWER 중 하나라도 포함되면 비결제로 판정.
     *
     * @return true = 비결제 (필터링 대상)
     */
    internal fun isObviouslyNonPayment(body: String): Boolean {
        val lowerBody = body.lowercase()
        return NON_PAYMENT_KEYWORDS_LOWER.any { lowerBody.contains(it) }
    }

    /**
     * 구조 기반 결제 최소 조건 미충족 판별
     *
     * 다음 중 하나라도 해당하면 결제 SMS가 아님:
     * 1. [MIN_SMS_LENGTH]자 미만 또는 [MAX_SMS_LENGTH]자 초과
     * 2. 숫자가 하나도 없음 (금액 표현 불가)
     * 3. 2자리 이상 연속 숫자가 없음 (금액 패턴 없음)
     * 4. HTTP 링크만 있고 "결제"/"승인" 키워드 없음 (광고 링크)
     * 5. 결제 힌트 키워드도 없고 "숫자+원" 패턴도 없음
     *
     * @return true = 결제 최소 조건 미충족 (필터링 대상)
     */
    internal fun lacksPaymentRequirements(body: String): Boolean {
        // 1. 길이 체크
        if (body.length < MIN_SMS_LENGTH) return true
        if (body.length > MAX_SMS_LENGTH) return true

        // 2. 숫자 존재 여부
        if (!body.any { it.isDigit() }) return true

        // 3. 2자리 이상 연속 숫자 (금액 패턴)
        if (!AMOUNT_PATTERN.containsMatchIn(body)) return true

        // 4. HTTP 링크 + 결제 키워드 없음 = 광고/안내 링크
        if (HTTP_PATTERN.containsMatchIn(body)) {
            if (!body.contains("결제") && !body.contains("승인")) return true
        }

        // 5. 결제 힌트 키워드 또는 "숫자+원" 패턴 필요
        val hasPaymentHint = PAYMENT_HINT_KEYWORDS.any { body.contains(it, ignoreCase = true) }
        val hasAmountWithUnit = AMOUNT_WITH_WON_PATTERN.containsMatchIn(body)
        if (!hasPaymentHint && !hasAmountWithUnit) return true

        return false
    }
}
