package com.sanha.moneytalk.feature.history.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.sanha.moneytalk.core.model.Category
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.ui.component.CategoryIcon
import com.sanha.moneytalk.core.ui.component.ExpenseDetailDialog
import com.sanha.moneytalk.core.ui.component.ExpenseItemCard
import com.sanha.moneytalk.core.util.DateUtils
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
                // 검색 모드일 때 검색 바, 아니면 일반 헤더
                if (uiState.isSearchMode) {
                    SearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = { viewModel.search(it) },
                        onClose = { viewModel.exitSearchMode() }
                    )
                } else {
                    // 헤더: 타이틀 + 검색/추가 아이콘
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.history_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            IconButton(onClick = { viewModel.enterSearchMode() }) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.common_search),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(onClick = { showAddDialog = true }) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.common_add),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // 기간 선택 및 지출/수입 요약
                    PeriodSummaryCard(
                        year = uiState.selectedYear,
                        month = uiState.selectedMonth,
                        monthStartDay = uiState.monthStartDay,
                        totalExpense = uiState.monthlyTotal,
                        totalIncome = uiState.monthlyIncomeTotal,
                        onPreviousMonth = { viewModel.previousMonth() },
                        onNextMonth = { viewModel.nextMonth() }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 검색 모드에서는 필터/탭 숨기기 (달력 의미 없음)
                if (!uiState.isSearchMode) {
                    // 탭 (목록/달력) + 필터
                    FilterTabRow(
                        currentMode = viewMode,
                        onModeChange = { viewMode = it },
                        cardNames = uiState.cardNames,
                        selectedCardName = uiState.selectedCardName,
                        onCardNameSelected = { viewModel.filterByCardName(it) },
                        selectedCategory = uiState.selectedCategory,
                        onCategorySelected = { viewModel.filterByCategory(it) },
                        sortOrder = uiState.sortOrder,
                        onSortOrderChange = { viewModel.setSortOrder(it) },
                        showIncomeView = uiState.showIncomeView,
                        onToggleIncomeView = { viewModel.toggleIncomeView() }
                    )
                }

                // 콘텐츠
                if (uiState.showIncomeView) {
                    IncomeListView(
                        incomes = uiState.incomes,
                        isLoading = uiState.isLoading,
                        onDeleteIncome = { viewModel.deleteIncome(it) },
                        onIncomeMemoChange = { id, memo -> viewModel.updateIncomeMemo(id, memo) }
                    )
                } else when (viewMode) {
                    ViewMode.LIST -> {
                        ExpenseListView(
                            expenses = uiState.expenses,
                            incomes = uiState.incomes,
                            sortOrder = uiState.sortOrder,
                            isLoading = uiState.isLoading,
                            onDelete = { viewModel.deleteExpense(it) },
                            onDeleteIncome = { viewModel.deleteIncome(it) },
                            onCategoryChange = { expense, newCategory ->
                                viewModel.updateExpenseCategory(expense.id, expense.storeName, newCategory)
                            },
                            onExpenseMemoChange = { id, memo -> viewModel.updateExpenseMemo(id, memo) },
                            onIncomeMemoChange = { id, memo -> viewModel.updateIncomeMemo(id, memo) }
                        )
                    }
                    ViewMode.CALENDAR -> {
                        BillingCycleCalendarView(
                            year = uiState.selectedYear,
                            month = uiState.selectedMonth,
                            monthStartDay = uiState.monthStartDay,
                            dailyTotals = uiState.dailyTotals,
                            expenses = uiState.expenses,
                            onDelete = { viewModel.deleteExpense(it) },
                            onCategoryChange = { expense, newCategory ->
                                viewModel.updateExpenseCategory(expense.id, expense.storeName, newCategory)
                            },
                            onExpenseMemoChange = { id, memo -> viewModel.updateExpenseMemo(id, memo) }
                        )
                    }
                }
            }
    }

    // 수동 지출 추가 다이얼로그
    if (showAddDialog) {
        AddExpenseDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { amount, storeName, category, cardName ->
                viewModel.addManualExpense(amount, storeName, category, cardName)
                showAddDialog = false
            }
        )
    }
}

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
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_clear))
                    }
                }
            }
        )
    }
}

@Composable
fun AddExpenseDialog(
    onDismiss: () -> Unit,
    onConfirm: (amount: Int, storeName: String, category: String, cardName: String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var storeName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("기타") }
    var cardName by remember { mutableStateOf("현금") }
    var showCategoryDropdown by remember { mutableStateOf(false) }

    val categories = Category.entries.map { it.displayName }

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
                    suffix = { Text("원") },
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
                Box {
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
                    DropdownMenu(
                        expanded = showCategoryDropdown,
                        onDismissRequest = { showCategoryDropdown = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    showCategoryDropdown = false
                                }
                            )
                        }
                    }
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

enum class ViewMode {
    LIST, CALENDAR
}

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
            "%02d.%02d.%02d",
            startCal.get(Calendar.YEAR) % 100,
            startCal.get(Calendar.MONTH) + 1,
            startCal.get(Calendar.DAY_OF_MONTH)
        )
        val end = String.format(
            "%02d.%02d.%02d",
            endCal.get(Calendar.YEAR) % 100,
            endCal.get(Calendar.MONTH) + 1,
            endCal.get(Calendar.DAY_OF_MONTH)
        )
        start to end
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 이전 월 버튼
            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.home_previous_month),
                    modifier = Modifier.size(28.dp)
                )
            }

            // 기간 표시
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$startDate",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "- $endDate",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // 지출/수입 요약
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.home_expense) + " ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = stringResource(R.string.common_won, numberFormat.format(totalExpense)),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (totalIncome > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.home_income) + " ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "+" + stringResource(R.string.common_won, numberFormat.format(totalIncome)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }

            // 다음 월 버튼
            IconButton(
                onClick = onNextMonth,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.home_next_month),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * 탭(목록/달력) + 필터 아이콘 통합 Row
 *
 * - 좌측: TabRow (목록 | 달력)
 * - 우측: 필터 아이콘 → 클릭 시 카드/카테고리/정렬 가로 병렬 드롭다운
 */
@Composable
fun FilterTabRow(
    currentMode: ViewMode,
    onModeChange: (ViewMode) -> Unit,
    cardNames: List<String>,
    selectedCardName: String?,
    onCardNameSelected: (String?) -> Unit,
    selectedCategory: String? = null,
    onCategorySelected: (String?) -> Unit = {},
    sortOrder: SortOrder = SortOrder.DATE_DESC,
    onSortOrderChange: (SortOrder) -> Unit = {},
    showIncomeView: Boolean = false,
    onToggleIncomeView: () -> Unit = {}
) {
    var showFilterPanel by remember { mutableStateOf(false) }

    val hasActiveFilter = selectedCardName != null || selectedCategory != null || sortOrder != SortOrder.DATE_DESC

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 탭 (목록 / 달력 / 수입)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (currentMode == ViewMode.LIST && !showIncomeView) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable {
                            onModeChange(ViewMode.LIST)
                            if (showIncomeView) onToggleIncomeView()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.history_view_list),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (currentMode == ViewMode.LIST && !showIncomeView) FontWeight.Bold else FontWeight.Normal,
                        color = if (currentMode == ViewMode.LIST && !showIncomeView)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (currentMode == ViewMode.CALENDAR && !showIncomeView) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .clickable {
                            onModeChange(ViewMode.CALENDAR)
                            if (showIncomeView) onToggleIncomeView()
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.history_view_calendar),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (currentMode == ViewMode.CALENDAR && !showIncomeView) FontWeight.Bold else FontWeight.Normal,
                        color = if (currentMode == ViewMode.CALENDAR && !showIncomeView)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (showIncomeView) Color(0xFF4CAF50)
                            else Color.Transparent
                        )
                        .clickable { onToggleIncomeView() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_income),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (showIncomeView) FontWeight.Bold else FontWeight.Normal,
                        color = if (showIncomeView)
                            Color.White
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 필터 아이콘 (수입 보기가 아닐 때, 목록 모드일 때만)
            if (currentMode == ViewMode.LIST && !showIncomeView) {
                IconButton(
                    onClick = { showFilterPanel = !showFilterPanel },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = stringResource(R.string.common_filter),
                        modifier = Modifier.size(20.dp),
                        tint = if (hasActiveFilter)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 필터 패널 (병렬 드롭다운 3개)
        AnimatedVisibility(visible = showFilterPanel && currentMode == ViewMode.LIST && !showIncomeView) {
            FilterPanel(
                cardNames = cardNames,
                selectedCardName = selectedCardName,
                onCardNameSelected = onCardNameSelected,
                selectedCategory = selectedCategory,
                onCategorySelected = onCategorySelected,
                sortOrder = sortOrder,
                onSortOrderChange = onSortOrderChange
            )
        }
    }
}

/**
 * 필터 패널: 카드사/카테고리/정렬을 가로로 병렬 배치
 */
@Composable
fun FilterPanel(
    cardNames: List<String>,
    selectedCardName: String?,
    onCardNameSelected: (String?) -> Unit,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit
) {
    var showCardMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 카드 필터
        Box(modifier = Modifier.weight(1f)) {
            FilterChipButton(
                label = selectedCardName ?: "카드 전체",
                isActive = selectedCardName != null,
                onClick = { showCardMenu = true },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = showCardMenu,
                onDismissRequest = { showCardMenu = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "전체",
                            fontWeight = if (selectedCardName == null) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedCardName == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { onCardNameSelected(null); showCardMenu = false }
                )
                cardNames.forEach { cardName ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                cardName,
                                fontWeight = if (selectedCardName == cardName) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedCardName == cardName) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = { onCardNameSelected(cardName); showCardMenu = false }
                    )
                }
            }
        }

        // 카테고리 필터
        Box(modifier = Modifier.weight(1f)) {
            FilterChipButton(
                label = selectedCategory ?: "카테고리 전체",
                isActive = selectedCategory != null,
                onClick = { showCategoryMenu = true },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = showCategoryMenu,
                onDismissRequest = { showCategoryMenu = false }
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "전체",
                            fontWeight = if (selectedCategory == null) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedCategory == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { onCategorySelected(null); showCategoryMenu = false }
                )
                Category.entries.forEach { category ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CategoryIcon(category = category, containerSize = 32.dp, iconSize = 20.dp)
                                Text(
                                    category.displayName,
                                    fontWeight = if (selectedCategory == category.displayName) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedCategory == category.displayName) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        onClick = { onCategorySelected(category.displayName); showCategoryMenu = false }
                    )
                }
            }
        }

        // 정렬
        Box(modifier = Modifier.weight(1f)) {
            val sortLabel = when (sortOrder) {
                SortOrder.DATE_DESC -> "최신순"
                SortOrder.AMOUNT_DESC -> "금액순"
                SortOrder.STORE_FREQ -> "사용처별"
            }
            FilterChipButton(
                label = sortLabel,
                isActive = sortOrder != SortOrder.DATE_DESC,
                onClick = { showSortMenu = true },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                listOf(
                    SortOrder.DATE_DESC to "최신순",
                    SortOrder.AMOUNT_DESC to "금액 높은순",
                    SortOrder.STORE_FREQ to "사용처별"
                ).forEach { (order, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                label,
                                fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Normal,
                                color = if (sortOrder == order) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = { onSortOrderChange(order); showSortMenu = false }
                    )
                }
            }
        }
    }
}

/**
 * 필터 칩 버튼 (일관된 스타일)
 */
@Composable
fun FilterChipButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ExpenseListView(
    expenses: List<ExpenseEntity>,
    incomes: List<IncomeEntity> = emptyList(),
    sortOrder: SortOrder = SortOrder.DATE_DESC,
    isLoading: Boolean,
    onDelete: (ExpenseEntity) -> Unit,
    onDeleteIncome: (IncomeEntity) -> Unit = {},
    onCategoryChange: (ExpenseEntity, String) -> Unit = { _, _ -> },
    onExpenseMemoChange: (Long, String?) -> Unit = { _, _ -> },
    onIncomeMemoChange: (Long, String?) -> Unit = { _, _ -> }
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    var selectedExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var selectedIncome by remember { mutableStateOf<IncomeEntity?>(null) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
            (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset > 200)
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (expenses.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "\uD83D\uDCED",
                    style = MaterialTheme.typography.displayLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.history_no_expense),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (sortOrder) {
                SortOrder.DATE_DESC -> {
                    // 날짜별 그룹핑 (지출 + 수입 통합)
                    val groupedExpenses = expenses.groupBy { expense ->
                        try {
                            val calendar = Calendar.getInstance().apply {
                                timeInMillis = expense.dateTime
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            calendar.time
                        } catch (e: Exception) {
                            Date()
                        }
                    }

                    val groupedIncomes = incomes.groupBy { income ->
                        try {
                            val calendar = Calendar.getInstance().apply {
                                timeInMillis = income.dateTime
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            calendar.time
                        } catch (e: Exception) {
                            Date()
                        }
                    }

                    // 모든 날짜 키를 합쳐서 정렬
                    val allDates = (groupedExpenses.keys + groupedIncomes.keys)
                        .toSortedSet(compareByDescending { it })

                    allDates.forEach { date ->
                        val dayExpenses = groupedExpenses[date] ?: emptyList()
                        val dayIncomes = groupedIncomes[date] ?: emptyList()
                        val dailyExpenseTotal = dayExpenses.sumOf { it.amount }
                        val dailyIncomeTotal = dayIncomes.sumOf { it.amount }
                        val calendar = Calendar.getInstance().apply { time = date }
                        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
                        val dayOfWeekResId = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                            Calendar.SUNDAY -> R.string.day_sunday
                            Calendar.MONDAY -> R.string.day_monday
                            Calendar.TUESDAY -> R.string.day_tuesday
                            Calendar.WEDNESDAY -> R.string.day_wednesday
                            Calendar.THURSDAY -> R.string.day_thursday
                            Calendar.FRIDAY -> R.string.day_friday
                            Calendar.SATURDAY -> R.string.day_saturday
                            else -> R.string.day_sunday
                        }

                        // 날짜 헤더
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.history_day_header, dayOfMonth, stringResource(dayOfWeekResId)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (dailyIncomeTotal > 0) {
                                        Text(
                                            text = "+" + stringResource(R.string.common_won, numberFormat.format(dailyIncomeTotal)),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF4CAF50)
                                        )
                                    }
                                    if (dailyExpenseTotal > 0) {
                                        Text(
                                            text = "-" + stringResource(R.string.common_won, numberFormat.format(dailyExpenseTotal)),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp
                            )
                        }

                        // 수입 항목 (해당 날짜)
                        items(
                            items = dayIncomes,
                            key = { "income_${it.id}" }
                        ) { income ->
                            IncomeItemCard(
                                income = income,
                                onClick = { selectedIncome = income }
                            )
                        }

                        // 지출 항목 (해당 날짜)
                        items(
                            items = dayExpenses,
                            key = { "expense_${it.id}" }
                        ) { expense ->
                            ExpenseItemCard(
                                expense = expense,
                                onClick = { selectedExpense = expense }
                            )
                        }
                    }
                }

                SortOrder.AMOUNT_DESC -> {
                    // 금액순: 전체 기간 플랫 리스트 (날짜 그룹 없이)
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "금액 높은순 (${expenses.size}건)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 0.5.dp
                        )
                    }

                    items(
                        items = expenses,
                        key = { it.id }
                    ) { expense ->
                        ExpenseItemCard(
                            expense = expense,
                            onClick = { selectedExpense = expense }
                        )
                    }
                }

                SortOrder.STORE_FREQ -> {
                    // 사용처별: 가게명으로 그룹핑
                    val storeGroups = expenses.groupBy { it.storeName }
                        .entries
                        .sortedByDescending { it.value.size }

                    storeGroups.forEach { (storeName, storeExpenses) ->
                        val storeTotal = storeExpenses.sumOf { it.amount }

                        // 사용처 헤더
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$storeName (${storeExpenses.size}회)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "-" + stringResource(R.string.common_won, numberFormat.format(storeTotal)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp
                            )
                        }

                        // 해당 사용처 지출 항목 (최신순)
                        items(
                            items = storeExpenses.sortedByDescending { it.dateTime },
                            key = { it.id }
                        ) { expense ->
                            ExpenseItemCard(
                                expense = expense,
                                onClick = { selectedExpense = expense }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Scroll to Top FAB
        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.common_scroll_to_top)
                )
            }
        }
    }

    // 지출 상세 다이얼로그 (삭제 및 카테고리 변경 기능 포함)
    selectedExpense?.let { expense ->
        ExpenseDetailDialog(
            expense = expense,
            onDismiss = { selectedExpense = null },
            onDelete = { onDelete(expense) },
            onCategoryChange = { newCategory ->
                onCategoryChange(expense, newCategory)
                selectedExpense = null
            },
            onMemoChange = { memo ->
                onExpenseMemoChange(expense.id, memo)
                selectedExpense = null
            }
        )
    }

    // 수입 상세 다이얼로그
    selectedIncome?.let { income ->
        IncomeDetailDialog(
            income = income,
            onDismiss = { selectedIncome = null },
            onDelete = { onDeleteIncome(income) },
            onMemoChange = { memo ->
                onIncomeMemoChange(income.id, memo)
                selectedIncome = null
            }
        )
    }
}


/**
 * 수입 목록 뷰
 */
@Composable
fun IncomeListView(
    incomes: List<IncomeEntity>,
    isLoading: Boolean,
    onDeleteIncome: (IncomeEntity) -> Unit = {},
    onIncomeMemoChange: (Long, String?) -> Unit = { _, _ -> }
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    var selectedIncome by remember { mutableStateOf<IncomeEntity?>(null) }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (incomes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "\uD83D\uDCB0",
                    style = MaterialTheme.typography.displayLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "수입 내역이 없습니다",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        return
    }

    // 날짜별 그룹핑
    val groupedIncomes = incomes.groupBy { income ->
        try {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = income.dateTime
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            calendar.time
        } catch (e: Exception) {
            Date()
        }
    }.toSortedMap(compareByDescending { it })

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        groupedIncomes.forEach { (date, dayIncomes) ->
            val dailyTotal = dayIncomes.sumOf { it.amount }
            val calendar = Calendar.getInstance().apply { time = date }
            val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
            val dayOfWeekResId = when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> R.string.day_sunday
                Calendar.MONDAY -> R.string.day_monday
                Calendar.TUESDAY -> R.string.day_tuesday
                Calendar.WEDNESDAY -> R.string.day_wednesday
                Calendar.THURSDAY -> R.string.day_thursday
                Calendar.FRIDAY -> R.string.day_friday
                Calendar.SATURDAY -> R.string.day_saturday
                else -> R.string.day_sunday
            }

            // 날짜 헤더
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.history_day_header, dayOfMonth, stringResource(dayOfWeekResId)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "+" + stringResource(R.string.common_won, numberFormat.format(dailyTotal)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            }

            // 수입 항목
            items(
                items = dayIncomes,
                key = { it.id }
            ) { income ->
                IncomeItemCard(
                    income = income,
                    onClick = { selectedIncome = income }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 수입 상세 다이얼로그
    selectedIncome?.let { income ->
        IncomeDetailDialog(
            income = income,
            onDismiss = { selectedIncome = null },
            onDelete = { onDeleteIncome(income) },
            onMemoChange = { memo ->
                onIncomeMemoChange(income.id, memo)
                selectedIncome = null
            }
        )
    }
}

/**
 * 수입 아이템 카드
 */
@Composable
fun IncomeItemCard(
    income: IncomeEntity,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREA)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // 수입 아이콘
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\uD83D\uDCB0",
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                // 설명 (예: "OOO에서 급여")
                Text(
                    text = income.description.ifBlank { income.type },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 유형 | 시간
                Text(
                    text = "${income.type} | ${timeFormat.format(Date(income.dateTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 금액 (+ 표시, 녹색)
        Text(
            text = "+${numberFormat.format(income.amount)}원",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50)
        )
    }
}

// 날짜 정보를 담는 데이터 클래스
data class CalendarDay(
    val year: Int,
    val month: Int,
    val day: Int,
    val dateString: String, // "yyyy-MM-dd" 형식
    val isCurrentPeriod: Boolean, // 현재 결제 기간에 속하는지
    val isFuture: Boolean, // 오늘 이후인지
    val isToday: Boolean
)

@Composable
fun BillingCycleCalendarView(
    year: Int,
    month: Int,
    monthStartDay: Int,
    dailyTotals: Map<String, Int>, // "yyyy-MM-dd" -> amount
    expenses: List<ExpenseEntity> = emptyList(),
    onDelete: (ExpenseEntity) -> Unit = {},
    onCategoryChange: (ExpenseEntity, String) -> Unit = { _, _ -> },
    onExpenseMemoChange: (Long, String?) -> Unit = { _, _ -> }
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    val today = Calendar.getInstance()
    val todayYear = today.get(Calendar.YEAR)
    val todayMonth = today.get(Calendar.MONTH) + 1
    val todayDay = today.get(Calendar.DAY_OF_MONTH)

    // 선택된 날짜 (dateString)
    var selectedDateString by remember { mutableStateOf<String?>(null) }
    // 상세 다이얼로그용 선택된 지출
    var selectedExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    // 결제 기간에 해당하는 날짜 목록 생성
    val calendarDays = remember(year, month, monthStartDay) {
        generateBillingCycleDays(year, month, monthStartDay, todayYear, todayMonth, todayDay)
    }

    // 주 단위로 그룹핑
    val weeks = remember(calendarDays) {
        calendarDays.chunked(7)
    }

    // 주별 합계 계산
    val weeklyTotals = remember(weeks, dailyTotals) {
        weeks.map { week ->
            week.filter { it.isCurrentPeriod }.sumOf { day ->
                dailyTotals[day.dateString] ?: 0
            }
        }
    }

    // 무지출일 계산 (오늘까지만)
    val noSpendDays = remember(calendarDays, dailyTotals) {
        calendarDays.count { day ->
            day.isCurrentPeriod && !day.isFuture && (dailyTotals[day.dateString] ?: 0) == 0
        }
    }

    // 선택된 날짜의 지출 목록
    val selectedDayExpenses = remember(selectedDateString, expenses) {
        if (selectedDateString == null) emptyList()
        else {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
            expenses.filter { expense ->
                val expenseDate = dateFormat.format(Date(expense.dateTime))
                expenseDate == selectedDateString
            }.sortedByDescending { it.dateTime }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        // 무지출일 배너
        if (noSpendDays > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE91E63))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.history_no_spend_month, month),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = stringResource(R.string.history_no_spend_total, noSpendDays),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // 요일 헤더
        val dayLabels = listOf(
            R.string.day_sun, R.string.day_mon, R.string.day_tue,
            R.string.day_wed, R.string.day_thu, R.string.day_fri, R.string.day_sat
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            dayLabels.forEachIndexed { index, dayResId ->
                Text(
                    text = stringResource(dayResId),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (index) {
                        0 -> Color(0xFFE91E63)
                        6 -> Color(0xFF2196F3)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    }
                )
            }
        }

        // 달력 그리드
        LazyColumn {
            weeks.forEachIndexed { weekIndex, week ->
                val weekTotal = weeklyTotals.getOrNull(weekIndex) ?: 0

                item {
                    Column {
                        // 주간 디바이더
                        if (weekIndex > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }

                        // 주간 합계 (오른쪽 정렬)
                        if (weekTotal > 0) {
                            Text(
                                text = "-${numberFormat.format(weekTotal)}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 4.dp, top = 4.dp, bottom = 2.dp),
                                textAlign = TextAlign.End,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Spacer(modifier = Modifier.height(18.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            week.forEach { calendarDay ->
                                CalendarDayCell(
                                    calendarDay = calendarDay,
                                    dayTotal = dailyTotals[calendarDay.dateString] ?: 0,
                                    isSelected = selectedDateString == calendarDay.dateString,
                                    onClick = {
                                        if (calendarDay.isCurrentPeriod && !calendarDay.isFuture) {
                                            selectedDateString = if (selectedDateString == calendarDay.dateString) {
                                                null // 토글: 같은 날짜 다시 클릭 시 닫기
                                            } else {
                                                calendarDay.dateString
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // 부족한 셀 채우기
                            repeat(7 - week.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // 선택된 날짜의 지출 목록
            if (selectedDateString != null && selectedDayExpenses.isNotEmpty()) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )

                    val dateStr = selectedDateString ?: ""
                    val parts = dateStr.split("-")
                    val dayNum = parts.getOrNull(2)?.toIntOrNull() ?: 0
                    Text(
                        text = "${parts.getOrNull(1) ?: ""}월 ${dayNum}일 지출 내역",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                items(
                    items = selectedDayExpenses,
                    key = { it.id }
                ) { expense ->
                    ExpenseItemCard(
                        expense = expense,
                        onClick = { selectedExpense = expense },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            } else if (selectedDateString != null && selectedDayExpenses.isEmpty()) {
                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                    Text(
                        text = "해당 날짜에 지출 내역이 없습니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 지출 상세 다이얼로그 (삭제 및 카테고리 변경 기능 포함)
    selectedExpense?.let { expense ->
        ExpenseDetailDialog(
            expense = expense,
            onDismiss = { selectedExpense = null },
            onDelete = {
                onDelete(expense)
                selectedExpense = null
            },
            onCategoryChange = { newCategory ->
                onCategoryChange(expense, newCategory)
                selectedExpense = null
            },
            onMemoChange = { memo ->
                onExpenseMemoChange(expense.id, memo)
                selectedExpense = null
            }
        )
    }
}

@Composable
fun CalendarDayCell(
    calendarDay: CalendarDay,
    dayTotal: Int,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    Box(
        modifier = modifier
            .padding(2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else Color.Transparent
            )
            .clickable(
                enabled = calendarDay.isCurrentPeriod && !calendarDay.isFuture
            ) { onClick() }
            .aspectRatio(0.8f),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 날짜
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            calendarDay.isToday -> Color(0xFF4CAF50)
                            else -> Color.Transparent
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = calendarDay.day.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (calendarDay.isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        calendarDay.isToday -> Color.White
                        calendarDay.isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        !calendarDay.isCurrentPeriod -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            // 일별 지출 (미래 날짜는 표시 안함)
            if (dayTotal > 0 && !calendarDay.isFuture && calendarDay.isCurrentPeriod) {
                Text(
                    text = "-${numberFormat.format(dayTotal)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1
                )
            }
        }
    }
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
                        text = "금액",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "+${numberFormat.format(income.amount)}원",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }

                // 유형
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "유형",
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
                            text = "출처",
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
                        text = "입금 시간",
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
                            text = "고정 수입",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "매월 ${income.recurringDay ?: "-"}일",
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
                            text = "메모",
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
                    income.memo?.let { memo ->
                        if (memo.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "메모",
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
                    text = "원본 문자",
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
                            text = income.originalSms ?: "원본 문자 정보가 없습니다",
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
                Text("삭제")
            }
        }
    )

    // 삭제 확인 다이얼로그
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("수입 삭제") },
            text = { Text("이 수입 항목을 삭제하시겠습니까?\n${income.description.ifBlank { income.type }} (+${NumberFormat.getNumberInstance(Locale.KOREA).format(income.amount)}원)") },
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
                    Text("삭제")
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
}

/**
 * 결제 기간에 해당하는 날짜 목록 생성
 * 예: monthStartDay가 21이면, 이전 달 21일 ~ 이번 달 20일
 */
private fun generateBillingCycleDays(
    year: Int,
    month: Int,
    monthStartDay: Int,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int
): List<CalendarDay> {
    val days = mutableListOf<CalendarDay>()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)

    // 시작 날짜 계산
    val startCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)

        // 시작일이 1이 아니면 이전 달로 이동
        if (monthStartDay > 1) {
            add(Calendar.MONTH, -1)
        }
        set(Calendar.DAY_OF_MONTH, monthStartDay.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
    }

    // 종료 날짜 계산 (시작일 - 1 또는 월말)
    val endCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        if (monthStartDay > 1) {
            set(Calendar.DAY_OF_MONTH, (monthStartDay - 1).coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH)))
        } else {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }
    }

    // 시작 주의 일요일로 이동 (캘린더 첫 행 시작)
    val displayStartCal = startCal.clone() as Calendar
    while (displayStartCal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
        displayStartCal.add(Calendar.DAY_OF_MONTH, -1)
    }

    // 종료 주의 토요일로 이동 (캘린더 마지막 행 끝)
    val displayEndCal = endCal.clone() as Calendar
    while (displayEndCal.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) {
        displayEndCal.add(Calendar.DAY_OF_MONTH, 1)
    }

    // 날짜 목록 생성
    val currentCal = displayStartCal.clone() as Calendar
    while (!currentCal.after(displayEndCal)) {
        val calYear = currentCal.get(Calendar.YEAR)
        val calMonth = currentCal.get(Calendar.MONTH) + 1
        val calDay = currentCal.get(Calendar.DAY_OF_MONTH)
        val dateString = dateFormat.format(currentCal.time)

        // 현재 결제 기간에 속하는지 확인
        val isInPeriod = !currentCal.before(startCal) && !currentCal.after(endCal)

        // 미래 날짜인지 확인
        val isFuture = when {
            calYear > todayYear -> true
            calYear < todayYear -> false
            calMonth > todayMonth -> true
            calMonth < todayMonth -> false
            else -> calDay > todayDay
        }

        // 오늘인지 확인
        val isToday = calYear == todayYear && calMonth == todayMonth && calDay == todayDay

        days.add(
            CalendarDay(
                year = calYear,
                month = calMonth,
                day = calDay,
                dateString = dateString,
                isCurrentPeriod = isInPeriod,
                isFuture = isFuture,
                isToday = isToday
            )
        )

        currentCal.add(Calendar.DAY_OF_MONTH, 1)
    }

    return days
}
