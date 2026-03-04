package com.sanha.moneytalk.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.CategoryType
import com.sanha.moneytalk.core.model.TransferDirection
import com.sanha.moneytalk.core.util.DateUtils
import java.text.NumberFormat
import java.util.Locale

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
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
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
                    value = stringResource(
                        R.string.common_won,
                        numberFormat.format(expense.amount)
                    ),
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
                                contentDescription = stringResource(R.string.detail_edit_category),
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
                                text = if (memoText.isBlank()) stringResource(R.string.detail_add_memo) else memoText,
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
                                contentDescription = stringResource(R.string.detail_edit_memo),
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
                    maxLines = 3,
                    trailingIcon = {
                        if (memoText.isNotEmpty()) {
                            IconButton(onClick = { memoText = "" }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_clear_input))
                            }
                        }
                    }
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
 * 카테고리 선택 BottomSheet (4열 그리드, 타입별 필터링)
 * 아이콘 + 하단 텍스트 형태, 하단에서 올라오는 시트
 *
 * @param currentCategory 현재 선택된 카테고리 displayName
 * @param categoryType 표시할 카테고리 타입 (null이면 지출 기본)
 * @param showAllOption true면 "전체" 항목 표시 (필터용)
 * @param transferDirection 이체 방향 (이체 타입일 때만 사용)
 * @param onDismiss 닫기
 * @param onCategorySelected 카테고리 선택 콜백 (null이면 "전체" 선택)
 * @param onTransferDirectionChanged 이체 방향 변경 콜백
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelectDialog(
    currentCategory: String?,
    categoryType: CategoryType? = null,
    showAllOption: Boolean = false,
    transferDirection: TransferDirection? = null,
    onDismiss: () -> Unit,
    onCategorySelected: (String?) -> Unit,
    onTransferDirectionChanged: ((TransferDirection) -> Unit)? = null
) {
    val type = categoryType ?: CategoryType.EXPENSE
    val categories = when (type) {
        CategoryType.EXPENSE -> Category.expenseEntries
        CategoryType.INCOME -> Category.incomeEntries
        CategoryType.TRANSFER -> Category.transferEntries
    }
    val sheetTitleResId = if (type == CategoryType.TRANSFER) {
        R.string.transfer_category_picker_title
    } else {
        R.string.detail_category
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.58f).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
        ) {
            // 타이틀
            Text(
                text = stringResource(sheetTitleResId),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                textAlign = TextAlign.Center
            )

            // 이체 타입: 입금/출금 방향 토글
            if (type == CategoryType.TRANSFER && onTransferDirectionChanged != null) {
                TransferDirectionToggleRow(
                    selectedDirection = transferDirection ?: TransferDirection.WITHDRAWAL,
                    onDirectionSelected = onTransferDirectionChanged,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 카테고리 그리드 + 하단 페이드
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (showAllOption) {
                        item {
                            CategoryGridItem(
                                emoji = "\uD83D\uDCCB",
                                label = stringResource(R.string.common_all),
                                isSelected = currentCategory == null,
                                onClick = { onCategorySelected(null) }
                            )
                        }
                    }
                    items(categories) { category ->
                        CategoryGridItem(
                            emoji = category.emoji,
                            label = category.displayName,
                            isSelected = category.displayName == currentCategory,
                            onClick = { onCategorySelected(category.displayName) }
                        )
                    }
                }

                // 하단 페이드 그라데이션 (스크롤 힌트)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TransferDirectionToggleRow(
    selectedDirection: TransferDirection,
    onDirectionSelected: (TransferDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TransferDirection.entries.forEach { direction ->
            val isSelected = selectedDirection == direction
            val animatedBackground by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = spring(stiffness = 600f),
                label = "transfer_toggle_background"
            )
            val animatedTextColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = spring(stiffness = 600f),
                label = "transfer_toggle_text"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(animatedBackground)
                    .clickable { onDirectionSelected(direction) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(direction.labelResId),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = animatedTextColor
                )
            }
        }
    }
}

/**
 * 카테고리 그리드 아이템 (아이콘 + 하단 텍스트)
 */
@Composable
private fun CategoryGridItem(
    emoji: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = emoji,
            fontSize = 28.sp
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 카테고리 선택 다이얼로그 (카테고리 변경용 - 하위 호환)
 */
@Composable
fun CategoryPickerDialog(
    currentCategory: String,
    onDismiss: () -> Unit,
    onCategorySelected: (String) -> Unit
) {
    CategorySelectDialog(
        currentCategory = currentCategory,
        showAllOption = false,
        onDismiss = onDismiss,
        onCategorySelected = { selected ->
            if (selected != null) {
                onCategorySelected(selected)
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
