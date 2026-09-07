package com.sanha.moneytalk.feature.chat.data

import com.sanha.moneytalk.core.database.dao.BudgetDao
import com.sanha.moneytalk.core.database.entity.BudgetEntity
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.util.ActionResult
import com.sanha.moneytalk.core.util.ActionType
import com.sanha.moneytalk.core.sms.DeletedSmsTracker
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.CategoryReferenceProvider
import com.sanha.moneytalk.core.util.DataAction
import com.sanha.moneytalk.core.util.StoreAliasManager
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

/** 채팅에서 허용한 거래/분류/제외 키워드/예산 수정 액션을 실행한다. */
class ChatActionExecutor @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val smsExclusionRepository: com.sanha.moneytalk.core.database.SmsExclusionRepository,
    private val categoryReferenceProvider: CategoryReferenceProvider,
    private val dataRefreshEvent: DataRefreshEvent,
    private val budgetDao: BudgetDao
) {
    private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    private fun normalizeExpenseCategoryName(categoryName: String): String {
        val trimmed = categoryName.trim()
        val category = Category.fromDisplayName(trimmed)
        return if (category == Category.ETC && trimmed != Category.ETC.displayName) {
            trimmed
        } else {
            category.displayName
        }
    }

    /**
     * Gemini가 요청한 액션을 실행
     */
    suspend fun execute(action: DataAction): ActionResult {
        return when (action.type) {
            ActionType.UPDATE_CATEGORY -> {
                val expenseId = action.expenseId
                val newCategory = action.newCategory

                if (expenseId == null || newCategory == null) {
                    ActionResult(
                        actionType = ActionType.UPDATE_CATEGORY,
                        success = false,
                        message = "지출 ID 또는 새 카테고리가 지정되지 않았습니다."
                    )
                } else {
                    val normalizedCategory = normalizeExpenseCategoryName(newCategory)
                    val affected = expenseRepository.updateCategoryById(expenseId, normalizedCategory)
                    if (affected > 0) categoryReferenceProvider.invalidateCache()
                    ActionResult(
                        actionType = ActionType.UPDATE_CATEGORY,
                        success = affected > 0,
                        message = if (affected > 0) "ID $expenseId 항목의 카테고리를 '$normalizedCategory'(으)로 변경했습니다." else "해당 항목을 찾을 수 없습니다.",
                        affectedCount = affected
                    )
                }
            }

            ActionType.UPDATE_CATEGORY_BY_STORE -> {
                val storeName = action.storeName
                val newCategory = action.newCategory

                if (storeName == null || newCategory == null) {
                    ActionResult(
                        actionType = ActionType.UPDATE_CATEGORY_BY_STORE,
                        success = false,
                        message = "가게명 또는 새 카테고리가 지정되지 않았습니다."
                    )
                } else {
                    val normalizedCategory = normalizeExpenseCategoryName(newCategory)
                    // StoreAliasManager를 사용하여 모든 별칭에 대해 업데이트
                    val aliases = StoreAliasManager.getAllAliases(storeName)
                    var totalAffected = 0
                    for (alias in aliases) {
                        totalAffected += expenseRepository.updateCategoryByStoreNameContaining(
                            alias,
                            normalizedCategory
                        )
                    }
                    if (totalAffected > 0) categoryReferenceProvider.invalidateCache()
                    ActionResult(
                        actionType = ActionType.UPDATE_CATEGORY_BY_STORE,
                        success = totalAffected > 0,
                        message = if (totalAffected > 0) "'$storeName' 관련 ${totalAffected}건의 카테고리를 '$normalizedCategory'(으)로 변경했습니다." else "'$storeName' 관련 항목을 찾을 수 없습니다.",
                        affectedCount = totalAffected
                    )
                }
            }

            ActionType.UPDATE_CATEGORY_BY_KEYWORD -> {
                val keyword = action.searchKeyword
                val newCategory = action.newCategory

                if (keyword == null || newCategory == null) {
                    ActionResult(
                        actionType = ActionType.UPDATE_CATEGORY_BY_KEYWORD,
                        success = false,
                        message = "검색 키워드 또는 새 카테고리가 지정되지 않았습니다."
                    )
                } else {
                    val normalizedCategory = normalizeExpenseCategoryName(newCategory)
                    // StoreAliasManager를 사용하여 모든 별칭에 대해 업데이트
                    val aliases = StoreAliasManager.getAllAliases(keyword)
                    var totalAffected = 0
                    for (alias in aliases) {
                        totalAffected += expenseRepository.updateCategoryByStoreNameContaining(
                            alias,
                            normalizedCategory
                        )
                    }
                    if (totalAffected > 0) categoryReferenceProvider.invalidateCache()
                    ActionResult(
                        actionType = ActionType.UPDATE_CATEGORY_BY_KEYWORD,
                        success = totalAffected > 0,
                        message = if (totalAffected > 0) "'$keyword' 관련 ${totalAffected}건의 카테고리를 '$normalizedCategory'(으)로 변경했습니다." else "'$keyword' 관련 항목을 찾을 수 없습니다.",
                        affectedCount = totalAffected
                    )
                }
            }

            ActionType.DELETE_EXPENSE -> {
                val expenseId = action.expenseId

                if (expenseId == null) {
                    ActionResult(
                        actionType = ActionType.DELETE_EXPENSE,
                        success = false,
                        message = "삭제할 지출 ID가 지정되지 않았습니다."
                    )
                } else {
                    val expense = expenseRepository.getExpenseById(expenseId)
                    if (expense != null) {
                        DeletedSmsTracker.markDeleted(expense.smsId)
                        expenseRepository.deleteById(expenseId)
                        ActionResult(
                            actionType = ActionType.DELETE_EXPENSE,
                            success = true,
                            message = "ID $expenseId 항목 (${expense.storeName}: ${
                                numberFormat.format(
                                    expense.amount
                                )
                            }원)을 삭제했습니다.",
                            affectedCount = 1
                        )
                    } else {
                        ActionResult(
                            actionType = ActionType.DELETE_EXPENSE,
                            success = false,
                            message = "ID $expenseId 항목을 찾을 수 없습니다."
                        )
                    }
                }
            }

            ActionType.DELETE_BY_KEYWORD -> {
                val keyword = action.searchKeyword
                if (keyword.isNullOrBlank()) {
                    ActionResult(
                        actionType = ActionType.DELETE_BY_KEYWORD,
                        success = false,
                        message = "삭제할 검색 키워드가 지정되지 않았습니다."
                    )
                } else {
                    val deletedCount = expenseRepository.deleteByKeyword(keyword)
                    ActionResult(
                        actionType = ActionType.DELETE_BY_KEYWORD,
                        success = deletedCount > 0,
                        message = if (deletedCount > 0) "'$keyword' 포함 항목 ${deletedCount}건을 삭제했습니다." else "'$keyword' 포함 항목이 없습니다.",
                        affectedCount = deletedCount
                    )
                }
            }

            ActionType.DELETE_DUPLICATES -> {
                val deletedCount = expenseRepository.deleteDuplicates()
                ActionResult(
                    actionType = ActionType.DELETE_DUPLICATES,
                    success = deletedCount > 0,
                    message = if (deletedCount > 0) "중복 ${deletedCount}건을 삭제했습니다." else "중복 항목이 없습니다.",
                    affectedCount = deletedCount
                )
            }

            ActionType.ADD_EXPENSE -> {
                val storeName = action.storeName
                val amount = action.amount
                val dateStr = action.date

                if (storeName.isNullOrBlank() || amount == null || amount <= 0) {
                    ActionResult(
                        actionType = ActionType.ADD_EXPENSE,
                        success = false,
                        message = "가게명과 금액은 필수입니다."
                    )
                } else {
                    val dateTime = if (!dateStr.isNullOrBlank()) {
                        try {
                            SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).parse(dateStr)?.time
                                ?: System.currentTimeMillis()
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }
                    } else {
                        System.currentTimeMillis()
                    }

                    val expense = ExpenseEntity(
                        storeName = storeName,
                        amount = amount,
                        dateTime = dateTime,
                        cardName = action.cardName ?: "수동입력",
                        category = action.newCategory?.let(::normalizeExpenseCategoryName) ?: "미분류",
                        originalSms = "",
                        smsId = "manual_${System.currentTimeMillis()}",
                        memo = action.memo
                    )
                    val id = expenseRepository.insert(expense)
                    ActionResult(
                        actionType = ActionType.ADD_EXPENSE,
                        success = true,
                        message = "'$storeName' ${numberFormat.format(amount)}원 지출을 추가했습니다. (ID: $id)",
                        affectedCount = 1
                    )
                }
            }

            ActionType.UPDATE_MEMO -> {
                val expenseId = action.expenseId
                if (expenseId == null) {
                    ActionResult(
                        actionType = ActionType.UPDATE_MEMO,
                        success = false,
                        message = "수정할 지출 ID가 지정되지 않았습니다."
                    )
                } else {
                    val expense = expenseRepository.getExpenseById(expenseId)
                    if (expense != null) {
                        val count = expenseRepository.updateMemo(expenseId, action.memo)
                        ActionResult(
                            actionType = ActionType.UPDATE_MEMO,
                            success = count > 0,
                            message = "ID $expenseId (${expense.storeName})의 메모를 '${action.memo ?: ""}'(으)로 수정했습니다.",
                            affectedCount = count
                        )
                    } else {
                        ActionResult(
                            actionType = ActionType.UPDATE_MEMO,
                            success = false,
                            message = "ID $expenseId 항목을 찾을 수 없습니다."
                        )
                    }
                }
            }

            ActionType.UPDATE_STORE_NAME -> {
                val id = action.expenseId
                val name = action.newStoreName
                if (id == null || name.isNullOrBlank()) {
                    ActionResult(
                        actionType = ActionType.UPDATE_STORE_NAME,
                        success = false,
                        message = "수정할 지출 ID와 새 가게명은 필수입니다."
                    )
                } else {
                    val expense = expenseRepository.getExpenseById(id)
                    if (expense != null) {
                        val oldName = expense.storeName
                        val count = expenseRepository.updateStoreName(id, name)
                        ActionResult(
                            actionType = ActionType.UPDATE_STORE_NAME,
                            success = count > 0,
                            message = "ID ${id}의 가게명을 '$oldName' → '$name'(으)로 수정했습니다.",
                            affectedCount = count
                        )
                    } else {
                        ActionResult(
                            actionType = ActionType.UPDATE_STORE_NAME,
                            success = false,
                            message = "ID $id 항목을 찾을 수 없습니다."
                        )
                    }
                }
            }

            ActionType.UPDATE_AMOUNT -> {
                val expenseId = action.expenseId
                val newAmount = action.newAmount
                if (expenseId == null || newAmount == null || newAmount <= 0) {
                    ActionResult(
                        actionType = ActionType.UPDATE_AMOUNT,
                        success = false,
                        message = "수정할 지출 ID와 새 금액은 필수입니다."
                    )
                } else {
                    val expense = expenseRepository.getExpenseById(expenseId)
                    if (expense != null) {
                        val oldAmount = expense.amount
                        val count = expenseRepository.updateAmount(expenseId, newAmount)
                        ActionResult(
                            actionType = ActionType.UPDATE_AMOUNT,
                            success = count > 0,
                            message = "ID $expenseId (${expense.storeName})의 금액을 ${
                                numberFormat.format(
                                    oldAmount
                                )
                            }원 → ${numberFormat.format(newAmount)}원으로 수정했습니다.",
                            affectedCount = count
                        )
                    } else {
                        ActionResult(
                            actionType = ActionType.UPDATE_AMOUNT,
                            success = false,
                            message = "ID $expenseId 항목을 찾을 수 없습니다."
                        )
                    }
                }
            }

            ActionType.ADD_SMS_EXCLUSION -> {
                val keyword = action.searchKeyword
                if (keyword.isNullOrBlank()) {
                    ActionResult(
                        actionType = ActionType.ADD_SMS_EXCLUSION,
                        success = false,
                        message = "추가할 제외 키워드가 필요합니다."
                    )
                } else {
                    val added = smsExclusionRepository.addKeyword(keyword, source = "chat")
                    ActionResult(
                        actionType = ActionType.ADD_SMS_EXCLUSION,
                        success = added,
                        message = if (added) "\"$keyword\" 키워드를 SMS 제외 목록에 추가했습니다. 다음 동기화부터 적용됩니다."
                        else "\"$keyword\" 키워드가 이미 존재합니다.",
                        affectedCount = if (added) 1 else 0
                    )
                }
            }

            ActionType.REMOVE_SMS_EXCLUSION -> {
                val keyword = action.searchKeyword
                if (keyword.isNullOrBlank()) {
                    ActionResult(
                        actionType = ActionType.REMOVE_SMS_EXCLUSION,
                        success = false,
                        message = "삭제할 제외 키워드가 필요합니다."
                    )
                } else {
                    val deleted = smsExclusionRepository.removeKeyword(keyword)
                    ActionResult(
                        actionType = ActionType.REMOVE_SMS_EXCLUSION,
                        success = deleted > 0,
                        message = if (deleted > 0) "\"$keyword\" 키워드를 SMS 제외 목록에서 삭제했습니다."
                        else "\"$keyword\" 키워드를 찾을 수 없거나 기본 키워드라 삭제할 수 없습니다.",
                        affectedCount = deleted
                    )
                }
            }

            ActionType.SET_BUDGET -> {
                val targetCategory = action.category ?: action.newCategory
                val amount = action.amount
                if (targetCategory.isNullOrBlank() || amount == null) {
                    ActionResult(
                        actionType = ActionType.SET_BUDGET,
                        success = false,
                        message = "카테고리 또는 금액이 지정되지 않았습니다."
                    )
                } else {
                    val normalizedCategory = if (targetCategory == "전체") {
                        targetCategory
                    } else {
                        normalizeExpenseCategoryName(targetCategory)
                    }
                    budgetDao.insert(
                        BudgetEntity(
                            category = normalizedCategory,
                            monthlyLimit = amount,
                            yearMonth = "default"
                        )
                    )
                    dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
                    ActionResult(
                        actionType = ActionType.SET_BUDGET,
                        success = true,
                        message = "'$normalizedCategory' 카테고리의 월 예산을 ${numberFormat.format(amount)}원으로 설정했습니다.",
                        affectedCount = 1
                    )
                }
            }
        }
    }

}
