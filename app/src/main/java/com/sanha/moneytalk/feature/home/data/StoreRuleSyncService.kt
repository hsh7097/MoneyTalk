package com.sanha.moneytalk.feature.home.data

import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import com.sanha.moneytalk.core.database.entity.supportsFixedExpense
import com.sanha.moneytalk.core.util.StatsExclusionClassifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StoreRule 변경을 실제 거래 데이터에 반영하는 동기화 서비스.
 *
 * 규칙 저장/삭제 시:
 * - 현재 규칙은 즉시 소급 적용
 * - 제거된 카테고리 규칙은 재분류
 * - 제거된 고정지출 true 규칙은 false로 원복
 * - 제거된 통계 제외 규칙은 자동 판별 기준으로 원복
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
            val statsExcludeRuleRemoved = oldRule.isExcludedFromStats != null &&
                    (newRule?.isExcludedFromStats == null || keywordChanged)

            if (fixedRuleRemoved) {
                reapplyFixedStateByKeyword(oldRule.keyword)
            }
            if (categoryRemoved) {
                reclassifyExpensesByKeyword(oldRule.keyword)
            }
            if (statsExcludeRuleRemoved) {
                reapplyStatsExcludedByKeyword(oldRule.keyword)
            }
        }

        newRule?.let { currentRule ->
            currentRule.category?.let { category ->
                expenseRepository.updateCategoryByStoreNameContaining(currentRule.keyword, category)
            }
            currentRule.isFixed?.let { isFixed ->
                expenseRepository.getExpensesByStoreNameContaining(currentRule.keyword)
                    .forEach { expense ->
                        val nextFixed = if (expense.supportsFixedExpense()) isFixed else false
                        if (expense.isFixed != nextFixed) {
                            expenseRepository.updateFixedById(expense.id, nextFixed)
                        }
                    }
            }
            currentRule.isExcludedFromStats?.let { isExcluded ->
                expenseRepository.getExpensesByStoreNameContaining(currentRule.keyword)
                    .forEach { expense ->
                        if (expense.isExcludedFromStats != isExcluded) {
                            expenseRepository.updateStatsExcludedById(expense.id, isExcluded)
                        }
                    }
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

        for (expense in expenses) {
            val isFixed = if (expense.supportsFixedExpense()) {
                storeRuleRepository.findMatchingRule(expense.storeName)?.isFixed ?: false
            } else {
                false
            }
            if (expense.isFixed != isFixed) {
                expenseRepository.updateFixedById(expense.id, isFixed)
            }
        }
    }

    private suspend fun reapplyStatsExcludedByKeyword(keyword: String) {
        val expenses = expenseRepository.getExpensesByStoreNameContaining(keyword)

        for (expense in expenses) {
            val isExcluded = storeRuleRepository.findMatchingRule(expense.storeName)
                ?.isExcludedFromStats
                ?: StatsExclusionClassifier.shouldExcludeExpense(expense)
            if (expense.isExcludedFromStats != isExcluded) {
                expenseRepository.updateStatsExcludedById(expense.id, isExcluded)
            }
        }
    }
}
