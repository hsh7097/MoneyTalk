package com.sanha.moneytalk.feature.home.data

import android.util.Log
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.util.SmsParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 카테고리 분류 서비스
 * 1. Room DB의 저장된 매핑 확인
 * 2. SmsParser의 로컬 키워드 매칭
 * 3. 미분류 항목은 Gemini API로 분류
 */
@Singleton
class CategoryClassifierService @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val geminiRepository: GeminiCategoryRepository,
    private val expenseRepository: ExpenseRepository
) {
    /**
     * 가게명으로 카테고리 조회 (Room 우선)
     */
    suspend fun getCategory(storeName: String, originalSms: String = ""): String {
        // 1. Room DB에서 저장된 매핑 확인
        categoryRepository.getCategoryByStoreName(storeName)?.let {
            Log.d("CategoryClassifier", "Room 매핑 사용: $storeName -> $it")
            return it
        }

        // 2. SmsParser의 로컬 키워드 매칭
        val localCategory = SmsParser.inferCategory(storeName, originalSms)
        if (localCategory != "기타") {
            // 로컬 매칭 결과를 Room에 저장
            categoryRepository.saveMapping(storeName, localCategory, "local")
            Log.d("CategoryClassifier", "로컬 키워드 매칭: $storeName -> $localCategory")
            return localCategory
        }

        // 3. 기타로 반환 (Gemini 분류는 배치로 별도 처리)
        return "기타"
    }

    /**
     * 미분류("기타") 항목들을 Gemini로 일괄 분류
     * @return 분류된 항목 수
     */
    suspend fun classifyUnclassifiedExpenses(): Int {
        // 카테고리가 "기타"인 지출 조회
        val unclassifiedExpenses = expenseRepository.getExpensesByCategoryOnce("기타")

        if (unclassifiedExpenses.isEmpty()) {
            Log.d("CategoryClassifier", "분류할 항목 없음")
            return 0
        }

        // 중복 제거된 가게명 목록
        val storeNames = unclassifiedExpenses.map { it.storeName }.distinct()
        Log.d("CategoryClassifier", "분류할 가게명: ${storeNames.size}개")

        // Gemini로 분류
        val classifications = geminiRepository.classifyStoreNames(storeNames)

        if (classifications.isEmpty()) {
            Log.e("CategoryClassifier", "Gemini 분류 실패")
            return 0
        }

        // 분류 결과를 Room에 저장
        val mappings = classifications.map { (store, category) -> store to category }
        categoryRepository.saveMappings(mappings, "gemini")

        // 지출 항목 카테고리 업데이트
        var updatedCount = 0
        for (expense in unclassifiedExpenses) {
            classifications[expense.storeName]?.let { newCategory ->
                expenseRepository.updateCategoryById(expense.id, newCategory)
                updatedCount++
            }
        }

        Log.d("CategoryClassifier", "분류 완료: $updatedCount 건")
        return updatedCount
    }

    /**
     * 특정 지출의 카테고리를 수동 변경 (사용자 지정)
     */
    suspend fun updateExpenseCategory(expenseId: Long, storeName: String, newCategory: String) {
        // Room 매핑 업데이트/추가
        categoryRepository.saveMapping(storeName, newCategory, "user")

        // 지출 항목 업데이트
        expenseRepository.updateCategoryById(expenseId, newCategory)

        Log.d("CategoryClassifier", "수동 분류: $storeName -> $newCategory")
    }

    /**
     * 동일 가게명을 가진 모든 지출의 카테고리 일괄 변경
     */
    suspend fun updateCategoryForAllSameStore(storeName: String, newCategory: String) {
        // Room 매핑 업데이트/추가
        categoryRepository.saveMapping(storeName, newCategory, "user")

        // 해당 가게명의 모든 지출 업데이트
        expenseRepository.updateCategoryByStoreName(storeName, newCategory)

        Log.d("CategoryClassifier", "일괄 분류: $storeName -> $newCategory (모든 항목)")
    }

    /**
     * Gemini API 키 설정 여부 확인
     */
    suspend fun hasGeminiApiKey(): Boolean {
        return geminiRepository.hasApiKey()
    }

    /**
     * 미분류 항목 수 조회
     */
    suspend fun getUnclassifiedCount(): Int {
        return expenseRepository.getExpensesByCategoryOnce("기타").size
    }

    /**
     * 미분류 항목이 없을 때까지 반복 분류
     * @param onProgress 진행 상황 콜백 (현재 라운드, 분류된 수, 남은 미분류 수)
     * @param maxRounds 최대 반복 횟수 (무한 루프 방지)
     * @return 총 분류된 항목 수
     */
    suspend fun classifyAllUntilComplete(
        onProgress: suspend (round: Int, classifiedInRound: Int, remaining: Int) -> Unit,
        maxRounds: Int = 10
    ): Int {
        var totalClassified = 0
        var round = 0

        while (round < maxRounds) {
            round++
            val remainingBefore = getUnclassifiedCount()

            if (remainingBefore == 0) {
                Log.d("CategoryClassifier", "모든 항목 분류 완료 (라운드 $round)")
                break
            }

            Log.d("CategoryClassifier", "라운드 $round 시작: $remainingBefore 개 미분류")

            val classifiedInRound = classifyUnclassifiedExpenses()
            totalClassified += classifiedInRound

            val remainingAfter = getUnclassifiedCount()
            onProgress(round, classifiedInRound, remainingAfter)

            // 더 이상 분류가 안 되면 종료 (진전이 없음)
            if (classifiedInRound == 0 || remainingAfter == remainingBefore) {
                Log.d("CategoryClassifier", "더 이상 분류 불가, 종료 (남은 미분류: $remainingAfter)")
                break
            }

            // Rate Limit 방지를 위한 딜레이
            kotlinx.coroutines.delay(2000)
        }

        Log.d("CategoryClassifier", "전체 분류 완료: 총 $totalClassified 건 분류됨")
        return totalClassified
    }
}
