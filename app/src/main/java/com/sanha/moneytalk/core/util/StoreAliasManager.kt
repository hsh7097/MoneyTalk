package com.sanha.moneytalk.core.util

import com.sanha.moneytalk.core.datastore.SettingsDataStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 가게명 별칭(Alias) 관리자
 * 영문명 ↔ 한글명 매핑 및 다양한 표기 변형 처리
 * 사용자 정의 별칭은 DataStore에 영구 저장
 */
@Singleton
class StoreAliasManager @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    // 가게 별칭 매핑 (모든 키는 소문자로 저장)
    private val aliasMap: Map<String, Set<String>> = mapOf(
        // 쿠팡 관련
        "쿠팡" to setOf("coupang", "쿠페이", "쿠팡이츠", "coupang eats", "로켓배송"),

        // 배달앱
        "배달의민족" to setOf("배민", "baemin", "배달의 민족"),
        "요기요" to setOf("yogiyo"),

        // 카페
        "스타벅스" to setOf("starbucks", "스벅", "별다방"),
        "투썸플레이스" to setOf("twosome", "투썸", "a twosome place"),
        "이디야" to setOf("ediya"),
        "메가커피" to setOf("mega coffee", "메가MGC"),
        "컴포즈커피" to setOf("compose coffee", "컴포즈"),
        "빽다방" to setOf("paik's coffee", "빽's"),

        // 편의점
        "GS25" to setOf("gs25", "지에스25"),
        "CU" to setOf("cu", "씨유"),
        "세븐일레븐" to setOf("7-eleven", "7eleven", "세븐 일레븐"),
        "이마트24" to setOf("emart24", "이마트 24"),

        // 마트/쇼핑
        "이마트" to setOf("emart", "e-mart"),
        "홈플러스" to setOf("homeplus", "home plus"),
        "롯데마트" to setOf("lotte mart", "lottemart"),
        "코스트코" to setOf("costco"),
        "다이소" to setOf("daiso"),
        "올리브영" to setOf("olive young", "oliveyoung"),

        // 교통
        "카카오택시" to setOf("kakao taxi", "카카오 택시", "카카오T"),
        "타다" to setOf("tada"),
        "티머니" to setOf("t-money", "tmoney"),

        // 구독 서비스
        "넷플릭스" to setOf("netflix"),
        "유튜브" to setOf("youtube", "youtube premium"),
        "멜론" to setOf("melon"),
        "스포티파이" to setOf("spotify"),
        "애플" to setOf("apple", "앱스토어", "app store"),
        "구글" to setOf("google", "google play", "구글플레이"),

        // 음식점 체인
        "맥도날드" to setOf("mcdonald's", "mcdonalds", "맥날"),
        "버거킹" to setOf("burger king", "burgerking"),
        "롯데리아" to setOf("lotteria"),
        "KFC" to setOf("kfc", "케이에프씨"),
        "파파존스" to setOf("papa john's", "papajohns"),
        "도미노피자" to setOf("domino's", "dominos", "도미노"),
        "피자헛" to setOf("pizza hut", "pizzahut"),

        // 기타
        "네이버" to setOf("naver", "네이버페이", "naver pay"),
        "카카오" to setOf("kakao", "카카오페이", "kakao pay"),
        "토스" to setOf("toss"),
    )

    // 사용자 정의 별칭 (DataStore에서 로드됨)
    private var customAliases = mutableMapOf<String, MutableSet<String>>()
    private var isLoaded = false

    // 역방향 매핑 캐시
    private var reverseMap: Map<String, String> = buildReverseMap()

    private fun buildReverseMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        aliasMap.forEach { (mainName, aliases) ->
            map[mainName.lowercase()] = mainName
            aliases.forEach { alias -> map[alias.lowercase()] = mainName }
        }
        customAliases.forEach { (mainName, aliases) ->
            map[mainName.lowercase()] = mainName
            aliases.forEach { alias -> map[alias.lowercase()] = mainName }
        }
        return map
    }

    /**
     * DataStore에서 사용자 정의 별칭 로드
     */
    suspend fun loadCustomAliases() {
        if (isLoaded) return
        val saved = settingsDataStore.getCustomAliases()
        customAliases = saved.mapValues { it.value.toMutableSet() }.toMutableMap()
        reverseMap = buildReverseMap()
        isLoaded = true
    }

    /**
     * 사용자 정의 별칭 추가 및 DataStore에 영구 저장
     */
    suspend fun addCustomAlias(mainName: String, alias: String) {
        loadCustomAliases()
        customAliases.getOrPut(mainName) { mutableSetOf() }.add(alias.lowercase())
        reverseMap = buildReverseMap()
        settingsDataStore.saveCustomAliases(customAliases)
    }

    /**
     * 검색 키워드로 찾을 수 있는 모든 별칭 반환
     */
    fun getAllAliases(keyword: String): Set<String> {
        val normalizedKeyword = keyword.lowercase().trim()
        val mainName = reverseMap[normalizedKeyword]

        return if (mainName != null) {
            val builtIn = aliasMap[mainName] ?: emptySet()
            val custom = customAliases[mainName] ?: emptySet()
            setOf(mainName) + builtIn + custom
        } else {
            setOf(keyword)
        }
    }

    /**
     * 가게명이 주어진 키워드와 매칭되는지 확인
     */
    fun matchesStore(storeName: String, keyword: String): Boolean {
        val normalizedStore = storeName.lowercase()
        val allAliases = getAllAliases(keyword)
        return allAliases.any { alias ->
            normalizedStore.contains(alias.lowercase())
        }
    }

    /**
     * 가게명을 정규화된 메인 이름으로 변환 (가능한 경우)
     */
    fun normalizeStoreName(storeName: String): String? {
        val normalizedStore = storeName.lowercase()
        reverseMap.forEach { (alias, mainName) ->
            if (normalizedStore.contains(alias)) {
                return mainName
            }
        }
        return null
    }

    companion object {
        /**
         * 정적 메서드 (DI 없이 사용하는 기존 코드 호환용)
         */
        @JvmStatic
        fun normalizeStoreNameStatic(storeName: String): String? {
            val normalizedStore = storeName.lowercase()
            staticReverseMap.forEach { (alias, mainName) ->
                if (normalizedStore.contains(alias)) {
                    return mainName
                }
            }
            return null
        }

        private val staticReverseMap: Map<String, String> by lazy {
            val map = mutableMapOf<String, String>()
            mapOf(
                "쿠팡" to setOf("coupang", "쿠페이", "쿠팡이츠"),
                "배달의민족" to setOf("배민", "baemin"),
                "스타벅스" to setOf("starbucks", "스벅"),
                "투썸플레이스" to setOf("twosome", "투썸"),
                "GS25" to setOf("gs25", "지에스25"),
                "CU" to setOf("cu", "씨유"),
                "넷플릭스" to setOf("netflix"),
                "유튜브" to setOf("youtube"),
                "맥도날드" to setOf("mcdonald's", "mcdonalds", "맥날"),
            ).forEach { (mainName, aliases) ->
                map[mainName.lowercase()] = mainName
                aliases.forEach { alias -> map[alias.lowercase()] = mainName }
            }
            map
        }
    }
}
