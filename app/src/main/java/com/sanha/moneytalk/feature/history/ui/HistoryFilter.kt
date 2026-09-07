package com.sanha.moneytalk.feature.history.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

private fun buildTextSummary(
    selectedItems: Set<String>,
    allText: String,
    multiFormat: String
): String {
    if (selectedItems.isEmpty()) return allText
    val firstItem = selectedItems.first()
    val remainCount = selectedItems.size - 1
    return if (remainCount <= 0) firstItem else String.format(multiFormat, firstItem, remainCount)
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
    currentCardNames: Set<String> = emptySet(),
    allCardNames: List<String> = emptyList(),
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
        Set<String>,
        FixedExpenseFilter
    ) -> Unit
) {
    var selection by remember {
        mutableStateOf(
            HistoryFilterSelection(
                sortOrder = currentSortOrder,
                showExpenses = currentShowExpenses,
                showIncomes = currentShowIncomes,
                showTransfers = currentShowTransfers,
                expenseCategories = currentExpenseCategories,
                incomeCategories = currentIncomeCategories,
                transferCategories = currentTransferCategories,
                cardNames = currentCardNames,
                fixedExpenseFilter = currentFixedExpenseFilter
            )
        )
    }
    var categorySheetType by remember { mutableStateOf<CategorySheetType?>(null) }
    var showCardSheet by remember { mutableStateOf(false) }

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
    val cardOptions = remember(allCardNames, currentCardNames) {
        (allCardNames + currentCardNames).filter { it.isNotBlank() }.distinct().sorted()
    }
    val selectedTypes = selection.selectedTypes

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
                            selectedIndex = fixedOptions.indexOfFirst { it.first == selection.fixedExpenseFilter },
                            onOptionSelected = { index ->
                                selection = selection.copy(fixedExpenseFilter = fixedOptions[index].first)
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
                            selectedIndex = sortOptions.indexOfFirst { it.first == selection.sortOrder },
                            onOptionSelected = { index ->
                                selection = selection.copy(sortOrder = sortOptions[index].first)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Column {
                        Text(
                            text = stringResource(R.string.history_filter_card),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = FriendlyMoneyColors.textPrimary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = stringResource(R.string.history_filter_card_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = FriendlyMoneyColors.textSecondary,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        if (cardOptions.isEmpty()) {
                            Text(
                                text = stringResource(R.string.history_filter_no_cards),
                                style = MaterialTheme.typography.bodySmall,
                                color = FriendlyMoneyColors.textSecondary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.background)
                                    .border(1.dp, FriendlyMoneyColors.border, RoundedCornerShape(16.dp))
                                    .padding(vertical = 6.dp)
                            ) {
                                FilterCategorySummaryRow(
                                    label = stringResource(R.string.history_filter_card),
                                    summary = buildTextSummary(
                                        selectedItems = selection.cardNames,
                                        allText = allText,
                                        multiFormat = summaryFormat
                                    ),
                                    onCategoryClick = { showCardSheet = true }
                                )
                            }
                        }
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
                                showExpenses = selection.showExpenses,
                                showIncomes = selection.showIncomes,
                                showTransfers = selection.showTransfers,
                                onTypeClick = { type -> selection = selection.selectType(type) }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (selectedTypes.size == 1) {
                            when (selectedTypes.first()) {
                                FilterTransactionType.EXPENSE -> FilterCategoryChipGroup(
                                    sheetType = CategorySheetType.EXPENSE,
                                    categories = allExpenseCategories,
                                    selectedCategories = selection.expenseCategories,
                                    allText = allText,
                                    onSelectionChanged = { selection = selection.selectCategories(CategorySheetType.EXPENSE, it) },
                                    onMoreClick = { categorySheetType = CategorySheetType.EXPENSE }
                                )
                                FilterTransactionType.INCOME -> FilterCategoryChipGroup(
                                    sheetType = CategorySheetType.INCOME,
                                    categories = allIncomeCategories,
                                    selectedCategories = selection.incomeCategories,
                                    allText = allText,
                                    onSelectionChanged = { selection = selection.selectCategories(CategorySheetType.INCOME, it) },
                                    onMoreClick = { categorySheetType = CategorySheetType.INCOME }
                                )
                                FilterTransactionType.TRANSFER -> FilterCategoryChipGroup(
                                    sheetType = CategorySheetType.TRANSFER,
                                    categories = allTransferCategories,
                                    selectedCategories = selection.transferCategories,
                                    allText = allText,
                                    onSelectionChanged = { selection = selection.selectCategories(CategorySheetType.TRANSFER, it) },
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
                                if (selection.showExpenses) {
                                    FilterCategorySummaryRow(
                                        label = stringResource(R.string.home_expense),
                                        summary = buildCategorySummary(
                                            selectedCategories = selection.expenseCategories,
                                            allCategories = allExpenseCategories,
                                            allText = allText,
                                            multiFormat = summaryFormat
                                        ),
                                        onCategoryClick = { categorySheetType = CategorySheetType.EXPENSE }
                                    )
                                }
                                if (selection.showIncomes) {
                                    if (selection.showExpenses) {
                                        HorizontalDivider(color = FriendlyMoneyColors.border)
                                    }
                                    FilterCategorySummaryRow(
                                        label = stringResource(R.string.home_income),
                                        summary = buildCategorySummary(
                                            selectedCategories = selection.incomeCategories,
                                            allCategories = allIncomeCategories,
                                            allText = allText,
                                            multiFormat = summaryFormat
                                        ),
                                        onCategoryClick = { categorySheetType = CategorySheetType.INCOME }
                                    )
                                }
                                if (selection.showTransfers) {
                                    if (selection.showExpenses || selection.showIncomes) {
                                        HorizontalDivider(color = FriendlyMoneyColors.border)
                                    }
                                    FilterCategorySummaryRow(
                                        label = stringResource(R.string.transaction_type_transfer),
                                        summary = buildCategorySummary(
                                            selectedCategories = selection.transferCategories,
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
                        onClick = { selection = HistoryFilterSelection() },
                        enabled = !selection.isDefault,
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
                                selection.sortOrder,
                                selection.showExpenses,
                                selection.showIncomes,
                                selection.showTransfers,
                                selection.expenseCategories,
                                selection.incomeCategories,
                                selection.transferCategories,
                                selection.cardNames,
                                selection.fixedExpenseFilter
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
            CategorySheetType.EXPENSE -> selection.expenseCategories
            CategorySheetType.INCOME -> selection.incomeCategories
            CategorySheetType.TRANSFER -> selection.transferCategories
        }
        val isTypeChecked = when (type) {
            CategorySheetType.EXPENSE -> selection.showExpenses
            CategorySheetType.INCOME -> selection.showIncomes
            CategorySheetType.TRANSFER -> selection.showTransfers
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
                selection = selection.selectCategories(type, updated)
            },
            onDismiss = { categorySheetType = null }
        )
    }

    if (showCardSheet) {
        CardFilterListBottomSheet(
            cardNames = cardOptions,
            selectedCardNames = selection.cardNames,
            onSelectionChanged = { selection = selection.copy(cardNames = it) },
            onDismiss = { showCardSheet = false }
        )
    }
}
