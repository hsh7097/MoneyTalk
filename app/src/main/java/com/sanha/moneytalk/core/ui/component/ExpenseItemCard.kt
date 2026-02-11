package com.sanha.moneytalk.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.util.DateUtils
import java.text.NumberFormat
import java.util.*

/**
 * 지출 상세 다이얼로그
 * 홈 화면과 내역 화면에서 공통 사용
 *
 * @param expense 지출 정보
 * @param onDismiss 다이얼로그 닫기
 * @param onDelete 삭제 콜백 (null이면 삭제 버튼 숨김)
 * @param onCategoryChange 카테고리 변경 콜백 (null이면 수정 불가)
 * @param onMemoChange 메모 변경 콜백 (null이면 수정 불가)
 */
@Composable
fun ExpenseDetailDialog(
    expense: ExpenseEntity,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onCategoryChange: ((String) -> Unit)? = null,
    onMemoChange: ((String?) -> Unit)? = null
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val category = Category.fromDisplayName(expense.category)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var isEditingMemo by remember { mutableStateOf(false) }
    var memoText by remember { mutableStateOf(expense.memo ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            CategoryIcon(
                category = category,
                containerSize = 48.dp,
                fontSize = 28.sp
            )
        },
        title = {
            Text(
                text = expense.storeName,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 금액 (색상으로만 구분)
                DetailRow(
                    label = stringResource(R.string.detail_amount),
                    value = stringResource(R.string.common_won, numberFormat.format(expense.amount)),
                    valueColor = MaterialTheme.colorScheme.error
                )

                // 카테고리 (수정 가능하면 클릭 가능)
                if (onCategoryChange != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showCategoryPicker = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.detail_category),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${category.emoji} ${category.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "카테고리 변경",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    DetailRow(
                        label = stringResource(R.string.detail_category),
                        value = "${category.emoji} ${category.displayName}"
                    )
                }

                // 카드
                DetailRow(
                    label = stringResource(R.string.detail_card),
                    value = expense.cardName
                )

                // 결제 시간
                DetailRow(
                    label = stringResource(R.string.detail_payment_time),
                    value = DateUtils.formatDisplayDateTime(expense.dateTime)
                )

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
                                text = if (memoText.isBlank()) "메모 추가" else memoText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (memoText.isBlank()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 180.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "메모 편집",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    expense.memo?.let { memo ->
                        if (memo.isNotBlank()) {
                            DetailRow(label = stringResource(R.string.detail_memo), value = memo)
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
                            text = expense.originalSms,
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
        dismissButton = if (onDelete != null) {
            {
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else null
    )

    // 카테고리 선택 다이얼로그
    if (showCategoryPicker && onCategoryChange != null) {
        CategoryPickerDialog(
            currentCategory = expense.category,
            onDismiss = { showCategoryPicker = false },
            onCategorySelected = { newCategory ->
                onCategoryChange(newCategory)
                showCategoryPicker = false
            }
        )
    }

    // 메모 편집 다이얼로그
    if (isEditingMemo && onMemoChange != null) {
        AlertDialog(
            onDismissRequest = { isEditingMemo = false },
            title = { Text("메모 편집") },
            text = {
                OutlinedTextField(
                    value = memoText,
                    onValueChange = { memoText = it },
                    placeholder = { Text("메모를 입력하세요") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onMemoChange(memoText.ifBlank { null })
                    isEditingMemo = false
                }) {
                    Text("저장")
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditingMemo = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 삭제 확인 다이얼로그
    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_message, expense.storeName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
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
 * 카테고리 선택 다이얼로그
 */
@Composable
fun CategoryPickerDialog(
    currentCategory: String,
    onDismiss: () -> Unit,
    onCategorySelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("카테고리 선택") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Category.entries.forEach { category ->
                    val isSelected = category.displayName == currentCategory
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategorySelected(category.displayName) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (valueColor != Color.Unspecified) valueColor else Color.Unspecified
        )
    }
}
