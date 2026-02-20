package com.sanha.moneytalk.core.sms2

import javax.inject.Inject
import javax.inject.Singleton

/**
 * ===== SMS 수입/결제 분류 필터 =====
 *
 * 역할: 금융 SMS 중에서 결제(지출) vs 수입(입금) vs 무관(SKIP)을 분류.
 *
 * SmsPreFilter와의 관계:
 * - SmsPreFilter: "이 SMS는 금융 거래가 아님" (인증번호, 광고, 배송 등 → 임베딩 전 제거)
 * - SmsIncomeFilter: "이 SMS는 금융 거래인데, 지출인가 수입인가?"
 *
 * SmsSyncCoordinator에서 SmsPipeline 전달 전에 호출됨.
 * 수입으로 분류된 SMS는 SmsPipeline에 넣지 않고 SyncResult.incomes로 반환.
 *
 * 분류 로직:
 * 1. shouldSkip: 빈 SMS, 100자 초과, 제외 키워드 → SKIP
 * 2. 금융기관 키워드 없음 → SKIP
 * 3. 금액 패턴 없음 → SKIP
 * 4. 취소 키워드 → INCOME (출금취소 = 돈 돌아옴)
 * 5. 결제 키워드 → PAYMENT
 * 6. 수입 제외 키워드 → SKIP (자동이체출금 안내 등)
 * 7. 수입 키워드 → INCOME
 * 8. 그 외 (금융+금액은 있지만 명시적 키워드 없음) → PAYMENT (벡터/LLM에 맡김)
 *
 * 의존성: 없음 (모든 키워드를 자체 보유, core/sms 미참조)
 *
 * @see SmsPreFilter 임베딩 전 비결제 제거 (SmsPipeline 내부)
 */
@Singleton
class SmsIncomeFilter @Inject constructor() {

    companion object {
        /** SMS 최대 길이 (일반 결제 SMS는 40~100자, 안내/광고성은 100자 이상) */
        private const val MAX_SMS_LENGTH = 100
    }

    // ===== 키워드 정의 =====

    /**
     * 금융기관 키워드 (46개)
     *
     * 카드사/은행 식별용. 이 키워드 중 하나라도 포함되어야 금융 SMS.
     * 주요 국내 카드사/은행을 포함합니다.
     */
    private val financialKeywords = setOf(
        // KB국민
        "kb", "국민", "노리",
        // 신한
        "신한", "sol", "쏠",
        // 삼성, 현대, 롯데, 하나, 우리
        "삼성", "현대", "롯데", "하나", "우리",
        // NH농협
        "nh", "농협",
        // BC
        "bc", "비씨",
        // 씨티
        "씨티", "시티", "citi",
        // 카카오, 토스
        "카카오", "카뱅", "토스",
        // 케이뱅크
        "케이뱅크", "k뱅크",
        // IBK기업, SC제일, 수협
        "ibk", "기업", "sc제일", "제일은행", "수협",
        // 지방은행
        "광주은행", "kjb", "전북은행", "jb", "경남은행", "bnk",
        "부산은행", "대구은행", "dgb",
        // 기타 금융기관
        "새마을", "mg", "신협", "kfcc",
        "우체국", "우정", "post",
        "저축은행", "ok저축",
        // 공통
        "체크카드", "신용카드", "선불", "후불"
    )

    /** 결제 키워드 */
    private val paymentKeywords = listOf(
        "결제", "승인", "사용", "출금", "이용", "cms출"
    )

    /** 수입 키워드 */
    private val incomeKeywords = listOf(
        "입금", "이체입금", "급여", "월급", "보너스", "상여",
        "환급", "정산", "송금", "받으셨습니다", "입금되었습니다",
        "자동이체입금", "무통장입금", "계좌입금",
        "출금취소"  // 출금 취소 = 돈이 돌아옴 → 수입
    )

    /** 취소/환불 키워드 (결제 키워드를 포함하지만 실제로는 수입) */
    private val cancellationKeywords = listOf(
        "출금취소", "승인취소", "결제취소", "취소승인", "취소완료"
    )

    /** 수입 제외 키워드 (자동이체 출금 안내 등) */
    private val incomeExcludeKeywords = listOf(
        "자동이체출금", "출금예정", "결제예정", "납부",
        "보험료", "카드대금", "통신료", "공과금"
    )

    /**
     * 제외 키워드 (광고/안내 필터링)
     *
     * SmsPreFilter의 NON_PAYMENT_KEYWORDS와 일부 중복되지만,
     * SmsIncomeFilter가 SmsPipeline 전에 실행되므로 필요.
     * 최소한의 제외만 수행 (SmsPreFilter가 상세 필터링 담당).
     */
    private val excludeKeywords = listOf(
        "광고", "홍보", "이벤트", "혜택안내", "포인트 적립",
        "명세서", "청구서", "이용대금",
        "결제내역", "결제금액", "카드대금", "결제대금", "청구금액",
        "출금 예정", "출금예정", "퇴직"
    )

    /** 금액 패턴 (사전 컴파일) */
    private val AMOUNT_PATTERN_WITH_WON = Regex("""[\d,]+원""")
    private val AMOUNT_PATTERN_NUMBER_ONLY = Regex("""\n[\d,]{3,}\n""")

    // ===== 사용자 제외 키워드 =====

    /** 사용자 정의 제외 키워드 (DB에서 로드, 호출자가 설정) */
    @Volatile
    private var userExcludeKeywords: Set<String> = emptySet()

    /**
     * 사용자 제외 키워드 설정
     *
     * SmsSyncCoordinator가 동기화 시작 전에 DB에서 로드하여 설정.
     * @param keywords lowercase 변환된 키워드 Set
     */
    fun setUserExcludeKeywords(keywords: Set<String>) {
        userExcludeKeywords = keywords
    }

    // ===== 분류 메소드 =====

    /**
     * SMS 본문으로 유형을 분류
     *
     * @param body SMS 본문 (SmsInput.body)
     * @return SmsType.PAYMENT, INCOME, or SKIP
     */
    fun classify(body: String): SmsType {
        // 1. 기본 필터
        if (body.isBlank()) return SmsType.SKIP
        if (body.length > MAX_SMS_LENGTH) return SmsType.SKIP

        val bodyLower = body.lowercase()

        // 제외 키워드 (광고, 안내 등)
        if (excludeKeywords.any { bodyLower.contains(it) }) return SmsType.SKIP
        if (userExcludeKeywords.any { bodyLower.contains(it) }) return SmsType.SKIP

        // 2. 금융기관 키워드
        if (financialKeywords.none { bodyLower.contains(it) }) return SmsType.SKIP

        // 3. 금액 패턴
        val hasAmount = AMOUNT_PATTERN_WITH_WON.containsMatchIn(body)
            || AMOUNT_PATTERN_NUMBER_ONLY.containsMatchIn(body)
        if (!hasAmount) return SmsType.SKIP

        // 4. 취소 → 수입 (결제 키워드보다 우선)
        if (cancellationKeywords.any { bodyLower.contains(it) }) return SmsType.INCOME

        // 5. 결제 → 지출
        if (paymentKeywords.any { bodyLower.contains(it) }) return SmsType.PAYMENT

        // 6. 수입 제외 키워드 (자동이체 출금 안내 등)
        if (incomeExcludeKeywords.any { bodyLower.contains(it) }) return SmsType.SKIP

        // 7. 수입 키워드
        if (incomeKeywords.any { bodyLower.contains(it) }) return SmsType.INCOME

        // 8. 금융 키워드 + 금액은 있지만 결제/수입 키워드 없음
        // → SmsPipeline에 넘겨서 벡터/LLM으로 판단하게 함
        return SmsType.PAYMENT
    }

    /**
     * SMS 목록을 유형별로 분류
     *
     * @param smsList 전체 SMS 입력
     * @return Triple(결제 후보, 수입 후보, 스킵된 SMS)
     */
    fun classifyAll(
        smsList: List<SmsInput>
    ): Triple<List<SmsInput>, List<SmsInput>, List<SmsInput>> {
        val payments = mutableListOf<SmsInput>()
        val incomes = mutableListOf<SmsInput>()
        val skipped = mutableListOf<SmsInput>()

        for (sms in smsList) {
            when (classify(sms.body)) {
                SmsType.PAYMENT -> payments.add(sms)
                SmsType.INCOME -> incomes.add(sms)
                SmsType.SKIP -> skipped.add(sms)
            }
        }

        return Triple(payments, incomes, skipped)
    }
}
