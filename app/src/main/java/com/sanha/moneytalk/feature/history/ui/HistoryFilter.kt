package com.sanha.moneytalk.feature.history.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.model.CategoryInfo
import com.sanha.moneytalk.core.model.CategoryProvider
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkOverlay
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkState
import com.sanha.moneytalk.core.ui.coachmark.CoachMarkTargetRegistry
import com.sanha.moneytalk.core.ui.coachmark.onboardingTarget
import com.sanha.moneytalk.core.ui.component.radiogroup.RadioGroupCompose
import com.sanha.moneytalk.core.ui.component.radiogroup.RadioGroupOption
import com.sanha.moneytalk.feature.history.ui.coachmark.filterCoachMarkSteps
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class CategorySheetType(
    @StringRes val titleResId: Int
) {
    EXPENSE(R.string.history_filter_expense_category_title),
    INCOME(R.string.history_filter_income_category_title),
    TRANSFER(R.string.history_filter_transfer_category_title)
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

/**
 * 필터 BottomSheet.
 *
 * 정렬 / 고정지출 / 타입별 카테고리 선택 후 적용.
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

    val context = LocalContext.current
    val provider = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            CategoryProvider.Provider::class.java
        ).categoryProvider()
    }
    var allExpenseCategories by remember { mutableStateOf<List<CategoryInfo>>(Category.expenseEntries) }
    var allIncomeCategories by remember { mutableStateOf<List<CategoryInfo>>(Category.incomeEntries) }
    var allTransferCategories by remember { mutableStateOf<List<CategoryInfo>>(Category.transferEntries) }
    LaunchedEffect(Unit) {
        val expense = withContext(Dispatchers.IO) { provider.getExpenseEntries() }
        val income = withContext(Dispatchers.IO) { provider.getIncomeEntries() }
        val transfer = withContext(Dispatchers.IO) { provider.getTransferEntries() }
        allExpenseCategories = expense
        allIncomeCategories = income
        allTransferCategories = transfer
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp - 100.dp
    val allText = stringResource(R.string.common_all)
    val summaryFormat = stringResource(R.string.history_filter_category_summary_multiple)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
        ) {
            // === 고정 영역: 타이틀 + 정렬 + 고정지출 (스크롤 영역 밖) ===
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
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
                    if (!isFilterDefault(
                            tempSortOrder,
                            tempShowExpenses,
                            tempShowIncomes,
                            tempShowTransfers,
                            tempExpenseCategories,
                            tempIncomeCategories,
                            tempTransferCategories,
                            tempFixedFilter
                        )
                    ) {
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
                                    tempShowTransfers = true
                                    tempExpenseCategories = emptySet()
                                    tempIncomeCategories = emptySet()
                                    tempTransferCategories = emptySet()
                                    tempFixedFilter = FixedExpenseFilter.ALL
                                    hasAutoCollapsed = false
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.onboardingTarget("filter_sort", filterCoachMarkRegistry)
                ) {
                    Text(
                        text = stringResource(R.string.history_filter_sort),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    val sortOptions = listOf(
                        SortOrder.DATE_DESC to stringResource(R.string.history_sort_date),
                        SortOrder.AMOUNT_DESC to stringResource(R.string.history_sort_amount_short),
                        SortOrder.STORE_FREQ to stringResource(R.string.history_sort_store)
                    )
                    RadioGroupCompose(
                        options = sortOptions.map { (order, label) ->
                            RadioGroupOption(label = label, isSelected = tempSortOrder == order)
                        },
                        onOptionSelected = { index ->
                            tempSortOrder = sortOptions[index].first
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = stringResource(R.string.history_filter_fixed),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    val fixedOptions = listOf(
                        FixedExpenseFilter.ALL to stringResource(R.string.history_filter_fixed_all),
                        FixedExpenseFilter.FIXED_ONLY to stringResource(R.string.history_filter_fixed_only),
                        FixedExpenseFilter.EXCLUDE_FIXED to stringResource(R.string.history_filter_fixed_exclude)
                    )
                    RadioGroupCompose(
                        options = fixedOptions.map { (filter, label) ->
                            RadioGroupOption(label = label, isSelected = tempFixedFilter == filter)
                        },
                        onOptionSelected = { index ->
                            tempFixedFilter = fixedOptions[index].first
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // === 스크롤 영역: 카테고리 선택만 ===
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.history_filter_category),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.history_filter_category_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(vertical = 6.dp)
                    ) {
                    FilterCategoryTypeRow(
                        label = stringResource(R.string.home_expense),
                        checked = tempShowExpenses,
                        summary = buildCategorySummary(
                            selectedCategories = tempExpenseCategories,
                            allCategories = allExpenseCategories,
                            allText = allText,
                            multiFormat = summaryFormat
                        ),
                        onCheckedChange = { checked ->
                            if (!checked && !tempShowIncomes && !tempShowTransfers) return@FilterCategoryTypeRow
                            tempShowExpenses = checked
                            if (!checked) tempExpenseCategories = emptySet()
                            hasAutoCollapsed = true
                        },
                        onCategoryClick = { categorySheetType = CategorySheetType.EXPENSE }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    FilterCategoryTypeRow(
                        label = stringResource(R.string.home_income),
                        checked = tempShowIncomes,
                        summary = buildCategorySummary(
                            selectedCategories = tempIncomeCategories,
                            allCategories = allIncomeCategories,
                            allText = allText,
                            multiFormat = summaryFormat
                        ),
                        onCheckedChange = { checked ->
                            if (!checked && !tempShowExpenses && !tempShowTransfers) return@FilterCategoryTypeRow
                            tempShowIncomes = checked
                            if (!checked) tempIncomeCategories = emptySet()
                            hasAutoCollapsed = true
                        },
                        onCategoryClick = { categorySheetType = CategorySheetType.INCOME }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    FilterCategoryTypeRow(
                        label = stringResource(R.string.transaction_type_transfer),
                        checked = tempShowTransfers,
                        summary = buildCategorySummary(
                            selectedCategories = tempTransferCategories,
                            allCategories = allTransferCategories,
                            allText = allText,
                            multiFormat = summaryFormat
                        ),
                        onCheckedChange = { checked ->
                            if (!checked && !tempShowExpenses && !tempShowIncomes) return@FilterCategoryTypeRow
                            tempShowTransfers = checked
                            if (!checked) tempTransferCategories = emptySet()
                            hasAutoCollapsed = true
                        },
                        onCategoryClick = { categorySheetType = CategorySheetType.TRANSFER }
                    )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                        tempFixedFilter
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
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
private fun FilterCategoryTypeRow(
    label: String,
    checked: Boolean,
    summary: String,
    onCheckedChange: (Boolean) -> Unit,
    onCategoryClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onCheckedChange(!checked) }
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onCheckedChange(!checked) }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onCategoryClick)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterListBottomSheet(
    sheetType: CategorySheetType,
    isTypeChecked: Boolean,
    selectedCategories: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val provider = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            CategoryProvider.Provider::class.java
        ).categoryProvider()
    }
    val defaultCategories: List<CategoryInfo> = when (sheetType) {
        CategorySheetType.EXPENSE -> Category.expenseEntries
        CategorySheetType.INCOME -> Category.incomeEntries
        CategorySheetType.TRANSFER -> Category.transferEntries
    }
    var categories by remember { mutableStateOf(defaultCategories) }
    LaunchedEffect(sheetType) {
        val loaded = withContext(Dispatchers.IO) {
            when (sheetType) {
                CategorySheetType.EXPENSE -> provider.getExpenseEntries()
                CategorySheetType.INCOME -> provider.getIncomeEntries()
                CategorySheetType.TRANSFER -> provider.getTransferEntries()
            }
        }
        categories = loaded
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.58f).dp
    val selectionSummary = if (selectedCategories.isEmpty()) {
        stringResource(R.string.history_filter_all_categories)
    } else {
        stringResource(R.string.history_filter_selected_count, selectedCategories.size)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_close)
                        )
                    }
                }
                Text(
                    text = selectionSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 0.dp,
                        bottom = 72.dp
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
                                MaterialTheme.colorScheme.surface
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
