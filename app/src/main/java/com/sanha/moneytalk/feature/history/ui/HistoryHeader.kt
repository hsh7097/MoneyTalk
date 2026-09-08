package com.sanha.moneytalk.feature.history.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.CategoryInfo
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.ui.component.tab.SegmentedTabInfo
import com.sanha.moneytalk.core.ui.component.tab.SegmentedTabRowCompose
import com.sanha.moneytalk.core.util.DateUtils
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
            shape = RoundedCornerShape(16.dp),
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

/** 검색/추가는 월 요약과 함께 움직이며 보기·필터 도구 행의 폭을 차지하지 않는다. */
@Composable
internal fun HistoryTitleBar(onSearchClick: () -> Unit, onAddClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSearchClick, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.common_search))
        }
        IconButton(onClick = onAddClick, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.common_add))
        }
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

    val (currentYear, currentMonth) = DateUtils.getEffectiveCurrentMonth(monthStartDay)
    val canGoNext = year < currentYear || (year == currentYear && month < currentMonth)

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousMonth, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.home_previous_month)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.finance_history_month, year, month),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.finance_history_period, startDate, endDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                IconButton(
                    onClick = onNextMonth,
                    modifier = Modifier.size(48.dp),
                    enabled = canGoNext
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.home_next_month)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_expense),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.common_won, numberFormat.format(totalExpense)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.moneyTalkColors.expense
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_income),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.common_won, numberFormat.format(totalIncome)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.moneyTalkColors.income
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
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
    selectedCardNames: Set<String> = emptySet(),
    availableCardNames: List<String> = emptyList(),
    expenseCategories: List<CategoryInfo> = Category.expenseEntries,
    incomeCategories: List<CategoryInfo> = Category.incomeEntries,
    transferCategories: List<CategoryInfo> = Category.transferEntries,
    fixedExpenseFilter: FixedExpenseFilter = FixedExpenseFilter.ALL,
    onApplyFilter: (
        SortOrder,
        Boolean,
        Boolean,
        Boolean,
        Set<String>,
        Set<String>,
        Set<String>,
        Set<String>,
        FixedExpenseFilter
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onResetFilter: () -> Unit = {},
    hasSeenFilterOnboarding: Boolean = true,
    onFilterCoachMarkComplete: () -> Unit = {}
) {
    var showBottomSheet by remember { mutableStateOf(false) }

    val hasActiveFilter = selectedExpenseCategories.isNotEmpty()
            || selectedIncomeCategories.isNotEmpty()
            || selectedTransferCategories.isNotEmpty()
            || selectedCardNames.isNotEmpty()
            || sortOrder != SortOrder.DATE_DESC
            || !showExpenses
            || !showIncomes
            || !showTransfers
            || fixedExpenseFilter != FixedExpenseFilter.ALL

    val primaryColor = MaterialTheme.colorScheme.surface
    val onPrimaryColor = MaterialTheme.colorScheme.onSurface

    val listLabel = stringResource(R.string.history_view_list)
    val calendarLabel = stringResource(R.string.history_view_calendar)

    val tabs = remember(currentMode, primaryColor, onPrimaryColor, listLabel, calendarLabel) {
        listOf(
            object : SegmentedTabInfo {
                override val label = listLabel
                override val isSelected = currentMode == ViewMode.LIST
                override val selectedColor = primaryColor
                override val selectedTextColor = onPrimaryColor
            },
            object : SegmentedTabInfo {
                override val label = calendarLabel
                override val isSelected = currentMode == ViewMode.CALENDAR
                override val selectedColor = primaryColor
                override val selectedTextColor = onPrimaryColor
            }
        )
    }

    val hasMultipleFilters = listOf(
        selectedExpenseCategories.isNotEmpty() ||
                selectedIncomeCategories.isNotEmpty() ||
                selectedTransferCategories.isNotEmpty(),
        selectedCardNames.isNotEmpty(),
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
        selectedCardNames.isNotEmpty() ->
            stringResource(R.string.history_filter_active_card)
        !showExpenses || !showIncomes || !showTransfers ->
            stringResource(R.string.history_filter_active_type)
        fixedExpenseFilter == FixedExpenseFilter.FIXED_ONLY ->
            stringResource(R.string.history_filter_active_fixed_only)
        fixedExpenseFilter == FixedExpenseFilter.EXCLUDE_FIXED ->
            stringResource(R.string.history_filter_active_fixed_exclude)
        else ->
            stringResource(R.string.history_filter_active_sort)
    }

    val selectedCategories = selectedExpenseCategories + selectedIncomeCategories + selectedTransferCategories
    val filterLabel = when {
        !hasActiveFilter || hasMultipleFilters -> stringResource(R.string.common_filter)
        selectedCategories.isNotEmpty() -> selectedCategories.singleOrNull()
            ?: stringResource(R.string.history_filter_category)
        selectedCardNames.isNotEmpty() -> selectedCardNames.singleOrNull()
            ?: stringResource(R.string.history_filter_card)
        !showExpenses || !showIncomes || !showTransfers -> stringResource(R.string.history_filter_type)
        fixedExpenseFilter != FixedExpenseFilter.ALL -> stringResource(R.string.history_filter_fixed)
        else -> stringResource(R.string.history_filter_sort)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("history_view_controls"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            FilterActionButton(
                label = filterLabel,
                isActive = hasActiveFilter,
                activeDescription = if (hasActiveFilter) filterDescription else null,
                onClick = { showBottomSheet = true }
            )
        }
        if (hasActiveFilter) {
            IconButton(onClick = onResetFilter, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.history_filter_reset),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            currentCardNames = selectedCardNames,
            allCardNames = availableCardNames,
            allExpenseCategories = expenseCategories,
            allIncomeCategories = incomeCategories,
            allTransferCategories = transferCategories,
            currentFixedExpenseFilter = fixedExpenseFilter,
            hasSeenFilterOnboarding = hasSeenFilterOnboarding,
            onCoachMarkComplete = onFilterCoachMarkComplete,
            onDismiss = { showBottomSheet = false },
            onApply = { newSort, newShowExp, newShowInc, newShowTransfer, expCats, incCats, transferCats, cardNames, newFixedFilter ->
                onApplyFilter(
                    newSort,
                    newShowExp,
                    newShowInc,
                    newShowTransfer,
                    expCats,
                    incCats,
                    transferCats,
                    cardNames,
                    newFixedFilter
                )
                showBottomSheet = false
            }
        )
    }
}

@Composable
private fun FilterActionButton(
    label: String,
    isActive: Boolean,
    activeDescription: String?,
    onClick: () -> Unit
) {
    val filterLabel = stringResource(R.string.common_filter)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.heightIn(min = 48.dp).testTag("history_filter_action").semantics {
            contentDescription = filterLabel
            activeDescription?.let { stateDescription = it }
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                modifier = Modifier.widthIn(max = 80.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
