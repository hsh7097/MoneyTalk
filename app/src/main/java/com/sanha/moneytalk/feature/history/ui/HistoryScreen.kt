package com.sanha.moneytalk.feature.history.ui

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.database.entity.ExpenseEntity
import com.sanha.moneytalk.core.database.entity.IncomeEntity
import com.sanha.moneytalk.core.model.Category
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.ui.component.CategorySelectDialog
import com.sanha.moneytalk.core.ui.component.ExpenseDetailDialog
import com.sanha.moneytalk.core.ui.component.tab.SegmentedTabInfo
import com.sanha.moneytalk.core.ui.component.tab.SegmentedTabRowCompose
import com.sanha.moneytalk.core.ui.component.transaction.card.ExpenseTransactionCardInfo
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardCompose
import com.sanha.moneytalk.core.ui.component.transaction.header.TransactionGroupHeaderCompose
import com.sanha.moneytalk.core.util.DateUtils
import com.sanha.moneytalk.core.util.toDpTextUnit
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            // 헤더: 타이틀만 (아이콘은 탭 행으로 이동)
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )

            // 기간 선택 및 지출/수입 요약
            PeriodSummaryCard(
                year = uiState.selectedYear,
                month = uiState.selectedMonth,
                monthStartDay = uiState.monthStartDay,
                totalExpense = uiState.filteredExpenseTotal,
                totalIncome = uiState.filteredIncomeTotal,
                onPreviousMonth = { viewModel.previousMonth() },
                onNextMonth = { viewModel.nextMonth() }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 검색 모드에서는 필터/탭 숨기기 (달력 의미 없음)
        if (!uiState.isSearchMode) {
            // 탭 (목록/달력) + 검색/추가/필터 아이콘
            FilterTabRow(
                currentMode = viewMode,
                onModeChange = { viewMode = it },
                sortOrder = uiState.sortOrder,
                selectedCategory = uiState.selectedCategory,
                showExpenses = uiState.showExpenses,
                showIncomes = uiState.showIncomes,
                onApplyFilter = { sortOrder, showExp, showInc, category ->
                    viewModel.applyFilter(sortOrder, showExp, showInc, category)
                },
                onSearchClick = { viewModel.enterSearchMode() },
                onAddClick = { showAddDialog = true }
            )
        }

        // 콘텐츠
        when {
            viewMode == ViewMode.LIST -> {
                TransactionListView(
                    items = uiState.transactionListItems,
                    isLoading = uiState.isLoading,
                    showExpenses = uiState.showExpenses,
                    showIncomes = uiState.showIncomes,
                    hasActiveFilter = uiState.selectedCategory != null,
                    onIntent = viewModel::onIntent
                )
            }

            viewMode == ViewMode.CALENDAR -> {
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

    // 다이얼로그 상태는 ViewModel에서 관리
    uiState.selectedExpense?.let { expense ->
        Log.e(
            "sanha",
            "HistoryScreen[selectedExpense] : \nstoreName : ${expense.storeName}\noriginalSms : ${expense.originalSms}\namount : ${expense.amount}원"
        )

        ExpenseDetailDialog(
            expense = expense,
            onDismiss = { viewModel.onIntent(HistoryIntent.DismissDialog) },
            onDelete = { viewModel.onIntent(HistoryIntent.DeleteExpense(expense)) },
            onCategoryChange = { newCategory ->
                viewModel.onIntent(
                    HistoryIntent.ChangeCategory(
                        expense.id,
                        expense.storeName,
                        newCategory
                    )
                )
            },
            onMemoChange = { memo ->
                viewModel.onIntent(HistoryIntent.UpdateExpenseMemo(expense.id, memo))
            }
        )
    }

    uiState.selectedIncome?.let { income ->
        Log.e(
            "sanha",
            "HistoryScreen[selectedIncome] : ${income.originalSms}, ${income.amount}원"
        )
        IncomeDetailDialog(
            income = income,
            onDismiss = { viewModel.onIntent(HistoryIntent.DismissDialog) },
            onDelete = { viewModel.onIntent(HistoryIntent.DeleteIncome(income)) },
            onMemoChange = { memo ->
                viewModel.onIntent(HistoryIntent.UpdateIncomeMemo(income.id, memo))
            }
        )
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
                if (showCategoryDropdown) {
                    CategorySelectDialog(
                        currentCategory = selectedCategory,
                        showAllOption = false,
                        onDismiss = { showCategoryDropdown = false },
                        onCategorySelected = { selected ->
                            if (selected != null) {
                                selectedCategory = selected
                            }
                            showCategoryDropdown = false
                        }
                    )
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
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 18.sp),
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
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = onNextMonth,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.home_next_month),
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 오른쪽: 지출/수입 요약 (오른쪽 정렬, 라벨 고정 너비)
        Column(
            horizontalAlignment = Alignment.End
        ) {
            // 지출
            Row(
                verticalAlignment = Alignment.CenterVertically
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
                    modifier = Modifier.width(120.dp),
                    text = stringResource(R.string.common_won, numberFormat.format(totalExpense)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.End
                )
            }
            // 수입
            if (totalIncome > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
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
                        modifier = Modifier.width(120.dp),
                        text = stringResource(
                            R.string.common_won,
                            numberFormat.format(totalIncome)
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.moneyTalkColors.income,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

/**
 * 탭(목록/달력) + 필터 아이콘 통합 Row
 *
 * - 좌측: TabRow (목록 | 달력) — 2탭
 * - 우측: 필터 아이콘 → 클릭 시 FilterBottomSheet 표시
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterTabRow(
    currentMode: ViewMode,
    onModeChange: (ViewMode) -> Unit,
    sortOrder: SortOrder = SortOrder.DATE_DESC,
    selectedCategory: String? = null,
    showExpenses: Boolean = true,
    showIncomes: Boolean = true,
    onApplyFilter: (SortOrder, Boolean, Boolean, String?) -> Unit = { _, _, _, _ -> },
    onSearchClick: () -> Unit = {},
    onAddClick: () -> Unit = {}
) {
    var showBottomSheet by remember { mutableStateOf(false) }

    val hasActiveFilter = selectedCategory != null
            || sortOrder != SortOrder.DATE_DESC
            || !showExpenses
            || !showIncomes

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

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
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 왼쪽: 탭 (목록 / 달력) + 필터 버튼 (마진 8dp)
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
            // 필터 버튼 (아이콘 + 텍스트)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (hasActiveFilter) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { showBottomSheet = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (hasActiveFilter)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = stringResource(R.string.common_filter),
                        fontSize = 14.toDpTextUnit,
                        fontWeight = if (hasActiveFilter) FontWeight.Bold else FontWeight.Normal,
                        color = if (hasActiveFilter)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 오른쪽: 검색 + 추가
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
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(
                onClick = onAddClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.common_add),
                    modifier = Modifier.size(24.dp)
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
            currentCategory = selectedCategory,
            onDismiss = { showBottomSheet = false },
            onApply = { newSort, newShowExp, newShowInc, newCategory ->
                onApplyFilter(newSort, newShowExp, newShowInc, newCategory)
                showBottomSheet = false
            }
        )
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
            // 제목
            Text(
                text = stringResource(R.string.history_filter_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )

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
private fun FilterCategoryGridItem(
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
 * 통합 거래 목록 뷰
 * ViewModel에서 가공된 TransactionListItem 리스트를 순수 렌더링만 담당
 */
@Composable
fun TransactionListView(
    items: List<TransactionListItem>,
    isLoading: Boolean,
    showExpenses: Boolean = true,
    showIncomes: Boolean = true,
    hasActiveFilter: Boolean = false,
    onIntent: (HistoryIntent) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 필터 변경 시 최상단 스크롤
    LaunchedEffect(items) {
        listState.scrollToItem(0)
    }

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

    if (items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val isIncomeOnly = !showExpenses && showIncomes
                val emptyMessageRes = when {
                    hasActiveFilter -> R.string.history_no_filtered
                    isIncomeOnly -> R.string.history_no_income
                    else -> R.string.history_no_expense
                }
                Text(
                    text = if (hasActiveFilter) "🔍" else if (isIncomeOnly) "💰" else "📭",
                    style = MaterialTheme.typography.displayLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(emptyMessageRes),
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                count = items.size,
                key = { index ->
                    when (val item = items[index]) {
                        is TransactionListItem.Header -> "header_$index"
                        is TransactionListItem.ExpenseItem -> "expense_${item.expense.id}"
                        is TransactionListItem.IncomeItem -> "income_${item.income.id}"
                    }
                }
            ) { index ->
                when (val item = items[index]) {
                    is TransactionListItem.Header -> {
                        TransactionGroupHeaderCompose(info = item)
                    }

                    is TransactionListItem.ExpenseItem -> {
                        TransactionCardCompose(
                            info = item.cardInfo,
                            onClick = { onIntent(HistoryIntent.SelectExpense(item.expense)) }
                        )
                    }

                    is TransactionListItem.IncomeItem -> {
                        TransactionCardCompose(
                            info = item.cardInfo,
                            onClick = { onIntent(HistoryIntent.SelectIncome(item.income)) }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Scroll to Top FAB — SVG 디자인 반영: 40dp 원형, #137FEC 배경, 흰색 화살표
        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.moneyTalkColors.income,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 8.dp
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_up),
                    contentDescription = stringResource(R.string.common_scroll_to_top),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
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
                                .background(MaterialTheme.moneyTalkColors.calendarSunday)
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
                        0 -> MaterialTheme.moneyTalkColors.calendarSunday
                        6 -> MaterialTheme.moneyTalkColors.calendarSaturday
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
                                            selectedDateString =
                                                if (selectedDateString == calendarDay.dateString) {
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
                    TransactionCardCompose(
                        info = ExpenseTransactionCardInfo(expense),
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
        Log.e(
            "sanha",
            "HistoryScreen[BillingCycleCalendarView] : ${expense.storeName}, ${expense.amount}원"
        )
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
                            calendarDay.isToday -> MaterialTheme.colorScheme.primary
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
                        !calendarDay.isCurrentPeriod -> MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.3f
                        )

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
                        text = stringResource(
                            R.string.common_won,
                            numberFormat.format(income.amount)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.moneyTalkColors.income
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
                                color = if (memoText.isBlank()) MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.4f
                                ) else MaterialTheme.colorScheme.primary,
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
            text = {
                Text(
                    "이 수입 항목을 삭제하시겠습니까?\n${income.description.ifBlank { income.type }} (${
                        NumberFormat.getNumberInstance(
                            Locale.KOREA
                        ).format(income.amount)
                    }원)"
                )
            },
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
        set(
            Calendar.DAY_OF_MONTH,
            monthStartDay.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH))
        )
    }

    // 종료 날짜 계산 (시작일 - 1 또는 월말)
    val endCal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        if (monthStartDay > 1) {
            set(
                Calendar.DAY_OF_MONTH,
                (monthStartDay - 1).coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH))
            )
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
