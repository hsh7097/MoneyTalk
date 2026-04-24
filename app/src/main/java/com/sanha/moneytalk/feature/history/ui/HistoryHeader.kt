package com.sanha.moneytalk.feature.history.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.FriendlyMoneyColors
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.ui.component.tab.SegmentedTabInfo
import com.sanha.moneytalk.core.ui.component.tab.SegmentedTabRowCompose
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.toDpTextUnit
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/** 내역 검색 바. 가게명/메모 키워드 입력으로 지출 내역을 실시간 검색 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back)
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.history_search_hint)) },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_clear)
                        )
                    }
                }
            }
        )
    }
}

/** 기간 요약 카드. 날짜 네비게이션과 해당 기간 총 수입/지출 금액을 표시 */
@Composable
fun PeriodSummaryCard(
    year: Int,
    month: Int,
    monthStartDay: Int,
    totalExpense: Int,
    totalIncome: Int,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    // 기간 계산 - DateUtils와 동일한 로직 사용
    val (startDate, endDate) = remember(year, month, monthStartDay) {
        val (startTs, endTs) = DateUtils.getCustomMonthPeriod(year, month, monthStartDay)
        val startCal = Calendar.getInstance().apply { timeInMillis = startTs }
        val endCal = Calendar.getInstance().apply { timeInMillis = endTs }
        val start = String.format(
            Locale.KOREA,
            "%02d.%02d.%02d",
            startCal.get(Calendar.YEAR) % 100,
            startCal.get(Calendar.MONTH) + 1,
            startCal.get(Calendar.DAY_OF_MONTH)
        )
        val end = String.format(
            Locale.KOREA,
            "%02d.%02d.%02d",
            endCal.get(Calendar.YEAR) % 100,
            endCal.get(Calendar.MONTH) + 1,
            endCal.get(Calendar.DAY_OF_MONTH)
        )
        start to end
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 왼쪽: 날짜 네비게이션 (줄넘김 형태)
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.home_previous_month),
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = startDate,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 18.toDpTextUnit),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(
                    modifier = Modifier
                        .width(6.dp)
                        .padding(vertical = 4.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = endDate,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 18.toDpTextUnit),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            val (effYear, effMonth) = DateUtils.getEffectiveCurrentMonth(monthStartDay)
            val isCurrentMonth = year >= effYear && month >= effMonth
            IconButton(
                onClick = onNextMonth,
                modifier = Modifier.size(28.dp),
                enabled = !isCurrentMonth
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.home_next_month),
                    modifier = Modifier.size(28.dp),
                    tint = if (isCurrentMonth) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        // 오른쪽: 지출/수입 요약 (오른쪽 정렬, 동적 너비)
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.width(IntrinsicSize.Max)
        ) {
            // 지출
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = stringResource(R.string.home_expense),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.common_won, numberFormat.format(totalExpense)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.toDpTextUnit),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.End
                )
            }
            // 수입 (0원이어도 항상 표시)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = stringResource(R.string.home_income),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.End
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        R.string.common_won,
                        numberFormat.format(totalIncome)
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.toDpTextUnit),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.moneyTalkColors.income,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/**
 * 탭(목록/달력) + 필터 아이콘 통합 Row
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterTabRow(
    currentMode: ViewMode,
    onModeChange: (ViewMode) -> Unit,
    sortOrder: SortOrder = SortOrder.DATE_DESC,
    showExpenses: Boolean = true,
    showIncomes: Boolean = true,
    showTransfers: Boolean = true,
    selectedExpenseCategories: Set<String> = emptySet(),
    selectedIncomeCategories: Set<String> = emptySet(),
    selectedTransferCategories: Set<String> = emptySet(),
    fixedExpenseFilter: FixedExpenseFilter = FixedExpenseFilter.ALL,
    onApplyFilter: (
        SortOrder,
        Boolean,
        Boolean,
        Boolean,
        Set<String>,
        Set<String>,
        Set<String>,
        FixedExpenseFilter
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onResetFilter: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onAddClick: () -> Unit = {},
    hasSeenFilterOnboarding: Boolean = true,
    onFilterCoachMarkComplete: () -> Unit = {}
) {
    var showBottomSheet by remember { mutableStateOf(false) }

    val hasActiveFilter = selectedExpenseCategories.isNotEmpty()
            || selectedIncomeCategories.isNotEmpty()
            || selectedTransferCategories.isNotEmpty()
            || sortOrder != SortOrder.DATE_DESC
            || !showExpenses
            || !showIncomes
            || !showTransfers
            || fixedExpenseFilter != FixedExpenseFilter.ALL

    val primaryColor = FriendlyMoneyColors.Mint
    val onPrimaryColor = Color.White

    val listLabel = stringResource(R.string.history_view_list)
    val calendarLabel = stringResource(R.string.history_view_calendar)
    val listIcon = Icons.AutoMirrored.Filled.List
    val calendarIcon = Icons.Default.DateRange

    val tabs = remember(currentMode, primaryColor, onPrimaryColor) {
        listOf(
            object : SegmentedTabInfo {
                override val label = listLabel
                override val isSelected = currentMode == ViewMode.LIST
                override val selectedColor = primaryColor
                override val selectedTextColor = onPrimaryColor
                override val icon = listIcon
            },
            object : SegmentedTabInfo {
                override val label = calendarLabel
                override val isSelected = currentMode == ViewMode.CALENDAR
                override val selectedColor = primaryColor
                override val selectedTextColor = onPrimaryColor
                override val icon = calendarIcon
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = 16.dp, end = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            SegmentedTabRowCompose(
                tabs = tabs,
                onTabClick = { index ->
                    when (index) {
                        0 -> onModeChange(ViewMode.LIST)
                        1 -> onModeChange(ViewMode.CALENDAR)
                    }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))

            if (hasActiveFilter) {
                val hasMultipleFilters = listOf(
                    selectedExpenseCategories.isNotEmpty() ||
                            selectedIncomeCategories.isNotEmpty() ||
                            selectedTransferCategories.isNotEmpty(),
                    sortOrder != SortOrder.DATE_DESC,
                    !showExpenses || !showIncomes || !showTransfers,
                    fixedExpenseFilter != FixedExpenseFilter.ALL
                ).count { it } > 1

                val filterDescription = when {
                    hasMultipleFilters ->
                        stringResource(R.string.history_filter_active_combined)
                    selectedExpenseCategories.isNotEmpty() ||
                            selectedIncomeCategories.isNotEmpty() ||
                            selectedTransferCategories.isNotEmpty() ->
                        stringResource(R.string.history_filter_active_category)
                    !showExpenses || !showIncomes || !showTransfers ->
                        stringResource(R.string.history_filter_active_type)
                    fixedExpenseFilter == FixedExpenseFilter.FIXED_ONLY ->
                        stringResource(R.string.history_filter_active_fixed_only)
                    fixedExpenseFilter == FixedExpenseFilter.EXCLUDE_FIXED ->
                        stringResource(R.string.history_filter_active_fixed_exclude)
                    else ->
                        stringResource(R.string.history_filter_active_sort)
                }

                FilterStatusChip(
                    label = filterDescription,
                    onResetFilter = onResetFilter
                )
            } else {
                FilterActionButton(onClick = { showBottomSheet = true })
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.common_search),
                    modifier = Modifier.size(24.dp),
                    tint = FriendlyMoneyColors.Mint
                )
            }
            IconButton(
                onClick = onAddClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.common_add),
                    modifier = Modifier.size(24.dp),
                    tint = FriendlyMoneyColors.Coral
                )
            }
        }
    }

    // 필터 BottomSheet
    if (showBottomSheet) {
        FilterBottomSheet(
            currentSortOrder = sortOrder,
            currentShowExpenses = showExpenses,
            currentShowIncomes = showIncomes,
            currentShowTransfers = showTransfers,
            currentExpenseCategories = selectedExpenseCategories,
            currentIncomeCategories = selectedIncomeCategories,
            currentTransferCategories = selectedTransferCategories,
            currentFixedExpenseFilter = fixedExpenseFilter,
            hasSeenFilterOnboarding = hasSeenFilterOnboarding,
            onCoachMarkComplete = onFilterCoachMarkComplete,
            onDismiss = { showBottomSheet = false },
            onApply = { newSort, newShowExp, newShowInc, newShowTransfer, expCats, incCats, transferCats, newFixedFilter ->
                onApplyFilter(
                    newSort,
                    newShowExp,
                    newShowInc,
                    newShowTransfer,
                    expCats,
                    incCats,
                    transferCats,
                    newFixedFilter
                )
                showBottomSheet = false
            }
        )
    }
}

@Composable
private fun FilterActionButton(
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(FriendlyMoneyColors.elevatedCardBackground)
            .border(1.dp, FriendlyMoneyColors.border, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.common_filter),
                fontSize = 14.toDpTextUnit,
                fontWeight = FontWeight.Medium,
                color = FriendlyMoneyColors.textSecondary
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = FriendlyMoneyColors.textSecondary
            )
        }
    }
}

@Composable
private fun FilterStatusChip(
    label: String,
    onResetFilter: () -> Unit
) {
    val chipColor = MaterialTheme.moneyTalkColors.income

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, chipColor, RoundedCornerShape(12.dp))
            .clickable { onResetFilter() }
            .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.toDpTextUnit,
                fontWeight = FontWeight.Medium,
                color = chipColor
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.common_clear),
                modifier = Modifier.size(16.dp),
                tint = chipColor
            )
        }
    }
}
