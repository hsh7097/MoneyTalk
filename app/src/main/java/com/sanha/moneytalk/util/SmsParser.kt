package com.sanha.moneytalk.util

import com.sanha.moneytalk.data.remote.dto.SmsAnalysisResult
import java.text.SimpleDateFormat
import java.util.*

object SmsParser {

    // 카드사 키워드 목록
    private val cardKeywords = listOf(
        "KB국민", "국민카드", "KB카드",
        "신한", "신한카드",
        "삼성", "삼성카드",
        "현대", "현대카드",
        "롯데", "롯데카드",
        "하나", "하나카드",
        "우리", "우리카드",
        "NH", "농협", "NH카드",
        "BC", "BC카드",
        "씨티", "시티", "Citi",
        "카카오뱅크", "카카오페이",
        "토스", "토스뱅크",
        "케이뱅크"
    )

    // 결제 관련 키워드
    private val paymentKeywords = listOf(
        "결제", "승인", "사용", "출금", "이용"
    )

    // 제외할 키워드 (광고, 안내 등)
    private val excludeKeywords = listOf(
        "광고", "안내", "홍보", "이벤트", "혜택", "포인트 적립",
        "한도", "실적", "명세서", "청구", "결제일"
    )

    // 카테고리 매핑 (가게명 키워드 기반)
    private val categoryKeywords = mapOf(
        "식비" to listOf(
            "치킨", "피자", "맥도날드", "버거킹", "KFC", "롯데리아", "맘스터치",
            "BBQ", "교촌", "BHC", "굽네", "네네", "푸라닭",
            "김밥", "분식", "떡볶이", "라면", "우동", "국수",
            "한식", "중식", "일식", "양식", "분식", "죽",
            "배달의민족", "요기요", "쿠팡이츠", "배민",
            "식당", "레스토랑", "음식점", "밥집", "고기", "삼겹살", "갈비",
            "초밥", "돈까스", "찌개", "탕", "냉면", "비빔밥"
        ),
        "카페" to listOf(
            "스타벅스", "투썸", "이디야", "커피빈", "탐앤탐스", "할리스",
            "메가커피", "컴포즈", "빽다방", "더벤티", "파스쿠찌",
            "카페", "커피", "베이커리", "빵집", "제과", "던킨", "크리스피"
        ),
        "교통" to listOf(
            "택시", "카카오T", "타다", "우버",
            "버스", "지하철", "KTX", "SRT", "코레일", "기차",
            "주유소", "SK에너지", "GS칼텍스", "현대오일", "S-OIL", "알뜰주유",
            "하이패스", "톨게이트", "고속도로", "주차", "파킹"
        ),
        "쇼핑" to listOf(
            "쿠팡", "11번가", "G마켓", "옥션", "위메프", "티몬",
            "네이버쇼핑", "SSG", "롯데ON", "현대Hmall",
            "이마트", "홈플러스", "롯데마트", "코스트코", "트레이더스",
            "편의점", "GS25", "CU", "세븐일레븐", "이마트24", "미니스톱",
            "올리브영", "롭스", "다이소", "아트박스",
            "무신사", "지그재그", "에이블리", "29CM", "W컨셉"
        ),
        "구독" to listOf(
            "넷플릭스", "유튜브", "스포티파이", "멜론", "지니", "플로", "바이브",
            "왓챠", "웨이브", "티빙", "시즌", "쿠팡플레이", "디즈니플러스",
            "애플뮤직", "애플TV", "아마존", "프라임",
            "구독", "정기결제", "자동결제", "멤버십"
        ),
        "의료/건강" to listOf(
            "병원", "의원", "클리닉", "치과", "안과", "피부과", "내과", "외과",
            "약국", "약", "건강", "헬스", "피트니스", "짐", "요가", "필라테스"
        ),
        "문화/여가" to listOf(
            "CGV", "메가박스", "롯데시네마", "영화관", "영화",
            "놀이공원", "에버랜드", "롯데월드", "키자니아",
            "노래방", "PC방", "당구장", "볼링장", "찜질방",
            "여행", "호텔", "펜션", "에어비앤비", "야놀자", "여기어때",
            "티켓", "공연", "뮤지컬", "콘서트"
        ),
        "교육" to listOf(
            "학원", "학습", "교육", "인강", "클래스101", "패스트캠퍼스",
            "책", "서점", "교보문고", "영풍문고", "알라딘", "예스24"
        ),
        "생활" to listOf(
            "통신", "SKT", "KT", "LG유플러스", "알뜰폰",
            "전기", "가스", "수도", "관리비", "공과금",
            "보험", "세금", "이사", "택배", "세탁", "미용실", "헤어"
        )
    )

    /**
     * 카드 결제 문자인지 확인
     */
    fun isCardPaymentSms(message: String): Boolean {
        // 제외 키워드가 있으면 false
        if (excludeKeywords.any { message.contains(it) }) {
            return false
        }

        // 카드사 키워드가 있고, 결제 관련 키워드가 있으면 true
        val hasCardKeyword = cardKeywords.any { message.contains(it) }
        val hasPaymentKeyword = paymentKeywords.any { message.contains(it) }

        // 금액 패턴 확인 (숫자+원 또는 숫자+,+원)
        val amountPattern = Regex("""[\d,]+원""")
        val hasAmount = amountPattern.containsMatchIn(message)

        return hasCardKeyword && hasPaymentKeyword && hasAmount
    }

    /**
     * 문자에서 카드사 추출
     */
    fun extractCardName(message: String): String {
        for (keyword in cardKeywords) {
            if (message.contains(keyword)) {
                return keyword
            }
        }
        return "기타"
    }

    /**
     * 문자에서 금액 추출 (간단 파싱)
     */
    fun extractAmount(message: String): Int? {
        val amountPattern = Regex("""([\d,]+)원""")
        val match = amountPattern.find(message)
        return match?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
    }

    /**
     * SMS ID 생성 (중복 방지용)
     */
    fun generateSmsId(address: String, body: String, date: Long): String {
        return "${address}_${date}_${body.hashCode()}"
    }

    /**
     * SMS 메시지 전체 파싱 (로컬 정규식 기반)
     * Claude API 대신 로컬에서 처리
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

    /**
     * 가게명 추출
     * SMS 형식 분석하여 금액 근처의 가게명 추출
     */
    fun extractStoreName(message: String): String {
        // 불필요한 정보 제거
        val cleanKeywords = listOf(
            "일시불", "할부", "취소", "승인", "결제", "출금",
            "누적", "잔액", "체크", "신용", "님"
        )

        // 금액 패턴 - 금액 앞뒤로 가게명이 있을 가능성이 높음
        val amountPattern = Regex("""([\d,]+)원""")
        val amountMatch = amountPattern.find(message) ?: return "기타"

        // 금액 뒤에서 가게명 추출 시도
        val afterAmount = message.substring(amountMatch.range.last + 1).trim()
        val beforeAmount = message.substring(0, amountMatch.range.first).trim()

        // 금액 뒤에 있는 텍스트에서 가게명 추출
        var storeName = extractStoreNameFromText(afterAmount, cleanKeywords)

        // 금액 뒤에서 찾지 못하면 금액 앞에서 추출 시도
        if (storeName.isBlank() || storeName == "기타") {
            storeName = extractStoreNameFromText(beforeAmount, cleanKeywords)
        }

        // 특수 패턴 처리: [카드사] 날짜 시간 금액 가게명
        if (storeName.isBlank() || storeName == "기타") {
            // 시간 패턴 이후의 텍스트 추출
            val timePattern = Regex("""\d{1,2}:\d{2}""")
            val timeMatch = timePattern.find(message)
            if (timeMatch != null) {
                val afterTime = message.substring(timeMatch.range.last + 1).trim()
                storeName = extractStoreNameFromText(afterTime, cleanKeywords)
            }
        }

        return if (storeName.isNotBlank()) storeName else "기타"
    }

    /**
     * 텍스트에서 가게명 후보 추출
     */
    private fun extractStoreNameFromText(text: String, excludeKeywords: List<String>): String {
        if (text.isBlank()) return ""

        // 공백이나 특수문자로 분리
        val parts = text.split(Regex("""[\s\[\]()\/,\n]+"""))
            .map { it.trim() }
            .filter { part ->
                part.isNotBlank() &&
                part.length >= 2 &&
                !part.matches(Regex("""[\d,]+""")) && // 숫자만 있는 건 제외
                !part.matches(Regex("""\d{1,2}[/.-]\d{1,2}""")) && // 날짜 패턴 제외
                !part.matches(Regex("""\d{1,2}:\d{2}""")) && // 시간 패턴 제외
                !excludeKeywords.any { keyword -> part.contains(keyword) } &&
                !cardKeywords.any { keyword -> part.contains(keyword) }
            }

        // 첫 번째 유효한 부분 반환 (가게명일 확률이 높음)
        return parts.firstOrNull()?.take(20) ?: "" // 최대 20자
    }

    /**
     * 날짜/시간 추출
     * @param message SMS 본문
     * @param smsTimestamp SMS 수신 시간 (밀리초)
     * @return "YYYY-MM-DD HH:mm" 형식
     */
    fun extractDateTime(message: String, smsTimestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = smsTimestamp
        val currentYear = calendar.get(Calendar.YEAR)

        // 날짜 패턴 1: MM/DD 또는 MM-DD 또는 MM.DD
        val datePattern1 = Regex("""(\d{1,2})[/.-](\d{1,2})""")
        // 날짜 패턴 2: M월 D일 또는 M월D일
        val datePattern2 = Regex("""(\d{1,2})월\s*(\d{1,2})일""")
        // 시간 패턴: HH:mm
        val timePattern = Regex("""(\d{1,2}):(\d{2})""")

        var month = calendar.get(Calendar.MONTH) + 1
        var day = calendar.get(Calendar.DAY_OF_MONTH)
        var hour = calendar.get(Calendar.HOUR_OF_DAY)
        var minute = calendar.get(Calendar.MINUTE)

        // 날짜 추출 시도
        val dateMatch1 = datePattern1.find(message)
        val dateMatch2 = datePattern2.find(message)

        if (dateMatch1 != null) {
            month = dateMatch1.groupValues[1].toIntOrNull() ?: month
            day = dateMatch1.groupValues[2].toIntOrNull() ?: day
        } else if (dateMatch2 != null) {
            month = dateMatch2.groupValues[1].toIntOrNull() ?: month
            day = dateMatch2.groupValues[2].toIntOrNull() ?: day
        }

        // 시간 추출 시도
        val timeMatch = timePattern.find(message)
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
     * 카테고리 추론 (가게명 기반)
     */
    fun inferCategory(storeName: String, message: String): String {
        val combinedText = "$storeName $message".lowercase()

        for ((category, keywords) in categoryKeywords) {
            for (keyword in keywords) {
                if (combinedText.contains(keyword.lowercase())) {
                    return category
                }
            }
        }

        return "기타"
    }
}
