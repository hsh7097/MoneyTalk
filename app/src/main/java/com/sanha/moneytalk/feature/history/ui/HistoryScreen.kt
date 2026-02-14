package com.sanha.moneytalk.feature.history.ui

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.ui.component.ExpenseDetailDialog
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardCompose
import com.sanha.moneytalk.core.ui.component.transaction.header.TransactionGroupHeaderCompose
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch

/**
 * History 탭 메인 화면
 * 검색/필터/목록/달력 뷰 전환 + 다이얼로그 관리
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    filterCategory: String? = null
) {
    // 외부에서 전달된 카테고리 필터 적용 (홈 → 내역 이동 시, 일회성)
    var filterConsumed by remember { mutableStateOf(false) }
    LaunchedEffect(filterCategory) {
        if (filterCategory != null && !filterConsumed) {
            filterConsumed = true
            viewModel.applyFilter(
                sortOrder = SortOrder.DATE_DESC,
                showExpenses = true,
                showIncomes = true,
                category = filterCategory
            )
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
                    scrollResetKey = Triple(
                        uiState.selectedCategory,
                        uiState.sortOrder,
                        uiState.selectedYear to uiState.selectedMonth
                    ),
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
                        viewModel.updateExpenseCategory(expense.storeName, newCategory)
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

enum class ViewMode {
    LIST, CALENDAR
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
    scrollResetKey: Any? = null,
    onIntent: (HistoryIntent) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 필터/월 변경 시에만 최상단 스크롤 (DB emit에는 반응하지 않음)
    LaunchedEffect(scrollResetKey) {
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

        // Scroll to Top FAB
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
