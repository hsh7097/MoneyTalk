package com.sanha.moneytalk.feature.transactionedit

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.CategoryType
import com.sanha.moneytalk.core.model.TransferDirection
import com.sanha.moneytalk.core.datastore.SettingsDataStore
import com.sanha.moneytalk.core.ui.AppSnackbarBus
import com.sanha.moneytalk.core.sms2.DeletedSmsTracker
import com.sanha.moneytalk.core.util.DataRefreshEvent
import com.sanha.moneytalk.core.util.MoneyTalkLogger
import com.sanha.moneytalk.feature.home.data.ExpenseRepository
import com.sanha.moneytalk.feature.home.data.IncomeRepository
import com.sanha.moneytalk.feature.home.data.StoreRuleRepository
import com.sanha.moneytalk.feature.home.data.StoreRuleSyncService
import com.sanha.moneytalk.core.database.entity.StoreRuleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@Stable
data class TransactionEditUiState(
    val isNew: Boolean = true,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val isLoading: Boolean = true,
    val amount: String = "",
    val storeName: String = "",
    val category: String = Category.ETC.displayName,
    val cardName: String = "",
    val incomeType: String = "",
    val source: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
    val hour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    val minute: Int = Calendar.getInstance().get(Calendar.MINUTE),
    val memo: String = "",
    val originalSms: String = "",
    val isFixed: Boolean = false,
    val transferDirection: TransferDirection? = null,
    /** 카테고리 변경을 동일 거래처에 일괄 적용 */
    val applyCategoryToAll: Boolean = false,
    /** 고정지출 변경을 동일 거래처에 일괄 적용 */
    val applyFixedToAll: Boolean = false,
    /** 거래처 규칙 매칭 키워드 (일괄 적용 시 사용) */
    val ruleKeyword: String = "",
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false
) {
    /** 하위 호환용 계산 프로퍼티 */
    val isIncome: Boolean get() = transactionType == TransactionType.INCOME
    val isTransfer: Boolean get() = transactionType == TransactionType.TRANSFER
}

/**
 * 거래 편집/추가 ViewModel.
 *
 * SavedStateHandle로 Intent extra를 수신:
 * - extra_expense_id: 기존 지출 편집 시 ID, -1이면 새 거래
 * - extra_income_id: 기존 수입 편집 시 ID, -1이면 무시
 * - extra_initial_date: 새 거래 추가 시 기본 날짜 (Long)
 *
 * 수입/지출/이체 전환:
 * - 지출 ↔ 이체: category만 변경 (같은 ExpenseEntity)
 * - 지출/이체 → 수입: 저장 시 ExpenseEntity 삭제 + IncomeEntity 생성
 * - 수입 → 지출/이체: 저장 시 IncomeEntity 삭제 + ExpenseEntity 생성
 */
private const val EXTRA_EXPENSE_ID = "extra_expense_id"
private const val EXTRA_INCOME_ID = "extra_income_id"
private const val EXTRA_INITIAL_DATE = "extra_initial_date"

@HiltViewModel
class TransactionEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val expenseRepository: ExpenseRepository,
    private val incomeRepository: IncomeRepository,
    private val dataRefreshEvent: DataRefreshEvent,
    private val snackbarBus: AppSnackbarBus,
    private val storeRuleRepository: StoreRuleRepository,
    private val storeRuleSyncService: StoreRuleSyncService,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val expenseId: Long = savedStateHandle[EXTRA_EXPENSE_ID] ?: -1L
    private val incomeId: Long = savedStateHandle[EXTRA_INCOME_ID] ?: -1L
    private val initialDate: Long = savedStateHandle[EXTRA_INITIAL_DATE]
        ?: System.currentTimeMillis()

    private val _uiState = MutableStateFlow(TransactionEditUiState())
    val uiState: StateFlow<TransactionEditUiState> = _uiState.asStateFlow()

    /** 원본 entity (수정 시 smsId 등 보존용) */
    private var originalExpenseEntity: ExpenseEntity? = null
    private var originalIncomeEntity: IncomeEntity? = null
    /** 편집 진입 시점에 매칭된 거래처 규칙 (키워드 변경 시 이전 규칙 정리용) */
    private var originalMatchedRule: StoreRuleEntity? = null

    /** 최초 로드 시 타입 (크로스 테이블 이동 판단용) */
    private var originalTransactionType: TransactionType = TransactionType.EXPENSE

    init {
        when {
            incomeId > 0 -> loadIncome(incomeId)
            expenseId > 0 -> loadExpense(expenseId)
            else -> initNewExpense()
        }
    }

    private fun loadExpense(id: Long) {
        viewModelScope.launch {
            val expense = expenseRepository.getExpenseById(id)
            if (expense != null) {
                originalExpenseEntity = expense
                val cal = Calendar.getInstance().apply { timeInMillis = expense.dateTime }
                val type = if (expense.transactionType == "TRANSFER") {
                    TransactionType.TRANSFER
                } else {
                    TransactionType.EXPENSE
                }
                val direction = TransferDirection.fromDbValue(expense.transferDirection)
                originalTransactionType = type

                // StoreRule이 있으면 "동일 거래처 일괄 적용" 체크박스만 사전 체크
                // DB 값(category, isFixed)은 소급 적용 시 이미 반영되어 있으므로 override하지 않음
                // → 사용자가 개별 변경한 DB 값을 존중
                // contains 매칭으로 규칙 조회 (keyword가 storeName에 포함되는 규칙)
                val matchingRule = if (type == TransactionType.EXPENSE) {
                    storeRuleRepository.findMatchingRule(expense.storeName.trim())
                } else {
                    null
                }
                originalMatchedRule = matchingRule
                val hasCategoryRule = matchingRule?.category != null
                val hasFixedRule = matchingRule?.isFixed != null

                _uiState.update {
                    it.copy(
                        isNew = false,
                        transactionType = type,
                        isLoading = false,
                        amount = expense.amount.toString(),
                        storeName = expense.storeName,
                        category = expense.category,
                        cardName = expense.cardName,
                        dateMillis = expense.dateTime,
                        hour = cal.get(Calendar.HOUR_OF_DAY),
                        minute = cal.get(Calendar.MINUTE),
                        memo = expense.memo ?: "",
                        originalSms = expense.originalSms,
                        isFixed = expense.isFixed,
                        transferDirection = direction,
                        applyCategoryToAll = hasCategoryRule,
                        applyFixedToAll = hasFixedRule,
                        ruleKeyword = matchingRule?.keyword ?: expense.storeName.trim()
                    )
                }
            } else {
                initNewExpense()
            }
        }
    }

    private fun loadIncome(id: Long) {
        viewModelScope.launch {
            val income = incomeRepository.getIncomeById(id)
            if (income != null) {
                originalIncomeEntity = income
                originalMatchedRule = null
                val cal = Calendar.getInstance().apply { timeInMillis = income.dateTime }
                originalTransactionType = TransactionType.INCOME
                _uiState.update {
                    it.copy(
                        isNew = false,
                        transactionType = TransactionType.INCOME,
                        isLoading = false,
                        amount = income.amount.toString(),
                        storeName = income.description,
                        category = income.category,
                        incomeType = income.type,
                        source = income.source,
                        dateMillis = income.dateTime,
                        hour = cal.get(Calendar.HOUR_OF_DAY),
                        minute = cal.get(Calendar.MINUTE),
                        memo = income.memo ?: "",
                        originalSms = income.originalSms ?: "",
                        isFixed = income.isRecurring
                    )
                }
            } else {
                initNewExpense()
            }
        }
    }

    private fun initNewExpense() {
        val cal = Calendar.getInstance().apply { timeInMillis = initialDate }
        originalMatchedRule = null
        originalTransactionType = TransactionType.EXPENSE
        _uiState.update {
            it.copy(
                isNew = true,
                transactionType = TransactionType.EXPENSE,
                isLoading = false,
                dateMillis = initialDate,
                hour = cal.get(Calendar.HOUR_OF_DAY),
                minute = cal.get(Calendar.MINUTE)
            )
        }
    }

    /**
     * 거래 유형 변경.
     * 타입 변경 시 카테고리를 미분류로 리셋.
     */
    fun setTransactionType(type: TransactionType) {
        val currentType = _uiState.value.transactionType
        if (currentType == type) return

        _uiState.update { state ->
            when (type) {
                TransactionType.TRANSFER -> state.copy(
                    transactionType = type,
                    category = Category.UNCLASSIFIED.displayName,
                    transferDirection = TransferDirection.WITHDRAWAL,
                    isFixed = false,
                    applyCategoryToAll = false,
                    applyFixedToAll = false
                )
                TransactionType.EXPENSE -> state.copy(
                    transactionType = type,
                    category = Category.UNCLASSIFIED.displayName,
                    transferDirection = null,
                    applyCategoryToAll = false,
                    applyFixedToAll = false
                )
                TransactionType.INCOME -> state.copy(
                    transactionType = type,
                    category = Category.INCOME_UNCLASSIFIED.displayName,
                    transferDirection = null,
                    applyCategoryToAll = false,
                    applyFixedToAll = false
                )
            }
        }
    }

    fun updateTransferDirection(direction: TransferDirection) {
        _uiState.update { it.copy(transferDirection = direction) }
    }

    fun updateAmount(value: String) {
        _uiState.update { it.copy(amount = value) }
    }

    fun updateStoreName(value: String) {
        _uiState.update { it.copy(storeName = value) }
    }

    fun updateCategory(value: String) {
        _uiState.update { it.copy(category = value) }
    }

    fun updateIncomeType(value: String) {
        _uiState.update { it.copy(incomeType = value) }
    }

    fun updateSource(value: String) {
        _uiState.update { it.copy(source = value) }
    }

    fun updateDate(millis: Long) {
        _uiState.update { it.copy(dateMillis = millis) }
    }

    fun updateTime(hour: Int, minute: Int) {
        _uiState.update { it.copy(hour = hour, minute = minute) }
    }

    fun updateMemo(value: String) {
        _uiState.update { it.copy(memo = value) }
    }

    fun updateIsFixed(value: Boolean) {
        _uiState.update { it.copy(isFixed = value) }
    }

    fun updateApplyCategoryToAll(value: Boolean) {
        _uiState.update { state ->
            state.copy(
                applyCategoryToAll = value,
                ruleKeyword = if (value && state.ruleKeyword.isBlank()) {
                    state.storeName.trim()
                } else {
                    state.ruleKeyword
                }
            )
        }
    }

    fun updateApplyFixedToAll(value: Boolean) {
        _uiState.update { state ->
            state.copy(
                applyFixedToAll = value,
                ruleKeyword = if (value && state.ruleKeyword.isBlank()) {
                    state.storeName.trim()
                } else {
                    state.ruleKeyword
                }
            )
        }
    }

    fun updateRuleKeyword(value: String) {
        _uiState.update { it.copy(ruleKeyword = value) }
    }

    fun save() {
        val state = _uiState.value
        when (state.transactionType) {
            TransactionType.INCOME -> saveAsIncome(state)
            TransactionType.EXPENSE, TransactionType.TRANSFER -> saveAsExpense(state)
        }
    }

    /**
     * 지출/이체로 저장.
     * 원래 수입이었던 거래를 지출/이체로 전환하는 경우 IncomeEntity 삭제 후 ExpenseEntity 생성.
     */
    private fun saveAsExpense(state: TransactionEditUiState) {
        val amount = state.amount.replace(",", "").toIntOrNull()
        if (amount == null || amount <= 0 || state.storeName.isBlank()) {
            snackbarBus.show(context.getString(R.string.transaction_edit_input_required))
            return
        }

        val dateTime = buildDateTime(state.dateMillis, state.hour, state.minute)

        viewModelScope.launch {
            try {
                val txType = if (state.transactionType == TransactionType.TRANSFER) "TRANSFER" else "EXPENSE"
                val txDirection = state.transferDirection?.dbValue ?: ""
                val effectiveIsFixed = state.isFixed && state.transactionType == TransactionType.EXPENSE

                // 수입 → 지출/이체 크로스 테이블 이동 (insert 먼저, delete 후 — 원자성 보장)
                if (originalTransactionType == TransactionType.INCOME && !state.isNew) {
                    val entity = ExpenseEntity(
                        amount = amount,
                        storeName = state.storeName.trim(),
                        category = state.category,
                        cardName = state.cardName.trim(),
                        dateTime = dateTime,
                        originalSms = state.originalSms,
                        smsId = originalIncomeEntity?.smsId ?: "manual_${System.currentTimeMillis()}",
                        senderAddress = originalIncomeEntity?.senderAddress ?: "",
                        memo = state.memo.ifBlank { null },
                        isFixed = effectiveIsFixed,
                        transactionType = txType,
                        transferDirection = txDirection
                    )
                    expenseRepository.insert(entity)
                    if (incomeId > 0) {
                        incomeRepository.deleteById(incomeId)
                    }
                } else if (state.isNew) {
                    val entity = ExpenseEntity(
                        amount = amount,
                        storeName = state.storeName.trim(),
                        category = state.category,
                        cardName = state.cardName.trim(),
                        dateTime = dateTime,
                        originalSms = "",
                        smsId = "manual_${System.currentTimeMillis()}",
                        memo = state.memo.ifBlank { null },
                        isFixed = effectiveIsFixed,
                        transactionType = txType,
                        transferDirection = txDirection
                    )
                    expenseRepository.insert(entity)
                } else {
                    val orig = originalExpenseEntity ?: return@launch
                    val updated = orig.copy(
                        amount = amount,
                        storeName = state.storeName.trim(),
                        category = state.category,
                        cardName = state.cardName.trim(),
                        dateTime = dateTime,
                        isFixed = effectiveIsFixed,
                        memo = state.memo.ifBlank { null },
                        transactionType = txType,
                        transferDirection = txDirection
                    )
                    expenseRepository.update(updated)
                }

                val trimmedStore = state.storeName.trim()
                val ruleKeyword = state.ruleKeyword.trim().ifBlank { trimmedStore }
                if (!state.isNew && state.transactionType == TransactionType.EXPENSE && ruleKeyword.isNotBlank()) {
                    try {
                        val keywordRule = storeRuleRepository.getByKeyword(ruleKeyword)
                        val previousRule = originalMatchedRule?.takeIf { matched ->
                            !matched.keyword.equals(ruleKeyword, ignoreCase = true)
                        } ?: keywordRule ?: originalMatchedRule
                        val newRule = if (state.applyCategoryToAll || state.applyFixedToAll) {
                            StoreRuleEntity(
                                id = keywordRule?.id ?: previousRule?.id ?: 0,
                                keyword = ruleKeyword,
                                category = if (state.applyCategoryToAll) state.category else null,
                                isFixed = if (state.applyFixedToAll) effectiveIsFixed else null,
                                createdAt = keywordRule?.createdAt ?: previousRule?.createdAt ?: System.currentTimeMillis()
                            )
                        } else {
                            null
                        }

                        storeRuleSyncService.applyRuleChange(
                            previousRule = previousRule,
                            newRule = newRule
                        )
                        originalMatchedRule = newRule
                    } catch (e: Exception) {
                        MoneyTalkLogger.w("일괄 적용 실패: ${e.message}")
                    }
                }

                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
                snackbarBus.show(context.getString(R.string.transaction_edit_saved))
                _uiState.update { it.copy(isSaved = true) }
            } catch (e: Exception) {
                snackbarBus.show(context.getString(R.string.transaction_edit_save_failed))
            }
        }
    }

    /**
     * 수입으로 저장.
     * 원래 지출/이체였던 거래를 수입으로 전환하는 경우 ExpenseEntity 삭제 후 IncomeEntity 생성.
     */
    private fun saveAsIncome(state: TransactionEditUiState) {
        val amount = state.amount.replace(",", "").toIntOrNull()
        if (amount == null || amount <= 0) {
            snackbarBus.show(context.getString(R.string.transaction_edit_income_input_required))
            return
        }

        val dateTime = buildDateTime(state.dateMillis, state.hour, state.minute)

        viewModelScope.launch {
            try {
                // 지출/이체 → 수입 크로스 테이블 이동 (insert 먼저, delete 후 — 원자성 보장)
                if (originalTransactionType != TransactionType.INCOME && !state.isNew) {
                    val entity = IncomeEntity(
                        amount = amount,
                        type = state.incomeType.trim().ifBlank {
                            context.getString(R.string.income_type_default)
                        },
                        source = state.source.trim(),
                        description = state.storeName.trim(),
                        isRecurring = state.isFixed,
                        dateTime = dateTime,
                        originalSms = state.originalSms.ifBlank { null },
                        smsId = originalExpenseEntity?.smsId,
                        senderAddress = originalExpenseEntity?.senderAddress ?: "",
                        memo = state.memo.ifBlank { null },
                        category = state.category
                    )
                    incomeRepository.insert(entity)
                    if (expenseId > 0) {
                        expenseRepository.deleteById(expenseId)
                    }
                } else if (state.isNew) {
                    val entity = IncomeEntity(
                        amount = amount,
                        type = state.incomeType.trim().ifBlank {
                            context.getString(R.string.income_type_default)
                        },
                        source = state.source.trim(),
                        description = state.storeName.trim(),
                        isRecurring = state.isFixed,
                        dateTime = dateTime,
                        memo = state.memo.ifBlank { null },
                        category = state.category
                    )
                    incomeRepository.insert(entity)
                } else {
                    val orig = originalIncomeEntity ?: return@launch
                    val updated = orig.copy(
                        amount = amount,
                        type = state.incomeType.trim().ifBlank { orig.type },
                        source = state.source.trim(),
                        description = state.storeName.trim(),
                        isRecurring = state.isFixed,
                        dateTime = dateTime,
                        memo = state.memo.ifBlank { null },
                        category = state.category
                    )
                    incomeRepository.update(updated)
                }
                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
                snackbarBus.show(context.getString(R.string.transaction_edit_saved))
                _uiState.update { it.copy(isSaved = true) }
            } catch (e: Exception) {
                snackbarBus.show(context.getString(R.string.transaction_edit_save_failed))
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                // 원래 타입 기준으로 삭제 (전환 전 원본 삭제)
                when (originalTransactionType) {
                    TransactionType.INCOME -> {
                        if (incomeId <= 0) return@launch
                        originalIncomeEntity?.smsId?.let { DeletedSmsTracker.markDeleted(it) }
                        incomeRepository.deleteById(incomeId)
                    }
                    TransactionType.EXPENSE, TransactionType.TRANSFER -> {
                        if (expenseId <= 0) return@launch
                        originalExpenseEntity?.let { DeletedSmsTracker.markDeleted(it.smsId) }
                        expenseRepository.deleteById(expenseId)
                    }
                }
                dataRefreshEvent.emit(DataRefreshEvent.RefreshType.TRANSACTION_ADDED)
                snackbarBus.show(context.getString(R.string.transaction_edit_deleted))
                _uiState.update { it.copy(isDeleted = true) }
            } catch (e: Exception) {
                snackbarBus.show(context.getString(R.string.transaction_edit_delete_failed))
            }
        }
    }

    // ===== 화면별 온보딩 =====

    fun hasSeenScreenOnboardingFlow(screenId: String) =
        settingsDataStore.hasSeenScreenOnboardingFlow(screenId)

    fun markScreenOnboardingSeen(screenId: String) {
        viewModelScope.launch {
            settingsDataStore.setScreenOnboardingSeen(screenId)
        }
    }

    /**
     * dateMillis(DatePicker 반환값, UTC 자정 기준)에서 년/월/일만 추출하여
     * 로컬 타임존의 hour/minute과 조합.
     * DatePicker.selectedDateMillis는 UTC 기반이므로 직접 timeInMillis에 넣으면
     * UTC 서쪽 타임존에서 날짜가 하루 밀릴 수 있다.
     */
    private fun buildDateTime(dateMillis: Long, hour: Int, minute: Int): Long {
        // UTC 기준 Calendar로 년/월/일만 추출
        val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = dateMillis
        }
        // 로컬 Calendar에 년/월/일 + 시/분 설정
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
            set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
