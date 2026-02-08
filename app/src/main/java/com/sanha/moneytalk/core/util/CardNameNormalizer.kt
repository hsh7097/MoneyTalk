package com.sanha.moneytalk.core.util

/**
 * 카드사명 정규화 유틸리티
 *
 * SMS에서 추출된 다양한 형태의 카드사명을 표준 이름으로 통합합니다.
 * 예: "KB", "KB국민", "KB카드", "국민카드", "kb" → "KB국민"
 *
 * 정규화 규칙:
 * 1. 대표 카드사별 alias 매핑 테이블
 * 2. 대소문자 무시 매칭
 * 3. 매핑 안 되는 이름은 원본 그대로 반환 (예: 지역 은행, 새마을금고 등)
 */
object CardNameNormalizer {

    /**
     * 대표 카드사 정규화 매핑
     * key: 정규화된 대표명, value: alias 목록 (소문자)
     */
    private val CANONICAL_NAMES: Map<String, List<String>> = mapOf(
        "KB국민" to listOf("kb", "kb국민", "국민카드", "kb카드", "kb체크", "국민체크", "노리", "노리2", "국민", "국민은행", "kookmin"),
        "신한" to listOf("신한", "신한카드", "신한체크", "sol", "쏠", "shinhan", "신한은행"),
        "삼성" to listOf("삼성", "삼성카드", "삼성체크", "samsung"),
        "현대" to listOf("현대", "현대카드", "현대체크", "hyundai", "현대m"),
        "롯데" to listOf("롯데", "롯데카드", "롯데체크", "lotte"),
        "하나" to listOf("하나", "하나카드", "하나체크", "하나은행", "hana", "하나머니"),
        "우리" to listOf("우리", "우리카드", "우리체크", "우리은행", "woori"),
        "NH농협" to listOf("nh", "농협", "nh카드", "농협카드", "nh체크", "농협은행", "nonghyup"),
        "BC" to listOf("bc", "bc카드", "비씨", "비씨카드"),
        "씨티" to listOf("씨티", "시티", "citi", "씨티은행"),
        "카카오뱅크" to listOf("카카오뱅크", "카카오페이", "카카오", "카뱅", "kakao"),
        "토스" to listOf("토스", "토스뱅크", "토스카드", "toss"),
        "케이뱅크" to listOf("케이뱅크", "k뱅크", "kbank"),
        "IBK기업" to listOf("ibk", "기업", "기업은행", "기업카드"),
        "SC제일" to listOf("sc", "제일", "제일은행", "sc제일"),
        "수협" to listOf("수협", "수협은행", "sh수협"),
        "광주은행" to listOf("광주", "광주은행", "kjb"),
        "전북은행" to listOf("전북", "전북은행", "jb"),
        "경남은행" to listOf("경남", "경남은행", "bnk경남"),
        "부산은행" to listOf("부산", "부산은행", "bnk부산"),
        "대구은행" to listOf("대구", "대구은행", "dgb"),
        "새마을금고" to listOf("새마을", "mg", "새마을금고"),
        "신협" to listOf("신협", "kfcc"),
        "우체국" to listOf("우체국", "우정", "post"),
    )

    /**
     * 역방향 매핑: alias → 대표명 (초기화 시 한 번만 구성)
     */
    private val ALIAS_TO_CANONICAL: Map<String, String> = buildMap {
        for ((canonical, aliases) in CANONICAL_NAMES) {
            for (alias in aliases) {
                put(alias, canonical)
            }
        }
    }

    /**
     * 카드사명 정규화
     *
     * @param rawCardName SMS에서 추출된 원본 카드사명
     * @return 정규화된 카드사명 (매핑 안 되면 원본 반환)
     */
    fun normalize(rawCardName: String): String {
        val trimmed = rawCardName.trim()
        if (trimmed.isBlank()) return ""

        val lower = trimmed.lowercase()

        // 1. 정확히 일치하는 alias 찾기
        ALIAS_TO_CANONICAL[lower]?.let { return it }

        // 2. 부분 일치: 가장 긴 alias가 매칭되는 대표명 반환
        //    (예: "KB국민카드" → "KB국민" 매칭)
        var bestMatch: String? = null
        var bestLength = 0
        for ((alias, canonical) in ALIAS_TO_CANONICAL) {
            if (lower.contains(alias) && alias.length > bestLength) {
                bestMatch = canonical
                bestLength = alias.length
            }
        }
        if (bestMatch != null) return bestMatch

        // 3. 매핑 안 되면 원본 반환
        return trimmed
    }
}
