package com.sanha.moneytalk.feature.transactionactions.data

import androidx.room.withTransaction
import com.sanha.moneytalk.core.database.AppDatabase
import com.sanha.moneytalk.core.database.CustomCategoryRepository
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.CategoryInfo
import com.sanha.moneytalk.core.model.CategoryProvider
import com.sanha.moneytalk.core.model.CategoryType
import com.sanha.moneytalk.core.sms.DeletedSmsTracker
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.feature.transactionactions.model.QuickTransaction
import com.sanha.moneytalk.feature.transactionactions.model.TransactionQuickPatch
import com.sanha.moneytalk.feature.transactionactions.model.TransactionQuickUpdateResult
import com.sanha.moneytalk.feature.transactionactions.model.TransactionTarget
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 한 거래의 빠른 수정/삭제만 담당한다. 거래처 규칙, 분류 학습, 다른 거래는 수정하지 않는다. */
@Singleton
class TransactionQuickActionService @Inject constructor(
    private val database: AppDatabase,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val categoryProvider: CategoryProvider,
    private val customCategoryRepository: CustomCategoryRepository,
    private val dataRefreshEvent: DataRefreshEvent
) {
    suspend fun load(target: TransactionTarget): QuickTransaction? = withContext(Dispatchers.IO) {
        if (target.id <= 0L) return@withContext null
        when (target) {
            is TransactionTarget.Expense -> expenseRepository.getExpenseById(target.id)?.toQuickTransaction()
            is TransactionTarget.Income -> incomeRepository.getIncomeById(target.id)?.toQuickTransaction()
        }
    }

    suspend fun categories(type: CategoryType): List<CategoryInfo> = withContext(Dispatchers.IO) {
        when (type) {
            CategoryType.EXPENSE -> categoryProvider.getExpenseEntries()
            CategoryType.INCOME -> categoryProvider.getIncomeEntries()
            CategoryType.TRANSFER -> categoryProvider.getTransferEntries()
        }
    }

    suspend fun update(
        target: TransactionTarget,
        patch: TransactionQuickPatch
    ): TransactionQuickUpdateResult = withContext(Dispatchers.IO) {
        if (target.id <= 0L) return@withContext TransactionQuickUpdateResult.Missing
        val result = database.withTransaction {
            when (target) {
                is TransactionTarget.Expense -> updateExpense(target.id, patch)
                is TransactionTarget.Income -> updateIncome(target.id, patch)
            }
        }
        if (result is TransactionQuickUpdateResult.Updated && result.changed) {
            dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
        }
        result
    }

    suspend fun delete(target: TransactionTarget): Boolean = withContext(Dispatchers.IO) {
        if (target.id <= 0L) return@withContext false
        currentCoroutineContext().ensureActive()
        // 삭제가 시작되면 DB commit과 재수집 방지 기록을 함께 마친 뒤 호출부에 취소를 전파한다.
        // 네트워크 작업은 없으며 실패한 DB 삭제에는 tombstone을 만들지 않는다.
        val deleted = withContext(NonCancellable) {
            val result = database.withTransaction {
                when (target) {
                    is TransactionTarget.Expense -> {
                        val row = expenseRepository.getExpenseById(target.id)
                            ?: return@withTransaction null
                        expenseRepository.deleteById(row.id)
                        DeletedTransaction(row.smsId)
                    }
                    is TransactionTarget.Income -> {
                        val row = incomeRepository.getIncomeById(target.id)
                            ?: return@withTransaction null
                        incomeRepository.deleteById(row.id)
                        DeletedTransaction(row.smsId)
                    }
                }
            }
            if (result != null) {
                result.smsId?.let(DeletedSmsTracker::markDeleted)
                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
            }
            result != null
        }
        currentCoroutineContext().ensureActive()
        deleted
    }

    private suspend fun updateExpense(id: Long, patch: TransactionQuickPatch): TransactionQuickUpdateResult {
        val current = expenseRepository.getExpenseById(id) ?: return TransactionQuickUpdateResult.Missing
        val category = patch.category?.trim() ?: current.category
        if (category != current.category && !isValidCategory(category, current.categoryType())) {
            return TransactionQuickUpdateResult.InvalidCategory
        }
        val updated = current.copy(
            category = category,
            isFixed = patch.isFixed ?: current.isFixed,
            isExcludedFromStats = patch.isExcludedFromStats ?: current.isExcludedFromStats
        )
        if (updated.category != current.category) {
            expenseRepository.updateCategoryById(id, updated.category)
        }
        if (updated.isFixed != current.isFixed) {
            expenseRepository.updateFixedById(id, updated.isFixed)
        }
        if (updated.isExcludedFromStats != current.isExcludedFromStats) {
            expenseRepository.updateStatsExcludedById(id, updated.isExcludedFromStats)
        }
        return TransactionQuickUpdateResult.Updated(updated.toQuickTransaction(), updated != current)
    }

    private suspend fun updateIncome(id: Long, patch: TransactionQuickPatch): TransactionQuickUpdateResult {
        val current = incomeRepository.getIncomeById(id) ?: return TransactionQuickUpdateResult.Missing
        if (patch.isExcludedFromStats != null) return TransactionQuickUpdateResult.UnsupportedField
        val category = patch.category?.trim() ?: current.category
        if (category != current.category && !isValidCategory(category, CategoryType.INCOME)) {
            return TransactionQuickUpdateResult.InvalidCategory
        }
        val isRecurring = patch.isFixed ?: current.isRecurring
        val recurringDay = if (isRecurring == current.isRecurring) {
            current.recurringDay
        } else if (isRecurring) {
            current.recurringDay?.takeIf { it in 1..31 }
                ?: Calendar.getInstance().apply { timeInMillis = current.dateTime }.get(Calendar.DAY_OF_MONTH)
        } else {
            null
        }
        val updated = current.copy(category = category, isRecurring = isRecurring, recurringDay = recurringDay)
        if (updated != current) {
            // 최신 행을 같은 Room transaction 안에서 복사한다. @Update는 없는 ID를 삽입하지 않는다.
            incomeRepository.update(updated)
        }
        return TransactionQuickUpdateResult.Updated(updated.toQuickTransaction(), updated != current)
    }

    private suspend fun isValidCategory(name: String, type: CategoryType): Boolean {
        if (name.isBlank()) return false
        if (Category.entries.any { it.categoryType == type && it.displayName == name }) return true
        // Picker의 캐시와 무관하게 삭제된 사용자 카테고리를 저장하지 않도록 최신 DB로 검증한다.
        return customCategoryRepository.getByType(type).any { it.displayName == name }
    }

    private fun ExpenseEntity.categoryType(): CategoryType =
        if (transactionType == CategoryType.TRANSFER.name) CategoryType.TRANSFER else CategoryType.EXPENSE

    private fun ExpenseEntity.toQuickTransaction() = QuickTransaction(
        target = TransactionTarget.Expense(id),
        title = storeName,
        amount = amount,
        category = category,
        categoryType = categoryType(),
        isFixed = isFixed,
        isExcludedFromStats = isExcludedFromStats
    )

    private fun IncomeEntity.toQuickTransaction() = QuickTransaction(
        target = TransactionTarget.Income(id),
        title = description.ifBlank { source },
        amount = amount,
        category = category,
        categoryType = CategoryType.INCOME,
        isFixed = isRecurring,
        isExcludedFromStats = null
    )

    private data class DeletedTransaction(val smsId: String?)
}
