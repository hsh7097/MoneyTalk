package com.sanha.moneytalk.core.appfunctions

import android.content.Context
import com.sanha.moneytalk.core.appfunctions.MoneyTalkAppFunctionInputRules.parseDate
import com.sanha.moneytalk.core.database.CustomCategoryRepository
import com.sanha.moneytalk.core.database.OwnedCardRepository
import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.database.dao.BudgetDao
import com.sanha.moneytalk.core.database.entity.BudgetEntity
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.CategoryType
import com.sanha.moneytalk.core.model.IncomeCategoryMapper
import com.sanha.moneytalk.core.sms.DeletedSmsTracker
import com.sanha.moneytalk.core.util.CategoryReferenceProvider
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.StoreAliasManager
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.feature.home.data.StoreRuleRepository
import com.sanha.moneytalk.feature.home.data.StoreRuleSyncService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** App Functions의 거래/설정 변경, 입력 검증과 변경 알림을 담당한다. */
@Singleton
class MoneyTalkChatAppFunctionActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val settingsDataStore: SettingsDataStore,
    private val smsExclusionRepository: SmsExclusionRepository,
    private val ownedCardRepository: OwnedCardRepository,
    private val storeRuleRepository: StoreRuleRepository,
    private val storeRuleSyncService: StoreRuleSyncService,
    private val customCategoryRepository: CustomCategoryRepository,
    private val categoryReferenceProvider: CategoryReferenceProvider,
    private val dataRefreshEvent: DataRefreshEvent,
    private val budgetDao: BudgetDao
) {
    suspend fun updateExpenseCategory(
        expenseId: Long?,
        newCategory: String?
    ): MoneyTalkOperationResult {
        if (expenseId == null || newCategory.isNullOrBlank()) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        val normalized = normalizeExpenseCategoryName(newCategory)
        val affected = expenseRepository.updateCategoryById(expenseId, normalized)
        if (affected > 0) {
            categoryReferenceProvider.invalidateCache()
            dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
        }
        return resultByAffected(affected, expenseId)
    }

    suspend fun updateExpenseCategoryByStore(
        storeName: String?,
        newCategory: String?
    ): MoneyTalkOperationResult {
        if (storeName.isNullOrBlank() || newCategory.isNullOrBlank()) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        return updateExpenseCategoryByAliases(storeName, newCategory)
    }

    suspend fun updateExpenseCategoryByKeyword(
        keyword: String?,
        newCategory: String?
    ): MoneyTalkOperationResult {
        if (keyword.isNullOrBlank() || newCategory.isNullOrBlank()) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        return updateExpenseCategoryByAliases(keyword, newCategory)
    }

    suspend fun deleteExpense(expenseId: Long?): MoneyTalkOperationResult {
        if (expenseId == null) return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        val expense = expenseRepository.getExpenseById(expenseId) ?: return failure(RESULT_NOT_FOUND)
        DeletedSmsTracker.markDeleted(expense.smsId)
        expenseRepository.deleteById(expenseId)
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return MoneyTalkOperationResult(
            success = true,
            resultCode = RESULT_SUCCESS,
            affectedCount = 1,
            resourceId = expenseId
        )
    }

    suspend fun deleteExpensesByKeyword(keyword: String?): MoneyTalkOperationResult {
        if (keyword.isNullOrBlank()) return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        val affected = expenseRepository.deleteByKeyword(keyword)
        if (affected > 0) dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return resultByAffected(affected)
    }

    suspend fun deleteDuplicateExpenses(): MoneyTalkOperationResult {
        val affected = expenseRepository.deleteDuplicates()
        if (affected > 0) dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return resultByAffected(affected)
    }

    suspend fun addExpense(
        storeName: String?,
        amount: Int?,
        date: String?,
        cardName: String?,
        category: String?,
        memo: String?
    ): MoneyTalkOperationResult {
        if (storeName.isNullOrBlank() || amount == null || amount <= 0) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        val dateTime = if (date.isNullOrBlank()) {
            System.currentTimeMillis()
        } else {
            parseDate(context, date, endOfDay = false)
        }
        val id = expenseRepository.insert(
            ExpenseEntity(
                storeName = storeName,
                amount = amount,
                dateTime = dateTime,
                cardName = cardName?.takeIf { it.isNotBlank() } ?: MANUAL_CARD_NAME,
                category = category?.takeIf { it.isNotBlank() }?.let(::normalizeExpenseCategoryName)
                    ?: UNCATEGORIZED_CATEGORY,
                originalSms = EMPTY_TEXT,
                smsId = "${MANUAL_SMS_PREFIX}${System.currentTimeMillis()}",
                memo = memo
            )
        )
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return MoneyTalkOperationResult(
            success = true,
            resultCode = RESULT_SUCCESS,
            affectedCount = 1,
            resourceId = id
        )
    }

    suspend fun updateExpenseMemo(
        expenseId: Long?,
        memo: String?
    ): MoneyTalkOperationResult {
        if (expenseId == null) return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        val affected = expenseRepository.updateMemo(expenseId, memo)
        if (affected > 0) dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return resultByAffected(affected, expenseId)
    }

    suspend fun updateExpenseStoreName(
        expenseId: Long?,
        newStoreName: String?
    ): MoneyTalkOperationResult {
        if (expenseId == null || newStoreName.isNullOrBlank()) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        val affected = expenseRepository.updateStoreName(expenseId, newStoreName)
        if (affected > 0) dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return resultByAffected(affected, expenseId)
    }

    suspend fun updateExpenseAmount(
        expenseId: Long?,
        newAmount: Int?
    ): MoneyTalkOperationResult {
        if (expenseId == null || newAmount == null || newAmount <= 0) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        val affected = expenseRepository.updateAmount(expenseId, newAmount)
        if (affected > 0) dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return resultByAffected(affected, expenseId)
    }

    suspend fun updateExpenseFixed(
        expenseId: Long?,
        isFixed: Boolean?
    ): MoneyTalkOperationResult {
        if (expenseId == null || isFixed == null) return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        val affected = expenseRepository.updateFixedById(expenseId, isFixed)
        if (affected > 0) dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return resultByAffected(affected, expenseId)
    }

    suspend fun updateExpenseStatsExcluded(
        expenseId: Long?,
        isExcludedFromStats: Boolean?
    ): MoneyTalkOperationResult {
        if (expenseId == null || isExcludedFromStats == null) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        val affected = expenseRepository.updateStatsExcludedById(expenseId, isExcludedFromStats)
        if (affected > 0) dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return resultByAffected(affected, expenseId)
    }

    suspend fun addIncome(
        source: String?,
        description: String?,
        amount: Int?,
        date: String?,
        type: String?,
        category: String?,
        memo: String?,
        isRecurring: Boolean?,
        recurringDay: Int?
    ): MoneyTalkOperationResult {
        if (amount == null || amount <= 0) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        val typeName = type?.takeIf { it.isNotBlank() } ?: DEFAULT_INCOME_TYPE
        val sourceName = source.orEmpty()
        val descriptionText = description?.takeIf { it.isNotBlank() }
            ?: sourceName.takeIf { it.isNotBlank() }
            ?: typeName
        val recurring = isRecurring ?: false
        val dateTime = if (date.isNullOrBlank()) {
            System.currentTimeMillis()
        } else {
            parseDate(context, date, endOfDay = false)
        }
        val id = incomeRepository.insert(
            IncomeEntity(
                amount = amount,
                type = typeName,
                source = sourceName,
                description = descriptionText,
                isRecurring = recurring,
                recurringDay = if (recurring) {
                    recurringDay?.coerceIn(MIN_RECURRING_DAY, MAX_RECURRING_DAY)
                } else {
                    null
                },
                dateTime = dateTime,
                smsId = "${MANUAL_INCOME_SMS_PREFIX}${System.currentTimeMillis()}",
                category = category?.takeIf { it.isNotBlank() }?.let(::normalizeIncomeCategoryName)
                    ?: IncomeCategoryMapper.categoryForType(typeName),
                memo = memo
            )
        )
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return MoneyTalkOperationResult(
            success = true,
            resultCode = RESULT_SUCCESS,
            affectedCount = 1,
            resourceId = id
        )
    }

    suspend fun updateIncomeMemo(
        incomeId: Long?,
        memo: String?
    ): MoneyTalkOperationResult {
        if (incomeId == null) return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        incomeRepository.getIncomeById(incomeId) ?: return failure(RESULT_NOT_FOUND)
        incomeRepository.updateMemo(incomeId, memo)
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return MoneyTalkOperationResult(
            success = true,
            resultCode = RESULT_SUCCESS,
            affectedCount = 1,
            resourceId = incomeId
        )
    }

    suspend fun updateIncomeCategoryByKeyword(
        keyword: String?,
        newCategory: String?
    ): MoneyTalkOperationResult {
        if (keyword.isNullOrBlank() || newCategory.isNullOrBlank()) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        val affected = incomeRepository.updateCategoryByKeyword(keyword, normalizeIncomeCategoryName(newCategory))
        if (affected > 0) {
            categoryReferenceProvider.invalidateCache()
            dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
        }
        return resultByAffected(affected)
    }

    suspend fun updateIncomeRecurringByKeyword(
        keyword: String?,
        isRecurring: Boolean?
    ): MoneyTalkOperationResult {
        if (keyword.isNullOrBlank() || isRecurring == null) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        val affected = incomeRepository.updateRecurringByKeyword(keyword, isRecurring)
        if (affected > 0) dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return resultByAffected(affected)
    }

    suspend fun addSmsExclusionKeyword(keyword: String?): MoneyTalkOperationResult {
        if (keyword.isNullOrBlank()) return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        val added = smsExclusionRepository.addKeyword(keyword, source = SOURCE_APP_FUNCTION)
        return MoneyTalkOperationResult(
            success = added,
            resultCode = if (added) RESULT_SUCCESS else RESULT_ALREADY_EXISTS,
            affectedCount = if (added) 1 else 0,
            resourceId = NO_RESOURCE_ID
        )
    }

    suspend fun removeSmsExclusionKeyword(keyword: String?): MoneyTalkOperationResult {
        if (keyword.isNullOrBlank()) return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        val affected = smsExclusionRepository.removeKeyword(keyword)
        return resultByAffected(affected)
    }

    suspend fun setCardOwnership(cardName: String?, isOwned: Boolean?): MoneyTalkOperationResult {
        if (cardName.isNullOrBlank() || isOwned == null) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        val updated = ownedCardRepository.addManualCard(cardName, isOwned)
        if (!updated) return failure(RESULT_INVALID_ARGUMENT)
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.OWNED_CARD_UPDATED)
        return MoneyTalkOperationResult(
            success = true,
            resultCode = RESULT_SUCCESS,
            affectedCount = 1,
            resourceId = NO_RESOURCE_ID
        )
    }

    suspend fun addManualCard(cardName: String?, isOwned: Boolean?): MoneyTalkOperationResult {
        return setCardOwnership(cardName, isOwned ?: true)
    }

    suspend fun setBudget(category: String?, amount: Int?): MoneyTalkOperationResult {
        if (category.isNullOrBlank() || amount == null || amount < 0) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }
        val normalized = if (category == TOTAL_BUDGET_CATEGORY) {
            category
        } else {
            normalizeExpenseCategoryName(category)
        }
        budgetDao.insert(
            BudgetEntity(
                category = normalized,
                monthlyLimit = amount,
                yearMonth = DEFAULT_BUDGET_MONTH
            )
        )
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return MoneyTalkOperationResult(
            success = true,
            resultCode = RESULT_SUCCESS,
            affectedCount = 1,
            resourceId = NO_RESOURCE_ID
        )
    }

    suspend fun setMonthlyIncome(amount: Int?): MoneyTalkOperationResult {
        if (amount == null || amount < 0) return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        settingsDataStore.saveMonthlyIncome(amount)
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return MoneyTalkOperationResult(
            success = true,
            resultCode = RESULT_SUCCESS,
            affectedCount = 1,
            resourceId = NO_RESOURCE_ID
        )
    }

    suspend fun setMonthStartDay(day: Int?): MoneyTalkOperationResult {
        if (day == null || day !in MIN_MONTH_START_DAY..MAX_MONTH_START_DAY) {
            return failure(RESULT_INVALID_ARGUMENT)
        }
        settingsDataStore.saveMonthStartDay(day)
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        return MoneyTalkOperationResult(
            success = true,
            resultCode = RESULT_SUCCESS,
            affectedCount = 1,
            resourceId = NO_RESOURCE_ID
        )
    }

    suspend fun upsertStoreRule(
        keyword: String?,
        category: String?,
        isFixed: Boolean?,
        isExcludedFromStats: Boolean?
    ): MoneyTalkOperationResult {
        val normalizedKeyword = keyword?.trim()
        if (normalizedKeyword.isNullOrBlank()) return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        if (category.isNullOrBlank() && isFixed == null && isExcludedFromStats == null) {
            return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        }

        val previousRule = storeRuleRepository.getByKeyword(normalizedKeyword)
        val nextRule = StoreRuleEntity(
            id = previousRule?.id ?: NO_RESOURCE_ID,
            keyword = normalizedKeyword,
            category = category?.takeIf { it.isNotBlank() }?.let(::normalizeExpenseCategoryName),
            isFixed = isFixed,
            isExcludedFromStats = isExcludedFromStats,
            createdAt = previousRule?.createdAt ?: System.currentTimeMillis()
        )
        storeRuleSyncService.applyRuleChange(previousRule, nextRule)
        categoryReferenceProvider.invalidateCache()
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
        return MoneyTalkOperationResult(
            success = true,
            resultCode = RESULT_SUCCESS,
            affectedCount = 1,
            resourceId = previousRule?.id ?: NO_RESOURCE_ID
        )
    }

    suspend fun deleteStoreRule(
        ruleId: Long?,
        keyword: String?
    ): MoneyTalkOperationResult {
        val previousRule = ruleId?.takeIf { it > 0 }?.let { id ->
            storeRuleRepository.getAllOnce().firstOrNull { it.id == id }
        } ?: keyword?.takeIf { it.isNotBlank() }?.let { storeRuleRepository.getByKeyword(it.trim()) }
        previousRule ?: return failure(RESULT_NOT_FOUND)
        storeRuleSyncService.applyRuleChange(previousRule, null)
        categoryReferenceProvider.invalidateCache()
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
        return MoneyTalkOperationResult(
            success = true,
            resultCode = RESULT_SUCCESS,
            affectedCount = 1,
            resourceId = previousRule.id
        )
    }

    suspend fun addCustomCategory(
        displayName: String?,
        emoji: String?,
        categoryType: String?
    ): MoneyTalkOperationResult {
        val name = displayName?.trim()
        if (name.isNullOrBlank()) return failure(RESULT_MISSING_REQUIRED_PARAMETER)
        val type = parseCategoryType(categoryType) ?: return failure(RESULT_INVALID_ARGUMENT)
        if (customCategoryRepository.isDuplicate(name, type)) {
            return failure(RESULT_ALREADY_EXISTS)
        }
        val id = customCategoryRepository.add(
            displayName = name,
            emoji = emoji?.takeIf { it.isNotBlank() } ?: DEFAULT_CUSTOM_CATEGORY_EMOJI,
            categoryType = type
        )
        categoryReferenceProvider.invalidateCache()
        dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
        return MoneyTalkOperationResult(
            success = true,
            resultCode = RESULT_SUCCESS,
            affectedCount = 1,
            resourceId = id
        )
    }

    private suspend fun updateExpenseCategoryByAliases(
        keyword: String,
        newCategory: String
    ): MoneyTalkOperationResult {
        val normalized = normalizeExpenseCategoryName(newCategory)
        val affected = StoreAliasManager.getAllAliases(keyword).sumOf { alias ->
            expenseRepository.updateCategoryByStoreNameContaining(alias, normalized)
        }
        if (affected > 0) {
            categoryReferenceProvider.invalidateCache()
            dataRefreshEvent.emit(DataRefreshEvent.RefreshType.CATEGORY_UPDATED)
        }
        return resultByAffected(affected)
    }

    private fun normalizeExpenseCategoryName(categoryName: String): String {
        val trimmed = categoryName.trim()
        val category = Category.fromDisplayName(trimmed)
        return if (category == Category.ETC && trimmed != Category.ETC.displayName) {
            trimmed
        } else {
            category.displayName
        }
    }

    private fun normalizeIncomeCategoryName(categoryName: String): String {
        val trimmed = categoryName.trim()
        val category = Category.fromDisplayName(trimmed, CategoryType.INCOME)
        return if (category.categoryType == CategoryType.INCOME) {
            category.displayName
        } else {
            trimmed
        }
    }

    private fun parseCategoryType(value: String?): CategoryType? {
        val normalized = value?.takeIf { it.isNotBlank() } ?: CategoryType.EXPENSE.name
        return CategoryType.entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
    }

    private fun resultByAffected(affected: Int, resourceId: Long = NO_RESOURCE_ID): MoneyTalkOperationResult {
        return MoneyTalkOperationResult(
            success = affected > 0,
            resultCode = if (affected > 0) RESULT_SUCCESS else RESULT_NOT_FOUND,
            affectedCount = affected,
            resourceId = resourceId
        )
    }

    private fun failure(resultCode: String): MoneyTalkOperationResult {
        return MoneyTalkOperationResult(
            success = false,
            resultCode = resultCode,
            affectedCount = 0,
            resourceId = NO_RESOURCE_ID
        )
    }

    private companion object {
        private const val NO_RESOURCE_ID = 0L
        private const val MIN_RECURRING_DAY = 1
        private const val MAX_RECURRING_DAY = 31
        private const val MIN_MONTH_START_DAY = 1
        private const val MAX_MONTH_START_DAY = 31
        private const val RESULT_SUCCESS = "success"
        private const val RESULT_NOT_FOUND = "not_found"
        private const val RESULT_ALREADY_EXISTS = "already_exists"
        private const val RESULT_MISSING_REQUIRED_PARAMETER = "missing_required_parameter"
        private const val RESULT_INVALID_ARGUMENT = "invalid_argument"
        private const val DEFAULT_BUDGET_MONTH = "default"
        private const val TOTAL_BUDGET_CATEGORY = "전체"
        private const val UNCATEGORIZED_CATEGORY = "미분류"
        private const val DEFAULT_INCOME_TYPE = "입금"
        private const val MANUAL_CARD_NAME = "수동입력"
        private const val MANUAL_SMS_PREFIX = "manual_app_function_"
        private const val MANUAL_INCOME_SMS_PREFIX = "manual_income_app_function_"
        private const val SOURCE_APP_FUNCTION = "chat"
        private const val EMPTY_TEXT = ""
        private const val DEFAULT_CUSTOM_CATEGORY_EMOJI = "📦"
    }
}
