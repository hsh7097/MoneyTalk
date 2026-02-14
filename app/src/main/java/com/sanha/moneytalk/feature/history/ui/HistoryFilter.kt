package com.sanha.moneytalk.feature.history.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.sanha.moneytalk.core.model.Category

private fun isFilterDefault(
    sortOrder: SortOrder,
    showExpenses: Boolean,
    showIncomes: Boolean,
    category: String?
): Boolean = sortOrder == SortOrder.DATE_DESC
        && showExpenses
        && showIncomes
        && category == null

/**
 * 필터 BottomSheet
 * 정렬 / 거래 유형 / 카테고리 선택 후 적용
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    currentSortOrder: SortOrder,
    currentShowExpenses: Boolean,
    currentShowIncomes: Boolean,
    currentCategory: String?,
    onDismiss: () -> Unit,
    onApply: (SortOrder, Boolean, Boolean, String?) -> Unit
) {
    // BottomSheet 내부 임시 상태 (적용 누르기 전까지 외부에 반영하지 않음)
    var tempSortOrder by remember { mutableStateOf(currentSortOrder) }
    var tempShowExpenses by remember { mutableStateOf(currentShowExpenses) }
    var tempShowIncomes by remember { mutableStateOf(currentShowIncomes) }
    var tempCategory by remember { mutableStateOf(currentCategory) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // 제목 + 초기화
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.history_filter_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!isFilterDefault(tempSortOrder, tempShowExpenses, tempShowIncomes, tempCategory)) {
                    Text(
                        text = stringResource(R.string.history_filter_reset),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                tempSortOrder = SortOrder.DATE_DESC
                                tempShowExpenses = true
                                tempShowIncomes = true
                                tempCategory = null
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // ── 정렬 ──
            Text(
                text = stringResource(R.string.history_filter_sort),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sortOptions = listOf(
                    SortOrder.DATE_DESC to stringResource(R.string.history_sort_date),
                    SortOrder.AMOUNT_DESC to stringResource(R.string.history_sort_amount_short),
                    SortOrder.STORE_FREQ to stringResource(R.string.history_sort_store)
                )
                sortOptions.forEach { (order, label) ->
                    FilterChipButton(
                        label = label,
                        isActive = tempSortOrder == order,
                        onClick = { tempSortOrder = order }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 거래 유형 ──
            Text(
                text = stringResource(R.string.history_filter_type),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 지출 체크박스 (수입이 꺼져 있으면 해제 불가)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (tempShowExpenses && !tempShowIncomes) return@clickable
                            tempShowExpenses = !tempShowExpenses
                        }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    Checkbox(
                        checked = tempShowExpenses,
                        onCheckedChange = {
                            if (!it && !tempShowIncomes) return@Checkbox
                            tempShowExpenses = it
                        }
                    )
                    Text(
                        text = stringResource(R.string.home_expense),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                // 수입 체크박스 (지출이 꺼져 있으면 해제 불가)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (tempShowIncomes && !tempShowExpenses) return@clickable
                            tempShowIncomes = !tempShowIncomes
                        }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    Checkbox(
                        checked = tempShowIncomes,
                        onCheckedChange = {
                            if (!it && !tempShowExpenses) return@Checkbox
                            tempShowIncomes = it
                        }
                    )
                    Text(
                        text = stringResource(R.string.home_income),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 카테고리 ──
            Text(
                text = stringResource(R.string.history_filter_category),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val categories = Category.parentEntries
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // "전체" 옵션
                item {
                    FilterCategoryGridItem(
                        emoji = "\uD83D\uDCCB",
                        label = stringResource(R.string.common_all),
                        isSelected = tempCategory == null,
                        onClick = { tempCategory = null }
                    )
                }
                items(categories) { category ->
                    FilterCategoryGridItem(
                        emoji = category.emoji,
                        label = category.displayName,
                        isSelected = category.displayName == tempCategory,
                        onClick = { tempCategory = category.displayName }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 적용 버튼
            Button(
                onClick = {
                    onApply(
                        tempSortOrder,
                        tempShowExpenses,
                        tempShowIncomes,
                        tempCategory
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.common_apply),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * BottomSheet 내 카테고리 그리드 아이템
 */
@Composable
internal fun FilterCategoryGridItem(
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
