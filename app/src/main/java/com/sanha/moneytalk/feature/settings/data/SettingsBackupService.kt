package com.sanha.moneytalk.feature.settings.data

import com.sanha.moneytalk.core.database.CustomCategoryRepository
import com.sanha.moneytalk.core.database.OwnedCardRepository
import com.sanha.moneytalk.core.database.SmsExclusionRepository
import com.sanha.moneytalk.core.database.dao.BudgetDao
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.model.CategoryProvider
import com.sanha.moneytalk.core.util.BackupData
import com.sanha.moneytalk.core.util.DataBackupManager
import com.sanha.moneytalk.core.util.ExportFilter
import com.sanha.moneytalk.core.util.ExportFormat
import com.sanha.moneytalk.feature.home.data.CategoryRepository
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.feature.home.data.StoreRuleRepository
import com.sanha.moneytalk.feature.home.data.StoreRuleSyncService
import javax.inject.Inject

data class SettingsRestoreCounts(
    val expenses: Int,
    val incomes: Int,
    val categorySettings: Int,
    val storeRules: Int,
    val userSettings: Int
)

/** 백업에 포함할 저장소와 복원 순서를 관리한다. UI 상태와 파일 선택은 호출자가 담당한다. */
class SettingsBackupService @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryRepository: CategoryRepository,
    private val customCategoryRepository: CustomCategoryRepository,
    private val categoryProvider: CategoryProvider,
    private val storeRuleRepository: StoreRuleRepository,
    private val storeRuleSyncService: StoreRuleSyncService,
    private val budgetDao: BudgetDao,
    private val ownedCardRepository: OwnedCardRepository,
    private val smsExclusionRepository: SmsExclusionRepository
) {
    suspend fun prepare(filter: ExportFilter, format: ExportFormat, monthStartDay: Int): String {
        var expenses = expenseRepository.getAllExpensesOnce()
        var incomes = incomeRepository.getAllIncomesOnce()

        // 필터 적용
        if (filter.includeExpenses) {
            expenses = DataBackupManager.filterExpenses(expenses, filter)
        } else {
            expenses = emptyList()
        }

        if (filter.includeIncomes) {
            incomes = DataBackupManager.filterIncomes(incomes, filter)
        } else {
            incomes = emptyList()
        }

        val savedMonthlyIncome = settingsDataStore.getMonthlyIncome()
        return when (format) {
            ExportFormat.JSON -> DataBackupManager.createBackupJson(
                expenses = expenses,
                incomes = incomes,
                monthlyIncome = savedMonthlyIncome,
                monthStartDay = monthStartDay,
                categoryMappings = categoryRepository.getAllMappingsOnce(),
                customCategories = customCategoryRepository.getAll(),
                storeRules = storeRuleRepository.getAllOnce(),
                budgets = budgetDao.getBudgetsByMonthOnce("default"),
                ownedCards = ownedCardRepository.getAllCardsOnce(),
                smsExclusionKeywords = smsExclusionRepository.getUserKeywordEntities()
            )

            ExportFormat.CSV -> DataBackupManager.createCombinedCsv(expenses, incomes)
        }
    }

    suspend fun restore(backupData: BackupData): SettingsRestoreCounts {
        // 설정 복원
        settingsDataStore.saveMonthlyIncome(backupData.settings.monthlyIncome)
        settingsDataStore.saveMonthStartDay(backupData.settings.monthStartDay)

        // 지출 데이터 복원
        val expenses = DataBackupManager.convertToExpenseEntities(backupData.expenses.orEmpty())
        if (expenses.isNotEmpty()) {
            expenseRepository.insertAll(expenses)
        }

        // 수입 데이터 복원
        val incomes = DataBackupManager.convertToIncomeEntities(backupData.incomes.orEmpty())
        if (incomes.isNotEmpty()) {
            incomeRepository.insertAll(incomes)
        }

        val categoryMappings = DataBackupManager.convertToCategoryMappingEntities(
            backupData.categoryMappings.orEmpty()
        )
        categoryRepository.restoreMappings(categoryMappings)

        val customCategories = DataBackupManager.convertToCustomCategoryEntities(
            backupData.customCategories.orEmpty()
        )
        customCategoryRepository.insertAll(customCategories)

        val storeRules = DataBackupManager.convertToStoreRuleEntities(
            backupData.storeRules.orEmpty()
        )
        storeRules.forEach { rule ->
            storeRuleSyncService.applyRuleChange(
                previousRule = null,
                newRule = rule
            )
        }
        storeRuleSyncService.reapplyAllRules()

        val budgets = DataBackupManager.convertToBudgetEntities(backupData.budgets.orEmpty())
        if (budgets.isNotEmpty()) {
            budgetDao.insertAll(budgets)
        }

        val ownedCards = DataBackupManager.convertToOwnedCardEntities(
            backupData.ownedCards.orEmpty()
        )
        ownedCardRepository.upsertAll(ownedCards)

        val smsExclusionKeywords = DataBackupManager.convertToSmsExclusionKeywordEntities(
            backupData.smsExclusionKeywords.orEmpty()
        )
        smsExclusionRepository.restoreKeywords(smsExclusionKeywords)
        expenseRepository.deleteDuplicates()
        incomeRepository.deleteDuplicates()

        categoryProvider.invalidateCache()
        return SettingsRestoreCounts(
            expenses = expenses.size,
            incomes = incomes.size,
            categorySettings = categoryMappings.size + customCategories.size,
            storeRules = storeRules.size,
            userSettings = budgets.size + ownedCards.size + smsExclusionKeywords.size
        )
    }
}
