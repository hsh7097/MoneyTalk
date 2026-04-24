package com.sanha.moneytalk.feature.transactionedit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.model.CategoryType
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkOverlay
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkState
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkTargetRegistry
import com.sanha.moneytalk.core.ui.component.CategoryAddDialog
import com.sanha.moneytalk.core.ui.component.CategorySelectDialog
import com.sanha.moneytalk.feature.transactionedit.ui.model.TransactionType
import com.sanha.moneytalk.feature.transactionedit.ui.coachmark.transactionEditCoachMarkSteps
import kotlinx.coroutines.delay

private data class TransactionEditSnapshot(
    val transactionType: TransactionType,
    val amount: String,
    val storeName: String,
    val category: String,
    val cardName: String,
    val incomeType: String,
    val source: String,
    val dateMillis: Long,
    val hour: Int,
    val minute: Int,
    val memo: String,
    val isFixed: Boolean,
    val transferDirection: String?
)

private fun TransactionEditUiState.toSnapshot(): TransactionEditSnapshot {
    return TransactionEditSnapshot(
        transactionType = transactionType,
        amount = amount,
        storeName = storeName,
        category = category,
        cardName = cardName,
        incomeType = incomeType,
        source = source,
        dateMillis = dateMillis,
        hour = hour,
        minute = minute,
        memo = memo,
        isFixed = isFixed,
        transferDirection = transferDirection?.dbValue
    )
}

private fun TransactionType.toCategoryType(): CategoryType {
    return when (this) {
        TransactionType.EXPENSE -> CategoryType.EXPENSE
        TransactionType.INCOME -> CategoryType.INCOME
        TransactionType.TRANSFER -> CategoryType.TRANSFER
    }
}

/**
 * 거래 상세/편집 화면.
 *
 * 상단 hero에서 거래처/금액/유형 전환을 제공하고,
 * 하단 카드에서 기본 정보/자동 정리/원본 문자를 분리해 보여준다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditScreen(
    onBack: () -> Unit,
    viewModel: TransactionEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 저장/삭제 완료 시 화면 종료
    LaunchedEffect(uiState.isSaved, uiState.isDeleted) {
        if (uiState.isSaved || uiState.isDeleted) {
            onBack()
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var initialSnapshot by remember { mutableStateOf<TransactionEditSnapshot?>(null) }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && initialSnapshot == null) {
            initialSnapshot = uiState.toSnapshot()
        }
    }

    val hasPendingChanges by remember(uiState, initialSnapshot) {
        derivedStateOf {
            val snapshot = initialSnapshot ?: return@derivedStateOf false
            !uiState.isSaved && !uiState.isDeleted && uiState.toSnapshot() != snapshot
        }
    }

    val onRequestClose = remember(
        hasPendingChanges,
        showDatePicker,
        showTimePicker,
        uiState.showCategoryPicker,
        uiState.showAddCategoryDialog,
        showDeleteConfirm
    ) {
        {
            if (hasPendingChanges) {
                showExitConfirm = true
            } else {
                onBack()
            }
        }
    }

    // ===== 키워드 가이드 (일괄 적용 최초 사용 시) =====
    val hasSeenKeywordGuide by viewModel.hasSeenScreenOnboardingFlow("rule_keyword_guide")
        .collectAsStateWithLifecycle(initialValue = true)

    // ===== 코치마크 (거래 편집 온보딩) =====
    val coachMarkRegistry = remember { CoachMarkTargetRegistry() }
    val coachMarkState = remember { CoachMarkState() }
    val allEditSteps = remember { transactionEditCoachMarkSteps() }
    val hasSeenEditOnboarding by viewModel.hasSeenScreenOnboardingFlow("transaction_edit")
        .collectAsStateWithLifecycle(initialValue = true)

    LaunchedEffect(hasSeenEditOnboarding, uiState.isLoading, uiState.isNew) {
        if (!hasSeenEditOnboarding && !uiState.isLoading && !uiState.isNew) {
            delay(1000)
            val visibleSteps = allEditSteps.filter { it.targetKey in coachMarkRegistry.targets }
            if (visibleSteps.isNotEmpty()) {
                coachMarkState.show(visibleSteps)
            }
        }
    }

    BackHandler(
        enabled = !showDatePicker &&
                !showTimePicker &&
                !uiState.showCategoryPicker &&
                !uiState.showAddCategoryDialog &&
                !showDeleteConfirm &&
                !showExitConfirm
    ) {
        onRequestClose()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding()
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return
            }

            TransactionEditDetailContent(
                uiState = uiState,
                hasSeenKeywordGuide = hasSeenKeywordGuide,
                coachMarkRegistry = coachMarkRegistry,
                onClose = onRequestClose,
                onSave = { viewModel.save() },
                onDelete = { showDeleteConfirm = true },
                onStoreNameChange = { viewModel.updateStoreName(it) },
                onAmountChange = { value -> viewModel.updateAmount(value.filter { it.isDigit() }) },
                onTypeChange = { viewModel.setTransactionType(it) },
                onCategoryClick = { viewModel.showCategoryPicker() },
                onApplyCategoryToAllChange = { viewModel.updateApplyCategoryToAll(it) },
                onDateTimeClick = { showDatePicker = true },
                onMemoChange = { viewModel.updateMemo(it) },
                onFixedToggle = { viewModel.updateIsFixed(it) },
                onApplyFixedToAllChange = { viewModel.updateApplyFixedToAll(it) },
                onRuleKeywordChange = { viewModel.updateRuleKeyword(it) },
                onKeywordGuideDismiss = {
                    viewModel.markScreenOnboardingSeen("rule_keyword_guide")
                }
            )
        }

        CoachMarkOverlay(
            state = coachMarkState,
            targetRegistry = coachMarkRegistry,
            onComplete = { viewModel.markScreenOnboardingSeen("transaction_edit") }
        )
    } // Box

    // DatePicker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.dateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.updateDate(it) }
                    showDatePicker = false
                    // 날짜 선택 후 시간 picker도 표시
                    showTimePicker = true
                }) {
                    Text(stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // TimePicker Dialog
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = uiState.hour,
            initialMinute = uiState.minute,
            is24Hour = true
        )
        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = {
                viewModel.updateTime(timePickerState.hour, timePickerState.minute)
                showTimePicker = false
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }

    // Category Picker Dialog
    if (uiState.showCategoryPicker) {
        CategorySelectDialog(
            currentCategory = uiState.category,
            categoryType = uiState.transactionType.toCategoryType(),
            showAllOption = false,
            transferDirection = uiState.transferDirection,
            customCategories = uiState.categoryEntries,
            onAddCategoryClick = { viewModel.showAddCategoryDialog() },
            onDismiss = { viewModel.dismissCategoryPicker() },
            onCategorySelected = { selected ->
                viewModel.selectCategory(selected)
            },
            onTransferDirectionChanged = { direction ->
                viewModel.updateTransferDirection(direction)
            }
        )
    }

    if (uiState.showAddCategoryDialog) {
        CategoryAddDialog(
            emoji = uiState.addCategoryEmoji,
            name = uiState.addCategoryName,
            error = uiState.addCategoryErrorResId?.let { stringResource(it) },
            onEmojiChange = { viewModel.updateAddCategoryEmoji(it) },
            onNameChange = { viewModel.updateAddCategoryName(it) },
            onConfirm = { viewModel.addCategoryFromPicker() },
            onDismiss = { viewModel.dismissAddCategoryDialog() }
        )
    }

    // 삭제 확인 Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.history_delete_message,
                        uiState.storeName.ifBlank { "" }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete()
                    showDeleteConfirm = false
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(text = stringResource(R.string.transaction_edit_exit_confirm_title)) },
            text = { Text(text = stringResource(R.string.transaction_edit_exit_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirm = false
                        onBack()
                    }
                ) {
                    Text(text = stringResource(R.string.transaction_edit_exit_confirm_exit))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text(text = stringResource(R.string.transaction_edit_exit_confirm_keep))
                }
            }
        )
    }
}

/**
 * TimePicker를 AlertDialog로 감싸는 래퍼.
 * Material3 TimePicker는 자체 Dialog를 제공하지 않으므로 직접 구현.
 */
@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        text = { content() }
    )
}
