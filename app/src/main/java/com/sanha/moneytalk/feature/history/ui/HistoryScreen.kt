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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sanha.moneytalk.R
import com.sanha.moneytalk.core.theme.moneyTalkColors
import com.sanha.moneytalk.core.ui.component.MonthKey
import com.sanha.moneytalk.core.ui.component.MonthPagerUtils
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.sanha.moneytalk.core.ui.component.ExpenseDetailDialog
import com.sanha.moneytalk.core.ui.component.transaction.card.TransactionCardCompose
import com.sanha.moneytalk.core.ui.component.transaction.header.TransactionGroupHeaderCompose
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.ComponentActivity
import com.sanha.moneytalk.MainViewModel
import com.sanha.moneytalk.core.ui.component.BannerAdCompose
import com.sanha.moneytalk.feature.home.ui.component.ImportDataCtaSection
import kotlinx.coroutines.launch

/**
 * History 탭 메인 화면
 * 검색/필터/목록/달력 뷰 전환 + 다이얼로그 관리
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onRequestSmsPermission: (onGranted: () -> Unit) -> Unit,
    filterCategory: String? = null,
    historyTabReClickEvent: kotlinx.coroutines.flow.SharedFlow<Unit>? = null
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

    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Activity-scoped MainViewModel (동기화/권한/광고 상태)
    val mainViewModel: MainViewModel = hiltViewModel(
        viewModelStoreOwner = context as ComponentActivity
    )
    val mainUiState by mainViewModel.uiState.collectAsStateWithLifecycle()

    // HorizontalPager — Virtual Infinite Pager
    val initialPage = remember {
        MonthPagerUtils.yearMonthToPage(uiState.selectedYear, uiState.selectedMonth)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { MonthPagerUtils.getPageCount(uiState.monthStartDay) }
    )
    val coroutineScope = rememberCoroutineScope()

    // ViewModel의 선택 월이 외부 요인(예: DataStore 설정 로드)으로 변경되면 Pager 위치도 동기화
    LaunchedEffect(uiState.selectedYear, uiState.selectedMonth) {
        val selectedPage = MonthPagerUtils.yearMonthToPage(
            uiState.selectedYear, uiState.selectedMonth
        )
        if (pagerState.currentPage != selectedPage) {
            pagerState.scrollToPage(selectedPage)
        }
    }

    // 페이지 변경 시 ViewModel에 월 변경 통지
    LaunchedEffect(pagerState.currentPage) {
        val (year, month) = MonthPagerUtils.pageToYearMonth(pagerState.currentPage)
        viewModel.setMonth(year, month)
    }

    // 내역 탭 재클릭 → 오늘(현재 커스텀 월) 페이지로 이동 + 필터 초기화
    val currentMonthStartDay by rememberUpdatedState(uiState.monthStartDay)
    LaunchedEffect(historyTabReClickEvent) {
        historyTabReClickEvent?.collect {
            viewModel.resetFilters()
            val (effYear, effMonth) = com.sanha.moneytalk.core.util.DateUtils.getEffectiveCurrentMonth(
                currentMonthStartDay
            )
            val todayPage = MonthPagerUtils.yearMonthToPage(effYear, effMonth)
            if (pagerState.currentPage != todayPage) {
                pagerState.animateScrollToPage(todayPage)
            }
        }
    }

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
                onPreviousMonth = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                },
                onNextMonth = {
                    coroutineScope.launch {
                        val target = pagerState.currentPage + 1
                        if (!MonthPagerUtils.isFutureMonth(target, uiState.monthStartDay)) {
                            pagerState.animateScrollToPage(target)
                        }
                    }
                }
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

        val isBannerAdEnabled = mainViewModel.adManager.isRewardAdEnabled()

        // 콘텐츠 — HorizontalPager로 월별 페이징
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            beyondViewportPageCount = 1,
            key = { it },
            userScrollEnabled = !uiState.isSearchMode
        ) { page ->
            // 이 페이지의 (year, month) 계산
            val (pageYear, pageMonth) = remember(page) {
                MonthPagerUtils.pageToYearMonth(page)
            }
            // pageCache에서 이 페이지의 데이터 읽기 (없으면 기본값)
            val pageData = uiState.pageCache[MonthKey(pageYear, pageMonth)]
                ?: HistoryPageData()

            // CTA 판별용: 현재 실효 월 여부
            val (effYearCta, effMonthCta) = com.sanha.moneytalk.core.util.DateUtils.getEffectiveCurrentMonth(uiState.monthStartDay)
            val isCurrentMonth = pageYear == effYearCta && pageMonth == effMonthCta
            val pageMonthLabel = if (isCurrentMonth) "이번달" else "${pageMonth}월"

            when {
                viewMode == ViewMode.LIST -> {
                    TransactionListView(
                        items = pageData.transactionListItems,
                        isLoading = pageData.isLoading,
                        showExpenses = uiState.showExpenses,
                        showIncomes = uiState.showIncomes,
                        hasActiveFilter = uiState.selectedCategory != null,
                        isCurrentMonth = isCurrentMonth,
                        isMonthSynced = mainViewModel.isMonthSynced(pageYear, pageMonth),
                        isPartiallyCovered = mainViewModel.isPagePartiallyCovered(pageYear, pageMonth),
                        hasSmsPermission = mainUiState.hasSmsPermission,
                        monthLabel = pageMonthLabel,
                        isAdEnabled = isBannerAdEnabled,
                        onImportData = {
                            onRequestSmsPermission {
                                mainViewModel.syncIncremental()
                            }
                        },
                        onRequestFullSync = {
                            if (isCurrentMonth) {
                                // 현재월 → 바로 증분 동기화
                                onRequestSmsPermission {
                                    mainViewModel.syncIncremental()
                                }
                            } else if (!isBannerAdEnabled) {
                                // 광고 비활성 → 광고 없이 바로 전체 동기화 해제
                                onRequestSmsPermission {
                                    mainViewModel.unlockFullSync(pageYear, pageMonth)
                                }
                            } else {
                                mainViewModel.showFullSyncAdDialog(pageYear, pageMonth)
                            }
                        },
                        scrollResetKey = Triple(
                            uiState.selectedCategory,
                            uiState.sortOrder,
                            pageYear to pageMonth
                        ),
                        onIntent = viewModel::onIntent
                    )
                }

                viewMode == ViewMode.CALENDAR -> {
                    BillingCycleCalendarView(
                        year = pageYear,
                        month = pageMonth,
                        monthStartDay = uiState.monthStartDay,
                        dailyTotals = pageData.dailyTotals,
                        dailyIncomeTotals = pageData.dailyIncomeTotals,
                        expenses = pageData.expenses,
                        onDelete = { viewModel.deleteExpense(it) },
                        onCategoryChange = { expense, newCategory ->
                            viewModel.updateExpenseCategory(expense.storeName, newCategory)
                        },
                        onExpenseMemoChange = { id, memo -> viewModel.updateExpenseMemo(id, memo) }
                    )
                }
            }
        }

        // 배너 광고 (RTDB reward_ad_enabled 연동)
        if (isBannerAdEnabled) {
            BannerAdCompose()
        }
    }

    // 다이얼로그 상태는 ViewModel에서 관리
    uiState.selectedExpense?.let { expense ->
        Log.e(
            "MT_DEBUG",
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
            "MT_DEBUG",
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

    // 동기화/광고 다이얼로그는 Activity 레벨(MoneyTalkApp)에서 전역 관리
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
    isCurrentMonth: Boolean = true,
    isMonthSynced: Boolean = false,
    isPartiallyCovered: Boolean = false,
    hasSmsPermission: Boolean = true,
    monthLabel: String = "이번달",
    isAdEnabled: Boolean = true,
    onImportData: () -> Unit = {},
    onRequestFullSync: () -> Unit = {},
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
        // 데이터 가져오기 CTA: 현재월 + 필터 없음 (items.isEmpty → 데이터 없음 확정)
        val showImportCta = isCurrentMonth && !hasActiveFilter
        // 전체 동기화 CTA: 과거 월 + 미해제 + 필터 없음
        val showFullSyncCta = !isCurrentMonth && !isMonthSynced && !hasActiveFilter
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (showImportCta) {
                ImportDataCtaSection(
                    onImportData = onImportData
                )
            } else if (showFullSyncCta) {
                com.sanha.moneytalk.core.ui.component.FullSyncCtaSection(
                    onRequestFullSync = onRequestFullSync,
                    monthLabel = monthLabel,
                    isAdEnabled = isAdEnabled
                )
            } else {
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
            // 현재월 + 권한 없음 → 데이터 가져오기 CTA (데이터 있어도 표시)
            if (isCurrentMonth && !hasSmsPermission) {
                item(key = "import_cta") {
                    ImportDataCtaSection(
                        onImportData = onImportData
                    )
                }
            }

            // 부분 데이터 안내 CTA (데이터 있지만 일부 기간 누락)
            if (isPartiallyCovered && !isMonthSynced) {
                item(key = "partial_cta") {
                    com.sanha.moneytalk.core.ui.component.FullSyncCtaSection(
                        onRequestFullSync = onRequestFullSync,
                        monthLabel = monthLabel,
                        isPartial = true,
                        isAdEnabled = isAdEnabled
                    )
                }
            }

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
