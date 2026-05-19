package com.sanha.moneytalk.feature.history.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.CategoryInfo
import com.sanha.moneytalk.core.theme.FriendlyMoneyColors
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkOverlay
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkState
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkTargetRegistry
import com.sanha.moneytalk.core.ui.coachmark.onboardingTarget
import com.sanha.moneytalk.core.util.toDpTextUnit
import com.sanha.moneytalk.feature.history.ui.coachmark.filterCoachMarkSteps
import kotlinx.coroutines.delay

private enum class CategorySheetType(
    @StringRes val titleResId: Int
) {
    EXPENSE(R.string.history_filter_expense_category_title),
    INCOME(R.string.history_filter_income_category_title),
    TRANSFER(R.string.history_filter_transfer_category_title)
}

private enum class FilterTransactionType(
    @StringRes val labelResId: Int
) {
    ALL(R.string.common_all),
    EXPENSE(R.string.home_expense),
    INCOME(R.string.home_income),
    TRANSFER(R.string.transaction_type_transfer)
}

private fun isFilterDefault(
    sortOrder: SortOrder,
    showExpenses: Boolean,
    showIncomes: Boolean,
    showTransfers: Boolean,
    expenseCategories: Set<String>,
    incomeCategories: Set<String>,
    transferCategories: Set<String>,
    fixedExpenseFilter: FixedExpenseFilter = FixedExpenseFilter.ALL
): Boolean = sortOrder == SortOrder.DATE_DESC &&
        showExpenses &&
        showIncomes &&
        showTransfers &&
        expenseCategories.isEmpty() &&
        incomeCategories.isEmpty() &&
        transferCategories.isEmpty() &&
        fixedExpenseFilter == FixedExpenseFilter.ALL

private fun buildCategorySummary(
    selectedCategories: Set<String>,
    allCategories: List<CategoryInfo>,
    allText: String,
    multiFormat: String
): String {
    if (selectedCategories.isEmpty()) return allText
    val firstCategory = allCategories.firstOrNull { selectedCategories.contains(it.displayName) }
        ?.displayName ?: selectedCategories.first()
    val remainCount = selectedCategories.size - 1
    return if (remainCount <= 0) firstCategory else String.format(multiFormat, firstCategory, remainCount)
}

private fun selectedFilterTypes(
    showExpenses: Boolean,
    showIncomes: Boolean,
    showTransfers: Boolean
): List<FilterTransactionType> = buildList {
    if (showExpenses) add(FilterTransactionType.EXPENSE)
    if (showIncomes) add(FilterTransactionType.INCOME)
    if (showTransfers) add(FilterTransactionType.TRANSFER)
}

private fun filterTypeIcon(type: FilterTransactionType) = when (type) {
    FilterTransactionType.ALL -> Icons.Default.RadioButtonChecked
    FilterTransactionType.EXPENSE -> Icons.Default.ArrowDownward
    FilterTransactionType.INCOME -> Icons.Default.ArrowUpward
    FilterTransactionType.TRANSFER -> Icons.Default.SwapHoriz
}

private fun filterTypeAccentColor(type: FilterTransactionType): Color = when (type) {
    FilterTransactionType.ALL -> FriendlyMoneyColors.Honey
    FilterTransactionType.EXPENSE -> FriendlyMoneyColors.Coral
    FilterTransactionType.INCOME -> FriendlyMoneyColors.Mint
    FilterTransactionType.TRANSFER -> FriendlyMoneyColors.Sky
}

/**
 * 필터 BottomSheet.
 *
 * 고정 거래/정렬을 먼저 조정하고, 카테고리 단계에서 거래 유형별 범위를 좁힌 뒤 적용.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    currentSortOrder: SortOrder,
    currentShowExpenses: Boolean,
    currentShowIncomes: Boolean,
    currentShowTransfers: Boolean = true,
    currentExpenseCategories: Set<String> = emptySet(),
    currentIncomeCategories: Set<String> = emptySet(),
    currentTransferCategories: Set<String> = emptySet(),
    allExpenseCategories: List<CategoryInfo> = Category.expenseEntries,
    allIncomeCategories: List<CategoryInfo> = Category.incomeEntries,
    allTransferCategories: List<CategoryInfo> = Category.transferEntries,
    currentFixedExpenseFilter: FixedExpenseFilter = FixedExpenseFilter.ALL,
    hasSeenFilterOnboarding: Boolean = true,
    onCoachMarkComplete: () -> Unit = {},
    onDismiss: () -> Unit,
    onApply: (
        SortOrder,
        Boolean,
        Boolean,
        Boolean,
        Set<String>,
        Set<String>,
        Set<String>,
        FixedExpenseFilter
    ) -> Unit
) {
    var tempSortOrder by remember { mutableStateOf(currentSortOrder) }
    var tempShowExpenses by remember { mutableStateOf(currentShowExpenses) }
    var tempShowIncomes by remember { mutableStateOf(currentShowIncomes) }
    var tempShowTransfers by remember { mutableStateOf(currentShowTransfers) }
    var tempExpenseCategories by remember { mutableStateOf(currentExpenseCategories) }
    var tempIncomeCategories by remember { mutableStateOf(currentIncomeCategories) }
    var tempTransferCategories by remember { mutableStateOf(currentTransferCategories) }
    var tempFixedFilter by remember { mutableStateOf(currentFixedExpenseFilter) }
    var categorySheetType by remember { mutableStateOf<CategorySheetType?>(null) }

    // 코치마크 (필터 온보딩)
    val filterCoachMarkRegistry = remember { CoachMarkTargetRegistry() }
    val filterCoachMarkState = remember { CoachMarkState() }
    val allFilterSteps = remember { filterCoachMarkSteps() }

    LaunchedEffect(hasSeenFilterOnboarding) {
        if (!hasSeenFilterOnboarding) {
            delay(1000)
            val visibleSteps = allFilterSteps.filter { it.targetKey in filterCoachMarkRegistry.targets }
            if (visibleSteps.isNotEmpty()) {
                filterCoachMarkState.show(visibleSteps)
            }
        }
    }

    // 3개 타입 모두 체크(기본) 상태에서 첫 카테고리 세부 선택 시 나머지 타입 자동 해제
    var hasAutoCollapsed by remember {
        mutableStateOf(!(currentShowExpenses && currentShowIncomes && currentShowTransfers))
    }

    val configuration = LocalConfiguration.current
    val compactSheetHeight = configuration.screenHeightDp.dp * 0.68f
    val expandedSheetHeight = configuration.screenHeightDp.dp - 100.dp
    val density = LocalDensity.current
    val maxSheetExpansionPx = with(density) {
        (expandedSheetHeight - compactSheetHeight).toPx().coerceAtLeast(0f)
    }
    var sheetExpansionPx by remember { mutableStateOf(0f) }
    val isSheetExpanded = sheetExpansionPx >= maxSheetExpansionPx - 1f
    val sheetHeight = compactSheetHeight + with(density) { sheetExpansionPx.toDp() }
    val bodyScrollState = rememberScrollState()
    LaunchedEffect(maxSheetExpansionPx) {
        sheetExpansionPx = sheetExpansionPx.coerceIn(0f, maxSheetExpansionPx)
    }
    LaunchedEffect(sheetExpansionPx) {
        if (sheetExpansionPx == 0f) {
            bodyScrollState.scrollTo(0)
        }
    }
    val expandBeforeBodyScroll = remember(maxSheetExpansionPx, bodyScrollState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                if (available.y < 0f && sheetExpansionPx < maxSheetExpansionPx) {
                    val consumed = minOf(-available.y, maxSheetExpansionPx - sheetExpansionPx)
                    sheetExpansionPx += consumed
                    return Offset(x = 0f, y = -consumed)
                }

                if (available.y > 0f && bodyScrollState.value == 0 && sheetExpansionPx > 0f) {
                    val consumed = minOf(available.y, sheetExpansionPx)
                    sheetExpansionPx -= consumed
                    return Offset(x = 0f, y = consumed)
                }

                return Offset.Zero
            }
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val allText = stringResource(R.string.common_all)
    val summaryFormat = stringResource(R.string.history_filter_category_summary_multiple)
    val selectedTypes = selectedFilterTypes(tempShowExpenses, tempShowIncomes, tempShowTransfers)
    val allTypesSelected = tempShowExpenses && tempShowIncomes && tempShowTransfers
    val effectiveFixedFilter = tempFixedFilter
    val isDefaultFilter = isFilterDefault(
        tempSortOrder,
        tempShowExpenses,
        tempShowIncomes,
        tempShowTransfers,
        tempExpenseCategories,
        tempIncomeCategories,
        tempTransferCategories,
        effectiveFixedFilter
    )
    val resetFilter = {
        tempSortOrder = SortOrder.DATE_DESC
        tempShowExpenses = true
        tempShowIncomes = true
        tempShowTransfers = true
        tempExpenseCategories = emptySet()
        tempIncomeCategories = emptySet()
        tempTransferCategories = emptySet()
        tempFixedFilter = FixedExpenseFilter.ALL
        hasAutoCollapsed = false
    }
    val selectAllTypes = {
        tempShowExpenses = true
        tempShowIncomes = true
        tempShowTransfers = true
        tempExpenseCategories = emptySet()
        tempIncomeCategories = emptySet()
        tempTransferCategories = emptySet()
        hasAutoCollapsed = false
    }
    val selectSingleType: (FilterTransactionType) -> Unit = { selectedType ->
        tempShowExpenses = selectedType == FilterTransactionType.EXPENSE
        tempShowIncomes = selectedType == FilterTransactionType.INCOME
        tempShowTransfers = selectedType == FilterTransactionType.TRANSFER

        if (selectedType != FilterTransactionType.EXPENSE) {
            tempExpenseCategories = emptySet()
        }
        if (selectedType != FilterTransactionType.INCOME) {
            tempIncomeCategories = emptySet()
        }
        if (selectedType != FilterTransactionType.TRANSFER) {
            tempTransferCategories = emptySet()
        }
        hasAutoCollapsed = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
            ) {
                // === 고정 영역: 타이틀/액션 ===
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.history_filter_title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.toDpTextUnit),
                        fontWeight = FontWeight.Bold,
                        color = FriendlyMoneyColors.textPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                sheetExpansionPx = if (isSheetExpanded) {
                                    0f
                                } else {
                                    maxSheetExpansionPx
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(
                                    if (isSheetExpanded) {
                                        R.string.history_filter_collapse_sheet
                                    } else {
                                        R.string.history_filter_expand_sheet
                                    }
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = FriendlyMoneyColors.Mint
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // === 스크롤 영역: 고정 거래 + 정렬 + 카테고리 ===
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .nestedScroll(expandBeforeBodyScroll)
                        .verticalScroll(bodyScrollState)
                        .padding(horizontal = 20.dp)
                ) {
                    FilterGuideCard(modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    FilterNoticeCard(
                        text = stringResource(R.string.history_filter_and_notice),
                        modifier = Modifier.padding(bottom = 18.dp)
                    )

                    Column(
                        modifier = Modifier.onboardingTarget("filter_sort", filterCoachMarkRegistry)
                    ) {
                        Text(
                            text = stringResource(R.string.history_filter_fixed),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = FriendlyMoneyColors.textPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val fixedOptions = listOf(
                            FixedExpenseFilter.ALL to stringResource(R.string.history_filter_fixed_all),
                            FixedExpenseFilter.FIXED_ONLY to stringResource(R.string.history_filter_fixed_only),
                            FixedExpenseFilter.EXCLUDE_FIXED to stringResource(R.string.history_filter_fixed_exclude)
                        )
                        FilterOptionPillRow(
                            options = fixedOptions.map { it.second },
                            selectedIndex = fixedOptions.indexOfFirst { it.first == tempFixedFilter },
                            onOptionSelected = { index ->
                                tempFixedFilter = fixedOptions[index].first
                            }
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = stringResource(R.string.history_filter_sort),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = FriendlyMoneyColors.textPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val sortOptions = listOf(
                            SortOrder.DATE_DESC to stringResource(R.string.history_sort_date),
                            SortOrder.AMOUNT_DESC to stringResource(R.string.history_sort_amount_short),
                            SortOrder.STORE_FREQ to stringResource(R.string.history_sort_store)
                        )
                        FilterOptionPillRow(
                            options = sortOptions.map { it.second },
                            selectedIndex = sortOptions.indexOfFirst { it.first == tempSortOrder },
                            onOptionSelected = { index ->
                                tempSortOrder = sortOptions[index].first
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Column(
                        modifier = Modifier.onboardingTarget("filter_category", filterCoachMarkRegistry)
                    ) {
                        Text(
                            text = stringResource(R.string.history_filter_category),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = FriendlyMoneyColors.textPrimary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = stringResource(R.string.history_filter_category_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = FriendlyMoneyColors.textSecondary,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        Column(
                            modifier = Modifier.onboardingTarget("filter_type", filterCoachMarkRegistry)
                        ) {
                            Text(
                                text = stringResource(R.string.history_filter_type),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.toDpTextUnit),
                                fontWeight = FontWeight.SemiBold,
                                color = FriendlyMoneyColors.textPrimary,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            FilterTransactionTypeSelector(
                                showExpenses = tempShowExpenses,
                                showIncomes = tempShowIncomes,
                                showTransfers = tempShowTransfers,
                                onTypeClick = { type ->
                                    when (type) {
                                        FilterTransactionType.ALL -> selectAllTypes()
                                        FilterTransactionType.EXPENSE -> {
                                            if (!allTypesSelected && selectedTypes.size == 1 && tempShowExpenses) {
                                                selectAllTypes()
                                            } else {
                                                selectSingleType(FilterTransactionType.EXPENSE)
                                            }
                                        }
                                        FilterTransactionType.INCOME -> {
                                            if (!allTypesSelected && selectedTypes.size == 1 && tempShowIncomes) {
                                                selectAllTypes()
                                            } else {
                                                selectSingleType(FilterTransactionType.INCOME)
                                            }
                                        }
                                        FilterTransactionType.TRANSFER -> {
                                            if (!allTypesSelected && selectedTypes.size == 1 && tempShowTransfers) {
                                                selectAllTypes()
                                            } else {
                                                selectSingleType(FilterTransactionType.TRANSFER)
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (selectedTypes.size == 1) {
                            when (selectedTypes.first()) {
                                FilterTransactionType.EXPENSE -> FilterCategoryChipGroup(
                                    sheetType = CategorySheetType.EXPENSE,
                                    categories = allExpenseCategories,
                                    selectedCategories = tempExpenseCategories,
                                    allText = allText,
                                    onSelectionChanged = { tempExpenseCategories = it },
                                    onMoreClick = { categorySheetType = CategorySheetType.EXPENSE }
                                )
                                FilterTransactionType.INCOME -> FilterCategoryChipGroup(
                                    sheetType = CategorySheetType.INCOME,
                                    categories = allIncomeCategories,
                                    selectedCategories = tempIncomeCategories,
                                    allText = allText,
                                    onSelectionChanged = { tempIncomeCategories = it },
                                    onMoreClick = { categorySheetType = CategorySheetType.INCOME }
                                )
                                FilterTransactionType.TRANSFER -> FilterCategoryChipGroup(
                                    sheetType = CategorySheetType.TRANSFER,
                                    categories = allTransferCategories,
                                    selectedCategories = tempTransferCategories,
                                    allText = allText,
                                    onSelectionChanged = { tempTransferCategories = it },
                                    onMoreClick = { categorySheetType = CategorySheetType.TRANSFER }
                                )
                                FilterTransactionType.ALL -> Unit
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.background)
                                    .border(1.dp, FriendlyMoneyColors.border, RoundedCornerShape(16.dp))
                                    .padding(vertical = 6.dp)
                            ) {
                                if (tempShowExpenses) {
                                    FilterCategorySummaryRow(
                                        label = stringResource(R.string.home_expense),
                                        summary = buildCategorySummary(
                                            selectedCategories = tempExpenseCategories,
                                            allCategories = allExpenseCategories,
                                            allText = allText,
                                            multiFormat = summaryFormat
                                        ),
                                        onCategoryClick = { categorySheetType = CategorySheetType.EXPENSE }
                                    )
                                }
                                if (tempShowIncomes) {
                                    if (tempShowExpenses) {
                                        HorizontalDivider(color = FriendlyMoneyColors.border)
                                    }
                                    FilterCategorySummaryRow(
                                        label = stringResource(R.string.home_income),
                                        summary = buildCategorySummary(
                                            selectedCategories = tempIncomeCategories,
                                            allCategories = allIncomeCategories,
                                            allText = allText,
                                            multiFormat = summaryFormat
                                        ),
                                        onCategoryClick = { categorySheetType = CategorySheetType.INCOME }
                                    )
                                }
                                if (tempShowTransfers) {
                                    if (tempShowExpenses || tempShowIncomes) {
                                        HorizontalDivider(color = FriendlyMoneyColors.border)
                                    }
                                    FilterCategorySummaryRow(
                                        label = stringResource(R.string.transaction_type_transfer),
                                        summary = buildCategorySummary(
                                            selectedCategories = tempTransferCategories,
                                            allCategories = allTransferCategories,
                                            allText = allText,
                                            multiFormat = summaryFormat
                                        ),
                                        onCategoryClick = { categorySheetType = CategorySheetType.TRANSFER }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(88.dp))
                }

                HorizontalDivider(color = FriendlyMoneyColors.border)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = resetFilter,
                        enabled = !isDefaultFilter,
                        modifier = Modifier
                            .weight(0.9f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, FriendlyMoneyColors.border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = FriendlyMoneyColors.Mint
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.history_filter_reset),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = {
                            onApply(
                                tempSortOrder,
                                tempShowExpenses,
                                tempShowIncomes,
                                tempShowTransfers,
                                tempExpenseCategories,
                                tempIncomeCategories,
                                tempTransferCategories,
                                effectiveFixedFilter
                            )
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FriendlyMoneyColors.Mint,
                            contentColor = FriendlyMoneyColors.Ink
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.common_apply),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // matchParentSize: Column 크기에 맞추되 Box 사이즈 결정에 영향 안 줌
            // (fillMaxSize 사용 시 BottomSheet가 풀스크린으로 확장되는 문제 방지)
            Box(modifier = Modifier.matchParentSize()) {
                CoachMarkOverlay(
                    state = filterCoachMarkState,
                    targetRegistry = filterCoachMarkRegistry,
                    onComplete = onCoachMarkComplete
                )
            }
        } // Box
    }

    categorySheetType?.let { type ->
        val selected = when (type) {
            CategorySheetType.EXPENSE -> tempExpenseCategories
            CategorySheetType.INCOME -> tempIncomeCategories
            CategorySheetType.TRANSFER -> tempTransferCategories
        }
        val isTypeChecked = when (type) {
            CategorySheetType.EXPENSE -> tempShowExpenses
            CategorySheetType.INCOME -> tempShowIncomes
            CategorySheetType.TRANSFER -> tempShowTransfers
        }

        CategoryFilterListBottomSheet(
            sheetType = type,
            categories = when (type) {
                CategorySheetType.EXPENSE -> allExpenseCategories
                CategorySheetType.INCOME -> allIncomeCategories
                CategorySheetType.TRANSFER -> allTransferCategories
            },
            isTypeChecked = isTypeChecked,
            selectedCategories = selected,
            onSelectionChanged = { updated ->
                when (type) {
                    CategorySheetType.EXPENSE -> tempExpenseCategories = updated
                    CategorySheetType.INCOME -> tempIncomeCategories = updated
                    CategorySheetType.TRANSFER -> tempTransferCategories = updated
                }
                // 체크되지 않은 타입에서 카테고리 선택 시 자동 체크
                val isCurrentlyChecked = when (type) {
                    CategorySheetType.EXPENSE -> tempShowExpenses
                    CategorySheetType.INCOME -> tempShowIncomes
                    CategorySheetType.TRANSFER -> tempShowTransfers
                }
                if (!isCurrentlyChecked) {
                    when (type) {
                        CategorySheetType.EXPENSE -> tempShowExpenses = true
                        CategorySheetType.INCOME -> tempShowIncomes = true
                        CategorySheetType.TRANSFER -> tempShowTransfers = true
                    }
                }
                // 기본 상태(3개 모두 체크)에서 첫 카테고리 세부 선택 시 나머지 타입 자동 해제
                if (!hasAutoCollapsed && updated.isNotEmpty() &&
                    tempShowExpenses && tempShowIncomes && tempShowTransfers
                ) {
                    when (type) {
                        CategorySheetType.EXPENSE -> {
                            tempShowIncomes = false
                            tempShowTransfers = false
                        }
                        CategorySheetType.INCOME -> {
                            tempShowExpenses = false
                            tempShowTransfers = false
                        }
                        CategorySheetType.TRANSFER -> {
                            tempShowExpenses = false
                            tempShowIncomes = false
                        }
                    }
                    hasAutoCollapsed = true
                }
            },
            onDismiss = { categorySheetType = null }
        )
    }
}

@Composable
private fun FilterTransactionTypeSelector(
    showExpenses: Boolean,
    showIncomes: Boolean,
    showTransfers: Boolean,
    onTypeClick: (FilterTransactionType) -> Unit
) {
    val allSelected = showExpenses && showIncomes && showTransfers

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterTransactionType.entries.forEach { type ->
            val selected = when (type) {
                FilterTransactionType.ALL -> allSelected
                FilterTransactionType.EXPENSE -> !allSelected && showExpenses
                FilterTransactionType.INCOME -> !allSelected && showIncomes
                FilterTransactionType.TRANSFER -> !allSelected && showTransfers
            }

            FilterTypeTile(
                type = type,
                label = stringResource(type.labelResId),
                selected = selected,
                onClick = { onTypeClick(type) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FilterGuideCard(
    modifier: Modifier = Modifier
) {
    val guideShape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(guideShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        FriendlyMoneyColors.Mint.copy(alpha = if (FriendlyMoneyColors.isDark) 0.24f else 0.16f),
                        FriendlyMoneyColors.Sky.copy(alpha = if (FriendlyMoneyColors.isDark) 0.12f else 0.08f)
                    )
                )
            )
            .border(1.dp, FriendlyMoneyColors.Mint.copy(alpha = 0.22f), guideShape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(FriendlyMoneyColors.Mint.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.history_filter_guide_icon),
                fontSize = 18.toDpTextUnit
            )
        }
        Text(
            text = stringResource(R.string.history_filter_guide_text),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.toDpTextUnit,
                lineHeight = 18.toDpTextUnit
            ),
            fontWeight = FontWeight.SemiBold,
            color = FriendlyMoneyColors.textPrimary
        )
    }
}

@Composable
private fun FilterTypeTile(
    type: FilterTransactionType,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val accentColor = filterTypeAccentColor(type)
    val backgroundColor = if (selected) {
        FriendlyMoneyColors.Mint.copy(alpha = if (FriendlyMoneyColors.isDark) 0.82f else 0.20f)
    } else {
        MaterialTheme.colorScheme.background
    }
    val borderColor = if (selected) {
        FriendlyMoneyColors.Mint
    } else {
        FriendlyMoneyColors.border
    }
    val iconColor = if (selected) {
        when (type) {
            FilterTransactionType.ALL -> FriendlyMoneyColors.Ink
            FilterTransactionType.EXPENSE -> FriendlyMoneyColors.Coral
            FilterTransactionType.INCOME -> FriendlyMoneyColors.MintDeep
            FilterTransactionType.TRANSFER -> FriendlyMoneyColors.Ink
        }
    } else {
        FriendlyMoneyColors.textSecondary
    }
    val textColor = if (selected) {
        if (FriendlyMoneyColors.isDark) FriendlyMoneyColors.Ink else FriendlyMoneyColors.textPrimary
    } else {
        FriendlyMoneyColors.textSecondary
    }

    Column(
        modifier = modifier
            .height(58.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = filterTypeIcon(type),
            contentDescription = null,
            tint = if (selected) iconColor else accentColor.copy(alpha = 0.58f),
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.toDpTextUnit),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FilterNoticeCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(FriendlyMoneyColors.Mint.copy(alpha = if (FriendlyMoneyColors.isDark) 0.16f else 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = FriendlyMoneyColors.Mint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = FriendlyMoneyColors.Mint
        )
    }
}

@Composable
private fun FilterOptionPillRow(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, label ->
            FilterOptionPill(
                label = label,
                selected = index == selectedIndex,
                onClick = { onOptionSelected(index) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FilterOptionPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val backgroundColor = if (selected) {
        FriendlyMoneyColors.Mint.copy(alpha = if (FriendlyMoneyColors.isDark) 0.78f else 0.18f)
    } else {
        Color.Transparent
    }
    val textColor = if (selected && FriendlyMoneyColors.isDark) FriendlyMoneyColors.Ink else {
        if (selected) FriendlyMoneyColors.Mint else FriendlyMoneyColors.textSecondary
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) FriendlyMoneyColors.Mint else FriendlyMoneyColors.border
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.toDpTextUnit),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterCategoryChipGroup(
    sheetType: CategorySheetType,
    categories: List<CategoryInfo>,
    selectedCategories: Set<String>,
    allText: String,
    onSelectionChanged: (Set<String>) -> Unit,
    onMoreClick: () -> Unit
) {
    val previewCategories = (categories.filter { selectedCategories.contains(it.displayName) } +
            categories.filterNot { selectedCategories.contains(it.displayName) })
        .distinctBy { it.displayName }
        .take(6)

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(sheetType.titleResId),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.toDpTextUnit),
                fontWeight = FontWeight.SemiBold,
                color = FriendlyMoneyColors.textPrimary
            )
            Text(
                text = stringResource(R.string.history_filter_more_categories),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = FriendlyMoneyColors.Mint,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onMoreClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryChoiceChip(
                label = allText,
                selected = selectedCategories.isEmpty(),
                onClick = { onSelectionChanged(emptySet()) }
            )
            previewCategories.forEach { category ->
                CategoryChoiceChip(
                    label = category.displayName,
                    emoji = category.emoji,
                    selected = selectedCategories.contains(category.displayName),
                    onClick = {
                        val next = if (selectedCategories.contains(category.displayName)) {
                            selectedCategories - category.displayName
                        } else {
                            selectedCategories + category.displayName
                        }
                        onSelectionChanged(next)
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    emoji: String? = null
) {
    val shape = RoundedCornerShape(11.dp)
    val backgroundColor = if (selected) {
        FriendlyMoneyColors.Mint.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.background
    }
    val borderColor = if (selected) FriendlyMoneyColors.Mint else FriendlyMoneyColors.border
    val textColor = if (selected) {
        FriendlyMoneyColors.Mint
    } else {
        FriendlyMoneyColors.textSecondary
    }

    Surface(
        onClick = onClick,
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (emoji != null) {
                Text(
                    text = emoji,
                    fontSize = 15.toDpTextUnit
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.toDpTextUnit),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = FriendlyMoneyColors.Mint,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun FilterCategorySummaryRow(
    label: String,
    summary: String,
    onCategoryClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onCategoryClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = FriendlyMoneyColors.textPrimary
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                color = FriendlyMoneyColors.textPrimary
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = FriendlyMoneyColors.textSecondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterListBottomSheet(
    sheetType: CategorySheetType,
    categories: List<CategoryInfo>,
    isTypeChecked: Boolean,
    selectedCategories: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val compactSheetHeight = screenHeight * 0.68f
    val expandedSheetHeight = screenHeight - 72.dp
    var isExpanded by remember { mutableStateOf(false) }
    val sheetHeight = if (isExpanded) expandedSheetHeight else compactSheetHeight
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectionSummary = if (selectedCategories.isEmpty()) {
        stringResource(R.string.history_filter_all_categories)
    } else {
        stringResource(R.string.history_filter_selected_count, selectedCategories.size)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(sheetType.titleResId),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FriendlyMoneyColors.textPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { isExpanded = !isExpanded }) {
                            Text(
                                text = stringResource(
                                    if (isExpanded) {
                                        R.string.history_filter_collapse_sheet
                                    } else {
                                        R.string.history_filter_expand_sheet
                                    }
                                ),
                                fontWeight = FontWeight.SemiBold,
                                color = FriendlyMoneyColors.Mint
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_close),
                                tint = FriendlyMoneyColors.textSecondary
                            )
                        }
                    }
                }
                Text(
                    text = selectionSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = FriendlyMoneyColors.textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 0.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    item {
                        CategoryFilterListRow(
                            emoji = null,
                            label = stringResource(R.string.history_filter_all_categories),
                            checked = isTypeChecked && selectedCategories.isEmpty(),
                            onCheckedChange = { onSelectionChanged(emptySet()) }
                        )
                    }

                    items(categories, key = { it.displayName }) { category ->
                        val isChecked = selectedCategories.contains(category.displayName)
                        CategoryFilterListRow(
                            emoji = category.emoji,
                            label = category.displayName,
                            checked = isChecked,
                            onCheckedChange = {
                                val next = if (isChecked) {
                                    selectedCategories - category.displayName
                                } else {
                                    selectedCategories + category.displayName
                                }
                                onSelectionChanged(next)
                            }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.common_confirm),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterListRow(
    emoji: String?,
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (checked) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                }
            )
            .clickable { onCheckedChange() }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange() }
        )
        if (emoji != null) {
            Text(text = emoji, modifier = Modifier.padding(end = 6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
