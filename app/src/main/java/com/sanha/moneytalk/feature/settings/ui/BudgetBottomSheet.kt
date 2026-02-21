package com.sanha.moneytalk.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.model.Category

/**
 * 예산 설정 BottomSheet.
 *
 * 전체 예산 + 카테고리별 예산을 한 화면에서 설정.
 * - 전체 예산 설정 시 "금액" / "%" 입력 모드 전환 가능
 *   - % 모드: 전체 예산의 N%로 카테고리 예산 자동 계산
 *   - 금액 모드: 직접 금액 입력 + 전체 예산 대비 % 표시
 * - 카테고리별 예산 초기화 버튼 ("카테고리별 예산" 헤더 우측)
 * - 하단 저장 버튼 고정, 위 영역 스크롤
 * - 상단 300dp 마진으로 배경 항상 노출
 * - "AI 분류 중" 카테고리는 표시하지 않음
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetBottomSheet(
    currentTotalBudget: Int?,
    currentCategoryBudgets: Map<String, Int>,
    onDismiss: () -> Unit,
    onSave: (totalBudget: Int?, categoryBudgets: Map<String, Int>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 내부 임시 상태
    val tempTotalBudget = remember {
        mutableStateOf(currentTotalBudget?.toString() ?: "")
    }
    val tempCategoryBudgets = remember {
        mutableStateMapOf<String, String>().apply {
            currentCategoryBudgets.forEach { (category, amount) ->
                put(category, amount.toString())
            }
        }
    }

    // % 입력 모드 상태 (전체 예산이 있을 때만 활성화 가능)
    val isPercentMode = remember { mutableStateOf(false) }
    // % 입력 값 (카테고리 → "10", "30" 등)
    val tempCategoryPercents = remember {
        mutableStateMapOf<String, String>().apply {
            val totalBudget = currentTotalBudget ?: 0
            if (totalBudget > 0) {
                currentCategoryBudgets.forEach { (category, amount) ->
                    val percent = (amount * 100L / totalBudget).toInt()
                    if (percent > 0) put(category, percent.toString())
                }
            }
        }
    }

    val configuration = LocalConfiguration.current
    val maxSheetHeight = configuration.screenHeightDp.dp - 300.dp

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
            // 스크롤 영역
            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // 제목
                item(key = "title") {
                    Text(
                        text = stringResource(R.string.budget_sheet_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                }

                // 전체 예산 입력
                item(key = "total_budget") {
                    TotalBudgetInput(
                        amountText = tempTotalBudget.value,
                        onAmountChange = { tempTotalBudget.value = it }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // 카테고리별 예산 헤더 + 입력 모드 토글 + 초기화
                item(key = "category_header") {
                    val totalBudgetValue = tempTotalBudget.value.toIntOrNull() ?: 0
                    CategoryBudgetHeader(
                        isPercentMode = isPercentMode.value,
                        showModeToggle = totalBudgetValue > 0,
                        onModeToggle = { usePercent ->
                            val total = tempTotalBudget.value.toIntOrNull() ?: 0
                            if (usePercent && total > 0) {
                                // 금액 → % 전환: 현재 금액을 % 로 변환
                                tempCategoryPercents.clear()
                                tempCategoryBudgets.forEach { (category, amountStr) ->
                                    val amount = amountStr.toIntOrNull() ?: 0
                                    if (amount > 0) {
                                        val pct = (amount * 100L / total).toInt()
                                        if (pct > 0) tempCategoryPercents[category] = pct.toString()
                                    }
                                }
                            } else if (!usePercent && total > 0) {
                                // % → 금액 전환: % 를 금액으로 변환
                                tempCategoryBudgets.clear()
                                tempCategoryPercents.forEach { (category, pctStr) ->
                                    val pct = pctStr.toIntOrNull() ?: 0
                                    if (pct > 0) {
                                        val amount = (total.toLong() * pct / 100).toInt()
                                        tempCategoryBudgets[category] = amount.toString()
                                    }
                                }
                            }
                            isPercentMode.value = usePercent
                        },
                        onReset = {
                            tempCategoryBudgets.clear()
                            tempCategoryPercents.clear()
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 카테고리별 예산 입력 (parentEntries에서 UNCLASSIFIED 이미 제외됨)
                val categories = Category.parentEntries
                items(
                    items = categories,
                    key = { it.name }
                ) { category ->
                    val totalBudgetValue = tempTotalBudget.value.toIntOrNull() ?: 0
                    if (isPercentMode.value && totalBudgetValue > 0) {
                        CategoryBudgetPercentRow(
                            emoji = category.emoji,
                            displayName = category.displayName,
                            percentText = tempCategoryPercents[category.displayName] ?: "",
                            totalBudget = totalBudgetValue,
                            onPercentChange = { newPercent ->
                                if (newPercent.isBlank()) {
                                    tempCategoryPercents.remove(category.displayName)
                                    tempCategoryBudgets.remove(category.displayName)
                                } else {
                                    tempCategoryPercents[category.displayName] = newPercent
                                    // % → 금액 자동 동기화
                                    val pct = newPercent.toIntOrNull() ?: 0
                                    if (pct > 0) {
                                        val amount = (totalBudgetValue.toLong() * pct / 100).toInt()
                                        tempCategoryBudgets[category.displayName] = amount.toString()
                                    } else {
                                        tempCategoryBudgets.remove(category.displayName)
                                    }
                                }
                            }
                        )
                    } else {
                        CategoryBudgetRow(
                            emoji = category.emoji,
                            displayName = category.displayName,
                            amountText = tempCategoryBudgets[category.displayName] ?: "",
                            totalBudget = totalBudgetValue,
                            onAmountChange = { newAmount ->
                                if (newAmount.isBlank()) {
                                    tempCategoryBudgets.remove(category.displayName)
                                } else {
                                    tempCategoryBudgets[category.displayName] = newAmount
                                }
                            }
                        )
                    }
                }

                // 하단 여백
                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 하단 고정 저장 버튼
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Button(
                onClick = {
                    val totalBudget = tempTotalBudget.value.toIntOrNull()
                    val categoryBudgets = tempCategoryBudgets
                        .mapValues { it.value.toIntOrNull() ?: 0 }
                        .filterValues { it > 0 }
                    onSave(totalBudget, categoryBudgets)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.common_save),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 전체 예산 입력 영역.
 */
@Composable
private fun TotalBudgetInput(
    amountText: String,
    onAmountChange: (String) -> Unit
) {
    Text(
        text = stringResource(R.string.budget_total_label),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    OutlinedTextField(
        value = amountText,
        onValueChange = { input ->
            onAmountChange(input.filter { it.isDigit() })
        },
        placeholder = { Text(stringResource(R.string.budget_total_hint)) },
        suffix = { Text(stringResource(R.string.common_suffix_won)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 카테고리별 예산 헤더 + 금액/% 모드 토글 + 우측 초기화 버튼.
 */
@Composable
private fun CategoryBudgetHeader(
    isPercentMode: Boolean,
    showModeToggle: Boolean,
    onModeToggle: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.budget_category_header),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 금액/% 토글 (전체 예산 설정 시만 표시)
            if (showModeToggle) {
                FilterChip(
                    selected = !isPercentMode,
                    onClick = { onModeToggle(false) },
                    label = {
                        Text(
                            text = stringResource(R.string.budget_input_mode_amount),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
                FilterChip(
                    selected = isPercentMode,
                    onClick = { onModeToggle(true) },
                    label = {
                        Text(
                            text = stringResource(R.string.budget_input_mode_percent),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }

            Text(
                text = stringResource(R.string.budget_category_reset),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onReset() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * 카테고리별 예산 입력 행 (금액 모드).
 *
 * 전체 예산이 있으면 입력 금액의 % 자동 표시.
 */
@Composable
private fun CategoryBudgetRow(
    emoji: String,
    displayName: String,
    amountText: String,
    totalBudget: Int,
    onAmountChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 이모지 + 카테고리명
        Text(
            text = emoji,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(64.dp)
        )

        // 금액 입력
        OutlinedTextField(
            value = amountText,
            onValueChange = { input ->
                onAmountChange(input.filter { it.isDigit() })
            },
            suffix = { Text(stringResource(R.string.common_suffix_won)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        // % 표시 (전체 예산이 있을 때만)
        if (totalBudget > 0) {
            val amount = amountText.toIntOrNull() ?: 0
            val percent = if (amount > 0) (amount * 100 / totalBudget) else 0
            Text(
                text = "(${percent}%)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier
                    .width(52.dp)
                    .padding(start = 8.dp)
            )
        }
    }
}

/**
 * 카테고리별 예산 입력 행 (% 모드).
 *
 * % 입력 시 전체 예산 대비 금액을 자동 계산하여 표시.
 */
@Composable
private fun CategoryBudgetPercentRow(
    emoji: String,
    displayName: String,
    percentText: String,
    totalBudget: Int,
    onPercentChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 이모지 + 카테고리명
        Text(
            text = emoji,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(64.dp)
        )

        // % 입력
        OutlinedTextField(
            value = percentText,
            onValueChange = { input ->
                val filtered = input.filter { it.isDigit() }
                // 100% 초과 방지
                val value = filtered.toIntOrNull()
                if (value == null || value <= 100) {
                    onPercentChange(filtered)
                }
            },
            suffix = { Text("%") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        // 계산된 금액 표시
        val percent = percentText.toIntOrNull() ?: 0
        val calculatedAmount = if (percent > 0) (totalBudget.toLong() * percent / 100).toInt() else 0
        val numberFormat = remember { java.text.NumberFormat.getNumberInstance(java.util.Locale.KOREA) }
        Text(
            text = if (calculatedAmount > 0) "${numberFormat.format(calculatedAmount)}원" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier
                .width(80.dp)
                .padding(start = 8.dp)
        )
    }
}
