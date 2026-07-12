package com.sanha.moneytalk.core.sms

import javax.inject.Inject
import javax.inject.Singleton

/**
 * ===== SMS 템플릿화 + 임베딩 생성 엔진 (Step 3) =====
 *
 * 역할:
 * 1. SMS 원본을 플레이스홀더 템플릿으로 변환 (templateize)
 *    예: "[KB]02/05 스타벅스 11,940원 승인" → "[KB]{DATE} {STORE} {AMOUNT}원 승인"
 *
 * 2. 템플릿에서 로컬 768차원 특징 벡터 생성 (embed)
 *
 * 템플릿화 이유:
 * - 같은 카드사/같은 형식의 SMS는 가게명/금액만 다름
 * - 변하는 부분을 플레이스홀더로 치환하면 "구조적 유사성"이 높아져
 *   벡터 유사도가 정확해짐 (스타벅스 결제든 카페 결제든 같은 KB 형식이면 유사)
 *
 * 호출 순서: SmsPipeline.batchEmbed() → [여기]
 *
 * 임베딩은 [SmsEmbeddingService]의 결정적 로컬 구현을 사용한다.
 */
@Singleton
class SmsTemplateEngine @Inject constructor(
    private val embeddingService: SmsEmbeddingService
) {

    // ===== 템플릿화 =====

    /**
     * SMS 본문을 플레이스홀더 템플릿으로 변환
     *
     * 치환 순서 (우선순위):
     * 1. 한 줄 SMS 가상 줄바꿈 전처리 (신한카드 등 구분자 기반 분리)
     * 2. 사용자 이름 마스킹 (예: "하*현" → "{USER_NAME}")
     * 3. 카드번호 마스킹 → {CARD_NUM} (예: "1234*567" → "{CARD_NUM}")
     * 4. 가게명 줄 → {STORE} (원본 텍스트에서 추출, 플레이스홀더 간섭 없음)
     * 5. 금액+원 → {AMOUNT}원 (예: "15,000원" → "{AMOUNT}원")
     * 6. 줄바꿈 사이 숫자 → {AMOUNT} (예: "\n15000\n" → "\n{AMOUNT}\n")
     * 7. 날짜 → {DATE} (예: "02/25" → "{DATE}")
     * 8. 시간 → {TIME} (예: "14:30" → "{TIME}")
     * 9. 잔액 → 잔액{BALANCE} (예: "잔액100,000" → "잔액{BALANCE}")
     *
     * 핵심 변경: 가게명 추출을 금액/날짜 치환보다 앞에서 수행.
     * 이유: 금액 치환 후 "{AMOUNT}원캐쉬백다이소" 같은 문자열에서
     *       "{"가 포함되어 isLikelyStoreName이 false 반환 → 가게명 추출 실패 방지.
     *
     * @param smsBody 원본 SMS 본문
     * @return 플레이스홀더로 치환된 템플릿 텍스트
     */
    fun templateize(smsBody: String): String {
        // 1. 한 줄 SMS → 가상 줄바꿈 전처리
        var template = preprocessSingleLine(smsBody)

        // 2. 사용자 이름 마스킹 (예: "하*현님" → "{USER_NAME}님")
        template = template.replace(USER_NAME_PATTERN, "{USER_NAME}")

        // 3. 카드번호 마스킹 치환 (가게명 추출 전에 처리 — "1234*567"이 가게명으로 오인 방지)
        template = template.replace(CARD_NUM_PATTERN, "{CARD_NUM}")

        // 4. 가게명 줄 치환 (원본 텍스트 기반 — 금액/날짜 치환 전이므로 "{" 간섭 없음)
        val lines = template.split("\n")
        if (lines.size >= 4) {
            var storeReplaced = false
            val result = lines.map { line ->
                val trimmed = line.trim()
                if (!storeReplaced && isLikelyStoreName(trimmed)) {
                    storeReplaced = true
                    "{STORE}"
                } else {
                    line
                }
            }
            template = result.joinToString("\n")
        }

        // 5. 금액+원 치환
        template = template.replace(AMOUNT_WON_PATTERN, "{AMOUNT}원")
        // 6. 줄바꿈 사이 순수 숫자 치환
        template = template.replace(AMOUNT_NUMBER_PATTERN, "\n{AMOUNT}\n")
        // 7. 날짜 치환 (MM/DD, MM-DD, MM.DD)
        template = template.replace(DATE_PATTERN, "{DATE}")
        // 8. 시간 치환 (HH:mm)
        template = template.replace(TIME_PATTERN, "{TIME}")
        // 9. 잔액 치환
        template = template.replace(BALANCE_PATTERN, "잔액{BALANCE}")

        return template
    }

    // ===== 한 줄 SMS 가상 줄바꿈 전처리 =====

    /** 한 줄 SMS 구분자 패턴 (공백 포함 슬래시, 등) */
    private val SINGLE_LINE_DELIMITERS = Regex("""\s+/\s+""")

    /**
     * 한 줄 SMS에 가상 줄바꿈 삽입
     *
     * 신한카드 등 일부 카드사는 줄바꿈 없이 한 줄로 SMS를 보냄.
     * 예: "[신한] 02/05 스타벅스 11,940원 승인 / 잔액 100,000원"
     * → 줄바꿈이 없으면 lines.size < 4 → 가게명 추출 스킵됨.
     *
     * 구분자(" / ")를 줄바꿈으로 변환하여 다중 줄 SMS와 동일하게 처리.
     * 이미 줄바꿈이 있는 SMS는 그대로 반환.
     *
     * @param smsBody 원본 SMS 본문
     * @return 가상 줄바꿈이 삽입된 SMS (또는 원본 그대로)
     */
    private fun preprocessSingleLine(smsBody: String): String {
        if (smsBody.contains("\n")) return smsBody
        val result = smsBody.replace(SINGLE_LINE_DELIMITERS, "\n")
        return if (result != smsBody) result else smsBody
    }

    // ===== 정규식 (사전 컴파일) =====

    /** 사용자 이름 마스킹 패턴 (예: "하*현", "김*수") */
    private val USER_NAME_PATTERN = Regex("""[가-힣]\*[가-힣]""")

    /** 카드번호 마스킹 패턴 (예: "1234*567") */
    private val CARD_NUM_PATTERN = Regex("""\d+\*+\d+""")

    /** 금액+원 패턴 (예: "15,000원") */
    private val AMOUNT_WON_PATTERN = Regex("""[\d,]+원""")

    /** 줄바꿈 사이 순수 숫자 패턴 (예: "\n15000\n") */
    private val AMOUNT_NUMBER_PATTERN = Regex("""\n[\d,]{3,}\n""")

    /** 날짜 패턴 (MM/DD, MM-DD, MM.DD) */
    private val DATE_PATTERN = Regex("""\d{1,2}[/.-]\d{1,2}""")

    /** 시간 패턴 (HH:mm) */
    private val TIME_PATTERN = Regex("""\d{1,2}:\d{2}""")

    /** 잔액 패턴 (예: "잔액100,000") */
    private val BALANCE_PATTERN = Regex("""잔액[\d,]+""")

    // ===== 가게명 판별 =====

    /**
     * 구조적 키워드 — 가게명이 아닌 SMS 형식 구성 요소
     *
     * 이 키워드가 포함된 줄은 가게명 후보에서 제외됨.
     */
    private val structuralKeywords = setOf(
        "출금", "입금", "승인", "결제", "이체", "잔액",
        "[web발신]", "누적", "일시불", "할부", "체크카드", "해외승인",
        "캐쉬백", "캐시백", "님"
    )

    /**
     * 해당 줄이 가게명일 가능성이 있는지 판별
     *
     * 가게명이 아닌 조건 (false 반환):
     * - 빈 줄, 2자 미만, 20자 초과
     * - 플레이스홀더({...}) 포함
     * - 구조 키워드(출금, 승인, 캐쉬백 등) 포함
     * - 숫자+콤마만으로 구성 (금액)
     *
     * 가게명 후보 조건 (true 반환):
     * - 첫 글자가 한글/영문/괄호/별표
     */
    internal fun isLikelyStoreName(line: String): Boolean {
        if (line.isEmpty() || line.length < 2 || line.length > 20) return false
        if (line.contains("{")) return false
        if (structuralKeywords.any { line.lowercase().contains(it) }) return false
        if (line.all { it.isDigit() || it == ',' }) return false
        val firstChar = line.first()
        return firstChar.isLetter() || firstChar == '(' || firstChar == '*'
    }

    // ===== 임베딩 생성 =====

    /**
     * 배치 임베딩 생성
     *
     * 여러 템플릿의 로컬 특징 벡터를 생성한다.
     *
     * @param templates 템플릿화된 텍스트 목록 (최대 100건)
     * @return 각 텍스트의 임베딩 벡터 목록 (실패한 항목은 null)
     */
    suspend fun batchEmbed(templates: List<String>): List<List<Float>?> {
        return embeddingService.generateEmbeddings(templates)
    }
}
