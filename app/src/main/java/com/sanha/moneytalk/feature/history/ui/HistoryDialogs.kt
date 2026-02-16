package com.sanha.moneytalk.feature.history.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.ui.component.CategorySelectDialog
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 수동 지출 추가 다이얼로그
 */
@Composable
fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onConfirm: (amount: Int, storeName: String, category: String, cardName: String) -> Unit
) {
    val defaultCardName = stringResource(R.string.history_default_payment_method_cash)
    var amountText by remember { mutableStateOf("") }
    var storeName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(Category.ETC.displayName) }
    var cardName by remember { mutableStateOf(defaultCardName) }
    var showCategoryDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_add_expense_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 금액 입력
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.history_amount)) },
                    suffix = { Text(stringResource(R.string.common_suffix_won)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 가게명 입력
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { storeName = it },
                    label = { Text(stringResource(R.string.history_store_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 카테고리 선택
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.history_category)) },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCategoryDropdown = true },
                    trailingIcon = {
                        IconButton(onClick = { showCategoryDropdown = true }) {
                            @Suppress("DEPRECATION")
                            Icon(Icons.Default.List, contentDescription = null)
                        }
                    }
                )
                if (showCategoryDropdown) {
                    CategorySelectDialog(
                        currentCategory = selectedCategory,
                        showAllOption = false,
                        onDismiss = { showCategoryDropdown = false },
                        onCategorySelected = { selected ->
                            if (selected != null) {
                                selectedCategory = selected
                            }
                            showCategoryDropdown = false
                        }
                    )
                }

                // 결제수단 입력
                OutlinedTextField(
                    value = cardName,
                    onValueChange = { cardName = it },
                    label = { Text(stringResource(R.string.history_payment_method)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = amountText.toIntOrNull() ?: 0
                    if (amount > 0 && storeName.isNotBlank()) {
                        onConfirm(amount, storeName, selectedCategory, cardName)
                    }
                },
                enabled = amountText.isNotBlank() && storeName.isNotBlank()
            ) {
                Text(stringResource(R.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/**
 * 수입 상세 다이얼로그
 * 수입 항목 클릭 시 원본 SMS와 상세 정보를 표시
 */
@Composable
fun IncomeDetailDialog(
    income: IncomeEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit = {},
    onMemoChange: ((String?) -> Unit)? = null
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isEditingMemo by remember { mutableStateOf(false) }
    var memoText by remember { mutableStateOf(income.memo ?: "") }
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val dateFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Text(
                text = "\uD83D\uDCB0",
                style = MaterialTheme.typography.displaySmall
            )
        },
        title = {
            Text(
                text = income.description.ifBlank { income.type },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 금액
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.detail_amount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = stringResource(
                            R.string.common_won,
                            numberFormat.format(income.amount)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.moneyTalkColors.income
                    )
                }

                // 유형
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.income_detail_type),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = income.type,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 출처
                if (income.source.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.income_detail_source),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = income.source,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 입금 시간
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.income_detail_time),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = dateFormat.format(Date(income.dateTime)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 고정 수입 여부
                if (income.isRecurring) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.income_detail_recurring),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = stringResource(R.string.income_detail_recurring_day, (income.recurringDay ?: "-").toString()),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 메모 (편집 가능)
                if (onMemoChange != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isEditingMemo = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.detail_memo),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (memoText.isBlank()) stringResource(R.string.income_detail_memo_add) else memoText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (memoText.isBlank()) MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.4f
                                ) else MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 180.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.income_detail_memo_edit),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    income.memo?.let { memo ->
                        if (memo.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.detail_memo),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = memo,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 원본 문자
                Text(
                    text = stringResource(R.string.detail_original_sms),
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
                            text = income.originalSms ?: stringResource(R.string.income_detail_no_sms),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { showDeleteConfirm = true },
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
                Text(stringResource(R.string.common_delete))
            }
        }
    )

    // 삭제 확인 다이얼로그
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.income_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.income_delete_message,
                        income.description.ifBlank { income.type },
                        NumberFormat.getNumberInstance(Locale.KOREA).format(income.amount)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 메모 편집 다이얼로그
    if (isEditingMemo && onMemoChange != null) {
        AlertDialog(
            onDismissRequest = { isEditingMemo = false },
            title = { Text(stringResource(R.string.income_detail_memo_edit)) },
            text = {
                OutlinedTextField(
                    value = memoText,
                    onValueChange = { memoText = it },
                    placeholder = { Text(stringResource(R.string.income_detail_memo_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onMemoChange(memoText.ifBlank { null })
                    isEditingMemo = false
                }) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditingMemo = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
