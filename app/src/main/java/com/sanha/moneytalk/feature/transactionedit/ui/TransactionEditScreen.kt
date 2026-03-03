package com.sanha.moneytalk.feature.transactionedit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.ui.component.CategorySelectDialog
import androidx.compose.material3.AlertDialog
import com.sanha.moneytalk.core.util.DateUtils

/**
 * 거래 편집/추가 화면.
 *
 * 전체 화면 편집 UI: 금액, 가게명, 카테고리, 결제수단, 날짜, 시간, 메모.
 * 기존 거래: 원본 SMS 표시 + 삭제 버튼.
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
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // TopBar
        TopAppBar(
            title = {
                Text(
                    text = if (uiState.isNew) {
                        stringResource(R.string.transaction_add_title)
                    } else {
                        stringResource(R.string.transaction_edit_title)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return
        }

        // 편집 폼
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 금액
            OutlinedTextField(
                value = uiState.amount,
                onValueChange = { value ->
                    viewModel.updateAmount(value.filter { it.isDigit() })
                },
                label = { Text(stringResource(R.string.transaction_edit_amount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                trailingIcon = {
                    if (uiState.amount.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateAmount("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_clear_input)
                            )
                        }
                    }
                },
                suffix = { Text("원") },
                modifier = Modifier.fillMaxWidth()
            )

            // 가게명
            OutlinedTextField(
                value = uiState.storeName,
                onValueChange = { viewModel.updateStoreName(it) },
                label = { Text(stringResource(R.string.transaction_edit_store)) },
                singleLine = true,
                trailingIcon = {
                    if (uiState.storeName.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateStoreName("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_clear_input)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 카테고리
            val category = Category.fromDisplayName(uiState.category)
            OutlinedTextField(
                value = "${category.emoji} ${category.displayName}",
                onValueChange = {},
                label = { Text(stringResource(R.string.detail_category)) },
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showCategoryPicker = true }) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = stringResource(R.string.detail_edit_category)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 결제수단
            OutlinedTextField(
                value = uiState.cardName,
                onValueChange = { viewModel.updateCardName(it) },
                label = { Text(stringResource(R.string.transaction_edit_card)) },
                singleLine = true,
                trailingIcon = {
                    if (uiState.cardName.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateCardName("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_clear_input)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 날짜
            OutlinedTextField(
                value = DateUtils.formatDisplayDate(uiState.dateMillis),
                onValueChange = {},
                label = { Text(stringResource(R.string.transaction_edit_date)) },
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = stringResource(R.string.transaction_edit_date)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 시간
            OutlinedTextField(
                value = String.format("%02d:%02d", uiState.hour, uiState.minute),
                onValueChange = {},
                label = { Text(stringResource(R.string.transaction_edit_time)) },
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showTimePicker = true }) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = stringResource(R.string.transaction_edit_time)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 메모
            OutlinedTextField(
                value = uiState.memo,
                onValueChange = { viewModel.updateMemo(it) },
                label = { Text(stringResource(R.string.transaction_edit_memo)) },
                maxLines = 3,
                trailingIcon = {
                    if (uiState.memo.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateMemo("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_clear_input)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // 원본 SMS (기존 거래만)
            if (!uiState.isNew && uiState.originalSms.isNotBlank()) {
                Text(
                    text = stringResource(R.string.transaction_edit_original_sms),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sms,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = uiState.originalSms,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // 하단 버튼
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 저장 버튼
            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.transaction_edit_save),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // 삭제 버튼 (기존 거래만)
            if (!uiState.isNew) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.common_delete))
                }
            }
        }
    }

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
    if (showCategoryPicker) {
        CategorySelectDialog(
            currentCategory = uiState.category,
            showAllOption = false,
            onDismiss = { showCategoryPicker = false },
            onCategorySelected = { selected ->
                if (selected != null) {
                    viewModel.updateCategory(selected)
                }
                showCategoryPicker = false
            }
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
