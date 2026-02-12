package com.sanha.moneytalk.core.util

import android.util.Log
import com.sanha.moneytalk.core.database.dao.CategoryMappingDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카테고리 참조 리스트 제공자
 *
 * 각 프롬프트에 추가할 수 있는 동적 참고 리스트를 생성합니다.
 * 사용자가 직접 설정한 카테고리 매핑(source="user")을 기반으로
 * LLM이 참고할 수 있는 가게→카테고리 매핑 목록을 제공합니다.
 *
 * 향후 채팅에서 카테고리 설정 관련 조건을 추가하면
 * 이 리스트에 반영되어 모든 프롬프트에서 참조됩니다.
 *
 * 사용처:
 * - [GeminiSmsExtractor]: SMS 추출 시 가게 카테고리 참고
 * - [GeminiCategoryRepository]: 카테고리 분류 시 참고
 * - ChatPrompts: 재무 상담 시 카테고리 정보 참고
 */
@Singleton
class CategoryReferenceProvider @Inject constructor(
    private val categoryMappingDao: CategoryMappingDao
) {
    companion object {
        private const val TAG = "CategoryRef"

        /** 참조 리스트에 포함할 최대 항목 수 (프롬프트 크기 제한) */
        private const val MAX_REFERENCE_ITEMS = 50

        /** 카테고리별 최대 예시 수 */
        private const val MAX_EXAMPLES_PER_CATEGORY = 5
    }

    /** 인메모리 캐시 */
    private var cachedReferenceMap: Map<String, List<String>>? = null
    private var cachedReferenceText: String? = null

    /**
     * 캐시 무효화 (카테고리 매핑 변경 시 호출)
     */
    fun invalidateCache() {
        cachedReferenceMap = null
        cachedReferenceText = null
    }

    /**
     * 카테고리별 가게명 참조 맵 조회
     *
     * 사용자가 직접 설정한(source="user") 매핑을 우선으로,
     * 카테고리별로 대표 가게명 목록을 생성합니다.
     *
     * @return Map<카테고리, List<가게명>>
     */
    suspend fun getCategoryReferenceMap(): Map<String, List<String>> {
        cachedReferenceMap?.let { return it }

        val allMappings = categoryMappingDao.getAllMappingsOnce()

        // source="user"를 우선으로 정렬, 카테고리별 그룹핑
        val userMappings = allMappings.filter { it.source == "user" }
        val otherMappings = allMappings.filter { it.source != "user" }

        val referenceMap = mutableMapOf<String, MutableList<String>>()

        // 사용자 매핑 우선 추가
        for (mapping in userMappings) {
            referenceMap.getOrPut(mapping.category) { mutableListOf() }
                .add(mapping.storeName)
        }

        // 나머지 매핑으로 보충 (카테고리별 최대 개수까지)
        for (mapping in otherMappings) {
            val list = referenceMap.getOrPut(mapping.category) { mutableListOf() }
            if (list.size < MAX_EXAMPLES_PER_CATEGORY) {
                list.add(mapping.storeName)
            }
        }

        val result = referenceMap.mapValues { (_, stores) ->
            stores.take(MAX_EXAMPLES_PER_CATEGORY)
        }

        cachedReferenceMap = result
        Log.d(TAG, "참조 맵 생성: ${result.size}개 카테고리, 총 ${result.values.sumOf { it.size }}개 가게")
        return result
    }

    /**
     * SMS 추출 프롬프트용 참조 리스트 텍스트 생성
     *
     * LLM이 SMS에서 카테고리를 추정할 때 참고할 수 있는
     * 가게→카테고리 매핑 목록을 텍스트로 반환합니다.
     *
     * @return 프롬프트에 삽입할 참조 리스트 텍스트 (없으면 빈 문자열)
     */
    suspend fun getSmsExtractionReference(): String {
        val refMap = getCategoryReferenceMap()
        if (refMap.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("\n[추가 참고: 사용자 설정 가게-카테고리 매핑]")
        for ((category, stores) in refMap) {
            if (stores.isNotEmpty()) {
                sb.appendLine("- $category: ${stores.joinToString(", ")}")
            }
        }
        return sb.toString()
    }

    /**
     * 카테고리 분류 프롬프트용 참조 리스트 텍스트 생성
     *
     * Gemini 배치 분류 시 참고할 수 있는 기존 분류 결과를 제공합니다.
     *
     * @return 프롬프트에 삽입할 참조 리스트 텍스트 (없으면 빈 문자열)
     */
    suspend fun getCategoryClassificationReference(): String {
        val refMap = getCategoryReferenceMap()
        if (refMap.isEmpty()) return ""

        val sb = StringBuilder()
        sb.appendLine("\n## 추가 참고: 기존 분류된 가게 예시 (이 매핑을 우선 참고)")
        for ((category, stores) in refMap) {
            if (stores.isNotEmpty()) {
                sb.appendLine("- $category: ${stores.joinToString(", ")}")
            }
        }
        return sb.toString()
    }

    /**
     * 참조 리스트에 사용자 설정 항목 추가
     *
     * 채팅에서 사용자가 카테고리 관련 조건을 추가할 때 호출합니다.
     * CategoryMappingEntity에 source="user"로 저장되어
     * 이후 모든 프롬프트의 참조 리스트에 반영됩니다.
     *
     * @param storeName 가게명
     * @param category 카테고리
     */
    suspend fun addUserReference(storeName: String, category: String) {
        // CategoryMappingDao에 source="user"로 저장은
        // CategoryClassifierService.updateExpenseCategory()에서 이미 수행
        // 여기서는 캐시만 무효화
        invalidateCache()
        Log.d(TAG, "사용자 참조 추가: $storeName → $category")
    }
}
