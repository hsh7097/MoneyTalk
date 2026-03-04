package com.sanha.moneytalk.feature.transactionedit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.ui.component.CategorySelectDialog
import com.sanha.moneytalk.core.util.DateUtils
import java.text.NumberFormat
import java.util.Locale

/**
 * 거래 편집/추가 화면 (뱅크셀러드 스타일).
 *
 * 상단: X 닫기 버튼 + 거래처명 + 금액 (강조).
 * 중단: 카테고리, 거래처, 날짜-시간, 메모, 원본 SMS.
 * 하단: 삭제 + 저장 버튼.
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
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp)
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return
        }

        // 헤더 영역: X 닫기 버튼 + 거래처명 + 금액
        TransactionHeader(
            storeName = uiState.storeName,
            amount = uiState.amount,
            isNew = uiState.isNew,
            onClose = onBack,
            onStoreNameChange = { viewModel.updateStoreName(it) },
            onAmountChange = { value ->
                viewModel.updateAmount(value.filter { it.isDigit() })
            }
        )

        // 편집 폼
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // 수입/지출/이체 분류 탭
            TransactionTypeTab(
                currentType = uiState.transactionType,
                onTypeChange = { viewModel.setTransactionType(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 카테고리 (모든 거래 유형에서 동일하게 표시)
            val category = Category.fromDisplayName(uiState.category)
            CompactReadOnlyRow(
                label = stringResource(R.string.detail_category),
                value = "${category.emoji} ${category.displayName}",
                onClick = { showCategoryPicker = true }
            )

            // 거래처
            CompactEditRow(
                label = stringResource(R.string.transaction_edit_store),
                value = uiState.storeName,
                onValueChange = { viewModel.updateStoreName(it) }
            )

            // 날짜-시간 (하나의 행)
            CompactReadOnlyRow(
                label = stringResource(R.string.transaction_edit_date_time),
                value = "${DateUtils.formatDisplayDate(uiState.dateMillis)}  ${String.format("%02d:%02d", uiState.hour, uiState.minute)}",
                onClick = { showDatePicker = true }
            )

            // 메모
            CompactEditRow(
                label = stringResource(R.string.transaction_edit_memo),
                value = uiState.memo,
                onValueChange = { viewModel.updateMemo(it) },
                maxLines = 3,
                singleLine = false
            )

            // 원본 SMS (기존 거래만)
            if (!uiState.isNew && uiState.originalSms.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.transaction_edit_original_sms),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
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

            // 고정지출 토글 (수입이 아닐 때만 표시, 원본 SMS 하단 고정 위치)
            if (!uiState.isIncome) {
                FixedExpenseToggle(
                    isFixed = uiState.isFixed,
                    onToggle = { viewModel.updateIsFixed(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 하단 버튼 영역
        TransactionBottomButtons(
            isNew = uiState.isNew,
            onSave = { viewModel.save() },
            onDelete = { showDeleteConfirm = true }
        )
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
 * 거래 상세 헤더 영역.
 * X 닫기 버튼 + 거래처명 (작은 텍스트) + 금액 (콤마 포맷 + 원, 포커스 시 밑줄).
 */
@Composable
private fun TransactionHeader(
    storeName: String,
    amount: String,
    isNew: Boolean,
    onClose: () -> Unit,
    onStoreNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit
) {
    var isAmountFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        // X 닫기 버튼 (우측 정렬)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 거래처명 (인라인 편집, 볼드 제거 + 작은 폰트)
        BasicTextField(
            value = storeName,
            onValueChange = onStoreNameChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Box {
                    if (storeName.isEmpty()) {
                        Text(
                            text = if (isNew) {
                                stringResource(R.string.transaction_edit_store_hint)
                            } else {
                                ""
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 금액 (인라인 편집, 콤마 포맷 + 원 suffix, 포커스 시 밑줄 + X 버튼)
        val amountSuffix = stringResource(R.string.transaction_edit_amount_suffix)
        val amountTransformation = remember(amountSuffix) {
            AmountSuffixTransformation(amountSuffix)
        }
        BasicTextField(
            value = amount,
            onValueChange = onAmountChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            ),
            visualTransformation = amountTransformation,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isAmountFocused = it.isFocused },
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (amount.isEmpty()) {
                            Text(
                                text = stringResource(R.string.transaction_edit_amount_hint),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.5f
                                ),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        innerTextField()
                    }
                    if (isAmountFocused && amount.isNotEmpty()) {
                        IconButton(
                            onClick = { onAmountChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_clear_input),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        )

        // 포커스 시 컬러 밑줄, 비포커스 시 없음
        Spacer(modifier = Modifier.height(8.dp))
        if (isAmountFocused) {
            HorizontalDivider(
                thickness = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 하단 저장/삭제 버튼 영역.
 * 1:1 비율, 높이 52dp.
 */
@Composable
private fun TransactionBottomButtons(
    isNew: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 삭제 버튼 (기존 거래만)
        if (!isNew) {
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(
                    text = stringResource(R.string.common_delete),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 저장 버튼
        Button(
            onClick = onSave,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = stringResource(R.string.transaction_edit_save),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 수입/지출/이체 분류 탭.
 * 개별 OutlinedButton 형태 (선택 시 primary 테두리 + 텍스트).
 */
@Composable
private fun TransactionTypeTab(
    currentType: TransactionType,
    onTypeChange: (TransactionType) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.transaction_edit_type_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(72.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransactionType.entries.forEach { type ->
                    val isSelected = type == currentType
                    OutlinedButton(
                        onClick = { onTypeChange(type) },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(type.labelResId),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * 고정지출 토글.
 * Switch + "고정지출에 추가" 라벨.
 */
@Composable
private fun FixedExpenseToggle(
    isFixed: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isFixed) }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.transaction_edit_fixed_expense),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = isFixed,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * 편집 가능한 컴팩트 필드 행.
 * 포커스 시에만 X 클리어 버튼 표시.
 */
@Composable
private fun CompactEditRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    suffix: String? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(72.dp)
            )

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                maxLines = maxLines,
                keyboardOptions = keyboardOptions,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused },
                decorationBox = { innerTextField ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (value.isEmpty() && placeholder.isNotEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = 0.5f
                                    )
                                )
                            }
                            innerTextField()
                        }
                        if (suffix != null && value.isNotEmpty()) {
                            Text(
                                text = suffix,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )

            if (isFocused && value.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange("") },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_clear_input),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * 읽기 전용 컴팩트 필드 행 (클릭 시 Picker 열림).
 */
@Composable
private fun CompactReadOnlyRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(72.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

/**
 * 금액 입력 시 콤마 포맷 + suffix(원/KRW) 표시용 VisualTransformation.
 * 원본 텍스트(숫자만)를 "1,234원" 형태로 변환하고 커서 위치를 올바르게 매핑.
 */
private class AmountSuffixTransformation(
    private val suffix: String
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        if (original.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val number = original.toLongOrNull()
            ?: return TransformedText(text, OffsetMapping.Identity)

        val formatted = NumberFormat.getNumberInstance(Locale.KOREA).format(number)
        val output = "$formatted$suffix"

        // formatted 문자열에서 숫자 위치 매핑
        val digitPositions = mutableListOf<Int>()
        for (i in formatted.indices) {
            if (formatted[i] != ',') {
                digitPositions.add(i)
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset >= digitPositions.size) return formatted.length
                return digitPositions[offset]
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset >= formatted.length) return original.length
                var count = 0
                for (pos in digitPositions) {
                    if (pos < offset) count++ else break
                }
                return count.coerceAtMost(original.length)
            }
        }

        return TransformedText(AnnotatedString(output), offsetMapping)
    }
}
