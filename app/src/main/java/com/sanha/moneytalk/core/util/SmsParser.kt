package com.sanha.moneytalk.core.util

import android.util.Log
import com.sanha.moneytalk.core.model.SmsAnalysisResult
import java.text.SimpleDateFormat
import java.util.*

/**
 * SMS 파싱 유틸리티
 *
 * 카드 결제 문자에서 지출 정보를 추출하는 로컬 파서입니다.
 * Claude/Gemini API 없이 정규식과 키워드 매칭으로 SMS를 분석합니다.
 *
 * 지원 기능:
 * - 카드 결제 문자 판별 (광고/안내 문자 제외)
 * - 금액 추출 (다양한 형식 지원: "원" 단위, 줄바꿈 숫자 등)
 * - 가게명 추출 (KB 등 특수 형식 포함)
 * - 날짜/시간 추출
 * - 카테고리 추론 (키워드 기반)
 * - 카드사 판별
 *
 * 지원 카드사:
 * KB국민, 신한, 삼성, 현대, 롯데, 하나, 우리, NH농협, BC, 씨티,
 * 카카오뱅크, 토스, 케이뱅크 등 주요 국내 카드사
 */
object SmsParser {

    // ========================
    // 키워드 정의 (모두 lowercase, contains 비교이므로 최소 대표 키워드만 유지)
    // ========================

    /**
     * 사용자 정의 제외 키워드 (DB에서 로드)
     * HomeViewModel에서 SMS 동기화 시작 전에 setUserExcludeKeywords()로 설정
     */
    private var userExcludeKeywords: Set<String> = emptySet()

    /**
     * 사용자 정의 제외 키워드 설정 (DB 연동용)
     * @param keywords lowercase로 변환된 키워드 Set
     */
    fun setUserExcludeKeywords(keywords: Set<String>) {
        userExcludeKeywords = keywords
    }

    /** 카드사 식별 키워드 목록 (순서 보존 — extractCardName에서 첫 매칭 반환용) */
    private val cardKeywords = listOf(
        // KB국민: "kb"가 "KB국민","KB카드","KB체크" 등 모두 매칭
        "kb", "국민", "노리",
        // 신한: "신한"이 "신한카드","신한체크","신한은행" 모두 매칭
        "신한", "sol", "쏠",
        // 삼성
        "삼성",
        // 현대
        "현대",
        // 롯데
        "롯데",
        // 하나
        "하나",
        // 우리
        "우리",
        // NH농협: "nh"가 "NH카드","NH체크" 모두 매칭
        "nh", "농협",
        // BC: "bc"가 "BC카드" 매칭, "비씨"가 "비씨카드" 매칭
        "bc", "비씨",
        // 씨티
        "씨티", "시티", "citi",
        // 카카오: "카카오"가 "카카오뱅크","카카오페이" 모두 매칭
        "카카오", "카뱅",
        // 토스: "토스"가 "토스뱅크","토스카드" 모두 매칭
        "토스",
        // 케이뱅크
        "케이뱅크", "k뱅크",
        // IBK기업은행: "ibk"가 "IBK" 매칭, "기업"이 "기업은행","기업카드" 매칭
        "ibk", "기업",
        // SC제일은행: "sc"/"제일"은 오탐 위험 → "sc제일","제일은행"으로 유지
        "sc제일", "제일은행",
        // 수협
        "수협",
        // 지방은행
        "광주은행", "kjb", "전북은행", "jb", "경남은행", "bnk", "부산은행", "대구은행", "dgb",
        // 기타 금융기관
        "새마을", "mg", "신협", "kfcc", "우체국", "우정", "post", "저축은행", "ok저축",
        // 공통
        "체크카드", "신용카드", "선불", "후불"
    )

    /**
     * 카드사 키워드 Set (isCardPaymentSms/isIncomeSms의 any{} 검색용)
     * List와 동일 내용이지만 Set 자료구조로 중복 제거 + 순회 최적화
     */
    private val cardKeywordsSet: Set<String> = cardKeywords.toSet()

    /** 결제 관련 키워드 (결제 문자 판별용) */
    private val paymentKeywords = listOf(
        "결제", "승인", "사용", "출금", "이용"
    )

    /** 수입 관련 키워드 (입금 문자 판별용) */
    private val incomeKeywords = listOf(
        "입금", "이체입금", "급여", "월급", "보너스", "상여",
        "환급", "정산", "송금", "받으셨습니다", "입금되었습니다",
        "자동이체입금", "무통장입금", "계좌입금"
    )

    /** 수입 제외 키워드 (자동이체 출금 등 제외) */
    private val incomeExcludeKeywords = listOf(
        "자동이체출금", "출금예정", "결제예정", "납부",
        "보험료", "카드대금", "통신료", "공과금"
    )

    /**
     * 기본 제외 키워드 (광고/안내 문자 필터링용)
     * contains 비교이므로 "광고"만 있으면 "[광고]","(광고)" 모두 매칭
     */
    private val excludeKeywords = listOf(
        "광고",       // "[광고]","(광고)" 등 모두 포함
        "홍보", "이벤트", "혜택안내", "포인트 적립",
        "명세서", "청구서", "이용대금",
        "결제금액",   // 카드사 결제예정 금액 안내
        "카드대금",   // 카드 대금 결제/이체 안내
        "결제대금",   // 카드 결제대금 안내
        "청구금액",   // 카드사 청구금액 안내
        "출금 예정",  // 자동이체 출금 예정 안내
        "출금예정",   // 띄어쓰기 없는 버전
        "퇴직"        // 퇴직연금 안내 문자 제외
    )

    /** SMS 최대 글자수 (일반 결제 SMS는 40~100자, 안내/광고성은 100자 이상) */
    private const val MAX_SMS_LENGTH = 100

    // ========================
    // 사전 컴파일 Regex 상수 (매 호출마다 재컴파일 방지)
    // ========================
    private val AMOUNT_PATTERN_WITH_WON = Regex("""[\d,]+원""")
    private val AMOUNT_PATTERN_NUMBER_ONLY = Regex("""\n[\d,]{3,}\n""")
    private val AMOUNT_EXTRACT_WITH_WON = Regex("""([\d,]+)원(?![가-힣])""")
    private val PURE_NUMBER_PATTERN = Regex("""[\d,]+""")
    private val AMOUNT_WON_HANGUL_PATTERN = Regex(""".*\d+원[가-힣]+.*""")
    private val FROM_PATTERN = Regex("""([가-힣a-zA-Z0-9]+)(님)?으?로부터""")
    private val DEPOSIT_PATTERN = Regex("""입금\s*([가-힣a-zA-Z0-9]{2,10})|([가-힣a-zA-Z0-9]{2,10})\s*입금""")
    private val DATE_PATTERN_SLASH = Regex("""(\d{1,2})[/.-](\d{1,2})""")
    private val DATE_PATTERN_KOREAN = Regex("""(\d{1,2})월\s*(\d{1,2})일""")
    private val TIME_PATTERN = Regex("""(\d{1,2}):(\d{2})""")
    // 가게명 추출용 Regex
    private val STORE_CARD_NUMBER_PATTERN = Regex("""[\d*]+""")
    private val STORE_DATETIME_PATTERN = Regex("""\d{1,2}[/.-]\d{1,2}\s+\d{1,2}:\d{2}""")
    private val STORE_BRACKET_PATTERN = Regex("""\[.+\]""")
    private val STORE_AMOUNT_THEN_TIME_PATTERN = Regex("""[\d,]+원\s*\((?:일시불|\d+개월)\)\s*\d{1,2}[/.-]\d{1,2}\s+\d{1,2}:\d{2}\s+(.+)$""")
    private val STORE_TIME_BEFORE_PATTERN = Regex("""(\d{1,2}:\d{2})\s*(.+?)[\s]*[\d,]+원""")
    private val STORE_BEFORE_AMOUNT_PATTERN = Regex("""(.+?)\s*([\d,]+)원""")
    private val STORE_AFTER_AMOUNT_PATTERN = Regex("""[\d,]+원\s*(.+?)(?:승인|결제|사용|일시불|할부|\s*$)""")
    private val STORE_SPLIT_PATTERN = Regex("""[\s\[\]()\/,\n]+""")
    private val STORE_WORD_SPLIT_PATTERN = Regex("""[\s\[\]()\/\n]+""")
    // cleanStoreName용 Regex
    private val CLEAN_CORP_PATTERN = Regex("""\(주\)|\(유\)|\(사\)|\(재\)""")
    private val CLEAN_SPECIAL_CHAR_PATTERN = Regex("""^[^\w가-힣]+|[^\w가-힣]+$""")
    private val CLEAN_NUMBER_WON_PATTERN = Regex("""^\d+원""")
    // isValidStoreName용 Regex
    private val VALID_NUMBER_ONLY_PATTERN = Regex("""[\d,.:]+""")
    private val VALID_DATE_PATTERN = Regex("""\d{1,2}[/.-]\d{1,2}""")
    private val VALID_TIME_PATTERN = Regex("""\d{1,2}:\d{2}""")
    private val VALID_MASKED_NAME_PATTERN = Regex("""[가-힣]\*+[가-힣]?""")

    /**
     * 카테고리 매핑 (가게명 키워드 기반)
     * 뱅크샐러드 스타일의 세분화된 카테고리 분류
     */
    private val categoryKeywords = mapOf(
        // 식비 (음식 관련 전부 → "식비"로 통합)
        "식비" to listOf(
            // 고기
            "푸줏간", "정육", "고기", "삼겹살", "갈비", "한우", "소고기", "돼지고기",
            // 일식
            "초밥", "스시", "사시미", "라멘", "우동", "돈까스", "일식", "이자카야",
            "백소정", "스시로", "쿠라스시",
            // 중식
            "짜장", "짬뽕", "중국집", "중식", "마라탕", "훠궈",
            // 한식
            "한식", "찌개", "탕", "냉면", "비빔밥", "국밥", "설렁탕", "갈비탕",
            "김치찌개", "된장찌개", "부대찌개",
            // 치킨
            "치킨", "BBQ", "교촌", "BHC", "굽네", "네네", "푸라닭", "호식이",
            // 피자
            "피자", "도미노", "피자헛", "미스터피자", "파파존스",
            // 패스트푸드
            "맥도날드", "버거킹", "KFC", "롯데리아", "맘스터치", "서브웨이",
            // 분식
            "김밥", "분식", "떡볶이", "라면", "국수",
            // 편의점
            "편의점", "GS25", "CU", "세븐일레븐", "이마트24", "미니스톱",
            // 마트 (식료품 중심)
            "이마트", "홈플러스", "롯데마트", "코스트코", "트레이더스",
            "마트", "하나로", "농협마트"
        ),
        // 카페
        "카페" to listOf(
            "스타벅스", "투썸", "이디야", "커피빈", "탐앤탐스", "할리스",
            "메가커피", "컴포즈", "빽다방", "더벤티", "파스쿠찌",
            "카페", "커피",
            // 베이커리
            "베이커리", "빵집", "제과", "던킨", "크리스피", "파리바게뜨", "뚜레쥬르",
            // 아이스크림/디저트
            "배스킨라빈스", "나뚜루", "하겐다즈", "설빙", "아이스크림", "빙수"
        ),
        // 교통
        "교통" to listOf(
            "택시", "카카오T", "타다", "우버",
            "버스", "지하철", "KTX", "SRT", "코레일", "기차",
            "주유소", "SK에너지", "GS칼텍스", "현대오일", "S-OIL", "알뜰주유",
            "하이패스", "톨게이트", "고속도로", "주차", "파킹"
        ),
        // 쇼핑
        "쇼핑" to listOf(
            // 온라인쇼핑
            "쿠팡", "11번가", "G마켓", "옥션", "위메프", "티몬",
            "네이버쇼핑", "SSG", "롯데ON", "현대Hmall",
            // 패션
            "무신사", "지그재그", "에이블리", "29CM", "W컨셉",
            "유니클로", "자라", "H&M",
            // 뷰티
            "올리브영", "롭스", "화장품", "뷰티",
            // 생활용품
            "다이소", "아트박스", "이케아", "오늘의집"
        ),
        // 구독
        "구독" to listOf(
            "넷플릭스", "유튜브", "스포티파이", "멜론", "지니", "플로", "바이브",
            "왓챠", "웨이브", "티빙", "시즌", "쿠팡플레이", "디즈니플러스",
            "애플뮤직", "애플TV", "아마존", "프라임",
            "구독", "정기결제", "자동결제", "멤버십"
        ),
        // 의료/건강
        "의료/건강" to listOf(
            "병원", "의원", "클리닉", "치과", "안과", "피부과", "내과", "외과",
            "약국", "약"
        ),
        // 운동
        "운동" to listOf(
            "헬스", "피트니스", "짐", "요가", "필라테스", "PT"
        ),
        // 문화/여가
        "문화/여가" to listOf(
            "CGV", "메가박스", "롯데시네마", "영화관", "영화",
            "놀이공원", "에버랜드", "롯데월드", "키자니아",
            "노래방", "PC방", "당구장", "볼링장", "찜질방",
            "여행", "호텔", "펜션", "에어비앤비", "야놀자", "여기어때",
            "티켓", "공연", "뮤지컬", "콘서트", "전시"
        ),
        // 교육
        "교육" to listOf(
            "학원", "학습", "교육", "인강", "클래스101", "패스트캠퍼스",
            "책", "서점", "교보문고", "영풍문고", "알라딘", "예스24"
        ),
        // 생활
        "생활" to listOf(
            "통신", "SKT", "KT", "LG유플러스", "알뜰폰",
            "전기", "가스", "수도", "관리비", "공과금",
            "미용실", "헤어", "네일", "왁싱"
        ),
        // 배달 (식비의 소 카테고리)
        "배달" to listOf(
            "배달의민족", "요기요", "쿠팡이츠", "배민", "위메프오", "땡겨요", "배달"
        ),
        // 보험
        "보험" to listOf(
            "보험", "보험료"
        )
    )

    /**
     * 카테고리 키워드 사전 lowercase 캐시
     * inferCategory()에서 매번 keyword.lowercase() 호출 방지 (250+회 문자열 생성 제거)
     */
    private val categoryKeywordsLower: Map<String, List<String>> = categoryKeywords.mapValues { (_, keywords) ->
        keywords.map { it.lowercase() }
    }

    // ========================
    // 공개 파싱 메소드
    // ========================

    /**
     * SMS 유형 판별 결과
     *
     * @property isPayment 카드 결제 문자 여부
     * @property isIncome 입금 문자 여부
     */
    data class SmsTypeResult(
        val isPayment: Boolean = false,
        val isIncome: Boolean = false
    )

    /**
     * SMS 유형을 한 번의 lowercase()로 동시 판별 (성능 최적화)
     *
     * isCardPaymentSms()와 isIncomeSms()를 개별 호출하면 message.lowercase()가
     * 2번 실행되고, 공통 키워드 체크(excludeKeywords, cardKeywords)도 중복됩니다.
     * 이 메서드는 lowercase 1회 + 공통 체크 1회로 통합하여 처리합니다.
     *
     * 2만 건 기준: lowercase() 2만회 절감 + 키워드 스캔 중복 제거 → ~15-20% 루프 시간 단축
     *
     * @param message SMS 본문
     * @return SmsTypeResult (isPayment, isIncome 동시 반환)
     */
    fun classifySmsType(message: String): SmsTypeResult {
        // 빈 메시지 조기 반환
        if (message.isBlank()) return SmsTypeResult()

        // 공통: lowercase 1회만 수행
        val msgLower = message.lowercase()

        // 공통: 제외 키워드 체크 (지출/수입 모두 제외)
        if (excludeKeywords.any { msgLower.contains(it) } ||
            userExcludeKeywords.any { msgLower.contains(it) }) {
            return SmsTypeResult()
        }

        // 공통: 금융기관 키워드 (지출=카드사, 수입=은행 — 같은 키워드 세트)
        val hasCardKeyword = cardKeywordsSet.any { msgLower.contains(it) }
        if (!hasCardKeyword) return SmsTypeResult()

        // 공통: 금액 패턴 (사전 컴파일된 Regex)
        val hasAmount = AMOUNT_PATTERN_WITH_WON.containsMatchIn(message) || AMOUNT_PATTERN_NUMBER_ONLY.containsMatchIn(message)
        if (!hasAmount) return SmsTypeResult()

        // ===== 지출 판별 =====
        // 글자수 제한 (안내/광고성 SMS 필터링, 지출만 적용)
        val isPayment = if (message.length <= MAX_SMS_LENGTH) {
            val hasPaymentKeyword = paymentKeywords.any { msgLower.contains(it) }
            hasPaymentKeyword
        } else {
            false
        }

        // 지출이면 수입 체크 불필요 (동일 SMS가 지출+수입 동시에 해당될 수 없음)
        if (isPayment) return SmsTypeResult(isPayment = true)

        // ===== 수입 판별 =====
        val hasIncomeExclude = incomeExcludeKeywords.any { msgLower.contains(it) }
        if (hasIncomeExclude) return SmsTypeResult()

        val hasIncomeKeyword = incomeKeywords.any { msgLower.contains(it) }
        val hasPaymentKeyword = paymentKeywords.any { msgLower.contains(it) }
        val isIncome = hasIncomeKeyword && !hasPaymentKeyword

        if (isIncome) {
            Log.d("SmsParser", "수입 SMS 감지: ${message.take(50)}...")
        }

        return SmsTypeResult(isIncome = isIncome)
    }

    /**
     * 카드 결제 문자인지 판별
     *
     * 조건:
     * 1. 광고/안내 키워드가 없어야 함
     * 2. 카드사 키워드가 있어야 함
     * 3. 결제 관련 키워드가 있어야 함
     * 4. 금액 패턴이 있어야 함
     *
     * @param message SMS 본문
     * @return 카드 결제 문자이면 true
     */
    fun isCardPaymentSms(message: String): Boolean {
        // 빈 메시지 조기 반환
        if (message.isBlank()) return false

        // 글자수 제한 (안내/광고성 SMS 필터링)
        if (message.length > MAX_SMS_LENGTH) {
            return false
        }

        val msgLower = message.lowercase()

        // 기본 제외 키워드 + 사용자 제외 키워드 (리스트 합치기 대신 별도 체크)
        if (excludeKeywords.any { msgLower.contains(it) } ||
            userExcludeKeywords.any { msgLower.contains(it) }) {
            Log.d("sanha","제외 키워드 ${message.take(50)}")
            return false
        }

        // 카드사 키워드가 있고, 결제 관련 키워드가 있으면 true (find→any 최적화)
        val hasCardKeyword = cardKeywordsSet.any { msgLower.contains(it) }
        val hasPaymentKeyword = paymentKeywords.any { msgLower.contains(it) }

        // 금액 패턴 확인 (사전 컴파일된 Regex 상수 사용)
        val hasAmount = AMOUNT_PATTERN_WITH_WON.containsMatchIn(message) || AMOUNT_PATTERN_NUMBER_ONLY.containsMatchIn(message)

        return hasCardKeyword && hasPaymentKeyword && hasAmount
    }

    /**
     * 수입(입금) 문자인지 판별
     *
     * 다음 조건을 만족하면 수입 문자로 판별:
     * 1. 제외 키워드(광고 등)가 없음
     * 2. 은행 키워드가 있음
     * 3. 입금 관련 키워드가 있음
     * 4. 금액 패턴이 있음
     * 5. 출금/결제 관련 키워드가 없음 (수입 제외 키워드 체크)
     *
     * @param message SMS 본문
     * @return 수입 문자이면 true
     */
    fun isIncomeSms(message: String): Boolean {
        // 빈 메시지 조기 반환 (isCardPaymentSms와 일관성)
        if (message.isBlank()) return false

        val msgLower = message.lowercase()

        // 기본 제외 키워드 + 사용자 제외 키워드 (리스트 합치기 대신 별도 체크)
        if (excludeKeywords.any { msgLower.contains(it) } ||
            userExcludeKeywords.any { msgLower.contains(it) }) {
            return false
        }

        // 수입 제외 키워드가 있으면 false (자동이체 출금 등)
        if (incomeExcludeKeywords.any { msgLower.contains(it) }) {
            return false
        }

        // 은행 키워드 확인
        val hasBankKeyword = cardKeywordsSet.any { msgLower.contains(it) }

        // 입금 관련 키워드 확인
        val hasIncomeKeyword = incomeKeywords.any { msgLower.contains(it) }

        // 금액 패턴 확인 (사전 컴파일된 Regex 상수 사용)
        val hasAmount = AMOUNT_PATTERN_WITH_WON.containsMatchIn(message) || AMOUNT_PATTERN_NUMBER_ONLY.containsMatchIn(message)

        // 지출(결제) 관련 키워드가 있으면 수입으로 판단하지 않음
        val hasPaymentKeyword = paymentKeywords.any { msgLower.contains(it) }

        val isIncome = hasBankKeyword && hasIncomeKeyword && hasAmount && !hasPaymentKeyword
        if (isIncome) {
            Log.d("SmsParser", "수입 SMS 감지: ${message.take(50)}...")
        }

        return isIncome
    }

    /**
     * 수입 SMS에서 금액 추출
     *
     * @param message SMS 본문
     * @return 입금 금액 (추출 실패 시 0)
     */
    fun extractIncomeAmount(message: String): Int {
        // 일반 금액 추출 로직 재사용
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
        // 패턴 1: "OOO님으로부터" 또는 "OOO으로부터" (사전 컴파일 Regex)
        FROM_PATTERN.find(message)?.let {
            return it.groupValues[1]
        }

        // 패턴 2: "입금 OOO" 또는 "OOO 입금" (사전 컴파일 Regex)
        DEPOSIT_PATTERN.find(message)?.let {
            val source = it.groupValues[1].ifEmpty { it.groupValues[2] }
            if (source.isNotBlank() && !incomeKeywords.any { keyword -> source == keyword }) {
                return source
            }
        }

        return ""
    }

    /**
     * SMS에서 카드사명 추출
     * @param message SMS 본문
     * @return 카드사명 (매칭 안 되면 "기타")
     */
    fun extractCardName(message: String): String {
        val msgLower = message.lowercase()
        for (keyword in cardKeywords) {
            if (msgLower.contains(keyword)) {
                return keyword
            }
        }
        return "기타"
    }

    /**
     * SMS에서 결제 금액 추출
     *
     * 지원 형식:
     * 1. KB 스타일: "체크카드출금\n금액\n잔액..." (원 단위 없음)
     * 2. 일반 형식: "금액원" (예: 15,000원)
     * 3. 줄바꿈 숫자: 순수 숫자만 있는 줄
     *
     * @param message SMS 본문
     * @return 금액 (100원 이상), 추출 실패 시 null
     */
    fun extractAmount(message: String): Int? {
        val lines = message.split("\n").map { it.trim() }

        // KB 스타일 우선 처리 - "체크카드출금" 또는 "출금" 다음 줄의 숫자
        // 예: 체크카드출금\n11,940\n잔액45,091
        for (i in lines.indices) {
            if (lines[i].contains("체크카드출금") || lines[i] == "출금") {
                // 바로 다음 줄이 금액
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1]
                    // 순수 숫자 (콤마 포함 가능) - 사전 컴파일 Regex
                    if (nextLine.matches(PURE_NUMBER_PATTERN)) {
                        val amount = nextLine.replace(",", "").toIntOrNull()
                        if (amount != null && amount >= 100) {
                            return amount
                        }
                    }
                }
            }
        }

        // 패턴1: 숫자+원 (예: 2,800원) - 사전 컴파일 Regex
        val matchWithWon = AMOUNT_EXTRACT_WITH_WON.find(message)
        if (matchWithWon != null) {
            val amount = matchWithWon.groupValues[1].replace(",", "").toIntOrNull()
            if (amount != null && amount >= 100) {
                return amount
            }
        }

        // 패턴2: 줄바꿈 사이의 금액 (잔액 제외) - 사전 컴파일 Regex
        for (i in lines.indices) {
            val line = lines[i]
            // 잔액 라인은 건너뛰기
            if (line.startsWith("잔액")) continue
            // 가게명 패턴 제외 (숫자+원+한글)
            if (line.matches(AMOUNT_WON_HANGUL_PATTERN)) continue
            // 순수 숫자 또는 콤마 포함 숫자 (100 이상) - 중복 replace 제거
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
     * SMS 고유 ID 생성
     * 중복 저장 방지를 위해 발신번호 + 시간 + 본문 해시로 구성
     */
    fun generateSmsId(address: String, body: String, date: Long): String {
        return "${address}_${date}_${body.hashCode()}"
    }

    /**
     * SMS 전체 파싱 (메인 메소드)
     *
     * 로컬 정규식 기반으로 SMS에서 모든 정보를 추출합니다.
     * API 호출 없이 빠르게 처리할 수 있습니다.
     *
     * @param message SMS 본문
     * @param smsTimestamp SMS 수신 시간 (밀리초)
     * @return SmsAnalysisResult (금액, 가게명, 카테고리, 날짜시간, 카드명)
     */
    fun parseSms(message: String, smsTimestamp: Long): SmsAnalysisResult {
        val amount = extractAmount(message) ?: 0
        val cardName = extractCardName(message)
        val storeName = extractStoreName(message)
        val dateTime = extractDateTime(message, smsTimestamp)
        val category = inferCategory(storeName, message)

        return SmsAnalysisResult(
            amount = amount,
            storeName = storeName,
            category = category,
            dateTime = dateTime,
            cardName = cardName
        )
    }

    // ========================
    // 내부 파싱 헬퍼
    // ========================

    /** 제외할 가게명 패턴 (URL, 발신 표시, 기타 비가게명) */
    private val excludeStorePatterns = listOf(
        "Web발신", "web발신", "WEB발신", "국외발신", "국제발신", "해외발신",
        "ltcard", "card.kr", ".kr", ".com", ".co.kr", "http", "www",
        "기준", "누적", "잔액", "한도", "가용",
        "일시불", "할부", "취소", "승인", "결제", "출금", "사용", "입금", "이체",
        "체크", "신용", "님", "고객", "회원",
        "원", "건", "월", "일", "시", "분",
        "SMS", "MMS", "안내", "알림", "통지",
        "민생회복", "입출통지"
    )

    /** 의미 없는 가게명 패턴 (랜덤 코드, 날짜 형식 등) */
    private val invalidStorePatterns = listOf(
        // KB] 날짜시간 형식 (예: KB]08/07 11:28)
        Regex("""^KB\]\d{2}/\d{2}\s+\d{2}:\d{2}$"""),
        // 랜덤 문자열 (영문+숫자 혼합, 대소문자 혼합, 7자 이하)
        Regex("""^[a-zA-Z0-9]{5,8}$"""),
        // 숫자로 끝나는 보험/금융 코드 (예: 삼성화08003, 현대해08036, 메리츠080071)
        Regex("""^.{2,4}(화|해|츠)\d{5,6}$"""),
        // 카드번호 형식 (예: 롯데카드2508)
        Regex("""^.+카드\d{4}$"""),
        // 월 입출통지 형식 (예: 07월입출통지)
        Regex("""^\d{2}월.+$""")
    )

    /**
     * SMS에서 가게명 추출
     *
     * 한국 카드사 SMS 형식에 맞춰 가게명을 추출합니다.
     *
     * 지원 형식:
     * 1. KB 스타일: 줄바꿈으로 구분된 형식 (체크카드출금 위 줄 탐색)
     *    [KB]
     *    02/05 22:47
     *    801302**775 (카드번호)
     *    *60원캐쉬백주식회사 (가게명)
     *    체크카드출금
     *    11,940 (금액)
     *    잔액45,091
     *
     * 2. 일반 형식: [카드사] MM/DD HH:mm 가게명 금액원 승인
     * 3. 금액 앞 형식: 가게명 금액원
     * 4. 금액 뒤 형식: 금액원 가게명 승인
     *
     * @param message SMS 본문
     * @return 가게명 (추출 실패 시 "결제")
     */
    fun extractStoreName(message: String): String {
        // KB 스타일 패턴 - "체크카드출금" 위 줄들을 탐색
        val lines = message.split("\n").map { it.trim() }
        for (i in lines.indices) {
            if (lines[i].contains("체크카드출금") || lines[i] == "출금") {
                // 위로 올라가면서 유효한 가게명 찾기
                for (j in (i - 1) downTo 0) {
                    val potentialStore = lines[j]

                    // 카드번호 패턴 제외 (예: 801302**775) - 사전 컴파일 Regex
                    if (potentialStore.contains("**") || potentialStore.matches(STORE_CARD_NUMBER_PATTERN)) {
                        continue
                    }

                    // 날짜/시간 패턴 제외 (예: 02/05 22:47) - 사전 컴파일 Regex
                    if (potentialStore.matches(STORE_DATETIME_PATTERN)) {
                        continue
                    }

                    // [KB] 같은 카드사 표시 제외 - 사전 컴파일 Regex
                    if (potentialStore.matches(STORE_BRACKET_PATTERN)) {
                        continue
                    }

                    // 빈 줄 제외
                    if (potentialStore.isBlank()) {
                        continue
                    }

                    // 유효한 가게명인지 확인
                    val cleanStore = cleanStoreName(potentialStore)
                    if (cleanStore.length >= 2) {
                        return cleanStore
                    }
                }
            }
        }

        // 패턴 0: 금액+일시불/할부+날짜시간 뒤에 가게명이 오는 경우 - 사전 컴파일 Regex
        val amountThenTimeMatch = STORE_AMOUNT_THEN_TIME_PATTERN.find(message)
        if (amountThenTimeMatch != null) {
            val potentialStore = amountThenTimeMatch.groupValues[1].trim()
            val cleanStore = cleanStoreName(potentialStore)
            if (isValidStoreName(cleanStore)) {
                return cleanStore
            }
        }

        // 패턴 1: 시간 뒤에 가게명이 오는 경우 (가장 흔함) - 사전 컴파일 Regex
        val timeMatch = STORE_TIME_BEFORE_PATTERN.find(message)
        if (timeMatch != null) {
            val potentialStore = timeMatch.groupValues[2].trim()
            val cleanStore = cleanStoreName(potentialStore)
            if (isValidStoreName(cleanStore)) {
                return cleanStore
            }
        }

        // 패턴 2: 금액 바로 앞에 가게명이 오는 경우 - 사전 컴파일 Regex
        val beforeMatch = STORE_BEFORE_AMOUNT_PATTERN.find(message)
        if (beforeMatch != null) {
            val beforeText = beforeMatch.groupValues[1]
            // 마지막 공백 이후 단어 추출
            val words = beforeText.split(STORE_WORD_SPLIT_PATTERN).filter { it.isNotBlank() }
            for (i in words.indices.reversed()) {
                val word = cleanStoreName(words[i])
                if (isValidStoreName(word) && !cardKeywords.any { word.lowercase().contains(it) }) {
                    return word
                }
            }
        }

        // 패턴 3: 금액 뒤에 가게명이 오는 경우 - 사전 컴파일 Regex
        val afterMatch = STORE_AFTER_AMOUNT_PATTERN.find(message)
        if (afterMatch != null) {
            val potentialStore = afterMatch.groupValues[1].trim()
            val cleanStore = cleanStoreName(potentialStore)
            if (isValidStoreName(cleanStore)) {
                return cleanStore
            }
        }

        // 패턴 4: 전체 메시지에서 유효한 가게명 추출 - 사전 컴파일 Regex
        val words = message.split(STORE_SPLIT_PATTERN)
            .map { cleanStoreName(it) }
            .filter { isValidStoreName(it) && !cardKeywords.any { keyword -> it.lowercase().contains(keyword) } }

        return words.firstOrNull() ?: "결제"
    }

    /**
     * 가게명 정리 (불필요한 문자 제거)
     * - 앞뒤 특수문자 제거
     * - 앞쪽 "숫자원" 패턴 제거 (예: "*60원캐쉬백주식회사" -> "캐쉬백주식회사")
     * - 최대 15자로 제한
     */
    private fun cleanStoreName(name: String): String {
        var cleaned = name.trim()
        // 법인 표기 제거 (예: "(주)", "(유)", "(사)", "(재)") - 사전 컴파일 Regex
        cleaned = cleaned.replace(CLEAN_CORP_PATTERN, "")
        // 앞뒤 특수문자 제거 - 사전 컴파일 Regex
        cleaned = cleaned.replace(CLEAN_SPECIAL_CHAR_PATTERN, "")
        // 앞쪽의 "숫자원" 패턴 제거 (예: "60원캐쉬백" -> "캐쉬백") - 사전 컴파일 Regex
        cleaned = cleaned.replace(CLEAN_NUMBER_WON_PATTERN, "")
        // 최대 15자로 제한
        return cleaned.take(15)
    }

    /**
     * 유효한 가게명인지 검증
     * - 2자 이상
     * - 숫자로만 구성되지 않음
     * - 날짜/시간 패턴이 아님
     * - 제외 패턴에 해당하지 않음
     * - 의미 없는 패턴(랜덤 코드, 보험/금융 코드 등)이 아님
     */
    private fun isValidStoreName(name: String): Boolean {
        if (name.isBlank() || name.length < 2) return false

        // 숫자로만 구성된 경우 제외 - 사전 컴파일 Regex
        if (name.matches(VALID_NUMBER_ONLY_PATTERN)) return false

        // 날짜/시간 패턴 제외 - 사전 컴파일 Regex
        if (name.matches(VALID_DATE_PATTERN)) return false
        if (name.matches(VALID_TIME_PATTERN)) return false

        // 마스킹된 이름 패턴 제외 (예: 하*현, 김*수, 이**) - 사전 컴파일 Regex
        if (name.matches(VALID_MASKED_NAME_PATTERN)) return false

        // 제외 패턴에 해당하는 경우 제외
        for (pattern in excludeStorePatterns) {
            if (name.equals(pattern, ignoreCase = true) || name.contains(pattern, ignoreCase = true)) {
                return false
            }
        }

        // 의미 없는 패턴에 해당하는 경우 제외
        for (pattern in invalidStorePatterns) {
            if (pattern.matches(name)) {
                Log.d("SmsParser", "의미 없는 가게명 패턴 제외: $name")
                return false
            }
        }

        return true
    }

    /** 텍스트에서 가게명 후보 추출 (레거시 - 호환성 유지) */
    private fun extractStoreNameFromText(text: String, excludeKeywords: List<String>): String {
        if (text.isBlank()) return ""

        val parts = text.split(STORE_SPLIT_PATTERN)
            .map { cleanStoreName(it) }
            .filter { isValidStoreName(it) && !excludeKeywords.any { keyword -> it.contains(keyword) } }

        return parts.firstOrNull() ?: ""
    }

    /**
     * SMS에서 날짜/시간 추출
     *
     * SMS 본문에서 날짜와 시간 정보를 추출합니다.
     * 날짜 형식: MM/DD, MM-DD, MM.DD, M월 D일
     * 시간 형식: HH:mm
     *
     * 추출 실패 시 SMS 수신 시간을 기본값으로 사용합니다.
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

        // 날짜 추출 시도 (사전 컴파일 Regex)
        val dateMatch1 = DATE_PATTERN_SLASH.find(message)
        val dateMatch2 = DATE_PATTERN_KOREAN.find(message)

        if (dateMatch1 != null) {
            month = dateMatch1.groupValues[1].toIntOrNull() ?: month
            day = dateMatch1.groupValues[2].toIntOrNull() ?: day
        } else if (dateMatch2 != null) {
            month = dateMatch2.groupValues[1].toIntOrNull() ?: month
            day = dateMatch2.groupValues[2].toIntOrNull() ?: day
        }

        // 시간 추출 시도 (사전 컴파일 Regex)
        val timeMatch = TIME_PATTERN.find(message)
        if (timeMatch != null) {
            hour = timeMatch.groupValues[1].toIntOrNull() ?: hour
            minute = timeMatch.groupValues[2].toIntOrNull() ?: minute
        }

        // 유효성 검사 및 보정
        if (month < 1 || month > 12) month = calendar.get(Calendar.MONTH) + 1
        if (day < 1 || day > 31) day = calendar.get(Calendar.DAY_OF_MONTH)
        if (hour < 0 || hour > 23) hour = calendar.get(Calendar.HOUR_OF_DAY)
        if (minute < 0 || minute > 59) minute = calendar.get(Calendar.MINUTE)

        return String.format("%04d-%02d-%02d %02d:%02d", currentYear, month, day, hour, minute)
    }

    /**
     * 카테고리 추론 (키워드 기반)
     *
     * 가게명과 SMS 본문에서 키워드를 찾아 카테고리를 결정합니다.
     * categoryKeywords 맵에 정의된 키워드와 매칭합니다.
     *
     * @param storeName 가게명
     * @param message SMS 본문 (추가 키워드 탐색용)
     * @return 매칭된 카테고리 (없으면 "미분류")
     */
    fun inferCategory(storeName: String, message: String): String {
        val combinedText = "$storeName $message".lowercase()

        // 사전 lowercase 캐시 사용 (매번 keyword.lowercase() 호출 방지)
        for ((category, keywords) in categoryKeywordsLower) {
            for (keyword in keywords) {
                if (combinedText.contains(keyword)) {
                    return category
                }
            }
        }

        return "미분류"
    }
}
