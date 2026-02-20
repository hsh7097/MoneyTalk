package com.sanha.moneytalk.core.sms2

import java.util.Calendar

/**
 * 수입 SMS 파싱 유틸리티 (sms2 전용)
 *
 * SmsParser(V1)에서 HomeViewModel의 saveIncomes()가 사용하는 메소드만 추출.
 * - extractIncomeAmount(): 입금 금액 추출
 * - extractIncomeType(): 입금 유형 추출 (급여/이체/송금 등)
 * - extractIncomeSource(): 송금인/출처 추출
 * - extractDateTime(): 날짜/시간 추출
 * - setUserExcludeKeywords(): 사용자 제외 키워드 설정
 *
 * sms2 파이프라인의 SmsIncomeFilter가 수입으로 분류한 SMS에 대해 호출됩니다.
 */
object SmsIncomeParser {

    // ========== 사용자 제외 키워드 ==========

    private var userExcludeKeywords: Set<String> = emptySet()

    /**
     * 사용자 정의 제외 키워드 설정 (DB 연동용)
     * @param keywords lowercase로 변환된 키워드 Set
     */
    fun setUserExcludeKeywords(keywords: Set<String>) {
        userExcludeKeywords = keywords
    }

    // ========== 사전 컴파일 Regex ==========

    /** 금액 패턴: 숫자+원 */
    private val AMOUNT_EXTRACT_WITH_WON = Regex("""([\d,]+)원(?![가-힣])""")
    /** 순수 숫자 패턴 */
    private val PURE_NUMBER_PATTERN = Regex("""[\d,]+""")
    /** 숫자+원+한글 (가게명 등 제외용) */
    private val AMOUNT_WON_HANGUL_PATTERN = Regex(""".*\d+원[가-힣]+.*""")

    /** 날짜 패턴: MM/DD, MM-DD, MM.DD */
    private val DATE_PATTERN_SLASH = Regex("""(\d{1,2})[/.-](\d{1,2})""")
    /** 날짜 패턴: M월 D일 */
    private val DATE_PATTERN_KOREAN = Regex("""(\d{1,2})월\s*(\d{1,2})일""")
    /** 시간 패턴: HH:mm */
    private val TIME_PATTERN = Regex("""(\d{1,2}):(\d{2})""")

    /** "OOO님으로부터" 패턴 */
    private val FROM_PATTERN = Regex("""([가-힣a-zA-Z0-9]+)(님)?으?로부터""")
    /** "입금 OOO" 또는 "OOO 입금" 패턴 */
    private val DEPOSIT_PATTERN =
        Regex("""입금\s*([가-힣a-zA-Z0-9]{2,10})|([가-힣a-zA-Z0-9]{2,10})\s*입금""")

    /** 카드번호 패턴 (출처 추출 시 제외) */
    private val CARD_NUMBER_PATTERN = Regex("""[\d*]+""")
    /** 날짜시간 패턴 (출처 추출 시 제외) */
    private val DATETIME_PATTERN = Regex("""\d{1,2}[/.-]\d{1,2}\s+\d{1,2}:\d{2}""")
    /** 대괄호 패턴 (출처 추출 시 제외) */
    private val BRACKET_PATTERN = Regex("""\[.+\]""")
    /** 대괄호+날짜시간 복합 패턴 (출처 추출 시 제외) */
    private val BRACKET_DATETIME_PATTERN = Regex("""^\[.+\]\d{1,2}[/.-]\d{1,2}\s+\d{1,2}:\d{2}$""")

    /** 수입 키워드 (extractIncomeSource에서 출처 제외용) */
    private val incomeKeywords = listOf(
        "입금", "이체입금", "급여", "월급", "보너스", "상여",
        "환급", "정산", "송금", "받으셨습니다", "입금되었습니다",
        "자동이체입금", "무통장입금", "계좌입금",
        "출금취소"
    )

    // ========== 가게명 정리용 ==========

    private val CLEAN_CORP_PATTERN = Regex("""\(주\)|\(유\)|\(사\)|\(재\)""")
    private val CLEAN_SPECIAL_CHAR_PATTERN = Regex("""^[^\w가-힣]+|[^\w가-힣]+$""")

    // ========== 공개 메소드 ==========

    /**
     * 수입 SMS에서 금액 추출
     *
     * @param message SMS 본문
     * @return 입금 금액 (추출 실패 시 0)
     */
    fun extractIncomeAmount(message: String): Int {
        return extractAmount(message) ?: 0
    }

    /**
     * 수입 SMS에서 입금 유형 추출
     *
     * @param message SMS 본문
     * @return 입금 유형 (급여, 이체, 환급 등)
     */
    fun extractIncomeType(message: String): String {
        return when {
            message.contains("급여") || message.contains("월급") -> "급여"
            message.contains("보너스") || message.contains("상여") -> "보너스"
            message.contains("환급") -> "환급"
            message.contains("정산") -> "정산"
            message.contains("이체") -> "이체"
            message.contains("송금") -> "송금"
            else -> "입금"
        }
    }

    /**
     * 수입 SMS에서 송금인/출처 추출
     *
     * @param message SMS 본문
     * @return 송금인/출처 (추출 실패 시 빈 문자열)
     */
    fun extractIncomeSource(message: String): String {
        // 패턴 0: KB 스타일 멀티라인 - "입금" 줄 위에서 출처 탐색
        val lines = message.split("\n").map { it.trim() }
        for (i in lines.indices) {
            if (lines[i] == "입금") {
                for (j in (i - 1) downTo 0) {
                    val potentialSource = lines[j]

                    if (potentialSource.contains("**") || potentialSource.matches(CARD_NUMBER_PATTERN)) {
                        continue
                    }
                    if (potentialSource.matches(DATETIME_PATTERN)) {
                        continue
                    }
                    if (potentialSource.matches(BRACKET_PATTERN)) {
                        continue
                    }
                    if (potentialSource.matches(BRACKET_DATETIME_PATTERN)) {
                        continue
                    }
                    if (potentialSource.isBlank() || potentialSource.contains("Web발신")) {
                        continue
                    }

                    val cleanSource = cleanStoreName(potentialSource)
                    if (cleanSource.isNotBlank()) {
                        return cleanSource
                    }
                }
            }
        }

        // 패턴 1: "OOO님으로부터" 또는 "OOO으로부터"
        FROM_PATTERN.find(message)?.let {
            return it.groupValues[1]
        }

        // 패턴 2: "입금 OOO" 또는 "OOO 입금" (같은 줄 내에서만 매칭)
        for (line in lines) {
            DEPOSIT_PATTERN.find(line)?.let {
                val source = it.groupValues[1].ifEmpty { it.groupValues[2] }
                if (source.isNotBlank() && !incomeKeywords.any { keyword -> source == keyword }) {
                    return source
                }
            }
        }

        return ""
    }

    /**
     * SMS에서 날짜/시간 추출
     *
     * @param message SMS 본문
     * @param smsTimestamp SMS 수신 시간 (밀리초)
     * @return "YYYY-MM-DD HH:mm" 형식
     */
    fun extractDateTime(message: String, smsTimestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = smsTimestamp
        val currentYear = calendar.get(Calendar.YEAR)

        var month = calendar.get(Calendar.MONTH) + 1
        var day = calendar.get(Calendar.DAY_OF_MONTH)
        var hour = calendar.get(Calendar.HOUR_OF_DAY)
        var minute = calendar.get(Calendar.MINUTE)

        val dateMatch1 = DATE_PATTERN_SLASH.find(message)
        val dateMatch2 = DATE_PATTERN_KOREAN.find(message)

        if (dateMatch1 != null) {
            month = dateMatch1.groupValues[1].toIntOrNull() ?: month
            day = dateMatch1.groupValues[2].toIntOrNull() ?: day
        } else if (dateMatch2 != null) {
            month = dateMatch2.groupValues[1].toIntOrNull() ?: month
            day = dateMatch2.groupValues[2].toIntOrNull() ?: day
        }

        val timeMatch = TIME_PATTERN.find(message)
        if (timeMatch != null) {
            hour = timeMatch.groupValues[1].toIntOrNull() ?: hour
            minute = timeMatch.groupValues[2].toIntOrNull() ?: minute
        }

        if (month < 1 || month > 12) month = calendar.get(Calendar.MONTH) + 1
        if (day < 1 || day > 31) day = calendar.get(Calendar.DAY_OF_MONTH)
        if (hour < 0 || hour > 23) hour = calendar.get(Calendar.HOUR_OF_DAY)
        if (minute < 0 || minute > 59) minute = calendar.get(Calendar.MINUTE)

        return String.format("%04d-%02d-%02d %02d:%02d", currentYear, month, day, hour, minute)
    }

    // ========== 내부 헬퍼 ==========

    /**
     * KB 스타일 출금 유형 줄인지 판별
     */
    private fun isKbWithdrawalLine(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.contains("체크카드출금") ||
                trimmed == "출금" ||
                trimmed.contains("FBS출금") ||
                trimmed.contains("공동CMS출")
    }

    /**
     * SMS에서 금액 추출
     */
    private fun extractAmount(message: String): Int? {
        val lines = message.split("\n").map { it.trim() }

        // KB 스타일 우선 처리
        for (i in lines.indices) {
            if (isKbWithdrawalLine(lines[i])) {
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1]
                    if (nextLine.matches(PURE_NUMBER_PATTERN)) {
                        val amount = nextLine.replace(",", "").toIntOrNull()
                        if (amount != null && amount >= 100) {
                            return amount
                        }
                    }
                }
            }
        }

        // 패턴1: 숫자+원
        val matchWithWon = AMOUNT_EXTRACT_WITH_WON.find(message)
        if (matchWithWon != null) {
            val amount = matchWithWon.groupValues[1].replace(",", "").toIntOrNull()
            if (amount != null && amount >= 100) {
                return amount
            }
        }

        // 패턴2: 줄바꿈 사이의 금액
        for (i in lines.indices) {
            val line = lines[i]
            if (line.startsWith("잔액")) continue
            if (line.matches(AMOUNT_WON_HANGUL_PATTERN)) continue
            if (line.matches(PURE_NUMBER_PATTERN)) {
                val stripped = line.replace(",", "")
                if (stripped.length >= 3) {
                    val amount = stripped.toIntOrNull()
                    if (amount != null && amount >= 100) {
                        return amount
                    }
                }
            }
        }

        return null
    }

    /**
     * 가게명/출처명 정리
     */
    private fun cleanStoreName(name: String): String {
        var cleaned = name.trim()
        cleaned = CLEAN_CORP_PATTERN.replace(cleaned, "")
        cleaned = CLEAN_SPECIAL_CHAR_PATTERN.replace(cleaned, "")
        return cleaned.trim()
    }
}
