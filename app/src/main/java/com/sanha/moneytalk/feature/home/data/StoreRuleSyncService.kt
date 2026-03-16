package com.sanha.moneytalk.feature.home.data

import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StoreRule 변경을 실제 거래 데이터에 반영하는 동기화 서비스.
 *
 * 규칙 저장/삭제 시:
 * - 현재 규칙은 즉시 소급 적용
 * - 제거된 카테고리 규칙은 재분류
 * - 제거된 고정지출 true 규칙은 false로 원복
 */
@Singleton
class StoreRuleSyncService @Inject constructor(
    private val storeRuleRepository: StoreRuleRepository,
    private val expenseRepository: ExpenseRepository,
    private val categoryClassifierService: CategoryClassifierService
) {

    suspend fun applyRuleChange(
        previousRule: StoreRuleEntity?,
        newRule: StoreRuleEntity?
    ) {
        val shouldDeletePreviousRule = previousRule != null &&
            newRule != null &&
            previousRule.id != newRule.id &&
            !previousRule.keyword.equals(newRule.keyword, ignoreCase = true)

        when {
            previousRule == null && newRule == null -> return
            newRule == null -> previousRule?.let { storeRuleRepository.deleteById(it.id) }
            else -> {
                storeRuleRepository.upsert(newRule)
                if (shouldDeletePreviousRule) {
                    previousRule?.let { storeRuleRepository.deleteById(it.id) }
                }
            }
        }

        previousRule?.let { oldRule ->
            val keywordChanged = !oldRule.keyword.equals(newRule?.keyword, ignoreCase = true)
            val categoryRemoved = oldRule.category != null &&
                    (newRule?.category == null || keywordChanged)
            val fixedRuleRemoved = oldRule.isFixed != null &&
                    (newRule?.isFixed == null || keywordChanged)

            if (fixedRuleRemoved) {
                reapplyFixedStateByKeyword(oldRule.keyword)
            }
            if (categoryRemoved) {
                reclassifyExpensesByKeyword(oldRule.keyword)
            }
        }

        newRule?.let { currentRule ->
            currentRule.category?.let { category ->
                expenseRepository.updateCategoryByStoreNameContaining(currentRule.keyword, category)
            }
            currentRule.isFixed?.let { isFixed ->
                expenseRepository.updateFixedByStoreNameContaining(currentRule.keyword, isFixed)
            }
        }
    }

    private suspend fun reclassifyExpensesByKeyword(keyword: String) {
        val expenses = expenseRepository.getExpensesByStoreNameContaining(keyword)
            .filter { it.transactionType != "TRANSFER" }

        for (expense in expenses) {
            val category = categoryClassifierService.getCategory(
                storeName = expense.storeName,
                originalSms = expense.originalSms
            )
            if (category != expense.category) {
                expenseRepository.updateCategoryById(expense.id, category)
            }
        }
    }

    private suspend fun reapplyFixedStateByKeyword(keyword: String) {
        val expenses = expenseRepository.getExpensesByStoreNameContaining(keyword)
            .filter { it.transactionType != "TRANSFER" }

        for (expense in expenses) {
            val isFixed = storeRuleRepository.findMatchingRule(expense.storeName)?.isFixed ?: false
            if (expense.isFixed != isFixed) {
                expenseRepository.updateFixedById(expense.id, isFixed)
            }
        }
    }
}
