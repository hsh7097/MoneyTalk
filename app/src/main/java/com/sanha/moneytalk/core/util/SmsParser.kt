package com.sanha.moneytalk.core.util

import android.util.Log
import com.sanha.moneytalk.feature.chat.data.SmsAnalysisResult
import java.text.SimpleDateFormat
import java.util.*

object SmsParser {

    // 카드사 키워드 목록
    private val cardKeywords = listOf(
        // KB국민 (다양한 형식 지원)
        "KB국민", "국민카드", "KB카드", "KB체크", "국민체크", "노리", "노리2",
        "KB", "kb", "국민", "국민은행", "KOOKMIN",
        // 신한 (다양한 형식 지원)
        "신한", "신한카드", "신한체크", "SOL", "쏠", "SHINHAN", "신한은행",
        // 삼성 (다양한 형식 지원)
        "삼성", "삼성카드", "삼성체크", "SAMSUNG",
        // 현대 (다양한 형식 지원)
        "현대", "현대카드", "현대체크", "HYUNDAI", "현대M",
        // 롯데 (다양한 형식 지원)
        "롯데", "롯데카드", "롯데체크", "LOTTE",
        // 하나 (다양한 형식 지원)
        "하나", "하나카드", "하나체크", "하나은행", "HANA", "하나머니",
        // 우리 (다양한 형식 지원)
        "우리", "우리카드", "우리체크", "우리은행", "WOORI",
        // NH농협 (다양한 형식 지원)
        "NH", "농협", "NH카드", "농협카드", "NH체크", "농협은행", "NONGHYUP",
        // BC (다양한 형식 지원)
        "BC", "BC카드", "비씨", "비씨카드",
        // 씨티 (다양한 형식 지원)
        "씨티", "시티", "Citi", "CITI", "씨티은행",
        // 카카오 (다양한 형식 지원)
        "카카오뱅크", "카카오페이", "카카오", "카뱅", "KAKAO",
        // 토스 (다양한 형식 지원)
        "토스", "토스뱅크", "토스카드", "TOSS",
        // 케이뱅크 (다양한 형식 지원)
        "케이뱅크", "K뱅크", "Kbank",
        // IBK기업은행
        "IBK", "기업", "기업은행", "기업카드",
        // SC제일은행
        "SC", "제일", "제일은행", "SC제일",
        // 수협
        "수협", "수협은행", "SH수협",
        // 광주은행
        "광주", "광주은행", "KJB",
        // 전북은행
        "전북", "전북은행", "JB",
        // 경남은행
        "경남", "경남은행", "BNK경남",
        // 부산은행
        "부산", "부산은행", "BNK부산",
        // 대구은행
        "대구", "대구은행", "DGB",
        // 새마을금고
        "새마을", "MG", "새마을금고",
        // 신협
        "신협", "KFCC",
        // 우체국
        "우체국", "우정", "POST",
        // 저축은행
        "저축은행", "SB", "OSB", "OK저축",
        // 기타
        "체크카드", "신용카드", "선불", "후불"
    )

    // 결제 관련 키워드
    private val paymentKeywords = listOf(
        "결제", "승인", "사용", "출금", "이용"
    )

    // 제외할 키워드 (광고, 안내 등)
    private val excludeKeywords = listOf(
        "광고", "[광고]", "(광고)",
        "홍보", "이벤트", "혜택안내", "포인트 적립",
        "명세서", "청구서", "이용대금"
    )

    // 카테고리 매핑 (가게명 키워드 기반) - 뱅크샐러드 스타일
    private val categoryKeywords = mapOf(
        // 음식 관련
        "고기" to listOf(
            "푸줏간", "정육", "고기", "삼겹살", "갈비", "한우", "소고기", "돼지고기"
        ),
        "일식" to listOf(
            "초밥", "스시", "사시미", "라멘", "우동", "돈까스", "일식", "이자카야",
            "백소정", "스시로", "쿠라스시"
        ),
        "중식" to listOf(
            "짜장", "짬뽕", "중국집", "중식", "마라탕", "훠궈"
        ),
        "한식" to listOf(
            "한식", "찌개", "탕", "냉면", "비빔밥", "국밥", "설렁탕", "갈비탕",
            "김치찌개", "된장찌개", "부대찌개"
        ),
        "치킨" to listOf(
            "치킨", "BBQ", "교촌", "BHC", "굽네", "네네", "푸라닭", "호식이"
        ),
        "피자" to listOf(
            "피자", "도미노", "피자헛", "미스터피자", "파파존스"
        ),
        "패스트푸드" to listOf(
            "맥도날드", "버거킹", "KFC", "롯데리아", "맘스터치", "서브웨이"
        ),
        "분식" to listOf(
            "김밥", "분식", "떡볶이", "라면", "국수", "우동"
        ),
        "배달" to listOf(
            "배달의민족", "요기요", "쿠팡이츠", "배민", "위메프오"
        ),
        // 편의점/마트
        "편의점" to listOf(
            "편의점", "GS25", "CU", "세븐일레븐", "이마트24", "미니스톱"
        ),
        "마트" to listOf(
            "이마트", "홈플러스", "롯데마트", "코스트코", "트레이더스",
            "마트", "하나로", "농협마트"
        ),
        // 카페/디저트
        "카페" to listOf(
            "스타벅스", "투썸", "이디야", "커피빈", "탐앤탐스", "할리스",
            "메가커피", "컴포즈", "빽다방", "더벤티", "파스쿠찌",
            "카페", "커피"
        ),
        "베이커리" to listOf(
            "베이커리", "빵집", "제과", "던킨", "크리스피", "파리바게뜨", "뚜레쥬르"
        ),
        "아이스크림/빙수" to listOf(
            "배스킨라빈스", "나뚜루", "하겐다즈", "설빙", "아이스크림", "빙수"
        ),
        // 교통
        "택시" to listOf(
            "택시", "카카오T", "타다", "우버"
        ),
        "대중교통" to listOf(
            "버스", "지하철", "KTX", "SRT", "코레일", "기차"
        ),
        "주유" to listOf(
            "주유소", "SK에너지", "GS칼텍스", "현대오일", "S-OIL", "알뜰주유"
        ),
        "주차" to listOf(
            "하이패스", "톨게이트", "고속도로", "주차", "파킹"
        ),
        // 쇼핑
        "온라인쇼핑" to listOf(
            "쿠팡", "11번가", "G마켓", "옥션", "위메프", "티몬",
            "네이버쇼핑", "SSG", "롯데ON", "현대Hmall"
        ),
        "패션" to listOf(
            "무신사", "지그재그", "에이블리", "29CM", "W컨셉",
            "유니클로", "자라", "H&M"
        ),
        "뷰티" to listOf(
            "올리브영", "롭스", "화장품", "뷰티"
        ),
        "생활용품" to listOf(
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
        "병원" to listOf(
            "병원", "의원", "클리닉", "치과", "안과", "피부과", "내과", "외과"
        ),
        "약국" to listOf(
            "약국", "약"
        ),
        "운동" to listOf(
            "헬스", "피트니스", "짐", "요가", "필라테스", "PT"
        ),
        // 문화/여가
        "영화" to listOf(
            "CGV", "메가박스", "롯데시네마", "영화관", "영화"
        ),
        "놀이공원" to listOf(
            "놀이공원", "에버랜드", "롯데월드", "키자니아"
        ),
        "게임/오락" to listOf(
            "노래방", "PC방", "당구장", "볼링장", "찜질방"
        ),
        "여행/숙박" to listOf(
            "여행", "호텔", "펜션", "에어비앤비", "야놀자", "여기어때"
        ),
        "공연/전시" to listOf(
            "티켓", "공연", "뮤지컬", "콘서트", "전시"
        ),
        // 교육
        "교육" to listOf(
            "학원", "학습", "교육", "인강", "클래스101", "패스트캠퍼스"
        ),
        "도서" to listOf(
            "책", "서점", "교보문고", "영풍문고", "알라딘", "예스24"
        ),
        // 생활
        "통신" to listOf(
            "통신", "SKT", "KT", "LG유플러스", "알뜰폰"
        ),
        "공과금" to listOf(
            "전기", "가스", "수도", "관리비", "공과금"
        ),
        "보험" to listOf(
            "보험"
        ),
        "미용" to listOf(
            "미용실", "헤어", "네일", "왁싱"
        )
    )

    /**
     * 카드 결제 문자인지 확인
     */
    fun isCardPaymentSms(message: String): Boolean {
        // 제외 키워드가 있으면 false
        if (excludeKeywords.any { message.contains(it) }) {
            Log.e("sanha","제외 키워드 $message")
            return false
        }

        // 카드사 키워드가 있고, 결제 관련 키워드가 있으면 true
        val hasCardKeyword = cardKeywords.any { message.contains(it) }
        val hasPaymentKeyword = paymentKeywords.any { message.contains(it) }

        // 금액 패턴 확인 (숫자+원 또는 숫자+,+원)
        val amountPattern = Regex("""[\d,]+원""")
        val hasAmount = amountPattern.containsMatchIn(message)

        val isCardPayment = hasCardKeyword && hasPaymentKeyword && hasAmount
        if(isCardPayment){
            Log.e("sanha","성공 키워드 [${message.length}자] $message")
        }else{
            // message가 너무 길면 앞 50자만 출력
            val previewMsg = if (message.length > 50) message.take(50) + "..." else message
            Log.e("sanha","실패 키워드 [${message.length}자] $previewMsg | hasCard:$hasCardKeyword hasPay:$hasPaymentKeyword hasAmt:$hasAmount")
        }

        return isCardPayment
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

    // 제외할 가게명 패턴 (URL, 발신 표시, 기타 비가게명)
    private val excludeStorePatterns = listOf(
        "Web발신", "web발신", "WEB발신",
        "ltcard", "card.kr", ".kr", ".com", ".co.kr", "http", "www",
        "기준", "누적", "잔액", "한도", "가용",
        "일시불", "할부", "취소", "승인", "결제", "출금", "사용",
        "체크", "신용", "님", "고객", "회원",
        "원", "건", "월", "일", "시", "분",
        "SMS", "MMS", "안내", "알림"
    )

    /**
     * 가게명 추출
     * 한국 카드사 SMS 형식에 맞춰 가게명 추출
     * 일반적인 형식: [카드사] MM/DD HH:mm 가게명 금액원 승인
     */
    fun extractStoreName(message: String): String {
        // 패턴 1: 시간 뒤에 가게명이 오는 경우 (가장 흔함)
        // 예: "KB국민 12/25 14:30 스타벅스 15,000원 승인"
        val timePattern = Regex("""(\d{1,2}:\d{2})\s*(.+?)[\s]*[\d,]+원""")
        val timeMatch = timePattern.find(message)
        if (timeMatch != null) {
            val potentialStore = timeMatch.groupValues[2].trim()
            val cleanStore = cleanStoreName(potentialStore)
            if (isValidStoreName(cleanStore)) {
                return cleanStore
            }
        }

        // 패턴 2: 금액 바로 앞에 가게명이 오는 경우
        // 예: "신한카드 스타벅스 15,000원 결제"
        val beforeAmountPattern = Regex("""(.+?)\s*([\d,]+)원""")
        val beforeMatch = beforeAmountPattern.find(message)
        if (beforeMatch != null) {
            val beforeText = beforeMatch.groupValues[1]
            // 마지막 공백 이후 단어 추출
            val words = beforeText.split(Regex("""[\s\[\]()\/\n]+""")).filter { it.isNotBlank() }
            for (i in words.indices.reversed()) {
                val word = cleanStoreName(words[i])
                if (isValidStoreName(word) && !cardKeywords.any { word.contains(it) }) {
                    return word
                }
            }
        }

        // 패턴 3: 금액 뒤에 가게명이 오는 경우
        // 예: "15,000원 스타벅스 승인"
        val afterAmountPattern = Regex("""[\d,]+원\s*(.+?)(?:승인|결제|사용|일시불|할부|\s*$)""")
        val afterMatch = afterAmountPattern.find(message)
        if (afterMatch != null) {
            val potentialStore = afterMatch.groupValues[1].trim()
            val cleanStore = cleanStoreName(potentialStore)
            if (isValidStoreName(cleanStore)) {
                return cleanStore
            }
        }

        // 패턴 4: 전체 메시지에서 유효한 가게명 추출
        val words = message.split(Regex("""[\s\[\]()\/,\n]+"""))
            .map { cleanStoreName(it) }
            .filter { isValidStoreName(it) && !cardKeywords.any { keyword -> it.contains(keyword) } }

        return words.firstOrNull() ?: "결제"
    }

    /**
     * 가게명 정리 (불필요한 문자 제거)
     */
    private fun cleanStoreName(name: String): String {
        var cleaned = name.trim()
        // 앞뒤 특수문자 제거
        cleaned = cleaned.replace(Regex("""^[^\w가-힣]+|[^\w가-힣]+$"""), "")
        // 최대 15자로 제한
        return cleaned.take(15)
    }

    /**
     * 유효한 가게명인지 확인
     */
    private fun isValidStoreName(name: String): Boolean {
        if (name.isBlank() || name.length < 2) return false

        // 숫자로만 구성된 경우 제외
        if (name.matches(Regex("""[\d,.:]+"""))) return false

        // 날짜/시간 패턴 제외
        if (name.matches(Regex("""\d{1,2}[/.-]\d{1,2}"""))) return false
        if (name.matches(Regex("""\d{1,2}:\d{2}"""))) return false

        // 제외 패턴에 해당하는 경우 제외
        for (pattern in excludeStorePatterns) {
            if (name.equals(pattern, ignoreCase = true) || name.contains(pattern, ignoreCase = true)) {
                return false
            }
        }

        return true
    }

    /**
     * 텍스트에서 가게명 후보 추출 (레거시 - 호환성 유지)
     */
    private fun extractStoreNameFromText(text: String, excludeKeywords: List<String>): String {
        if (text.isBlank()) return ""

        val parts = text.split(Regex("""[\s\[\]()\/,\n]+"""))
            .map { cleanStoreName(it) }
            .filter { isValidStoreName(it) && !excludeKeywords.any { keyword -> it.contains(keyword) } }

        return parts.firstOrNull() ?: ""
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
